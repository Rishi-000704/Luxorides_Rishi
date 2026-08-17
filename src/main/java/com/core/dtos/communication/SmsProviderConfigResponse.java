package com.core.dtos.communication;

import com.core.models.enums.SmsProviderType;

import java.time.Instant;

public record SmsProviderConfigResponse(

        String id,

        String orgId,

        SmsProviderType providerType,

        Boolean active,

        Boolean defaultConfig,

        String displayName,

        String senderId,

        String apiBaseUrl,

        Boolean authKeyConfigured,

        String authKeyMasked,

        Boolean apiKeyConfigured,

        String apiKeyMasked,

        Boolean apiSecretConfigured,

        String apiSecretMasked,

        String otpTemplateId,

        String bookingConfirmationTemplateId,

        String dutyAllotmentTemplateId,

        String dutyClosureTemplateId,

        String paymentConfirmationTemplateId,

        String paymentPendingTemplateId,

        String bookingCancellationTemplateId,

        String refundInitiatedTemplateId,

        String refundCompletedTemplateId,

        String dutyReAllotmentTemplateId,

        String dutyReClosureTemplateId,

        Boolean webhookSecretConfigured,

        String webhookSecretMasked,

        String providerSettingsJson,

        Instant createdAt,

        Instant updatedAt,

        String createdBy,

        String updatedBy
) {
}