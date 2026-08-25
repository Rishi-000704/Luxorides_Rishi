package com.core.services;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.DriverDutyAccessToken;
import com.core.models.enums.DriverDutyTokenStatus;
import com.core.repositories.DriverDutyAccessTokenRepository;

import lombok.RequiredArgsConstructor;

/*
 * Extracted from ExternalDriverDutyService's two private token-resolution
 * methods (resolveValidToken / resolveTokenForPaymentStatus) so the
 * WebSocket duty-payment handshake can apply the exact same rules instead
 * of re-deriving a third copy.
 */
@Component
@RequiredArgsConstructor
public class DriverDutyTokenValidator {

	private final DriverDutyAccessTokenRepository tokenRepository;

	public DriverDutyAccessToken resolveValidToken(String rawToken) {
		DriverDutyAccessToken token = findByRawToken(rawToken);

		if (token.getStatus() == DriverDutyTokenStatus.REVOKED) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty link has been revoked");
		}

		if (isExpired(token)) {
			markExpired(token);
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty link has expired");
		}

		if (token.getStatus() == DriverDutyTokenStatus.EXPIRED) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty link has expired");
		}

		return token;
	}

	/*
	 * Payment polling/push must remain available after duty completion --
	 * otherwise a client reconnecting after the duty ended can never learn
	 * whether the QR was paid. See ExternalDriverDutyService's original
	 * comment on this same carve-out.
	 */
	public DriverDutyAccessToken resolveTokenForPaymentStatus(String rawToken) {
		DriverDutyAccessToken token = findByRawToken(rawToken);

		if (token.getStatus() == DriverDutyTokenStatus.REVOKED) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty link has been revoked");
		}

		if (token.getStatus() == DriverDutyTokenStatus.COMPLETED) {
			return token;
		}

		if (isExpired(token)) {
			markExpired(token);
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty link has expired");
		}

		if (token.getStatus() == DriverDutyTokenStatus.EXPIRED) {
			throw new BusinessException(ErrorCode.BAD_REQUEST, "Duty link has expired");
		}

		return token;
	}

	private DriverDutyAccessToken findByRawToken(String rawToken) {
		return tokenRepository.findByTokenHash(hash(rawToken))
				.orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Invalid duty link"));
	}

	private boolean isExpired(DriverDutyAccessToken token) {
		return token.getExpiresAt() != null && token.getExpiresAt().isBefore(Instant.now());
	}

	private void markExpired(DriverDutyAccessToken token) {
		token.setStatus(DriverDutyTokenStatus.EXPIRED);
		tokenRepository.save(token);
	}

	public String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes()));
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to hash token", ex);
		}
	}
}
