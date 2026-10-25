package com.ikalagaming.characters.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes a definition or one of its fields for people writing content. It becomes the
 * description in the generated JSON Schema, which editors show as hover help.
 *
 * @author Ches Burks
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.TYPE})
public @interface Doc {
    /**
     * The description.
     *
     * @return The description.
     */
    String value();
}
