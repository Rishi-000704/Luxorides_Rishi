package com.core.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.core.models.UserOtp;

public interface UserOtpRepository extends JpaRepository<UserOtp, String> {
	UserOtp findByPhone(String phone);
}
