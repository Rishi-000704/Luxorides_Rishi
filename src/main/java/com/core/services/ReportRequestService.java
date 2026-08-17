package com.core.services;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.core.config.ReportGenerationProperties;
import com.core.dtos.report.ReportRequestCreateRequest;
import com.core.dtos.report.ReportRequestResponse;
import com.core.dtos.report.ReportTypeOptionResponse;
import com.core.events.ReportRequestedEvent;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.Client;
import com.core.models.ReportRequest;
import com.core.models.enums.ReportCategory;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportPartyType;
import com.core.models.enums.ReportPeriodRule;
import com.core.models.enums.ReportStatus;
import com.core.models.enums.ReportType;
import com.core.reports.definition.ReportDefinition;
import com.core.reports.definition.ReportDefinitionRegistry;
import com.core.reports.generation.ReportGeneratorRegistry;
import com.core.repositories.ClientBillingEntityRepository;
import com.core.repositories.ClientRepository;
import com.core.repositories.OrgBillingEntityRepository;
import com.core.repositories.ReportRequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportRequestService {

    private final ReportRequestRepository repository;
    private final OrgBillingEntityRepository orgBillingEntityRepository;
    private final ClientRepository clientRepository;
    private final ClientBillingEntityRepository clientBillingEntityRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ReportDefinitionRegistry definitionRegistry;
    private final ReportGeneratorRegistry generatorRegistry;
    private final ReportRecoveryService recoveryService;
    private final ReportGenerationProperties properties;

    @Transactional
    public ReportRequestResponse requestReport(String orgId, String requestedBy, ReportRequestCreateRequest request) {
        ReportDefinition definition = validateCreateRequest(orgId, request);

        ReportRequest reportRequest = new ReportRequest();
        reportRequest.setOrgId(orgId);
        reportRequest.setRequestedBy(requestedBy);
        reportRequest.setCategory(request.category());
        reportRequest.setReportType(request.reportType());
        reportRequest.setFormat(request.format());
        reportRequest.setStatus(ReportStatus.QUEUED);
        reportRequest.setPeriodFrom(request.periodFrom());
        reportRequest.setPeriodTo(request.periodTo());
        reportRequest.setOrgBillingEntityId(trimToNull(request.orgBillingEntityId()));
        reportRequest.setPartyType(request.partyType());
        reportRequest.setPartyId(trimToNull(request.partyId()));
        reportRequest.setFiltersJson(writeFilters(request.filters()));
        reportRequest.setDisplayName(buildDisplayName(definition, request));
        reportRequest.setRequestedAt(Instant.now());

        ReportRequest saved = repository.save(reportRequest);
        eventPublisher.publishEvent(toEvent(saved));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ReportRequestResponse get(String orgId, String id) {
        return toResponse(findOwned(orgId, id));
    }

    public ReportRequestResponse refresh(String orgId, String id) {
        validateRequiredText(orgId, "Org id is required");
        validateRequiredText(id, "Report request id is required");
        return toResponse(recoveryService.refresh(orgId, id.trim()));
    }

    @Transactional(readOnly = true)
    public Page<ReportRequestResponse> page(
            String orgId,
            ReportStatus status,
            ReportCategory category,
            ReportType reportType,
            ReportFormat format,
            String searchstr,
            Pageable pageable
    ) {
        return repository.findPage(
                        orgId,
                        status,
                        category,
                        reportType,
                        format,
                        searchstr == null ? "" : searchstr.trim(),
                        pageable
                )
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReportRequest validateDownloadRequest(String orgId, String id) {
        ReportRequest reportRequest = findOwned(orgId, id);

        if (reportRequest.getStatus() != ReportStatus.COMPLETED) {
            throw new BusinessException(
                    ErrorCode.REPORT_NOT_READY,
                    "Report is not generated yet. Current status: " + reportRequest.getStatus()
            );
        }

        if (!hasText(reportRequest.getFileName())) {
            throw new BusinessException(ErrorCode.REPORT_FILE_NOT_FOUND, "Report file is missing");
        }

        return reportRequest;
    }

    @Transactional
    public void markDownloaded(String orgId, String id) {
        ReportRequest reportRequest = findOwned(orgId, id);

        reportRequest.setDownloadCount(
                reportRequest.getDownloadCount() == null
                        ? 1
                        : reportRequest.getDownloadCount() + 1
        );
        reportRequest.setLastDownloadedAt(Instant.now());

        repository.save(reportRequest);
    }

    @Transactional(readOnly = true)
    public List<ReportTypeOptionResponse> metadata() {
        return definitionRegistry.all()
                .stream()
                .map(definition -> new ReportTypeOptionResponse(
                        definition.category(),
                        definition.reportType(),
                        definition.label(),
                        definition.available(),
                        definition.comingSoonMessage(),
                        definition.supportedFormats(),
                        true,
                        definition.periodRule(),
                        definition.requiresOrgBillingEntity(),
                        definition.requiresParty(),
                        definition.allowedPartyTypes()
                ))
                .toList();
    }

    public String defaultContentType(ReportFormat format) {
        if (format == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return switch (format) {
            case PDF -> MediaType.APPLICATION_PDF_VALUE;
            case CSV -> "text/csv";
            case JSON -> MediaType.APPLICATION_JSON_VALUE;
        };
    }

    private ReportDefinition validateCreateRequest(String orgId, ReportRequestCreateRequest request) {
        validateRequiredText(orgId, "Org id is required");

        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Report request cannot be null");
        }

        if (request.category() == null) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_CATEGORY, "Report category is required");
        }

        if (request.reportType() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Report type is required");
        }

        ReportDefinition definition = definitionRegistry.find(request.reportType())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REPORT_CATEGORY,
                        request.reportType() + " is not available in the report request form"
                ));

        if (definition.category() != request.category()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REPORT_CATEGORY,
                    definition.label() + " is not valid for " + request.category() + " report category"
            );
        }

        if (!definition.available()) {
            throw new BusinessException(
                    ErrorCode.REPORT_COMING_SOON,
                    definition.comingSoonMessage()
            );
        }

        if (request.format() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Report format is required");
        }

        if (!definition.supportsFormat(request.format())) {
            throw new BusinessException(
                    ErrorCode.REPORT_FORMAT_COMING_SOON,
                    request.format() + " format for " + definition.label() + " is coming soon"
            );
        }

        if (!generatorRegistry.supports(request.reportType(), request.format())) {
            throw new BusinessException(
                    ErrorCode.REPORT_GENERATOR_NOT_FOUND,
                    "Report generator is not available for " + definition.label() + " in " + request.format() + " format"
            );
        }

        validatePeriod(definition, request);
        validateOrgBillingEntity(orgId, definition, request);
        validateParty(orgId, definition, request);

        return definition;
    }

    private void validatePeriod(ReportDefinition definition, ReportRequestCreateRequest request) {
        if (request.periodFrom() == null) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD, "Report period from date is required");
        }

        if (request.periodTo() == null) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD, "Report period to date is required");
        }

        if (request.periodFrom().isAfter(request.periodTo())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REPORT_PERIOD,
                    "Report period from date cannot be after to date"
            );
        }

        if (definition.periodRule() == ReportPeriodRule.SINGLE_CALENDAR_MONTH) {
            boolean sameMonth = request.periodFrom().getYear() == request.periodTo().getYear()
                    && request.periodFrom().getMonth() == request.periodTo().getMonth();
            boolean completeMonth = request.periodFrom().getDayOfMonth() == 1
                    && request.periodTo().getDayOfMonth() == request.periodTo().lengthOfMonth();

            if (!sameMonth || !completeMonth) {
                throw new BusinessException(
                        ErrorCode.INVALID_REPORT_PERIOD,
                        definition.label() + " must cover one complete calendar month"
                );
            }
        }
    }

    private void validateOrgBillingEntity(
            String orgId,
            ReportDefinition definition,
            ReportRequestCreateRequest request
    ) {
        if (!definition.requiresOrgBillingEntity()) {
            return;
        }

        if (!hasText(request.orgBillingEntityId())) {
            throw new BusinessException(
                    ErrorCode.BILLING_ENTITY_NOT_FOUND,
                    "Org billing entity is required for " + definition.label()
            );
        }

        boolean exists = orgBillingEntityRepository
                .findByIdAndOrgId(request.orgBillingEntityId().trim(), orgId)
                .isPresent();

        if (!exists) {
            throw new BusinessException(ErrorCode.BILLING_ENTITY_NOT_FOUND, "Org billing entity not found");
        }
    }

    private void validateParty(String orgId, ReportDefinition definition, ReportRequestCreateRequest request) {
        if (!definition.requiresParty()) {
            return;
        }

        if (request.partyType() == null) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Party type is required for " + definition.label()
            );
        }

        if (!definition.allowedPartyTypes().contains(request.partyType())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    request.partyType() + " party type is not valid for " + definition.label()
            );
        }

        if (!hasText(request.partyId())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Party id is required for " + definition.label()
            );
        }

        String partyId = request.partyId().trim();

        switch (request.partyType()) {
            case CLIENT -> clientRepository
                    .findByIdAndOrgId(partyId, orgId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CLIENT_NOT_FOUND, "Client not found"));
            case CLIENT_BILLING_ENTITY -> clientBillingEntityRepository
                    .findByIdAndOrgId(partyId, orgId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.BILLING_ENTITY_NOT_FOUND,
                            "Client billing entity not found"
                    ));
            case VENDOR -> {
                Client vendor = clientRepository
                        .findByIdAndOrgId(partyId, orgId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CLIENT_NOT_FOUND, "Vendor not found"));

                if (!Boolean.TRUE.equals(vendor.getSupplier())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "Selected party is not a vendor");
                }
            }
        }
    }

    private ReportRequest findOwned(String orgId, String id) {
        validateRequiredText(id, "Report request id is required");

        return repository.findByIdAndOrgId(id.trim(), orgId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REPORT_REQUEST_NOT_FOUND,
                        "Report request not found"
                ));
    }

    private ReportRequestResponse toResponse(ReportRequest reportRequest) {
        boolean downloadable = reportRequest.getStatus() == ReportStatus.COMPLETED
                && hasText(reportRequest.getFileName());
        boolean refreshable = isRefreshable(reportRequest, downloadable);

        return new ReportRequestResponse(
                reportRequest.getId(),
                reportRequest.getOrgId(),
                reportRequest.getRequestedBy(),
                reportRequest.getCategory(),
                reportRequest.getReportType(),
                reportRequest.getFormat(),
                reportRequest.getStatus(),
                reportRequest.getPeriodFrom(),
                reportRequest.getPeriodTo(),
                reportRequest.getOrgBillingEntityId(),
                reportRequest.getPartyType(),
                reportRequest.getPartyId(),
                readFilters(reportRequest.getFiltersJson()),
                reportRequest.getDisplayName(),
                reportRequest.getFileName(),
                reportRequest.getOriginalFileName(),
                reportRequest.getContentType(),
                reportRequest.getFileSize(),
                reportRequest.getErrorMessage(),
                reportRequest.getAttemptCount(),
                reportRequest.getRequestedAt(),
                reportRequest.getStartedAt(),
                reportRequest.getCompletedAt(),
                reportRequest.getExpiresAt(),
                reportRequest.getDownloadCount(),
                reportRequest.getLastDownloadedAt(),
                reportRequest.getCreatedAt(),
                reportRequest.getUpdatedAt(),
                reportRequest.getCreatedBy(),
                reportRequest.getUpdatedBy(),
                downloadable,
                downloadable ? "/reports/requests/" + reportRequest.getId() + "/download" : null,
                refreshable,
                statusMessage(reportRequest)
        );
    }

    private boolean isRefreshable(ReportRequest reportRequest, boolean downloadable) {
        if (downloadable) {
            return false;
        }

        return switch (reportRequest.getStatus()) {
            case QUEUED, PROCESSING -> true;
            case FAILED -> safeAttemptCount(reportRequest) < properties.getMaxAttempts();
            case COMPLETED -> true;
            case CANCELLED, EXPIRED -> false;
        };
    }

    private String statusMessage(ReportRequest reportRequest) {
        return switch (reportRequest.getStatus()) {
            case QUEUED -> "Report is queued for generation";
            case PROCESSING -> "Report generation is in progress";
            case COMPLETED -> hasText(reportRequest.getFileName())
                    ? "Report generated successfully"
                    : "Report generation completed but the file is unavailable";
            case FAILED -> hasText(reportRequest.getErrorMessage())
                    ? reportRequest.getErrorMessage()
                    : "Report generation failed";
            case CANCELLED -> "Report request was cancelled";
            case EXPIRED -> "Report file has expired";
        };
    }

    private String writeFilters(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(filters);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid report filters");
        }
    }

    private Map<String, Object> readFilters(String filtersJson) {
        if (!hasText(filtersJson)) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(filtersJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private String buildDisplayName(ReportDefinition definition, ReportRequestCreateRequest request) {
        return definition.label()
                + " - "
                + request.periodFrom()
                + " to "
                + request.periodTo()
                + " ("
                + request.format()
                + ")";
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

    private int safeAttemptCount(ReportRequest reportRequest) {
        return reportRequest.getAttemptCount() == null ? 0 : reportRequest.getAttemptCount();
    }

    private void validateRequiredText(String value, String message) {
        if (!hasText(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
