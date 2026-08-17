package com.core.controllers;

import java.util.List;

import com.core.dtos.report.ReportRequestCreateRequest;
import com.core.dtos.report.ReportRequestPageRequest;
import com.core.dtos.report.ReportRequestResponse;
import com.core.dtos.report.ReportTypeOptionResponse;
import com.core.models.ReportRequest;
import com.core.reports.storage.ReportFileStorageService;
import com.core.reports.storage.ReportFileStorageService.StoredReportResource;
import com.core.security.SecurityContextUtil;
import com.core.services.ReportRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports/requests")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class ReportRequestController {

    private final ReportRequestService reportRequestService;
    private final SecurityContextUtil security;
    private final ReportFileStorageService reportFileStorageService;

    @GetMapping("/metadata")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public List<ReportTypeOptionResponse> metadata() {
        return reportRequestService.metadata();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('REPORT_REQUEST')")
    public ReportRequestResponse requestReport(@Valid @RequestBody ReportRequestCreateRequest request) {
        return reportRequestService.requestReport(security.orgId(), security.userId(), request);
    }

    @PostMapping("/{id}/refresh")
    @PreAuthorize("hasAuthority('REPORT_REQUEST')")
    public ReportRequestResponse refresh(@PathVariable String id) {
        return reportRequestService.refresh(security.orgId(), id);
    }

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public Page<ReportRequestResponse> page(@RequestBody(required = false) ReportRequestPageRequest request) {
        ReportRequestPageRequest pageRequest = request == null
                ? new ReportRequestPageRequest(null, null, null, null, "", 0, 10, "createdAt", Sort.Direction.DESC)
                : request;

        Pageable pageable = PageRequest.of(
                pageRequest.page(),
                pageRequest.size(),
                Sort.by(pageRequest.direction(), pageRequest.sortBy())
        );

        return reportRequestService.page(
                security.orgId(),
                pageRequest.status(),
                pageRequest.category(),
                pageRequest.reportType(),
                pageRequest.format(),
                pageRequest.searchstr(),
                pageable
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ReportRequestResponse get(@PathVariable String id) {
        return reportRequestService.get(security.orgId(), id);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('REPORT_DOWNLOAD')")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        ReportRequest reportRequest = reportRequestService.validateDownloadRequest(security.orgId(), id);
        StoredReportResource storedResource = reportFileStorageService.load(reportRequest.getFileName());
        reportRequestService.markDownloaded(security.orgId(), id);

        String downloadFileName = reportRequest.getOriginalFileName() != null
                && !reportRequest.getOriginalFileName().isBlank()
                ? reportRequest.getOriginalFileName()
                : reportRequest.getFileName();

        MediaType mediaType = reportRequest.getContentType() == null
                || reportRequest.getContentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(reportRequest.getContentType());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadFileName + "\"")
                .contentType(mediaType)
                .contentLength(storedResource.contentLength())
                .body(storedResource.resource());
    }
}
