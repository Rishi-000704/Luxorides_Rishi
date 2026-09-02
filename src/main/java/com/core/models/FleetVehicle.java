package com.core.models;

import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.AuditableEntity;
import com.core.models.enums.OwnershipType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * P1.8 -- idx_fleet_vehicle_org added: this entity had no indexes at all,
 * despite every org-scoped query against it (findByOrgId, findByOrgIdAndIdIn,
 * findByOrgIdFetchMasterVehicle) doing a full table scan. Those queries feed
 * DispatchSuggestionService's per-duty vehicle context, VehicleMaintenanceService's
 * prediction pass, and FleetAnalyticsService's utilization report -- all hot,
 * frequently-called read paths. No `name` on @Table -- keeps whatever table
 * name Hibernate's default naming strategy was already deriving; only adds
 * an index definition. Plain (non-unique) index -- safe to add regardless of
 * existing data, unlike a uniqueness constraint.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(indexes = { @Index(name = "idx_fleet_vehicle_org", columnList = "org_id") })
public class FleetVehicle extends AuditableEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40)
	private String id;
	@Column(nullable = false, length = 40)
	private String orgId;
	@Column(nullable = false, length = 40)
	private String masterVehicleId;
	@Column(length = 40)
	private String clientId;

	@Column(nullable = false, length = 20)
	private String registrationNumber;
	
	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "formattedAddress", column = @Column(name = "garage_address", length = 300)),
		@AttributeOverride(name = "googlePlaceId", column = @Column(name = "garage_place_id", length = 100)),
		@AttributeOverride(name = "latitude", column = @Column(name = "garage_latitude")),
		@AttributeOverride(name = "longitude", column = @Column(name = "garage_longitude"))
	})
	private AddressSnapshot garageLocation;

	@Column(nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private OwnershipType ownership;

	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
	@JoinColumn(name = "masterVehicleId", referencedColumnName = "id", insertable = false, updatable = false)
	private MasterVehicle masterVehicle;

	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
	@JoinColumn(name = "clientId", referencedColumnName = "id", insertable = false, updatable = false)
	private Client client;
}
