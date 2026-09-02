package com.core.services.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import com.core.models.Employee;
import com.core.repositories.ClientRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.EmployeeRepository;
import com.core.repositories.UserOtpRepository;
import com.core.repositories.UserRepository;
import com.core.services.JwtService;

/*
 * P1.6 -- Employee.pic is an ordinary display avatar (not KYC/evidence), so
 * updatePic now goes through FileService.saveDisplayImage instead of
 * saveFile. The pre-existing delete-old-file-before-save behavior is
 * unchanged.
 */
class AuthenticationServiceUpdatePicTest {

	private static final String ORG_ID = "org-1";
	private static final String USER_ID = "user-1";

	private EmployeeRepository employeeRepository;
	private FileService fileService;
	private AuthenticationService service;

	@BeforeEach
	void setUp() {
		UserRepository userRepository = mock(UserRepository.class);
		ClientRepository clientRepository = mock(ClientRepository.class);
		employeeRepository = mock(EmployeeRepository.class);
		DriverRepository driverRepository = mock(DriverRepository.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		JwtService jwtService = mock(JwtService.class);
		AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
		UserOtpRepository userOtpRepository = mock(UserOtpRepository.class);
		SMSService smsService = mock(SMSService.class);
		fileService = mock(FileService.class);
		OtpRecordWriter otpRecordWriter = mock(OtpRecordWriter.class);
		FileAccessTokenService fileAccessTokenService = mock(FileAccessTokenService.class);

		service = new AuthenticationService(userRepository, clientRepository, employeeRepository, driverRepository,
				passwordEncoder, jwtService, authenticationManager, userOtpRepository, smsService, fileService,
				otpRecordWriter, fileAccessTokenService);
	}

	private Employee employee() {
		Employee e = new Employee();
		e.setId("emp-1");
		e.setOrgId(ORG_ID);
		e.setUserId(USER_ID);
		return e;
	}

	@Test
	void updatePic_usesSaveDisplayImage_notSaveFile() throws Exception {
		when(employeeRepository.findByUserId(USER_ID)).thenReturn(employee());
		when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
		when(fileService.saveDisplayImage(any())).thenReturn("resized.jpg");

		MultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
		Employee result = service.updatePic(USER_ID, ORG_ID, file);

		org.junit.jupiter.api.Assertions.assertEquals("resized.jpg", result.getPic());
		verify(fileService).saveDisplayImage(file);
		verify(fileService, never()).saveFile(any());
	}

	@Test
	void updatePic_deletesThePreviousPic_beforeSavingTheNewOne() throws Exception {
		Employee existing = employee();
		existing.setPic("old-avatar.jpg");
		when(employeeRepository.findByUserId(USER_ID)).thenReturn(existing);
		when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
		when(fileService.saveDisplayImage(any())).thenReturn("new-avatar.jpg");

		MultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
		service.updatePic(USER_ID, ORG_ID, file);

		verify(fileService).deleteFile("old-avatar.jpg");
	}
}
