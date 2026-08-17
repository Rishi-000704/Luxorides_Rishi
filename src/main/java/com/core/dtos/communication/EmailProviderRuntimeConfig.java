package com.core.dtos.communication;

import com.core.models.enums.EmailProviderType;

public record EmailProviderRuntimeConfig(
        String id,
        String orgId,
        EmailProviderType providerType,
        Boolean active,

        String displayName,
        String senderName,
        String senderEmail,
        String replyToEmail,

        String apiBaseUrl,
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