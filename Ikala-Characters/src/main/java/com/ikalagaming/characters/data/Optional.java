package com.ikalagaming.characters.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field that may be left out. A missing list or map reads as empty, and anything else as
 * null. Use {@link Default} instead for a field with a value when it is left out.
 *
 * @author Ches Burks
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Optional {}
