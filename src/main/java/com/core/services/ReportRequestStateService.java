package com.core.services;

import java.time.Instant;

import com.core.config.ReportGenerationProperties;
import com.core.models.ReportRequest;
import com.core.models.enums.ReportStatus;
import com.core.repositories.ReportRequestRepository;
import com.core.reports.storage.ReportFileStorageService.StoredReportFile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportRequestStateService {

    private final ReportRequestRepository repository;
    private final ReportGenerationProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReportRequest claimForProcessing(String reportRequestId, String orgId) {
        ReportRequest reportRequest = repository
                .findByIdAndOrgIdForUpdate(reportRequestId, orgId)
                .orElse(null);

        if (reportRequest == null || reportRequest.getStatus() != ReportStatus.QUEUED) {
            return null;
        }

        int attemptCount = safeAttemptCount(reportRequest);
        if (attemptCount >= properties.getMaxAttempts()) {
            reportRequest.setStatus(ReportStatus.FAILED);
            reportRequest.setCompletedAt(Instant.now());
            reportRequest.setErrorMessage("Maximum report generation attempts reached");
            repository.saveAndFlush(reportRequest);
            return null;
        }

        reportRequest.setStatus(ReportStatus.PROCESSING);
        reportRequest.setStartedAt(Instant.now());
        reportRequest.setCompletedAt(null);
        reportRequest.setAttemptCount(attemptCount + 1);
        reportRequest.setErrorMessage(null);

        return repository.saveAndFlush(reportRequest);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markCompleted(
            String reportRequestId,
            String orgId,
            int expectedAttempt,
            StoredReportFile storedFile
    ) {
        ReportRequest reportRequest = repository
                .findByIdAndOrgIdForUpdate(reportRequestId, orgId)
                .orElse(null);

        if (!isCurrentAttempt(reportRequest, expectedAttempt)) {
            return false;
        }

        reportRequest.setStatus(ReportStatus.COMPLETED);
        reportRequest.setCompletedAt(Instant.now());
        reportRequest.setFileName(storedFile.fileName());
        reportRequest.setOriginalFileName(storedFile.originalFileName());
        reportRequest.setContentType(storedFile.contentType());
        reportRequest.setFileSize(storedFile.fileSize());
        reportRequest.setErrorMessage(null);

        repository.saveAndFlush(reportRequest);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            String reportRequestId,
            String orgId,
            int expectedAttempt,
            String errorMessage
    ) {
        ReportRequest reportRequest = repository
                .findByIdAndOrgIdForUpdate(reportRequestId, orgId)
                .orElse(null);

        if (!isCurrentAttempt(reportRequest, expectedAttempt)) {
            return;
        }

        reportRequest.setStatus(ReportStatus.FAILED);
        reportRequest.setCompletedAt(Instant.now());
        reportRequest.setErrorMessage(shortError(errorMessage));

        repository.saveAndFlush(reportRequest);
    }

    private boolean isCurrentAttempt(ReportRequest reportRequest, int expectedAttempt) {
        return reportRequest != null
                && reportRequest.getStatus() == ReportStatus.PROCESSING
                && safeAttemptCount(reportRequest) == expectedAttempt;
    }

    private int safeAttemptCount(ReportRequest reportRequest) {
        return reportRequest.getAttemptCount() == null ? 0 : reportRequest.getAttemptCount();
    }

    private String shortError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Report generation failed";
        }

        String cleaned = errorMessage.trim();
        return cleaned.length() <= 2000 ? cleaned : cleaned.substring(0, 2000);
    }
}
