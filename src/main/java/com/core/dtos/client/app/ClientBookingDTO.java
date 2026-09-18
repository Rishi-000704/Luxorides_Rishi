package com.core.dtos.client.app;

import java.time.Instant;
import java.util.List;

import com.core.dtos.common.MoneyDTO;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.embedded.PackageSnapshot;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.PaymentMode;

public record ClientBookingDTO(

		String bookingId, String clientBillingEntityName, GstSnapshot gstSnapshot, String invoiceNumber,

		BookingStatus status, Instant createdAt, MoneyDTO total,

		List<Entry> entries, List<Payment> payments

) {

	public record Entry(

			String dutyId, DutyStatus status, PackageSnapshot packageSnapshot, List<String> passengers,

			String vehicleName, String vehiclePic, String vehicleNumber, String brand, String category,

			String driverName, String driverPic, String driverGender, String driverPhone,
			Double driverRatingAverage, Long driverRatingCount,

			String reportingLocation, Instant reportingTime, Integer startingKM, Instant startAt, Instant arrivedAtPickupAt,

			String dropLocation, Instant dropTime, Integer closingKM, Instant endAt,

			String flightNumber, Integer runningDays, Integer extraChargebleDistance, Float extraChargebleTime,

			Boolean nightChargeble, Money dutyTotal, String clientNotes,

			List<ExtraCharge> charges

	) {
	}

	public record Payment(

			String paymentId, PaymentMode paymentMode,

			String transactionNumber, Instant transactionDate,

			MoneyDTO paymentAmount) {
	}

	public record ExtraCharge(String id, String description, MoneyDTO amount) {
	}

}
