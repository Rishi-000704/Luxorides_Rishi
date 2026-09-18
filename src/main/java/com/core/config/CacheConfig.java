package com.core.config;

import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/*
 * P1.11 -- targeted cache for hot, rarely-changing per-org config reads
 * identified in the cost/latency audit: SmsProviderConfigService,
 * PaymentGatewayConfigService, and EmailProviderConfigService's
 * getRuntimeConfig() methods, all of which hit the DB (and decrypt several
 * secrets) on every SMS send / every payment operation / every notification
 * email, including the duty-payment reconciliation job's poll. This is
 * deliberately NOT a general-purpose @Cacheable layer across the app -- only
 * these specific methods are annotated (see the services themselves).
 *
 * Email config was added in a follow-up pass after the initial audit missed
 * it -- ZohoMailService.send() calls getRuntimeConfig() on every one of 9
 * notification types (booking confirm/cancel, payment pending/confirmed,
 * refund initiated/completed, duty allotment/re-allotment/closure/
 * re-closure), same waste shape as the SMS/payment reads already fixed.
 *
 * Caffeine (in-process, bounded, single JVM) rather than Redis: this backend
 * currently runs as a single container with no load balancer / multi-instance
 * config anywhere in the repo (see Dockerfile), so there is no cross-instance
 * cache-consistency problem to solve yet. If the deployment ever becomes
 * multi-instance, these three caches (small, config-only, cheap to
 * recompute on a miss) are the least risky thing to move to a shared cache --
 * nothing here assumes single-instance in a way that would make that move
 * unsafe later.
 *
 * TTL is a bounded safety net, not the primary invalidation mechanism: both
 * services explicitly evict on their own upsert() (see @CacheEvict there),
 * so a config change -- including an org switching MOCK <-> RAZORPAY -- is
 * picked up on the very next call, not after the TTL expires.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SMS_PROVIDER_RUNTIME_CONFIG = "smsProviderRuntimeConfig";
    public static final String PAYMENT_GATEWAY_RUNTIME_CONFIG_DEFAULT = "paymentGatewayRuntimeConfigDefault";
    public static final String PAYMENT_GATEWAY_RUNTIME_CONFIG_BY_GATEWAY = "paymentGatewayRuntimeConfigByGateway";
    public static final String EMAIL_PROVIDER_RUNTIME_CONFIG = "emailProviderRuntimeConfig";

    // Bounds each cache independently -- generous for any realistic org (and
    // org+gateway pair) count, small enough to never be a memory concern.
    private static final int MAX_ENTRIES_PER_CACHE = 2_000;
    private static final int TTL_MINUTES = 5;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                SMS_PROVIDER_RUNTIME_CONFIG,
                PAYMENT_GATEWAY_RUNTIME_CONFIG_DEFAULT,
                PAYMENT_GATEWAY_RUNTIME_CONFIG_BY_GATEWAY,
                EMAIL_PROVIDER_RUNTIME_CONFIG);

        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(MAX_ENTRIES_PER_CACHE)
                .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES));

        return manager;
    }
}
