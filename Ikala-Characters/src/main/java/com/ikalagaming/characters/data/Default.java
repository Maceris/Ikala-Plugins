package com.ikalagaming.characters.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value a field has when it is left out, written as JSON, like {@code "0.35"} or {@code
 * "\"body\""}.
 *
 * @author Ches Burks
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Default {
    /**
     * The value, as JSON.
     *
     * @return The value.
     */
    String value();
}
