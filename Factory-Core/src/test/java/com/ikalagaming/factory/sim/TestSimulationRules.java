package com.ikalagaming.factory.sim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Guards the simulation's rules on its source: no floating point, no wall clock, no unseeded
 * randomness and no hash-ordered collections, since any of them can make the client and server
 * disagree.
 */
class TestSimulationRules {

    /** Where the simulation's source is, from the project folder tests run in. */
    private static final Path SOURCE = Path.of("src/main/java/com/ikalagaming/factory/sim");

    /** Comments, string literals and character literals, which may say anything. */
    private static final Pattern NOT_CODE =
            Pattern.compile(
                    "/\\*.*?\\*/|//[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'",
                    Pattern.DOTALL);

    /** What the simulation must not use, and why. */
    private static final List<Rule> RULES =
            List.of(
                    new Rule("\\b(?:float|double|Float|Double|StrictMath)\\b", "floating point"),
                    new Rule(
                            "\\bMath\\.(?:sqrt|cbrt|hypot|sin|cos|tan|asin|acos|atan2?|pow|exp|log\\w*"
                                    + "|round|rint|floor|ceil|signum|toRadians|toDegrees|random)\\b",
                            "floating point math"),
                    new Rule(
                            "\\b(?:System\\.(?:nanoTime|currentTimeMillis)|Instant|LocalDateTime"
                                    + "|Clock)\\b",
                            "the wall clock"),
                    new Rule(
                            "\\b(?:Random|ThreadLocalRandom|SecureRandom|UUID)\\b",
                            "unseeded randomness; use SplittableRandom with a seed"),
                    new Rule("\\b(?:HashMap|HashSet|ConcurrentHashMap)\\b", "hash order"));

    /**
     * A forbidden pattern.
     *
     * @param regex What to look for in code.
     * @param why What it would bring in.
     */
    private record Rule(String regex, String why) {
        /**
         * The compiled pattern.
         *
         * @return The pattern.
         */
        Pattern pattern() {
            return Pattern.compile(regex);
        }
    }

    /**
     * Code with comments and literals blanked out, keeping lines where they were.
     *
     * @param source The source.
     * @return The code.
     */
    static String code(String source) {
        final Matcher matcher = NOT_CODE.matcher(source);
        final StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(
                    out, Matcher.quoteReplacement(matcher.group().replaceAll("[^\\n]", " ")));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Where code breaks the rules.
     *
     * @param name The file's name, for the report.
     * @param source The source.
     * @return One line per break.
     */
    static List<String> breaks(String name, String source) {
        final String code = code(source);
        final List<String> out = new ArrayList<>();
        for (Rule rule : RULES) {
            final Matcher matcher = rule.pattern().matcher(code);
            while (matcher.find()) {
                final int line =
                        1
                                + (int)
                                        code.substring(0, matcher.start())
                                                .chars()
                                                .filter(c -> c == '\n')
                                                .count();
                out.add(name + ":" + line + " uses " + matcher.group() + " (" + rule.why() + ")");
            }
        }
        return out;
    }

    /** The simulation's source follows the rules. */
    @Test
    void testSource() throws IOException {
        assertTrue(Files.isDirectory(SOURCE), () -> SOURCE.toAbsolutePath() + " is missing");
        final List<String> found = new ArrayList<>();
        int files = 0;
        try (Stream<Path> paths = Files.walk(SOURCE)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                ++files;
                found.addAll(
                        breaks(
                                SOURCE.relativize(path).toString(),
                                Files.readString(path, StandardCharsets.UTF_8)));
            }
        }
        assertTrue(files > 0, "no simulation source found");
        assertTrue(found.isEmpty(), () -> String.join("\n", found));
    }

    /** The guard catches what it should and ignores comments and strings. */
    @Test
    void testGuard() {
        assertFalse(breaks("a", "double x = 1;").isEmpty());
        assertFalse(breaks("a", "long t = System.nanoTime();").isEmpty());
        assertFalse(breaks("a", "var r = new Random();").isEmpty());
        assertFalse(breaks("a", "long r = (long) Math.sqrt(4);").isEmpty());
        assertTrue(breaks("a", "long a = Math.floorDiv(1, 2); // a double").isEmpty());
        assertTrue(breaks("a", "/** A double, from Random. */\nString s = \"double\";").isEmpty());
        assertTrue(breaks("a", "var r = new SplittableRandom(4);").isEmpty());
    }
}
