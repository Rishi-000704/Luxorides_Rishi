package com.core.services.common;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.core.models.UserOtp;
import com.core.repositories.UserOtpRepository;

/*
 * Isolated in its own bean (not a method on AuthenticationService) so its
 * REQUIRES_NEW @Transactional actually opens a fresh physical transaction on
 * every call -- calling a REQUIRES_NEW method on `this` from within the same
 * class would skip Spring's proxy (self-invocation) and just join the caller's
 * existing transaction, defeating the retry below.
 */
@Component
class OtpRecordWriter {

	private final UserOtpRepository userOtpRepository;

	OtpRecordWriter(UserOtpRepository userOtpRepository) {
		this.userOtpRepository = userOtpRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void replace(UserOtp record) {
		this.userOtpRepository.deleteByPhone(record.getPhone());
		this.userOtpRepository.save(record);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void delete(UserOtp record) {
		this.userOtpRepository.delete(record);
	}
}
