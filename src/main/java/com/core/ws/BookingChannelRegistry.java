package com.core.ws;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.core.ws.dto.BookingStatusPushMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/*
 * In-memory, single-instance session registry for the /ws/bookings/{bookingId}
 * channel. A clustered/multi-instance deployment would need this backed by
 * Redis pub-sub instead -- explicitly out of scope for this pass.
 */
@Component
@RequiredArgsConstructor
public class BookingChannelRegistry {

	private final ObjectMapper objectMapper;

	private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByBookingId = new ConcurrentHashMap<>();

	public void register(String bookingId, WebSocketSession session) {
		sessionsByBookingId
				.computeIfAbsent(bookingId, key -> new CopyOnWriteArraySet<>())
				.add(session);
	}

	public void deregister(String bookingId, WebSocketSession session) {
		sessionsByBookingId.computeIfPresent(bookingId, (key, sessions) -> {
			sessions.remove(session);
			return sessions.isEmpty() ? null : sessions;
		});
	}

	public void broadcast(String bookingId, String event) {
		Set<WebSocketSession> sessions = sessionsByBookingId.get(bookingId);

		if (sessions == null || sessions.isEmpty()) {
			return;
		}

		String payload = serialize(new BookingStatusPushMessage(bookingId, event));

		for (WebSocketSession session : sessions) {
			send(bookingId, session, payload);
		}
	}

	private void send(String bookingId, WebSocketSession session, String payload) {
		try {
			if (session.isOpen()) {
				session.sendMessage(new TextMessage(payload));
			} else {
				deregister(bookingId, session);
			}
		} catch (IOException ex) {
			deregister(bookingId, session);
		}
	}

	private String serialize(BookingStatusPushMessage message) {
		try {
			return objectMapper.writeValueAsString(message);
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to serialize booking push message", ex);
		}
	}
}
