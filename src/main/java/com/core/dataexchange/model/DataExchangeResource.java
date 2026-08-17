package com.core.dataexchange.model;

import java.util.Arrays;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;

public enum DataExchangeResource {
    CLIENT("clients"),
    VENDOR("vendors"),
    PACKAGE("packages"),
    MASTER_VEHICLE("master-vehicles"),
    FLEET_VEHICLE("fleet-vehicles"),
    DRIVER("drivers");

    private final String path;

    DataExchangeResource(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }

    public static DataExchangeResource fromPath(String value) {
        return Arrays.stream(values())
                .filter(resource -> resource.path.equalsIgnoreCase(value)
                        || resource.name().equalsIgnoreCase(value.replace('-', '_')))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "Unsupported data exchange resource: " + value
                ));
    }
}
