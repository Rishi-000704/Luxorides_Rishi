package com.core.services;

import com.core.events.ReportRequestedEvent;
import com.core.exception.FleetovoException;
import com.core.models.ReportRequest;
import com.core.reports.generation.GeneratedReportFile;
import com.core.reports.generation.ReportGeneratorRegistry;
import com.core.reports.storage.ReportFileStorageService;
import com.core.reports.storage.ReportFileStorageService.StoredReportFile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportGenerationProcessorService {

    private final ReportRequestStateService stateService;
    private final ReportGeneratorRegistry reportGeneratorRegistry;
    private final ReportFileStorageService reportFileStorageService;

    public void process(ReportRequestedEvent event) {
        ReportRequest reportRequest = stateService.claimForProcessing(
                event.reportRequestId(),
                event.orgId()
        );

        if (reportRequest == null) {
            return;
        }

        int attempt = reportRequest.getAttemptCount();
        StoredReportFile storedFile = null;

        try {
            GeneratedReportFile generatedFile = reportGeneratorRegistry.generate(reportRequest);
            storedFile = reportFileStorageService.save(reportRequest.getOrgId(), generatedFile);

            boolean completed = stateService.markCompleted(
                    reportRequest.getId(),
                    reportRequest.getOrgId(),
                    attempt,
                    storedFile
            );

            if (!completed) {
                reportFileStorageService.delete(storedFile.fileName());
            }
        } catch (Exception ex) {
            if (storedFile != null) {
                reportFileStorageService.delete(storedFile.fileName());
            }

            stateService.markFailed(
                    event.reportRequestId(),
                    event.orgId(),
                    attempt,
                    errorMessage(ex)
            );
        }
    }

    private String errorMessage(Exception ex) {
        if (ex instanceof FleetovoException && hasText(ex.getMessage())) {
            return ex.getMessage();
        }

        Throwable cause = ex.getCause();
        if (cause instanceof FleetovoException && hasText(cause.getMessage())) {
            return cause.getMessage();
        }

        if (hasText(ex.getMessage())) {
            return ex.getMessage();
        }

        return "Report generation failed due to an internal error";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
