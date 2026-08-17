package com.core.dataexchange.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.core.dataexchange.dto.DataExchangeColumn;
import com.core.dataexchange.dto.DataExchangeImportIssue;
import com.core.dataexchange.internal.AbstractDataExchangeHandler;
import com.core.dataexchange.internal.CsvRow;
import com.core.dataexchange.internal.PreparedImport;
import com.core.dataexchange.internal.RowValidation;
import com.core.dataexchange.model.DataExchangeResource;
import com.core.models.MasterVehicle;
import com.core.models.enums.VehicleStatus;
import com.core.repositories.MasterVehicleRepository;

@Component
public class MasterVehicleDataExchangeHandler
        extends AbstractDataExchangeHandler<MasterVehicleDataExchangeHandler.Command> {

    private final MasterVehicleRepository repository;

    public MasterVehicleDataExchangeHandler(MasterVehicleRepository repository) {
        this.repository = repository;
    }

    @Override
    public DataExchangeResource resource() {
        return DataExchangeResource.MASTER_VEHICLE;
    }

    @Override
    public String displayName() {
        return "Master Vehicles";
    }

    @Override
    public List<DataExchangeColumn> columns() {
        return List.of(
                DataExchangeColumn.identifier("record_id", "Record ID", false,
                        "Leave blank to create. Keep the exported ID to update."),
                DataExchangeColumn.text("name", "Name", true, "Vehicle display name."),
                DataExchangeColumn.text("fuel_system", "Fuel System", false, "Petrol, diesel, electric, hybrid, etc."),
                DataExchangeColumn.text("fuel_consumption", "Fuel Consumption", false, "Display value."),
                DataExchangeColumn.text("vehicle_color", "Vehicle Color", false, "Display value."),
                DataExchangeColumn.text("category", "Category", false, "Sedan, SUV, MUV, luxury, etc."),
                DataExchangeColumn.text("brand", "Brand", false, "Manufacturer or brand."),
                DataExchangeColumn.text("seats", "Seats", false, "Display value."),
                DataExchangeColumn.text("doors", "Doors", false, "Display value."),
                DataExchangeColumn.text("transmission_type", "Transmission Type", false, "Automatic, manual, etc."),
                DataExchangeColumn.text("horse_power", "Horse Power", false, "Display value."),
                DataExchangeColumn.text("vehicle_class", "Vehicle Class", false, "Display value."),
                DataExchangeColumn.text("model_year", "Model Year", false, "Display value."),
                DataExchangeColumn.text("performance", "Performance", false, "Display value."),
                DataExchangeColumn.integer("dimension_length", "Length", false, "Numeric dimension."),
                DataExchangeColumn.integer("dimension_width", "Width", false, "Numeric dimension."),
                DataExchangeColumn.integer("dimension_height", "Height", false, "Numeric dimension."),
                DataExchangeColumn.integer("dimension_wheelbase", "Wheelbase", false, "Numeric dimension."),
                DataExchangeColumn.text("slug", "Slug", false, "URL-safe slug; must be unique when provided."),
                DataExchangeColumn.integer("rating", "Rating", false, "Non-negative ranking value."),
                DataExchangeColumn.integer("popularity", "Popularity", false, "Non-negative ranking value."),
                DataExchangeColumn.bool("chauffeur_driven", "Chauffeur Driven", false, "TRUE or FALSE."),
                DataExchangeColumn.enumeration("status", "Status", true, "Vehicle publication status.", enumValues(VehicleStatus.class)),
                DataExchangeColumn.text("remarks", "Remarks", false, "Internal remarks.")
        );
    }

    @Override
    public List<String> notes() {
        return List.of(
                "Vehicle image filenames are intentionally excluded; CSV import never changes images.",
                "Deleting a CSV row never deletes the master vehicle.",
                "Use record_id for updates; vehicle name is not treated as an update key."
        );
    }

    @Override
    public List<Map<String, String>> exportRows(String orgId) {
        return repository.findByOrgId(orgId).stream().map(vehicle -> row(
                "record_id", vehicle.getId(),
                "name", vehicle.getName(),
                "fuel_system", vehicle.getFuelSystem(),
                "fuel_consumption", vehicle.getFuelConsumption(),
                "vehicle_color", vehicle.getVehicleColor(),
                "category", vehicle.getCategory(),
                "brand", vehicle.getBrand(),
                "seats", vehicle.getSeats(),
                "doors", vehicle.getDoors(),
                "transmission_type", vehicle.getTransmissionType(),
                "horse_power", vehicle.getHorsePower(),
                "vehicle_class", vehicle.getVehicleClass(),
                "model_year", vehicle.getModelYear(),
                "performance", vehicle.getPerformance(),
                "dimension_length", vehicle.getDimension_length(),
                "dimension_width", vehicle.getDimension_width(),
                "dimension_height", vehicle.getDimension_height(),
                "dimension_wheelbase", vehicle.getDimension_wheelbase(),
                "slug", vehicle.getSlug(),
                "rating", vehicle.getRating(),
                "popularity", vehicle.getPopularity(),
                "chauffeur_driven", vehicle.getChauffeurDriven(),
                "status", vehicle.getStatus(),
                "remarks", vehicle.getRemarks()
        )).toList();
    }

    @Override
    protected PreparedImport<Command> prepare(List<CsvRow> rows, String orgId) {
        List<DataExchangeImportIssue> errors = new ArrayList<>();
        List<Command> commands = new ArrayList<>();
        Map<String, MasterVehicle> existingById = new HashMap<>();
        Map<String, String> existingSlugToId = new HashMap<>();
        for (MasterVehicle vehicle : repository.findByOrgId(orgId)) {
            existingById.put(vehicle.getId(), vehicle);
            if (vehicle.getSlug() != null && !vehicle.getSlug().isBlank()) {
                existingSlugToId.put(vehicle.getSlug().toLowerCase(Locale.ROOT), vehicle.getId());
            }
        }

        Map<String, Long> seenIds = new HashMap<>();
        Map<String, Long> seenSlugs = new HashMap<>();
        int creates = 0;
        int updates = 0;
        int unchanged = 0;

        for (CsvRow row : rows) {
            int errorStart = errors.size();
            RowValidation v = new RowValidation(row, errors);
            String id = v.optional("record_id", 40);
            validateDuplicateRecordId(id, row.rowNumber(), seenIds, errors);
            MasterVehicle existing = id == null ? null : existingById.get(id);
            if (id != null && existing == null) {
                v.issue("record_id", "RECORD_NOT_FOUND", "Master vehicle does not exist in this organization.");
            }

            String name = v.required("name", 100);
            String fuelSystem = v.optional("fuel_system", 50);
            String fuelConsumption = v.optional("fuel_consumption", 50);
            String vehicleColor = v.optional("vehicle_color", 50);
            String category = v.optional("category", 50);
            String brand = v.optional("brand", 50);
            String seats = v.optional("seats", 50);
            String doors = v.optional("doors", 50);
            String transmissionType = v.optional("transmission_type", 50);
            String horsePower = v.optional("horse_power", 50);
            String vehicleClass = v.optional("vehicle_class", 50);
            String modelYear = v.optional("model_year", 10);
            String performance = v.optional("performance", 50);
            Integer length = v.integer("dimension_length", false, 0);
            Integer width = v.integer("dimension_width", false, 0);
            Integer height = v.integer("dimension_height", false, 0);
            Integer wheelbase = v.integer("dimension_wheelbase", false, 0);
            String slug = v.optional("slug", 100);
            Integer rating = v.integer("rating", false, 0);
            Integer popularity = v.integer("popularity", false, 0);
            Boolean chauffeurDriven = v.bool("chauffeur_driven", false);
            VehicleStatus status = v.enumeration("status", true, VehicleStatus.class);
            String remarks = v.optional("remarks", 255);

            if (slug != null) {
                String slugKey = slug.toLowerCase(Locale.ROOT);
                Long previous = seenSlugs.putIfAbsent(slugKey, row.rowNumber());
                if (previous != null) {
                    v.issue("slug", "DUPLICATE_IN_FILE", "Slug is already used at CSV row " + previous + ".");
                }
                String existingId = existingSlugToId.get(slugKey);
                if (existingId != null && !existingId.equals(id)) {
                    v.issue("slug", "DUPLICATE_IN_DATABASE", "Slug is already used by another master vehicle.");
                }
            }

            if (errors.size() > errorStart) {
                continue;
            }

            Command command = new Command(id, name, fuelSystem, fuelConsumption, vehicleColor, category, brand,
                    seats, doors, transmissionType, horsePower, vehicleClass, modelYear, performance,
                    length, width, height, wheelbase, slug, rating, popularity,
                    chauffeurDriven == null ? Boolean.FALSE : chauffeurDriven, status, remarks);
            if (existing == null) {
                creates++;
                commands.add(command);
            } else if (changed(existing, command)) {
                updates++;
                commands.add(command);
            } else {
                unchanged++;
            }
        }
        return new PreparedImport<>(commands, rows.size(), creates, updates, unchanged, errors);
    }

    @Override
    protected void apply(List<Command> commands, String orgId) {
        for (Command command : commands) {
            MasterVehicle vehicle = command.id() == null
                    ? new MasterVehicle()
                    : repository.findByIdAndOrgId(command.id(), orgId).orElseThrow();
            if (command.id() == null) {
                vehicle.setOrgId(orgId);
            }
            vehicle.setName(command.name());
            vehicle.setFuelSystem(command.fuelSystem());
            vehicle.setFuelConsumption(command.fuelConsumption());
            vehicle.setVehicleColor(command.vehicleColor());
            vehicle.setCategory(command.category());
            vehicle.setBrand(command.brand());
            vehicle.setSeats(command.seats());
            vehicle.setDoors(command.doors());
            vehicle.setTransmissionType(command.transmissionType());
            vehicle.setHorsePower(command.horsePower());
            vehicle.setVehicleClass(command.vehicleClass());
            vehicle.setModelYear(command.modelYear());
            vehicle.setPerformance(command.performance());
            vehicle.setDimension_length(command.length());
            vehicle.setDimension_width(command.width());
            vehicle.setDimension_height(command.height());
            vehicle.setDimension_wheelbase(command.wheelbase());
            vehicle.setSlug(command.slug());
            vehicle.setRating(command.rating());
            vehicle.setPopularity(command.popularity());
            vehicle.setChauffeurDriven(command.chauffeurDriven());
            vehicle.setStatus(command.status());
            vehicle.setRemarks(command.remarks());
            repository.save(vehicle);
        }
    }

    private boolean changed(MasterVehicle v, Command c) {
        return !same(v.getName(), c.name())
                || !same(blankToNull(v.getFuelSystem()), c.fuelSystem())
                || !same(blankToNull(v.getFuelConsumption()), c.fuelConsumption())
                || !same(blankToNull(v.getVehicleColor()), c.vehicleColor())
                || !same(blankToNull(v.getCategory()), c.category())
                || !same(blankToNull(v.getBrand()), c.brand())
                || !same(blankToNull(v.getSeats()), c.seats())
                || !same(blankToNull(v.getDoors()), c.doors())
                || !same(blankToNull(v.getTransmissionType()), c.transmissionType())
                || !same(blankToNull(v.getHorsePower()), c.horsePower())
                || !same(blankToNull(v.getVehicleClass()), c.vehicleClass())
                || !same(blankToNull(v.getModelYear()), c.modelYear())
                || !same(blankToNull(v.getPerformance()), c.performance())
                || !same(v.getDimension_length(), c.length())
                || !same(v.getDimension_width(), c.width())
                || !same(v.getDimension_height(), c.height())
                || !same(v.getDimension_wheelbase(), c.wheelbase())
                || !same(blankToNull(v.getSlug()), c.slug())
                || !same(v.getRating(), c.rating())
                || !same(v.getPopularity(), c.popularity())
                || !same(Boolean.TRUE.equals(v.getChauffeurDriven()), c.chauffeurDriven())
                || !same(v.getStatus(), c.status())
                || !same(blankToNull(v.getRemarks()), c.remarks());
    }

    protected record Command(
            String id, String name, String fuelSystem, String fuelConsumption, String vehicleColor,
            String category, String brand, String seats, String doors, String transmissionType,
            String horsePower, String vehicleClass, String modelYear, String performance,
            Integer length, Integer width, Integer height, Integer wheelbase, String slug,
            Integer rating, Integer popularity, Boolean chauffeurDriven, VehicleStatus status, String remarks
    ) {
    }
}
