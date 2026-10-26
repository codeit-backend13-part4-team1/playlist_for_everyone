package com.codeit.sb13.playlist_for_everyone.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = Utf8ByteLengthValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT, METHOD, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface Utf8ByteLength {

    String message() default "UTF-8 기준 {max}바이트 이하여야 합니다.";

    int max();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
