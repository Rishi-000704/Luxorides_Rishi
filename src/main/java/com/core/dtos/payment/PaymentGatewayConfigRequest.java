package com.core.dtos.payment;

import com.core.models.enums.PaymentGateway;

public record PaymentGatewayConfigRequest(

        PaymentGateway gateway,

        Boolean active,

        String displayName,

        String merchantName,

        String currency,

        Boolean checkoutEnabled,

        Boolean qrEnabled,

        Boolean autoCapture,

        String apiBaseUrl,

        /*
         * Plain secret input.
         * If null/blank during update, existing encrypted value will be preserved.
         */
        String keyId,

        String keySecret,

        String webhookSecret,

        String providerSettingsJson
) {
}