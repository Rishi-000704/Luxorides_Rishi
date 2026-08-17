package com.core.controllers.config;

import com.core.dtos.communication.EmailProviderConfigRequest;
import com.core.dtos.communication.EmailProviderConfigResponse;
import com.core.services.EmailProviderConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.security.SecurityContextUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/config/email-provider")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class EmailProviderConfigController {

    private final EmailProviderConfigService service;
    private final SecurityContextUtil security;

    @GetMapping
    @PreAuthorize("hasAuthority('ORG_NOTIFICATION_CONFIG_VIEW')")
    public EmailProviderConfigResponse getCurrent() {
        return service.getCurrent(security.orgId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORG_NOTIFICATION_CONFIG_EDIT')")
    public EmailProviderConfigResponse upsert(@RequestBody EmailProviderConfigRequest request) {
        return service.upsert(security.orgId(), request);
    }
}