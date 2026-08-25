package com.core.ws;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.core.ws.dto.DutyPaymentPushMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/*
 * In-memory, single-instance session registry for the /ws/duty-payment/{token}
 * channel. Same clustering caveat as BookingChannelRegistry -- see there.
 */
@Component
@RequiredArgsConstructor
public class DutyPaymentChannelRegistry {

	private final ObjectMapper objectMapper;

	private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByDutyId = new ConcurrentHashMap<>();

	public void register(String dutyId, WebSocketSession session) {
		sessionsByDutyId
				.computeIfAbsent(dutyId, key -> new CopyOnWriteArraySet<>())
				.add(session);
	}

	public void deregister(String dutyId, WebSocketSession session) {
		sessionsByDutyId.computeIfPresent(dutyId, (key, sessions) -> {
			sessions.remove(session);
			return sessions.isEmpty() ? null : sessions;
		});
	}

	public void broadcast(String dutyId, boolean paid, String status) {
		Set<WebSocketSession> sessions = sessionsByDutyId.get(dutyId);

		if (sessions == null || sessions.isEmpty()) {
			return;
		}

		String payload = serialize(new DutyPaymentPushMessage(dutyId, paid, status));

		for (WebSocketSession session : sessions) {
			send(dutyId, session, payload);
		}
	}

	private void send(String dutyId, WebSocketSession session, String payload) {
		try {
			if (session.isOpen()) {
				session.sendMessage(new TextMessage(payload));
			} else {
				deregister(dutyId, session);
			}
		} catch (IOException ex) {
			deregister(dutyId, session);
		}
	}

	private String serialize(DutyPaymentPushMessage message) {
		try {
			return objectMapper.writeValueAsString(message);
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to serialize duty payment push message", ex);
		}
	}
}
