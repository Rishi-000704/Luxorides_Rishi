package com.core.util;

import com.core.models.enums.PaymentMode;

public final class PaymentUtil {
	private PaymentUtil() {
	}

	public static PaymentMode mapPaymentMode(String method) {
		return switch (method.toLowerCase()) {
		case "upi" -> PaymentMode.UPI;
		case "card" -> PaymentMode.CARD;
		case "netbanking" -> PaymentMode.NET_BANKING;
		case "wallet" -> PaymentMode.WALLET;
		default -> PaymentMode.UNKNOWN;
		};
	}
}
