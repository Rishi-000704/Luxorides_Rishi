package com.core.services.common;

import java.io.IOException;
import java.security.SecureRandom;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

import com.core.config.OtpAuthenticationToken;
import com.core.dtos.auth.ClientOtpRequest;
import com.core.dtos.auth.ClientOtpResponse;
import com.core.dtos.auth.ClientOtpVerifyRequest;
import com.core.dtos.auth.DriverOtpRequest;
import com.core.dtos.auth.DriverOtpResponse;
import com.core.dtos.auth.DriverOtpVerifyRequest;
import com.core.dtos.auth.EmployeeLoginRequest;
import com.core.dtos.auth.EmployeeMeResponse;
import com.core.dtos.auth.LoginResponse;
import com.core.dtos.config.EmployeeRequest;
import com.core.dtos.config.UpdatePasswordRequest;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Client;
import com.core.models.Driver;
import com.core.models.Employee;
import com.core.models.User;
import com.core.models.UserOtp;
import com.core.models.enums.AccountType;
import com.core.models.enums.Authority;
import com.core.models.enums.FileAccessCategory;
import com.core.repositories.ClientRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.EmployeeRepository;
import com.core.repositories.UserOtpRepository;
import com.core.repositories.UserRepository;
import com.core.services.JwtService;
import com.core.util.AddressUtil;
import com.core.util.PhoneNumberNormalizer;
import com.core.dtos.config.EmployeeListItem;

@Slf4j
@Service
public class AuthenticationService {

	private final UserRepository userRepository;
	private final ClientRepository clientRepository;
	private final EmployeeRepository employeeRepository;
	private final DriverRepository driverRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;
	private final UserOtpRepository userOtpRepository;
	private final SMSService smsService;
	private final FileService fileService;
	private final OtpRecordWriter otpRecordWriter;
	private final FileAccessTokenService fileAccessTokenService;

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final int OTP_WRITE_MAX_ATTEMPTS = 3;

	/*
	 * P1.8 -- neither /auth/client/generate-otp nor /auth/driver/generate-otp
	 * had any throttle: issueOtp unconditionally sent a fresh SMS on every
	 * call, so hammering either endpoint with the same phone number triggered
	 * unlimited real OTP SMS sends (cost abuse / SMS-bombing risk against any
	 * phone number, since the client-side flow requires no pre-existing
	 * account). This is a minimum interval between two OTPs for the same
	 * phone, not a request-count limiter -- narrow and self-contained, unlike
	 * app-wide rate limiting, which is a larger, separately-scoped concern.
	 */
	private static final long OTP_RESEND_COOLDOWN_SECONDS = 30;

	public AuthenticationService(UserRepository userRepository, ClientRepository clientRepository,
			EmployeeRepository employeeRepository, DriverRepository driverRepository,
			PasswordEncoder passwordEncoder, JwtService jwtService,
			AuthenticationManager authenticationManager, UserOtpRepository userOtpRepository, SMSService smsService,
			FileService fileService, OtpRecordWriter otpRecordWriter, FileAccessTokenService fileAccessTokenService) {
		super();
		this.userRepository = userRepository;
		this.clientRepository = clientRepository;
		this.employeeRepository = employeeRepository;
		this.driverRepository = driverRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.authenticationManager = authenticationManager;
		this.userOtpRepository = userOtpRepository;
		this.smsService = smsService;
		this.fileService = fileService;
		this.otpRecordWriter = otpRecordWriter;
		this.fileAccessTokenService = fileAccessTokenService;
	}

	@Transactional
	public ClientOtpResponse generateOtp(ClientOtpRequest request) {
		issueOtp(request.orgId(), request.mobileNumber());
		return new ClientOtpResponse(true, "OTP sent and valid for 10 min.", 600);
	}

	/*
	 * Driver OTP login. Unlike the client flow, a Driver record must already
	 * exist (created by ops staff via DriverController) — there is no
	 * driver self-signup. This keeps drivers a company-managed record, matching
	 * how DriverController/DriverService already work.
	 */
	@Transactional
	public DriverOtpResponse generateDriverOtp(DriverOtpRequest request) {
		if (!hasText(request.orgId())) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Organization is required to send OTP.");
		}

