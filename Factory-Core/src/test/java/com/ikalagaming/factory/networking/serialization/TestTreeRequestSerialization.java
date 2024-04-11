package com.ikalagaming.factory.networking.serialization;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.networking.ComplexRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class TestTreeRequestSerialization {

    private boolean expectedBoolValue;
    private byte expectedByteValue;
    private double expectedDoubleValue;
    private float expectedFloatValue;
    private int expectedIntValue;
    private long expectedLongValue;
    private KVT expectedKvtValue;
    private short expectedShortValue;
    private String expectedStringValue;

    private List<Boolean> expectedBoolList;
    private List<Byte> expectedByteList;
    private List<Double> expectedDoubleList;
    private List<Float> expectedFloatList;
    private List<Integer> expectedIntList;
    private List<Long> expectedLongList;
    private List<KVT> expectedKvtList;
    private List<Short> expectedShortList;
    private List<String> expectedStringList;

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

        expectedRequest =
                new ComplexRequest(
                        expectedBoolValue,
                        expectedByteValue,
                        expectedDoubleValue,
                        expectedFloatValue,
                        expectedIntValue,
                        expectedLongValue,
                        expectedKvtValue,
                        expectedShortValue,
                        expectedStringValue,
                        expectedBoolList,
                        expectedByteList,
                        expectedDoubleList,
                        expectedFloatList,
                        expectedIntList,
                        expectedLongList,
                        expectedKvtList,
                        expectedShortList,
                        expectedStringList);
    }

    @Test
    void testSerialization() {
        var encoded = TreeRequestSerialization.fromObject(expectedRequest);
        assertNotNull(encoded);
        assertTrue(encoded.isPresent());
    }
}
