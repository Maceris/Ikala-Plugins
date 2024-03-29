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
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;

/**
 * Note that it is not currently possible to use Tiled to create<br>
 * maps with embedded image data, even though the TMX format<br>
 * supports this. It is possible to create such maps using<br>
 * `libtiled` (Qt/C++) or<br>
 * [tmxlib](https://pypi.python.org/pypi/tmxlib) (Python).
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
        name = "Image",
        propOrder = {})
@Generated(
        value = "com.sun.tools.xjc.Driver",
        comments = "JAXB RI v2.3.7",
        date = "2023-01-22T18:49:20-05:00")
public class ImageData {

    /**
     * @since 0.9
     */
    @XmlElement(required = true)
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Data data;

    /**
     * Used for embedded images, in combination with a `data` child<br>
     * element. Valid values are file extensions like `png`, `gif`,<br>
     * `jpg`, `bmp`, etc.<br>
     * <br>
     *
     * @since 0.9
     */
    @XmlAttribute(name = "format")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected String format;

    /**
     * Used by some versions of Tiled Java.<br>
     * <br>
     *
     * @deprecated and unsupported by Tiled Qt.
     */
    @XmlAttribute(name = "id")
    @Deprecated
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Integer id;

    /**
     * The reference to the tileset image file<br>
     * (Tiled supports most common image formats).<br>
     * <br>
     *
     * @since 0.9
     */
    @XmlAttribute(name = "source")
    @XmlSchemaType(name = "anyURI")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected String source;

    /**
     * Defines a specific color that is treated as transparent<br>
     * (example value: "#FF00FF" for magenta). Up until Tiled 0.12,<br>
     * this value is written out without a `#` but this is planned<br>
     * to change.
     */
    @XmlAttribute(name = "trans")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected String trans;

    /**
     * The image width in pixels (optional, used for tile index<br>
     * correction when the image changes)
     */
    @XmlAttribute(name = "width")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Integer width;

    /** The image height in pixels (optional) */
    @XmlAttribute(name = "height")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Integer height;

    /**
     * @since 0.9
     * @return possible object is {@link Data }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Data getData() {
        return data;
    }

    /**
     * Used for embedded images, in combination with a `data` child<br>
     * element. Valid values are file extensions like `png`, `gif`,<br>
     * `jpg`, `bmp`, etc.<br>
     * <br>
     *
     * @since 0.9
     * @return possible object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public String getFormat() {
        return format;
    }

    /**
     * The image height in pixels (optional)
     *
     * @return possible object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Integer getHeight() {
        return height;
    }

    /**
     * Used by some versions of Tiled Java.<br>
     * <br>
     *
     * @deprecated and unsupported by Tiled Qt.
     * @return possible object is {@link Integer }
     */
    @Deprecated
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Integer getId() {
        return id;
    }

    /**
     * The reference to the tileset image file<br>
     * (Tiled supports most common image formats).<br>
     * <br>
     *
     * @since 0.9
     * @return possible object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public String getSource() {
        return source;
    }

    /**
     * Defines a specific color that is treated as transparent<br>
     * (example value: "#FF00FF" for magenta). Up until Tiled 0.12,<br>
     * this value is written out without a `#` but this is planned<br>
     * to change.
     *
     * @return possible object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public String getTrans() {
        return trans;
    }

    /**
     * The image width in pixels (optional, used for tile index<br>
     * correction when the image changes)
     *
     * @return possible object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Integer getWidth() {
        return width;
    }

    /**
     * @since 0.9
     * @param value allowed object is {@link Data }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setData(Data value) {
        data = value;
    }

    /**
     * Used for embedded images, in combination with a `data` child<br>
     * element. Valid values are file extensions like `png`, `gif`,<br>
     * `jpg`, `bmp`, etc.<br>
     * <br>
     *
     * @since 0.9
     * @param value allowed object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setFormat(String value) {
        format = value;
    }

    /**
     * The image height in pixels (optional)
     *
     * @param value allowed object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setHeight(Integer value) {
        height = value;
    }

    /**
     * Used by some versions of Tiled Java.<br>
     * <br>
     *
     * @deprecated and unsupported by Tiled Qt.
     * @param value allowed object is {@link Integer }
     */
    @Deprecated
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setId(Integer value) {
        id = value;
    }

    /**
     * The reference to the tileset image file<br>
     * (Tiled supports most common image formats).<br>
     * <br>
     *
     * @since 0.9
     * @param value allowed object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setSource(String value) {
        source = value;
    }

    /**
     * Defines a specific color that is treated as transparent<br>
     * (example value: "#FF00FF" for magenta). Up until Tiled 0.12,<br>
     * this value is written out without a `#` but this is planned<br>
     * to change.
     *
     * @param value allowed object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setTrans(String value) {
        trans = value;
    }

    /**
     * The image width in pixels (optional, used for tile index<br>
     * correction when the image changes)
     *
     * @param value allowed object is {@link Integer }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setWidth(Integer value) {
        width = value;
    }
}
