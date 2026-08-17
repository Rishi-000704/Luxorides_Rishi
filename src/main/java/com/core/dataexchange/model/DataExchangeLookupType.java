package com.core.dataexchange.model;

import java.util.Arrays;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;

public enum DataExchangeLookupType {
    CLIENT("clients"),
    VENDOR("vendors"),
    MASTER_VEHICLE("master-vehicles");

    private final String path;

    DataExchangeLookupType(String path) {
        this.path = path;
    }

    public static DataExchangeLookupType fromPath(String value) {
        return Arrays.stream(values())
                .filter(type -> type.path.equalsIgnoreCase(value)
                        || type.name().equalsIgnoreCase(value.replace('-', '_')))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "Unsupported lookup type: " + value));
    }
}
