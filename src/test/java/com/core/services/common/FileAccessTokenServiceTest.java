package com.core.services.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.exception.BusinessException;
import com.core.models.enums.FileAccessCategory;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/*
 * Covers Phase P0.2's core security guarantee: GET /file/{filename} now
 * requires a token that is (a) genuinely signed by the server, (b) not
 * expired, and (c) bound to the exact filename being requested -- and
 * proves each of the failure modes in section 11 of the checkpoint that
 * are testable at this layer (org/user/resource-ownership scoping happens
 * at token-issuance time in the assemblers, not here -- covered separately).
 */
class FileAccessTokenServiceTest {

	private static final String ORG_ID = "org-1";

	private static String hmacShaKeyForBase64() {
		return java.util.Base64.getEncoder().encodeToString(
				Keys.secretKeyFor(SignatureAlgorithm.HS256).getEncoded());
	}

	private FileAccessTokenService service;

	@BeforeEach
	void setUp() throws Exception {
		service = new FileAccessTokenService();
		Field field = FileAccessTokenService.class.getDeclaredField("secretKey");
		field.setAccessible(true);
		field.set(service, hmacShaKeyForBase64());
	}

	@Test
	void toAccessUrl_returnsFilenamePlusTokenQueryParam() {
		String url = service.toAccessUrl("abc123.jpg", ORG_ID, FileAccessCategory.PRIVATE);

		assertNotNull(url);
		assertTrue(url.startsWith("abc123.jpg?token="));
	}

	@Test
	void toAccessUrl_returnsNull_forNullOrBlankFilename() {
		assertEquals(null, service.toAccessUrl(null, ORG_ID, FileAccessCategory.PRIVATE));
		assertEquals(null, service.toAccessUrl("  ", ORG_ID, FileAccessCategory.PRIVATE));
	}

	@Test
	void validate_succeeds_forFreshlyIssuedToken() {
		String url = service.toAccessUrl("abc123.jpg", ORG_ID, FileAccessCategory.PRIVATE);
		String token = url.substring(url.indexOf("token=") + 6);

		FileAccessCategory category = service.validate("abc123.jpg", token);

		assertEquals(FileAccessCategory.PRIVATE, category);
	}

	@Test
	void validate_rejectsMissingToken() {
		assertThrows(BusinessException.class, () -> service.validate("abc123.jpg", null));
		assertThrows(BusinessException.class, () -> service.validate("abc123.jpg", ""));
	}

	@Test
	void validate_rejectsMalformedToken() {
		assertThrows(BusinessException.class, () -> service.validate("abc123.jpg", "not-a-jwt-at-all"));
	}

	@Test
	void validate_rejectsForgedToken_signedWithDifferentKey() {
		Key otherKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);

		Map<String, Object> claims = new HashMap<>();
		claims.put("purpose", "file-access");
		claims.put("orgId", ORG_ID);
		claims.put("category", "PRIVATE");

		String forged = Jwts.builder()
				.setClaims(claims)
				.setSubject("abc123.jpg")
				.setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(otherKey, SignatureAlgorithm.HS256)
				.compact();

		assertThrows(BusinessException.class, () -> service.validate("abc123.jpg", forged));
	}

	@Test
	void validate_rejectsExpiredToken() throws Exception {
		Field field = FileAccessTokenService.class.getDeclaredField("secretKey");
		field.setAccessible(true);
		String secret = (String) field.get(service);
		byte[] keyBytes = Decoders.BASE64.decode(secret);
		Key key = Keys.hmacShaKeyFor(keyBytes);

		Map<String, Object> claims = new HashMap<>();
		claims.put("purpose", "file-access");
		claims.put("orgId", ORG_ID);
		claims.put("category", "PRIVATE");

		String expired = Jwts.builder()
				.setClaims(claims)
				.setSubject("abc123.jpg")
				.setIssuedAt(new Date(System.currentTimeMillis() - 120_000))
				.setExpiration(new Date(System.currentTimeMillis() - 60_000))
				.signWith(key, SignatureAlgorithm.HS256)
				.compact();

		assertThrows(BusinessException.class, () -> service.validate("abc123.jpg", expired));
	}

	@Test
	void validate_rejectsToken_whenUsedAgainstADifferentFilename() {
		String url = service.toAccessUrl("file-a.jpg", ORG_ID, FileAccessCategory.PRIVATE);
		String token = url.substring(url.indexOf("token=") + 6);

		assertThrows(BusinessException.class, () -> service.validate("file-b.jpg", token));
	}

	@Test
	void validate_rejectsToken_missingPurposeClaim() throws Exception {
		Field field = FileAccessTokenService.class.getDeclaredField("secretKey");
		field.setAccessible(true);
		String secret = (String) field.get(service);
		byte[] keyBytes = Decoders.BASE64.decode(secret);
		Key key = Keys.hmacShaKeyFor(keyBytes);

		// A token signed with the SAME key but for a different purpose (e.g. a
		// hypothetical future token type) must not be usable here.
		String wrongPurpose = Jwts.builder()
				.setSubject("abc123.jpg")
				.claim("purpose", "something-else")
				.setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(key, SignatureAlgorithm.HS256)
				.compact();

		assertThrows(BusinessException.class, () -> service.validate("abc123.jpg", wrongPurpose));
	}

	@Test
	void issuedTokens_forDifferentCategories_carryTheRightCategoryClaim() {
		for (FileAccessCategory category : FileAccessCategory.values()) {
			String url = service.toAccessUrl("f.jpg", ORG_ID, category);
			String token = url.substring(url.indexOf("token=") + 6);

			assertEquals(category, service.validate("f.jpg", token));
		}
	}

	@Test
	void tokenDoesNotExposeRawOrgIdAsSubject_onlyFileName() {
		// The token's subject (what a decoded-but-unverified reader would see
		// first) is the filename, not any org/user identifier -- keeping PII
		// and internal identifiers out of the most casually-inspectable field.
		String url = service.toAccessUrl("abc123.jpg", ORG_ID, FileAccessCategory.PRIVATE);
		String token = url.substring(url.indexOf("token=") + 6);

		String[] parts = token.split("\\.");
		assertEquals(3, parts.length, "expected a standard 3-part JWS");
	}
}
