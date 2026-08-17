package com.core.services.common;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.core.dtos.communication.EmailProviderRuntimeConfig;
import com.core.models.enums.EmailProviderType;
import com.core.services.EmailProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZohoMailService {

    private static final String DEFAULT_ZEPTO_MAIL_URL = "https://api.zeptomail.in/v1.1/email";

    private final EmailProviderConfigService emailProviderConfigService;

    public boolean send(String orgId, String to, String subject, String body) {
        if (!hasText(orgId)) {
            log.warn("Email skipped. orgId is missing.");
            return false;
        }

        if (!hasText(to)) {
            log.warn("Email skipped for org {}. Recipient email is missing.", orgId);
            return false;
        }

        EmailProviderRuntimeConfig config = emailProviderConfigService
                .getRuntimeConfig(orgId)
                .orElse(null);

        if (config == null) {
            log.warn("Email skipped for org {}. No active email provider config found.", orgId);
            return false;
        }

        if (!Boolean.TRUE.equals(config.active())) {
            log.warn("Email skipped for org {}. Email provider config is inactive.", orgId);
            return false;
        }

        if (config.providerType() == EmailProviderType.ZEPTO_MAIL) {
            return sendViaZeptoMail(config, to, subject, body);
        }

        log.warn(
                "Email skipped for org {}. Unsupported email provider: {}",
                orgId,
                config.providerType()
        );

        return false;
    }

    private boolean sendViaZeptoMail(
            EmailProviderRuntimeConfig config,
            String to,
            String subject,
            String body
    ) {
        String apiUrl = hasText(config.apiBaseUrl())
                ? config.apiBaseUrl().trim()
                : DEFAULT_ZEPTO_MAIL_URL;

        if (!hasText(config.apiKey())) {
            log.warn("Zepto email skipped for org {}. API key is not configured.", config.orgId());
            return false;
        }

        if (!hasText(config.senderEmail())) {
            log.warn("Zepto email skipped for org {}. Sender email is not configured.", config.orgId());
            return false;
        }

        HttpURLConnection conn = null;

        try {
            URI uri = URI.create(apiUrl);
            URL url = uri.toURL();

            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Zoho-enczapikey " + config.apiKey().trim());
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            JSONObject from = new JSONObject()
                    .put("address", config.senderEmail().trim());

            if (hasText(config.senderName())) {
                from.put("name", config.senderName().trim());
            }

            JSONObject recipientEmail = new JSONObject()
                    .put("address", to.trim());

            JSONObject payload = new JSONObject();
            payload.put("from", from);
            payload.put("to", new JSONArray().put(
                    new JSONObject().put("email_address", recipientEmail)
            ));
            payload.put("subject", subject == null ? "" : subject);
            payload.put("htmlbody", body == null ? "" : body);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            responseCode < 400 ? conn.getInputStream() : conn.getErrorStream(),
                            StandardCharsets.UTF_8
                    )
            )) {
                StringBuilder response = new StringBuilder();
                String output;

                while ((output = br.readLine()) != null) {
                    response.append(output);
                }

                if (responseCode >= 400) {
                    log.warn(
                            "Zepto email failed for org {}. Status: {}, Response: {}",
                            config.orgId(),
                            responseCode,
                            response
                    );
                    return false;
                }
            }

            return true;
        } catch (Exception ex) {
            log.warn(
                    "Zepto email failed for org {}. Error: {}",
                    config.orgId(),
                    ex.getMessage()
            );
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}