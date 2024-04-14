package com.ikalagaming.factory.networking.serialization;

import static org.junit.jupiter.api.Assertions.*;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.networking.ComplexRequest;
import com.ikalagaming.factory.networking.SimpleRequest;
import com.ikalagaming.localization.Localization;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.ResourceBundle;

class TestTreeRequestSerialization {

    private static MockedStatic<FactoryPlugin> fakePlugin;

    @BeforeAll
    static void setUpBeforeClass() {
        var bundle =
                ResourceBundle.getBundle(
                        "com.ikalagaming.factory.strings", Localization.getLocale());

        fakePlugin = Mockito.mockStatic(FactoryPlugin.class);
        fakePlugin.when(FactoryPlugin::getResourceBundle).thenReturn(bundle);
    }

    @AfterAll
    static void tearDownAfterClass() {
        fakePlugin.close();
    }

    private boolean expectedBoolValue;
    private byte expectedByteValue;
    private double expectedDoubleValue;
    private float expectedFloatValue;
    private int expectedIntValue;
    private long expectedLongValue;
    private KVT expectedKvtValue;
    private short expectedShortValue;

    private String expectedStringValue;
    private ComplexRequest.SampleEnum expectedEnumValue;
    private List<Boolean> expectedBoolList;
    private List<Byte> expectedByteList;
    private List<Double> expectedDoubleList;
    private List<Float> expectedFloatList;
    private List<Integer> expectedIntList;
    private List<Long> expectedLongList;
    private List<KVT> expectedKvtList;
    private List<Short> expectedShortList;

    private List<String> expectedStringList;

    private List<ComplexRequest.SampleEnum> expectedEnumList;

    private ComplexRequest expectedRequest;

    @BeforeEach
    void setup() {
        expectedBoolValue = true;
        expectedByteValue = 1;
        expectedDoubleValue = 2.01;
        expectedFloatValue = 3.14f;
        expectedIntValue = 5;
        expectedLongValue = 6;
        expectedKvtValue = new Node();
        expectedKvtValue.addInteger("age", 5);
        expectedKvtValue.addStringArray("steps", List.of("penguin", "dog", "whale-dog"));
        expectedShortValue = 7;
        expectedStringValue = "lotomation:ice";
        expectedEnumValue = ComplexRequest.SampleEnum.COUCH;

        expectedBoolList = List.of(true, false, true);
        expectedByteList = List.of((byte) 3, (byte) 3, (byte) 3, (byte) 4, (byte) 42);
        expectedDoubleList = List.of(4.2, .01, Double.NaN);
        expectedFloatList = List.of(1.0f, Float.NaN, -4.3f);
        expectedIntList = List.of(9, 0, 1);
        expectedLongList = List.of(Long.MAX_VALUE, 123345645L);
        KVT node1 = new Node();
        node1.addIntegerArray("timers", List.of(4, 5, 6, -1));
        KVT node2 = new Node();
        node2.addFloat("elapsedTime", 0.003f);
        expectedKvtList = List.of(node1, node2);
        expectedShortList = List.of((short) 1234, (short) -1);
        expectedStringList = List.of("Ice", "Fire", "Water", "Florida");
        expectedEnumList =
                List.of(ComplexRequest.SampleEnum.COUCH, ComplexRequest.SampleEnum.CHAIR);

        expectedRequest = new ComplexRequest();

        expectedRequest.setBoolValue(expectedBoolValue);
        expectedRequest.setByteValue(expectedByteValue);
        expectedRequest.setDoubleValue(expectedDoubleValue);
        expectedRequest.setFloatValue(expectedFloatValue);
        expectedRequest.setIntValue(expectedIntValue);
        expectedRequest.setLongValue(expectedLongValue);
        expectedRequest.setKvtValue(expectedKvtValue);
        expectedRequest.setShortValue(expectedShortValue);
        expectedRequest.setStringValue(expectedStringValue);
        expectedRequest.setEnumValue(expectedEnumValue);

        expectedRequest.setBoolList(expectedBoolList);
        expectedRequest.setByteList(expectedByteList);
        expectedRequest.setDoubleList(expectedDoubleList);
        expectedRequest.setFloatList(expectedFloatList);
        expectedRequest.setIntList(expectedIntList);
        expectedRequest.setLongList(expectedLongList);
        expectedRequest.setKvtList(expectedKvtList);
        expectedRequest.setShortList(expectedShortList);
        expectedRequest.setStringList(expectedStringList);
        expectedRequest.setEnumList(expectedEnumList);
    }

