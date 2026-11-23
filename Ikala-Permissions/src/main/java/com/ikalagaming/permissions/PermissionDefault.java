package com.ikalagaming.permissions;

import lombok.NonNull;

import java.util.Locale;
import java.util.Optional;

/** Who has a permission when nothing sets it, as Bukkit's {@code default:} says. */
public enum PermissionDefault {
    /** Everyone. */
    TRUE,
    /** Nobody. */
    FALSE,
    /** Operators. */
    OP,
    /** Everyone who isn't an operator. */
    NOT_OP;

    /**
     * Whether someone has the permission by default.
     *
     * @param op Whether they are an operator.
     * @return True if they have it.
     */
    public boolean value(boolean op) {
        return switch (this) {
            case TRUE -> true;
            case FALSE -> false;
            case OP -> op;
            case NOT_OP -> !op;
        };
    }

    /**
     * Read a default as written in YAML: {@code true}, {@code false}, {@code op}, {@code not op}
     * and Bukkit's other spellings, in any case.
     *
     * @param value The value, a boolean or text.
     * @return The default, if the value is one.
     */
    public static Optional<PermissionDefault> named(Object value) {
        if (value instanceof Boolean bool) {
            return Optional.of(bool ? TRUE : FALSE);
        }
        if (value == null) {
            return Optional.empty();
        }
        return switch (value.toString().strip().toLowerCase(Locale.ROOT)) {
            case "true", "yes" -> Optional.of(TRUE);
            case "false", "no" -> Optional.of(FALSE);
            case "op", "isop", "operator", "admin" -> Optional.of(OP);
            case "not op", "notop", "!op", "not_op", "!operator", "!admin" -> Optional.of(NOT_OP);
            default -> Optional.empty();
        };
    }

    /**
     * The default's name in YAML.
     *
     * @param def The default.
     * @return Its text.
     */
    public static String text(@NonNull PermissionDefault def) {
        return def == NOT_OP ? "not op" : def.name().toLowerCase(Locale.ROOT);
    }
}
