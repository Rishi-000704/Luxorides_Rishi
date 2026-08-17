package com.core.services;

import java.util.List;

import com.core.dtos.communication.EmailProviderConfigRequest;
import com.core.dtos.communication.EmailProviderConfigResponse;
import com.core.models.EmailProviderConfig;
import com.core.models.enums.EmailProviderType;
import com.core.repositories.EmailProviderConfigRepository;
import com.core.services.common.SecretCryptoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import com.core.dtos.communication.EmailProviderRuntimeConfig;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailProviderConfigService {

    private final EmailProviderConfigRepository repository;
    private final SecretCryptoService cryptoService;

    @Transactional(readOnly = true)
    public EmailProviderConfigResponse getCurrent(String orgId) {
        EmailProviderConfig config = repository
                .findFirstByOrgIdAndDefaultConfigTrueOrderByUpdatedAtDesc(orgId)
                .or(() -> repository.findFirstByOrgIdOrderByUpdatedAtDesc(orgId))
                .orElse(null);

        return toResponse(config, orgId);
    }

    @Transactional
    public EmailProviderConfigResponse upsert(String orgId, EmailProviderConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Email provider config request cannot be null");
        }

        EmailProviderType providerType = request.providerType() == null
                ? EmailProviderType.ZEPTO_MAIL
                : request.providerType();

        EmailProviderConfig config = repository
                .findFirstByOrgIdAndDefaultConfigTrueOrderByUpdatedAtDesc(orgId)
                .or(() -> repository.findByOrgIdAndProviderType(orgId, providerType))
                .or(() -> repository.findFirstByOrgIdOrderByUpdatedAtDesc(orgId))
                .orElseGet(() -> {
                    EmailProviderConfig created = new EmailProviderConfig();
                    created.setOrgId(orgId);
                    created.setProviderType(providerType);
                    created.setDefaultConfig(true);
                    created.setActive(true);
                    return created;
                });

        applyRequest(config, request, providerType);

        EmailProviderConfig saved = repository.save(config);

        markOtherConfigsAsNonDefault(orgId, saved.getId());

        return toResponse(saved, orgId);
    }

    @Transactional(readOnly = true)
    public Optional<EmailProviderRuntimeConfig> getRuntimeConfig(String orgId) {
        if (!hasText(orgId)) {
            return Optional.empty();
        }

        return repository
                .findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc(orgId)
                .or(() -> repository.findFirstByOrgIdAndActiveTrueOrderByUpdatedAtDesc(orgId))
                .map(config -> new EmailProviderRuntimeConfig(
                        config.getId(),
                        config.getOrgId(),
                        config.getProviderType(),
                        config.getActive(),

                        config.getDisplayName(),
                        config.getSenderName(),
                        config.getSenderEmail(),
                        config.getReplyToEmail(),

                        config.getApiBaseUrl(),
                        cryptoService.decrypt(config.getApiKeyEncrypted()),
                        cryptoService.decrypt(config.getApiSecretEncrypted()),

                        config.getSmtpHost(),
                        config.getSmtpPort(),
                        cryptoService.decrypt(config.getSmtpUsernameEncrypted()),
                        cryptoService.decrypt(config.getSmtpPasswordEncrypted()),
                        config.getSmtpUseTls(),
                        config.getSmtpUseSsl(),

                        cryptoService.decrypt(config.getWebhookSecretEncrypted()),
                        config.getProviderSettingsJson()
                ));
    }

    private void applyRequest(
            EmailProviderConfig config,
            EmailProviderConfigRequest request,
            EmailProviderType providerType
    ) {
        config.setProviderType(providerType);
        config.setDefaultConfig(true);

        if (request.active() != null) {
            config.setActive(request.active());
        } else if (config.getActive() == null) {
            config.setActive(true);
        }

        config.setDisplayName(trimToNull(request.displayName()));
        config.setSenderName(trimToNull(request.senderName()));
        config.setSenderEmail(trimToNull(request.senderEmail()));
        config.setReplyToEmail(trimToNull(request.replyToEmail()));
        config.setApiBaseUrl(trimToNull(request.apiBaseUrl()));

        if (hasText(request.apiKey())) {
            config.setApiKeyEncrypted(cryptoService.encrypt(request.apiKey()));
        }

        if (hasText(request.apiSecret())) {
            config.setApiSecretEncrypted(cryptoService.encrypt(request.apiSecret()));
        }

        config.setSmtpHost(trimToNull(request.smtpHost()));
        config.setSmtpPort(request.smtpPort());

        if (hasText(request.smtpUsername())) {
            config.setSmtpUsernameEncrypted(cryptoService.encrypt(request.smtpUsername()));
        }

        if (hasText(request.smtpPassword())) {
            config.setSmtpPasswordEncrypted(cryptoService.encrypt(request.smtpPassword()));
        }

        if (request.smtpUseTls() != null) {
            config.setSmtpUseTls(request.smtpUseTls());
        }

        if (request.smtpUseSsl() != null) {
            config.setSmtpUseSsl(request.smtpUseSsl());
        }

        if (hasText(request.webhookSecret())) {
            config.setWebhookSecretEncrypted(cryptoService.encrypt(request.webhookSecret()));
        }

        config.setProviderSettingsJson(trimToNull(request.providerSettingsJson()));
    }

    private void markOtherConfigsAsNonDefault(String orgId, String activeConfigId) {
        if (!hasText(activeConfigId)) {
            return;
        }

        List<EmailProviderConfig> others = repository.findByOrgIdAndIdNot(orgId, activeConfigId);

        for (EmailProviderConfig other : others) {
            if (Boolean.TRUE.equals(other.getDefaultConfig())) {
                other.setDefaultConfig(false);
                repository.save(other);
            }
        }
    }

    private EmailProviderConfigResponse toResponse(EmailProviderConfig config, String orgId) {
        if (config == null) {
            return new EmailProviderConfigResponse(
                    null,
                    orgId,
                    null,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    false,
                    null,
                    null,
                    null,
                    false,
                    null,
                    false,
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

        return new EmailProviderConfigResponse(
                config.getId(),
                config.getOrgId(),
                config.getProviderType(),
                config.getActive(),
                config.getDefaultConfig(),
                config.getDisplayName(),
                config.getSenderName(),
                config.getSenderEmail(),
                config.getReplyToEmail(),
                config.getApiBaseUrl(),

                cryptoService.hasSecret(config.getApiKeyEncrypted()),
                cryptoService.masked(config.getApiKeyEncrypted()),

                cryptoService.hasSecret(config.getApiSecretEncrypted()),
                cryptoService.masked(config.getApiSecretEncrypted()),

                config.getSmtpHost(),
                config.getSmtpPort(),

                cryptoService.hasSecret(config.getSmtpUsernameEncrypted()),
                cryptoService.masked(config.getSmtpUsernameEncrypted()),

                cryptoService.hasSecret(config.getSmtpPasswordEncrypted()),
                cryptoService.masked(config.getSmtpPasswordEncrypted()),

                config.getSmtpUseTls(),
                config.getSmtpUseSsl(),

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
