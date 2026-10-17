package com.ikalagaming.factory.kvt;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.plugins.PluginManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

/**
 * Tests converting to and from strings.
 *
 * @author Ches Burks
 */
class TestStringSerialization {

    private static FactoryPlugin plugin;

    /** Set up before all the tests. */
    @BeforeAll
    static void setUpBeforeClass() {
        EventManager.getInstance();
        PluginManager.getInstance();
        TestStringSerialization.plugin = new FactoryPlugin();
        TestStringSerialization.plugin.onLoad();
        TestStringSerialization.plugin.onEnable();
    }

    /** Tear down after all the tests. */
    @AfterAll
    static void tearDownAfterClass() {
        TestStringSerialization.plugin.onDisable();
        TestStringSerialization.plugin.onUnload();
        TestStringSerialization.plugin = null;
        PluginManager.destroyInstance();
        EventManager.destroyInstance();
    }

    /** Test converting to and from strings. */
    @Test
    void testFromString() {
        KVT tree = new Node();

        tree.addString(
                "annoying name 7!@#$%^ %^&_+'-={}|[]\\\\*()\\\"",
                "annoying value 7!@#$%^ %^&_+'-={}|[]\\\\*()\\\"");
        tree.addBoolean("bool", true);
        tree.addByte("byte", (byte) 1);
        tree.addDouble("double", 1.02);
        tree.addFloat("float", 2.34f);
        tree.addInteger("int", 3);
        tree.addLong("long", 4L);
        tree.addShort("short", (short) 5);
        tree.addString("string", "test");
        tree.addNode("child1.child2");

        tree.addBooleanArray("boolArray", List.of(true, false));
        tree.addByteArray("byteArray", List.of((byte) 1));
        tree.addDoubleArray("doubleArray", List.of(1.02));
        tree.addFloatArray("floatArray", List.of(2.34f));
        tree.addIntegerArray("intArray", List.of(3));
        tree.addLongArray("longArray", List.of(4L));
        tree.addShortArray("shortArray", List.of((short) 5));
        tree.addStringArray("stringArray", List.of("test"));
        tree.addNodeArray("childArray");

        Optional<Node> maybeNode = TreeStringSerialization.fromString(tree.toString());
        Assertions.assertTrue(maybeNode.isPresent());

        Assertions.assertEquals(tree, maybeNode.get());
    }

    /** Block comments are skipped anywhere between tokens, but not inside strings. */
    @Test
    void testComments() {
        Optional<Node> maybeNode =
                TreeStringSerialization.fromString(
                        """
                        /* Before */
                        {name:"a /* b */ c", /* after a value,
                           over several lines */ count:/* inline */2}
                        /* At the end */""");
        Assertions.assertTrue(maybeNode.isPresent());
        Assertions.assertEquals("a /* b */ c", maybeNode.get().getString("name"));
        Assertions.assertEquals(2, (int) maybeNode.get().getInteger("count"));
        // Everything on one line still parses, which line comments wouldn't allow
        Assertions.assertTrue(
                TreeStringSerialization.fromString("{/* note */a:1,/* note */b:[I;1,/* x */2]}")
                        .isPresent());
    }

    /** Keys that would read as a number or boolean are quoted, so they read back as keys. */
    @Test
    void testLiteralKeys() {
        // A period is a path, so floating point keys with a point can't happen
        List<String> literalKeys =
                List.of(
                        "12",
                        "-3",
                        "+4",
                        "5b",
                        "6L",
                        "7s",
                        "true",
                        "false",
                        "1e5",
                        "-2E+3f",
                        "3f",
                        "4D",
                        "NaN",
                        "-Infinity",
                        "InfinityF");
        for (String key : literalKeys) {
            KVT tree = new Node();
            tree.addInteger(key, 1);
            Assertions.assertEquals("{\"" + key + "\":1}", tree.toString(), key);
            Optional<Node> maybeNode = TreeStringSerialization.fromString(tree.toString());
            Assertions.assertTrue(maybeNode.isPresent(), key);
            Assertions.assertEquals(tree, maybeNode.get(), key);
        }

        // Only whole literals need quotes, the lexer reads anything longer as an identifier
        List<String> plainKeys = List.of("x12", "12x", "1-2", "truey", "_true", "1e", "a+b");
        for (String key : plainKeys) {
            KVT tree = new Node();
            tree.addInteger(key, 1);
            Assertions.assertEquals("{" + key + ":1}", tree.toString(), key);
            Optional<Node> maybeNode = TreeStringSerialization.fromString(tree.toString());
            Assertions.assertTrue(maybeNode.isPresent(), key);
            Assertions.assertEquals(tree, maybeNode.get(), key);
        }
    }

    /** Values that are not finite numbers are written in a form that reads back. */
    @Test
    void testNonFiniteNumbers() {
        KVT tree = new Node();
        tree.addDouble("dNaN", Double.NaN);
        tree.addDouble("dPos", Double.POSITIVE_INFINITY);
        tree.addDouble("dNeg", Double.NEGATIVE_INFINITY);
        tree.addFloat("fNaN", Float.NaN);
        tree.addFloat("fPos", Float.POSITIVE_INFINITY);
        tree.addFloat("fNeg", Float.NEGATIVE_INFINITY);
        tree.addDoubleArray(
                "dArray", List.of(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
        tree.addFloatArray(
                "fArray", List.of(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY));

        Optional<Node> maybeNode = TreeStringSerialization.fromString(tree.toString());
        Assertions.assertTrue(maybeNode.isPresent(), tree.toString());
        Assertions.assertEquals(tree, maybeNode.get());
        Assertions.assertEquals(Optional.of(NodeType.FLOAT), maybeNode.get().getType("fNeg"));
        Assertions.assertEquals(Optional.of(NodeType.DOUBLE), maybeNode.get().getType("dNeg"));

        // Written by hand, with either suffix and sign
        Optional<Node> handWritten =
                TreeStringSerialization.fromString("{a:+Infinityd,b:-NaNf,c:Infinity}");
        Assertions.assertTrue(handWritten.isPresent());
        Assertions.assertEquals(Double.POSITIVE_INFINITY, handWritten.get().getDouble("a"));
        Assertions.assertTrue(Float.isNaN(handWritten.get().getFloat("b")));
        Assertions.assertEquals(Double.POSITIVE_INFINITY, handWritten.get().getDouble("c"));
    }

    /** An empty key is written as a pair of quotes, which read back as the empty key. */
    @Test
    void testEmptyKey() {
        KVT tree = new Node();
        tree.addInteger("", 1);
        tree.addStringArray("strings", List.of("", "a"));
        Assertions.assertEquals("{\"\":1,strings:[T;\"\",\"a\"]}", tree.toString());

        Optional<Node> maybeNode = TreeStringSerialization.fromString(tree.toString());
        Assertions.assertTrue(maybeNode.isPresent());
        Assertions.assertEquals(List.of(""), maybeNode.get().getKeys().subList(0, 1));
        Assertions.assertEquals(tree, maybeNode.get());
    }
}
