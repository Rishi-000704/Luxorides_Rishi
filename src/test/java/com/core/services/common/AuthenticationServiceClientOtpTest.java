package com.core.services.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.core.dtos.auth.ClientOtpVerifyRequest;
import com.core.dtos.auth.LoginResponse;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.Client;
import com.core.models.User;
import com.core.models.UserOtp;
import com.core.models.enums.AccountType;
import com.core.repositories.ClientRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.EmployeeRepository;
import com.core.repositories.UserOtpRepository;
import com.core.repositories.UserRepository;
import com.core.services.JwtService;

/*
 * P0 -- client OTP login mints a fresh JWT directly (no AuthenticationManager
 * involved, since there is no password here for DaoAuthenticationProvider to
 * check), so it never got the account-status check employee password login
 * gets for free. A disabled client account must not be able to obtain a new
 * valid session just by re-verifying OTP.
 */
class AuthenticationServiceClientOtpTest {

	private static final String ORG_ID = "org-1";
	private static final String PHONE = "+919999900002";

	private UserRepository userRepository;
	private ClientRepository clientRepository;
	private JwtService jwtService;
	private UserOtpRepository userOtpRepository;
	private PasswordEncoder passwordEncoder;
	private AuthenticationService service;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		clientRepository = mock(ClientRepository.class);
		EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
		DriverRepository driverRepository = mock(DriverRepository.class);
		passwordEncoder = mock(PasswordEncoder.class);
		jwtService = mock(JwtService.class);
		AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
		userOtpRepository = mock(UserOtpRepository.class);
		SMSService smsService = mock(SMSService.class);
		FileService fileService = mock(FileService.class);
		OtpRecordWriter otpRecordWriter = mock(OtpRecordWriter.class);
		FileAccessTokenService fileAccessTokenService = mock(FileAccessTokenService.class);

		service = new AuthenticationService(userRepository, clientRepository, employeeRepository, driverRepository,
				passwordEncoder, jwtService, authenticationManager, userOtpRepository, smsService, fileService,
				otpRecordWriter, fileAccessTokenService);
	}

	private UserOtp validOtpRecord() {
		UserOtp otp = new UserOtp();
		otp.setId("otp-1");
		otp.setPhone(PHONE);
		otp.setOtpHash("hashed");
		otp.setExpiresAt(Instant.now().plusSeconds(300));
		return otp;
	}

	private void stubExistingClientAndUser(boolean enabled) {
		UserOtp otp = validOtpRecord();
		when(userOtpRepository.findByPhone(PHONE)).thenReturn(otp);
		when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);

		Client client = new Client();
		client.setId("client-1");
		client.setUserId("user-1");
		when(clientRepository.findByPhoneAndOrgId(PHONE, ORG_ID)).thenReturn(client);

		User user = new User();
		user.setId("user-1");
		user.setAccountType(AccountType.CLIENT);
		user.setOrgId(ORG_ID);
		user.setEnabled(enabled);
		when(userRepository.findByPhoneAndOrgIdAndAccountType(PHONE, ORG_ID, AccountType.CLIENT))
				.thenReturn(java.util.Optional.of(user));
	}

	@Test
	void verifyOtp_enabledAccount_succeeds() {
		stubExistingClientAndUser(true);
		when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

		LoginResponse response = service.verifyOtp(new ClientOtpVerifyRequest(PHONE, ORG_ID, "123456"));

		assertEquals("jwt-token", response.getToken());
	}

	@Test
	void verifyOtp_disabledAccount_rejected() {
		stubExistingClientAndUser(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.verifyOtp(new ClientOtpVerifyRequest(PHONE, ORG_ID, "123456")));

		assertEquals(ErrorCode.ACCOUNT_DISABLED, ex.getErrorCode());
		verify(jwtService, never()).generateToken(any(User.class));
	}
}
