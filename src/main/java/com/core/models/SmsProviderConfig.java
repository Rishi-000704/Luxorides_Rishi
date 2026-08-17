package com.core.models;

import com.core.models.embedded.AuditableEntity;

import com.core.models.enums.SmsProviderType;
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
        name = "sms_provider_configs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sms_provider_org_provider", columnNames = {"org_id", "provider_type"})
        }
)
public class SmsProviderConfig extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, length = 40)
    private String id;

    @Column(name = "org_id", nullable = false, length = 40)
    private String orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 40)
    private SmsProviderType providerType;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "default_config", nullable = false)
    private Boolean defaultConfig = true;

    @Column(length = 100)
    private String displayName;

    @Column(length = 50)
    private String senderId;

    @Column(length = 500)
    private String apiBaseUrl;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String authKeyEncrypted;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String apiSecretEncrypted;

    @Column(length = 100)
    private String otpTemplateId;

    @Column(length = 100)
    private String bookingConfirmationTemplateId;

    @Column(length = 100)
    private String dutyAllotmentTemplateId;

    @Column(length = 100)
    private String dutyClosureTemplateId;

    @Column(length = 100)
    private String paymentConfirmationTemplateId;

    @Column(length = 100)
    private String paymentPendingTemplateId;

    @Column(length = 100)
    private String bookingCancellationTemplateId;

    @Column(length = 100)
    private String refundInitiatedTemplateId;

    @Column(length = 100)
    private String refundCompletedTemplateId;

    @Column(length = 100)
    private String dutyReAllotmentTemplateId;

    @Column(length = 100)
    private String dutyReClosureTemplateId;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String webhookSecretEncrypted;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String providerSettingsJson;

    @PrePersist
    private void prePersist() {
        if (providerType == null) {
            providerType = SmsProviderType.MSG91;
        }

        if (active == null) {
            active = true;
        }

        if (defaultConfig == null) {
            defaultConfig = true;
        }
    }
}