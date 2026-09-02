package com.core.services.common;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.models.enums.FileAccessCategory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/*
 * Phase P0.2 -- makes GET /file/{filename} require a short-lived, tamper-proof,
 * file-specific access token instead of being fully open (see FileController).
 *
 * DESIGN: stateless HMAC-signed token (reusing the same jjwt library and
 * signing idiom JwtService already uses for auth tokens), NOT a DB-backed
 * opaque token like DriverDutyAccessToken/EstimateAccessToken/TripShareLink.
 * Those three all use an opaque-token-plus-DB-lookup pattern, which is the
 * more common pattern in this codebase -- but that pattern exists because
 * those tokens need explicit revocation over a long validity window (hours,
 * for a shared duty/estimate/trip link). File-access tokens are the opposite
 * case: minted fresh on every single authorized DTO read, valid for minutes,
 * potentially many per screen (a KYC review screen alone might render half a
 * dozen document photos). A DB lookup per image at that volume is real,
 * avoidable load; a stateless signature check is not. Revocation isn't a
 * meaningful requirement at a few-minutes TTL. Given the sensitivity here is
 * "can this token be forged, or replayed against a different file/forever",
 * not "can we revoke a token an operator no longer trusts", a signed
 * stateless token is the better fit for this one specific use, even though
 * it's a different pattern from the codebase's other three token types.
 *
 * A token is only ever minted by server-side code that has already produced
 * an authorized DTO for the requesting caller (see the assemblers that call
 * toAccessUrl) -- there is no endpoint that hands out a token for an
 * arbitrary caller-supplied filename.
 */
@Service
public class FileAccessTokenService {

	private static final String CLAIM_PURPOSE = "purpose";
	private static final String CLAIM_ORG_ID = "orgId";
	private static final String CLAIM_CATEGORY = "category";
	private static final String PURPOSE_FILE_ACCESS = "file-access";

	private static final long KYC_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);
	private static final long PRIVATE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);
	private static final long PUBLIC_TTL_MILLIS = TimeUnit.MINUTES.toMillis(60);

	@Value("${security.file-token.secret-key}")
	private String secretKey;

	/**
	 * Builds the value an assembler/DTO should expose in place of a bare
	 * stored filename. Every existing frontend's "turn this value into an
	 * <img src>" helper does plain string concatenation (verified against
	 * the customer app's getFileUrl, which already tolerates a query string
	 * in the value), so appending "?token=..." here requires no frontend
	 * change. Returns null unchanged for a null/blank filename so callers
	 * don't need their own null checks.
	 */
	public String toAccessUrl(String fileName, String orgId, FileAccessCategory category) {
		if (fileName == null || fileName.isBlank()) {
			return null;
		}

		return fileName + "?token=" + issueToken(fileName, orgId, category);
	}

	private String issueToken(String fileName, String orgId, FileAccessCategory category) {
		Map<String, Object> claims = new HashMap<>();
		claims.put(CLAIM_PURPOSE, PURPOSE_FILE_ACCESS);
		claims.put(CLAIM_ORG_ID, orgId);
		claims.put(CLAIM_CATEGORY, category.name());

		long ttlMillis = switch (category) {
			case KYC -> KYC_TTL_MILLIS;
			case PRIVATE -> PRIVATE_TTL_MILLIS;
			case PUBLIC -> PUBLIC_TTL_MILLIS;
		};

		return Jwts.builder()
				.setClaims(claims)
				.setSubject(fileName)
				.setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
				.signWith(signingKey(), SignatureAlgorithm.HS256)
				.compact();
	}

	/**
	 * Validates that {@code rawToken} is a genuine, unexpired, unmodified
	 * file-access token minted specifically for {@code requestedFileName}.
	 * Throws the same error for every failure reason (missing token,
	 * malformed token, forged signature, expired, wrong file bound) so a
	 * caller can't distinguish "almost valid" from "completely wrong" --
	 * deliberately not an enumeration side channel.
	 */
	public FileAccessCategory validate(String requestedFileName, String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw fileNotFound();
		}

		Claims claims;

		try {
			claims = Jwts.parserBuilder()
					.setSigningKey(signingKey())
					.build()
					.parseClaimsJws(rawToken)
					.getBody();
		} catch (JwtException | IllegalArgumentException ex) {
			throw fileNotFound();
		}

		if (!PURPOSE_FILE_ACCESS.equals(claims.get(CLAIM_PURPOSE, String.class))) {
			throw fileNotFound();
		}

		if (!requestedFileName.equals(claims.getSubject())) {
			throw fileNotFound();
		}

		String categoryName = claims.get(CLAIM_CATEGORY, String.class);

		try {
			return FileAccessCategory.valueOf(categoryName);
		} catch (IllegalArgumentException | NullPointerException ex) {
			throw fileNotFound();
		}
	}

	private BusinessException fileNotFound() {
		return new BusinessException(ErrorCode.ACCESS_DENIED, "File not found");
	}

	private Key signingKey() {
		byte[] keyBytes = Decoders.BASE64.decode(secretKey);
		return Keys.hmacShaKeyFor(keyBytes);
	}
}
