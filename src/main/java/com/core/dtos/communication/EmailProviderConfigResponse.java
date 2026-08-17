package com.core.dtos.communication;

import com.core.models.enums.EmailProviderType;

import java.time.Instant;


public record EmailProviderConfigResponse(

        String id,

        String orgId,

        EmailProviderType providerType,

        Boolean active,

        Boolean defaultConfig,

        String displayName,

        String senderName,

        String senderEmail,

        String replyToEmail,

        String apiBaseUrl,

        Boolean apiKeyConfigured,

        String apiKeyMasked,

        Boolean apiSecretConfigured,

        String apiSecretMasked,

        String smtpHost,

        Integer smtpPort,

        Boolean smtpUsernameConfigured,

        String smtpUsernameMasked,

        Boolean smtpPasswordConfigured,

        String smtpPasswordMasked,

        Boolean smtpUseTls,

        Boolean smtpUseSsl,

        Boolean webhookSecretConfigured,

        String webhookSecretMasked,

        String providerSettingsJson,

        Instant createdAt,

        Instant updatedAt,

        String createdBy,

        String updatedBy
) {
}
