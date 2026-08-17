package com.core.controllers.config;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.core.dtos.config.GarageRequest;
import com.core.models.CityGarage;
import com.core.security.SecurityContextUtil;
import com.core.services.config.CityGarageService;

import lombok.RequiredArgsConstructor;

@RequestMapping("/config/city-garage")
@RestController
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class CityGarageController {

    private final SecurityContextUtil security;
    private final CityGarageService service;

    @PostMapping
    @PreAuthorize("hasAuthority('GARAGE_ADD')")
    public CityGarage addGarage(@RequestBody GarageRequest request) {
        return service.save(security.orgId(), request);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('GARAGE_EDIT')")
    public CityGarage updateGarage(@RequestBody GarageRequest request) {
        return service.update(security.orgId(), request);
    }

    @GetMapping("/{city}")
    @PreAuthorize("hasAuthority('GARAGE_VIEW')")
    public CityGarage getGarage(@PathVariable String city) {
        return service.get(security.orgId(), city);
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('GARAGE_VIEW')")
    public List<CityGarage> getGarageList() {
        return service.getList(security.orgId());
    }
}