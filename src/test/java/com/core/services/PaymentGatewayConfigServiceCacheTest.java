package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.core.config.CacheConfig;
import com.core.dtos.payment.PaymentGatewayConfigRequest;
import com.core.dtos.payment.PaymentGatewayRuntimeConfig;
import com.core.models.PaymentGatewayConfig;
import com.core.models.enums.PaymentGateway;
import com.core.repositories.PaymentGatewayConfigRepository;
import com.core.services.common.SecretCryptoService;

/*
 * P1.11 -- proves the Razorpay/payment-gateway config cache (the one on
 * RazorpayClientFactory.credentials()'s path, called on every payment
 * operation including the QR reconciliation job's poll) behaves correctly
 * under Spring's real caching proxy. Same minimal-context approach as
 * SmsProviderConfigServiceCacheTest -- see its header comment for why this
 * doesn't use @SpringBootTest.
 */
class PaymentGatewayConfigServiceCacheTest {

	private PaymentGatewayConfigRepository repository;
	private SecretCryptoService cryptoService;
	private AnnotationConfigApplicationContext context;
	private PaymentGatewayConfigService service;

	@BeforeEach
	void setUp() {
		repository = mock(PaymentGatewayConfigRepository.class);
		cryptoService = mock(SecretCryptoService.class);
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(cryptoService.hasSecret(any())).thenReturn(true);

		context = new AnnotationConfigApplicationContext();
		context.register(CacheConfig.class);
		context.registerBean(PaymentGatewayConfigRepository.class, () -> repository);
		context.registerBean(SecretCryptoService.class, () -> cryptoService);
		context.registerBean(PaymentGatewayConfigService.class,
				() -> new PaymentGatewayConfigService(repository, cryptoService));
		context.refresh();

		service = context.getBean(PaymentGatewayConfigService.class);
	}

	@AfterEach
	void tearDown() {
		context.close();
	}

	private PaymentGatewayConfig config(String orgId, PaymentGateway gateway) {
		PaymentGatewayConfig c = new PaymentGatewayConfig();
		c.setId("cfg-" + orgId + "-" + gateway);
		c.setOrgId(orgId);
		c.setGateway(gateway);
		c.setActive(true);
		c.setDefaultConfig(true);
		c.setKeyIdEncrypted("enc-key-id");
		c.setKeySecretEncrypted("enc-key-secret");
		return c;
	}

	private PaymentGatewayConfigRequest updateRequest(String displayName) {
		return new PaymentGatewayConfigRequest(
				PaymentGateway.RAZORPAY, // gateway
				true,                    // active
				displayName,             // displayName
				null,                    // merchantName
				null,                    // currency
				null,                    // checkoutEnabled
				null,                    // qrEnabled
				null,                    // autoCapture
				null,                    // apiBaseUrl
				null,                    // keyId
				null,                    // keySecret
				null,                    // webhookSecret
				null);                   // providerSettingsJson
	}

	@Test
	void firstRead_hitsRepository() {
		when(repository.findByOrgIdAndGateway("org-1", PaymentGateway.RAZORPAY))
				.thenReturn(Optional.of(config("org-1", PaymentGateway.RAZORPAY)));

		service.getRuntimeConfig("org-1", PaymentGateway.RAZORPAY);

		verify(repository, times(1)).findByOrgIdAndGateway("org-1", PaymentGateway.RAZORPAY);
	}

	@Test
	void repeatedReads_doNotHitRepositoryAgain() {
		when(repository.findByOrgIdAndGateway("org-1", PaymentGateway.RAZORPAY))
				.thenReturn(Optional.of(config("org-1", PaymentGateway.RAZORPAY)));

		service.getRuntimeConfig("org-1", PaymentGateway.RAZORPAY);
		service.getRuntimeConfig("org-1", PaymentGateway.RAZORPAY);
		service.getRuntimeConfig("org-1", PaymentGateway.RAZORPAY);

		verify(repository, times(1)).findByOrgIdAndGateway("org-1", PaymentGateway.RAZORPAY);
	}

