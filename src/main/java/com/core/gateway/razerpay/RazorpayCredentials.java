package com.core.gateway.razerpay;

public record RazorpayCredentials(
        String orgId,
        String keyId,
        String keySecret,
        String apiBaseUrl,
        String currency,
        String merchantName,
        String displayName,
        Boolean checkoutEnabled,
        Boolean qrEnabled,
        Boolean autoCapture,
        String webhookSecret
) {

    public static final String DEFAULT_API_BASE_URL = "https://api.razorpay.com/v1";
    public static final String DEFAULT_CURRENCY = "INR";
    public static final String DEFAULT_QR_NAME = "Fleetovo";

    public String resolvedApiBaseUrl() {
        return apiBaseUrl == null || apiBaseUrl.isBlank()
                ? DEFAULT_API_BASE_URL
                : apiBaseUrl.trim();
    }

    public String resolvedCurrency() {
        return currency == null || currency.isBlank()
                ? DEFAULT_CURRENCY
                : currency.trim().toUpperCase();
    }

    public String resolvedMerchantName() {
        if (merchantName != null && !merchantName.isBlank()) {
            return merchantName.trim();
        }

        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }

        return DEFAULT_QR_NAME;
    }

    public boolean isCheckoutEnabled() {
        return Boolean.TRUE.equals(checkoutEnabled);
    }

    public boolean isQrEnabled() {
        return Boolean.TRUE.equals(qrEnabled);
    }

    public boolean isAutoCapture() {
        return Boolean.TRUE.equals(autoCapture);
    }

    public boolean hasWebhookSecret() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }
}