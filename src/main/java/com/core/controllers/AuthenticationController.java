package com.core.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.auth.ClientOtpRequest;
import com.core.dtos.auth.ClientOtpResponse;
import com.core.dtos.auth.ClientOtpVerifyRequest;
import com.core.dtos.auth.EmployeeLoginRequest;
import com.core.dtos.auth.EmployeeMeResponse;
import com.core.dtos.auth.LoginResponse;
import com.core.dtos.client.ClientDTO;
import com.core.mapper.ClientAssembler;
import com.core.services.common.AuthenticationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticationController {

	private final AuthenticationService authenticationService;
	private final ClientAssembler clientAssembler;

	@PostMapping("/client/generate-otp")
	public ResponseEntity<ClientOtpResponse> generateOtp(@RequestBody ClientOtpRequest request) {
		return ResponseEntity.ok(this.authenticationService.generateOtp(request));
	}

	@PostMapping("/client/verify-otp")
	public ResponseEntity<LoginResponse> verifyOtp(@RequestBody ClientOtpVerifyRequest request) {
		return ResponseEntity.ok(this.authenticationService.verifyOtp(request));
	}

	@GetMapping("/client/me")
	public ResponseEntity<ClientDTO> clientme(Authentication authentication) {
		return ResponseEntity.ok(this.clientAssembler.assemble(this.authenticationService.clientme(authentication)));
	}

	@PostMapping("/employee/login")
	public ResponseEntity<LoginResponse> login(@RequestBody EmployeeLoginRequest request) {
		return ResponseEntity.ok(this.authenticationService.authenticateEmployee(request));
	}

	@GetMapping("/employee/me")
	public ResponseEntity<EmployeeMeResponse> employeeme(Authentication authentication) {
		return ResponseEntity.ok(this.authenticationService.employeeme(authentication));
	}
}
