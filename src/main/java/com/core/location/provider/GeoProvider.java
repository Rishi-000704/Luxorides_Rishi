package com.core.location.provider;

import com.core.location.api.DistanceTimeResult;
import com.core.models.embedded.AddressSnapshot;

public interface GeoProvider {

	String getName();

	boolean isAirport(AddressSnapshot address);

	DistanceTimeResult calculateDistanceAndTime(
			AddressSnapshot source,
			AddressSnapshot destination
	);

	String resolveState(AddressSnapshot address);

	String resolveCity(AddressSnapshot address);
}