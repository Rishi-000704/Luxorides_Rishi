package com.core.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.estimate.EstimateEntryForm;
import com.core.dtos.estimate.EstimateEntryForm.ExtraChargeForm;
import com.core.dtos.estimate.EstimateForm;
import com.core.dtos.estimate.EstimateLinkResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Estimate;
import com.core.models.EstimateAccessToken;
import com.core.models.EstimateEntry;
import com.core.models.ExtraCharge;
import com.core.models.Package;
import com.core.models.Payment;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.EstimateLinkStatus;
import com.core.models.enums.EstimateStatus;
import com.core.models.enums.GstType;
import com.core.repositories.EstimateAccessTokenRepository;
import com.core.repositories.EstimateRepository;
import com.core.repositories.PackageRepository;
import com.core.repositories.PaymentRepository;
import com.core.util.AddressUtil;
import com.core.util.EstimateUtil;
import com.core.util.PackageUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EstimateService {

	private final EstimateRepository estimateRepository;
	private final EstimateAccessTokenRepository tokenRepository;
	private final PackageRepository packageRepository;
	private final PaymentRepository paymentRepository;
	private final ClientService clientService;

	@Value("${estimate.public-base-url:https://estimate.luxorides.com/client}")
	private String publicBaseUrl;

	@Transactional
	public Estimate create(
			EstimateForm form,
			String orgId) {

		Estimate estimate = new Estimate();

		estimate.setId(UUID.randomUUID().toString());
		estimate.setEstimateId(
				EstimateUtil.generateEstimateId());
		estimate.setOrgId(orgId);
		estimate.setStatus(EstimateStatus.DRAFT);
		estimate.setEntries(new ArrayList<>());

		applyHeader(estimate, form, orgId);

		if (form.entries() != null) {
			estimate.setEntries(
					buildEntries(
							estimate,
							form.entries(),
							orgId));
		}

		recalculateAndApplyAdvance(
				estimate,
				form.discountAmount(),
				form.gstRate(),
				form.advanceAmount());

		return attachData(
				estimateRepository.save(estimate));
	}

	@Transactional
	public Estimate update(
			EstimateForm form,
			String orgId) {

		if (form.estimateId() == null
				|| form.estimateId().isBlank()) {
			throw new NotFoundException(
					ErrorCode.ESTIMATE_NOT_FOUND,
					"estimateId is required for update");
		}

		Estimate estimate = lockEditableEstimate(
				form.estimateId(),
				orgId);

		applyHeader(estimate, form, orgId);

		if (form.entries() != null) {
			replaceEntries(
					estimate,
					form.entries(),
					orgId);
		}

		recalculateAndApplyAdvance(
				estimate,
				form.discountAmount(),
				form.gstRate(),
				form.advanceAmount());

		return attachData(
				estimateRepository.save(estimate));
	}

	@Transactional
	public Estimate addEntry(
			String estimateId,
			EstimateEntryForm form,
			String orgId) {

		Estimate estimate = lockEditableEstimate(
				estimateId,
				orgId);

		if (estimate.getEntries() == null) {
			estimate.setEntries(new ArrayList<>());
		}

		EstimateEntry entry = buildEntry(
				estimate,
				form,
				orgId,
				nextEntryIndex(estimate));

		estimate.getEntries().add(entry);

		recalculateWithExistingCommercials(estimate);

		return attachData(
				estimateRepository.save(estimate));
	}

	@Transactional
	public Estimate updateEntry(
			String estimateId,
			EstimateEntryForm form,
			String orgId) {

		if (form == null
				|| form.estimateEntryId() == null
				|| form.estimateEntryId().isBlank()) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"estimateEntryId is required for estimate entry update");
		}

		Estimate estimate = lockEditableEstimate(
				estimateId,
				orgId);

		EstimateEntry entry = findEntryOrThrow(
				estimate,
				form.estimateEntryId());

		applyEntryForm(
				entry,
				form,
				orgId,
				true);

		recalculateWithExistingCommercials(estimate);

		return attachData(
				estimateRepository.save(estimate));
	}

	@Transactional
	public Estimate deleteEntry(
			String estimateId,
			String estimateEntryId,
			String orgId) {

		Estimate estimate = lockEditableEstimate(
				estimateId,
				orgId);

		if (estimate.getEntries() == null
				|| estimate.getEntries().isEmpty()) {
			throw new NotFoundException(
					ErrorCode.BAD_REQUEST,
					"Estimate entry not found");
		}

		boolean removed = estimate.getEntries()
				.removeIf(entry ->
						estimateEntryId.equals(
								entry.getEstimateEntryId()));

		if (!removed) {
			throw new NotFoundException(
					ErrorCode.BAD_REQUEST,
					"Estimate entry not found");
		}

		recalculateWithExistingCommercials(estimate);

		return attachData(
				estimateRepository.save(estimate));
	}

	/**
	 * Loads the complete estimate data required by the employee detail API.
	 *
	 * This same retrieval path is also reused by the employee-managed client-link
	 * create, status and revoke operations. Public token resolution is intentionally
	 * unchanged for now.
	 */
	@Transactional(readOnly = true)
	public EstimateData getEstimate(
			String estimateId,
			String orgId) {

		if (estimateId == null || estimateId.isBlank()) {
			throw new NotFoundException(
					ErrorCode.ESTIMATE_NOT_FOUND,
					"Estimate not found");
		}

		Estimate estimate = estimateRepository
				.findByEstimateIdAndOrgId(
						estimateId,
						orgId)
				.orElseThrow(() ->
						new NotFoundException(
								ErrorCode.ESTIMATE_NOT_FOUND,
								"Estimate not found"));

		attachData(estimate);
		initializeDetailRelations(estimate);

		List<Payment> payments = paymentRepository
				.findAllByOrgIdAndEstimate_IdOrderByCreatedAtAsc(
						orgId,
						estimate.getId());

		return new EstimateData(
				estimate,
				payments == null
						? List.of()
						: List.copyOf(payments));
	}

	@Transactional(readOnly = true)
	public Page<Estimate> getPage(
			String orgId,
			EstimateStatus status,
			String search,
			Pageable pageable) {

		return estimateRepository.search(
				orgId,
				status,
				search,
				pageable);
	}

	@Transactional
	public EstimateLinkResponse createClientLink(
			String estimateId,
			String orgId) {

		Estimate estimate = getEstimate(
				estimateId,
				orgId).estimate();

		if (estimate.getStatus() == EstimateStatus.PAID
				|| estimate.getStatus()
				== EstimateStatus.CONVERTED) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Estimate is already paid/converted");
		}

		if (estimate.getEntries() == null
				|| estimate.getEntries().isEmpty()) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Estimate must have at least one entry before sharing");
		}

		validateEstimateBeforeSharing(estimate);

		tokenRepository
				.findTopByEstimateIdAndOrgIdAndStatusOrderByCreatedAtDesc(
						estimate.getId(),
						orgId,
						EstimateLinkStatus.ACTIVE)
				.ifPresent(oldToken -> {
					oldToken.setStatus(
							EstimateLinkStatus.REVOKED);
					oldToken.setRevokedAt(Instant.now());
					tokenRepository.save(oldToken);
				});

		String rawToken =
				EstimateUtil.generatePublicToken();

		EstimateAccessToken token =
				new EstimateAccessToken();

		token.setOrgId(orgId);
		token.setEstimateId(estimate.getId());
		token.setTokenHash(
				EstimateUtil.sha256(rawToken));
		token.setStatus(EstimateLinkStatus.ACTIVE);
		token.setExpiresAt(resolveExpiry(estimate));

		estimate.setStatus(EstimateStatus.SENT);
		estimate.setSentAt(Instant.now());

		estimateRepository.save(estimate);
		tokenRepository.save(token);

		return new EstimateLinkResponse(
				estimate.getEstimateId(),
				buildPublicUrl(rawToken),
				token.getStatus(),
				token.getExpiresAt(),
				token.getLastViewedAt(),
				token.getPaidAt());
	}

	@Transactional(readOnly = true)
	public EstimateLinkResponse getClientLinkStatus(
			String estimateId,
			String orgId) {

		Estimate estimate = getEstimate(
				estimateId,
				orgId).estimate();

		return tokenRepository
				.findTopByEstimateIdAndOrgIdAndStatusOrderByCreatedAtDesc(
						estimate.getId(),
						orgId,
						EstimateLinkStatus.ACTIVE)
				.map(token ->
						new EstimateLinkResponse(
								estimate.getEstimateId(),
								null,
								token.getStatus(),
								token.getExpiresAt(),
								token.getLastViewedAt(),
								token.getPaidAt()))
				.orElse(
						new EstimateLinkResponse(
								estimate.getEstimateId(),
								null,
								null,
								null,
								null,
								null));
	}

	@Transactional
	public void revokeClientLink(
			String estimateId,
			String orgId) {

		Estimate estimate = getEstimate(
				estimateId,
				orgId).estimate();

		tokenRepository
				.findTopByEstimateIdAndOrgIdAndStatusOrderByCreatedAtDesc(
						estimate.getId(),
						orgId,
						EstimateLinkStatus.ACTIVE)
				.ifPresent(token -> {
					token.setStatus(
							EstimateLinkStatus.REVOKED);
					token.setRevokedAt(Instant.now());
					tokenRepository.save(token);
				});

		if (estimate.getStatus() != EstimateStatus.PAID
				&& estimate.getStatus()
				!= EstimateStatus.CONVERTED) {
			estimate.setStatus(EstimateStatus.REVOKED);
			estimateRepository.save(estimate);
		}
	}

	private Estimate lockEditableEstimate(
			String estimateId,
			String orgId) {

		Estimate estimate = estimateRepository
				.lockByEstimateIdAndOrgId(
						estimateId,
						orgId)
				.orElseThrow(() ->
						new NotFoundException(
								ErrorCode.ESTIMATE_NOT_FOUND,
								"Estimate not found"));

		if (estimate.getStatus() == EstimateStatus.PAID
				|| estimate.getStatus()
				== EstimateStatus.CONVERTED) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Paid/converted estimate cannot be edited");
		}

		return estimate;
	}

	private void applyHeader(
			Estimate estimate,
			EstimateForm form,
			String orgId) {

		if (form == null) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Estimate form is required");
		}

		if (form.clientId() == null
				|| form.clientId().isBlank()) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"clientId is required");
		}

		clientService.get(
				form.clientId(),
				orgId);

		estimate.setEstimateDate(
				form.estimateDate() == null
						? Instant.now()
						: form.estimateDate());

		estimate.setValidTill(form.validTill());
		estimate.setClientId(form.clientId());

		estimate.setClientBillingEntityId(
				blankToNull(
						form.clientBillingEntityId()));

		estimate.setRemarks(form.remarks());

		estimate.setGstSnapshot(
				GstSnapshot.of(
						form.gstType() == null
								? GstType.EXEMPT
								: form.gstType(),
						BigDecimal.ZERO,
						form.gstRate() == null
								? 0
								: form.gstRate()));
	}

	private List<EstimateEntry> buildEntries(
			Estimate estimate,
			List<EstimateEntryForm> forms,
			String orgId) {

		List<EstimateEntry> entries =
				new ArrayList<>();

		if (forms == null || forms.isEmpty()) {
			return entries;
		}

		int index = 1;

		for (EstimateEntryForm form : forms) {
			entries.add(
					buildEntry(
							estimate,
							form,
							orgId,
							index++));
		}

		return entries;
	}

	private void replaceEntries(
			Estimate estimate,
			List<EstimateEntryForm> forms,
			String orgId) {

		if (estimate.getEntries() == null) {
			estimate.setEntries(new ArrayList<>());
		} else {
			estimate.getEntries().clear();
		}

		if (forms == null || forms.isEmpty()) {
			return;
		}

		int index = 1;

		for (EstimateEntryForm form : forms) {
			estimate.getEntries().add(
					buildEntry(
							estimate,
							form,
							orgId,
							index++));
		}
	}

	private EstimateEntry buildEntry(
			Estimate estimate,
			EstimateEntryForm form,
			String orgId,
			int index) {

		EstimateEntry entry = new EstimateEntry();

		entry.setEstimateEntryId(
				EstimateUtil.generateEstimateEntryId(
						estimate,
						index));

		applyEntryForm(
				entry,
				form,
				orgId,
				false);

		return entry;
	}

	private void applyEntryForm(
			EstimateEntry entry,
			EstimateEntryForm form,
			String orgId,
			boolean packageOptional) {

		if (form == null) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Estimate entry is required");
		}

		validateRequiredEntryFields(form);
		validateOperationalFields(form);

		Package pack = null;

		if (form.packageId() == null
				|| form.packageId().isBlank()) {

			if (!packageOptional) {
				throw new BusinessException(
						ErrorCode.BAD_REQUEST,
						"packageId is required");
			}
		} else {
			pack = packageRepository
					.findById(form.packageId())
					.orElseThrow(() ->
							new NotFoundException(
									ErrorCode.PACKAGE_NOT_FOUND,
									"Package not found: "
											+ form.packageId()));

			if (!orgId.equals(pack.getOrgId())) {
				throw new SecurityException(
						"Package does not belong to current org");
			}
		}

		if (pack != null) {
			entry.setPack(
					PackageUtil.toPackageSnapshot(pack));

			entry.setMasterVehicleId(
					form.masterVehicleId() == null
							|| form.masterVehicleId().isBlank()
							? pack.getMasterVehicleId()
							: form.masterVehicleId());

		} else if (form.masterVehicleId() != null
				&& !form.masterVehicleId().isBlank()) {
			entry.setMasterVehicleId(
					form.masterVehicleId());
		}

		entry.setReportingTime(
				form.reportingTime());

		entry.setDropTime(
				form.dropTime());

		entry.setReportingLocation(
				AddressUtil.toAddressSnapshot(
						form.reportingLocation()));

		entry.setDropLocation(
				AddressUtil.toAddressSnapshot(
						form.dropLocation()));

		entry.setRunningDays(
				form.runningDays());

		entry.setExtraChargebleDistance(
				form.extraChargebleDistance());

		entry.setExtraChargebleTime(
				form.extraChargebleTime());

		entry.setNightChargeble(
				Boolean.TRUE.equals(
						form.nightChargeble()));

		replaceExtraCharges(
				entry,
				form.charges());
	}

	private void validateRequiredEntryFields(
			EstimateEntryForm form) {

		if (form.reportingTime() == null) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"reportingTime is required");
		}

		if (form.reportingLocation() == null) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"reportingLocation is required");
		}

		String formattedAddress =
				form.reportingLocation()
						.formattedAddress();

		if (formattedAddress == null
				|| formattedAddress.isBlank()) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"reportingLocation.formattedAddress is required");
		}
	}

	private void validateOperationalFields(
			EstimateEntryForm form) {

		if (form.runningDays() != null
				&& form.runningDays() < 0) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"runningDays cannot be negative");
		}

		if (form.extraChargebleDistance() != null
				&& form.extraChargebleDistance() < 0) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"extraChargebleDistance cannot be negative");
		}

		if (form.extraChargebleTime() != null
				&& form.extraChargebleTime() < 0) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"extraChargebleTime cannot be negative");
		}
	}

	private void replaceExtraCharges(
			EstimateEntry entry,
			List<ExtraChargeForm> forms) {

		if (entry.getCharges() == null) {
			entry.setCharges(new ArrayList<>());
		} else {
			entry.getCharges().clear();
		}

		if (forms == null || forms.isEmpty()) {
			return;
		}

		for (ExtraChargeForm form : forms) {
			entry.getCharges().add(
					buildExtraCharge(
							entry,
							form));
		}
	}

	private ExtraCharge buildExtraCharge(
			EstimateEntry entry,
			ExtraChargeForm form) {

		if (form == null) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Extra charge cannot be null");
		}

		if (form.description() == null
				|| form.description().isBlank()) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Extra charge description is required");
		}

		if (form.amount() == null) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Extra charge amount is required");
		}

		if (form.amount()
				.compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Extra charge amount cannot be negative");
		}

		ExtraCharge charge = new ExtraCharge();

		charge.setEstimateEntry(entry);
		charge.setBookingEntry(null);
		charge.setDescription(
				form.description().trim());
		charge.setAmount(
				Money.INR(form.amount()));

		return charge;
	}

	private void validateEstimateBeforeSharing(
			Estimate estimate) {

		for (EstimateEntry entry
				: estimate.getEntries()) {

			String entryReference =
					entry.getEstimateEntryId()
							== null
							? entry.getId()
							: entry.getEstimateEntryId();

			if (entry.getMasterVehicleId()
					== null
					|| entry
					.getMasterVehicleId()
					.isBlank()) {

				throw new BusinessException(
						ErrorCode.BAD_REQUEST,
						"Vehicle is required for entry "
								+ entryReference);
			}

			if (entry.getPack() == null) {

				throw new BusinessException(
						ErrorCode.BAD_REQUEST,
						"Package is required for entry "
								+ entryReference);
			}

			if (entry.getReportingTime()
					== null) {

				throw new BusinessException(
						ErrorCode.BAD_REQUEST,
						"Reporting time is required for entry "
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
						"Reporting location is required for entry "
								+ entryReference);
			}
		}
	}

	private EstimateEntry findEntryOrThrow(
			Estimate estimate,
			String estimateEntryId) {

		if (estimate.getEntries() == null) {
			throw new NotFoundException(
					ErrorCode.BAD_REQUEST,
					"Estimate entry not found");
		}

		return estimate.getEntries()
				.stream()
				.filter(entry ->
						estimateEntryId.equals(
								entry.getEstimateEntryId()))
				.findFirst()
				.orElseThrow(() ->
						new NotFoundException(
								ErrorCode.BAD_REQUEST,
								"Estimate entry not found"));
	}

	private int nextEntryIndex(Estimate estimate) {
		if (estimate.getEntries() == null
				|| estimate.getEntries().isEmpty()) {
			return 1;
		}

		int max = 0;
		String prefix =
				estimate.getEstimateId() + "-";

		for (EstimateEntry entry
				: estimate.getEntries()) {

			String entryId =
					entry.getEstimateEntryId();

			if (entryId == null
					|| !entryId.startsWith(prefix)) {
				continue;
			}

			try {
				max = Math.max(
						max,
						Integer.parseInt(
								entryId.substring(
										prefix.length())));
			} catch (NumberFormatException ignored) {
				// Ignore legacy or non-standard entry IDs.
			}
		}

		return max + 1;
	}

	private void recalculateWithExistingCommercials(
			Estimate estimate) {

		BigDecimal discountAmount =
				estimate.getDiscount() == null
						? BigDecimal.ZERO
						: estimate.getDiscount()
						.getAmount();

		Integer gstRate =
				estimate.getGstSnapshot() == null
						? 0
						: estimate.getGstSnapshot()
						.getGstRate();

		BigDecimal advanceAmount =
				estimate.getAdvanceAmount() == null
						? BigDecimal.ZERO
						: estimate.getAdvanceAmount()
						.getAmount();

		recalculateAndApplyAdvance(
				estimate,
				discountAmount,
				gstRate,
				advanceAmount);
	}

	private void recalculateAndApplyAdvance(
			Estimate estimate,
			BigDecimal discountAmount,
			Integer gstRate,
			BigDecimal advanceAmount) {

		if (estimate.getGstSnapshot() == null) {
			estimate.setGstSnapshot(
					GstSnapshot.of(
							GstType.EXEMPT,
							BigDecimal.ZERO,
							0));
		}

		EstimateUtil.calculateTotals(
				estimate,
				discountAmount,
				gstRate);

		BigDecimal safeAdvanceAmount =
				advanceAmount == null
						? BigDecimal.ZERO
						: advanceAmount;

		if (safeAdvanceAmount
				.compareTo(BigDecimal.ZERO) > 0
				&& safeAdvanceAmount.compareTo(
				estimate.getEstimatedPayable()
						.getAmount()) > 0) {

			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Advance amount cannot be greater than estimate payable amount");
		}

		estimate.setAdvanceAmount(
				Money.INR(safeAdvanceAmount));
	}

	private Estimate attachData(
			Estimate estimate) {

		if (estimate == null) {
			return null;
		}

		if (estimate.getClientId() != null) {
			estimate.setClient(
					clientService.get(
							estimate.getClientId(),
							estimate.getOrgId()));
		}

		if (estimate.getClientBillingEntityId() != null) {
			estimate.setClientBillingEntity(
					clientService.getCorporate(
							estimate.getClientBillingEntityId(),
							estimate.getOrgId()));
		}

		return estimate;
	}

	private void initializeDetailRelations(
			Estimate estimate) {

		if (estimate.getEntries() == null) {
			return;
		}

		for (EstimateEntry entry : estimate.getEntries()) {
			if (entry.getCharges() != null) {
				entry.getCharges().size();
			}

			if (entry.getRequestedVehicle() != null) {
				// Access a non-identifier field so the Hibernate proxy is fully loaded
				// before the service transaction closes.
				entry.getRequestedVehicle().getName();
			}
		}
	}

	private Instant resolveExpiry(
			Estimate estimate) {

		if (estimate.getValidTill() != null
				&& estimate.getValidTill()
				.isAfter(Instant.now())) {
			return estimate.getValidTill();
		}

		return Instant.now()
				.plus(7, ChronoUnit.DAYS);
	}

	private String buildPublicUrl(
			String rawToken) {

		return publicBaseUrl
				.replaceAll("/$", "")
				+ "/"
				+ rawToken;
	}

	private String blankToNull(
			String value) {

		return value == null || value.isBlank()
				? null
				: value;
	}

	public record EstimateData(
			Estimate estimate,
			List<Payment> payments) {
	}

}