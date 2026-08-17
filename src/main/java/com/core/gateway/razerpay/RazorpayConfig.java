package com.core.gateway.razerpay;

/**
 * Razorpay is now configured per organization through PaymentGatewayConfig.
 *
 * Do not create a singleton RazorpayClient bean here.
 * Every payment operation must resolve credentials by orgId using RazorpayClientFactory.
 */
public final class RazorpayConfig {

    private RazorpayConfig() {
    }
}