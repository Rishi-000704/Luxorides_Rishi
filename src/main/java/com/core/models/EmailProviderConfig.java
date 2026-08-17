package com.core.models;

import com.core.models.embedded.AuditableEntity;

import com.core.models.enums.EmailProviderType;
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
        name = "email_provider_configs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_email_provider_org_provider", columnNames = {"org_id", "provider_type"})
        }
)
public class EmailProviderConfig extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, length = 40)
    private String id;

    @Column(name = "org_id", nullable = false, length = 40)
    private String orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 40)
    private EmailProviderType providerType;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "default_config", nullable = false)
    private Boolean defaultConfig = true;

    @Column(length = 100)
    private String displayName;

    @Column(length = 100)
    private String senderName;

    @Column(length = 150)
    private String senderEmail;

    @Column(length = 150)
    private String replyToEmail;

    @Column(length = 500)
    private String apiBaseUrl;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String apiSecretEncrypted;

    @Column(length = 200)
    private String smtpHost;

    private Integer smtpPort;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String smtpUsernameEncrypted;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String smtpPasswordEncrypted;

    private Boolean smtpUseTls = true;

    private Boolean smtpUseSsl = false;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String webhookSecretEncrypted;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String providerSettingsJson;

    @PrePersist
    private void prePersist() {
        if (providerType == null) {
            providerType = EmailProviderType.ZEPTO_MAIL;
        }

        if (active == null) {
            active = true;
        }

        if (defaultConfig == null) {
            defaultConfig = true;
        }
    }
}
