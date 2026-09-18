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
import com.core.dtos.communication.SmsProviderConfigRequest;
import com.core.dtos.communication.SmsProviderRuntimeConfig;
import com.core.models.SmsProviderConfig;
import com.core.models.enums.SmsProviderType;
import com.core.repositories.SmsProviderConfigRepository;
import com.core.services.common.SecretCryptoService;

/*
 * P1.11 -- proves the SMS provider config cache (added to stop a DB hit +
 * secret-decrypt on every single SMS send) actually behaves correctly under
 * Spring's real caching proxy, not just as plain unmocked business logic.
 * Uses a minimal plain AnnotationConfigApplicationContext (CacheConfig +
 * this one service) rather than @SpringBootTest, since the full app context
 * needs a real DB connection that isn't available in this environment --
 * see CoreApplicationTests' known, unrelated, env-only failure.
 */
class SmsProviderConfigServiceCacheTest {

	private SmsProviderConfigRepository repository;
	private SecretCryptoService cryptoService;
	private AnnotationConfigApplicationContext context;
	private SmsProviderConfigService service;

	@BeforeEach
	void setUp() {
		repository = mock(SmsProviderConfigRepository.class);
		cryptoService = mock(SecretCryptoService.class);
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		context = new AnnotationConfigApplicationContext();
		context.register(CacheConfig.class);
		context.registerBean(SmsProviderConfigRepository.class, () -> repository);
		context.registerBean(SecretCryptoService.class, () -> cryptoService);
		context.registerBean(SmsProviderConfigService.class,
				() -> new SmsProviderConfigService(repository, cryptoService));
		context.refresh();

		service = context.getBean(SmsProviderConfigService.class);
	}

	@AfterEach
	void tearDown() {
		context.close();
	}

	private SmsProviderConfig config(String orgId) {
		SmsProviderConfig c = new SmsProviderConfig();
		c.setId("cfg-" + orgId);
		c.setOrgId(orgId);
		c.setActive(true);
		c.setDefaultConfig(true);
		c.setProviderType(SmsProviderType.MSG91);
		return c;
	}

	private SmsProviderConfigRequest updateRequest(String displayName) {
		return new SmsProviderConfigRequest(
				SmsProviderType.MSG91, // providerType
				true,                  // active
				displayName,           // displayName
				null,                  // senderId
				null,                  // apiBaseUrl
				null,                  // authKey
				null,                  // apiKey
				null,                  // apiSecret
				null,                  // otpTemplateId
				null,                  // bookingConfirmationTemplateId
				null,                  // dutyAllotmentTemplateId
				null,                  // dutyClosureTemplateId
				null,                  // paymentConfirmationTemplateId
				null,                  // paymentPendingTemplateId
				null,                  // bookingCancellationTemplateId
				null,                  // refundInitiatedTemplateId
				null,                  // refundCompletedTemplateId
				null,                  // dutyReAllotmentTemplateId
				null,                  // dutyReClosureTemplateId
				null,                  // webhookSecret
				null);                 // providerSettingsJson
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

		Optional<SmsProviderRuntimeConfig> a = service.getRuntimeConfig("org-1");
		Optional<SmsProviderRuntimeConfig> b = service.getRuntimeConfig("org-2");

		assertEquals("org-1", a.orElseThrow().orgId());
		assertEquals("org-2", b.orElseThrow().orgId());
		verify(repository, times(1)).findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-1");
		verify(repository, times(1)).findFirstByOrgIdAndActiveTrueAndDefaultConfigTrueOrderByUpdatedAtDesc("org-2");
	}

	@Test
	void upsert_invalidatesCache_soTheNextReadSeesTheUpdatedValue() {
		SmsProviderConfig existing = config("org-1");
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

		Optional<SmsProviderRuntimeConfig> result = service.getRuntimeConfig("org-cold");

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
