package com.ikalagaming.factory.chat;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A line of chat made of colored spans, like a grey system message or a player's colored title
 * before their name. Players can't type colors yet; the server builds them.
 *
 * @param spans The spans, in order.
 */
public record ChatText(@NonNull List<Span> spans) {

    /** The color that means the chat's default text color. */
    public static final int DEFAULT = -1;

    /** Grey, for system messages. */
    public static final int GREY = 0xAAAAAA;

    /** White. */
    public static final int WHITE = 0xFFFFFF;

    /** Yellow, for joins and leaves. */
    public static final int YELLOW = 0xFFFF55;

    /** Red, for errors. */
    public static final int RED = 0xFF5555;

    /** Green, for success. */
    public static final int GREEN = 0x55FF55;

    /** Gold, for the owner. */
    public static final int GOLD = 0xFFAA00;

    /** Aqua, for operators. */
    public static final int AQUA = 0x55FFFF;

    /**
     * A run of text in one color.
     *
     * @param text The text.
     * @param color The color as 0xRRGGBB, or {@link #DEFAULT}.
     */
    public record Span(@NonNull String text, int color) {}

    /**
     * Copy.
     *
     * @param spans The spans.
     */
    public ChatText {
        spans = List.copyOf(spans);
    }

    /**
     * Plain text in the default color.
     *
     * @param text The text.
     * @return The line.
     */
    public static ChatText of(@NonNull String text) {
        return new ChatText(List.of(new Span(text, DEFAULT)));
    }

    /**
     * Text in one color.
     *
     * @param text The text.
     * @param color The color.
     * @return The line.
     */
    public static ChatText of(@NonNull String text, int color) {
        return new ChatText(List.of(new Span(text, color)));
    }

    /**
     * This line with more text after it.
     *
     * @param text The text.
     * @param color Its color.
     * @return The longer line.
     */
    public ChatText append(@NonNull String text, int color) {
        final List<Span> longer = new ArrayList<>(spans);
        longer.add(new Span(text, color));
        return new ChatText(longer);
    }

    /**
     * The text without colors, for logs.
     *
     * @return The text.
     */
    public String plain() {
        final StringBuilder text = new StringBuilder();
        spans.forEach(span -> text.append(span.text()));
        return text.toString();
    }
}
