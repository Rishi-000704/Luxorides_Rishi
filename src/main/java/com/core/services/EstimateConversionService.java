package com.core.services;

import java.time.Instant;
import java.util.ArrayList;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.events.assembler.PaymentEventAssembler;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Booking;
import com.core.models.BookingEntry;
import com.core.models.Estimate;
import com.core.models.EstimateEntry;
import com.core.models.ExtraCharge;
import com.core.models.Payment;
import com.core.models.embedded.Money;
import com.core.models.enums.BookingStatus;
import com.core.models.enums.DutyStatus;
import com.core.models.enums.EstimateStatus;
import com.core.models.enums.PaymentStatus;
import com.core.repositories.BookingRepository;
import com.core.repositories.EstimateRepository;
import com.core.repositories.PaymentRepository;
import com.core.util.BookingUtil;
import com.core.util.EstimateUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EstimateConversionService {

	private final EstimateRepository estimateRepository;
	private final BookingRepository bookingRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentEventAssembler paymentEventAssembler;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public Booking convertAfterPayment(
			String estimateDbId,
			String orgId,
			String paymentId) {

		Estimate estimate = estimateRepository
				.lockByIdAndOrgId(
						estimateDbId,
						orgId)
				.orElseThrow(() ->
						new NotFoundException(
								ErrorCode.ESTIMATE_NOT_FOUND,
								"Estimate not found"));

		Payment payment = paymentRepository
				.findByIdAndOrgId(
						paymentId,
						orgId)
				.orElseThrow(() ->
						new BusinessException(
								ErrorCode.BAD_REQUEST,
								"Payment not found"));

		if (payment.getStatus()
				!= PaymentStatus.CONFIRMED) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Only confirmed payment can convert an estimate");
		}

		if (estimate.getConvertedBookingId() != null
				&& !estimate.getConvertedBookingId()
				.isBlank()) {

			return bookingRepository
					.findByBookingIdAndOrgId(
							estimate.getConvertedBookingId(),
							orgId)
					.orElseThrow(() ->
							new NotFoundException(
									ErrorCode.BOOKING_NOT_FOUND,
									"Converted booking not found"));
		}

		validateEstimateForConversion(estimate);

		Booking booking = new Booking();

		booking.setBookingId(
				BookingUtil.generateBookingId());

		booking.setOrgId(orgId);
		booking.setClientId(
				estimate.getClientId());

		/*
		 * clientId controls persistence.
		 * The relation is also populated in memory because payment
		 * notification assembly runs before this entity is reloaded.
		 */
		booking.setClient(
				estimate.getClient());

		booking.setClientBillingEntityId(
				estimate.getClientBillingEntityId());

		booking.setClientBillingEntity(
				estimate.getClientBillingEntity());

		booking.setSourceEstimateId(
				estimate.getEstimateId());

		booking.setGstSnapshot(
				estimate.getGstSnapshot());

		booking.setTotal(
				estimate.getEstimatedPayable());

		booking.setDiscount(
				estimate.getDiscount());

		booking.setRemarks(
				buildBookingRemarks(estimate));

		booking.setStatus(
				BookingStatus.CONFIRMED);

		booking.setEntries(new ArrayList<>());
		booking.setPayments(new ArrayList<>());

		int dutyIndex = 1;

		for (EstimateEntry estimateEntry
				: estimate.getEntries()) {

			BookingEntry duty = mapDuty(
					booking,
					estimateEntry,
					dutyIndex++);

			booking.getEntries().add(duty);
		}

		payment.setBooking(booking);
		booking.getPayments().add(payment);

		Booking saved =
				bookingRepository.save(booking);

		paymentRepository.save(payment);

		estimate.setStatus(
				EstimateStatus.CONVERTED);

		estimate.setConvertedAt(
				Instant.now());

		estimate.setConvertedBookingId(
				saved.getBookingId());

		estimateRepository.save(estimate);

		eventPublisher.publishEvent(
				paymentEventAssembler
						.toPaymentConfirmedEvent(
								saved,
								payment));

		return saved;
	}

	private BookingEntry mapDuty(
			Booking booking,
			EstimateEntry estimateEntry,
			int dutyIndex) {

		BookingEntry duty = new BookingEntry();

		duty.setBooking(booking);

		duty.setDutyId(
				booking.getBookingId()
						+ "-"
						+ dutyIndex);

		duty.setPack(
				estimateEntry.getPack());

		duty.setMasterVehicleId(
				estimateEntry.getMasterVehicleId());

		duty.setReportingTime(
				estimateEntry.getReportingTime());

		duty.setDropTime(
				estimateEntry.getDropTime());

		duty.setReportingLocation(
				estimateEntry.getReportingLocation());

		duty.setDropLocation(
				estimateEntry.getDropLocation());

		duty.setRunningDays(
				estimateEntry.getRunningDays());

		duty.setExtraChargebleDistance(
				estimateEntry.getExtraChargebleDistance());

		duty.setExtraChargebleTime(
				estimateEntry.getExtraChargebleTime());

		duty.setNightChargeble(
				Boolean.TRUE.equals(
						estimateEntry.getNightChargeble()));

		duty.setStatus(DutyStatus.REQUESTED);

		duty.setDutyTotal(
				EstimateUtil.calculateLineTotal(
						estimateEntry));

		duty.setCharges(new ArrayList<>());

		copyExtraCharges(
				estimateEntry,
				duty);

		return duty;
	}

	private void copyExtraCharges(
			EstimateEntry estimateEntry,
			BookingEntry duty) {

		if (estimateEntry.getCharges() == null
				|| estimateEntry.getCharges().isEmpty()) {
			return;
		}

		for (ExtraCharge estimateCharge
				: estimateEntry.getCharges()) {

			if (estimateCharge == null) {
				continue;
			}

			ExtraCharge bookingCharge =
					new ExtraCharge();

			bookingCharge.setBookingEntry(duty);
			bookingCharge.setEstimateEntry(null);

			bookingCharge.setDescription(
					estimateCharge.getDescription());

			if (estimateCharge.getAmount() != null) {
				bookingCharge.setAmount(
						new Money(
								estimateCharge
										.getAmount()
										.getAmount(),
								estimateCharge
										.getAmount()
										.getCurrency()));
			}

			duty.getCharges().add(
					bookingCharge);
		}
	}

	private void validateEstimateForConversion(
			Estimate estimate) {

		if (estimate.getClientId() == null
				|| estimate.getClientId()
				.isBlank()) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Client is required before estimate conversion");
		}

		if (estimate.getEntries() == null
				|| estimate.getEntries()
				.isEmpty()) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Estimate must have at least one entry before conversion");
		}

		for (EstimateEntry entry
				: estimate.getEntries()) {

			String entryReference =
					entry.getEstimateEntryId()
							== null
							? entry.getId()
							: entry.getEstimateEntryId();

			if (entry.getReportingTime()
					== null) {

				throw new BusinessException(
						ErrorCode.BAD_REQUEST,
						"Reporting time is required for estimate entry "
								+ entryReference);
			}

			if (entry.getReportingLocation()
					== null
					|| entry
					.getReportingLocation()
					.getFormattedAddress()
					== null
					|| entry
					.getReportingLocation()
					.getFormattedAddress()
					.isBlank()) {

				throw new BusinessException(
						ErrorCode.BAD_REQUEST,
						"Reporting location is required for estimate entry "
								+ entryReference);
			}

			if (entry.getPack() == null) {

				throw new BusinessException(
						ErrorCode.BAD_REQUEST,
						"Package is required for estimate entry "
								+ entryReference);
			}

			if (entry.getMasterVehicleId()
					== null
					|| entry
					.getMasterVehicleId()
					.isBlank()) {

				throw new BusinessException(
						ErrorCode.BAD_REQUEST,
						"Vehicle is required for estimate entry "
								+ entryReference);
			}
		}
	}

	private String buildBookingRemarks(
			Estimate estimate) {

		String prefix =
				"Converted from estimate "
						+ estimate.getEstimateId();

		if (estimate.getRemarks() == null
				|| estimate.getRemarks().isBlank()) {
			return prefix;
		}

		return prefix
				+ " | "
				+ estimate.getRemarks();
	}
}