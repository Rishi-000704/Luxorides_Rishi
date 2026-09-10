package com.core.services.common;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.core.dtos.auth.AuditActorDTO;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Client;
import com.core.models.Driver;
import com.core.models.Employee;
import com.core.models.User;
import com.core.repositories.ClientRepository;
import com.core.repositories.DriverRepository;
import com.core.repositories.EmployeeRepository;
import com.core.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditActorService {

	/*
	 * Phase B -- request-scoped memoization key. BookingAssembler alone calls
	 * resolve(x.getCreatedBy())/resolve(x.getUpdatedBy()) for the booking
	 * itself plus every nested client/entry/payment/driver/vehicle on a
	 * single response (ClientAssembler, VehicleAssembler, PackageAssembler,
	 * DriverAssembler, PurchaseInvoiceAssembler do the same for their own
	 * trees), and the same handful of employees/clients/drivers created or
	 * last touched most of the rows on any one page -- so the identical
	 * (userId -> ActorInfo) DB lookup repeats many times per request.
	 */
	private static final String MEMO_ATTRIBUTE = AuditActorService.class.getName() + ".MEMO";

	private final UserRepository userRepo;
	private final EmployeeRepository employeeRepo;
	private final ClientRepository clientRepo;
	private final DriverRepository driverRepo;

	@Transactional(readOnly = true)
	public AuditActorDTO resolve(String userId) {
		if (userId == null || userId.equals("SYSTEM")) {
			return new AuditActorDTO("SYSTEM", "System", "SYSTEM");
		}

		Map<String, AuditActorDTO> memo = currentRequestMemo();
		if (memo != null) {
			AuditActorDTO cached = memo.get(userId);
			if (cached != null) {
				return cached;
			}
		}

		AuditActorDTO resolved = resolveWithoutMemo(userId);

		if (memo != null) {
			memo.put(userId, resolved);
		}

		return resolved;
	}

	private AuditActorDTO resolveWithoutMemo(String userId) {
		User user = userRepo.findById(userId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found."));

		return switch (user.getAccountType()) {
		case EMPLOYEE -> {
			Employee e = employeeRepo.findByUserId(userId);
			yield new AuditActorDTO(userId, e != null ? e.getName().getDisplayName() : "Unknown Employee", "EMPLOYEE");
		}
		case CLIENT -> {
			Client c = clientRepo.findByUserId(userId);
			yield new AuditActorDTO(userId, c != null ? c.getName().getDisplayName() : "Unknown Client", "CLIENT");
		}
		case DRIVER -> {
			Driver d = driverRepo.findByUserId(userId).orElse(null);
			yield new AuditActorDTO(userId, d != null ? d.getName().getDisplayName() : "Unknown Driver", "DRIVER");
		}
		};
	}

	/*
	 * Request-scoped ONLY -- deliberately never a field on this singleton
	 * bean (a field here would be a global, cross-user cache, exactly what
	 * Phase B forbids). The memo map lives as an attribute on the current
	 * HttpServletRequest via Spring's RequestContextHolder -- the same kind
	 * of per-request-then-discarded state RequestTraceFilter already keeps
	 * for its trace id, just using Spring MVC's own request-attribute bag
	 * instead of a dedicated Filter/ThreadLocal, so there is nothing here to
	 * explicitly clear: the servlet container discards the request's
	 * attributes (this map included) the moment the request completes, and
	 * the next request gets a fresh RequestAttributes with nothing in it.
	 * One worker thread handles one request start-to-finish in this app (no
	 * @Async/@Scheduled path calls into AuditActorService), so a plain
	 * HashMap needs no synchronization -- and RequestContextHolder's
	 * ThreadLocal is not inherited by any child thread, so even a
	 * hypothetical future parallel-worker path within a request would see no
	 * bound request here rather than another thread's half-built map.
	 * Falls back to unmemoized resolution (never throws) when no request is
	 * bound to the current thread at all.
	 */
	@SuppressWarnings("unchecked")
	private Map<String, AuditActorDTO> currentRequestMemo() {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		if (attrs == null) {
			return null;
		}

		Object existing = attrs.getAttribute(MEMO_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		if (existing != null) {
			return (Map<String, AuditActorDTO>) existing;
		}

		Map<String, AuditActorDTO> memo = new HashMap<>();
		attrs.setAttribute(MEMO_ATTRIBUTE, memo, RequestAttributes.SCOPE_REQUEST);
		return memo;
	}
}
