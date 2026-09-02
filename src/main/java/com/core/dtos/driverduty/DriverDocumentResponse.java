package com.core.dtos.driverduty;

import java.time.Instant;

import com.core.models.enums.DocumentVerificationStatus;

public record DriverDocumentResponse(
		String documentType,
		DocumentVerificationStatus status,
		String rejectionReason,
		Instant verifiedAt,
		Instant expiryDate
) {
}
