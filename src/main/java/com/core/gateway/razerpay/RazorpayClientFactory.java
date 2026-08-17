package com.core.gateway.razerpay;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.core.dtos.payment.PaymentGatewayRuntimeConfig;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.enums.PaymentGateway;
import com.core.services.PaymentGatewayConfigService;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RazorpayClientFactory {

    private final PaymentGatewayConfigService paymentGatewayConfigService;

    public RazorpayCredentials credentials(String orgId) {
        if (!hasText(orgId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Organization id is required for payment gateway");
        }

        PaymentGatewayRuntimeConfig config = paymentGatewayConfigService
                .getRuntimeConfig(orgId, PaymentGateway.RAZORPAY)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "Razorpay payment gateway is not configured for this organization"
                ));

        if (!Boolean.TRUE.equals(config.active())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Razorpay payment gateway is disabled for this organization"
            );
        }

        if (!hasText(config.keyId()) || !hasText(config.keySecret())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Razorpay key id and key secret are required for this organization"
            );
        }

        return new RazorpayCredentials(
                orgId,
                config.keyId().trim(),
                config.keySecret().trim(),
                config.apiBaseUrl(),
                config.currency(),
                config.merchantName(),
                config.displayName(),
                config.checkoutEnabled(),
                config.qrEnabled(),
                config.autoCapture()
        );
    }

    public RazorpayClient client(RazorpayCredentials credentials) throws RazorpayException {
        return new RazorpayClient(credentials.keyId(), credentials.keySecret());
    }

    public void assertCheckoutEnabled(RazorpayCredentials credentials) {
        if (!credentials.isCheckoutEnabled()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Razorpay checkout is disabled for this organization"
            );
        }
    }

    public void assertQrEnabled(RazorpayCredentials credentials) {
        if (!credentials.isQrEnabled()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Razorpay QR payment is disabled for this organization"
            );
        }
    }

    public void verifySignature(
            RazorpayCredentials credentials,
            String orderId,
            String paymentId,
            String signature
    ) throws Exception {

        if (!hasText(orderId) || !hasText(paymentId) || !hasText(signature)) {
            throw new SecurityException("Invalid Razorpay payment verification request");
        }

        String payload = orderId + "|" + paymentId;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                credentials.keySecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));

        String expected = HexFormat.of().formatHex(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))
        );

        if (!expected.equals(signature)) {
            throw new SecurityException("Invalid Razorpay signature");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}