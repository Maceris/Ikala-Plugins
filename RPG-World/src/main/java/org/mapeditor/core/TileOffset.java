//
// This file was generated by the Eclipse Implementation of JAXB, v2.3.7
// See https://eclipse-ee4j.github.io/jaxb-ri
// Any modifications to this file will be lost upon recompilation of the source
// schema.
// Generated on: 2023.01.22 at 06:49:20 PM EST
//

package org.mapeditor.core;

import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

/**
 * This element is used to specify an offset in pixels, to be<br>
 * applied when drawing a tile from the related tileset. When not<br>
 * present, no offset is applied.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TileOffset")
@Generated(
        value = "com.sun.tools.xjc.Driver",
        comments = "JAXB RI v2.3.7",
        date = "2023-01-22T18:49:20-05:00")
public class TileOffset {

    /** Horizontal offset in pixels */
    @XmlAttribute(name = "x")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Integer x;

    /** Vertical offset in pixels (positive is down) */
    @XmlAttribute(name = "y")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Integer y;

    /**
     * Horizontal offset in pixels
     *
     * @return possible object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Integer getX() {
        return x;
    }

    /**
     * Vertical offset in pixels (positive is down)
     *
     * @return possible object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Integer getY() {
        return y;
    }

    /**
     * Horizontal offset in pixels
     *
     * @param value allowed object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setX(Integer value) {
        x = value;
    }

    /**
     * Vertical offset in pixels (positive is down)
     *
     * @param value allowed object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setY(Integer value) {
        y = value;
    }
}
