package com.core.controllers;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.driverduty.DriverDocumentResponse;
import com.core.security.SecurityContextUtil;
import com.core.services.DriverDocumentService;

import lombok.RequiredArgsConstructor;

/*
 * Authenticated driver KYC/document upload API, Bearer JWT (mirrors
 * DriverAppController's auth model). Real persistence via the extended
 * Document entity -- no mock/fake success state.
 */
@RestController
@RequestMapping("/driver/app/documents")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class DriverDocumentController {

	private final DriverDocumentService driverDocumentService;
	private final SecurityContextUtil security;

	@GetMapping("/{documentType}")
	public DriverDocumentResponse getDocumentStatus(@PathVariable String documentType) {
		return driverDocumentService.getDocumentStatus(security.orgId(), security.userId(), documentType);
	}

	@PostMapping(
			value = "/{documentType}",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE
	)
	public DriverDocumentResponse uploadDocument(
			@PathVariable String documentType,
			@RequestPart("file") MultipartFile file
	) throws IOException {
		return driverDocumentService.uploadDocument(security.orgId(), security.userId(), documentType, file);
	}
}