		Driver driver = this.driverRepository.findByPhoneAndOrgId(request.mobileNumber(), request.orgId());
		if (driver == null) {
			throw new NotFoundException(ErrorCode.DRIVER_NOT_FOUND,
					"No driver is registered with this mobile number. Contact your dispatcher.");
		}

		issueOtp(request.orgId(), request.mobileNumber());
		return new DriverOtpResponse(true, "OTP sent and valid for 10 min.", 600);
	}

	/*
	 * Shared OTP-issuance step behind both generateOtp (client) and
	 * generateDriverOtp — same UserOtp table/SMS provider, just reused instead
	 * of re-implemented per account type.
	 */
	private String issueOtp(String orgId, String mobileNumber) {
		enforceOtpResendCooldown(mobileNumber);

		String otp = generate6DigitOtp();
		UserOtp record = new UserOtp();
		record.setPhone(mobileNumber);
		record.setOtpHash(this.passwordEncoder.encode(otp));
		writeOtpRecordWithRetry(record);

		if (!hasText(orgId)) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Organization is required to send OTP.");
		}

		boolean sent = this.smsService.sendOtp(orgId, mobileNumber, otp, "10");

		if (!sent) {
			this.otpRecordWriter.delete(record);
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Unable to send OTP. SMS provider is not configured for this organization."
			);
		}

		return otp;
	}

	/*
	 * Read-only lookup, not followed by any entity-based delete -- distinct
	 * from the find-then-delete(entity) race that OtpRecordWriter.replace()
	 * (a bulk deleteByPhone() + save()) already exists to avoid. A prior OTP
	 * record still within the cooldown window is left untouched here; the
	 * normal issuance flow below still clears and replaces it atomically once
	 * the cooldown has passed.
	 */
	private void enforceOtpResendCooldown(String mobileNumber) {
		String normalizedPhone = PhoneNumberNormalizer.normalize(mobileNumber);
		UserOtp existing = this.userOtpRepository.findByPhone(normalizedPhone);

		if (existing == null || existing.getCreatedAt() == null) {
			return;
		}

		long secondsSinceIssue = Duration.between(existing.getCreatedAt(), Instant.now()).getSeconds();

		if (secondsSinceIssue < OTP_RESEND_COOLDOWN_SECONDS) {
			long secondsRemaining = OTP_RESEND_COOLDOWN_SECONDS - secondsSinceIssue;
			throw new BusinessException(
					ErrorCode.OTP_COOLDOWN_ACTIVE,
					"Please wait " + secondsRemaining + " seconds before requesting another OTP."
			);
		}
	}

	/*
	 * The delete-then-insert in OtpRecordWriter.replace() can deadlock under
	 * InnoDB when two requests race for the same phone number (e.g. a
	 * double-tapped "Send OTP" button, or a legitimate resend racing the
	 * original request) -- each REQUIRES_NEW attempt is its own fresh
	 * transaction, so retrying here is safe and doesn't reuse a
	 * transaction that MySQL already aborted.
	 */
	private void writeOtpRecordWithRetry(UserOtp record) {
		for (int attempt = 1; ; attempt++) {
			try {
				this.otpRecordWriter.replace(record);
				return;
			} catch (PessimisticLockingFailureException deadlock) {
				if (attempt >= OTP_WRITE_MAX_ATTEMPTS) {
					throw deadlock;
				}
				log.warn("OTP write for phone={} hit a DB lock conflict (attempt {}/{}), retrying: {}",
						record.getPhone(), attempt, OTP_WRITE_MAX_ATTEMPTS, deadlock.getMessage());
				try {
					Thread.sleep(50L * attempt);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw deadlock;
				}
			}
		}
	}

	@Transactional
	@SuppressWarnings("null")
	public LoginResponse verifyOtp(ClientOtpVerifyRequest request) {
		UserOtp record = this.userOtpRepository.findByPhone(request.mobileNumber());
		if (record == null) {
			throw new BusinessException(ErrorCode.OTP_NOT_INITIATED, "No OTP request found for this number");
		} else if (record.isExpired()) {
			throw new BusinessException(ErrorCode.OTP_EXPIRED, "OTP has expired");
		} else if (!this.passwordEncoder.matches(request.otp(), record.getOtpHash())) {
			record.setAttemptCount(record.getAttemptCount() + 1);
			if (record.getAttemptCount() > 3) {
				this.userOtpRepository.deleteById(record.getId());
				throw new BusinessException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED, "Maximum OTP attempts exceeded",
						Map.of("maxAttempts", 3));
			}
			this.userOtpRepository.save(record);
			throw new BusinessException(ErrorCode.OTP_INVALID, "Invalid OTP",
					Map.of("attempt", record.getAttemptCount()));
		} else {
			userOtpRepository.deleteById(record.getId());
			Client localClient = this.clientRepository.findByPhoneAndOrgId(request.mobileNumber(), request.orgId());
			User localUser = this.userRepository
					.findByPhoneAndOrgIdAndAccountType(request.mobileNumber(), request.orgId(), AccountType.CLIENT)
					.orElse(null);
			if (localClient == null && localUser == null) {
				localUser = this.saveUser(request);
				localClient = this.saveClient(request, localUser.getId());
			} else if (localClient == null && localUser != null) {
				localClient = this.saveClient(request, localUser.getId());
			} else if (localClient != null && localUser == null) {
				localUser = this.saveUser(request);
				localClient.setUserId(localUser.getId());
				this.clientRepository.save(localClient);
			}

			OtpAuthenticationToken authToken = new OtpAuthenticationToken(localUser);

			SecurityContextHolder.getContext().setAuthentication(authToken);

			LoginResponse response = new LoginResponse();
			response.setToken(this.jwtService.generateToken(localUser));
			return response;
		}

	}

	@Transactional(readOnly = true)
	public Client clientme(Authentication authentication) {
		User user = (User) authentication.getPrincipal();
		return this.clientRepository.findByPhoneAndOrgId(user.getPhone(), user.getOrgId());
	}

	@Transactional
	@SuppressWarnings("null")
	public LoginResponse verifyDriverOtp(DriverOtpVerifyRequest request) {
		UserOtp record = this.userOtpRepository.findByPhone(request.mobileNumber());
		if (record == null) {
			throw new BusinessException(ErrorCode.OTP_NOT_INITIATED, "No OTP request found for this number");
		} else if (record.isExpired()) {
			throw new BusinessException(ErrorCode.OTP_EXPIRED, "OTP has expired");
		} else if (!this.passwordEncoder.matches(request.otp(), record.getOtpHash())) {
			record.setAttemptCount(record.getAttemptCount() + 1);
			if (record.getAttemptCount() > 3) {
				this.userOtpRepository.deleteById(record.getId());
				throw new BusinessException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED, "Maximum OTP attempts exceeded",
						Map.of("maxAttempts", 3));
			}
			this.userOtpRepository.save(record);
			throw new BusinessException(ErrorCode.OTP_INVALID, "Invalid OTP",
					Map.of("attempt", record.getAttemptCount()));
		}

		this.userOtpRepository.deleteById(record.getId());

		Driver driver = this.driverRepository.findByPhoneAndOrgId(request.mobileNumber(), request.orgId());
		if (driver == null) {
			throw new NotFoundException(ErrorCode.DRIVER_NOT_FOUND,
					"No driver is registered with this mobile number. Contact your dispatcher.");
		}

		User user = this.userRepository
				.findByPhoneAndOrgIdAndAccountType(request.mobileNumber(), request.orgId(), AccountType.DRIVER)
				.orElse(null);
		if (user == null) {
			user = new User();
			user.setAccountType(AccountType.DRIVER);
			user.setOrgId(request.orgId());
			user.setPhone(request.mobileNumber());
			user = this.userRepository.save(user);
		}

		if (driver.getUserId() == null) {
			driver.setUserId(user.getId());
			this.driverRepository.save(driver);
		}

		OtpAuthenticationToken authToken = new OtpAuthenticationToken(user);
		SecurityContextHolder.getContext().setAuthentication(authToken);

		LoginResponse response = new LoginResponse();
		response.setToken(this.jwtService.generateToken(user));
		return response;
	}

	@Transactional(readOnly = true)
	public Driver driverme(Authentication authentication) {
		if (authentication == null) {
			throw new AuthenticationCredentialsNotFoundException("Authentication required");
		}
		User user = (User) authentication.getPrincipal();
		return this.driverRepository.findByUserId(user.getId())
				.orElseThrow(() -> new NotFoundException(ErrorCode.DRIVER_NOT_FOUND,
						"Driver record not found for this account"));
	}

	@Transactional(readOnly = true)
	public LoginResponse authenticateEmployee(EmployeeLoginRequest request) {
		Employee emp = this.employeeRepository.findByEmail(request.username());
		if (emp == null) {
			emp = this.employeeRepository.findByPhone(request.username());
			if (emp == null) {
				throw new NotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found");
			}
		}
		User user = this.getUser(emp.getOrgId(), emp.getUserId());
		if (this.passwordEncoder.matches(request.password(), user.getPassword())) {
			UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(user.getId(),
					request.password());
			authenticationManager.authenticate(token);
			LoginResponse response = new LoginResponse();
			response.setToken(this.jwtService.generateToken(user));
			return response;
		} else {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password");
		}
	}

	@Transactional(readOnly = true)
	public EmployeeMeResponse employeeme(Authentication authentication) {
		if (authentication != null) {
			User user = (User) authentication.getPrincipal();
			Employee employee = this.employeeRepository.findByUserId(user.getId());
			return new EmployeeMeResponse(
					employee.getId(),
					employee.getOrgId(),
					employee.getUserId(),
					employee.getName(),
					employee.getEmail(),
					employee.getPhone(),
					employee.getAddress(),
					fileAccessTokenService.toAccessUrl(employee.getPic(), employee.getOrgId(), FileAccessCategory.PRIVATE),
					user.getAuthorityList(),
					employee.getCreatedAt(),
					employee.getUpdatedAt(),
					employee.getCreatedBy(),
					employee.getUpdatedBy()
			);
		} else {
			throw new AuthenticationCredentialsNotFoundException("Authentication required");
		}
	}

	@Transactional
	public Employee updatePassword(String orgId, String userId, UpdatePasswordRequest request) {
		User user = this.getUser(orgId, userId);
		if (this.passwordEncoder.matches(request.oldPassword(), user.getPassword())
				&& request.oldPassword().equals(request.verifyOldPassword()) && (request.newPassword() != null)) {
			return this.resetPassword(orgId, userId, request.newPassword());
		} else {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Old password is incorrect");
		}
	}

	@Transactional
	public User updateUserEnabled(String orgId, String userId, Boolean enabled) {
		if (enabled == null) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Enabled status is required.");
		}

		User user = this.getUser(orgId, userId);
		user.setEnabled(enabled);

		return this.userRepository.save(user);
	}

	@Transactional
	public Employee resetPassword(String orgId, String userId, String newPassword) {
		User user = this.getUser(orgId, userId);
		user.setPassword(this.passwordEncoder.encode(newPassword));
		this.userRepository.save(user);
		return this.employeeRepository.findByUserId(user.getId());
	}

	@Transactional
	public Employee saveUserEmployee(String orgId, EmployeeRequest request) {
		Employee local = this.employeeRepository.findByPhone(request.phone());
		if (local != null) {
			throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "Mobile number already registered.");
		}
		local = this.employeeRepository.findByEmail(request.email());
		if (local != null) {
			throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "Email already registered.");
		}

		User user = new User();
		user.setAccountType(AccountType.EMPLOYEE);
		user.setEmail(request.email());
		user.setEnabled(true);
		user.setOrgId(orgId);
		user.setPhone(request.phone());

		user = this.userRepository.save(user);

		Employee emp = new Employee();
		emp.setAddress(AddressUtil.toDisplayAddress(request.address()));
		emp.setEmail(request.email());
		emp.setName(request.name());
		emp.setOrgId(orgId);
		emp.setPhone(request.phone());
		emp.setUserId(user.getId());
		emp.setPic(null);

		return this.employeeRepository.save(emp);
	}

	@Transactional
	public Employee updateUserEmployee(String orgId, EmployeeRequest request) {
		User user = this.userRepository.findByPhoneAndOrgIdAndAccountType(request.phone(), orgId, AccountType.EMPLOYEE)
				.orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found."));

		user.setEmail(request.email());
		user.setPhone(request.phone());

		this.userRepository.save(user);

		Employee emp = this.employeeRepository.findByEmailAndOrgId(request.email(), orgId);
		if (emp == null) {
			emp = this.employeeRepository.findByPhoneAndOrgId(request.phone(), orgId);
			if (emp == null) {
				throw new NotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found");
			}
		}
		emp.setAddress(AddressUtil.toDisplayAddress(request.address()));
		emp.setEmail(request.email());
		emp.setName(request.name());
		emp.setPhone(request.phone());

		return this.employeeRepository.save(emp);
	}

	@Transactional
	public void updateAuthorities(String orgId, String userId, List<Authority> authorities) {
		User user = this.getUser(orgId, userId);
		user.setAuthorities(authorities);
		this.userRepository.save(user);
	}

	@Transactional
	public Employee updatePic(String userId, String orgId, MultipartFile file) throws IOException {
		Employee emp = this.employeeRepository.findByUserId(userId);
		if (emp.getPic() != null) {
			this.fileService.deleteFile(emp.getPic());
		}
		emp.setPic(this.fileService.saveDisplayImage(file));
		return this.employeeRepository.save(emp);
	}

	@Transactional(readOnly = true)
	public List<EmployeeListItem> getEmployeeList(String orgId) {
		List<Employee> employees = this.employeeRepository.findByOrgId(orgId);

		/*
		 * P1.4 -- previously called userRepository.findByOrgIdAndId once per
		 * employee (1 + N queries). Batched into a single IN query, matching
		 * the same org-scoping and the same null-safe fallback below for an
		 * employee whose user record isn't found.
		 */
		List<String> userIds = employees.stream()
				.map(Employee::getUserId)
				.filter(java.util.Objects::nonNull)
				.distinct()
				.toList();

		Map<String, User> usersByUserId = userIds.isEmpty()
				? Map.of()
				: this.userRepository.findByOrgIdAndIdIn(orgId, userIds).stream()
						.collect(Collectors.toMap(User::getId, Function.identity()));

		return employees.stream()
				.map(employee -> {
					User user = usersByUserId.get(employee.getUserId());

					return new EmployeeListItem(
							employee.getId(),
							employee.getOrgId(),
							employee.getUserId(),

							employee.getName(),
							employee.getEmail(),
							employee.getPhone(),
							employee.getAddress(),
							fileAccessTokenService.toAccessUrl(employee.getPic(), employee.getOrgId(), FileAccessCategory.PRIVATE),

							user != null ? user.isEnabled() : Boolean.FALSE,
							user != null ? user.getAuthorityList() : List.of()
					);
				})
				.toList();
	}

	@Transactional(readOnly = true)
	public User getUser(String orgId, String id) {
		return this.userRepository.findByOrgIdAndId(orgId, id)
				.orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found."));
	}

	private static String generate6DigitOtp() {
		return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
	}

	private User saveUser(ClientOtpVerifyRequest request) {
		User user = new User();
		user.setAccountType(AccountType.CLIENT);
		user.setOrgId(request.orgId());
		user.setPhone(request.mobileNumber());
		return this.userRepository.save(user);
	}

	private Client saveClient(ClientOtpVerifyRequest request, String userId) {
		Client client = new Client();
		client.setPhone(request.mobileNumber());
		client.setOrgId(request.orgId());
		client.setUserId(userId);
		return this.clientRepository.save(client);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
