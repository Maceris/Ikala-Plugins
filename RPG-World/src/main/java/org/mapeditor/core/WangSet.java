//
// This file was generated by the Eclipse Implementation of JAXB, v2.3.7
// See https://eclipse-ee4j.github.io/jaxb-ri
// Any modifications to this file will be lost upon recompilation of the source
// schema.
// Generated on: 2023.01.22 at 06:49:20 PM EST
//

package org.mapeditor.core;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Defines a list of corner colors and a list of edge colors, and<br>
 * any number of Wang tiles using these colors.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
        name = "WangSet",
        propOrder = {"wangcornercolor", "wangedgecolor", "wangtile"})
@Generated(
        value = "com.sun.tools.xjc.Driver",
        comments = "JAXB RI v2.3.7",
        date = "2023-01-22T18:49:20-05:00")
public class WangSet {

    /** */
    @XmlElement(required = true)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected List<WangCornerColor> wangcornercolor;

    /** */
    @XmlElement(required = true)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected List<WangEdgeColor> wangedgecolor;

    /** */
    @XmlElement(required = true)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected List<WangTile> wangtile;

    /** The name of the Wang set. */
    @XmlAttribute(name = "name")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected String name;

    /** The tile ID of the tile representing this Wang set. */
    @XmlAttribute(name = "tile")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Integer tile;

    /**
     * The name of the Wang set.
     *
     * @return possible object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public String getName() {
        return name;
    }

    /**
     * The tile ID of the tile representing this Wang set.
     *
     * @return possible object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Integer getTile() {
        return tile;
    }

    /** */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public List<WangCornerColor> getWangcornercolor() {
        if (wangcornercolor == null) {
            wangcornercolor = new ArrayList<>();
        }
        return wangcornercolor;
    }

    /** */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public List<WangEdgeColor> getWangedgecolor() {
        if (wangedgecolor == null) {
            wangedgecolor = new ArrayList<>();
        }
        return wangedgecolor;
    }

    /** */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public List<WangTile> getWangtile() {
        if (wangtile == null) {
            wangtile = new ArrayList<>();
        }
        return wangtile;
    }

    /**
     * The name of the Wang set.
     *
     * @param value allowed object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setName(String value) {
        name = value;
    }

    /**
     * The tile ID of the tile representing this Wang set.
     *
     * @param value allowed object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setTile(Integer value) {
        tile = value;
    }
}
