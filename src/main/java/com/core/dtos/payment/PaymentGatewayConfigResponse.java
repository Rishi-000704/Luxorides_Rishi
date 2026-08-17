package com.core.dtos.payment;

import com.core.models.enums.PaymentGateway;

import java.time.Instant;

public record PaymentGatewayConfigResponse(

        String id,

        String orgId,

        PaymentGateway gateway,

        Boolean active,

        Boolean defaultConfig,

        String displayName,

        String merchantName,

        String currency,

        Boolean checkoutEnabled,

        Boolean qrEnabled,

        Boolean autoCapture,

        String apiBaseUrl,

        Boolean keyIdConfigured,

        String keyIdMasked,

        Boolean keySecretConfigured,

        String keySecretMasked,

        Boolean webhookSecretConfigured,

        String webhookSecretMasked,

        String providerSettingsJson,

        Instant createdAt,

        Instant updatedAt,

        String createdBy,

        String updatedBy
) {
}