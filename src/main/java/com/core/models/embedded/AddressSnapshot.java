package com.core.models.embedded;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AddressSnapshot {

	@Column(length = 300, nullable = false)
	private String formattedAddress;

	@Column(length = 100)
	private String googlePlaceId;

	private Double latitude;

	private Double longitude;

	public boolean isGeoVerified() {
		return googlePlaceId != null && !googlePlaceId.isBlank();
	}

	public boolean isvalid() {
		return latitude != null && longitude != null;
	}
}
