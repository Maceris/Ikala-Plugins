package com.ikalagaming.factory.server;

import com.ikalagaming.factory.world.WorldTime;

import lombok.NonNull;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Reads the times {@code /time} takes, and writes the world's clock for people.
 *
 * <ul>
 *   <li>A time of day: a name ({@code sunrise}, {@code morning}, {@code noon}, {@code sunset},
 *       {@code night}, {@code midnight}), a 24-hour {@code HH:MM}, or an hour such as {@code 18} or
 *       {@code 6.5}.
 *   <li>A length of time: ticks such as {@code 500}, hours such as {@code 3h}, or days such as
 *       {@code 2d}.
 * </ul>
 */
final class TimeArguments {

    /** Named times of day, as hours. */
    private static final Map<String, Double> NAMED =
            Map.of(
                    "sunrise", 6.0,
                    "morning", 8.0,
                    "day", 8.0,
                    "noon", 12.0,
                    "sunset", 18.0,
                    "night", 21.0,
                    "midnight", 0.0);

    /** Minutes in an hour. */
    private static final int MINUTES = 60;

    /**
     * A time of day.
     *
     * @param text What was typed.
     * @return Ticks since midnight, from 0 up to a day, or empty if it isn't a time of day.
     */
    static OptionalLong timeOfDay(@NonNull String text) {
        final String word = text.strip().toLowerCase(Locale.ROOT);
        double hours;
        if (NAMED.containsKey(word)) {
            hours = NAMED.get(word);
        } else if (word.matches("\\d{1,2}:\\d{2}")) {
            final String[] parts = word.split(":");
            final int minutes = Integer.parseInt(parts[1]);
            if (minutes >= MINUTES) {
                return OptionalLong.empty();
            }
            hours = Integer.parseInt(parts[0]) + minutes / (double) MINUTES;
        } else {
            try {
                hours = Double.parseDouble(word);
            } catch (NumberFormatException e) {
                return OptionalLong.empty();
            }
        }
        if (!(hours >= 0 && hours <= WorldTime.DAY_HOURS)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(
                Math.floorMod(Math.round(hours * WorldTime.HOUR_TICKS), WorldTime.DAY_TICKS));
    }

    /**
     * A length of time.
     *
     * @param text What was typed.
     * @return Ticks, possibly negative, or empty if it isn't a length.
     */
    static OptionalLong length(@NonNull String text) {
        final String word = text.strip().toLowerCase(Locale.ROOT);
        long unit = 1;
        String number = word;
        if (word.endsWith("h")) {
            unit = WorldTime.HOUR_TICKS;
            number = word.substring(0, word.length() - 1);
        } else if (word.endsWith("d")) {
            unit = WorldTime.DAY_TICKS;
            number = word.substring(0, word.length() - 1);
        }
        try {
            final double amount = Double.parseDouble(number);
            if (!Double.isFinite(amount)) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Math.round(amount * unit));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * A time the same day as another, at a time of day.
     *
     * @param now The time now.
     * @param timeOfDay Ticks since midnight.
     * @return The time.
     */
    static long sameDay(long now, long timeOfDay) {
        return WorldTime.day(now) * WorldTime.DAY_TICKS + timeOfDay;
    }

    /**
     * A time for people: the day, counting from 1, and the 24-hour clock.
     *
     * @param ticks The time.
     * @return Text such as "Day 3, 18:30".
     */
    static String describe(long ticks) {
        final long minutes = WorldTime.timeOfDay(ticks) * MINUTES / WorldTime.HOUR_TICKS;
        return String.format(
                Locale.ROOT,
                "Day %d, %02d:%02d",
                WorldTime.day(ticks) + 1,
                minutes / MINUTES,
                minutes % MINUTES);
    }

    /** Not to be made. */
    private TimeArguments() {}
}
