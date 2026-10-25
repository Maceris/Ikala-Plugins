package com.ikalagaming.characters.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Id IDs}.
 *
 * @author Ches Burks
 */
class TestId {

    /** IDs parse into their parts and print back the same. */
    @Test
    void testParse() {
        Id id = Id.parse("ikala:part/biped/forearm_robot");
        Assertions.assertEquals("ikala", id.namespace());
        Assertions.assertEquals("part/biped/forearm_robot", id.path());
        Assertions.assertEquals("ikala:part/biped/forearm_robot", id.toString());
    }

    /** Malformed IDs are rejected. */
    @Test
    void testInvalid() {
        String[] invalid = {
            "biped",
            "Ikala:biped",
            "ikala:Biped",
            "ikala:",
            ":biped",
            "ikala:a//b",
            "ikala:/a",
            "ikala:a/",
            "ikala:a b"
        };
        for (String text : invalid) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> Id.parse(text), text);
        }
    }

    /** Patterns match exactly or by prefix. */
    @Test
    void testMatches() {
        Id id = Id.parse("ikala:slider/biped/height");
        Assertions.assertTrue(id.matches("ikala:slider/biped/height"));
        Assertions.assertTrue(id.matches("ikala:slider/biped/*"));
        Assertions.assertTrue(id.matches("ikala:*"));
        Assertions.assertFalse(id.matches("ikala:slider/quadruped/*"));
        Assertions.assertFalse(id.matches("ikala:slider/biped/heigh"));
    }

    /** IDs sort by namespace, then path. */
    @Test
    void testOrder() {
        Assertions.assertTrue(Id.parse("a:z").compareTo(Id.parse("b:a")) < 0);
        Assertions.assertTrue(Id.parse("a:a/b").compareTo(Id.parse("a:a/c")) < 0);
    }
}
