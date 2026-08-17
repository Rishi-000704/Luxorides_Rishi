package com.core.mapper;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.core.dtos.booking.BookingDTO;
import com.core.dtos.booking.BookingEntryDTO;
import com.core.dtos.booking.BookingListItem;
import com.core.dtos.booking.DutyListItem;
import com.core.dtos.client.ClientBillingEntityDTO;
import com.core.dtos.client.ClientDTO;
import com.core.dtos.client.PassengerDTO;
import com.core.dtos.common.AddressSnapshotDTO;
import com.core.dtos.common.DisplayAddressDTO;
import com.core.dtos.common.MoneyDTO;
import com.core.dtos.common.NameDTO;
import com.core.dtos.driver.DriverDTO;
import com.core.dtos.payment.PaymentDTO;
import com.core.dtos.vehicle.FleetVehicleDTO;
import com.core.dtos.vehicle.MasterVehicleDTO;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Client;
import com.core.models.ClientBillingEntity;
import com.core.models.Driver;
import com.core.models.ExtraCharge;
import com.core.models.FleetVehicle;
import com.core.models.MasterVehicle;
import com.core.models.Passenger;
import com.core.models.Payment;
import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.Money;
import com.core.models.embedded.Name;
import com.core.services.common.AuditActorService;

@Component
public class BookingAssembler {

	private final AuditActorService auditActorService;

	public BookingAssembler(AuditActorService auditActorService) {
		this.auditActorService = auditActorService;
	}

	/*
	 * ===================================================== BOOKING
	 * =====================================================
	 */

	public BookingDTO assemble(Booking booking) {

		return new BookingDTO(booking.getBookingId(), booking.getOrgId(), booking.getStatus(), booking.getGstSnapshot(),
				booking.getRemarks(), booking.getInvoiceNumber(),

				enrichClient(booking.getClient()),

				booking.getClientBillingEntity() != null ? enrichBillingEntity(booking.getClientBillingEntity()) : null,

				booking.getEntries() == null ? List.of()
						: booking.getEntries().stream().map(this::assembleEntry).toList(),

				booking.getPayments() == null ? List.of()
						: booking.getPayments().stream().map(this::enrichPayment).toList(),

				toMoneyDTO(booking.getDiscount()),
				toMoneyDTO(booking.getTotal()),

				booking.getCreatedAt(), booking.getUpdatedAt(),
				auditActorService.resolve(booking.getCreatedBy()).displayName(),
				auditActorService.resolve(booking.getUpdatedBy()).displayName());
	}

	/*
	 * ===================================================== BOOKING ENTRY
	 * =====================================================
	 */

	private BookingEntryDTO assembleEntry(BookingEntry d) {

		return new BookingEntryDTO(d.getDutyId(), d.getStatus(),

				// PackageSnapshot (already frozen at booking time)
				d.getPack(),

				d.getReportingLocation() != null ? d.getReportingLocation().getFormattedAddress() : null,
				d.getReportingTime(), d.getStartingKM(),

				d.getDropLocation() != null ? d.getDropLocation().getFormattedAddress() : null, d.getDropTime(),
				d.getClosingKM(),

				d.getStartAt(), d.getEndAt(), d.getFlightNumber(),

				d.getRunningDays(), d.getExtraChargebleDistance(), d.getExtraChargebleTime(),
				Boolean.TRUE.equals(d.getNightChargeble()),

				d.getDutySlipImage(),

				d.getDutyTotal(),

				d.getMasterVehicleId() != null ? enrichMasterVehicle(d.getRequestedVehicle()) : null,

				d.getAllotedVehicle() != null ? enrichFleetVehicle(d.getAllotedVehicle()) : null,

				d.getDriver() != null ? enrichDriver(d.getDriver()) : null,

				d.getSupplier() != null ? enrichClient(d.getSupplier()) : null,

				d.getCharges() == null ? List.of() : d.getCharges().stream().map(this::enrichCharge).toList(),

				d.getPassengerIds(),d.getClientNotes(),

				d.getCreatedAt(), d.getUpdatedAt(), auditActorService.resolve(d.getCreatedBy()).displayName(),
				auditActorService.resolve(d.getUpdatedBy()).displayName());
	}

