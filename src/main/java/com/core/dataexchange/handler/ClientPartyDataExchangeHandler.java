package com.core.dataexchange.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.core.dataexchange.dto.DataExchangeColumn;
import com.core.dataexchange.dto.DataExchangeImportIssue;
import com.core.dataexchange.internal.AbstractDataExchangeHandler;
import com.core.dataexchange.internal.CsvRow;
import com.core.dataexchange.internal.PreparedImport;
import com.core.dataexchange.internal.RowValidation;
import com.core.dataexchange.model.DataExchangeResource;
import com.core.models.Client;
import com.core.models.User;
import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.Name;
import com.core.repositories.ClientRepository;
import com.core.repositories.UserRepository;

abstract class ClientPartyDataExchangeHandler
        extends AbstractDataExchangeHandler<ClientPartyDataExchangeHandler.Command> {

    protected final ClientRepository clientRepository;
    protected final UserRepository userRepository;
    private final DataExchangeResource resource;
    private final boolean supplier;

    protected ClientPartyDataExchangeHandler(ClientRepository clientRepository,
                                             UserRepository userRepository,
                                             DataExchangeResource resource,
                                             boolean supplier) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.resource = resource;
        this.supplier = supplier;
    }

    @Override
    public DataExchangeResource resource() {
        return resource;
    }

    @Override
    public String displayName() {
        return supplier ? "Vendors" : "Clients";
    }

    @Override
    public List<DataExchangeColumn> columns() {
        return List.of(
                DataExchangeColumn.identifier("record_id", "Record ID", false,
                        "Leave blank to create a new record. Keep the exported ID to update an existing record."),
                DataExchangeColumn.text("salutation", "Salutation", false, "Example: Mr., Ms., Dr."),
                DataExchangeColumn.text("first_name", "First Name", true, "Required name."),
                DataExchangeColumn.text("last_name", "Last Name", false, "Optional surname."),
                DataExchangeColumn.email("email", "Email", false, "Must be unique within the organization when provided."),
                DataExchangeColumn.phone("phone", "Phone", true, "Required E.164 number, for example +919721110777."),
                DataExchangeColumn.text("formatted_address", "Formatted Address", false, "Complete display address."),
                DataExchangeColumn.text("city", "City", false, "City name."),
                DataExchangeColumn.text("state", "State", false, "State or province."),
                DataExchangeColumn.text("pincode", "Pincode", false, "Postal code."),
                DataExchangeColumn.text("country_code", "Country Code", false, "ISO country code, for example IND.")
        );
    }

    @Override
    public List<String> notes() {
        return List.of(
                supplier ? "Every imported row is stored as a vendor." : "Every imported row is stored as a client.",
                "Profile images, billing entities, passengers, user links and audit fields are not changed by CSV import.",
                "Deleting a CSV row never deletes the database record."
        );
    }

    @Override
    public List<Map<String, String>> exportRows(String orgId) {
        return clientRepository.findByOrgId(orgId).stream()
                .filter(this::belongsToResource)
                .map(client -> row(
                        "record_id", client.getId(),
                        "salutation", value(client.getName(), Name::getSalutation),
                        "first_name", value(client.getName(), Name::getFirstName),
                        "last_name", value(client.getName(), Name::getLastName),
                        "email", client.getEmail(),
                        "phone", client.getPhone(),
                        "formatted_address", value(client.getAddress(), DisplayAddress::getFormattedAddress),
                        "city", value(client.getAddress(), DisplayAddress::getCity),
                        "state", value(client.getAddress(), DisplayAddress::getState),
                        "pincode", value(client.getAddress(), DisplayAddress::getPincode),
                        "country_code", value(client.getAddress(), DisplayAddress::getCountryCode)
                ))
                .toList();
    }

    @Override
    protected PreparedImport<Command> prepare(List<CsvRow> rows, String orgId) {
        List<DataExchangeImportIssue> errors = new ArrayList<>();
        List<Command> commands = new ArrayList<>();
        Map<String, Client> existingById = new HashMap<>();
        Map<String, String> existingPhoneToId = new HashMap<>();
        Map<String, String> existingEmailToId = new HashMap<>();

        for (Client client : clientRepository.findByOrgId(orgId)) {
            existingById.put(client.getId(), client);
            if (client.getPhone() != null) {
                existingPhoneToId.put(client.getPhone(), client.getId());
            }
            if (client.getEmail() != null && !client.getEmail().isBlank()) {
                existingEmailToId.put(client.getEmail().toLowerCase(Locale.ROOT), client.getId());
            }
        }

        Map<String, Long> seenIds = new HashMap<>();
        Map<String, Long> seenPhones = new HashMap<>();
        Map<String, Long> seenEmails = new HashMap<>();
        int creates = 0;
        int updates = 0;
        int unchanged = 0;

        for (CsvRow row : rows) {
            int errorStart = errors.size();
            RowValidation v = new RowValidation(row, errors);
            String id = v.optional("record_id", 40);
            validateDuplicateRecordId(id, row.rowNumber(), seenIds, errors);

            Client existing = id == null ? null : existingById.get(id);
            if (id != null && existing == null) {
                v.issue("record_id", "RECORD_NOT_FOUND", "Record does not exist in this organization.");
            } else if (existing != null && !belongsToResource(existing)) {
                v.issue("record_id", "WRONG_RESOURCE",
                        supplier ? "This record is a client, not a vendor." : "This record is a vendor, not a client.");
            }

            String salutation = v.optional("salutation", 255);
            String firstName = v.required("first_name", 255);
            String lastName = v.optional("last_name", 255);
            String email = v.email("email", false, 100);
            String phone = v.phone("phone", true);
            String formattedAddress = v.optional("formatted_address", 300);
            String city = v.optional("city", 100);
            String state = v.optional("state", 100);
            String pincode = v.optional("pincode", 20);
            String countryCode = v.optional("country_code", 3);

            checkUnique("phone", phone, id, row.rowNumber(), seenPhones, existingPhoneToId, v);
            checkUnique("email", email == null ? null : email.toLowerCase(Locale.ROOT), id,
                    row.rowNumber(), seenEmails, existingEmailToId, v);

            if (errors.size() > errorStart) {
                continue;
            }

            Command command = new Command(id, salutation, firstName, lastName, email, phone,
                    formattedAddress, city, state, pincode, countryCode);
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
            Client client;
            boolean creating = command.id() == null;
            if (creating) {
                client = new Client();
                client.setOrgId(orgId);
                client.setSupplier(supplier);
                client.setClientBillingEntityIds(new ArrayList<>());
            } else {
                client = clientRepository.findByIdAndOrgId(command.id(), orgId).orElseThrow();
            }

            client.setName(new Name(command.salutation(), command.firstName(), command.lastName()));
            client.setEmail(command.email());
            client.setPhone(command.phone());
            client.setAddress(address(command));
            client.setSupplier(supplier);
            clientRepository.save(client);

            if (!creating && client.getUserId() != null) {
                userRepository.findByOrgIdAndId(orgId, client.getUserId()).ifPresent(user -> syncUser(user, command));
            }
        }
    }

    private void syncUser(User user, Command command) {
        user.setEmail(command.email());
        user.setPhone(command.phone());
        userRepository.save(user);
    }

    private DisplayAddress address(Command command) {
        if (command.formattedAddress() == null && command.city() == null && command.state() == null
                && command.pincode() == null && command.countryCode() == null) {
            return null;
        }
        return new DisplayAddress(command.formattedAddress(), command.city(), command.state(),
                command.pincode(), command.countryCode());
    }

    private boolean changed(Client client, Command command) {
        Name name = client.getName();
        DisplayAddress address = client.getAddress();
        return !same(value(name, Name::getSalutation), command.salutation())
                || !same(value(name, Name::getFirstName), command.firstName())
                || !same(value(name, Name::getLastName), command.lastName())
                || !same(blankToNull(client.getEmail()), command.email())
                || !same(client.getPhone(), command.phone())
                || !same(value(address, DisplayAddress::getFormattedAddress), command.formattedAddress())
                || !same(value(address, DisplayAddress::getCity), command.city())
                || !same(value(address, DisplayAddress::getState), command.state())
                || !same(value(address, DisplayAddress::getPincode), command.pincode())
                || !same(value(address, DisplayAddress::getCountryCode), command.countryCode())
                || !belongsToResource(client);
    }

    private boolean belongsToResource(Client client) {
        return supplier ? Boolean.TRUE.equals(client.getSupplier()) : !Boolean.TRUE.equals(client.getSupplier());
    }

    private void checkUnique(String column, String value, String currentId, long rowNumber,
                             Map<String, Long> seenValues, Map<String, String> existingValueToId,
                             RowValidation validation) {
        if (value == null) {
            return;
        }
        Long previousRow = seenValues.putIfAbsent(value, rowNumber);
        if (previousRow != null) {
            validation.issue(column, "DUPLICATE_IN_FILE",
                    "Value is already used at CSV row " + previousRow + ".");
        }
        String existingId = existingValueToId.get(value);
        if (existingId != null && !existingId.equals(currentId)) {
            validation.issue(column, "DUPLICATE_IN_DATABASE",
                    "Value is already used by another client or vendor.");
        }
    }

    private <T, R> R value(T source, java.util.function.Function<T, R> getter) {
        return source == null ? null : getter.apply(source);
    }

    protected record Command(
            String id,
            String salutation,
            String firstName,
            String lastName,
            String email,
            String phone,
            String formattedAddress,
            String city,
            String state,
            String pincode,
            String countryCode
    ) {
    }
}
