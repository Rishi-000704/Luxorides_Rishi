package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.core.dtos.driverduty.DriverDocumentResponse;
import com.core.exception.BusinessException;
import com.core.models.Document;
import com.core.models.Driver;
import com.core.models.enums.DocumentVerificationStatus;
import com.core.repositories.DocumentRepository;
import com.core.repositories.DriverRepository;
import com.core.services.common.FileService;

/*
 * Covers P2.5's KYC expiry wiring: expiryDate now round-trips through
 * upload/status, and the server rejects an expiry date in the past rather
 * than trusting whatever the mobile client happens to send.
 */
class DriverDocumentServiceTest {

	private static final String ORG_ID = "org-1";
	private static final String USER_ID = "user-1";
	private static final String DRIVER_ID = "driver-1";
	private static final String DOC_TYPE = "drivingLicence";

	private DriverRepository driverRepository;
	private DocumentRepository documentRepository;
	private FileService fileService;
	private DriverDocumentService service;

	@BeforeEach
	void setUp() throws Exception {
		driverRepository = mock(DriverRepository.class);
		documentRepository = mock(DocumentRepository.class);
		fileService = mock(FileService.class);
		service = new DriverDocumentService(driverRepository, documentRepository, fileService);

		Driver driver = new Driver();
		driver.setId(DRIVER_ID);
		driver.setOrgId(ORG_ID);
		when(driverRepository.findByUserId(USER_ID)).thenReturn(Optional.of(driver));

		when(documentRepository.findByReferenceIdAndOrgIdAndDocumentType(DRIVER_ID, ORG_ID, DOC_TYPE)).thenReturn(Optional.empty());
		when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
			Document d = inv.getArgument(0);
			if (d.getId() == null) d.setId("doc-1");
			return d;
		});
		when(fileService.saveFile(any())).thenReturn("stored-file.jpg");
	}

	private MockMultipartFile file() {
		return new MockMultipartFile("file", "licence.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
	}

	@Test
	void uploadDocument_storesExpiryDate_andReturnsPendingReview() throws Exception {
		Instant expiry = Instant.now().plus(365, ChronoUnit.DAYS);

		DriverDocumentResponse response = service.uploadDocument(ORG_ID, USER_ID, DOC_TYPE, file(), expiry);

		assertEquals(DOC_TYPE, response.documentType());
		assertEquals(DocumentVerificationStatus.PENDING_REVIEW, response.status());
		assertEquals(expiry, response.expiryDate());
	}

	@Test
	void uploadDocument_rejectsExpiryDateInThePast() {
		Instant pastExpiry = Instant.now().minus(1, ChronoUnit.DAYS);

		assertThrows(BusinessException.class, () -> service.uploadDocument(ORG_ID, USER_ID, DOC_TYPE, file(), pastExpiry));
	}

	@Test
	void uploadDocument_allowsNullExpiry_forDocumentsWithoutOne() throws Exception {
		DriverDocumentResponse response = service.uploadDocument(ORG_ID, USER_ID, DOC_TYPE, file(), null);

		assertNull(response.expiryDate());
	}

	@Test
	void uploadDocument_firstTimeUpload_neverCallsDeleteFile() throws Exception {
		service.uploadDocument(ORG_ID, USER_ID, DOC_TYPE, file(), null);

		verify(fileService, never()).deleteFile(any());
	}

	@Test
	void uploadDocument_reupload_deletesThePreviouslyStoredFile_afterTheRowIsSaved() throws Exception {
		Document existing = new Document();
		existing.setId("doc-1");
		existing.setDocumentType(DOC_TYPE);
		existing.setFileName("old-licence.jpg");
		when(documentRepository.findByReferenceIdAndOrgIdAndDocumentType(DRIVER_ID, ORG_ID, DOC_TYPE))
				.thenReturn(Optional.of(existing));
		when(fileService.saveFile(any())).thenReturn("new-licence.jpg");

		service.uploadDocument(ORG_ID, USER_ID, DOC_TYPE, file(), null);

		verify(fileService).deleteFile("old-licence.jpg");
	}

	@Test
	void getDocumentStatus_returnsNullStatus_whenNeverUploaded() {
		DriverDocumentResponse response = service.getDocumentStatus(ORG_ID, USER_ID, "aadhaarCard");

		assertEquals("aadhaarCard", response.documentType());
		assertNull(response.status());
	}
}
