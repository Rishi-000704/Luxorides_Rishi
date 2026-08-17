package com.core.dtos.communication;

import com.core.models.enums.SmsProviderType;

public record SmsProviderRuntimeConfig(
        String id,
        String orgId,
        SmsProviderType providerType,
        Boolean active,

        String displayName,
        String senderId,
        String apiBaseUrl,

        String authKey,
        String apiKey,
        String apiSecret,

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

        String webhookSecret,
        String providerSettingsJson
) {
}