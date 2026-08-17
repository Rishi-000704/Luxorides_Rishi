package com.core.models;

import java.time.Instant;
import java.util.List;

import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.Money;
import com.core.models.embedded.PackageSnapshot;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "invoice_entry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceEntry extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(length = 40)
	private String id;

	/* ---------------- Ownership ---------------- */

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invoice_id", nullable = false)
	private Invoice invoice;

	/* ---------------- Snapshot data ---------------- */

	@Column(length = 40)
	private String dutyId;

	@Embedded
	private PackageSnapshot pack;

	@Column(length = 40)
	private String masterVehicleId;

	private Instant reportingTime;
	private Instant dropTime;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "formattedAddress", column = @Column(name = "reporting_address", length = 300, nullable = false)),
			@AttributeOverride(name = "googlePlaceId", column = @Column(name = "reporting_place_id", length = 100)),
			@AttributeOverride(name = "latitude", column = @Column(name = "reporting_latitude")),
			@AttributeOverride(name = "longitude", column = @Column(name = "reporting_longitude")) })
	private AddressSnapshot reportingLocation;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "formattedAddress", column = @Column(name = "drop_address", length = 300, nullable = true)),
			@AttributeOverride(name = "googlePlaceId", column = @Column(name = "drop_place_id", length = 100)),
			@AttributeOverride(name = "latitude", column = @Column(name = "drop_latitude")),
			@AttributeOverride(name = "longitude", column = @Column(name = "drop_longitude")) })
	private AddressSnapshot dropLocation;

	private Integer startingKM;
	private Integer closingKM;
	private Instant startAt;
	private Instant endAt;
	private Integer totalRunningKM;
	private Float totalRunningHS;

	@Column(length = 255)
	private String dutySlipImage;

	@Column(length = 10)
	private String flightNumber;

	@Column(length = 40)
	private String supplierId;

	@Column(length = 40)
	private String fleetVehicleId;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "formattedAddress", column = @Column(name = "garage_address", length = 300)),
			@AttributeOverride(name = "googlePlaceId", column = @Column(name = "garage_place_id", length = 100)),
			@AttributeOverride(name = "latitude", column = @Column(name = "garage_latitude")),
			@AttributeOverride(name = "longitude", column = @Column(name = "garage_longitude")) })
	private AddressSnapshot garageLocation;

	@Column(length = 40)
	private String driverId;

	private Integer runningDays;
	private Integer extraChargebleDistance;
	private Float extraChargebleTime;
	private Boolean nightChargeble;
	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "extra_distance_charge_amount", precision = 15, scale = 2)),
			@AttributeOverride(name = "currency", column = @Column(name = "extra_distance_charge_currency", length = 3)), })
	private Money extraChargeDistance;
	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "extra_time_charge_amount", precision = 15, scale = 2)),
			@AttributeOverride(name = "currency", column = @Column(name = "extra_time_charge_currency", length = 3)), })
	private Money extraChargeTime;
	
	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "chargeble_base_fare_amount", nullable = false, precision = 15, scale = 2)),
			@AttributeOverride(name = "currency", column = @Column(name = "chargeble_base_fare_currency", nullable = false, length = 3)), })
	private Money chargebleBaseFare;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "duty_total_amount", nullable = false, precision = 15, scale = 2)),
			@AttributeOverride(name = "currency", column = @Column(name = "duty_total_currency", nullable = false, length = 3)), })
	private Money dutyTotal;

	@OneToMany(mappedBy = "invoiceEntry", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<InvoiceExtraCharge> charges;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "invoice_entry_passengers", joinColumns = @JoinColumn(name = "invoice_entry_id"))
	@Column(name = "passenger_name", length = 100)
	private List<String> passengerNames;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "masterVehicleId", referencedColumnName = "id", insertable = false, updatable = false)
	private MasterVehicle requestedVehicle;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fleetVehicleId", referencedColumnName = "id", insertable = false, updatable = false)
	private FleetVehicle allotedVehicle;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "driverId", referencedColumnName = "id", insertable = false, updatable = false)
	private Driver driver;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "supplierId", referencedColumnName = "id", insertable = false, updatable = false)
	private Client supplier;

	@Column(length = 500)
	private String clientNotes;
}
