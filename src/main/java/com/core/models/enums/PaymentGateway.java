package com.core.models.enums;

public enum PaymentGateway {
    RAZORPAY,
    CASHFREE,
    STRIPE,
    PAYU,
    MANUAL_ENTRY,

    /** Dev/local-only: simulates a payment with no real money movement. See MockPaymentService. */
    MOCK
}