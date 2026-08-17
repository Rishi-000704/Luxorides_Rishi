package com.core.controllers.config;

import com.core.dtos.communication.SmsProviderConfigRequest;
import com.core.dtos.communication.SmsProviderConfigResponse;
import com.core.services.SmsProviderConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.security.SecurityContextUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/config/sms-provider")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class SmsProviderConfigController {

    private final SmsProviderConfigService service;
    private final SecurityContextUtil security;

    @GetMapping
    @PreAuthorize("hasAuthority('ORG_NOTIFICATION_CONFIG_VIEW')")
    public SmsProviderConfigResponse getCurrent() {
        return service.getCurrent(security.orgId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORG_NOTIFICATION_CONFIG_EDIT')")
    public SmsProviderConfigResponse upsert(@RequestBody SmsProviderConfigRequest request) {
        return service.upsert(security.orgId(), request);
    }
}