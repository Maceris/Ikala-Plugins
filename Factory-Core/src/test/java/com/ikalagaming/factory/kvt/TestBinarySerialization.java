package com.ikalagaming.factory.kvt;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.plugins.PluginManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

/**
 * Tests converting to and from binary.
 *
 * @author Ches Burks
 */
class TestBinarySerialization {

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
        TestBinarySerialization.plugin = new FactoryPlugin();
        TestBinarySerialization.plugin.onLoad();
        TestBinarySerialization.plugin.onEnable();
    }

    /**
     * Tear down after all the tests.
     *
     * @throws Exception If something goes wrong.
     */
    @AfterAll
    static void tearDownAfterClass() throws Exception {
        TestBinarySerialization.plugin.onDisable();
        TestBinarySerialization.plugin.onUnload();
        TestBinarySerialization.plugin = null;
        PluginManager.destoryInstance();
        EventManager.destoryInstance();
    }

    /** Test converting to and from binary data. */
    @Test
    void testFromBinary() {
        Node tree = new Node();

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

        final int sizeInBytes = TreeBinarySerialization.calculateTotalSize(tree);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(sizeInBytes);

        Assertions.assertTrue(TreeBinarySerialization.write(tree, outputStream));
        Assertions.assertEquals(sizeInBytes, outputStream.size());

        byte[] dataBuffer = outputStream.toByteArray();

        Assertions.assertEquals(sizeInBytes, dataBuffer.length);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(dataBuffer);

        Optional<Node> parsed = TreeBinarySerialization.read(inputStream);
        Assertions.assertTrue(parsed.isPresent());
        Assertions.assertEquals(tree, parsed.get());
    }
}
