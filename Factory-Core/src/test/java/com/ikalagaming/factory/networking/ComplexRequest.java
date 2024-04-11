package com.ikalagaming.factory.networking;

import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.util.List;

/** A request used to test serialization. */
@Getter
@AllArgsConstructor
public class ComplexRequest implements Request {
    @Override
    public void sendUsing(@NonNull Connection connection) {}

    private final boolean boolValue;
    private final byte byteValue;
    private final double doubleValue;
    private final float floatValue;
    private final int intValue;
    private final long longValue;
    private final KVT kvtValue;
    private final short shortValue;
    private final String stringValue;

    private final List<Boolean> boolList;
    private final List<Byte> byteList;
    private final List<Double> doubleList;
    private final List<Float> floatList;
    private final List<Integer> intList;
    private final List<Long> longList;
    private final List<KVT> kvtList;
    private final List<Short> shortList;
    private final List<String> stringList;
}