    @Test
    void testComplexSerialization() {
        var encoded = TreeRequestSerialization.fromObject(expectedRequest);
        assertNotNull(encoded);
        assertTrue(encoded.isPresent());

        var decoded = TreeRequestSerialization.toObject(encoded.get(), ComplexRequest.class);
        assertNotNull(decoded);
        assertTrue(decoded.isPresent());

        var result = decoded.get();
        assertEquals(expectedBoolValue, result.isBoolValue());
        assertEquals(expectedByteValue, result.getByteValue());
        assertEquals(expectedDoubleValue, result.getDoubleValue());
        assertEquals(expectedFloatValue, result.getFloatValue());
        assertEquals(expectedIntValue, result.getIntValue());
        assertEquals(expectedLongValue, result.getLongValue());
        assertEquals(expectedKvtValue, result.getKvtValue());
        assertEquals(expectedShortValue, result.getShortValue());
        assertEquals(expectedStringValue, result.getStringValue());
        assertEquals(expectedEnumValue, result.getEnumValue());

        assertArrayEquals(expectedBoolList.toArray(), result.getBoolList().toArray());
        assertArrayEquals(expectedByteList.toArray(), result.getByteList().toArray());
        assertArrayEquals(expectedDoubleList.toArray(), result.getDoubleList().toArray());
        assertArrayEquals(expectedFloatList.toArray(), result.getFloatList().toArray());
        assertArrayEquals(expectedIntList.toArray(), result.getIntList().toArray());
        assertArrayEquals(expectedLongList.toArray(), result.getLongList().toArray());
        assertArrayEquals(expectedKvtList.toArray(), result.getKvtList().toArray());
        assertArrayEquals(expectedShortList.toArray(), result.getShortList().toArray());
        assertArrayEquals(expectedStringList.toArray(), result.getStringList().toArray());
        assertArrayEquals(expectedEnumList.toArray(), result.getEnumList().toArray());
    }

    @Test
    void testNullSerialization() {
        var empty = new ComplexRequest();
        var encoded = TreeRequestSerialization.fromObject(empty);
        assertNotNull(encoded);
        assertTrue(encoded.isPresent());

        var decoded = TreeRequestSerialization.toObject(encoded.get(), ComplexRequest.class);
        assertNotNull(decoded);
        assertTrue(decoded.isPresent());

        var result = decoded.get();
        assertFalse(result.isBoolValue());
        assertEquals((byte) 0, result.getByteValue());
        assertEquals(0d, result.getDoubleValue());
        assertEquals(0f, result.getFloatValue());
        assertEquals(0, result.getIntValue());
        assertEquals(0L, result.getLongValue());
        assertNull(result.getKvtValue());
        assertEquals((short) 0, result.getShortValue());
        assertNull(result.getStringValue());
        assertNull(result.getEnumValue());

        assertNull(result.getBoolList());
        assertNull(result.getByteList());
        assertNull(result.getDoubleList());
        assertNull(result.getFloatList());
        assertNull(result.getIntList());
        assertNull(result.getLongList());
        assertNull(result.getKvtList());
        assertNull(result.getShortList());
        assertNull(result.getStringList());
        assertNull(result.getEnumList());
    }

    @Test
    void testSimpleSerialization() {
        var original = new SimpleRequest();

        var encoded = TreeRequestSerialization.fromObject(original);
        assertNotNull(encoded);
        assertTrue(encoded.isPresent());

        var decoded = TreeRequestSerialization.toObject(encoded.get(), SimpleRequest.class);
        assertNotNull(decoded);
        assertTrue(decoded.isPresent());
    }
}
