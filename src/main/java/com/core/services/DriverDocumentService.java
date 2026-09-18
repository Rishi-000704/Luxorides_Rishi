package com.core.services;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.driverduty.DocumentReviewRequest;
import com.core.dtos.driverduty.DriverDocumentResponse;
import com.core.dtos.driverduty.DriverDocumentReviewResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Document;
import com.core.models.Driver;
import com.core.models.enums.DocumentVerificationStatus;
import com.core.models.enums.FileAccessCategory;
import com.core.repositories.DocumentRepository;
import com.core.repositories.DriverRepository;
import com.core.services.common.FileAccessTokenService;
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

	// The documents a driver must have VERIFIED before they can be allotted
	// a duty or move a vehicle out of the garage (see areRequiredDocumentsVerified,
	// called from DriverAppService#acceptDuty and ExternalDriverDutyService's
	// duty-start submission). Mirrors the Chauffeur app's own DocKind union
	// (drivingLicence/aadhaarCard) -- the two documents onboarding collects.
	private static final Set<String> REQUIRED_DOCUMENT_TYPES = Set.of("drivingLicence", "aadhaarCard");

	private final DriverRepository driverRepository;
	private final DocumentRepository documentRepository;
	private final FileService fileService;
	private final FileAccessTokenService fileAccessTokenService;

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

	// Employee-side: every document this driver has ever submitted, with a
	// real file URL so a reviewer can actually look at the photo before
	// deciding. A required type with no row yet (never uploaded) is included
	// as an explicit "not submitted" entry -- an ops reviewer needs to see
	// that gap, not just the documents that happen to exist.
	@Transactional(readOnly = true)
	public List<DriverDocumentReviewResponse> listForReview(String orgId, String driverId) {
		Driver driver = resolveDriverById(orgId, driverId);

		List<Document> existing = documentRepository.findByReferenceIdAndOrgId(driver.getId(), orgId);
		List<DriverDocumentReviewResponse> responses = new ArrayList<>(
				existing.stream().map(this::toReviewResponse).toList());

		Set<String> submittedTypes = existing.stream().map(Document::getDocumentType).collect(Collectors.toSet());
		for (String requiredType : REQUIRED_DOCUMENT_TYPES) {
			if (!submittedTypes.contains(requiredType)) {
				responses.add(new DriverDocumentReviewResponse(requiredType, null, null, null, null, null, null));
			}
		}

		return responses;
	}

	// Employee-side: approve or reject one of this driver's submitted
	// documents. A rejection requires a real reason -- the driver sees this
	// verbatim (see PhotoCapture's errorText on re-upload), never a generic
	// "rejected" with no explanation of what to fix.
	@Transactional
	public DriverDocumentReviewResponse reviewDocument(
			String orgId,
			String driverId,
			String documentType,
			String reviewerUserId,
			DocumentReviewRequest request
	) {
		if (Boolean.FALSE.equals(request.approved())
				&& (request.rejectionReason() == null || request.rejectionReason().isBlank())) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "A rejection reason is required");
		}

		Driver driver = resolveDriverById(orgId, driverId);

		Document document = documentRepository
				.findByReferenceIdAndOrgIdAndDocumentType(driver.getId(), orgId, documentType)
				.orElseThrow(() -> new NotFoundException(ErrorCode.DOCUMENT_NOT_FOUND, "This document hasn't been submitted yet"));

		document.setStatus(Boolean.TRUE.equals(request.approved())
				? DocumentVerificationStatus.VERIFIED
				: DocumentVerificationStatus.REJECTED);
		document.setRejectionReason(Boolean.TRUE.equals(request.approved()) ? null : request.rejectionReason());
		document.setVerifiedAt(Instant.now());
		document.setVerifiedBy(reviewerUserId);

		return toReviewResponse(documentRepository.save(document));
	}

	// The hard safety gate: true only when BOTH required documents are
	// VERIFIED for real (an ops reviewer explicitly approved each one) --
	// never inferred from "uploaded" or "pending". Called before a driver
	// can accept a duty (DriverAppService#acceptDuty) and again before a
	// duty can actually start (ExternalDriverDutyService's start submission)
	// so a vehicle can never leave the garage on an unverified driver.
	@Transactional(readOnly = true)
	public boolean areRequiredDocumentsVerified(String orgId, String driverId) {
		List<Document> documents = documentRepository.findByReferenceIdAndOrgId(driverId, orgId);

		for (String requiredType : REQUIRED_DOCUMENT_TYPES) {
			boolean verified = documents.stream()
					.anyMatch(d -> requiredType.equals(d.getDocumentType()) && d.getStatus() == DocumentVerificationStatus.VERIFIED);
			if (!verified) {
				return false;
			}
		}

		return true;
	}

	private DriverDocumentReviewResponse toReviewResponse(Document document) {
		return new DriverDocumentReviewResponse(
				document.getDocumentType(),
				document.getStatus(),
				toFileUrl(document.getFileName(), document.getOrgId()),
				document.getRejectionReason(),
				document.getVerifiedAt(),
				document.getVerifiedBy(),
				document.getExpiryDate()
		);
	}

	// toAccessUrl returns only "filename?token=..." (see FileAccessTokenService)
	// -- the root-relative "/file/" prefix a browser can actually GET is added
	// here, matching EmployeeDriverDutySubmissionService's fileUrl() convention
	// for the same DTO-field-name pattern ("...Url" fields are ready-to-use,
	// unlike DriverAssembler's bare `pic`, which the frontend itself prefixes).
	private String toFileUrl(String fileName, String orgId) {
		if (fileName == null || fileName.isBlank()) {
			return null;
		}

		return "/file/" + fileAccessTokenService.toAccessUrl(fileName, orgId, FileAccessCategory.PRIVATE);
	}

	private Driver resolveDriverById(String orgId, String driverId) {
		return driverRepository.findByIdAndOrgId(driverId, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.DRIVER_NOT_FOUND, "Driver not found"));
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
