package com.core.dtos.common;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Msg91TemplateSmsRequest(
    @JsonProperty("template_id") String templateId,
    @JsonProperty("short_url") String shortUrl,
    @JsonProperty("short_url_expiry") String shortUrlExpiry,
    @JsonProperty("realTimeResponse") String realTimeResponse,
    List<Recipient> recipients
) {

    public static class Recipient {

        private final String mobiles;
        private final Map<String, String> variables;

        public Recipient(String mobiles, Map<String, String> variables) {
            this.mobiles = mobiles;
            this.variables = variables;
        }

        @JsonProperty("mobiles")
        public String getMobiles() {
            return mobiles;
        }

        /**
         * 🔥 THIS IS THE KEY PART
         * This flattens VAR1, VAR2 into the JSON object
         */
        @JsonAnyGetter
        public Map<String, String> getVariables() {
            return variables;
        }
    }
}
