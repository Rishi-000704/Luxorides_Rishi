package com.core.controllers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.core.dataexchange.dto.DataExchangeImportResult;
import com.core.dataexchange.dto.DataExchangeLookupItem;
import com.core.dataexchange.dto.DataExchangeMetadata;
import com.core.dataexchange.model.DataExchangeLookupType;
import com.core.dataexchange.model.DataExchangeResource;
import com.core.dataexchange.service.DataExchangeLookupService;
import com.core.dataexchange.service.DataExchangeService;
import com.core.security.SecurityContextUtil;

@RestController
@RequestMapping("/data-exchange")
@PreAuthorize("@securityContextUtil.isEmployee()")
public class DataExchangeController {

    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv");

    private final DataExchangeService service;
    private final DataExchangeLookupService lookupService;
    private final SecurityContextUtil security;

    public DataExchangeController(DataExchangeService service,
                                  DataExchangeLookupService lookupService,
                                  SecurityContextUtil security) {
        this.service = service;
        this.lookupService = lookupService;
        this.security = security;
    }

    @GetMapping("/resources")
    @PreAuthorize("""
        @securityContextUtil.isEmployee()
        and hasAuthority('DATA_EXCHANGE_VIEW')
        """)
    public List<DataExchangeMetadata> resources() {
        return service.resources();
    }

    @GetMapping("/{resource}/metadata")
    @PreAuthorize("""
        @securityContextUtil.isEmployee()
        and hasAuthority('DATA_EXCHANGE_VIEW')
        """)
    public DataExchangeMetadata metadata(@PathVariable String resource) {
        return service.metadata(DataExchangeResource.fromPath(resource));
    }

    @GetMapping("/{resource}/template")
    @PreAuthorize("""
        @securityContextUtil.isEmployee()
        and hasAuthority('DATA_EXCHANGE_VIEW')
        """)
    public ResponseEntity<byte[]> template(@PathVariable String resource) {
        DataExchangeResource resolved = DataExchangeResource.fromPath(resource);
        return csv(service.template(resolved), resolved.path() + "-template.csv");
    }

    @GetMapping("/{resource}/export")
    @PreAuthorize("""
        @securityContextUtil.isEmployee()
        and hasAuthority('DATA_EXCHANGE_VIEW')
        and hasAuthority('DATA_EXCHANGE_EXPORT')
        """)
    public ResponseEntity<byte[]> export(@PathVariable String resource) {
        DataExchangeResource resolved = DataExchangeResource.fromPath(resource);
        String fileName = resolved.path() + "-" + LocalDate.now() + ".csv";
        return csv(service.export(resolved, security.orgId()), fileName);
    }

    @PostMapping(path = "/{resource}/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("""
        @securityContextUtil.isEmployee()
        and hasAuthority('DATA_EXCHANGE_VIEW')
        and hasAuthority('DATA_EXCHANGE_IMPORT')
        """)
    public DataExchangeImportResult validate(@PathVariable String resource,
                                             @RequestParam("file") MultipartFile file) {
        return service.validate(DataExchangeResource.fromPath(resource), file, security.orgId());
    }

    @PostMapping(path = "/{resource}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("""
        @securityContextUtil.isEmployee()
        and hasAuthority('DATA_EXCHANGE_VIEW')
        and hasAuthority('DATA_EXCHANGE_IMPORT')
        """)
    public DataExchangeImportResult importCsv(@PathVariable String resource,
                                              @RequestParam("file") MultipartFile file) {
        return service.importCsv(DataExchangeResource.fromPath(resource), file, security.orgId());
    }

    @GetMapping("/lookups/{type}")
    @PreAuthorize("""
        @securityContextUtil.isEmployee()
        and hasAuthority('DATA_EXCHANGE_VIEW')
        """)
    public List<DataExchangeLookupItem> lookup(@PathVariable String type) {
        return lookupService.get(DataExchangeLookupType.fromPath(type), security.orgId());
    }

    private ResponseEntity<byte[]> csv(byte[] bytes, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(CSV_MEDIA_TYPE);
        headers.setContentLength(bytes.length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl("no-store, max-age=0");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
