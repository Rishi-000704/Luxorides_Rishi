package com.core.models.enums;

public enum SmsProviderType {
    MSG91,
    TWILIO,
    GUPSHUP,
    TEXTLOCAL,
    AWS_SNS,

    /**
     * Dev/local-only: logs the message instead of calling a real gateway.
     * Never send real SMS. See SMSService#send.
     */
    CONSOLE
}
