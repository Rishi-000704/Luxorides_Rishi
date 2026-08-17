package com.core.controllers.config;

import com.core.dtos.payment.PaymentGatewayConfigRequest;
import com.core.dtos.payment.PaymentGatewayConfigResponse;
import com.core.models.enums.PaymentGateway;
import com.core.security.SecurityContextUtil;
import com.core.services.PaymentGatewayConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/config/payment-gateway")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class PaymentGatewayConfigController {

    private final PaymentGatewayConfigService service;
    private final SecurityContextUtil security;

    @GetMapping
    @PreAuthorize("hasAuthority('ORG_PAYMENT_GATEWAY_CONFIG_VIEW')")
    public PaymentGatewayConfigResponse getCurrent() {
        return service.getCurrent(security.orgId());
    }

    @GetMapping("/{gateway}")
    @PreAuthorize("hasAuthority('ORG_PAYMENT_GATEWAY_CONFIG_VIEW')")
    public PaymentGatewayConfigResponse getByGateway(@PathVariable PaymentGateway gateway) {
        return service.getByGateway(security.orgId(), gateway);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORG_PAYMENT_GATEWAY_CONFIG_EDIT')")
    public PaymentGatewayConfigResponse upsert(@RequestBody PaymentGatewayConfigRequest request) {
        return service.upsert(security.orgId(), request);
    }
}