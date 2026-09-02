package com.core.dtos.driverduty;

import java.util.List;

import com.core.location.api.GeoPoint;

/**
 * A real, on-demand route computation for one leg of the garage-to-garage
 * duty model (A=garage, B=pickup, C=drop), computed the same way as the
 * existing C-&gt;A return-leg estimate (same {@link com.core.location.api.LocationService},
 * same provider chain) -- never a client-side calculation.
 *
 * {@code geometry} is only populated when a real road route came back;
 * when {@code routeAvailable} is false the frontend must show an honest
 * "route unavailable" state instead of fabricating a polyline. Computed
 * from each leg's known waypoints (garage/pickup/drop as captured on the
 * booking), not from the driver's live position -- the mobile client
 * combines this static route with the driver's own real-time GPS fix to
 * derive remaining distance along the polyline.
 */
public record DutyRouteLegResponse(
		String leg,
		boolean available,
		Double distanceKm,
		Long durationSeconds,
		String provider,
		boolean routeAvailable,
		GeoPoint fromLocation,
		GeoPoint toLocation,
		List<GeoPoint> geometry
) {
}
