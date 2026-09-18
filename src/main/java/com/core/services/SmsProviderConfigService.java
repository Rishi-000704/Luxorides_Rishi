package com.core.services;

import java.util.List;

import com.core.config.CacheConfig;
import com.core.dtos.communication.SmsProviderConfigRequest;
import com.core.dtos.communication.SmsProviderConfigResponse;
import com.core.models.SmsProviderConfig;
import com.core.models.enums.SmsProviderType;
import com.core.repositories.SmsProviderConfigRepository;
import com.core.services.common.SecretCryptoService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import com.core.dtos.communication.SmsProviderRuntimeConfig;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SmsProviderConfigService {

    private final SmsProviderConfigRepository repository;
    private final SecretCryptoService cryptoService;

    @Transactional(readOnly = true)
    public SmsProviderConfigResponse getCurrent(String orgId) {
        SmsProviderConfig config = repository
                .findFirstByOrgIdAndDefaultConfigTrueOrderByUpdatedAtDesc(orgId)
                .or(() -> repository.findFirstByOrgIdOrderByUpdatedAtDesc(orgId))
                .orElse(null);

        return toResponse(config, orgId);
    }

    @Transactional
    @SuppressWarnings("null")
    @CacheEvict(value = CacheConfig.SMS_PROVIDER_RUNTIME_CONFIG, key = "#orgId")
    public SmsProviderConfigResponse upsert(String orgId, SmsProviderConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("SMS provider config request cannot be null");
        }

        SmsProviderType providerType = request.providerType() == null
                ? SmsProviderType.MSG91
                : request.providerType();

        SmsProviderConfig config = repository
                .findFirstByOrgIdAndDefaultConfigTrueOrderByUpdatedAtDesc(orgId)
                .or(() -> repository.findByOrgIdAndProviderType(orgId, providerType))
                .or(() -> repository.findFirstByOrgIdOrderByUpdatedAtDesc(orgId))
                .orElseGet(() -> {
                    SmsProviderConfig created = new SmsProviderConfig();
                    created.setOrgId(orgId);
                    created.setProviderType(providerType);
                    created.setDefaultConfig(true);
                    created.setActive(true);
                    return created;
                });

        applyRequest(config, request, providerType);

        SmsProviderConfig saved = repository.save(config);

        markOtherConfigsAsNonDefault(orgId, saved.getId());

        return toResponse(saved, orgId);
    }

    /*
     * P1.11 -- called on every SMS send (see SMSService), so this is one of
     * the hottest, most rarely-changing DB reads in the app (org config that
     * only changes when an admin edits SMS provider settings). Cached here;
     * upsert() above evicts this org's entry immediately on any change, so a
     * config edit is never served stale beyond the current request.
     * sync = true so a cold-cache stampede (many concurrent SMS sends for
     * the same org racing the very first lookup) blocks behind one DB read
     * instead of every concurrent caller hitting the DB independently.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = CacheConfig.SMS_PROVIDER_RUNTIME_CONFIG,
            key = "#orgId",
            condition = "#orgId != null and !#orgId.isBlank()",
            sync = true)
    public Optional<SmsProviderRuntimeConfig> getRuntimeConfig(String orgId) {
        if (!hasText(orgId)) {
            return Optional.empty();
        }

        return repository
                .findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc(orgId)
                .or(() -> repository.findFirstByOrgIdAndActiveTrueOrderByUpdatedAtDesc(orgId))
                .map(config -> new SmsProviderRuntimeConfig(
                        config.getId(),
                        config.getOrgId(),
                        config.getProviderType(),
                        config.getActive(),

                        config.getDisplayName(),
                        config.getSenderId(),
                        config.getApiBaseUrl(),

                        cryptoService.decrypt(config.getAuthKeyEncrypted()),
                        cryptoService.decrypt(config.getApiKeyEncrypted()),
                        cryptoService.decrypt(config.getApiSecretEncrypted()),

                        config.getOtpTemplateId(),
                        config.getBookingConfirmationTemplateId(),
                        config.getDutyAllotmentTemplateId(),
                        config.getDutyClosureTemplateId(),
                        config.getPaymentConfirmationTemplateId(),
                        config.getPaymentPendingTemplateId(),
                        config.getBookingCancellationTemplateId(),
                        config.getRefundInitiatedTemplateId(),
                        config.getRefundCompletedTemplateId(),
                        config.getDutyReAllotmentTemplateId(),
                        config.getDutyReClosureTemplateId(),

                        cryptoService.decrypt(config.getWebhookSecretEncrypted()),
                        config.getProviderSettingsJson()
                ));
    }

    private void applyRequest(
            SmsProviderConfig config,
            SmsProviderConfigRequest request,
            SmsProviderType providerType
    ) {
        config.setProviderType(providerType);
        config.setDefaultConfig(true);

        if (request.active() != null) {
            config.setActive(request.active());
        } else if (config.getActive() == null) {
            config.setActive(true);
        }

        config.setDisplayName(trimToNull(request.displayName()));
        config.setSenderId(trimToNull(request.senderId()));
        config.setApiBaseUrl(trimToNull(request.apiBaseUrl()));

        if (hasText(request.authKey())) {
            config.setAuthKeyEncrypted(cryptoService.encrypt(request.authKey()));
        }

        if (hasText(request.apiKey())) {
            config.setApiKeyEncrypted(cryptoService.encrypt(request.apiKey()));
        }

        if (hasText(request.apiSecret())) {
            config.setApiSecretEncrypted(cryptoService.encrypt(request.apiSecret()));
        }

        config.setOtpTemplateId(trimToNull(request.otpTemplateId()));
        config.setBookingConfirmationTemplateId(trimToNull(request.bookingConfirmationTemplateId()));
        config.setDutyAllotmentTemplateId(trimToNull(request.dutyAllotmentTemplateId()));
        config.setDutyClosureTemplateId(trimToNull(request.dutyClosureTemplateId()));
        config.setPaymentConfirmationTemplateId(trimToNull(request.paymentConfirmationTemplateId()));
        config.setPaymentPendingTemplateId(trimToNull(request.paymentPendingTemplateId()));
        config.setBookingCancellationTemplateId(trimToNull(request.bookingCancellationTemplateId()));
        config.setRefundInitiatedTemplateId(trimToNull(request.refundInitiatedTemplateId()));
        config.setRefundCompletedTemplateId(trimToNull(request.refundCompletedTemplateId()));
        config.setDutyReAllotmentTemplateId(trimToNull(request.dutyReAllotmentTemplateId()));
        config.setDutyReClosureTemplateId(trimToNull(request.dutyReClosureTemplateId()));

        if (hasText(request.webhookSecret())) {
            config.setWebhookSecretEncrypted(cryptoService.encrypt(request.webhookSecret()));
        }

        config.setProviderSettingsJson(trimToNull(request.providerSettingsJson()));
    }

    private void markOtherConfigsAsNonDefault(String orgId, String activeConfigId) {
        if (!hasText(activeConfigId)) {
            return;
        }

        List<SmsProviderConfig> others = repository.findByOrgIdAndIdNot(orgId, activeConfigId);

        for (SmsProviderConfig other : others) {
            if (Boolean.TRUE.equals(other.getDefaultConfig())) {
                other.setDefaultConfig(false);
                repository.save(other);
            }
        }
    }

    private SmsProviderConfigResponse toResponse(SmsProviderConfig config, String orgId) {
        if (config == null) {
            return new SmsProviderConfigResponse(
                    null,
                    orgId,
                    null,
                    false,
                    false,
                    null,
                    null,
                    null,
                    false,
                    null,
                    false,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        return new SmsProviderConfigResponse(
                config.getId(),
                config.getOrgId(),
                config.getProviderType(),
                config.getActive(),
                config.getDefaultConfig(),
                config.getDisplayName(),
                config.getSenderId(),
                config.getApiBaseUrl(),

                cryptoService.hasSecret(config.getAuthKeyEncrypted()),
                cryptoService.masked(config.getAuthKeyEncrypted()),

                cryptoService.hasSecret(config.getApiKeyEncrypted()),
                cryptoService.masked(config.getApiKeyEncrypted()),

                cryptoService.hasSecret(config.getApiSecretEncrypted()),
                cryptoService.masked(config.getApiSecretEncrypted()),

                config.getOtpTemplateId(),
                config.getBookingConfirmationTemplateId(),
                config.getDutyAllotmentTemplateId(),
                config.getDutyClosureTemplateId(),
                config.getPaymentConfirmationTemplateId(),
                config.getPaymentPendingTemplateId(),
                config.getBookingCancellationTemplateId(),
                config.getRefundInitiatedTemplateId(),
                config.getRefundCompletedTemplateId(),
                config.getDutyReAllotmentTemplateId(),
                config.getDutyReClosureTemplateId(),

                cryptoService.hasSecret(config.getWebhookSecretEncrypted()),
                cryptoService.masked(config.getWebhookSecretEncrypted()),

                config.getProviderSettingsJson(),
                config.getCreatedAt(),
                config.getUpdatedAt(),
                config.getCreatedBy(),
                config.getUpdatedBy()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
