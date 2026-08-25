package com.core.gateway.razerpay;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RazorpayCheckoutPayload {

    /** "RAZORPAY" for a real order, "MOCK" for the dev-only dummy gateway -- lets the
     *  frontend skip opening the real Razorpay checkout widget for a mock order. */
    @Builder.Default
    private String gateway = "RAZORPAY";

    private String key;
    private String orderId;
    private long amount; // paise
    private String currency;

    private Prefill prefill;

    @Data
    @Builder
    public static class Prefill {
        private String name;
        private String email;
        private String contact;
    }
}

