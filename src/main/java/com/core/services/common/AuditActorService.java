package com.core.services.common;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dtos.auth.AuditActorDTO;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Client;
import com.core.models.Employee;
import com.core.models.User;
import com.core.repositories.ClientRepository;
import com.core.repositories.EmployeeRepository;
import com.core.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditActorService {

	private final UserRepository userRepo;
	private final EmployeeRepository employeeRepo;
	private final ClientRepository clientRepo;

	@Transactional(readOnly = true)
	public AuditActorDTO resolve(String userId) {
		if (userId == null || userId.equals("SYSTEM")) {
			return new AuditActorDTO("SYSTEM", "System", "SYSTEM");
		}

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
		};
	}
}
