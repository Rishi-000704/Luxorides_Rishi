package com.core.ws.dto;

public record DutyPaymentPushMessage(String dutyId, boolean paid, String status) {
}
