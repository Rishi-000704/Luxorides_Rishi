package com.core.ws;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DutyPaymentWebSocketHandler extends TextWebSocketHandler {

	public static final String DUTY_ID_ATTRIBUTE = "dutyId";

	private final DutyPaymentChannelRegistry registry;

	@Override
	public void afterConnectionEstablished(@NonNull WebSocketSession session) {
		String dutyId = (String) session.getAttributes().get(DUTY_ID_ATTRIBUTE);

		if (dutyId != null) {
			registry.register(dutyId, session);
		}
	}

	@Override
	public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
		String dutyId = (String) session.getAttributes().get(DUTY_ID_ATTRIBUTE);

		if (dutyId != null) {
			registry.deregister(dutyId, session);
		}
	}
}
