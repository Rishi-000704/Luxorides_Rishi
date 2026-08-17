package com.core.location.api;

import org.springframework.stereotype.Service;

import com.core.location.orchestrator.GeoProviderChain;
import com.core.location.util.CityNameNormalizer;
import com.core.models.embedded.AddressSnapshot;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

	private final GeoProviderChain chain;

	@Override
	public boolean isAirport(AddressSnapshot address) {
//		return chain.isAirport(address);
		boolean b = chain.isAirport(address);
		System.out.println(address + " is airport :"+b);
		return b;
	}

	@Override
	public DistanceTimeResult calculateDistanceAndTime(
			AddressSnapshot source,
			AddressSnapshot destination
	) {
		return chain.calculateDistance(source, destination);
	}

	@Override
	public String resolveState(AddressSnapshot address) {
		return chain.resolveState(address);
	}

	@Override
	public String resolveCity(AddressSnapshot address) {
		String city = chain.resolveCity(address);
		return CityNameNormalizer.normalize(city);
	}
}