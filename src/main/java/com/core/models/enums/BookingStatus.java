package com.core.models.enums;

public enum BookingStatus {
	DRAFT, REQUESTED, CONFIRMED, RUNNING, COMPLETED, BILLED, CANCELLED;

	public boolean isTerminal() {
		return this == COMPLETED || this == CANCELLED;
	}
}