package com.core.services.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.dtos.communication.SmsProviderRuntimeConfig;
import com.core.models.enums.SmsProviderType;
import com.core.services.SmsProviderConfigService;

class SMSServiceTest {

	private SmsProviderConfigService smsProviderConfigService;
	private Msg91SmsService msg91SmsService;
	private SMSService smsService;

	@BeforeEach
	void setUp() {
		smsProviderConfigService = mock(SmsProviderConfigService.class);
		msg91SmsService = mock(Msg91SmsService.class);
		smsService = new SMSService(smsProviderConfigService, msg91SmsService);
	}

	@Test
	void consoleProviderLogsInsteadOfCallingARealGatewayAndReportsSuccess() {
		SmsProviderRuntimeConfig config = consoleConfig("demo");
		when(smsProviderConfigService.getRuntimeConfig("demo")).thenReturn(Optional.of(config));

		boolean sent = smsService.sendOtp("demo", "+918840844028", "123456", "10");

		assertTrue(sent);
		verifyNoInteractions(msg91SmsService);
	}

	@Test
	void consoleProviderDoesNotRequireATemplateIdUnlikeRealProviders() {
		SmsProviderRuntimeConfig config = new SmsProviderRuntimeConfig(
				"id-1", "demo", SmsProviderType.CONSOLE, true,
				"Local dev console", null, null,
				null, null, null,
				null, null, null, null, null, null, null, null, null, null, null,
				null, null
		);
		when(smsProviderConfigService.getRuntimeConfig("demo")).thenReturn(Optional.of(config));

		assertTrue(smsService.sendOtp("demo", "+918840844028", "123456", "10"));
	}

	@Test
	void missingConfigStillFailsClosedForOtherOrgs() {
		when(smsProviderConfigService.getRuntimeConfig("no-config-org")).thenReturn(Optional.empty());

		assertFalse(smsService.sendOtp("no-config-org", "+918840844028", "123456", "10"));
		verifyNoInteractions(msg91SmsService);
	}

	private SmsProviderRuntimeConfig consoleConfig(String orgId) {
		return new SmsProviderRuntimeConfig(
				"id-1", orgId, SmsProviderType.CONSOLE, true,
				"Local dev console", null, null,
				null, null, null,
				"otp-template", null, null, null, null, null, null, null, null, null, null,
				null, null
		);
	}
}
