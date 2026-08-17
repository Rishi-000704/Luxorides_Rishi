package com.core.location.api;

import com.core.models.embedded.AddressSnapshot;

public interface LocationService {

	boolean isAirport(AddressSnapshot address);

	DistanceTimeResult calculateDistanceAndTime(
			AddressSnapshot source,
			AddressSnapshot destination
	);

	String resolveState(AddressSnapshot address);

	String resolveCity(AddressSnapshot address);
}