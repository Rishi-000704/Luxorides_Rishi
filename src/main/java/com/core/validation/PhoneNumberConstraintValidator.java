package com.core.validation;

import com.core.util.PhoneNumberNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PhoneNumberConstraintValidator
        implements ConstraintValidator<ValidPhone, String> {

    @Override
    public boolean isValid(String value,
                           ConstraintValidatorContext context) {

        // Optional phone → valid if empty
        if (value == null || value.isBlank()) {
            return true;
        }

        try {
            PhoneNumberNormalizer.normalize(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
