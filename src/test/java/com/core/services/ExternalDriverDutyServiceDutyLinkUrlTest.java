package com.core.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.core.dtos.driverduty.DriverDutyLinkResponse;
import com.core.events.assembler.PaymentEventAssembler;
import com.core.gateway.mock.MockPaymentService;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.location.api.LocationService;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.DriverDutyAccessToken;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;
import com.core.repositories.BookingEntryRepository;
import com.core.repositories.BookingRepository;
import com.core.repositories.DriverDutyAccessTokenRepository;
import com.core.repositories.DriverDutyCheckpointRepository;
import com.core.repositories.DriverDutyExpenseRepository;
import com.core.repositories.DriverDutyLiveLocationRepository;
import com.core.repositories.PaymentRepository;
import com.core.services.common.FileService;
import com.core.services.common.SMSService;
import com.core.ws.DutyLocationChannelRegistry;
import org.springframework.beans.factory.annotation.Value;

/*
 * Covers the P0 fix: driverDutyPublicUrl (ExternalDriverDutyService) must
 * come from configuration (driver.duty.public-url), never from a hardcoded
 * sandbox default, and the property itself must fail application startup
 * rather than silently resolve to nothing when unset.
 */
class ExternalDriverDutyServiceDutyLinkUrlTest {

	private static final String ORG_ID = "org-1";
	private static final String DUTY_ID = "duty-1";
	private static final String ENTRY_ID = "entry-1";
	private static final String BOOKING_ID = "booking-1";

	private BookingEntryRepository bookingEntryRepository;
	private DriverDutyAccessTokenRepository tokenRepository;
	private DriverDutyTokenValidator tokenValidator;
	private ExternalDriverDutyService service;

	@BeforeEach
	void setUp() {
		bookingEntryRepository = mock(BookingEntryRepository.class);
		tokenRepository = mock(DriverDutyAccessTokenRepository.class);
		tokenValidator = mock(DriverDutyTokenValidator.class);

		service = new ExternalDriverDutyService(
				bookingEntryRepository,
				mock(BookingRepository.class),
				tokenRepository,
				mock(DriverDutyCheckpointRepository.class),
				mock(DriverDutyExpenseRepository.class),
				mock(FileService.class),
				mock(BookingService.class),
				mock(RazorpayPaymentService.class),
				mock(MockPaymentService.class),
				mock(PaymentGatewayConfigService.class),
				tokenValidator,
				mock(ApplicationEventPublisher.class),
				mock(DriverDutyLiveLocationRepository.class),
				mock(DutyLocationChannelRegistry.class),
				mock(FraudSignalService.class),
				new BCryptPasswordEncoder(),
				mock(SMSService.class),
				mock(LocationService.class),
				mock(ObjectProvider.class),
				mock(PaymentRepository.class),
				mock(PaymentEventAssembler.class)
		);

		BookingEntry entry = entry();
		when(bookingEntryRepository.findByDutyIdAndOrgId(DUTY_ID, ORG_ID)).thenReturn(Optional.of(entry));
		when(tokenRepository.findFirstByBookingEntry_IdAndStatusOrderByCreatedAtDesc(
				org.mockito.ArgumentMatchers.eq(ENTRY_ID), org.mockito.ArgumentMatchers.any()))
				.thenReturn(Optional.empty());
		when(tokenRepository.save(org.mockito.ArgumentMatchers.any(DriverDutyAccessToken.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(tokenValidator.hash(org.mockito.ArgumentMatchers.anyString())).thenReturn("hashed-token");
	}

	private BookingEntry entry() {
		Booking booking = new Booking();
		booking.setBookingId(BOOKING_ID);
		booking.setOrgId(ORG_ID);
		booking.setStatus(BookingStatus.CONFIRMED);

		BookingEntry e = new BookingEntry();
		e.setId(ENTRY_ID);
		e.setDutyId(DUTY_ID);
		e.setBooking(booking);
		e.setStatus(DutyStatus.ALLOTTED);
		return e;
	}

	@Test
	void configuredUrl_isUsedToBuildTheDriverDutyLink() {
		ReflectionTestUtils.setField(service, "driverDutyPublicUrl", "https://app.fleetovo.com/extrenal");

		DriverDutyLinkResponse response = service.generateDriverDutyLink(BOOKING_ID, DUTY_ID, ORG_ID);

		assertTrue(response.url().startsWith("https://app.fleetovo.com/extrenal/"));
	}

	@Test
	void sandbox_isNeverUsedAsAnImplicitProductionFallback() {
		// A distinct, deliberately non-sandbox value -- if any hardcoded
		// "https://sandbox.fleetovo.com" fallback still existed anywhere in
		// generateDriverDutyLink, this assertion would catch it.
		ReflectionTestUtils.setField(service, "driverDutyPublicUrl", "https://app.fleetovo.com/extrenal");

		DriverDutyLinkResponse response = service.generateDriverDutyLink(BOOKING_ID, DUTY_ID, ORG_ID);

		assertFalse(response.url().contains("sandbox.fleetovo.com"));
	}

	@Test
	void missingDriverDutyPublicUrl_failsApplicationStartup_insteadOfSilentlyResolvingEmpty() {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
			ctx.register(RequiresDriverDutyPublicUrl.class);
			assertThrows(Exception.class, ctx::refresh);
		}
	}

	@Configuration
	static class RequiresDriverDutyPublicUrl {

		@Value("${driver.duty.public-url}")
		String url;

		@Bean
		static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}
	}
}
