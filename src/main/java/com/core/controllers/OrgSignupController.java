package com.core.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.auth.OrgSignupRequest;
import com.core.models.Org;
import com.core.services.OrgSignupService;

import lombok.RequiredArgsConstructor;

/*
 * Unauthenticated by design (falls under SecurityConfiguration's existing
 * /auth/** permitAll) -- a brand-new fleet operator has no token yet.
 * Self-onboarding only: creates one new Org + its first admin employee, no
 * cross-org visibility of any kind.
 */
@RestController
@RequestMapping("/auth/org")
@RequiredArgsConstructor
public class OrgSignupController {

	private final OrgSignupService orgSignupService;

	@PostMapping("/signup")
	public Org signup(@RequestBody OrgSignupRequest request) {
		return orgSignupService.signup(request);
	}
}
