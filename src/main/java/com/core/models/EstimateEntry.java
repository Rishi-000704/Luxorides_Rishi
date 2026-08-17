package com.core.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.PackageSnapshot;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EstimateEntry extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40)
	private String id;

	@Column(length = 40)
	private String estimateEntryId;

	@Embedded
	private PackageSnapshot pack;

	@Column(length = 40)
	private String masterVehicleId;

	@Column(nullable = false)
	private Instant reportingTime;

	private Instant dropTime;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(
					name = "formattedAddress",
					column = @Column(
							name = "reporting_address",
							length = 300,
							nullable = false)),
			@AttributeOverride(
					name = "googlePlaceId",
					column = @Column(
							name = "reporting_place_id",
							length = 100)),
			@AttributeOverride(
					name = "latitude",
					column = @Column(name = "reporting_latitude")),
			@AttributeOverride(
					name = "longitude",
					column = @Column(name = "reporting_longitude"))
	})
	private AddressSnapshot reportingLocation;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(
					name = "formattedAddress",
					column = @Column(
							name = "drop_address",
							length = 300)),
			@AttributeOverride(
					name = "googlePlaceId",
					column = @Column(
							name = "drop_place_id",
							length = 100)),
			@AttributeOverride(
					name = "latitude",
					column = @Column(name = "drop_latitude")),
			@AttributeOverride(
					name = "longitude",
					column = @Column(name = "drop_longitude"))
	})
	private AddressSnapshot dropLocation;

	private Integer runningDays;
	private Integer extraChargebleDistance;
	private Float extraChargebleTime;
	private Boolean nightChargeble;

	@OneToMany(
			mappedBy = "estimateEntry",
			cascade = CascadeType.ALL,
			orphanRemoval = true)
	private List<ExtraCharge> charges = new ArrayList<>();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "masterVehicleId",
			referencedColumnName = "id",
			insertable = false,
			updatable = false)
	private MasterVehicle requestedVehicle;
}