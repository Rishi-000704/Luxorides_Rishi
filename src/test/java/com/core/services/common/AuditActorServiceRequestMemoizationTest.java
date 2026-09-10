package com.core.services.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.core.dtos.auth.AuditActorDTO;
import com.core.models.Client;
import com.core.models.Driver;
import com.core.models.Employee;
import com.core.models.User;
import com.core.models.embedded.Name;
import com.core.models.enums.AccountType;
import com.core.repositories.ClientRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.EmployeeRepository;
import com.core.repositories.UserRepository;

/*
 * Phase B -- AuditActorService.resolve() is called once per createdBy/
 * updatedBy on every row of a response tree (BookingAssembler alone touches
 * it for the booking plus every nested client/entry/payment/driver/vehicle),
 * repeatedly hitting the DB for the same handful of actors within a single
 * request. These prove: memoization actually collapses the repeated DB work
 * within one request, never collides across different users or leaks across
 * requests, and produces byte-identical DTOs to the unmemoized path.
 */
class AuditActorServiceRequestMemoizationTest {

	private UserRepository userRepo;
	private EmployeeRepository employeeRepo;
	private ClientRepository clientRepo;
	private DriverRepository driverRepo;
	private AuditActorService service;

	@BeforeEach
	void setUp() {
		userRepo = mock(UserRepository.class);
		employeeRepo = mock(EmployeeRepository.class);
		clientRepo = mock(ClientRepository.class);
		driverRepo = mock(DriverRepository.class);
		service = new AuditActorService(userRepo, employeeRepo, clientRepo, driverRepo);

		bindNewRequest();
	}

	@AfterEach
	void tearDown() {
		RequestContextHolder.resetRequestAttributes();
	}

	private void bindNewRequest() {
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
	}

	private User employeeUser(String userId) {
		User user = new User();
		user.setId(userId);
		user.setAccountType(AccountType.EMPLOYEE);
		return user;
	}

	private Employee employee(String userId, String displayFirstName) {
		Employee e = new Employee();
		e.setUserId(userId);
		e.setName(new Name("Mr.", displayFirstName, "Test"));
		return e;
	}

	@Test
	void firstResolution_performsTheUnderlyingLookup() {
		when(userRepo.findById("u1")).thenReturn(Optional.of(employeeUser("u1")));
		when(employeeRepo.findByUserId("u1")).thenReturn(employee("u1", "Asha"));

		AuditActorDTO result = service.resolve("u1");

		assertNotNull(result);
		assertEquals("u1", result.userId());
		assertEquals("EMPLOYEE", result.accountType());
		verify(userRepo, times(1)).findById("u1");
		verify(employeeRepo, times(1)).findByUserId("u1");
	}

	@Test
	void repeatedSameUserResolution_withinOneRequest_reusesTheResult() {
		when(userRepo.findById("u1")).thenReturn(Optional.of(employeeUser("u1")));
		when(employeeRepo.findByUserId("u1")).thenReturn(employee("u1", "Asha"));

		AuditActorDTO first = service.resolve("u1");
		AuditActorDTO second = service.resolve("u1");
		AuditActorDTO third = service.resolve("u1");

		assertEquals(first, second);
		assertEquals(first, third);
		// Exactly one underlying lookup for three calls -- the other two were memo hits.
		verify(userRepo, times(1)).findById("u1");
		verify(employeeRepo, times(1)).findByUserId("u1");
	}

	@Test
	void differentUsers_doNotCollide() {
		when(userRepo.findById("u1")).thenReturn(Optional.of(employeeUser("u1")));
		when(employeeRepo.findByUserId("u1")).thenReturn(employee("u1", "Asha"));

		User clientUser = new User();
		clientUser.setId("u2");
		clientUser.setAccountType(AccountType.CLIENT);
		when(userRepo.findById("u2")).thenReturn(Optional.of(clientUser));
		Client client = new Client();
		client.setUserId("u2");
		client.setName(new Name("Ms.", "Priya", "Test"));
		when(clientRepo.findByUserId("u2")).thenReturn(client);

		AuditActorDTO first = service.resolve("u1");
		AuditActorDTO second = service.resolve("u2");

		assertEquals("u1", first.userId());
		assertEquals("EMPLOYEE", first.accountType());
		assertEquals("u2", second.userId());
		assertEquals("CLIENT", second.accountType());
		verify(userRepo, times(1)).findById("u1");
		verify(userRepo, times(1)).findById("u2");
	}

