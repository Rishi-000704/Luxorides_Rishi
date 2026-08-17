package com.core.models;

import com.core.models.embedded.AddressSnapshot;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CityGarage {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false)
	private String id;
	
	@Column(nullable = false, length = 40)
	private String orgId;
	
	@Column(length = 40)
	private String city;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "formattedAddress", column = @Column(name = "garage_address", length = 300)),
			@AttributeOverride(name = "googlePlaceId", column = @Column(name = "garage_place_id", length = 100)),
			@AttributeOverride(name = "latitude", column = @Column(name = "garage_latitude")),
			@AttributeOverride(name = "longitude", column = @Column(name = "garage_longitude")) })
	private AddressSnapshot garageLocation;
}
