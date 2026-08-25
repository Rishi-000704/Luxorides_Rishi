package com.core.services.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.core.dtos.auth.DriverOtpRequest;
import com.core.dtos.auth.DriverOtpVerifyRequest;
import com.core.dtos.auth.LoginResponse;
import com.core.exception.BusinessException;
import com.core.exception.NotFoundException;
import com.core.models.Driver;
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
 * Covers the driver OTP login flow added to AuthenticationService: unlike the
 * client flow, a Driver record must already exist (no self-signup), and the
 * first successful login links Driver.userId to a lazily-created User.
 */
class AuthenticationServiceDriverTest {

	private UserRepository userRepository;
	private DriverRepository driverRepository;
	private PasswordEncoder passwordEncoder;
	private JwtService jwtService;
	private UserOtpRepository userOtpRepository;
	private SMSService smsService;
	private OtpRecordWriter otpRecordWriter;
	private AuthenticationService service;

	private static final String ORG_ID = "org-1";
	private static final String PHONE = "+919999900001";

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		ClientRepository clientRepository = mock(ClientRepository.class);
		EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
		driverRepository = mock(DriverRepository.class);
		passwordEncoder = mock(PasswordEncoder.class);
		jwtService = mock(JwtService.class);
		AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
		userOtpRepository = mock(UserOtpRepository.class);
		smsService = mock(SMSService.class);
		FileService fileService = mock(FileService.class);
		otpRecordWriter = mock(OtpRecordWriter.class);