	/*
	 * ===================================================== CLIENT
	 * =====================================================
	 */

	private ClientDTO enrichClient(Client client) {
		if (client == null)
			return null;

		return new ClientDTO(client.getId(), client.getOrgId(), client.getUserId(),

				enrichName(client.getName()), client.getEmail(), client.getPhone(),

				enrichAddress(client.getAddress()), client.getPic(), client.getSupplier(),

				enrichBillingEntities(client.getClientBillingEntity()), enrichPassengers(client.getPassengers()),

				client.getCreatedAt(), client.getUpdatedAt(),
				auditActorService.resolve(client.getCreatedBy()).displayName(),
				auditActorService.resolve(client.getUpdatedBy()).displayName());
	}

	private NameDTO enrichName(Name n) {
		return n == null ? null : new NameDTO(n.getSalutation(), n.getFirstName(), n.getLastName());
	}

	private DisplayAddressDTO enrichAddress(DisplayAddress a) {
		return a == null ? null
				: new DisplayAddressDTO(a.getFormattedAddress(), a.getCity(), a.getState(), a.getPincode(),
						a.getCountryCode());
	}

	/*
	 * ===================================================== BILLING ENTITY
	 * =====================================================
	 */

	private List<ClientBillingEntityDTO> enrichBillingEntities(List<ClientBillingEntity> entities) {
		return entities == null || entities.isEmpty() ? List.of()
				: entities.stream().map(this::enrichBillingEntity).toList();
	}

	private ClientBillingEntityDTO enrichBillingEntity(ClientBillingEntity e) {
		return new ClientBillingEntityDTO(e.getId(), e.getOrgId(), e.getBrandName(), e.getLegalName(),

				e.getAddress() == null ? null
						: new DisplayAddressDTO(e.getAddress().getFormattedAddress(), e.getAddress().getCity(),
								e.getAddress().getState(), e.getAddress().getPincode(),
								e.getAddress().getCountryCode()),

				e.getCin(), e.getGstin(), e.getPhone(), e.getEmail(), e.getBusinessType(),

				e.getCreatedAt(), e.getUpdatedAt(), auditActorService.resolve(e.getCreatedBy()).displayName(),
				auditActorService.resolve(e.getUpdatedBy()).displayName());
	}

	/*
	 * ===================================================== PASSENGERS
	 * =====================================================
	 */

	private List<PassengerDTO> enrichPassengers(List<Passenger> passengers) {
	    if (passengers == null || passengers.isEmpty()) {
	        return List.of();
	    }

	    return passengers.stream()
	            .map(this::enrichPassenger)
	            .toList();
	}

	private PassengerDTO enrichPassenger(Passenger p) {
		return new PassengerDTO(p.getId(), p.getOrgId(), p.getClientId(),
				p.getName() == null ? null
						: new NameDTO(p.getName().getSalutation(), p.getName().getFirstName(),
								p.getName().getLastName()),
				p.getPhone(), p.getEmail(), p.getCreatedAt(), p.getUpdatedAt(),
				auditActorService.resolve(p.getCreatedBy()).displayName(),
				auditActorService.resolve(p.getUpdatedBy()).displayName());
	}

	/*
	 * ===================================================== PAYMENTS
	 * =====================================================
	 */

	private PaymentDTO enrichPayment(Payment p) {
		return new PaymentDTO(p.getId(), p.getPaymentMode(), p.getTransactionNumber(), p.getTransactionDate(),
				toMoneyDTO(p.getReceivedAmount()), toMoneyDTO(p.getTds()), p.getGateway(), p.getGatewayOrderId(),
				p.getGatewayPaymentId(), p.getStatus(), p.getRemarks(), p.getCreatedAt(), p.getUpdatedAt(),
				auditActorService.resolve(p.getCreatedBy()).displayName(),
				auditActorService.resolve(p.getUpdatedBy()).displayName());
	}

