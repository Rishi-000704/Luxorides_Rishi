package com.core.ws;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.core.models.DriverDutyAccessToken;
import com.core.services.DriverDutyTokenValidator;

import lombok.RequiredArgsConstructor;

/*
 * The raw duty token IS the credential (same convention as
 * /driver-api/duty/{token}/**), so it travels as the last path segment, not
 * a query param. Validated via DriverDutyTokenValidator.resolveTokenForPaymentStatus
 * -- the same rule ExternalDriverDutyService.checkQrPaymentStatus applies,
 * including letting COMPLETED tokens through so a driver reconnecting after
 * duty completion can still see the final payment push.
 */
@Component
@RequiredArgsConstructor
public class DutyTokenHandshakeInterceptor implements HandshakeInterceptor {

	private final DriverDutyTokenValidator tokenValidator;

	@Override
	public boolean beforeHandshake(
			@NonNull ServerHttpRequest request,
			@NonNull ServerHttpResponse response,
			@NonNull WebSocketHandler wsHandler,
			@NonNull Map<String, Object> attributes) {

		String rawToken = extractLastPathSegment(request.getURI());

		if (rawToken == null) {
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}

		try {
			DriverDutyAccessToken token = tokenValidator.resolveTokenForPaymentStatus(rawToken);
			attributes.put(DutyPaymentWebSocketHandler.DUTY_ID_ATTRIBUTE, token.getDutyId());
			return true;
		} catch (Exception ex) {
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}
	}

	@Override
	public void afterHandshake(
			@NonNull ServerHttpRequest request,
			@NonNull ServerHttpResponse response,
			@NonNull WebSocketHandler wsHandler,
			Exception exception) {
	}

	private String extractLastPathSegment(URI uri) {
		String path = uri.getPath();

		if (path == null || path.isBlank()) {
			return null;
		}

		String[] segments = path.split("/");
		return segments.length == 0 ? null : segments[segments.length - 1];
	}
}