		service = new AuthenticationService(userRepository, clientRepository, employeeRepository, driverRepository,
				passwordEncoder, jwtService, authenticationManager, userOtpRepository, smsService, fileService,
				otpRecordWriter);
	}

	@Test
	void generateDriverOtp_rejectsUnknownPhone_noSelfSignup() {
		when(driverRepository.findByPhoneAndOrgId(PHONE, ORG_ID)).thenReturn(null);

		assertThrows(NotFoundException.class,
				() -> service.generateDriverOtp(new DriverOtpRequest(PHONE, ORG_ID)));

		verify(smsService, never()).sendOtp(anyString(), anyString(), anyString(), anyString());
		verify(userOtpRepository, never()).save(any());
	}

	@Test
	void generateDriverOtp_sendsOtp_forRegisteredDriver() {
		Driver driver = new Driver();
		driver.setPhone(PHONE);
		driver.setOrgId(ORG_ID);
		when(driverRepository.findByPhoneAndOrgId(PHONE, ORG_ID)).thenReturn(driver);
		when(passwordEncoder.encode(anyString())).thenReturn("hashed");
		when(smsService.sendOtp(eq(ORG_ID), eq(PHONE), anyString(), anyString())).thenReturn(true);

		var response = service.generateDriverOtp(new DriverOtpRequest(PHONE, ORG_ID));

		assertEquals(true, response.success());
		verify(smsService, times(1)).sendOtp(eq(ORG_ID), eq(PHONE), anyString(), eq("10"));
	}

	/*
	 * Regression test for a real race: issueOtp used to findByPhone() then delete(entity),
	 * which throws StaleObjectStateException if a concurrent request for the same phone
	 * (e.g. a double-tapped "Send OTP") already deleted that row. The write now goes
	 * through OtpRecordWriter.replace() (a bulk deleteByPhone() + save() in its own
	 * REQUIRES_NEW transaction) -- AuthenticationService itself must never touch
	 * userOtpRepository's delete/find directly for the issuance path.
	 */
	@Test
	void generateDriverOtp_clearsAnyPriorOtpAtomically_notFindThenDelete() {
		Driver driver = new Driver();
		driver.setPhone(PHONE);
		driver.setOrgId(ORG_ID);
		when(driverRepository.findByPhoneAndOrgId(PHONE, ORG_ID)).thenReturn(driver);
		when(passwordEncoder.encode(anyString())).thenReturn("hashed");
		when(smsService.sendOtp(eq(ORG_ID), eq(PHONE), anyString(), anyString())).thenReturn(true);

		service.generateDriverOtp(new DriverOtpRequest(PHONE, ORG_ID));

		verify(otpRecordWriter, times(1)).replace(argThat(record -> PHONE.equals(record.getPhone())));
		verify(userOtpRepository, never()).deleteByPhone(anyString());
		verify(userOtpRepository, never()).findByPhone(PHONE);
		verify(userOtpRepository, never()).delete(any());
	}

	/*
	 * Regression test for a real deadlock: two requests racing to issue an OTP for the
	 * same phone (e.g. a double-tapped "Send OTP") can deadlock InnoDB on the
	 * delete-then-insert in OtpRecordWriter.replace(). Each retry must go through a
	 * fresh call (and therefore a fresh REQUIRES_NEW transaction) rather than reusing
	 * the transaction MySQL already aborted -- this only proves the retry happens; the
	 * fresh-transaction guarantee itself is covered by the live concurrent test against
	 * a real MySQL instance, not by this mock-based test.
	 */
	@Test
	void generateDriverOtp_retriesOnce_whenOtpWriteHitsADeadlock_thenSucceeds() {
		Driver driver = new Driver();
		driver.setPhone(PHONE);
		driver.setOrgId(ORG_ID);
		when(driverRepository.findByPhoneAndOrgId(PHONE, ORG_ID)).thenReturn(driver);
		when(passwordEncoder.encode(anyString())).thenReturn("hashed");
		when(smsService.sendOtp(eq(ORG_ID), eq(PHONE), anyString(), anyString())).thenReturn(true);

		doThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"))
				.doNothing()
				.when(otpRecordWriter).replace(any());

		var response = service.generateDriverOtp(new DriverOtpRequest(PHONE, ORG_ID));

		assertEquals(true, response.success());
		verify(otpRecordWriter, times(2)).replace(any());
		verify(smsService, times(1)).sendOtp(eq(ORG_ID), eq(PHONE), anyString(), anyString());
	}

	@Test
	void generateDriverOtp_givesUpAndFails_afterRepeatedDeadlocks() {
		Driver driver = new Driver();
		driver.setPhone(PHONE);
		driver.setOrgId(ORG_ID);
		when(driverRepository.findByPhoneAndOrgId(PHONE, ORG_ID)).thenReturn(driver);
		when(passwordEncoder.encode(anyString())).thenReturn("hashed");

		doThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"))
				.when(otpRecordWriter).replace(any());

		assertThrows(CannotAcquireLockException.class,
				() -> service.generateDriverOtp(new DriverOtpRequest(PHONE, ORG_ID)));

		verify(otpRecordWriter, times(3)).replace(any());
		verify(smsService, never()).sendOtp(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void verifyDriverOtp_firstLogin_createsUserAndLinksDriver() {
		UserOtp otp = validOtpRecord();
		when(userOtpRepository.findByPhone(PHONE)).thenReturn(otp);
		when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);

		Driver driver = new Driver();
		driver.setId("driver-1");
		driver.setPhone(PHONE);
		driver.setOrgId(ORG_ID);
		when(driverRepository.findByPhoneAndOrgId(PHONE, ORG_ID)).thenReturn(driver);
		when(userRepository.findByPhoneAndOrgIdAndAccountType(PHONE, ORG_ID, AccountType.DRIVER))
				.thenReturn(Optional.empty());

		User savedUser = new User();
		savedUser.setId("user-1");
		savedUser.setAccountType(AccountType.DRIVER);
		savedUser.setOrgId(ORG_ID);
		savedUser.setPhone(PHONE);
		when(userRepository.save(any(User.class))).thenReturn(savedUser);
		when(jwtService.generateToken(savedUser)).thenReturn("jwt-token");

		LoginResponse response = service.verifyDriverOtp(new DriverOtpVerifyRequest(PHONE, ORG_ID, "123456"));

		assertEquals("jwt-token", response.getToken());
		assertEquals("user-1", driver.getUserId());
		verify(driverRepository).save(driver);
	}

	@Test
	void verifyDriverOtp_repeatLogin_reusesExistingUser_doesNotOverwriteLink() {
		UserOtp otp = validOtpRecord();
		when(userOtpRepository.findByPhone(PHONE)).thenReturn(otp);
		when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);

		Driver driver = new Driver();
		driver.setId("driver-1");
		driver.setPhone(PHONE);
		driver.setOrgId(ORG_ID);
		driver.setUserId("user-1"); // already linked from a prior login
		when(driverRepository.findByPhoneAndOrgId(PHONE, ORG_ID)).thenReturn(driver);

		User existingUser = new User();
		existingUser.setId("user-1");
		existingUser.setAccountType(AccountType.DRIVER);
		when(userRepository.findByPhoneAndOrgIdAndAccountType(PHONE, ORG_ID, AccountType.DRIVER))
				.thenReturn(Optional.of(existingUser));
		when(jwtService.generateToken(existingUser)).thenReturn("jwt-token-2");

		LoginResponse response = service.verifyDriverOtp(new DriverOtpVerifyRequest(PHONE, ORG_ID, "123456"));

		assertEquals("jwt-token-2", response.getToken());
		verify(userRepository, never()).save(any(User.class));
		verify(driverRepository, never()).save(any(Driver.class));
	}

	@Test
	void verifyDriverOtp_correctOtp_unknownDriver_stillRejected() {
		UserOtp otp = validOtpRecord();
		when(userOtpRepository.findByPhone(PHONE)).thenReturn(otp);
		when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);
		when(driverRepository.findByPhoneAndOrgId(PHONE, ORG_ID)).thenReturn(null);

		assertThrows(NotFoundException.class,
				() -> service.verifyDriverOtp(new DriverOtpVerifyRequest(PHONE, ORG_ID, "123456")));
	}

	@Test
	void verifyDriverOtp_wrongOtp_rejected() {
		UserOtp otp = validOtpRecord();
		when(userOtpRepository.findByPhone(PHONE)).thenReturn(otp);
		when(passwordEncoder.matches("000000", "hashed")).thenReturn(false);

		assertThrows(BusinessException.class,
				() -> service.verifyDriverOtp(new DriverOtpVerifyRequest(PHONE, ORG_ID, "000000")));

		verify(driverRepository, never()).findByPhoneAndOrgId(anyString(), anyString());
	}

	private UserOtp validOtpRecord() {
		UserOtp otp = new UserOtp();
		otp.setId("otp-1");
		otp.setPhone(PHONE);
		otp.setOtpHash("hashed");
		otp.setExpiresAt(Instant.now().plusSeconds(300));
		return otp;
	}
}