	@Test
	void differentOrgs_doNotShareCachedConfig() {
		when(repository.findByOrgIdAndGateway("org-1", PaymentGateway.RAZORPAY))
				.thenReturn(Optional.of(config("org-1", PaymentGateway.RAZORPAY)));
		when(repository.findByOrgIdAndGateway("org-2", PaymentGateway.RAZORPAY))
				.thenReturn(Optional.of(config("org-2", PaymentGateway.RAZORPAY)));

		Optional<PaymentGatewayRuntimeConfig> a = service.getRuntimeConfig("org-1", PaymentGateway.RAZORPAY);
		Optional<PaymentGatewayRuntimeConfig> b = service.getRuntimeConfig("org-2", PaymentGateway.RAZORPAY);

		assertEquals("org-1", a.orElseThrow().orgId());
		assertEquals("org-2", b.orElseThrow().orgId());
		verify(repository, times(1)).findByOrgIdAndGateway("org-1", PaymentGateway.RAZORPAY);
		verify(repository, times(1)).findByOrgIdAndGateway("org-2", PaymentGateway.RAZORPAY);
	}

	@Test
	void differentGateways_forTheSameOrg_areCachedSeparately() {
		when(repository.findByOrgIdAndGateway("org-1", PaymentGateway.RAZORPAY))
				.thenReturn(Optional.of(config("org-1", PaymentGateway.RAZORPAY)));
		when(repository.findByOrgIdAndGateway("org-1", PaymentGateway.MOCK))
				.thenReturn(Optional.of(config("org-1", PaymentGateway.MOCK)));

		Optional<PaymentGatewayRuntimeConfig> razorpay = service.getRuntimeConfig("org-1", PaymentGateway.RAZORPAY);
		Optional<PaymentGatewayRuntimeConfig> mock = service.getRuntimeConfig("org-1", PaymentGateway.MOCK);

		assertEquals(PaymentGateway.RAZORPAY, razorpay.orElseThrow().gateway());
		assertEquals(PaymentGateway.MOCK, mock.orElseThrow().gateway());
	}

	@Test
	void gatewaySwitch_viaUpsert_isVisibleOnTheNextRead_notTheOldCachedValue() {
		PaymentGatewayConfig existing = config("org-1", PaymentGateway.RAZORPAY);
		when(repository.findByOrgIdAndGateway("org-1", PaymentGateway.RAZORPAY)).thenReturn(Optional.of(existing));

		service.getRuntimeConfig("org-1", PaymentGateway.RAZORPAY); // populate cache

		service.upsert("org-1", updateRequest("Updated"));

		service.getRuntimeConfig("org-1", PaymentGateway.RAZORPAY); // must not be the stale cached copy

		// 1st getRuntimeConfig (populates cache) + upsert()'s own internal
		// lookup (always uncached -- it needs the authoritative current row
		// to update) + the 2nd getRuntimeConfig, which must re-hit the DB
		// rather than return the evicted cache's stale copy.
		verify(repository, times(3)).findByOrgIdAndGateway("org-1", PaymentGateway.RAZORPAY);
	}

	@Test
	void applicationRemainsFunctional_whenNothingIsCachedYet() {
		when(repository.findByOrgIdAndGateway("org-cold", PaymentGateway.RAZORPAY)).thenReturn(Optional.empty());

		Optional<PaymentGatewayRuntimeConfig> result = service.getRuntimeConfig("org-cold", PaymentGateway.RAZORPAY);

		assertEquals(Optional.empty(), result);
	}

	@Test
	void blankOrgId_isNeverCached_andNeverQueriesTheRepository() {
		service.getRuntimeConfig("", PaymentGateway.RAZORPAY);
		service.getRuntimeConfig("", PaymentGateway.RAZORPAY);

		verify(repository, never()).findByOrgIdAndGateway(anyString(), any());
	}
}
