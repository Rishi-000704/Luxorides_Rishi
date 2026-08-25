package com.core.util;

import com.core.models.embedded.AddressSnapshot;

/*
 * Real-time ETA/distance-remaining for an in-progress duty, derived purely
 * from the driver's live GPS fix -- never a fabricated value. Deliberately
 * NOT routed through LocationService/GeoProviderChain: that would fire a
 * real Google/ORS Directions call on every ~15s location ping, which is
 * both costly and rate-limited for a background polling cadence. Instead
 * this uses haversine straight-line distance (same formula as
 * FallbackGeoProvider, duplicated locally since that one is private to the
 * GeoProvider interface) combined with the device-reported instantaneous
 * GPS speed (speedMps) already captured on every ping -- a real measurement,
 * not an estimate built from history. Always straight-line, so the result
 * is always marked "estimated".
 */
public final class EtaEstimator {

	private static final double EARTH_RADIUS_KM = 6371;

	/* Below this speed the ETA math produces meaningless (huge/near-infinite) numbers. */
	private static final double MIN_SPEED_MPS_FOR_ETA = 1.0;

	private EtaEstimator() {
	}

	public record Estimate(Double distanceRemainingKm, Double etaMinutes) {
		static final Estimate EMPTY = new Estimate(null, null);
	}

	public static Estimate estimate(
			AddressSnapshot dropLocation,
			Double currentLatitude,
			Double currentLongitude,
			Double speedMps
	) {
		if (dropLocation == null
				|| dropLocation.getLatitude() == null
				|| dropLocation.getLongitude() == null
				|| currentLatitude == null
				|| currentLongitude == null) {
			return Estimate.EMPTY;
		}

		double distanceKm = haversine(
				currentLatitude,
				currentLongitude,
				dropLocation.getLatitude(),
				dropLocation.getLongitude()
		);

		if (speedMps == null || speedMps < MIN_SPEED_MPS_FOR_ETA) {
			return new Estimate(round(distanceKm), null);
		}

		double etaMinutes = (distanceKm * 1000.0 / speedMps) / 60.0;

		return new Estimate(round(distanceKm), round(etaMinutes));
	}

	private static double round(double value) {
		return Math.round(value * 10.0) / 10.0;
	}

	private static double haversine(double lat1, double lon1, double lat2, double lon2) {
		double latDiff = Math.toRadians(lat2 - lat1);
		double lonDiff = Math.toRadians(lon2 - lon1);

		double a = Math.sin(latDiff / 2) * Math.sin(latDiff / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(lonDiff / 2) * Math.sin(lonDiff / 2);

		return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}
}
