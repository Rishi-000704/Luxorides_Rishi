package com.core.models.enums;

public enum GstType {

	IGST, CGST_SGST, EXEMPT;

	public boolean isInterState() {
		return this == IGST;
	}

	public boolean hasTax() {
		return this != EXEMPT;
	}
}
