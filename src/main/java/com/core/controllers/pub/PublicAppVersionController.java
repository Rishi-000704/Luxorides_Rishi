package com.core.controllers.pub;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.pub.AppVersionCheckResponse;
import com.core.services.AppVersionService;

import lombok.RequiredArgsConstructor;

/*
 * Unauthenticated by design (see SecurityConfiguration's /public/** permitAll)
 * -- must be reachable before login/at cold launch, and exposes nothing
 * beyond version numbers already visible in the published app store listing.
 */
@RestController
@RequestMapping("/public/app")
@RequiredArgsConstructor
public class PublicAppVersionController {

	private final AppVersionService appVersionService;

	@GetMapping("/version")
	public AppVersionCheckResponse checkVersion(@RequestParam(required = false) String installedVersion) {
		return appVersionService.check(installedVersion);
	}
}
