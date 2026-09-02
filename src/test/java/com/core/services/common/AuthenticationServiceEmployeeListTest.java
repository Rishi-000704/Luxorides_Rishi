package com.core.services.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.core.dtos.config.EmployeeListItem;
import com.core.models.Employee;
import com.core.models.User;
import com.core.models.enums.Authority;
import com.core.repositories.ClientRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.EmployeeRepository;
import com.core.repositories.UserOtpRepository;
import com.core.repositories.UserRepository;
import com.core.services.JwtService;

/*
 * P1.4 -- covers AuthenticationService.getEmployeeList, previously
 * 1 (employeeRepository.findByOrgId) + N (userRepository.findByOrgIdAndId,
 * once per employee) queries. Now 1 + 1 (a single batched
 * findByOrgIdAndIdIn). These tests prove the batched call is used, the
 * per-employee response is identical to what the old per-employee lookup
 * produced, and the existing null-safe fallback for a missing user is
 * preserved.
 */
class AuthenticationServiceEmployeeListTest {

	private static final String ORG_ID = "org-1";

	private EmployeeRepository employeeRepository;
	private UserRepository userRepository;
	private AuthenticationService service;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		ClientRepository clientRepository = mock(ClientRepository.class);
		employeeRepository = mock(EmployeeRepository.class);
		DriverRepository driverRepository = mock(DriverRepository.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		JwtService jwtService = mock(JwtService.class);
		AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
		UserOtpRepository userOtpRepository = mock(UserOtpRepository.class);
		SMSService smsService = mock(SMSService.class);
		FileService fileService = mock(FileService.class);
		OtpRecordWriter otpRecordWriter = mock(OtpRecordWriter.class);
		FileAccessTokenService fileAccessTokenService = mock(FileAccessTokenService.class);

		service = new AuthenticationService(userRepository, clientRepository, employeeRepository, driverRepository,
				passwordEncoder, jwtService, authenticationManager, userOtpRepository, smsService, fileService,
				otpRecordWriter, fileAccessTokenService);
	}

	private Employee employee(String id, String userId) {
		Employee e = new Employee();
		e.setId(id);
		e.setOrgId(ORG_ID);
		e.setUserId(userId);
		return e;
	}

	private User user(String id, boolean enabled, Authority... authorities) {
		User u = new User();
		u.setId(id);
		u.setOrgId(ORG_ID);
		u.setEnabled(enabled);
		u.setAuthorities(List.of(authorities));
		return u;
	}

	@Test
	void getEmployeeList_batchesUserLookup_insteadOfOnePerEmployee() {
		when(employeeRepository.findByOrgId(ORG_ID)).thenReturn(
				List.of(employee("e1", "u1"), employee("e2", "u2"), employee("e3", "u3")));

		when(userRepository.findByOrgIdAndIdIn(eq(ORG_ID), anyCollection())).thenReturn(List.of(
				user("u1", true, Authority.EMPLOYEE_VIEW),
				user("u2", false),
				user("u3", true)));

		service.getEmployeeList(ORG_ID);

		verify(userRepository).findByOrgIdAndIdIn(eq(ORG_ID), anyCollection());
		verify(userRepository, never()).findByOrgIdAndId(any(), any());
	}

	@Test
	void getEmployeeList_mapsEachEmployeeToItsOwnUser_sameAsThePreviousPerEmployeeLookup() {
		when(employeeRepository.findByOrgId(ORG_ID)).thenReturn(
				List.of(employee("e1", "u1"), employee("e2", "u2")));

		when(userRepository.findByOrgIdAndIdIn(eq(ORG_ID), anyCollection())).thenReturn(List.of(
				user("u1", true, Authority.EMPLOYEE_VIEW, Authority.EMPLOYEE_EDIT),
				user("u2", false)));

		List<EmployeeListItem> result = service.getEmployeeList(ORG_ID);

		assertEquals(2, result.size());

		EmployeeListItem item1 = result.stream().filter(i -> i.id().equals("e1")).findFirst().orElseThrow();
		assertTrue(item1.enabled());
		assertEquals(List.of(Authority.EMPLOYEE_VIEW, Authority.EMPLOYEE_EDIT), item1.authorities());

		EmployeeListItem item2 = result.stream().filter(i -> i.id().equals("e2")).findFirst().orElseThrow();
		assertFalse(item2.enabled());
	}

	@Test
	void getEmployeeList_fallsBackSafely_whenAnEmployeesUserRecordIsMissing() {
		when(employeeRepository.findByOrgId(ORG_ID)).thenReturn(List.of(employee("e1", "u1")));

		// User batch returns nothing -- same as the old per-employee
		// findByOrgIdAndId returning empty for this employee.
		when(userRepository.findByOrgIdAndIdIn(eq(ORG_ID), anyCollection())).thenReturn(List.of());

		List<EmployeeListItem> result = service.getEmployeeList(ORG_ID);

		assertEquals(1, result.size());
		assertEquals(Boolean.FALSE, result.get(0).enabled());
		assertEquals(List.of(), result.get(0).authorities());
	}

	@Test
	void getEmployeeList_emptyOrg_returnsEmptyListWithoutCallingUserBatch() {
		when(employeeRepository.findByOrgId(ORG_ID)).thenReturn(List.of());

		List<EmployeeListItem> result = service.getEmployeeList(ORG_ID);

		assertTrue(result.isEmpty());
		verify(userRepository, never()).findByOrgIdAndIdIn(any(), any());
	}
}
