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
import com.core.models.Client;
import com.core.models.FleetVehicle;
import com.core.models.MasterVehicle;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.enums.OwnershipType;
import com.core.repositories.ClientRepository;
import com.core.repositories.FleetVehicleRepository;
import com.core.repositories.MasterVehicleRepository;

@Component
public class FleetVehicleDataExchangeHandler
        extends AbstractDataExchangeHandler<FleetVehicleDataExchangeHandler.Command> {

    private final FleetVehicleRepository fleetVehicleRepository;
    private final MasterVehicleRepository masterVehicleRepository;
    private final ClientRepository clientRepository;

    public FleetVehicleDataExchangeHandler(FleetVehicleRepository fleetVehicleRepository,
                                           MasterVehicleRepository masterVehicleRepository,
                                           ClientRepository clientRepository) {
        this.fleetVehicleRepository = fleetVehicleRepository;
        this.masterVehicleRepository = masterVehicleRepository;
        this.clientRepository = clientRepository;
    }

    @Override
    public DataExchangeResource resource() {
        return DataExchangeResource.FLEET_VEHICLE;
    }

    @Override
    public String displayName() {
        return "Fleet Vehicles";
    }

    @Override
    public List<DataExchangeColumn> columns() {
        return List.of(
                DataExchangeColumn.identifier("record_id", "Record ID", false,
                        "Leave blank to create. Keep the exported ID to update."),
                DataExchangeColumn.identifier("master_vehicle_id", "Master Vehicle ID", true,
                        "Must reference a master vehicle in the same organization."),
                DataExchangeColumn.identifier("client_id", "Client/Vendor ID", false,
                        "Required only for CLIENT ownership."),
                DataExchangeColumn.text("registration_number", "Registration Number", true,
                        "Unique registration number. Spaces are removed and letters are uppercased."),
                DataExchangeColumn.enumeration("ownership", "Ownership", true,
                        "ORG for own fleet or CLIENT for client/vendor-owned fleet.", enumValues(OwnershipType.class)),
                DataExchangeColumn.text("garage_formatted_address", "Garage Address", true,
                        "Complete garage or parking address."),
                DataExchangeColumn.text("garage_google_place_id", "Google Place ID", false,
                        "Optional verified Google place ID."),
                DataExchangeColumn.decimal("garage_latitude", "Garage Latitude", false,
                        "Optional latitude."),
                DataExchangeColumn.decimal("garage_longitude", "Garage Longitude", false,
                        "Optional longitude.")
        );
    }

    @Override
    public List<String> notes() {
        return List.of(
                "The import does not call Google Maps. Coordinates and place ID are stored only when supplied.",
                "When ownership is ORG, client_id must be blank. When ownership is CLIENT, client_id is required.",
                "Deleting a CSV row never deletes the fleet vehicle."
        );
    }

    @Override
    public List<Map<String, String>> exportRows(String orgId) {
        return fleetVehicleRepository.findByOrgId(orgId).stream().map(vehicle -> row(
                "record_id", vehicle.getId(),
                "master_vehicle_id", vehicle.getMasterVehicleId(),
                "client_id", vehicle.getClientId(),
                "registration_number", vehicle.getRegistrationNumber(),
                "ownership", vehicle.getOwnership(),
                "garage_formatted_address", value(vehicle.getGarageLocation(), AddressSnapshot::getFormattedAddress),
                "garage_google_place_id", value(vehicle.getGarageLocation(), AddressSnapshot::getGooglePlaceId),
                "garage_latitude", value(vehicle.getGarageLocation(), AddressSnapshot::getLatitude),
                "garage_longitude", value(vehicle.getGarageLocation(), AddressSnapshot::getLongitude)
        )).toList();
    }

    @Override
    protected PreparedImport<Command> prepare(List<CsvRow> rows, String orgId) {
        List<DataExchangeImportIssue> errors = new ArrayList<>();
        List<Command> commands = new ArrayList<>();
        Map<String, FleetVehicle> existingById = new HashMap<>();
        Map<String, String> registrationToId = new HashMap<>();
        for (FleetVehicle vehicle : fleetVehicleRepository.findByOrgId(orgId)) {
            existingById.put(vehicle.getId(), vehicle);
            if (vehicle.getRegistrationNumber() != null) {
                registrationToId.put(normalizeRegistration(vehicle.getRegistrationNumber()), vehicle.getId());
            }
        }
        Map<String, MasterVehicle> masterVehiclesById = new HashMap<>();
        for (MasterVehicle vehicle : masterVehicleRepository.findByOrgId(orgId)) {
            masterVehiclesById.put(vehicle.getId(), vehicle);
        }
        Map<String, Client> clientsById = new HashMap<>();
        for (Client client : clientRepository.findByOrgId(orgId)) {
            clientsById.put(client.getId(), client);
        }

        Map<String, Long> seenIds = new HashMap<>();
        Map<String, Long> seenRegistrations = new HashMap<>();
        int creates = 0;
        int updates = 0;
        int unchanged = 0;

        for (CsvRow row : rows) {
            int errorStart = errors.size();
            RowValidation v = new RowValidation(row, errors);
            String id = v.optional("record_id", 40);
            validateDuplicateRecordId(id, row.rowNumber(), seenIds, errors);
            FleetVehicle existing = id == null ? null : existingById.get(id);
            if (id != null && existing == null) {
                v.issue("record_id", "RECORD_NOT_FOUND", "Fleet vehicle does not exist in this organization.");
            }

            String masterVehicleId = v.required("master_vehicle_id", 40);
            if (masterVehicleId != null && !masterVehiclesById.containsKey(masterVehicleId)) {
                v.issue("master_vehicle_id", "REFERENCE_NOT_FOUND",
                        "Master vehicle does not exist in this organization.");
            }

            OwnershipType ownership = v.enumeration("ownership", true, OwnershipType.class);
            String clientId = v.optional("client_id", 40);
            if (ownership == OwnershipType.ORG && clientId != null) {
                v.issue("client_id", "INVALID_OWNERSHIP_REFERENCE", "client_id must be blank for ORG ownership.");
            }
            if (ownership == OwnershipType.CLIENT) {
                if (clientId == null) {
                    v.issue("client_id", "REQUIRED", "client_id is required for CLIENT ownership.");
                } else if (!clientsById.containsKey(clientId)) {
                    v.issue("client_id", "REFERENCE_NOT_FOUND", "Client/vendor does not exist in this organization.");
                }
            }

            String rawRegistration = v.required("registration_number", 40);
            String registration = rawRegistration == null ? null : normalizeRegistration(rawRegistration);
            if (registration != null && registration.length() > 20) {
                v.issue("registration_number", "TOO_LONG",
                        "Normalized registration number must not exceed 20 characters.");
            }
            if (registration != null) {
                Long previous = seenRegistrations.putIfAbsent(registration, row.rowNumber());
                if (previous != null) {
                    v.issue("registration_number", "DUPLICATE_IN_FILE",
                            "Registration number is already used at CSV row " + previous + ".");
                }
                String existingId = registrationToId.get(registration);
                if (existingId != null && !existingId.equals(id)) {
                    v.issue("registration_number", "DUPLICATE_IN_DATABASE",
                            "Registration number is already used by another fleet vehicle.");
                }
            }

            String garageAddress = v.required("garage_formatted_address", 300);
            String placeId = v.optional("garage_google_place_id", 100);
            Double latitude = v.decimalDouble("garage_latitude", false);
            Double longitude = v.decimalDouble("garage_longitude", false);
            if ((latitude == null) != (longitude == null)) {
                v.issue(latitude == null ? "garage_latitude" : "garage_longitude",
                        "INCOMPLETE_COORDINATES", "Latitude and longitude must be supplied together.");
            }
            if (latitude != null && (latitude < -90 || latitude > 90)) {
                v.issue("garage_latitude", "OUT_OF_RANGE", "Latitude must be between -90 and 90.");
            }
            if (longitude != null && (longitude < -180 || longitude > 180)) {
                v.issue("garage_longitude", "OUT_OF_RANGE", "Longitude must be between -180 and 180.");
            }

            if (errors.size() > errorStart) {
                continue;
            }

            Command command = new Command(id, masterVehicleId,
                    ownership == OwnershipType.CLIENT ? clientId : null,
                    registration, ownership, garageAddress, placeId, latitude, longitude);
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
            FleetVehicle vehicle = command.id() == null
                    ? new FleetVehicle()
                    : fleetVehicleRepository.findByIdAndOrgId(command.id(), orgId).orElseThrow();
            if (command.id() == null) {
                vehicle.setOrgId(orgId);
            }
            vehicle.setMasterVehicleId(command.masterVehicleId());
            vehicle.setClientId(command.clientId());
            vehicle.setRegistrationNumber(command.registrationNumber());
            vehicle.setOwnership(command.ownership());
            vehicle.setGarageLocation(new AddressSnapshot(
                    command.garageAddress(), command.placeId(), command.latitude(), command.longitude()));
            fleetVehicleRepository.save(vehicle);
        }
    }

    private boolean changed(FleetVehicle vehicle, Command c) {
        return !same(vehicle.getMasterVehicleId(), c.masterVehicleId())
                || !same(blankToNull(vehicle.getClientId()), c.clientId())
                || !same(normalizeRegistration(vehicle.getRegistrationNumber()), c.registrationNumber())
                || !same(vehicle.getOwnership(), c.ownership())
                || !same(value(vehicle.getGarageLocation(), AddressSnapshot::getFormattedAddress), c.garageAddress())
                || !same(value(vehicle.getGarageLocation(), AddressSnapshot::getGooglePlaceId), c.placeId())
                || !same(value(vehicle.getGarageLocation(), AddressSnapshot::getLatitude), c.latitude())
                || !same(value(vehicle.getGarageLocation(), AddressSnapshot::getLongitude), c.longitude());
    }

    private String normalizeRegistration(String value) {
        return value == null ? null : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private <T, R> R value(T source, java.util.function.Function<T, R> getter) {
        return source == null ? null : getter.apply(source);
    }

    protected record Command(
            String id, String masterVehicleId, String clientId, String registrationNumber,
            OwnershipType ownership, String garageAddress, String placeId,
            Double latitude, Double longitude
    ) {
    }
}