	@Test
	void differentRequests_doNotShareMemoizedState() {
		when(userRepo.findById("u1")).thenReturn(Optional.of(employeeUser("u1")));
		when(employeeRepo.findByUserId("u1")).thenReturn(employee("u1", "Asha"));

		// Request A
		service.resolve("u1");
		service.resolve("u1");
		verify(userRepo, times(1)).findById("u1");

		// Request A ends, Request B begins on a fresh request context.
		RequestContextHolder.resetRequestAttributes();
		bindNewRequest();

		service.resolve("u1");

		// A fresh request must not see Request A's memo -- the DB is hit again.
		verify(userRepo, times(2)).findById("u1");
	}

	@Test
	void organizationIsolation_differentOrgActors_resolveIndependentlyWithoutCrossContamination() {
		// userId is globally unique across orgs in this schema -- these two
		// belong to different (fictitious) orgs "org-A"/"org-B", proving the
		// memo (keyed purely on userId, the same key space resolve() already
		// used before Phase B) cannot merge or leak data between them.
		User orgAUser = employeeUser("org-a-u1");
		when(userRepo.findById("org-a-u1")).thenReturn(Optional.of(orgAUser));
		Employee orgAEmployee = employee("org-a-u1", "Asha");
		when(employeeRepo.findByUserId("org-a-u1")).thenReturn(orgAEmployee);

		User orgBUser = new User();
		orgBUser.setId("org-b-u1");
		orgBUser.setAccountType(AccountType.DRIVER);
		when(userRepo.findById("org-b-u1")).thenReturn(Optional.of(orgBUser));
		Driver orgBDriver = new Driver();
		orgBDriver.setUserId("org-b-u1");
		orgBDriver.setName(new Name("Mr.", "Rakesh", "Test"));
		when(driverRepo.findByUserId("org-b-u1")).thenReturn(Optional.of(orgBDriver));

		AuditActorDTO orgAActor = service.resolve("org-a-u1");
		AuditActorDTO orgBActor = service.resolve("org-b-u1");
		// Re-resolve org A's actor after org B's lookup -- must still be org A's own data.
		AuditActorDTO orgAActorAgain = service.resolve("org-a-u1");

		assertEquals("Mr. Asha Test", orgAActor.displayName());
		assertEquals("Mr. Rakesh Test", orgBActor.displayName());
		assertEquals(orgAActor, orgAActorAgain);
		verify(userRepo, times(1)).findById("org-a-u1");
		verify(userRepo, times(1)).findById("org-b-u1");
	}

	@Test
	void existingBehavior_systemAndNullActor_unchanged_neverMemoizedOrQueried() {
		AuditActorDTO systemFromNull = service.resolve(null);
		AuditActorDTO systemFromSentinel = service.resolve("SYSTEM");

		assertEquals(new AuditActorDTO("SYSTEM", "System", "SYSTEM"), systemFromNull);
		assertEquals(new AuditActorDTO("SYSTEM", "System", "SYSTEM"), systemFromSentinel);
		verify(userRepo, never()).findById(eq("SYSTEM"));
	}

	@Test
	void existingBehavior_unknownDriverAccount_stillFallsBackToUnknownDriverDisplayName() {
		User driverUser = new User();
		driverUser.setId("u3");
		driverUser.setAccountType(AccountType.DRIVER);
		when(userRepo.findById("u3")).thenReturn(Optional.of(driverUser));
		when(driverRepo.findByUserId("u3")).thenReturn(Optional.empty());

		AuditActorDTO result = service.resolve("u3");

		assertEquals("Unknown Driver", result.displayName());
		assertEquals("DRIVER", result.accountType());
	}

	@Test
	void noRequestBound_fallsBackToUnmemoizedResolution_insteadOfThrowing() {
		RequestContextHolder.resetRequestAttributes();

		when(userRepo.findById("u1")).thenReturn(Optional.of(employeeUser("u1")));
		when(employeeRepo.findByUserId("u1")).thenReturn(employee("u1", "Asha"));

		AuditActorDTO first = service.resolve("u1");
		AuditActorDTO second = service.resolve("u1");

		assertEquals(first, second);
		// No request bound -- memoization is a no-op, not an error; every call hits the DB.
		verify(userRepo, times(2)).findById("u1");
	}
}
