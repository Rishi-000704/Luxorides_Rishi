package com.core.dtos.driverduty;

import java.math.BigDecimal;

public record PaymentInstruction(
	boolean collectionRequired,
	BigDecimal amount,
	String qrCodeUrl,
	String paymentLink,
	String message
	) {}
