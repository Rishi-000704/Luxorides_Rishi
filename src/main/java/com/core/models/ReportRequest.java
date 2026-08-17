package com.core.models;

import java.time.Instant;
import java.time.LocalDate;

import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.ReportCategory;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportPartyType;
import com.core.models.enums.ReportStatus;
import com.core.models.enums.ReportType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
        name = "report_requests",
        indexes = {
                @Index(name = "idx_report_request_org_status", columnList = "org_id,status"),
                @Index(name = "idx_report_request_org_category", columnList = "org_id,category"),
                @Index(name = "idx_report_request_org_type", columnList = "org_id,report_type")
        }
)
public class ReportRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, length = 40)
    private String id;

    @Column(name = "org_id", nullable = false, length = 40)
    private String orgId;

    @Column(name = "requested_by", nullable = false, length = 40)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReportCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 60)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportStatus status = ReportStatus.QUEUED;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "org_billing_entity_id", length = 40)
    private String orgBillingEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", length = 40)
    private ReportPartyType partyType;

    @Column(name = "party_id", length = 40)
    private String partyId;

    @Lob
    @Column(name = "filters_json", columnDefinition = "TEXT")
    private String filtersJson;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Lob
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "download_count", nullable = false)
    private Integer downloadCount = 0;

    @Column(name = "last_downloaded_at")
    private Instant lastDownloadedAt;

    @PrePersist
    private void prePersist() {
        if (status == null) {
            status = ReportStatus.QUEUED;
        }

        if (attemptCount == null) {
            attemptCount = 0;
        }

        if (downloadCount == null) {
            downloadCount = 0;
        }

        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }
}