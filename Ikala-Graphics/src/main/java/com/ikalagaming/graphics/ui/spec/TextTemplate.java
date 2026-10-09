package com.ikalagaming.graphics.ui.spec;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Text with {@code {name}} bindings in it, like {@code "Loaded {model.count} models"}. {@code {{}
 * and {@code }}} are literal braces.
 */
final class TextTemplate {

    /**
     * A piece of the text.
     *
     * @param text Literal text, or the binding name.
     * @param binding Whether this piece is a binding.
     */
    record Part(String text, boolean binding) {}

    /** The pieces, in order. */
    private final List<Part> parts;

    /**
     * Create a template.
     *
     * @param parts The pieces.
     */
    private TextTemplate(List<Part> parts) {
        this.parts = parts;
    }

    /**
     * Parse text.
     *
     * @param text The text.
     * @param path Where it is, for messages.
     * @return The template.
     * @throws SpecException If a brace isn't closed or a binding is empty.
     */
    static TextTemplate parse(@NonNull String text, @NonNull String path) {
        List<Part> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '{' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                literal.append('{');
                i += 2;
            } else if (c == '}' && i + 1 < text.length() && text.charAt(i + 1) == '}') {
                literal.append('}');
                i += 2;
            } else if (c == '{') {
                int end = text.indexOf('}', i);
                if (end < 0) {
                    throw new SpecException(path + ": unclosed '{' in \"" + text + "\"");
                }
                String name = text.substring(i + 1, end).trim();
                if (name.isEmpty()) {
                    throw new SpecException(path + ": empty binding in \"" + text + "\"");
                }
                if (!literal.isEmpty()) {
                    parts.add(new Part(literal.toString(), false));
                    literal.setLength(0);
                }
                parts.add(new Part(name, true));
                i = end + 1;
            } else {
                literal.append(c);
                ++i;
            }
        }
        if (!literal.isEmpty() || parts.isEmpty()) {
            parts.add(new Part(literal.toString(), false));
        }
        return new TextTemplate(List.copyOf(parts));
    }

    /**
     * The pieces.
     *
     * @return The pieces, in order.
     */
    List<Part> parts() {
        return parts;
    }

    /**
     * Whether the text is exactly one binding, so it can stand for a non-text value.
     *
     * @return True for text like {@code "{status.visible}"}.
     */
    boolean isSingleBinding() {
        return parts.size() == 1 && parts.getFirst().binding();
    }

    /**
     * Fill in the bindings.
     *
     * @param lookup Gives the value of a binding.
     * @return The text.
     */
    String render(@NonNull Function<String, Object> lookup) {
        StringBuilder out = new StringBuilder();
        for (Part part : parts) {
            if (part.binding()) {
                Object value = lookup.apply(part.text());
                out.append(value == null ? "" : value);
            } else {
                out.append(part.text());
            }
        }
        return out.toString();
    }
}
