package com.codeit.sb13.playlist_for_everyone.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class Utf8ByteLengthValidator implements ConstraintValidator<Utf8ByteLength, String> {

    private int max;

    @Override
    public void initialize(Utf8ByteLength annotation) {
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        return value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
