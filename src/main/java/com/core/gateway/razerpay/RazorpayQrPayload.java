package com.core.gateway.razerpay;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class RazorpayQrPayload {

	private String paymentId;      // Local Fleetovo payment id
	private String qrCodeId;       // Razorpay qr_xxxxx
	private String qrImageUrl;     // Razorpay image_url
	private String qrImageContent;
	private BigDecimal amount;
	private Instant expiresAt;
}