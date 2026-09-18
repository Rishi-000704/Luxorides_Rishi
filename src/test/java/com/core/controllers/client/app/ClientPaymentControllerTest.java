package com.core.controllers.client.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.core.dtos.payment.PaymentGatewayRuntimeConfig;
import com.core.gateway.PaymentOrderDTO;
import com.core.gateway.razerpay.RazorpayCheckoutPayload;
import com.core.gateway.mock.MockPaymentService;
import com.core.gateway.razerpay.RazorpayPaymentService;
import com.core.models.Client;
import com.core.models.enums.PaymentGateway;
import com.core.security.SecurityContextUtil;
import com.core.services.ClientService;
import com.core.services.PaymentGatewayConfigService;

/*
 * Regression coverage for the client-selectable-MOCK-gateway gap: an org with
 * no PaymentGatewayConfig row used to fall back to whatever gateway the
 * CLIENT requested, letting an authenticated caller force MOCK and get a
 * free "paid" booking. resolveEffectiveGateway() must now always resolve an
 * unconfigured org to RAZORPAY, regardless of what the client asked for.
 */
class ClientPaymentControllerTest {

    private RazorpayPaymentService razorpayPaymentService;
    private MockPaymentService mockPaymentService;
    private PaymentGatewayConfigService paymentGatewayConfigService;
    private SecurityContextUtil security;
    private ClientService clientService;
    private ClientPaymentController controller;

    private static final String ORG_ID = "org-1";
    private static final String CLIENT_ID = "client-1";
    private static final String BOOKING_ID = "booking-1";

    @BeforeEach
    void setUp() {
        razorpayPaymentService = mock(RazorpayPaymentService.class);
        mockPaymentService = mock(MockPaymentService.class);
        paymentGatewayConfigService = mock(PaymentGatewayConfigService.class);
        security = mock(SecurityContextUtil.class);
        clientService = mock(ClientService.class);

        controller = new ClientPaymentController(
                razorpayPaymentService, mockPaymentService, paymentGatewayConfigService, security, clientService);

        when(security.orgId()).thenReturn(ORG_ID);
        when(security.userId()).thenReturn("user-1");

        Client client = new Client();
        client.setId(CLIENT_ID);
        when(clientService.findByUserId("user-1")).thenReturn(client);
    }

    @Test
    void unconfiguredOrg_clientRequestsMock_stillRoutesToRazorpay() throws Exception {
        when(paymentGatewayConfigService.getRuntimeConfig(ORG_ID)).thenReturn(Optional.empty());
        when(razorpayPaymentService.createRazorpayOrder(BOOKING_ID, CLIENT_ID, ORG_ID))
                .thenReturn(RazorpayCheckoutPayload.builder().build());

        controller.createOrder(new PaymentOrderDTO(BOOKING_ID, PaymentGateway.MOCK));

        verify(razorpayPaymentService, times(1)).createRazorpayOrder(BOOKING_ID, CLIENT_ID, ORG_ID);
        verify(mockPaymentService, never()).createMockOrder(anyString(), anyString(), anyString());
    }

    @Test
    void unconfiguredOrg_clientRequestsRazorpay_routesToRazorpay_unchangedBehavior() throws Exception {
        when(paymentGatewayConfigService.getRuntimeConfig(ORG_ID)).thenReturn(Optional.empty());
        when(razorpayPaymentService.createRazorpayOrder(BOOKING_ID, CLIENT_ID, ORG_ID))
                .thenReturn(RazorpayCheckoutPayload.builder().build());

        controller.createOrder(new PaymentOrderDTO(BOOKING_ID, PaymentGateway.RAZORPAY));

        verify(razorpayPaymentService, times(1)).createRazorpayOrder(BOOKING_ID, CLIENT_ID, ORG_ID);
        verify(mockPaymentService, never()).createMockOrder(anyString(), anyString(), anyString());
    }

    @Test
    void orgWithExplicitMockConfig_stillRoutesToMock_devSeedingStillWorks() throws Exception {
        PaymentGatewayRuntimeConfig mockConfig = new PaymentGatewayRuntimeConfig(
                "cfg-1", ORG_ID, PaymentGateway.MOCK, true,
                null, null, "INR", true, true, false,
                null, null, null, null, null);
        when(paymentGatewayConfigService.getRuntimeConfig(ORG_ID)).thenReturn(Optional.of(mockConfig));

        controller.createOrder(new PaymentOrderDTO(BOOKING_ID, PaymentGateway.RAZORPAY));

        verify(mockPaymentService, times(1)).createMockOrder(BOOKING_ID, CLIENT_ID, ORG_ID);
        verify(razorpayPaymentService, never()).createRazorpayOrder(anyString(), anyString(), anyString());
    }

    @Test
    void orgWithExplicitRazorpayConfig_clientRequestingMock_isOverriddenToRazorpay() throws Exception {
        PaymentGatewayRuntimeConfig razorpayConfig = new PaymentGatewayRuntimeConfig(
                "cfg-2", ORG_ID, PaymentGateway.RAZORPAY, true,
                null, null, "INR", true, true, false,
                null, null, null, null, null);
        when(paymentGatewayConfigService.getRuntimeConfig(ORG_ID)).thenReturn(Optional.of(razorpayConfig));
        when(razorpayPaymentService.createRazorpayOrder(BOOKING_ID, CLIENT_ID, ORG_ID))
                .thenReturn(RazorpayCheckoutPayload.builder().build());

        controller.createOrder(new PaymentOrderDTO(BOOKING_ID, PaymentGateway.MOCK));

        verify(razorpayPaymentService, times(1)).createRazorpayOrder(BOOKING_ID, CLIENT_ID, ORG_ID);
        verify(mockPaymentService, never()).createMockOrder(anyString(), anyString(), anyString());
    }
}
