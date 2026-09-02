package com.core.services;

import java.io.IOException;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.driverduty.DriverDocumentResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Document;
import com.core.models.Driver;
import com.core.models.enums.DocumentVerificationStatus;
import com.core.repositories.DocumentRepository;
import com.core.repositories.DriverRepository;
import com.core.services.common.FileService;

import lombok.RequiredArgsConstructor;

/*
 * Real KYC/document upload for the driver app -- extends the previously
 * orphaned Document entity in place (confirmed zero existing references
 * anywhere in the codebase before this change) rather than building a
 * parallel entity. No auto-verification: every upload lands as
 * PENDING_REVIEW, exactly like the pre-existing real flow has no ops
 * reviewer UI yet -- this deliberately does not simulate an instant
 * verified/failed outcome the way the mock did.
 */
@Service
@RequiredArgsConstructor
public class DriverDocumentService {

	private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;

	private final DriverRepository driverRepository;
	private final DocumentRepository documentRepository;
	private final FileService fileService;

	@Transactional
	public DriverDocumentResponse uploadDocument(
			String orgId,
			String userId,
			String documentType,
			MultipartFile file,
			Instant expiryDate
	) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "A document file is required");
		}
		if (file.getSize() > MAX_FILE_SIZE) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "File size cannot exceed 10 MB");
		}
		if (expiryDate != null && expiryDate.isBefore(Instant.now())) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Expiry date cannot be in the past");
		}

		Driver driver = resolveDriver(orgId, userId);

		Document document = documentRepository
				.findByReferenceIdAndOrgIdAndDocumentType(driver.getId(), orgId, documentType)
				.orElseGet(Document::new);

		String previousFileName = document.getFileName();

		document.setOrgId(orgId);
		document.setReferenceId(driver.getId());
		document.setDocumentType(documentType);
		document.setFileName(fileService.saveFile(file));
		document.setExpiryDate(expiryDate);
		document.setStatus(DocumentVerificationStatus.PENDING_REVIEW);
		document.setRejectionReason(null);
		document.setVerifiedAt(null);
		document.setVerifiedBy(null);

		Document saved = documentRepository.save(document);

		/*
		 * P1.6 -- a re-upload of the same documentType (retake after
		 * rejection, or a renewed document) previously left the superseded
		 * file on disk forever: the row's fileName was overwritten with no
		 * reference to the old one ever kept anywhere. Deleted only now, after
		 * the row pointing at the new file is durably saved -- if save() had
		 * thrown, the transaction rolls back and the old file stays the
		 * record of truth. There is no history/versioning of prior
		 * submissions in this entity today (a re-upload already discards the
		 * old rejectionReason/verifiedAt/By in the same way), so this does
		 * not remove anything the system itself still considers current.
		 */
		if (previousFileName != null && !previousFileName.equals(saved.getFileName())) {
			try {
				fileService.deleteFile(previousFileName);
			} catch (Exception ignored) {
				// Orphan cleanup must not fail an otherwise-successful upload.
			}
		}

		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public DriverDocumentResponse getDocumentStatus(String orgId, String userId, String documentType) {
		Driver driver = resolveDriver(orgId, userId);

		return documentRepository
				.findByReferenceIdAndOrgIdAndDocumentType(driver.getId(), orgId, documentType)
				.map(this::toResponse)
				.orElseGet(() -> new DriverDocumentResponse(documentType, null, null, null, null));
	}

	private DriverDocumentResponse toResponse(Document document) {
		return new DriverDocumentResponse(
				document.getDocumentType(),
				document.getStatus(),
				document.getRejectionReason(),
				document.getVerifiedAt(),
				document.getExpiryDate()
		);
	}

	private Driver resolveDriver(String orgId, String userId) {
		return driverRepository.findByUserId(userId)
				.filter(d -> orgId.equals(d.getOrgId()))
				.orElseThrow(() -> new NotFoundException(ErrorCode.DRIVER_NOT_FOUND, "Driver record not found for this account"));
	}
}
