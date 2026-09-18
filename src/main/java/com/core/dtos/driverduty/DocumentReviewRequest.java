package com.core.dtos.driverduty;

import jakarta.validation.constraints.NotNull;

// approved=true -> VERIFIED, approved=false -> REJECTED (rejectionReason
// required in that case -- enforced in DriverDocumentService, not here,
// since it's a cross-field rule the bean-validation layer can't express
// cleanly without a custom constraint).
public record DocumentReviewRequest(
		@NotNull Boolean approved,
		String rejectionReason
) {
}
