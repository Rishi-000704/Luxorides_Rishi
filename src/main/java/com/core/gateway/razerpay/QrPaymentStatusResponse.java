package com.core.gateway.razerpay;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QrPaymentStatusResponse {

	private String status; // NOT_CREATED, PENDING, PAID, EXPIRED, FAILED

	private boolean paid;

	private BigDecimal amount;

	private String paymentId;          // Local Fleetovo payment id
	private String razorpayPaymentId;  // pay_xxxxx

	private String qrCodeId;           // qr_xxxxx
	private String qrImageUrl;         // Razorpay hosted QR image URL
	private String qrImageContent;     // Raw QR/UPI payload if Razorpay enables it

	private Instant expiresAt;
	private Instant paidAt;

	private String message;
}