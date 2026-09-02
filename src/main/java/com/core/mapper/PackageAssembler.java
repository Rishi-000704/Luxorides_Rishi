package com.core.mapper;

import org.springframework.stereotype.Component;

import com.core.dtos.client.ClientDTO;
import com.core.dtos.common.DisplayAddressDTO;
import com.core.dtos.common.MoneyDTO;
import com.core.dtos.common.NameDTO;
import com.core.dtos.common.PackageDTO;
import com.core.dtos.vehicle.MasterVehicleDTO;
import com.core.models.Client;
import com.core.models.MasterVehicle;
import com.core.models.Package;
import com.core.models.embedded.Money;
import com.core.models.enums.FileAccessCategory;
import com.core.services.common.AuditActorService;
import com.core.services.common.FileAccessTokenService;

@Component
public class PackageAssembler {

	private final AuditActorService auditActorService;
	private final FileAccessTokenService fileAccessTokenService;

	public PackageAssembler(AuditActorService auditActorService, FileAccessTokenService fileAccessTokenService) {
		this.auditActorService = auditActorService;
		this.fileAccessTokenService = fileAccessTokenService;
	}

	public PackageDTO assemble(Package p) {

		if (p == null) return null;

		return new PackageDTO(

				/* ================= Identity ================= */
				p.getId(),
				p.getOrgId(),

				/* ============ Scope & ownership ============= */
				p.getScope(),
				p.getClientId(),

				/* ============ Vehicle reference ============== */
				p.getMasterVehicleId(),

				/* ============ Package definition ============= */
				p.getDutyType(),
				p.getTime(),
				p.getUnit(),
				p.getDistance(),

				/* ================= Pricing ================== */
				money(p.getBaseFare()),
				money(p.getExtraPerKM()),
				money(p.getExtraPerHS()),
				money(p.getNightCharge()),

				/* ======== Visibility & applicability ========= */
				p.getForSales(),
				p.getLocation(),

				/* =========== Controlled expansions =========== */
				masterVehicleLite(p.getMasterVehicle()),
				p.getClient()!=null?clientLite(p.getClient()):null,

				/* ================== Audit =================== */
				p.getCreatedAt(),
				p.getUpdatedAt(),
				auditActorService.resolve(p.getCreatedBy()).displayName(),
				auditActorService.resolve(p.getUpdatedBy()).displayName()
		);
	}

	/*
	 * ========================= Sub-mappers =========================
	 * Explicit. Predictable. Zero surprises.
	 */

	private MoneyDTO money(Money m) {
		if (m == null) return null;
		return new MoneyDTO(m.getAmount(), m.getCurrency());
	}

	/**
	 * ONLY what Package UI / pricing needs.
	 * No billing entities. No passengers. No audit noise.
	 */
	private ClientDTO clientLite(Client c) {
		if (c == null) return null;

		return new ClientDTO(
				c.getId(),
				c.getOrgId(),
				c.getUserId(),
				c.getName() == null ? null
						: new NameDTO(
								c.getName().getSalutation(),
								c.getName().getFirstName(),
								c.getName().getLastName()
						),
				c.getEmail(),
				c.getPhone(),
				c.getAddress() == null ? null
						: new DisplayAddressDTO(
								c.getAddress().getFormattedAddress(),
								c.getAddress().getCity(),
								c.getAddress().getState(),
								c.getAddress().getPincode(),
								c.getAddress().getCountryCode()
						),
				fileAccessTokenService.toAccessUrl(c.getPic(), c.getOrgId(), FileAccessCategory.PRIVATE),
				c.getSupplier(),

				/* Hard stop – intentionally NOT loaded */
				java.util.List.of(),
				java.util.List.of(),

				c.getCreatedAt(),
				c.getUpdatedAt(),
				auditActorService.resolve(c.getCreatedBy()).displayName(),
				auditActorService.resolve(c.getUpdatedBy()).displayName()
		);
	}

	/**
	 * Read-only vehicle snapshot for package context.
	 * Enough for pricing & display. Nothing operational.
	 */
	private MasterVehicleDTO masterVehicleLite(MasterVehicle v) {
		if (v == null) return null;

		return new MasterVehicleDTO(
				v.getId(),
				v.getOrgId(),
				v.getName(),
				fileAccessTokenService.toAccessUrl(v.getPic(), v.getOrgId(), FileAccessCategory.PUBLIC),

				v.getFuelSystem(),
				v.getFuelConsumption(),
				v.getVehicleColor(),
				v.getCategory(),
				v.getBrand(),
				v.getSeats(),
				v.getDoors(),
				v.getTransmissionType(),
				v.getHorsePower(),
				v.getVehicleClass(),
				v.getModelYear(),
				v.getPerformance(),

				v.getDimension_length(),
				v.getDimension_width(),
				v.getDimension_height(),
				v.getDimension_wheelbase(),

				v.getSlug(),
				v.getRating(),
				v.getPopularity(),
				v.getChauffeurDriven(),

				v.getStatus(),
				v.getRemarks(),

				v.getCreatedAt(),
				v.getUpdatedAt(),
				auditActorService.resolve(v.getCreatedBy()).displayName(),
				auditActorService.resolve(v.getUpdatedBy()).displayName()
		);
	}
}
