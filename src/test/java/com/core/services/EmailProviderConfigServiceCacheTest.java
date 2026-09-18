package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.core.config.CacheConfig;
import com.core.dtos.communication.EmailProviderConfigRequest;
import com.core.dtos.communication.EmailProviderRuntimeConfig;
import com.core.models.EmailProviderConfig;
import com.core.models.enums.EmailProviderType;
import com.core.repositories.EmailProviderConfigRepository;
import com.core.services.common.SecretCryptoService;

/*
 * P1.11-followup -- proves the email provider config cache (added to stop a
 * DB hit + 5-secret-decrypt on every ZohoMailService.send() call, across all
 * 9 notification types) behaves correctly under Spring's real caching proxy,
 * not just as plain unmocked business logic. Mirrors
 * SmsProviderConfigServiceCacheTest's structure exactly -- same gap, same
 * fix, same verification shape.
 */
class EmailProviderConfigServiceCacheTest {

	private EmailProviderConfigRepository repository;
	private SecretCryptoService cryptoService;
	private AnnotationConfigApplicationContext context;
	private EmailProviderConfigService service;

	@BeforeEach
	void setUp() {
		repository = mock(EmailProviderConfigRepository.class);
		cryptoService = mock(SecretCryptoService.class);
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		context = new AnnotationConfigApplicationContext();
		context.register(CacheConfig.class);
		context.registerBean(EmailProviderConfigRepository.class, () -> repository);
		context.registerBean(SecretCryptoService.class, () -> cryptoService);
		context.registerBean(EmailProviderConfigService.class,
				() -> new EmailProviderConfigService(repository, cryptoService));
		context.refresh();

		service = context.getBean(EmailProviderConfigService.class);
	}

	@AfterEach
	void tearDown() {
		context.close();
	}

	private EmailProviderConfig config(String orgId) {
		EmailProviderConfig c = new EmailProviderConfig();
		c.setId("cfg-" + orgId);
		c.setOrgId(orgId);
		c.setActive(true);
		c.setDefaultConfig(true);
		c.setProviderType(EmailProviderType.ZEPTO_MAIL);
		return c;
	}

	private EmailProviderConfigRequest updateRequest(String displayName) {
		return new EmailProviderConfigRequest(
				EmailProviderType.ZEPTO_MAIL, // providerType
				true,                         // active
				displayName,                  // displayName
				null,                         // senderName
				null,                         // senderEmail
				null,                         // replyToEmail
				null,                         // apiBaseUrl
				null,                         // apiKey
				null,                         // apiSecret
				null,                         // smtpHost
				null,                         // smtpPort
				null,                         // smtpUsername
				null,                         // smtpPassword
				null,                         // smtpUseTls
				null,                         // smtpUseSsl
				null,                         // webhookSecret
				null);                        // providerSettingsJson
	}

	@Test
	void firstRead_hitsRepository() {
		when(repository.findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-1"))
				.thenReturn(Optional.of(config("org-1")));

		service.getRuntimeConfig("org-1");

		verify(repository, times(1)).findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-1");
	}

	@Test
	void repeatedReads_doNotHitRepositoryAgain() {
		when(repository.findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-1"))
				.thenReturn(Optional.of(config("org-1")));

		service.getRuntimeConfig("org-1");
		service.getRuntimeConfig("org-1");
		service.getRuntimeConfig("org-1");

		verify(repository, times(1)).findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-1");
	}

	@Test
	void differentOrgs_doNotShareCachedConfig() {
		when(repository.findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-1"))
				.thenReturn(Optional.of(config("org-1")));
		when(repository.findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-2"))
				.thenReturn(Optional.of(config("org-2")));

		Optional<EmailProviderRuntimeConfig> a = service.getRuntimeConfig("org-1");
		Optional<EmailProviderRuntimeConfig> b = service.getRuntimeConfig("org-2");

		assertEquals("org-1", a.orElseThrow().orgId());
		assertEquals("org-2", b.orElseThrow().orgId());
		verify(repository, times(1)).findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-1");
		verify(repository, times(1)).findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-2");
	}

	@Test
	void upsert_invalidatesCache_soTheNextReadSeesTheUpdatedValue() {
		EmailProviderConfig existing = config("org-1");
		when(repository.findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-1"))
				.thenReturn(Optional.of(existing));
		when(repository.findFirstByOrgIdAndDefaultConfigTrueOrderByUpdatedAtDesc("org-1"))
				.thenReturn(Optional.of(existing));

		service.getRuntimeConfig("org-1"); // populate the cache
		service.upsert("org-1", updateRequest("Updated Display Name"));
		service.getRuntimeConfig("org-1"); // must re-read, not return the stale cached copy

		verify(repository, times(2)).findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-1");
	}

	@Test
	void applicationRemainsFunctional_whenNothingIsCachedYet() {
		when(repository.findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-cold"))
				.thenReturn(Optional.empty());

		Optional<EmailProviderRuntimeConfig> result = service.getRuntimeConfig("org-cold");

		assertEquals(Optional.empty(), result);
	}

	@Test
	void blankOrgId_isNeverCached_andNeverQueriesTheRepository() {
		service.getRuntimeConfig("");
		service.getRuntimeConfig("");

		verify(repository, never()).findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc(anyString());
	}

	@Test
	void concurrentReads_forTheSameOrg_areThreadSafe_andStillOnlyHitRepositoryOnce() throws Exception {
		when(repository.findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-1"))
				.thenReturn(Optional.of(config("org-1")));

		int threadCount = 20;
		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		CountDownLatch ready = new CountDownLatch(threadCount);
		CountDownLatch go = new CountDownLatch(1);
		AtomicInteger failures = new AtomicInteger(0);

		List<Runnable> tasks = java.util.stream.IntStream.range(0, threadCount)
				.<Runnable>mapToObj(i -> () -> {
					ready.countDown();
					try {
						go.await(5, TimeUnit.SECONDS);
						service.getRuntimeConfig("org-1");
					} catch (Exception e) {
						failures.incrementAndGet();
					}
				})
				.toList();

		tasks.forEach(pool::execute);
		ready.await(5, TimeUnit.SECONDS);
		go.countDown();
		pool.shutdown();
		pool.awaitTermination(5, TimeUnit.SECONDS);

		assertEquals(0, failures.get());
		// @Cacheable(sync = true) makes concurrent callers block behind the
		// one thread populating the cache, instead of every thread racing
		// its own DB read on a cold cache.
		verify(repository, times(1)).findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-1");
	}
}