	/*
	 * ===================================================== DUTY SUB-ENTITIES
	 * =====================================================
	 */

	private BookingEntryDTO.ExtraChargeDTO enrichCharge(ExtraCharge c) {
		return new BookingEntryDTO.ExtraChargeDTO(
				c.getId(),
				c.getDescription(),
				toMoneyDTO(c.getAmount()),
				c.getImage()
		);
	}

	private DriverDTO enrichDriver(Driver driver) {
		if (driver == null) {
			return null;
		}

		return new DriverDTO(driver.getId(), driver.getOrgId(), driver.getClientId(),
				driver.getClientId() != null ? enrichName(driver.getClient().getName()) : null,
				enrichName(driver.getName()), enrichName(driver.getFatherName()),

				driver.getGender(), driver.getPhone(), driver.getAlternatePhone(),

				enrichAddress(driver.getAddress()),

				driver.getAdharNumber(), driver.getLicenseNumber(), driver.getPic(),

				driver.getOwnership(),

				driver.getCreatedAt(), driver.getUpdatedAt(),
				auditActorService.resolve(driver.getCreatedBy()).displayName(),
				auditActorService.resolve(driver.getUpdatedBy()).displayName());
	}

	private MoneyDTO toMoneyDTO(Money m) {
		if (m == null)
			return null;
		return new MoneyDTO(m.getAmount(), m.getCurrency());
	}

	private MasterVehicleDTO enrichMasterVehicle(MasterVehicle mv) {
		if (mv == null)
			return null;

		return new MasterVehicleDTO(mv.getId(), mv.getOrgId(),

				mv.getName(), mv.getPic(),

				mv.getFuelSystem(), mv.getFuelConsumption(), mv.getVehicleColor(), mv.getCategory(), mv.getBrand(),
				mv.getSeats(), mv.getDoors(), mv.getTransmissionType(), mv.getHorsePower(), mv.getVehicleClass(),
				mv.getModelYear(), mv.getPerformance(),

				mv.getDimension_length(), mv.getDimension_width(), mv.getDimension_height(),
				mv.getDimension_wheelbase(),

				mv.getSlug(), mv.getRating(), mv.getPopularity(), mv.getChauffeurDriven(),

				mv.getStatus(), mv.getRemarks(),

				mv.getCreatedAt(), mv.getUpdatedAt(), auditActorService.resolve(mv.getCreatedBy()).displayName(),
				auditActorService.resolve(mv.getUpdatedBy()).displayName());
	}

	private FleetVehicleDTO enrichFleetVehicle(FleetVehicle fv) {
		if (fv == null)
			return null;

		return new FleetVehicleDTO(fv.getId(), fv.getOrgId(),

				fv.getMasterVehicleId(), fv.getClientId(),

				fv.getRegistrationNumber(), fv.getOwnership(),

				fv.getGarageLocation() == null ? null
						: new AddressSnapshotDTO(fv.getGarageLocation().getFormattedAddress(),
								fv.getGarageLocation().getGooglePlaceId(), fv.getGarageLocation().getLatitude(),
								fv.getGarageLocation().getLongitude()),

				enrichMasterVehicle(fv.getMasterVehicle()), null,

				fv.getCreatedAt(), fv.getUpdatedAt(), auditActorService.resolve(fv.getCreatedBy()).displayName(),
				auditActorService.resolve(fv.getUpdatedBy()).displayName());
	}

	/*
	 * ===================================================== PAGE
	 * =====================================================
	 */

	public Page<BookingListItem> assemble(Page<Booking> page) {
		return page.map(BookingListItem::from);
	}

	public Page<DutyListItem> assembleDutyPage(Page<BookingEntry> page) {
		return page.map(DutyListItem::from);
	}
}
