package com.ikalagaming.database;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Identifies that a field should be stored as a JSON blob in the database.
 *
 * @author Ches Burks
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface JSONField {}
