package com.core.dtos.payment;

import com.core.models.enums.PaymentGateway;

public record PaymentGatewayRuntimeConfig(
        String id,
        String orgId,
        PaymentGateway gateway,
        Boolean active,

        String displayName,
        String merchantName,
        String currency,
        Boolean checkoutEnabled,
        Boolean qrEnabled,
        Boolean autoCapture,

        String apiBaseUrl,
        String keyId,
        String keySecret,
        String webhookSecret,
        String providerSettingsJson
) {
}