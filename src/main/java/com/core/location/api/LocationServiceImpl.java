package com.core.location.api;

import org.springframework.stereotype.Service;

import com.core.location.cache.RouteCacheService;
import com.core.location.orchestrator.GeoProviderChain;
import com.core.location.util.CityNameNormalizer;
import com.core.models.embedded.AddressSnapshot;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

	private final GeoProviderChain chain;
	private final RouteCacheService routeCacheService;

	@Override
	public boolean isAirport(AddressSnapshot address) {
//		return chain.isAirport(address);
		boolean b = chain.isAirport(address);
		System.out.println(address + " is airport :"+b);
		return b;
	}

	/*
	 * Cost-aware route reuse (routing audit Phase 2B/2C): a fresh cached
	 * result for this exact origin/destination is returned with no external
	 * call at all; otherwise this instance's concurrent identical requests are
	 * coalesced into one live GeoProviderChain call, whose genuine (non-
	 * estimated) result is then persisted for reuse. GeoProviderChain itself
	 * is unchanged -- its provider failover/resilience behavior is exactly as
	 * before, RouteCacheService only decides whether that chain needs to run
	 * at all for this call.
	 */
	@Override
	public DistanceTimeResult calculateDistanceAndTime(
			AddressSnapshot source,
			AddressSnapshot destination
	) {
		return routeCacheService.getOrCompute(
				source,
				destination,
				() -> chain.calculateDistance(source, destination));
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