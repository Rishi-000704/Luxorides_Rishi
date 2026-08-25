package com.core.services;

import java.util.Arrays;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.auth.OrgSignupRequest;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.Employee;
import com.core.models.Org;
import com.core.models.User;
import com.core.models.enums.AccountType;
import com.core.models.enums.Authority;
import com.core.models.enums.OrgStatus;
import com.core.repositories.EmployeeRepository;
import com.core.repositories.OrgRepository;
import com.core.repositories.UserRepository;
import com.core.util.AddressUtil;

import lombok.RequiredArgsConstructor;

/*
 * Real self-onboarding for a new fleet operator: creates an actual Org row
 * (previously only ever created by DevDataSeeder at startup -- confirmed no
 * signup path existed before this) plus its first admin User+Employee,
 * granted every current Authority so they can manage their own org from
 * first login. Deliberately does NOT add any cross-org read capability --
 * every existing orgId-scoped query in the codebase stays exactly as
 * isolated as it was before this feature, per explicit product decision.
 */
@Service
@RequiredArgsConstructor
public class OrgSignupService {

	private final OrgRepository orgRepository;
	private final UserRepository userRepository;
	private final EmployeeRepository employeeRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public Org signup(OrgSignupRequest request) {
		if (request.orgId() == null || request.orgId().isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "orgId is required");
		}

		if (orgRepository.findByOrgId(request.orgId()).isPresent()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "This organization id is already taken");
		}

		if (employeeRepository.findByPhone(request.adminPhone()) != null) {
			throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "Mobile number already registered.");
		}

		if (employeeRepository.findByEmail(request.adminEmail()) != null) {
			throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "Email already registered.");
		}

		if (request.adminPassword() == null || request.adminPassword().length() < 8) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Password must be at least 8 characters");
		}

		Org org = new Org();
		org.setOrgId(request.orgId());
		org.setOrgName(request.orgName());
		org.setAddress(AddressUtil.toDisplayAddress(request.address()));
		org.setPan(request.pan());
		org.setCin(request.cin());
		org.setGstin(request.gstin());
		org.setPhone(request.phone());
		org.setEmail(request.email());
		org.setStatus(OrgStatus.ACTIVE);

		Org savedOrg = orgRepository.save(org);

		User user = new User();
		user.setAccountType(AccountType.EMPLOYEE);
		user.setEmail(request.adminEmail());
		user.setPhone(request.adminPhone());
		user.setOrgId(savedOrg.getOrgId());
		user.setEnabled(true);
		user.setPassword(passwordEncoder.encode(request.adminPassword()));
		user.setAuthorities(Arrays.asList(Authority.values()));

		User savedUser = userRepository.save(user);

		Employee employee = new Employee();
		employee.setOrgId(savedOrg.getOrgId());
		employee.setUserId(savedUser.getId());
		employee.setName(request.adminName());
		employee.setEmail(request.adminEmail());
		employee.setPhone(request.adminPhone());
		employee.setAddress(AddressUtil.toDisplayAddress(request.address()));

		employeeRepository.save(employee);

		return savedOrg;
	}
}
