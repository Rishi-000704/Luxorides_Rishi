package com.core.dtos.driverduty;

import java.time.Instant;

import com.core.models.enums.DocumentVerificationStatus;

/*
 * Employee-side view of one driver document -- same fields as
 * DriverDocumentResponse (the driver's own self-view) plus fileUrl and
 * verifiedBy, which a reviewing employee needs and a driver doesn't.
 */
public record DriverDocumentReviewResponse(
		String documentType,
		DocumentVerificationStatus status,
		String fileUrl,
		String rejectionReason,
		Instant verifiedAt,
		String verifiedBy,
		Instant expiryDate
) {
}
