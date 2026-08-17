package com.core.services.common;

import java.util.List;
import java.util.Map;

import com.core.dtos.common.Msg91TemplateSmsRequest;
import com.core.dtos.communication.SmsProviderRuntimeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class Msg91SmsService {

	private static final String DEFAULT_MSG91_FLOW_URL = "https://control.msg91.com/api/v5/flow";

	private final RestTemplate restTemplate = new RestTemplate();

	public boolean sendTemplateSms(
			SmsProviderRuntimeConfig config,
			String templateId,
			String mobileNumber,
			Map<String, String> variables
	) {
		if (config == null) {
			log.warn("MSG91 SMS skipped. Runtime config is missing.");
			return false;
		}

		if (!hasText(config.authKey())) {
			log.warn("MSG91 SMS skipped for org {}. Auth key is not configured.", config.orgId());
			return false;
		}

		if (!hasText(templateId)) {
			log.warn("MSG91 SMS skipped for org {}. Template ID is missing.", config.orgId());
			return false;
		}

		if (!hasText(mobileNumber)) {
			log.warn("MSG91 SMS skipped for org {}. Mobile number is missing.", config.orgId());
			return false;
		}

		String apiUrl = hasText(config.apiBaseUrl())
				? config.apiBaseUrl().trim()
				: DEFAULT_MSG91_FLOW_URL;

		try {
			Msg91TemplateSmsRequest.Recipient recipient =
					new Msg91TemplateSmsRequest.Recipient(mobileNumber, variables);

			Msg91TemplateSmsRequest payload =
					new Msg91TemplateSmsRequest(templateId, "0", null, "0", List.of(recipient));

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.set("authkey", config.authKey().trim());

			var response = restTemplate.postForEntity(
					apiUrl,
					new HttpEntity<>(payload, headers),
					String.class
			);

			if (!response.getStatusCode().is2xxSuccessful()) {
				log.warn(
						"MSG91 SMS failed for org {}. Status: {}, Response: {}",
						config.orgId(),
						response.getStatusCode(),
						response.getBody()
				);
				return false;
			}

			return true;
		} catch (Exception ex) {
			log.warn(
					"MSG91 SMS failed for org {}. Error: {}",
					config.orgId(),
					ex.getMessage()
			);
			return false;
		}
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}