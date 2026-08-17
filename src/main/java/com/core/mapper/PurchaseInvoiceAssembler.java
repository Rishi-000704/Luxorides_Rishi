package com.core.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.core.dtos.common.MoneyDTO;
import com.core.dtos.purchase.EligiblePurchaseDutyDTO;
import com.core.dtos.purchase.PaymentOutDTO;
import com.core.dtos.purchase.PurchaseInvoiceDTO;
import com.core.dtos.purchase.PurchaseInvoiceEntryDTO;
import com.core.dtos.purchase.PurchasePackageOptionDTO;
import com.core.models.BookingEntry;
import com.core.models.Client;
import com.core.models.ClientBillingEntity;
import com.core.models.FleetVehicle;
import com.core.models.MasterVehicle;
import com.core.models.OrgBillingEntity;
import com.core.models.Package;
import com.core.models.PaymentOut;
import com.core.models.PurchaseInvoice;
import com.core.models.PurchaseInvoiceEntry;
import com.core.models.embedded.Money;
import com.core.services.common.AuditActorService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PurchaseInvoiceAssembler {

    private final AuditActorService auditActorService;

    public PurchaseInvoiceDTO assemble(
            PurchaseInvoice invoice,
            boolean includePayments
    ) {
        if (invoice == null) {
            return null;
        }

        return new PurchaseInvoiceDTO(
                invoice.getId(),
                invoice.getOrgId(),

                invoice.getVendorId(),
                clientName(invoice.getVendor()),

                invoice.getVendorBillingEntityId(),
                billingName(invoice.getVendorBillingEntity()),

                invoice.getOrgBillingEntityId(),
                billingName(invoice.getOrgBillingEntity()),

                invoice.getPurchaseInvoiceNumber(),
                invoice.getVendorInvoiceNumber(),
                invoice.getVendorInvoiceDate(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),

                invoice.getSubtotal(),
                invoice.getTaxableAmount(),
                invoice.getGstSnapshot(),
                invoice.getGrandTotal(),
                invoice.getPaidAmount(),
                invoice.getBalanceAmount(),

                invoice.getStatus(),
                invoice.getRemarks(),

                invoice.getEntries() == null
                        ? List.of()
                        : invoice.getEntries()
                        .stream()
                        .map(this::entry)
                        .toList(),

                !includePayments || invoice.getPayments() == null
                        ? List.of()
                        : invoice.getPayments()
                        .stream()
                        .map(this::paymentOut)
                        .toList(),

                invoice.getCreatedAt(),
                invoice.getUpdatedAt(),
                resolveActor(invoice.getCreatedBy()),
                resolveActor(invoice.getUpdatedBy())
        );
    }

    public EligiblePurchaseDutyDTO eligibleDuty(BookingEntry entry) {
        if (entry == null) {
            return null;
        }

        MasterVehicle requestedVehicle = entry.getRequestedVehicle();
        FleetVehicle allottedVehicle = entry.getAllotedVehicle();

        return new EligiblePurchaseDutyDTO(
                entry.getBooking() == null ? null : entry.getBooking().getBookingId(),
                entry.getId(),
                entry.getDutyId(),
                entry.getStatus(),

                entry.getBooking() == null ? null : entry.getBooking().getClientId(),
                entry.getBooking() == null ? null : clientName(entry.getBooking().getClient()),

                entry.getSupplierId(),

                entry.getMasterVehicleId(),
                requestedVehicle == null ? null : requestedVehicle.getName(),

                entry.getFleetVehicleId(),
                allottedVehicle == null ? null : allottedVehicle.getRegistrationNumber(),

                entry.getDriverId(),
                entry.getDriver() == null || entry.getDriver().getName() == null
                        ? null
                        : entry.getDriver().getName().getDisplayName(),

                entry.getReportingTime(),
                entry.getDropTime(),

                entry.getRunningDays(),
                entry.getExtraChargebleDistance(),
                entry.getExtraChargebleTime(),
                entry.getNightChargeble(),

                entry.getDutyTotal()
        );
    }

    public PurchasePackageOptionDTO packageOption(Package pack) {
        if (pack == null) {
            return null;
        }

        return new PurchasePackageOptionDTO(
                pack.getId(),
                pack.getScope(),
                pack.getClientId(),
                pack.getMasterVehicleId(),
                pack.getDutyType(),
                pack.getTime(),
                pack.getUnit(),
                pack.getDistance(),
                money(pack.getBaseFare()),
                money(pack.getExtraPerKM()),
                money(pack.getExtraPerHS()),
                money(pack.getNightCharge()),
                pack.getForSales(),
                pack.getLocation()
        );
    }

    private PurchaseInvoiceEntryDTO entry(PurchaseInvoiceEntry entry) {
        if (entry == null) {
            return null;
        }

        return new PurchaseInvoiceEntryDTO(
                entry.getId(),
                entry.getBookingId(),
                entry.getBookingEntryId(),
                entry.getDutyId(),

                entry.getPack(),

                entry.getMasterVehicleId(),

                entry.getReportingTime(),
                entry.getDropTime(),

                entry.getRunningDays(),
                entry.getExtraChargebleDistance(),
                entry.getExtraChargebleTime(),
                entry.getNightChargeble(),

                entry.getChargebleBaseFare(),
                entry.getExtraChargeDistance(),
                entry.getExtraChargeTime(),
                entry.getNightCharge(),
                entry.getExtraChargesTotal(),
                entry.getDutyTotal(),

                entry.getRemarks()
        );
    }

    private PaymentOutDTO paymentOut(PaymentOut paymentOut) {
        if (paymentOut == null) {
            return null;
        }

        return new PaymentOutDTO(
                paymentOut.getId(),
                paymentOut.getVendorId(),
                paymentOut.getPurchaseInvoice() == null
                        ? null
                        : paymentOut.getPurchaseInvoice().getId(),

                paymentOut.getPaymentMode(),
                paymentOut.getTransactionNumber(),
                paymentOut.getTransactionDate(),

                paymentOut.getPaidAmount(),
                paymentOut.getTds(),

                paymentOut.getRemarks(),
                paymentOut.getStatus(),

                paymentOut.getCreatedAt(),
                paymentOut.getUpdatedAt()
        );
    }

    private String clientName(Client client) {
        return client == null || client.getName() == null
                ? null
                : client.getName().getDisplayName();
    }

    private String billingName(ClientBillingEntity billingEntity) {
        return billingEntity == null ? null : billingEntity.getLegalName();
    }

    private String billingName(OrgBillingEntity billingEntity) {
        return billingEntity == null ? null : billingEntity.getLegalName();
    }

    private MoneyDTO money(Money money) {
        return money == null
                ? null
                : new MoneyDTO(money.getAmount(), money.getCurrency());
    }

    private String resolveActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            return null;
        }

        try {
            return auditActorService.resolve(actorId).displayName();
        } catch (Exception ex) {
            return actorId;
        }
    }
}