package com.core.dataexchange.internal;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.core.dataexchange.model.DataExchangeResource;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;

@Component
public class DataExchangeHandlerRegistry {

    private final Map<DataExchangeResource, DataExchangeHandler> handlers;

    public DataExchangeHandlerRegistry(List<DataExchangeHandler> handlerList) {
        EnumMap<DataExchangeResource, DataExchangeHandler> map = new EnumMap<>(DataExchangeResource.class);
        for (DataExchangeHandler handler : handlerList) {
            if (map.put(handler.resource(), handler) != null) {
                throw new IllegalStateException("Duplicate data exchange handler for " + handler.resource());
            }
        }
        this.handlers = Map.copyOf(map);
    }

    public DataExchangeHandler get(DataExchangeResource resource) {
        DataExchangeHandler handler = handlers.get(resource);
        if (handler == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Data exchange is not configured for " + resource.name() + ".");
        }
        return handler;
    }

    public List<DataExchangeHandler> all() {
        return Arrays.stream(DataExchangeResource.values())
                .map(handlers::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
