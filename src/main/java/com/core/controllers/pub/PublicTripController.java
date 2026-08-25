package com.core.controllers.pub;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.pub.PublicTripStatusResponse;
import com.core.services.TripShareService;

import lombok.RequiredArgsConstructor;

/*
 * Unauthenticated by design (see SecurityConfiguration's /public/** permitAll) --
 * the opaque, hashed share token itself is the credential, matching the
 * driver-duty-link pattern already used for /driver-api/duty/{token}/**.
 */
@RestController
@RequestMapping("/public/trip")
@RequiredArgsConstructor
public class PublicTripController {

	private final TripShareService tripShareService;

	@GetMapping("/{token}")
	public PublicTripStatusResponse getStatus(@PathVariable String token) {
		return tripShareService.getPublicStatus(token);
	}
}
