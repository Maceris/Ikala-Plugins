package com.ikalagaming.factory.networking;

import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.networking.base.Request;

import lombok.Data;

import java.util.List;

/** A request with maximal contents used to test serialization. */
@Data
public class ComplexRequest implements Request {

    public enum SampleEnum {
        COUCH,
        CHAIR
    }

    private boolean boolValue;
    private byte byteValue;
    private double doubleValue;
    private float floatValue;
    private int intValue;
    private long longValue;
    private KVT kvtValue;
    private short shortValue;
    private String stringValue;
    private SampleEnum enumValue;

    private List<Boolean> boolList;
    private List<Byte> byteList;
    private List<Double> doubleList;
    private List<Float> floatList;
    private List<Integer> intList;
    private List<Long> longList;
    private List<KVT> kvtList;
    private List<Short> shortList;
    private List<String> stringList;
    private List<SampleEnum> enumList;
}
