package com.core.models;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.PaymentGateway;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
		name = "payment_gateway_configs",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_payment_gateway_org_gateway", columnNames = {"org_id", "gateway"})
		}
)
public class PaymentGatewayConfig extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, length = 40)
	private String id;

	@Column(name = "org_id", nullable = false, length = 40)
	private String orgId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private PaymentGateway gateway;

	@Column(nullable = false)
	private Boolean active = true;

	@Column(name = "default_config", nullable = false)
	private Boolean defaultConfig = true;

	@Column(length = 100)
	private String displayName;

	@Column(length = 150)
	private String merchantName;

	@Column(length = 20)
	private String currency;

	@Column(nullable = false)
	private Boolean checkoutEnabled = true;

	@Column(nullable = false)
	private Boolean qrEnabled = true;

	@Column(nullable = false)
	private Boolean autoCapture = false;

	@Column(length = 500)
	private String apiBaseUrl;

	@Lob
	@Column(columnDefinition = "TEXT")
	private String keyIdEncrypted;

	@Lob
	@Column(columnDefinition = "TEXT")
	private String keySecretEncrypted;

	@Lob
	@Column(columnDefinition = "TEXT")
	private String webhookSecretEncrypted;

	@Lob
	@Column(columnDefinition = "TEXT")
	private String providerSettingsJson;

	@PrePersist
	private void prePersist() {
		if (gateway == null) {
			gateway = PaymentGateway.RAZORPAY;
		}

		if (active == null) {
			active = true;
		}

		if (defaultConfig == null) {
			defaultConfig = true;
		}

		if (currency == null || currency.isBlank()) {
			currency = "INR";
		}

		if (checkoutEnabled == null) {
			checkoutEnabled = true;
		}

		if (qrEnabled == null) {
			qrEnabled = true;
		}

		if (autoCapture == null) {
			autoCapture = false;
		}
	}
}