package com.core.dataexchange.handler;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import com.core.models.MasterVehicle;
import com.core.models.Package;
import com.core.models.embedded.Money;
import com.core.models.enums.Currency;
import com.core.models.enums.DutyType;
import com.core.models.enums.PackageScope;
import com.core.repositories.ClientRepository;
import com.core.repositories.MasterVehicleRepository;
import com.core.repositories.PackageRepository;

@Component
public class PackageDataExchangeHandler
        extends AbstractDataExchangeHandler<PackageDataExchangeHandler.Command> {

    private final PackageRepository packageRepository;
    private final MasterVehicleRepository masterVehicleRepository;
    private final ClientRepository clientRepository;

    public PackageDataExchangeHandler(PackageRepository packageRepository,
                                      MasterVehicleRepository masterVehicleRepository,
                                      ClientRepository clientRepository) {
        this.packageRepository = packageRepository;
        this.masterVehicleRepository = masterVehicleRepository;
        this.clientRepository = clientRepository;
    }

    @Override
    public DataExchangeResource resource() {
        return DataExchangeResource.PACKAGE;
    }

    @Override
    public String displayName() {
        return "Packages";
    }

    @Override
    public List<DataExchangeColumn> columns() {
        return List.of(
                DataExchangeColumn.identifier("record_id", "Record ID", false,
                        "Leave blank to create. Keep the exported ID to update."),
                DataExchangeColumn.enumeration("scope", "Scope", true,
                        "MASTER for default pricing or CLIENT for party-specific pricing.", enumValues(PackageScope.class)),
                DataExchangeColumn.identifier("party_id", "Client/Vendor ID", false,
                        "Required for CLIENT scope; blank for MASTER scope."),
                DataExchangeColumn.identifier("master_vehicle_id", "Master Vehicle ID", true,
                        "Must reference a master vehicle in the same organization."),
                DataExchangeColumn.enumeration("duty_type", "Duty Type", true,
                        "Supported duty type.", enumValues(DutyType.class)),
                DataExchangeColumn.integer("time", "Time", false, "Included time quantity."),
                DataExchangeColumn.text("unit", "Unit", false, "Package unit label."),
                DataExchangeColumn.integer("distance", "Distance", false, "Included distance."),
                DataExchangeColumn.decimal("base_fare_amount", "Base Fare Amount", true, "Non-negative amount."),
                DataExchangeColumn.enumeration("base_fare_currency", "Base Fare Currency", true,
                        "Currency code.", enumValues(Currency.class)),
                DataExchangeColumn.decimal("extra_per_km_amount", "Extra Per KM Amount", true, "Non-negative amount."),
                DataExchangeColumn.enumeration("extra_per_km_currency", "Extra Per KM Currency", true,
                        "Currency code.", enumValues(Currency.class)),
                DataExchangeColumn.decimal("extra_per_hour_amount", "Extra Per Hour Amount", true, "Non-negative amount."),
                DataExchangeColumn.enumeration("extra_per_hour_currency", "Extra Per Hour Currency", true,
                        "Currency code.", enumValues(Currency.class)),
                DataExchangeColumn.decimal("night_charge_amount", "Night Charge Amount", true, "Non-negative amount."),
                DataExchangeColumn.enumeration("night_charge_currency", "Night Charge Currency", true,
                        "Currency code.", enumValues(Currency.class)),
                DataExchangeColumn.bool("for_sales", "For Sales", true,
                        "TRUE for client sales pricing; FALSE for vendor purchase pricing."),
                DataExchangeColumn.text("location", "Location", false, "Pricing location or city.")
        );
    }

    @Override
    public List<String> notes() {
        return List.of(
                "For MASTER scope, party_id must be blank. For CLIENT scope, party_id is required.",
                "When for_sales is TRUE, party_id must reference a client. When FALSE, it must reference a vendor.",
                "All money values are rounded to two decimal places during import.",
                "Deleting a CSV row never deletes the package."
        );
    }

    @Override
    public List<Map<String, String>> exportRows(String orgId) {
        return packageRepository.findByOrgId(orgId).stream().map(pack -> row(
                "record_id", pack.getId(),
                "scope", pack.getScope(),
                "party_id", pack.getClientId(),
                "master_vehicle_id", pack.getMasterVehicleId(),
                "duty_type", pack.getDutyType(),
                "time", pack.getTime(),
                "unit", pack.getUnit(),
                "distance", pack.getDistance(),
                "base_fare_amount", amount(pack.getBaseFare()),
                "base_fare_currency", currency(pack.getBaseFare()),
                "extra_per_km_amount", amount(pack.getExtraPerKM()),
                "extra_per_km_currency", currency(pack.getExtraPerKM()),
                "extra_per_hour_amount", amount(pack.getExtraPerHS()),
                "extra_per_hour_currency", currency(pack.getExtraPerHS()),
                "night_charge_amount", amount(pack.getNightCharge()),
                "night_charge_currency", currency(pack.getNightCharge()),
                "for_sales", pack.getForSales(),
                "location", pack.getLocation()
        )).toList();
    }

    @Override
    protected PreparedImport<Command> prepare(List<CsvRow> rows, String orgId) {
        List<DataExchangeImportIssue> errors = new ArrayList<>();
        List<Command> commands = new ArrayList<>();
        Map<String, Package> existingById = new HashMap<>();
        for (Package pack : packageRepository.findByOrgId(orgId)) {
            existingById.put(pack.getId(), pack);
        }
        Map<String, MasterVehicle> masterVehiclesById = new HashMap<>();
        for (MasterVehicle vehicle : masterVehicleRepository.findByOrgId(orgId)) {
            masterVehiclesById.put(vehicle.getId(), vehicle);
        }
        Map<String, Client> partiesById = new HashMap<>();
        for (Client client : clientRepository.findByOrgId(orgId)) {
            partiesById.put(client.getId(), client);
        }

        Map<String, Long> seenIds = new HashMap<>();
        int creates = 0;
        int updates = 0;
        int unchanged = 0;

        for (CsvRow row : rows) {
            int errorStart = errors.size();
            RowValidation v = new RowValidation(row, errors);
            String id = v.optional("record_id", 40);
            validateDuplicateRecordId(id, row.rowNumber(), seenIds, errors);
            Package existing = id == null ? null : existingById.get(id);
            if (id != null && existing == null) {
                v.issue("record_id", "RECORD_NOT_FOUND", "Package does not exist in this organization.");
            }

            PackageScope scope = v.enumeration("scope", true, PackageScope.class);
            String partyId = v.optional("party_id", 40);
            Boolean forSales = v.bool("for_sales", true);
            if (scope == PackageScope.MASTER && partyId != null) {
                v.issue("party_id", "INVALID_SCOPE_REFERENCE", "party_id must be blank for MASTER scope.");
            }
            if (scope == PackageScope.CLIENT) {
                if (partyId == null) {
                    v.issue("party_id", "REQUIRED", "party_id is required for CLIENT scope.");
                } else {
                    Client party = partiesById.get(partyId);
                    if (party == null) {
                        v.issue("party_id", "REFERENCE_NOT_FOUND", "Client/vendor does not exist in this organization.");
                    } else if (forSales != null) {
                        boolean vendor = Boolean.TRUE.equals(party.getSupplier());
                        if (forSales && vendor) {
                            v.issue("party_id", "WRONG_PARTY_TYPE", "Sales package must reference a client, not a vendor.");
                        }
                        if (!forSales && !vendor) {
                            v.issue("party_id", "WRONG_PARTY_TYPE", "Purchase package must reference a vendor.");
                        }
                    }
                }
            }

            String masterVehicleId = v.required("master_vehicle_id", 40);
            if (masterVehicleId != null && !masterVehiclesById.containsKey(masterVehicleId)) {
                v.issue("master_vehicle_id", "REFERENCE_NOT_FOUND",
                        "Master vehicle does not exist in this organization.");
            }
            DutyType dutyType = v.enumeration("duty_type", true, DutyType.class);
            Integer time = v.integer("time", false, 0);
            String unit = v.optional("unit", 20);
            Integer distance = v.integer("distance", false, 0);
            BigDecimal baseFareAmount = scale(v.decimal("base_fare_amount", true, BigDecimal.ZERO));
            Currency baseFareCurrency = v.enumeration("base_fare_currency", true, Currency.class);
            BigDecimal extraKmAmount = scale(v.decimal("extra_per_km_amount", true, BigDecimal.ZERO));
            Currency extraKmCurrency = v.enumeration("extra_per_km_currency", true, Currency.class);
            BigDecimal extraHourAmount = scale(v.decimal("extra_per_hour_amount", true, BigDecimal.ZERO));
            Currency extraHourCurrency = v.enumeration("extra_per_hour_currency", true, Currency.class);
            BigDecimal nightAmount = scale(v.decimal("night_charge_amount", true, BigDecimal.ZERO));
            Currency nightCurrency = v.enumeration("night_charge_currency", true, Currency.class);
            String location = v.optional("location", 100);

            if (errors.size() > errorStart) {
                continue;
            }

            Command command = new Command(id, scope, scope == PackageScope.CLIENT ? partyId : null,
                    masterVehicleId, dutyType, time, unit, distance,
                    baseFareAmount, baseFareCurrency, extraKmAmount, extraKmCurrency,
                    extraHourAmount, extraHourCurrency, nightAmount, nightCurrency,
                    forSales, location);
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
            Package pack = command.id() == null
                    ? new Package()
                    : packageRepository.findByIdAndOrgId(command.id(), orgId).orElseThrow();
            if (command.id() == null) {
                pack.setOrgId(orgId);
            }
            pack.setScope(command.scope());
            pack.setClientId(command.partyId());
            pack.setMasterVehicleId(command.masterVehicleId());
            pack.setDutyType(command.dutyType());
            pack.setTime(command.time());
            pack.setUnit(command.unit());
            pack.setDistance(command.distance());
            pack.setBaseFare(new Money(command.baseFareAmount(), command.baseFareCurrency()));
            pack.setExtraPerKM(new Money(command.extraKmAmount(), command.extraKmCurrency()));
            pack.setExtraPerHS(new Money(command.extraHourAmount(), command.extraHourCurrency()));
            pack.setNightCharge(new Money(command.nightAmount(), command.nightCurrency()));
            pack.setForSales(command.forSales());
            pack.setLocation(command.location());
            packageRepository.save(pack);
        }
    }

    private boolean changed(Package pack, Command c) {
        return !same(pack.getScope(), c.scope())
                || !same(blankToNull(pack.getClientId()), c.partyId())
                || !same(pack.getMasterVehicleId(), c.masterVehicleId())
                || !same(pack.getDutyType(), c.dutyType())
                || !same(pack.getTime(), c.time())
                || !same(blankToNull(pack.getUnit()), c.unit())
                || !same(pack.getDistance(), c.distance())
                || !moneySame(pack.getBaseFare(), c.baseFareAmount(), c.baseFareCurrency())
                || !moneySame(pack.getExtraPerKM(), c.extraKmAmount(), c.extraKmCurrency())
                || !moneySame(pack.getExtraPerHS(), c.extraHourAmount(), c.extraHourCurrency())
                || !moneySame(pack.getNightCharge(), c.nightAmount(), c.nightCurrency())
                || !same(Boolean.TRUE.equals(pack.getForSales()), c.forSales())
                || !same(blankToNull(pack.getLocation()), c.location());
    }

    private boolean moneySame(Money money, BigDecimal amount, Currency currency) {
        return money != null && money.getAmount() != null && amount != null
                && money.getAmount().compareTo(amount) == 0 && money.getCurrency() == currency;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String amount(Money money) {
        return money == null || money.getAmount() == null ? null : money.getAmount().toPlainString();
    }

    private Currency currency(Money money) {
        return money == null ? null : money.getCurrency();
    }

    protected record Command(
            String id, PackageScope scope, String partyId, String masterVehicleId,
            DutyType dutyType, Integer time, String unit, Integer distance,
            BigDecimal baseFareAmount, Currency baseFareCurrency,
            BigDecimal extraKmAmount, Currency extraKmCurrency,
            BigDecimal extraHourAmount, Currency extraHourCurrency,
            BigDecimal nightAmount, Currency nightCurrency,
            Boolean forSales, String location
    ) {
    }
}
