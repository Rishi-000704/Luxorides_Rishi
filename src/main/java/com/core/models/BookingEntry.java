package com.core.models;

import java.time.Instant;
import java.util.List;

import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.Money;
import com.core.models.embedded.PackageSnapshot;
import com.core.models.enums.DutyStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class BookingEntry extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, length = 40)
	private String id;

	@Column(nullable = false, length = 40)
	private String dutyId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;
	
	@Embedded
	private PackageSnapshot pack;
	
	@Column(length = 40, nullable = false)
	private String masterVehicleId;

	private Instant reportingTime;
	private Instant dropTime;
	
	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "formattedAddress", column = @Column(name = "reporting_address", length = 300, nullable = false)),
		@AttributeOverride(name = "googlePlaceId", column = @Column(name = "reporting_place_id", length = 100)),
		@AttributeOverride(name = "latitude", column = @Column(name = "reporting_latitude")),
		@AttributeOverride(name = "longitude", column = @Column(name = "reporting_longitude"))
	})
	private AddressSnapshot reportingLocation;

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "formattedAddress", column = @Column(name = "drop_address", length = 300, nullable = true)),
		@AttributeOverride(name = "googlePlaceId", column = @Column(name = "drop_place_id", length = 100)),
		@AttributeOverride(name = "latitude", column = @Column(name = "drop_latitude")),
		@AttributeOverride(name = "longitude", column = @Column(name = "drop_longitude"))
	})
	private AddressSnapshot dropLocation;

	private Integer startingKM;
	private Integer closingKM;
	private Instant startAt;
	private Instant endAt;

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
		@AttributeOverride(name = "longitude", column = @Column(name = "garage_longitude"))
	})
	private AddressSnapshot garageLocation;

	@Column(length = 40)
	private String driverId;

	private Integer runningDays;
	private Integer extraChargebleDistance;
	private Float extraChargebleTime;
	private Boolean nightChargeble;

	@Column(length = 255)
	private String dutySlipImage;
	
	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "amount", column = @Column(name = "duty_total_amount", nullable = false, precision = 15, scale = 2)),
		@AttributeOverride(name = "currency", column = @Column(name = "duty_total_currency", nullable = false, length = 3)),
	})
	private Money dutyTotal;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DutyStatus status;

	@OneToMany(mappedBy = "bookingEntry", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ExtraCharge> charges;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "booking_entry_passengers", joinColumns = @JoinColumn(name = "booking_entry_id"))
	@Column(name = "passenger_id", length = 40)
	private List<String> passengerIds;
	
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
