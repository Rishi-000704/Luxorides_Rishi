package com.core.location.provider;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.core.location.api.DistanceTimeResult;
import com.core.location.util.AddressParsingUtil;
import com.core.models.embedded.AddressSnapshot;

import lombok.extern.slf4j.Slf4j;

@Component
@Order(100)
@Slf4j
public class FallbackGeoProvider implements GeoProvider {

	@Override
	public String getName() {
		return "HAVERSINE";
	}

	@Override
	public boolean isAirport(AddressSnapshot address) {
		return AddressParsingUtil.looksLikeAirportText(
				address != null ? address.getFormattedAddress() : null
		);
	}

	@Override
	public DistanceTimeResult calculateDistanceAndTime(
			AddressSnapshot source,
			AddressSnapshot destination
	) {
		if (source == null
				|| destination == null
				|| source.getLatitude() == null
				|| source.getLongitude() == null
				|| destination.getLatitude() == null
				|| destination.getLongitude() == null) {

			throw new IllegalArgumentException(
					"Source and destination must have latitude and longitude"
			);
		}

		double distanceKm = haversine(
				source.getLatitude(),
				source.getLongitude(),
				destination.getLatitude(),
				destination.getLongitude()
		);

		long durationSeconds = (long) ((distanceKm / 40) * 3600);

		return new DistanceTimeResult(
				distanceKm,
				durationSeconds,
				true
		);
	}

	@Override
	public String resolveState(AddressSnapshot address) {
		String state = AddressParsingUtil.parseState(address);

		if (state == null || state.isBlank()) {
			throw new IllegalStateException(
					"Unable to resolve state from formatted address"
			);
		}

		log.warn(
				"Resolved state using low-confidence formatted-address fallback. state={}",
				state
		);

		return state;
	}

	@Override
	public String resolveCity(AddressSnapshot address) {
		return AddressParsingUtil.parseCity(address);
	}

	private double haversine(
			double lat1,
			double lon1,
			double lat2,
			double lon2
	) {
		double earthRadiusKm = 6371;

		double latitudeDifference = Math.toRadians(lat2 - lat1);
		double longitudeDifference = Math.toRadians(lon2 - lon1);

		double calculation =
				Math.sin(latitudeDifference / 2)
						* Math.sin(latitudeDifference / 2)
						+ Math.cos(Math.toRadians(lat1))
						* Math.cos(Math.toRadians(lat2))
						* Math.sin(longitudeDifference / 2)
						* Math.sin(longitudeDifference / 2);

		return earthRadiusKm
				* 2
				* Math.atan2(
				Math.sqrt(calculation),
				Math.sqrt(1 - calculation)
		);
	}
}