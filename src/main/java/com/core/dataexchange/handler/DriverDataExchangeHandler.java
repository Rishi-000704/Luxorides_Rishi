package com.core.dataexchange.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import com.core.models.Driver;
import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.Name;
import com.core.models.enums.OwnershipType;
import com.core.repositories.ClientRepository;
import com.core.repositories.DriverRepository;

@Component
public class DriverDataExchangeHandler
        extends AbstractDataExchangeHandler<DriverDataExchangeHandler.Command> {

    private final DriverRepository driverRepository;
    private final ClientRepository clientRepository;

    public DriverDataExchangeHandler(DriverRepository driverRepository, ClientRepository clientRepository) {
        this.driverRepository = driverRepository;
        this.clientRepository = clientRepository;
    }

    @Override
    public DataExchangeResource resource() {
        return DataExchangeResource.DRIVER;
    }

    @Override
    public String displayName() {
        return "Drivers";
    }

    @Override
    public List<DataExchangeColumn> columns() {
        return List.of(
                DataExchangeColumn.identifier("record_id", "Record ID", false,
                        "Leave blank to create. Keep the exported ID to update."),
                DataExchangeColumn.enumeration("ownership", "Ownership", true,
                        "ORG for own driver or CLIENT for client/vendor-owned driver.", enumValues(OwnershipType.class)),
                DataExchangeColumn.identifier("client_id", "Client/Vendor ID", false,
                        "Required only when ownership is CLIENT."),
                DataExchangeColumn.text("salutation", "Salutation", false, "Example: Mr., Ms., Dr."),
                DataExchangeColumn.text("first_name", "First Name", true, "Required driver name."),
                DataExchangeColumn.text("last_name", "Last Name", false, "Optional surname."),
                DataExchangeColumn.text("father_salutation", "Father Salutation", false, "Optional."),
                DataExchangeColumn.text("father_first_name", "Father First Name", false, "Optional."),
                DataExchangeColumn.text("father_last_name", "Father Last Name", false, "Optional."),
                DataExchangeColumn.text("gender", "Gender", true, "Current driver gender value."),
                DataExchangeColumn.phone("phone", "Phone", true, "Required E.164 number."),
                DataExchangeColumn.phone("alternate_phone", "Alternate Phone", false, "Optional E.164 number."),
                DataExchangeColumn.text("formatted_address", "Formatted Address", false, "Complete display address."),
                DataExchangeColumn.text("city", "City", false, "City name."),
                DataExchangeColumn.text("state", "State", false, "State or province."),
                DataExchangeColumn.text("pincode", "Pincode", false, "Postal code."),
                DataExchangeColumn.text("country_code", "Country Code", false, "ISO country code."),
                DataExchangeColumn.text("license_number", "License Number", false, "Driving licence number.")
        );
    }

    @Override
    public List<String> notes() {
        return List.of(
                "Aadhaar number and profile image are intentionally excluded from CSV export/import.",
                "When ownership is ORG, client_id must be blank. When ownership is CLIENT, client_id is required.",
                "Deleting a CSV row never deletes the driver."
        );
    }

    @Override
    public List<Map<String, String>> exportRows(String orgId) {
        return driverRepository.findByOrgId(orgId).stream().map(driver -> row(
                "record_id", driver.getId(),
                "ownership", driver.getOwnership(),
                "client_id", driver.getClientId(),
                "salutation", value(driver.getName(), Name::getSalutation),
                "first_name", value(driver.getName(), Name::getFirstName),
                "last_name", value(driver.getName(), Name::getLastName),
                "father_salutation", value(driver.getFatherName(), Name::getSalutation),
                "father_first_name", value(driver.getFatherName(), Name::getFirstName),
                "father_last_name", value(driver.getFatherName(), Name::getLastName),
                "gender", driver.getGender(),
                "phone", driver.getPhone(),
                "alternate_phone", driver.getAlternatePhone(),
                "formatted_address", value(driver.getAddress(), DisplayAddress::getFormattedAddress),
                "city", value(driver.getAddress(), DisplayAddress::getCity),
                "state", value(driver.getAddress(), DisplayAddress::getState),
                "pincode", value(driver.getAddress(), DisplayAddress::getPincode),
                "country_code", value(driver.getAddress(), DisplayAddress::getCountryCode),
                "license_number", driver.getLicenseNumber()
        )).toList();
    }

    @Override
    protected PreparedImport<Command> prepare(List<CsvRow> rows, String orgId) {
        List<DataExchangeImportIssue> errors = new ArrayList<>();
        List<Command> commands = new ArrayList<>();
        Map<String, Driver> existingById = new HashMap<>();
        Map<String, String> phoneToId = new HashMap<>();
        for (Driver driver : driverRepository.findByOrgId(orgId)) {
            existingById.put(driver.getId(), driver);
            if (driver.getPhone() != null) {
                phoneToId.put(driver.getPhone(), driver.getId());
            }
        }
        Map<String, Client> clientsById = new HashMap<>();
        for (Client client : clientRepository.findByOrgId(orgId)) {
            clientsById.put(client.getId(), client);
        }

        Map<String, Long> seenIds = new HashMap<>();
        Map<String, Long> seenPhones = new HashMap<>();
        int creates = 0;
        int updates = 0;
        int unchanged = 0;

        for (CsvRow row : rows) {
            int errorStart = errors.size();
            RowValidation v = new RowValidation(row, errors);
            String id = v.optional("record_id", 40);
            validateDuplicateRecordId(id, row.rowNumber(), seenIds, errors);
            Driver existing = id == null ? null : existingById.get(id);
            if (id != null && existing == null) {
                v.issue("record_id", "RECORD_NOT_FOUND", "Driver does not exist in this organization.");
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

            String salutation = v.optional("salutation", 10);
            String firstName = v.required("first_name", 50);
            String lastName = v.optional("last_name", 50);
            String fatherSalutation = v.optional("father_salutation", 10);
            String fatherFirstName = v.optional("father_first_name", 50);
            String fatherLastName = v.optional("father_last_name", 50);
            String gender = v.required("gender", 20);
            String phone = v.phone("phone", true);
            String alternatePhone = v.phone("alternate_phone", false);
            String formattedAddress = v.optional("formatted_address", 300);
            String city = v.optional("city", 100);
            String state = v.optional("state", 100);
            String pincode = v.optional("pincode", 20);
            String countryCode = v.optional("country_code", 3);
            String licenseNumber = v.optional("license_number", 20);

            if (phone != null) {
                Long previous = seenPhones.putIfAbsent(phone, row.rowNumber());
                if (previous != null) {
                    v.issue("phone", "DUPLICATE_IN_FILE", "Phone is already used at CSV row " + previous + ".");
                }
                String existingId = phoneToId.get(phone);
                if (existingId != null && !existingId.equals(id)) {
                    v.issue("phone", "DUPLICATE_IN_DATABASE", "Phone is already used by another driver.");
                }
            }
            if (alternatePhone != null && alternatePhone.equals(phone)) {
                v.issue("alternate_phone", "DUPLICATE_PHONE", "Alternate phone must differ from primary phone.");
            }

            if (errors.size() > errorStart) {
                continue;
            }

            Command command = new Command(id, ownership, clientId, salutation, firstName, lastName,
                    fatherSalutation, fatherFirstName, fatherLastName, gender, phone, alternatePhone,
                    formattedAddress, city, state, pincode, countryCode, licenseNumber);
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
            Driver driver = command.id() == null
                    ? new Driver()
                    : driverRepository.findByIdAndOrgId(command.id(), orgId).orElseThrow();
            if (command.id() == null) {
                driver.setOrgId(orgId);
            }
            driver.setOwnership(command.ownership());
            driver.setClientId(command.ownership() == OwnershipType.CLIENT ? command.clientId() : null);
            driver.setName(new Name(command.salutation(), command.firstName(), command.lastName()));
            driver.setFatherName(fatherName(command));
            driver.setGender(command.gender());
            driver.setPhone(command.phone());
            driver.setAlternatePhone(command.alternatePhone());
            driver.setAddress(address(command));
            driver.setLicenseNumber(command.licenseNumber());
            driverRepository.save(driver);
        }
    }

    private Name fatherName(Command command) {
        if (command.fatherSalutation() == null && command.fatherFirstName() == null && command.fatherLastName() == null) {
            return null;
        }
        return new Name(command.fatherSalutation(), command.fatherFirstName(), command.fatherLastName());
    }

    private DisplayAddress address(Command command) {
        if (command.formattedAddress() == null && command.city() == null && command.state() == null
                && command.pincode() == null && command.countryCode() == null) {
            return null;
        }
        return new DisplayAddress(command.formattedAddress(), command.city(), command.state(),
                command.pincode(), command.countryCode());
    }

    private boolean changed(Driver driver, Command c) {
        return !same(driver.getOwnership(), c.ownership())
                || !same(blankToNull(driver.getClientId()), c.ownership() == OwnershipType.CLIENT ? c.clientId() : null)
                || !same(value(driver.getName(), Name::getSalutation), c.salutation())
                || !same(value(driver.getName(), Name::getFirstName), c.firstName())
                || !same(value(driver.getName(), Name::getLastName), c.lastName())
                || !same(value(driver.getFatherName(), Name::getSalutation), c.fatherSalutation())
                || !same(value(driver.getFatherName(), Name::getFirstName), c.fatherFirstName())
                || !same(value(driver.getFatherName(), Name::getLastName), c.fatherLastName())
                || !same(blankToNull(driver.getGender()), c.gender())
                || !same(driver.getPhone(), c.phone())
                || !same(blankToNull(driver.getAlternatePhone()), c.alternatePhone())
                || !same(value(driver.getAddress(), DisplayAddress::getFormattedAddress), c.formattedAddress())
                || !same(value(driver.getAddress(), DisplayAddress::getCity), c.city())
                || !same(value(driver.getAddress(), DisplayAddress::getState), c.state())
                || !same(value(driver.getAddress(), DisplayAddress::getPincode), c.pincode())
                || !same(value(driver.getAddress(), DisplayAddress::getCountryCode), c.countryCode())
                || !same(blankToNull(driver.getLicenseNumber()), c.licenseNumber());
    }

    private <T, R> R value(T source, java.util.function.Function<T, R> getter) {
        return source == null ? null : getter.apply(source);
    }

    protected record Command(
            String id, OwnershipType ownership, String clientId,
            String salutation, String firstName, String lastName,
            String fatherSalutation, String fatherFirstName, String fatherLastName,
            String gender, String phone, String alternatePhone,
            String formattedAddress, String city, String state, String pincode,
            String countryCode, String licenseNumber
    ) {
    }
}
