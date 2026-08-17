package com.core.util;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.google.i18n.phonenumbers.NumberParseException;

public final class PhoneNumberNormalizer {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneNumberNormalizer() {}

    /**
     * Accepts ONLY E.164 formatted phone numbers.
     * Example: +918840844024
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
        	throw new BusinessException(ErrorCode.ILLEGAL_PHONE_NUMBER, "Phone number is required.");
        }

        if (!raw.startsWith("+")) {
        	throw new BusinessException(ErrorCode.ILLEGAL_PHONE_NUMBER, "Phone number must be in E.164 format (e.g. +918840******).");
        }

        try {
            Phonenumber.PhoneNumber number = UTIL.parse(raw, null);

            if (!UTIL.isValidNumber(number)) {
            	throw new BusinessException(ErrorCode.ILLEGAL_PHONE_NUMBER, "Invalid phone number.");
            }

            return UTIL.format(
                number,
                PhoneNumberUtil.PhoneNumberFormat.E164
            );

        } catch (NumberParseException e) {
        	throw new BusinessException(ErrorCode.ILLEGAL_PHONE_NUMBER, e.getMessage());
        }
    }
}
