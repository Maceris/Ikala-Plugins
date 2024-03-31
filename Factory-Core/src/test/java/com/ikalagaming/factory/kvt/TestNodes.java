package com.ikalagaming.factory.kvt;

import static org.junit.jupiter.api.Assertions.*;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.plugins.PluginManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Tests the value nodes.
 *
 * @author Ches Burks
 */
class TestNodes {

    private static FactoryPlugin plugin;

    /** Set up before all the tests. */
    @BeforeAll
    static void setUpBeforeClass() {
        EventManager.getInstance();
        PluginManager.getInstance();
        TestNodes.plugin = new FactoryPlugin();
        TestNodes.plugin.onLoad();
        TestNodes.plugin.onEnable();
    }

    /** Tear down after all the tests. */
    @AfterAll
    static void tearDownAfterClass() {
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

        assertTrue(((List<Boolean>) tree.get("bool")).get(0));
        assertEquals(1, (byte) ((List<Byte>) tree.get("byte")).get(0));
        assertEquals(1.02, ((List<Double>) tree.get("double")).get(0));
        assertEquals(2.34f, (float) ((List<Float>) tree.get("float")).get(0));
        assertEquals(3, (int) ((List<Integer>) tree.get("int")).get(0));
        assertEquals(4L, (long) ((List<Long>) tree.get("long")).get(0));
        assertEquals(5, (short) ((List<Short>) tree.get("short")).get(0));
        assertEquals("test", ((List<String>) tree.get("string")).get(0));
    }

    @Test
    void testGetKeys() {
        KVT tree = new Node();

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

        var expectedKeys =
                Stream.of(
                                "bool",
                                "byte",
                                "double",
                                "float",
                                "int",
                                "long",
                                "short",
                                "string",
                                "child1",
                                "boolArray",
                                "byteArray",
                                "doubleArray",
                                "floatArray",
                                "intArray",
                                "longArray",
                                "shortArray",
                                "stringArray",
                                "childArray")
                        .sorted()
                        .toArray();

        assertArrayEquals(expectedKeys, tree.getKeys().toArray());
    }

    @Test
    void testGetTypes() {
        KVT tree = new Node();

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

        assertEquals(Optional.of(NodeType.BOOLEAN), tree.getType("bool"));
        assertEquals(Optional.of(NodeType.BYTE), tree.getType("byte"));
        assertEquals(Optional.of(NodeType.DOUBLE), tree.getType("double"));
        assertEquals(Optional.of(NodeType.FLOAT), tree.getType("float"));
        assertEquals(Optional.of(NodeType.INTEGER), tree.getType("int"));
        assertEquals(Optional.of(NodeType.LONG), tree.getType("long"));
        assertEquals(Optional.of(NodeType.SHORT), tree.getType("short"));
        assertEquals(Optional.of(NodeType.STRING), tree.getType("string"));
        assertEquals(Optional.of(NodeType.NODE), tree.getType("child1"));
        assertEquals(Optional.of(NodeType.NODE), tree.getType("child1.child2"));

        assertEquals(Optional.of(NodeType.BOOLEAN_ARRAY), tree.getType("boolArray"));
        assertEquals(Optional.of(NodeType.BYTE_ARRAY), tree.getType("byteArray"));
        assertEquals(Optional.of(NodeType.DOUBLE_ARRAY), tree.getType("doubleArray"));
        assertEquals(Optional.of(NodeType.FLOAT_ARRAY), tree.getType("floatArray"));
        assertEquals(Optional.of(NodeType.INTEGER_ARRAY), tree.getType("intArray"));
        assertEquals(Optional.of(NodeType.LONG_ARRAY), tree.getType("longArray"));
        assertEquals(Optional.of(NodeType.SHORT_ARRAY), tree.getType("shortArray"));
        assertEquals(Optional.of(NodeType.STRING_ARRAY), tree.getType("stringArray"));
        assertEquals(Optional.of(NodeType.NODE_ARRAY), tree.getType("childArray"));
    }

    /** Check if we can store regular values. */
    @Test
    void testNestedNodes() {
        KVT tree = new Node();

        tree.add("several.nodes.down");
        assertNotNull(tree.get("several"));
        assertNotNull(tree.get("several.nodes"));
        assertNotNull(tree.get("several.nodes.down"));

        assertNotNull(((Node) tree.get("several")).get("nodes"));
        assertNotNull(((Node) ((Node) tree.get("several")).get("nodes")).get("down"));

        tree.addInteger("several.up", 2);
        assertEquals(2, tree.getInteger("several.up"));

        assertNull(tree.getString("several.up.invalid"));

        tree.addDoubleArray("multiple.nodes.values", List.of(3.4, 1.2));
        assertEquals(3.4, tree.getDoubleArray("multiple.nodes.values").get(0));
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

        assertEquals(expected, tree.toString());
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

        assertTrue((boolean) tree.get("bool"));
        assertEquals(1, (byte) tree.get("byte"));
        assertEquals(1.02, tree.get("double"));
        assertEquals(2.34f, (float) tree.get("float"));
        assertEquals(3, (int) tree.get("int"));
        assertEquals(4L, (long) tree.get("long"));
        assertEquals(5, (short) tree.get("short"));
        assertEquals("test", tree.get("string"));
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

        assertTrue(tree.getBoolean("bool"));
        assertEquals(1, tree.getByte("byte"));
        assertEquals(1.02, tree.getDouble("double"));
        assertEquals(2.34f, tree.getFloat("float"));
        assertEquals(3, tree.getInteger("int"));
        assertEquals(4L, tree.getLong("long"));
        assertEquals(5, tree.getShort("short"));
        assertEquals("test", tree.getString("string"));

        assertFalse(tree.getBoolean("DoesNotExist"));
        assertNull(tree.getBooleanArray("DoesNotExist"));

        assertEquals(0, tree.getByte("DoesNotExist"));
        assertNull(tree.getByteArray("DoesNotExist"));
        assertEquals(0, tree.getDouble("DoesNotExist"));
        assertNull(tree.getDoubleArray("DoesNotExist"));
        assertEquals(0, tree.getFloat("DoesNotExist"));
        assertNull(tree.getFloatArray("DoesNotExist"));
        assertEquals(0, tree.getInteger("DoesNotExist"));
        assertNull(tree.getIntegerArray("DoesNotExist"));
        assertEquals(0, tree.getLong("DoesNotExist"));
        assertNull(tree.getLongArray("DoesNotExist"));
        assertEquals(0, tree.getShort("DoesNotExist"));
        assertNull(tree.getShortArray("DoesNotExist"));
        assertNull(tree.getString("DoesNotExist"));
        assertNull(tree.getStringArray("DoesNotExist"));
    }
}
