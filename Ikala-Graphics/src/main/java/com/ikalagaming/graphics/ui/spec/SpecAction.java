package com.ikalagaming.graphics.ui.spec;

import static com.ikalagaming.graphics.ui.spec.SpecKeys.*;

import lombok.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A handler written as an action instead of a handler name, so a spec can work with scripts without
 * any Java: {@code resume(tag, value)} resumes the script that opened the spec, and {@code
 * script(file.iks#label)} starts a script.
 */
sealed interface SpecAction {
    /** An action: a known name, then its arguments in parentheses. */
    Pattern FORM =
            Pattern.compile(
                    "^\\s*("
                            + Pattern.quote(RESUME)
                            + "|"
                            + Pattern.quote(SCRIPT)
                            + ")\\s*\\((.*)\\)\\s*$",
                    Pattern.DOTALL);

    /** A whole value that is one binding, like {@code {item.id}}, which keeps its type. */
    Pattern BINDING = Pattern.compile("^\\{([^{}]+)}$");

    /** A whole number. */
    Pattern INTEGER = Pattern.compile("^[+-]?\\d+$");

    /** A decimal number. */
    Pattern DECIMAL = Pattern.compile("^[+-]?(\\d+\\.\\d*|\\.\\d+|\\d+)([eE][+-]?\\d+)?$");

    /**
     * Resume the script that opened the spec.
     *
     * @param tag The tag its await waits for.
     * @param value The value as written, or null to pass the event's value.
     */
    record Resume(@NonNull String tag, String value) implements SpecAction {}

    /**
     * Start a script file owned by the spec's plugin.
     *
     * @param file The file, in the plugin's scripts folder.
     * @param label The label to start at, or null for the beginning.
     */
    record RunScript(@NonNull String file, String label) implements SpecAction {}

    /**
     * Read a handler value as an action.
     *
     * @param text The handler value.
     * @param where Where it is, for messages.
     * @return The action, or null if it is a plain handler name.
     * @throws SpecException If it is an action written wrong.
     */
    static SpecAction parse(@NonNull String text, @NonNull String where) {
        Matcher matcher = FORM.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        String arguments = matcher.group(2).trim();
        if (RESUME.equals(matcher.group(1))) {
            int comma = arguments.indexOf(',');
            String tag = (comma < 0 ? arguments : arguments.substring(0, comma)).trim();
            if (tag.isEmpty()) {
                throw new SpecException(where + ": " + RESUME + "(...) needs a tag");
            }
            String value = comma < 0 ? null : arguments.substring(comma + 1).trim();
            return new Resume(tag, value);
        }
        int hash = arguments.indexOf(SCRIPT_LABEL);
        String file = (hash < 0 ? arguments : arguments.substring(0, hash)).trim();
        String label = hash < 0 ? null : arguments.substring(hash + 1).trim();
        if (file.isEmpty() || (label != null && label.isEmpty())) {
            throw new SpecException(
                    where
                            + ": "
                            + SCRIPT
                            + "(...) needs a file, and a label after '#' if it has one");
        }
        return new RunScript(file, label);
    }

    /**
     * Read a written value: a number, true, false, null, quoted text, or plain text. Bindings are
     * handled by whoever has the scope.
     *
     * @param text The value as written.
     * @return The value.
     */
    static Object literal(@NonNull String text) {
        if (INTEGER.matcher(text).matches()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                // Too big for an int, so it is a double
                return Double.parseDouble(text);
            }
        }
        if (DECIMAL.matcher(text).matches()) {
            return Double.parseDouble(text);
        }
        switch (text) {
            case "true":
                return Boolean.TRUE;
            case "false":
                return Boolean.FALSE;
            case "null":
                return null;
            default:
                break;
        }
        if (text.length() >= 2) {
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if ((first == '"' || first == '\'') && last == first) {
                return text.substring(1, text.length() - 1);
            }
        }
        return text;
    }
}
