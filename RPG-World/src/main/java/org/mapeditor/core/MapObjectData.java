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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 * While tile layers are very suitable for anything repetitive<br>
 * aligned to the tile grid, sometimes you want to annotate your<br>
 * map with other information, not necessarily aligned to the grid.<br>
 * Hence the objects have their coordinates and size in pixels, but<br>
 * you can still easily align that to the grid when you want to.<br>
 * <br>
 * You generally use objects to add custom information to your tile<br>
 * map, such as spawn points, warps, exits, etc.<br>
 * <br>
 * When the object has a `gid` set, then it is represented by the<br>
 * image of the tile with that global ID. The image alignment<br>
 * currently depends on the map orientation. In orthogonal<br>
 * orientation it's aligned to the bottom-left while in isometric<br>
 * it's aligned to the bottom-center.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
        name = "Object",
        propOrder = {"properties", "point", "ellipse", "polygon", "polyline", "text", "image"})
@Generated(
        value = "com.sun.tools.xjc.Driver",
        comments = "JAXB RI v2.3.7",
        date = "2023-01-22T18:49:20-05:00")
public class MapObjectData {

    /** */
    @XmlElement(required = true, type = Properties.class)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Properties properties;

    /**
     * @since 1.1
     */
    @XmlElement(required = true)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Point point;

    /**
     * @since 0.9
     */
    @XmlElement(required = true)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Ellipse ellipse;

    /** */
    @XmlElement(required = true)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Polygon polygon;

    /** */
    @XmlElement(required = true)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Polyline polyline;

    /**
     * @since 1.0
     */
    @XmlElement(required = true)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Text text;

    /** */
    @XmlElement(required = true)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected ImageData image;

    /**
     * Unique ID of the object. Each object that is placed on a map<br>
     * gets a unique id. Even if an object was deleted, no object<br>
     * gets the same ID. Can not be changed in Tiled Qt.<br>
     * <br>
     *
     * @since 0.11
     */
    @XmlAttribute(name = "id")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Integer id;

    /** The name of the object. An arbitrary string. */
    @XmlAttribute(name = "name")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected String name;

    /** The type of the object. An arbitrary string. */
    @XmlAttribute(name = "type")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected String type;

    /** The x coordinate of the object in pixels. */
    @XmlAttribute(name = "x", required = true)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected double x;

    /** The y coordinate of the object in pixels. */
    @XmlAttribute(name = "y", required = true)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected double y;

    /** The width of the object in pixels (defaults to 0). */
    @XmlAttribute(name = "width")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Double width;

    /** The height of the object in pixels (defaults to 0). */
    @XmlAttribute(name = "height")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Double height;

    /**
     * The rotation of the object in degrees clockwise (defaults to<br>
     * 0).<br>
     * <br>
     *
     * @since 0.10
     */
    @XmlAttribute(name = "rotation", required = true)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected double rotation;

    /** An reference to a tile (optional). */
    @XmlAttribute(name = "gid")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Integer gid;

    /**
     * Whether the object is shown (1) or hidden (0). Defaults to<br>
     * 1.<br>
     * <br>
     *
     * @since 0.9
     */
    @XmlAttribute(name = "visible")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Boolean visible;

    /** A reference to a template file (optional). */
    @XmlAttribute(name = "template")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected String template;

