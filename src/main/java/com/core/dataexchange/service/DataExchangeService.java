package com.core.dataexchange.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.core.dataexchange.dto.DataExchangeMetadata;
import com.core.dataexchange.dto.DataExchangeImportResult;
import com.core.dataexchange.internal.CsvCodec;
import com.core.dataexchange.internal.CsvRow;
import com.core.dataexchange.internal.DataExchangeHandler;
import com.core.dataexchange.internal.DataExchangeHandlerRegistry;
import com.core.dataexchange.model.DataExchangeResource;

@Service
public class DataExchangeService {

    private static final String SCHEMA_VERSION = "1.0";

    private final DataExchangeHandlerRegistry registry;
    private final CsvCodec csvCodec;
    private final DataExchangeAccessService accessService;
    private final int maxRows;
    private final long maxFileSizeBytes;

    public DataExchangeService(
            DataExchangeHandlerRegistry registry,
            CsvCodec csvCodec,
            DataExchangeAccessService accessService,
            @Value("${fleetovo.data-exchange.max-rows:5000}") int maxRows,
            @Value("${fleetovo.data-exchange.max-file-size-bytes:5242880}") long maxFileSizeBytes
    ) {
        this.registry = registry;
        this.csvCodec = csvCodec;
        this.accessService = accessService;
        this.maxRows = maxRows;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Transactional(readOnly = true)
    public List<DataExchangeMetadata> resources() {
        accessService.requireModuleView();

        return registry.all().stream()
                .filter(handler ->
                        accessService.canView(handler.resource())
                )
                .map(this::metadata)
                .toList();
    }

    @Transactional(readOnly = true)
    public DataExchangeMetadata metadata(DataExchangeResource resource) {
        accessService.requireView(resource);
        return metadata(registry.get(resource));
    }

    @Transactional(readOnly = true)
    public byte[] template(DataExchangeResource resource) {
        accessService.requireView(resource);
        DataExchangeHandler handler = registry.get(resource);
        return csvCodec.write(headers(handler), List.of());
    }

    @Transactional(readOnly = true)
    public byte[] export(DataExchangeResource resource, String orgId) {
        accessService.requireExport(resource);
        DataExchangeHandler handler = registry.get(resource);
        List<Map<String, String>> rows = handler.exportRows(orgId);
        return csvCodec.write(headers(handler), rows);
    }

    @Transactional(readOnly = true)
    public DataExchangeImportResult validate(
            DataExchangeResource resource,
            MultipartFile file,
            String orgId
    ) {
        /*
         * Check baseline import access before parsing or inspecting the file.
         */
        accessService.requireImport(resource, 0, 0);

        DataExchangeHandler handler = registry.get(resource);
        List<CsvRow> rows = parse(file, handler);

        DataExchangeImportResult result =
                handler.validate(rows, orgId);

        /*
         * Only valid files have executable create/update operations.
         * Require the corresponding granular authorities.
         */
        if (result.valid()) {
            accessService.requireImport(
                    resource,
                    result.createCount(),
                    result.updateCount()
            );
        }

        return result;
    }

    @Transactional
    public DataExchangeImportResult importCsv(
            DataExchangeResource resource,
            MultipartFile file,
            String orgId
    ) {
        /*
         * Block callers without baseline import access before processing the file.
         */
        accessService.requireImport(resource, 0, 0);

        DataExchangeHandler handler = registry.get(resource);
        List<CsvRow> rows = parse(file, handler);

        /*
         * The handler prepares once. The exact prepared create/update counts are
         * authorized before those same prepared commands are applied.
         */
        return handler.importRows(
                rows,
                orgId,
                (creates, updates) -> accessService.requireImport(
                        resource,
                        creates,
                        updates
                )
        );
    }

    private List<CsvRow> parse(MultipartFile file, DataExchangeHandler handler) {
        return csvCodec.parse(file, headers(handler), maxFileSizeBytes, maxRows);
    }

    private List<String> headers(DataExchangeHandler handler) {
        return handler.columns().stream().map(column -> column.name()).toList();
    }

    private DataExchangeMetadata metadata(DataExchangeHandler handler) {
        return new DataExchangeMetadata(
                handler.resource(),
                handler.resource().path(),
                handler.displayName(),
                SCHEMA_VERSION,
                maxRows,
                maxFileSizeBytes,
                handler.columns(),
                handler.notes()
        );
    }
}
