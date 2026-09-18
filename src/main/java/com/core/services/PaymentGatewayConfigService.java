package com.core.services;

import java.util.List;
import java.util.Optional;

import com.core.config.CacheConfig;
import com.core.dtos.payment.PaymentGatewayConfigRequest;
import com.core.dtos.payment.PaymentGatewayConfigResponse;
import com.core.dtos.payment.PaymentGatewayRuntimeConfig;
import com.core.models.PaymentGatewayConfig;
import com.core.models.enums.PaymentGateway;
import com.core.repositories.PaymentGatewayConfigRepository;
import com.core.services.common.SecretCryptoService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentGatewayConfigService {

    private static final String DEFAULT_CURRENCY = "INR";
    private static final String RAZORPAY_API_BASE_URL = "https://api.razorpay.com/v1";

    private final PaymentGatewayConfigRepository repository;
    private final SecretCryptoService cryptoService;

    @Transactional(readOnly = true)
    public PaymentGatewayConfigResponse getCurrent(String orgId) {
        PaymentGatewayConfig config = repository
                .findFirstByOrgIdAndDefaultConfigTrueOrderByUpdatedAtDesc(orgId)
                .or(() -> repository.findFirstByOrgIdOrderByUpdatedAtDesc(orgId))
                .orElse(null);

        return toResponse(config, orgId);
    }

    @Transactional(readOnly = true)
    public PaymentGatewayConfigResponse getByGateway(String orgId, PaymentGateway gateway) {
        PaymentGateway resolvedGateway = resolveGateway(gateway);

        PaymentGatewayConfig config = repository
                .findByOrgIdAndGateway(orgId, resolvedGateway)
                .orElse(null);

        return toResponse(config, orgId, resolvedGateway);
    }

    /*
     * P1.11 -- an org switching gateway (MOCK <-> RAZORPAY) or rotating a
     * credential must never be served from a stale cache entry, so both
     * runtime-config caches are cleared for this org on every upsert. The
     * by-gateway cache is cleared entirely (allEntries) rather than keyed
     * precisely by (orgId, gateway): upserts are a rare admin action, so the
     * small extra recompute cost elsewhere is worth not hand-rolling a
     * composite-key match against Spring's default key generator.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PAYMENT_GATEWAY_RUNTIME_CONFIG_DEFAULT, key = "#orgId"),
            @CacheEvict(value = CacheConfig.PAYMENT_GATEWAY_RUNTIME_CONFIG_BY_GATEWAY, allEntries = true)
    })
    public PaymentGatewayConfigResponse upsert(String orgId, PaymentGatewayConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment gateway config request cannot be null");
        }

        PaymentGateway gateway = resolveGateway(request.gateway());
        validateConfigurableGateway(gateway);

        PaymentGatewayConfig config = repository
                .findByOrgIdAndGateway(orgId, gateway)
                .orElseGet(() -> {
                    PaymentGatewayConfig created = new PaymentGatewayConfig();
                    created.setOrgId(orgId);
                    created.setGateway(gateway);
                    created.setDefaultConfig(true);
                    created.setActive(true);
                    return created;
                });

        applyRequest(config, request, gateway);
        validateActiveConfig(config);

        PaymentGatewayConfig saved = repository.save(config);

        markOtherConfigsAsNonDefault(orgId, saved.getId());

        return toResponse(saved, orgId);
    }

    /*
     * P1.11 -- backs ClientPaymentController's resolveEffectiveGateway(),
     * called on every checkout initiation and every payment verification.
     * Cached; upsert() above evicts this org's entry immediately on any
     * gateway/credential change.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = CacheConfig.PAYMENT_GATEWAY_RUNTIME_CONFIG_DEFAULT,
            key = "#orgId",
            condition = "#orgId != null and !#orgId.isBlank()",
            sync = true)
    public Optional<PaymentGatewayRuntimeConfig> getRuntimeConfig(String orgId) {
        if (!hasText(orgId)) {
            return Optional.empty();
        }

        return repository
                .findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc(orgId)
                .or(() -> repository.findFirstByOrgIdAndActiveTrueOrderByUpdatedAtDesc(orgId))
                .map(config -> new PaymentGatewayRuntimeConfig(
                        config.getId(),
                        config.getOrgId(),
                        config.getGateway(),
                        config.getActive(),

                        config.getDisplayName(),
                        config.getMerchantName(),
                        config.getCurrency(),
                        config.getCheckoutEnabled(),
                        config.getQrEnabled(),
                        config.getAutoCapture(),

                        config.getApiBaseUrl(),
                        cryptoService.decrypt(config.getKeyIdEncrypted()),
                        cryptoService.decrypt(config.getKeySecretEncrypted()),
                        cryptoService.decrypt(config.getWebhookSecretEncrypted()),
                        config.getProviderSettingsJson()
                ));
    }

    /*
     * P1.11 -- backs RazorpayClientFactory.credentials(), called on every
     * Razorpay operation including the duty-payment QR reconciliation job's
     * poll (see DutyPaymentReconciliationJob). This was the specific
     * uncached hot read the cost/latency audit flagged as most important to
     * fix, since it sits directly on that job's per-check path. Cached;
     * upsert() above evicts on any change.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = CacheConfig.PAYMENT_GATEWAY_RUNTIME_CONFIG_BY_GATEWAY,
            condition = "#orgId != null and !#orgId.isBlank()",
            sync = true)
    public Optional<PaymentGatewayRuntimeConfig> getRuntimeConfig(String orgId, PaymentGateway gateway) {
        if (!hasText(orgId)) {
            return Optional.empty();
        }

        PaymentGateway resolvedGateway = resolveGateway(gateway);

        return repository
                .findByOrgIdAndGateway(orgId, resolvedGateway)
                .filter(config -> Boolean.TRUE.equals(config.getActive()))
                .map(config -> new PaymentGatewayRuntimeConfig(
                        config.getId(),
                        config.getOrgId(),
                        config.getGateway(),
                        config.getActive(),

                        config.getDisplayName(),
                        config.getMerchantName(),
                        config.getCurrency(),
                        config.getCheckoutEnabled(),
                        config.getQrEnabled(),
                        config.getAutoCapture(),

                        config.getApiBaseUrl(),
                        cryptoService.decrypt(config.getKeyIdEncrypted()),
                        cryptoService.decrypt(config.getKeySecretEncrypted()),
                        cryptoService.decrypt(config.getWebhookSecretEncrypted()),
                        config.getProviderSettingsJson()
                ));
    }

    private void applyRequest(
            PaymentGatewayConfig config,
            PaymentGatewayConfigRequest request,
            PaymentGateway gateway
    ) {
        config.setGateway(gateway);
        config.setDefaultConfig(true);

        if (request.active() != null) {
            config.setActive(request.active());
        } else if (config.getActive() == null) {
            config.setActive(true);
        }

        config.setDisplayName(trimToNull(request.displayName()));
        config.setMerchantName(trimToNull(request.merchantName()));
        config.setCurrency(normalizeCurrency(request.currency()));

        if (request.checkoutEnabled() != null) {
            config.setCheckoutEnabled(request.checkoutEnabled());
        } else if (config.getCheckoutEnabled() == null) {
            config.setCheckoutEnabled(true);
        }

        if (request.qrEnabled() != null) {
            config.setQrEnabled(request.qrEnabled());
        } else if (config.getQrEnabled() == null) {
            config.setQrEnabled(true);
        }

        if (request.autoCapture() != null) {
            config.setAutoCapture(request.autoCapture());
        } else if (config.getAutoCapture() == null) {
            config.setAutoCapture(false);
        }

        config.setApiBaseUrl(resolveApiBaseUrl(gateway, request.apiBaseUrl(), config.getApiBaseUrl()));

        if (hasText(request.keyId())) {
            config.setKeyIdEncrypted(cryptoService.encrypt(request.keyId()));
        }

        if (hasText(request.keySecret())) {
            config.setKeySecretEncrypted(cryptoService.encrypt(request.keySecret()));
        }

        if (hasText(request.webhookSecret())) {
            config.setWebhookSecretEncrypted(cryptoService.encrypt(request.webhookSecret()));
        }

        config.setProviderSettingsJson(trimToNull(request.providerSettingsJson()));
    }

    private void validateConfigurableGateway(PaymentGateway gateway) {
        if (gateway == PaymentGateway.MANUAL_ENTRY) {
            throw new IllegalArgumentException("MANUAL_ENTRY does not require payment gateway configuration");
        }
    }

    private void validateActiveConfig(PaymentGatewayConfig config) {
        if (!Boolean.TRUE.equals(config.getActive())) {
            return;
        }

        if (config.getGateway() == PaymentGateway.RAZORPAY) {
            if (!cryptoService.hasSecret(config.getKeyIdEncrypted())
                    || !cryptoService.hasSecret(config.getKeySecretEncrypted())) {
                throw new IllegalArgumentException("Razorpay keyId and keySecret are required when payment gateway config is active");
            }
        }
    }

    private void markOtherConfigsAsNonDefault(String orgId, String activeConfigId) {
        if (!hasText(activeConfigId)) {
            return;
        }

        List<PaymentGatewayConfig> others = repository.findByOrgIdAndIdNot(orgId, activeConfigId);

        for (PaymentGatewayConfig other : others) {
            if (Boolean.TRUE.equals(other.getDefaultConfig())) {
                other.setDefaultConfig(false);
                repository.save(other);
            }
        }
    }

    private PaymentGatewayConfigResponse toResponse(PaymentGatewayConfig config, String orgId) {
        return toResponse(config, orgId, null);
    }

    private PaymentGatewayConfigResponse toResponse(PaymentGatewayConfig config, String orgId, PaymentGateway requestedGateway) {
        if (config == null) {
            return new PaymentGatewayConfigResponse(
                    null,
                    orgId,
                    requestedGateway,
                    false,
                    false,
                    null,
                    null,
                    DEFAULT_CURRENCY,
                    false,
                    false,
                    false,
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
                    null
            );
        }

        return new PaymentGatewayConfigResponse(
                config.getId(),
                config.getOrgId(),
                config.getGateway(),
                config.getActive(),
                config.getDefaultConfig(),
                config.getDisplayName(),
                config.getMerchantName(),
                config.getCurrency(),
                config.getCheckoutEnabled(),
                config.getQrEnabled(),
                config.getAutoCapture(),
                config.getApiBaseUrl(),

                cryptoService.hasSecret(config.getKeyIdEncrypted()),
                cryptoService.masked(config.getKeyIdEncrypted()),

                cryptoService.hasSecret(config.getKeySecretEncrypted()),
                cryptoService.masked(config.getKeySecretEncrypted()),

                cryptoService.hasSecret(config.getWebhookSecretEncrypted()),
                cryptoService.masked(config.getWebhookSecretEncrypted()),

                config.getProviderSettingsJson(),
                config.getCreatedAt(),
                config.getUpdatedAt(),
                config.getCreatedBy(),
                config.getUpdatedBy()
        );
    }

    private PaymentGateway resolveGateway(PaymentGateway gateway) {
        return gateway == null ? PaymentGateway.RAZORPAY : gateway;
    }

    private String resolveApiBaseUrl(PaymentGateway gateway, String requestedApiBaseUrl, String existingApiBaseUrl) {
        if (hasText(requestedApiBaseUrl)) {
            return requestedApiBaseUrl.trim();
        }

        if (hasText(existingApiBaseUrl)) {
            return existingApiBaseUrl.trim();
        }

        if (gateway == PaymentGateway.RAZORPAY) {
            return RAZORPAY_API_BASE_URL;
        }

        return null;
    }

    private String normalizeCurrency(String currency) {
        if (!hasText(currency)) {
            return DEFAULT_CURRENCY;
        }

        return currency.trim().toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}