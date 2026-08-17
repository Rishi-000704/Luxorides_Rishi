package com.core.services;

import java.time.Instant;

import com.core.config.ReportGenerationProperties;
import com.core.events.ReportRequestedEvent;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.ReportRequest;
import com.core.models.enums.ReportStatus;
import com.core.repositories.ReportRequestRepository;
import com.core.reports.storage.ReportFileStorageService;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportRecoveryService {

    private final ReportRequestRepository repository;
    private final ReportFileStorageService fileStorageService;
    private final ReportGenerationProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReportRequest refresh(String orgId, String reportRequestId) {
        ReportRequest reportRequest = repository
                .findByIdAndOrgIdForUpdate(reportRequestId, orgId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REPORT_REQUEST_NOT_FOUND,
                        "Report request not found"
                ));

        return switch (reportRequest.getStatus()) {
            case COMPLETED -> refreshCompleted(reportRequest);
            case QUEUED -> refreshQueued(reportRequest);
            case PROCESSING -> refreshProcessing(reportRequest);
            case FAILED -> requeue(reportRequest);
            case CANCELLED -> throw new BusinessException(
                    ErrorCode.REPORT_RECOVERY_NOT_ALLOWED,
                    "Cancelled report requests cannot be restarted"
            );
            case EXPIRED -> throw new BusinessException(
                    ErrorCode.REPORT_RECOVERY_NOT_ALLOWED,
                    "Expired report requests cannot be restarted"
            );
        };
    }

    private ReportRequest refreshCompleted(ReportRequest reportRequest) {
        if (hasText(reportRequest.getFileName()) && fileStorageService.exists(reportRequest.getFileName())) {
            return reportRequest;
        }

        return requeue(reportRequest);
    }

    private ReportRequest refreshQueued(ReportRequest reportRequest) {
        Instant stateTime = reportRequest.getUpdatedAt() != null
                ? reportRequest.getUpdatedAt()
                : reportRequest.getRequestedAt();

        if (!isStale(stateTime, properties.getQueuedTimeout())) {
            return reportRequest;
        }

        return requeue(reportRequest);
    }

    private ReportRequest refreshProcessing(ReportRequest reportRequest) {
        if (!isStale(reportRequest.getStartedAt(), properties.getProcessingTimeout())) {
            return reportRequest;
        }

        return requeue(reportRequest);
    }

    private ReportRequest requeue(ReportRequest reportRequest) {
        int attempts = reportRequest.getAttemptCount() == null ? 0 : reportRequest.getAttemptCount();
        if (attempts >= properties.getMaxAttempts()) {
            throw new BusinessException(
                    ErrorCode.REPORT_RETRY_LIMIT_REACHED,
                    "Maximum report generation attempts reached"
            );
        }

        reportRequest.setStatus(ReportStatus.QUEUED);
        reportRequest.setStartedAt(null);
        reportRequest.setCompletedAt(null);
        reportRequest.setErrorMessage(null);
        reportRequest.setFileName(null);
        reportRequest.setOriginalFileName(null);
        reportRequest.setContentType(null);
        reportRequest.setFileSize(null);

        ReportRequest saved = repository.saveAndFlush(reportRequest);
        eventPublisher.publishEvent(toEvent(saved));
        return saved;
    }

    private boolean isStale(Instant stateTime, java.time.Duration timeout) {
        if (stateTime == null) {
            return true;
        }

        return stateTime.plus(timeout).isBefore(Instant.now());
    }

    private ReportRequestedEvent toEvent(ReportRequest reportRequest) {
        return new ReportRequestedEvent(
                reportRequest.getId(),
                reportRequest.getOrgId(),
                reportRequest.getRequestedBy(),
                reportRequest.getCategory(),
                reportRequest.getReportType(),
                reportRequest.getFormat(),
                reportRequest.getPeriodFrom(),
                reportRequest.getPeriodTo()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
