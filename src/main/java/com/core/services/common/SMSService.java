package com.core.services.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import com.core.dtos.communication.SmsProviderRuntimeConfig;
import com.core.models.enums.SmsProviderType;
import com.core.services.SmsProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SMSService {

	private final SmsProviderConfigService smsProviderConfigService;
	private final Msg91SmsService msg91SmsService;

	public boolean sendOtp(String orgId, String mobile, String otp, String expiryMinutes) {
		return send(
				orgId,
				mobile,
				SmsProviderRuntimeConfig::otpTemplateId,
				vars("var1", otp, "var2", expiryMinutes, "var3", "#" + nullSafe(otp))
		);
	}

	public boolean sendBookingConfirmation(
			String orgId,
			String mobileNumber,
			String bookingId,
			String numberOfDuties
	) {
		return send(
				orgId,
				mobileNumber,
				SmsProviderRuntimeConfig::bookingConfirmationTemplateId,
				vars("var1", bookingId, "var2", numberOfDuties)
		);
	}

	public boolean sendDutyAllotmentSms(
			String orgId,
			String mobileNumber,
			String dutyId,
			String driverName,
			String driverNumber,
			String vehicleName,
			String vehicleNumber
	) {
		return send(
				orgId,
				mobileNumber,
				SmsProviderRuntimeConfig::dutyAllotmentTemplateId,
				vars(
						"var1", dutyId,
						"var2", driverName,
						"var3", driverNumber,
						"var4", vehicleName,
						"var5", vehicleNumber
				)
		);
	}

	public boolean sendDutyClosureSms(String orgId, String mobileNumber, String dutyId) {
		return send(
				orgId,
				mobileNumber,
				SmsProviderRuntimeConfig::dutyClosureTemplateId,
				vars("var1", dutyId, "var2", "")
		);
	}

	public boolean sendPaymentConfirmationSms(
			String orgId,
			String mobileNumber,
			String bookingId,
			String amount
	) {
		return send(
				orgId,
				mobileNumber,
				SmsProviderRuntimeConfig::paymentConfirmationTemplateId,
				vars("var1", bookingId, "var2", amount)
		);
	}

	public boolean sendPaymentPendingSms(
			String orgId,
			String mobileNumber,
			String bookingId,
			String amount
	) {
		return send(
				orgId,
				mobileNumber,
				SmsProviderRuntimeConfig::paymentPendingTemplateId,
				vars("var1", bookingId, "var2", amount)
		);
	}

	public boolean sendBookingCancellationSms(String orgId, String mobileNumber, String bookingId) {
		return send(
				orgId,
				mobileNumber,
				SmsProviderRuntimeConfig::bookingCancellationTemplateId,
				vars("var1", bookingId)
		);
	}

	public boolean sendRefundInitiatedSms(
			String orgId,
			String mobileNumber,
			String bookingId,
			String amount
	) {
		return send(
				orgId,
				mobileNumber,
				SmsProviderRuntimeConfig::refundInitiatedTemplateId,
				vars("var1", bookingId, "var2", amount)
		);
	}

	public boolean sendRefundCompletedSms(
			String orgId,
			String mobileNumber,
			String bookingId,
			String amount
	) {
		return send(
				orgId,
				mobileNumber,
				SmsProviderRuntimeConfig::refundCompletedTemplateId,
				vars("var1", bookingId, "var2", amount)
		);
	}

	public boolean sendReDutyAllotmentSms(
			String orgId,
			String mobileNumber,
			String dutyId,
			String driverName,
			String driverNumber,
			String vehicleName,
			String vehicleNumber
	) {
		return send(
				orgId,
				mobileNumber,
				SmsProviderRuntimeConfig::dutyReAllotmentTemplateId,
				vars(
						"var1", dutyId,
						"var2", driverName,
						"var3", driverNumber,
						"var4", vehicleName,
						"var5", vehicleNumber
				)
		);
	}

	public boolean sendReDutyClosureSms(String orgId, String mobileNumber, String bookingId) {
		return send(
				orgId,
				mobileNumber,
				SmsProviderRuntimeConfig::dutyReClosureTemplateId,
				vars("var1", bookingId)
		);
	}

	private boolean send(
			String orgId,
			String mobileNumber,
			Function<SmsProviderRuntimeConfig, String> templateResolver,
			Map<String, String> variables
	) {
		if (!hasText(orgId)) {
			log.warn("SMS skipped. orgId is missing.");
			return false;
		}

		SmsProviderRuntimeConfig config = smsProviderConfigService
				.getRuntimeConfig(orgId)
				.orElse(null);

		if (config == null) {
			log.warn("SMS skipped for org {}. No active SMS provider config found.", orgId);
			return false;
		}

		if (!Boolean.TRUE.equals(config.active())) {
			log.warn("SMS skipped for org {}. SMS provider config is inactive.", orgId);
			return false;
		}

		String templateId = templateResolver.apply(config);

		if (!hasText(templateId)) {
			log.warn(
					"SMS skipped for org {}. Template is not configured for provider {}.",
					orgId,
					config.providerType()
			);
			return false;
		}

		if (config.providerType() == SmsProviderType.MSG91) {
			return msg91SmsService.sendTemplateSms(config, templateId, mobileNumber, variables);
		}

		log.warn(
				"SMS skipped for org {}. Unsupported SMS provider: {}",
				orgId,
				config.providerType()
		);

		return false;
	}

	private Map<String, String> vars(String... values) {
		Map<String, String> map = new LinkedHashMap<>();

		for (int i = 0; i < values.length; i += 2) {
			String key = values[i];
			String value = i + 1 < values.length ? values[i + 1] : "";
			map.put(key, nullSafe(value));
		}

		return map;
	}

	private String nullSafe(String value) {
		return value == null ? "" : value;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}