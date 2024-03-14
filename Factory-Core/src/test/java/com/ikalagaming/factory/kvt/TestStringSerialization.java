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

    /**
     * Set up before all the tests.
     *
     * @throws Exception If something goes wrong.
     */
    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        EventManager.getInstance();
        PluginManager.getInstance();
        TestStringSerialization.plugin = new FactoryPlugin();
        TestStringSerialization.plugin.onLoad();
        TestStringSerialization.plugin.onEnable();
    }

    /**
     * Tear down after all the tests.
     *
     * @throws Exception If something goes wrong.
     */
    @AfterAll
    static void tearDownAfterClass() throws Exception {
        TestStringSerialization.plugin.onDisable();
        TestStringSerialization.plugin.onUnload();
        TestStringSerialization.plugin = null;
        PluginManager.destroyInstance();
        EventManager.destroyInstance();
    }

    /** Test converting to and from strings. */
    @Test
    void testFromString() {
        NodeTree tree = new Node();

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
}