    /**
     * @since 0.9
     * @return possible object is {@link Ellipse }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Ellipse getEllipse() {
        return ellipse;
    }

    /**
     * An reference to a tile (optional).
     *
     * @return possible object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Integer getGid() {
        return gid;
    }

    /**
     * The height of the object in pixels (defaults to 0).
     *
     * @return possible object is {@link Double }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Double getHeight() {
        return height;
    }

    /**
     * Unique ID of the object. Each object that is placed on a map<br>
     * gets a unique id. Even if an object was deleted, no object<br>
     * gets the same ID. Can not be changed in Tiled Qt.<br>
     * <br>
     *
     * @since 0.11
     * @return possible object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Integer getId() {
        return id;
    }

    /**
     * @return possible object is {@link ImageData }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public ImageData getImage() {
        return image;
    }

    /**
     * The name of the object. An arbitrary string.
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
     * @since 1.1
     * @return possible object is {@link Point }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Point getPoint() {
        return point;
    }

    /**
     * @return possible object is {@link Polygon }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Polygon getPolygon() {
        return polygon;
    }

    /**
     * @return possible object is {@link Polyline }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Polyline getPolyline() {
        return polyline;
    }

    /**
     * @return possible object is {@link PropertiesData }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Properties getProperties() {
        return properties;
    }

    /**
     * The rotation of the object in degrees clockwise (defaults to<br>
     * 0).<br>
     * <br>
     *
     * @since 0.10
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public double getRotation() {
        return rotation;
    }

    /**
     * A reference to a template file (optional).
     *
     * @return possible object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public String getTemplate() {
        return template;
    }

    /**
     * @since 1.0
     * @return possible object is {@link Text }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Text getText() {
        return text;
    }

    /**
     * The type of the object. An arbitrary string.
     *
     * @return possible object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public String getType() {
        return type;
    }

    /**
     * The width of the object in pixels (defaults to 0).
     *
     * @return possible object is {@link Double }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Double getWidth() {
        return width;
    }

    /** The x coordinate of the object in pixels. */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public double getX() {
        return x;
    }

    /** The y coordinate of the object in pixels. */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public double getY() {
        return y;
    }

    /**
     * Whether the object is shown (1) or hidden (0). Defaults to<br>
     * 1.<br>
     * <br>
     *
     * @since 0.9
     * @return possible object is {@link Boolean }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Boolean isVisible() {
        return visible;
    }

    /**
     * @since 0.9
     * @param value allowed object is {@link Ellipse }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setEllipse(Ellipse value) {
        ellipse = value;
    }

    /**
     * An reference to a tile (optional).
     *
     * @param value allowed object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setGid(Integer value) {
        gid = value;
    }

    /**
     * The height of the object in pixels (defaults to 0).
     *
     * @param value allowed object is {@link Double }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setHeight(Double value) {
        height = value;
    }

    /**
     * Unique ID of the object. Each object that is placed on a map<br>
     * gets a unique id. Even if an object was deleted, no object<br>
     * gets the same ID. Can not be changed in Tiled Qt.<br>
     * <br>
     *
     * @since 0.11
     * @param value allowed object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setId(Integer value) {
        id = value;
    }

    /**
     * @param value allowed object is {@link ImageData }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setImage(ImageData value) {
        image = value;
    }

    /**
     * The name of the object. An arbitrary string.
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
     * @since 1.1
     * @param value allowed object is {@link Point }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setPoint(Point value) {
        point = value;
    }

    /**
     * @param value allowed object is {@link Polygon }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setPolygon(Polygon value) {
        polygon = value;
    }

    /**
     * @param value allowed object is {@link Polyline }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setPolyline(Polyline value) {
        polyline = value;
    }

    /**
     * @param value allowed object is {@link PropertiesData }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setProperties(Properties value) {
        properties = value;
    }

    /**
     * The rotation of the object in degrees clockwise (defaults to<br>
     * 0).<br>
     * <br>
     *
     * @since 0.10
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setRotation(double value) {
        rotation = value;
    }

    /**
     * A reference to a template file (optional).
     *
     * @param value allowed object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setTemplate(String value) {
        template = value;
    }

    /**
     * @since 1.0
     * @param value allowed object is {@link Text }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setText(Text value) {
        text = value;
    }

    /**
     * The type of the object. An arbitrary string.
     *
     * @param value allowed object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setType(String value) {
        type = value;
    }

    /**
     * Whether the object is shown (1) or hidden (0). Defaults to<br>
     * 1.<br>
     * <br>
     *
     * @since 0.9
     * @param value allowed object is {@link Boolean }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setVisible(Boolean value) {
        visible = value;
    }

    /**
     * The width of the object in pixels (defaults to 0).
     *
     * @param value allowed object is {@link Double }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setWidth(Double value) {
        width = value;
    }

    /** The x coordinate of the object in pixels. */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setX(double value) {
        x = value;
    }

    /** The y coordinate of the object in pixels. */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setY(double value) {
        y = value;
    }
}
