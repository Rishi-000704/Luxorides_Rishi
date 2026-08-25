package com.core.ws;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookingStatusWebSocketHandler extends TextWebSocketHandler {

	public static final String BOOKING_ID_ATTRIBUTE = "bookingId";

	private final BookingChannelRegistry registry;

	@Override
	public void afterConnectionEstablished(@NonNull WebSocketSession session) {
		String bookingId = (String) session.getAttributes().get(BOOKING_ID_ATTRIBUTE);

		if (bookingId != null) {
			registry.register(bookingId, session);
		}
	}

	@Override
	public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
		String bookingId = (String) session.getAttributes().get(BOOKING_ID_ATTRIBUTE);

		if (bookingId != null) {
			registry.deregister(bookingId, session);
		}
	}
}
