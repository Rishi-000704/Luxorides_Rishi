package com.core.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.core.models.UserOtp;

public interface UserOtpRepository extends JpaRepository<UserOtp, String> {
	UserOtp findByPhone(String phone);

	/*
	 * A single bulk DELETE statement rather than find-then-remove(entity) -- the latter
	 * throws StaleObjectStateException if a concurrent request for the same phone (e.g.
	 * a double-tapped "Send OTP", or two overlapping requests) already deleted the row,
	 * since Hibernate's entity-level delete checks the affected-row-count. A bulk delete
	 * has no such expectation and is safe to call even when nothing matches.
	 */
	@Modifying
	@Query("DELETE FROM UserOtp u WHERE u.phone = :phone")
	void deleteByPhone(@Param("phone") String phone);
}
