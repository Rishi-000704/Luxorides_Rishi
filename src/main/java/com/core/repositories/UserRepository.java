package com.core.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.core.models.User;
import com.core.models.enums.AccountType;

public interface UserRepository extends JpaRepository<User, String> {

	Optional<User> findByEmailAndOrgIdAndAccountType(String email, String orgId, AccountType accountType);

	Optional<User> findByPhoneAndOrgIdAndAccountType(String phone, String orgId, AccountType accountType);
	
	Optional<User> findByOrgIdAndId(String orgId, String id);

}
