//
// This file was generated by the Eclipse Implementation of JAXB, v2.3.7
// See https://eclipse-ee4j.github.io/jaxb-ri
// Any modifications to this file will be lost upon recompilation of the source
// schema.
// Generated on: 2023.01.22 at 06:49:20 PM EST
//

package org.mapeditor.core;

import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;

/** */
@XmlType(name = "StaggerAxis")
@XmlEnum
@Generated(
        value = "com.sun.tools.xjc.Driver",
        comments = "JAXB RI v2.3.7",
        date = "2023-01-22T18:49:20-05:00")
public enum StaggerAxis {

    /** */
    @XmlEnumValue("x")
    X("x"),

    /** */
    @XmlEnumValue("y")
    Y("y");

    public static StaggerAxis fromValue(String v) {
        for (StaggerAxis c : StaggerAxis.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

    private final String value;

    StaggerAxis(String v) {
        value = v;
    }

    public String value() {
        return value;
    }
}
