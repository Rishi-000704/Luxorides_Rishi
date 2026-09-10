package com.core.controllers.config;

import java.io.IOException;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.config.EmployeeRequest;
import com.core.dtos.config.UpdatePasswordRequest;
import com.core.models.Employee;
import com.core.models.User;
import com.core.models.enums.Authority;
import com.core.security.SecurityContextUtil;
import com.core.services.common.AuthenticationService;

import lombok.RequiredArgsConstructor;
import com.core.dtos.config.EmployeeListItem;

@RequestMapping("/config/employee")
@RestController
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class EmployeeController {
	private final SecurityContextUtil security;
	private final AuthenticationService service;

	@PostMapping
	@PreAuthorize("hasAuthority('EMPLOYEE_ADD')")
	public Employee addEmployee(@RequestBody EmployeeRequest request) {
		return this.service.saveUserEmployee(security.orgId(), request);
	}

	@PutMapping
	@PreAuthorize("hasAuthority('EMPLOYEE_EDIT')")
	public Employee updateEmployee(@RequestBody EmployeeRequest request) {
		return this.service.updateUserEmployee(security.orgId(), request);
	}

	@PostMapping("/reset-password")
	@PreAuthorize("hasAuthority('EMPLOYEE_RESET_PASSWORD')")
	public Employee resetPassword(@RequestParam String userId, @RequestParam String newPassword) {
		return this.service.resetPassword(security.orgId(), userId, newPassword);
	}

	@PostMapping("/update-password")
	@PreAuthorize("hasRole('EMPLOYEE')")
	public Employee updatePassword(@RequestBody UpdatePasswordRequest request) {
		return this.service.updatePassword(security.orgId(), security.userId(), request);
	}

	@GetMapping("/list")
	@PreAuthorize("hasAuthority('EMPLOYEE_VIEW')")
	public List<EmployeeListItem> getList() {
		return this.service.getEmployeeList(security.orgId());
	}

	@GetMapping("/user/{userId}")
	@PreAuthorize("hasAuthority('EMPLOYEE_VIEW')")
	public User getUser(@PathVariable String userId) {
		return this.service.getUser(security.orgId(), userId);
	}

	@PutMapping("/users/{userId}/authorities")
	@PreAuthorize("hasAuthority('EMPLOYEE_UPDATE_AUTHORITIES')")
	public void updateAuthorities(@PathVariable String userId, @RequestBody List<Authority> authorities) {
		this.service.updateAuthorities(security.orgId(), userId, authorities);
	}

	@PutMapping("/users/{userId}/enabled")
	@PreAuthorize("hasAuthority('EMPLOYEE_EDIT')")
	public User updateUserEnabled(@PathVariable String userId, @RequestParam Boolean enabled) {
		if (security.userId().equals(userId) && Boolean.FALSE.equals(enabled)) {
			throw new AccessDeniedException("You cannot disable your own account.");
		}
		return this.service.updateUserEnabled(security.orgId(), userId, enabled);
	}

	@PostMapping("/update-pic")
	@PreAuthorize("hasRole('EMPLOYEE')")
	public Employee updatePic(@RequestParam("file") MultipartFile file) throws IOException {
		return this.service.updatePic(security.userId(), security.orgId(), file);
	}

	@PostMapping("/update-profile")
	@PreAuthorize("hasRole('EMPLOYEE')")
	public Employee updateProfile(@RequestBody EmployeeRequest request) {
		if (security.userId().equals(request.userId())) {
			return this.service.updateUserEmployee(security.orgId(), request);
		} else {
			throw new AccessDeniedException("You are not allowed to update another user's profile");
		}
	}
}
