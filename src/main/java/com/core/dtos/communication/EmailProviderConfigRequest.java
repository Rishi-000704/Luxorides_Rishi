package com.core.dtos.communication;

import com.core.models.enums.EmailProviderType;

public record EmailProviderConfigRequest(

        EmailProviderType providerType,

        Boolean active,

        String displayName,

        String senderName,

        String senderEmail,

        String replyToEmail,

        String apiBaseUrl,

        /*
         * Plain secret input.
         * If null/blank during update, existing encrypted value will be preserved.
         */
        String apiKey,

        String apiSecret,

        String smtpHost,

        Integer smtpPort,

        String smtpUsername,

        String smtpPassword,

        Boolean smtpUseTls,

        Boolean smtpUseSsl,

        String webhookSecret,

        String providerSettingsJson
) {
}