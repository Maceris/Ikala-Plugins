package com.ikalagaming.factory.kvt;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.plugins.PluginManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests the value nodes.
 *
 * @author Ches Burks
 */
class TestNodes {

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
        TestNodes.plugin = new FactoryPlugin();
        TestNodes.plugin.onLoad();
        TestNodes.plugin.onEnable();
    }

    /**
     * Tear down after all the tests.
     *
     * @throws Exception If something goes wrong.
     */
    @AfterAll
    static void tearDownAfterClass() throws Exception {
        TestNodes.plugin.onDisable();
        TestNodes.plugin.onUnload();
        TestNodes.plugin = null;
        PluginManager.destroyInstance();
        EventManager.destroyInstance();
    }

    /** Check if we can store regular values. */
    @SuppressWarnings("unchecked")
    @Test
    void testArrayNodes() {
        KVT tree = new Node();

        tree.addBooleanArray("bool", List.of(true, false));
        tree.addByteArray("byte", List.of((byte) 1));
        tree.addDoubleArray("double", List.of(1.02));
        tree.addFloatArray("float", List.of(2.34f));
        tree.addIntegerArray("int", List.of(3));
        tree.addLongArray("long", List.of(4L));
        tree.addShortArray("short", List.of((short) 5));
        tree.addStringArray("string", List.of("test"));

        Assertions.assertTrue(((List<Boolean>) tree.get("bool")).get(0));
        Assertions.assertEquals(1, (byte) ((List<Byte>) tree.get("byte")).get(0));
        Assertions.assertEquals(1.02, ((List<Double>) tree.get("double")).get(0));
        Assertions.assertEquals(2.34f, (float) ((List<Float>) tree.get("float")).get(0));
        Assertions.assertEquals(3, (int) ((List<Integer>) tree.get("int")).get(0));
        Assertions.assertEquals(4L, (long) ((List<Long>) tree.get("long")).get(0));
        Assertions.assertEquals(5, (short) ((List<Short>) tree.get("short")).get(0));
        Assertions.assertEquals("test", ((List<String>) tree.get("string")).get(0));
    }

    /** Check if we can store regular values. */
    @Test
    void testNestedNodes() {
        KVT tree = new Node();

        tree.add("several.nodes.down");
        Assertions.assertNotNull(tree.get("several"));
        Assertions.assertNotNull(tree.get("several.nodes"));
        Assertions.assertNotNull(tree.get("several.nodes.down"));

        Assertions.assertNotNull(((Node) tree.get("several")).get("nodes"));
        Assertions.assertNotNull(((Node) ((Node) tree.get("several")).get("nodes")).get("down"));

        tree.addInteger("several.up", 2);
        Assertions.assertEquals(2, tree.getInteger("several.up"));

        Assertions.assertNull(tree.getString("several.up.invalid"));

        tree.addDoubleArray("multiple.nodes.values", List.of(3.4, 1.2));
        Assertions.assertEquals(3.4, tree.getDoubleArray("multiple.nodes.values").get(0));
    }

    /** Check that the toString functionality works. */
    @Test
    void testToString() {
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

        final String expected =
                String.format(
                        "{%s}",
                        String.join(
                                ",",
                                "\"annoying name 7!@#$%^ %^&_+'-={}|[]\\\\*()\\\"\":"
                                        + "\"annoying value 7!@#$%^ %^&_+'-={}|[]\\\\*()\\\"\"",
                                "bool:true",
                                "boolArray:[Z;true,false]",
                                "byte:1B",
                                "byteArray:[B;1]",
                                "child1:{child2:{}}",
                                "childArray:[N;]",
                                "double:1.02",
                                "doubleArray:[D;1.02]",
                                "float:2.34F",
                                "floatArray:[F;2.34]",
                                "int:3",
                                "intArray:[I;3]",
                                "long:4L",
                                "longArray:[L;4]",
                                "short:5S",
                                "shortArray:[S;5]",
                                "string:\"test\"",
                                "stringArray:[T;\"test\"]"));

        Assertions.assertEquals(expected, tree.toString());
    }

    /** Check if we can store regular values. */
    @Test
    void testValueNodes() {
        KVT tree = new Node();

        tree.addBoolean("bool", true);
        tree.addByte("byte", (byte) 1);
        tree.addDouble("double", 1.02);
        tree.addFloat("float", 2.34f);
        tree.addInteger("int", 3);
        tree.addLong("long", 4L);
        tree.addShort("short", (short) 5);
        tree.addString("string", "test");

        Assertions.assertTrue((boolean) tree.get("bool"));
        Assertions.assertEquals(1, (byte) tree.get("byte"));
        Assertions.assertEquals(1.02, tree.get("double"));
        Assertions.assertEquals(2.34f, (float) tree.get("float"));
        Assertions.assertEquals(3, (int) tree.get("int"));
        Assertions.assertEquals(4L, (long) tree.get("long"));
        Assertions.assertEquals(5, (short) tree.get("short"));
        Assertions.assertEquals("test", tree.get("string"));
    }

    /** Check if we can fetch regular values. */
    @Test
    void testValueNodesGetters() {
        KVT tree = new Node();

        tree.addBoolean("bool", true);
        tree.addByte("byte", (byte) 1);
        tree.addDouble("double", 1.02);
        tree.addFloat("float", 2.34f);
        tree.addInteger("int", 3);
        tree.addLong("long", 4L);
        tree.addShort("short", (short) 5);
        tree.addString("string", "test");

        Assertions.assertTrue(tree.getBoolean("bool"));
        Assertions.assertEquals(1, tree.getByte("byte"));
        Assertions.assertEquals(1.02, tree.getDouble("double"));
        Assertions.assertEquals(2.34f, tree.getFloat("float"));
        Assertions.assertEquals(3, tree.getInteger("int"));
        Assertions.assertEquals(4L, tree.getLong("long"));
        Assertions.assertEquals(5, tree.getShort("short"));
        Assertions.assertEquals("test", tree.getString("string"));

        Assertions.assertFalse(tree.getBoolean("DoesNotExist"));
        Assertions.assertNull(tree.getBooleanArray("DoesNotExist"));

        Assertions.assertEquals(0, tree.getByte("DoesNotExist"));
        Assertions.assertNull(tree.getByteArray("DoesNotExist"));
        Assertions.assertEquals(0, tree.getDouble("DoesNotExist"));
        Assertions.assertNull(tree.getDoubleArray("DoesNotExist"));
        Assertions.assertEquals(0, tree.getFloat("DoesNotExist"));
        Assertions.assertNull(tree.getFloatArray("DoesNotExist"));
        Assertions.assertEquals(0, tree.getInteger("DoesNotExist"));
        Assertions.assertNull(tree.getIntegerArray("DoesNotExist"));
        Assertions.assertEquals(0, tree.getLong("DoesNotExist"));
        Assertions.assertNull(tree.getLongArray("DoesNotExist"));
        Assertions.assertEquals(0, tree.getShort("DoesNotExist"));
        Assertions.assertNull(tree.getShortArray("DoesNotExist"));
        Assertions.assertNull(tree.getString("DoesNotExist"));
        Assertions.assertNull(tree.getStringArray("DoesNotExist"));
    }
}
