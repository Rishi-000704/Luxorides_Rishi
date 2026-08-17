package com.core.gateway.razerpay;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RazorpayCheckoutPayload {

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

