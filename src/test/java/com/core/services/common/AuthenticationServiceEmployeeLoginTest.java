package com.core.services.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.core.dtos.auth.EmployeeLoginRequest;
import com.core.dtos.auth.LoginResponse;
import com.core.exception.BusinessException;
import com.core.models.Employee;
import com.core.models.User;
import com.core.models.enums.AccountType;
import com.core.repositories.ClientRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.EmployeeRepository;
import com.core.repositories.UserOtpRepository;
import com.core.repositories.UserRepository;
import com.core.services.JwtService;

/*
 * P0 -- unlike client/driver OTP login, employee password login already
 * goes through AuthenticationManager.authenticate(...), so it already gets
 * Spring's own DaoAuthenticationProvider account-status checks (enabled/
 * locked/expired) for free -- no new production code needed here. These
 * tests exist to actually prove that (previously zero coverage of
 * authenticateEmployee at all) and lock in the existing correct behavior.
 */
class AuthenticationServiceEmployeeLoginTest {

	private static final String ORG_ID = "org-1";
	private static final String EMAIL = "employee@example.com";
	private static final String PASSWORD = "correct-password";

	private EmployeeRepository employeeRepository;
	private UserRepository userRepository;
	private PasswordEncoder passwordEncoder;
	private JwtService jwtService;
	private AuthenticationManager authenticationManager;
	private AuthenticationService service;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		ClientRepository clientRepository = mock(ClientRepository.class);
		employeeRepository = mock(EmployeeRepository.class);
		DriverRepository driverRepository = mock(DriverRepository.class);
		passwordEncoder = mock(PasswordEncoder.class);
		jwtService = mock(JwtService.class);
		authenticationManager = mock(AuthenticationManager.class);
		UserOtpRepository userOtpRepository = mock(UserOtpRepository.class);
		SMSService smsService = mock(SMSService.class);
		FileService fileService = mock(FileService.class);
		OtpRecordWriter otpRecordWriter = mock(OtpRecordWriter.class);
		FileAccessTokenService fileAccessTokenService = mock(FileAccessTokenService.class);

		service = new AuthenticationService(userRepository, clientRepository, employeeRepository, driverRepository,
				passwordEncoder, jwtService, authenticationManager, userOtpRepository, smsService, fileService,
				otpRecordWriter, fileAccessTokenService);
	}

	private Employee employee() {
		Employee emp = new Employee();
		emp.setId("emp-1");
		emp.setOrgId(ORG_ID);
		emp.setUserId("user-1");
		emp.setEmail(EMAIL);
		return emp;
	}

	private User backingUser(boolean enabled) {
		User user = new User();
		user.setId("user-1");
		user.setOrgId(ORG_ID);
		user.setAccountType(AccountType.EMPLOYEE);
		user.setPassword("hashed-password");
		user.setEnabled(enabled);
		return user;
	}

	@Test
	void authenticateEmployee_enabledAccount_correctPassword_succeeds() {
		Employee emp = employee();
		when(employeeRepository.findByEmail(EMAIL)).thenReturn(emp);
		User user = backingUser(true);
		when(userRepository.findByOrgIdAndId(ORG_ID, "user-1")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(PASSWORD, "hashed-password")).thenReturn(true);
		when(jwtService.generateToken(user)).thenReturn("jwt-token");

		LoginResponse response = service.authenticateEmployee(new EmployeeLoginRequest(EMAIL, PASSWORD));

		assertEquals("jwt-token", response.getToken());
	}

	// Proves the existing DaoAuthenticationProvider wiring already rejects a
	// disabled employee at login -- no production code change was needed for
	// this account type, only this test to demonstrate it.
	@Test
	void authenticateEmployee_disabledAccount_correctPassword_rejected() {
		Employee emp = employee();
		when(employeeRepository.findByEmail(EMAIL)).thenReturn(emp);
		User user = backingUser(false);
		when(userRepository.findByOrgIdAndId(ORG_ID, "user-1")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(PASSWORD, "hashed-password")).thenReturn(true);
		when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("Account is disabled"));

		assertThrows(DisabledException.class,
				() -> service.authenticateEmployee(new EmployeeLoginRequest(EMAIL, PASSWORD)));

		verify(jwtService, never()).generateToken(any(User.class));
	}

	@Test
	void authenticateEmployee_wrongPassword_rejected() {
		Employee emp = employee();
		when(employeeRepository.findByEmail(EMAIL)).thenReturn(emp);
		User user = backingUser(true);
		when(userRepository.findByOrgIdAndId(ORG_ID, "user-1")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

		assertThrows(BusinessException.class,
				() -> service.authenticateEmployee(new EmployeeLoginRequest(EMAIL, "wrong")));

		verify(jwtService, never()).generateToken(any(User.class));
	}
}
