package com.core.mapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.core.dtos.client.app.ClientBookingDTO;
import com.core.dtos.client.app.ClientBookingListDTO;
import com.core.dtos.common.MoneyDTO;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.FleetVehicle;
import com.core.models.MasterVehicle;
import com.core.models.Payment;
import com.core.models.embedded.Money;
import com.core.models.enums.FileAccessCategory;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.TripRatingRepository;
import com.core.services.common.FileAccessTokenService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public final class ClientBookingAssembler {

	private final TripRatingRepository tripRatingRepository;
	private final FileAccessTokenService fileAccessTokenService;

	/* ===================== ROOT ===================== */

	public ClientBookingDTO toDTO(Booking booking) {

		return new ClientBookingDTO(booking.getBookingId(),
				booking.getClientBillingEntity() != null ? booking.getClientBillingEntity().getLegalName() : null,
				booking.getGstSnapshot(),booking.getInvoiceNumber(), booking.getStatus(), booking.getCreatedAt(), toMoneyDTO(booking.getTotal()),
				mapEntries(booking), mapPayments(booking.getPayments()));
	}

	private MoneyDTO toMoneyDTO(Money m) {
		if (m == null)
			return null;
		return new MoneyDTO(m.getAmount(), m.getCurrency());
	}

	/* ===================== ENTRIES ===================== */

	private List<ClientBookingDTO.Entry> mapEntries(Booking booking) {
		if (booking.getEntries() == null) {
			return List.of();
		}

		/*
		 * P1.4 -- previously mapEntry called
		 * tripRatingRepository.findAverageStarsByDriverIdAndOrgId +
		 * countByDriverIdAndOrgId once each per entry (1 + 2N queries for a
		 * booking with N duty legs). Batched into one IN query for every
		 * distinct driver across the booking's entries.
		 */
		List<String> driverIds = booking.getEntries().stream()
				.map(e -> e.getDriver() != null ? e.getDriver().getId() : null)
				.filter(java.util.Objects::nonNull)
				.distinct()
				.toList();

		Map<String, double[]> ratingsByDriverId = driverIds.isEmpty()
				? Map.of()
				: tripRatingRepository.aggregateStarsByDriverIds(booking.getOrgId(), driverIds).stream()
						.collect(Collectors.toMap(
								r -> (String) r[0],
								r -> new double[] { (Double) r[1], ((Number) r[2]).doubleValue() }));

		return booking.getEntries().stream().map(e -> mapEntry(e, booking, ratingsByDriverId)).toList();
	}

	private ClientBookingDTO.Entry mapEntry(BookingEntry e, Booking booking, Map<String, double[]> ratingsByDriverId) {

		FleetVehicle fv = e.getAllotedVehicle();
		MasterVehicle mv = e.getRequestedVehicle();

		String vehicleName = fv != null ? fv.getMasterVehicle().getName() : mv != null ? mv.getName() : null;

		String rawPic = fv != null ? fv.getMasterVehicle().getPic() : mv != null ? mv.getPic() : null;
		String pic = fileAccessTokenService.toAccessUrl(rawPic, booking.getOrgId(), FileAccessCategory.PUBLIC);

		String registrationNumber = fv != null ? fv.getRegistrationNumber() : null;

		String brand = fv != null ? fv.getMasterVehicle().getBrand() : mv != null ? mv.getBrand() : null;

		String category = fv != null ? fv.getMasterVehicle().getCategory() : mv != null ? mv.getCategory() : null;

		String driverId = e.getDriver() != null ? e.getDriver().getId() : null;

		// GROUP BY omits a driver with zero ratings entirely -- same
		// null-average/0-count semantics as the single-driver methods this replaced.
		double[] rating = driverId != null ? ratingsByDriverId.get(driverId) : null;
		Double ratingAverage = rating != null ? rating[0] : null;
		Long ratingCount = rating != null ? (long) rating[1] : 0L;

		return new ClientBookingDTO.Entry(e.getDutyId(), e.getStatus(), e.getPack(),
				mapPassengers(e.getPassengerIds(), booking),

				// Vehicle
				vehicleName, pic, registrationNumber, brand, category,

				// Driver
				e.getDriver() != null ? e.getDriver().getName().getDisplayName() : null,
				e.getDriver() != null
						? fileAccessTokenService.toAccessUrl(e.getDriver().getPic(), booking.getOrgId(), FileAccessCategory.PRIVATE)
						: null,
				e.getDriver() != null ? e.getDriver().getGender() : null,
				e.getDriver() != null ? e.getDriver().getPhone() : null,
				ratingAverage, ratingCount,

				// Reporting
				e.getReportingLocation() != null ? e.getReportingLocation().getFormattedAddress() : null,
				e.getReportingTime(), e.getStartingKM(), e.getStartAt(), e.getArrivedAtPickupAt(),

				// Drop
				e.getDropLocation() != null ? e.getDropLocation().getFormattedAddress() : null, e.getDropTime(),
				e.getClosingKM(), e.getEndAt(),

				// Duty details
				e.getFlightNumber(), e.getRunningDays(), e.getExtraChargebleDistance(), e.getExtraChargebleTime(),
				e.getNightChargeble(), e.getDutyTotal(), e.getClientNotes(),

				mapExtraCharges(e.getCharges()));
	}

	/* ===================== PASSENGERS ===================== */

	private List<String> mapPassengers(List<String> passengerIds, Booking booking) {

		if (passengerIds == null || passengerIds.isEmpty()) {
			return List.of();
		}

		if (booking.getClient() == null || booking.getClient().getPassengers() == null) {
			return List.of();
		}

		return passengerIds
				.stream().map(pid -> booking.getClient().getPassengers().stream().filter(p -> p.getId().equals(pid))
						.findFirst().map(p -> p.getName().getDisplayName()).orElse(null))
				.filter(name -> name != null).toList();
	}

	/* ===================== EXTRA CHARGES ===================== */

	private List<ClientBookingDTO.ExtraCharge> mapExtraCharges(List<com.core.models.ExtraCharge> charges) {
		if (charges == null)
			return List.of();

		return charges.stream()
				.map(c -> new ClientBookingDTO.ExtraCharge(c.getId(), c.getDescription(), toMoneyDTO(c.getAmount())))
				.toList();
	}

	/* ===================== PAYMENTS ===================== */

	private List<ClientBookingDTO.Payment> mapPayments(List<Payment> payments) {
		if (payments == null)
			return List.of();

		return payments.stream().filter(p -> p.getStatus() == PaymentStatus.CONFIRMED).map(this::mapPayment).toList();
	}

	private ClientBookingDTO.Payment mapPayment(Payment p) {
		return new ClientBookingDTO.Payment(p.getId(), p.getPaymentMode(), p.getTransactionNumber(),
				p.getTransactionDate(), toMoneyDTO(p.getReceivedAmount()));
	}

	/* ===================== LIST VIEW ===================== */

	public ClientBookingListDTO toBookingList(Booking booking) {

		BookingEntry first = booking.getEntries() != null && !booking.getEntries().isEmpty()
				? booking.getEntries().get(0)
				: null;

		return new ClientBookingListDTO(booking.getBookingId(), resolveVehicleName(first),
				resolveReportingLocation(first), resolveReportingTime(first), resolveDropTime(first),
				booking.getStatus(), booking.getEntries() != null ? booking.getEntries().size() : 0);
	}

	private String resolveVehicleName(BookingEntry e) {
		if (e == null)
			return null;
		if (e.getAllotedVehicle() != null) {
			return e.getAllotedVehicle().getMasterVehicle().getName();
		}
		return e.getRequestedVehicle() != null ? e.getRequestedVehicle().getName() : null;
	}

	private String resolveReportingLocation(BookingEntry e) {
		return e != null && e.getReportingLocation() != null ? e.getReportingLocation().getFormattedAddress() : null;
	}

	private java.time.Instant resolveReportingTime(BookingEntry e) {
		return e != null ? e.getReportingTime() : null;
	}
	
	private java.time.Instant resolveDropTime(BookingEntry e) {
		return e != null ? e.getDropTime() : null;
	}
}
