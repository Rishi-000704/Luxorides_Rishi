package com.core.reports.definition;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.core.models.enums.ReportCategory;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportPartyType;
import com.core.models.enums.ReportPeriodRule;
import com.core.models.enums.ReportType;

import org.springframework.stereotype.Component;

@Component
public class ReportDefinitionRegistry {

    private final List<ReportDefinition> definitions;
    private final Map<ReportType, ReportDefinition> definitionsByType;

    public ReportDefinitionRegistry() {
        this.definitions = List.of(

                /*
                 * GST REPORTS
                 */

                available(
                        ReportCategory.GST,
                        ReportType.GSTR1,
                        "GSTR-1",
                        EnumSet.of(ReportFormat.JSON),
                        ReportPeriodRule.SINGLE_CALENDAR_MONTH,
                        false,
                        Set.of()
                ),

                comingSoon(
                        ReportCategory.GST,
                        ReportType.GSTR3B_SUMMARY,
                        "GSTR-3B",
                        ReportPeriodRule.SINGLE_CALENDAR_MONTH,
                        false,
                        Set.of()
                ),

                comingSoon(
                        ReportCategory.GST,
                        ReportType.PURCHASE_GST_REGISTER,
                        "Purchase GST Register",
                        ReportPeriodRule.DATE_RANGE,
                        false,
                        Set.of()
                ),

                comingSoon(
                        ReportCategory.GST,
                        ReportType.HSN_SUMMARY,
                        "HSN Summary",
                        ReportPeriodRule.DATE_RANGE,
                        false,
                        Set.of()
                ),

                comingSoon(
                        ReportCategory.GST,
                        ReportType.B2B_REGISTER,
                        "B2B Register",
                        ReportPeriodRule.DATE_RANGE,
                        false,
                        Set.of()
                ),

                comingSoon(
                        ReportCategory.GST,
                        ReportType.B2C_REGISTER,
                        "B2C Register",
                        ReportPeriodRule.DATE_RANGE,
                        false,
                        Set.of()
                ),

                /*
                 * SALES / PURCHASE REPORTS
                 */

                available(
                        ReportCategory.SALES_PURCHASE,
                        ReportType.SALES_REGISTER,
                        "Sales Report",
                        EnumSet.of(ReportFormat.CSV),
                        ReportPeriodRule.DATE_RANGE,
                        false,
                        Set.of()
                ),

                available(
                        ReportCategory.SALES_PURCHASE,
                        ReportType.PURCHASE_REGISTER,
                        "Purchase Report",
                        EnumSet.of(
                                ReportFormat.CSV,
                                ReportFormat.JSON
                        ),
                        ReportPeriodRule.DATE_RANGE,
                        false,
                        Set.of()
                ),

                comingSoon(
                        ReportCategory.SALES_PURCHASE,
                        ReportType.SALES_SUMMARY,
                        "Sales Summary",
                        ReportPeriodRule.DATE_RANGE,
                        false,
                        Set.of()
                ),

                comingSoon(
                        ReportCategory.SALES_PURCHASE,
                        ReportType.PURCHASE_SUMMARY,
                        "Purchase Summary",
                        ReportPeriodRule.DATE_RANGE,
                        false,
                        Set.of()
                ),

                /*
                 * LEDGER / STATEMENT REPORTS
                 */

                comingSoon(
                        ReportCategory.LEDGER,
                        ReportType.CLIENT_LEDGER,
                        "Client Ledger",
                        ReportPeriodRule.DATE_RANGE,
                        true,
                        EnumSet.of(ReportPartyType.CLIENT)
                ),

                comingSoon(
                        ReportCategory.LEDGER,
                        ReportType.CLIENT_BILLING_ENTITY_LEDGER,
                        "Client Billing Entity Ledger",
                        ReportPeriodRule.DATE_RANGE,
                        true,
                        EnumSet.of(ReportPartyType.CLIENT_BILLING_ENTITY)
                ),

                comingSoon(
                        ReportCategory.LEDGER,
                        ReportType.VENDOR_LEDGER,
                        "Vendor Ledger",
                        ReportPeriodRule.DATE_RANGE,
                        true,
                        EnumSet.of(ReportPartyType.VENDOR)
                )
        );

        Map<ReportType, ReportDefinition> map =
                new EnumMap<>(ReportType.class);

        definitions.forEach(
                definition -> map.put(
                        definition.reportType(),
                        definition
                )
        );

        this.definitionsByType = Map.copyOf(map);
    }

    public List<ReportDefinition> all() {
        return definitions;
    }

    public Optional<ReportDefinition> find(ReportType reportType) {
        if (reportType == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                definitionsByType.get(reportType)
        );
    }

    private static ReportDefinition available(
            ReportCategory category,
            ReportType reportType,
            String label,
            Set<ReportFormat> formats,
            ReportPeriodRule periodRule,
            boolean requiresParty,
            Set<ReportPartyType> allowedPartyTypes
    ) {
        return new ReportDefinition(
                category,
                reportType,
                label,
                true,
                formats,
                periodRule,
                true,
                requiresParty,
                allowedPartyTypes,
                null
        );
    }

    private static ReportDefinition comingSoon(
            ReportCategory category,
            ReportType reportType,
            String label,
            ReportPeriodRule periodRule,
            boolean requiresParty,
            Set<ReportPartyType> allowedPartyTypes
    ) {
        return new ReportDefinition(
                category,
                reportType,
                label,
                false,
                Set.of(),
                periodRule,
                true,
                requiresParty,
                allowedPartyTypes,
                label + " report is coming soon"
        );
    }
}