package com.core.ws;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.core.dtos.driverduty.DriverDutyLocationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/*
 * In-memory, single-instance session registry for the /ws/duty-location/{dutyId}
 * channel. Same clustering caveat as BookingChannelRegistry/DutyPaymentChannelRegistry
 * -- a clustered/multi-instance deployment would need this backed by Redis
 * pub-sub instead.
 *
 * Unlike the other two registries, this one broadcasts the real payload
 * (coordinates) rather than a bare signal -- location changes continuously
 * (~every 10-15s during a duty), so routing every ping through "push signal,
 * client refetches REST" would double load for no benefit, since there's no
 * other derived state to reconcile beyond the coordinates themselves.
 */
@Component
@RequiredArgsConstructor
public class DutyLocationChannelRegistry {

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

	public void broadcast(String dutyId, DriverDutyLocationResponse payload) {
		Set<WebSocketSession> sessions = sessionsByDutyId.get(dutyId);

		if (sessions == null || sessions.isEmpty()) {
			return;
		}

		String serialized = serialize(payload);

		for (WebSocketSession session : sessions) {
			send(dutyId, session, serialized);
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

	private String serialize(DriverDutyLocationResponse payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to serialize duty location push message", ex);
		}
	}
}
