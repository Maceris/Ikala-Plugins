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
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * When no encoding or compression is given, the tiles are stored<br>
 * as individual XML `tile` elements. Next to that, the easiest<br>
 * format to parse is the "csv" (comma separated values) format.<br>
 * <br>
 * The base64-encoded and optionally compressed layer data is<br>
 * somewhat more complicated to parse. First you need to<br>
 * base64-decode it, then you may need to decompress it. Now you<br>
 * have an array of bytes, which should be interpreted as an array<br>
 * of unsigned 32-bit integers using little-endian byte ordering.<br>
 * <br>
 * Whatever format you choose for your layer data, you will always<br>
 * end up with so called "global tile IDs" (gids). They are global,<br>
 * since they may refer to a tile from any of the tilesets used by<br>
 * the map. In order to find out from which tileset the tile is you<br>
 * need to find the tileset with the highest `firstgid` that is<br>
 * still lower or equal than the gid. The tilesets are always<br>
 * stored with increasing `firstgid`s.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
        name = "Data",
        propOrder = {"value"})
@Generated(
        value = "com.sun.tools.xjc.Driver",
        comments = "JAXB RI v2.3.7",
        date = "2023-01-22T18:49:20-05:00")
public class Data {

    /** */
    @XmlValue
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlSchemaType(name = "token")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected String value;

    /**
     * The encoding used to encode the tile layer data.<br>
     * When used, it can be "base64" and "csv" at the<br>
     * moment.
     */
    @XmlAttribute(name = "encoding")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Encoding encoding;

    /**
     * The compression used to compress the tile layer<br>
     * data. Tiled Qt supports "gzip" and "zlib".
     */
    @XmlAttribute(name = "compression")
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    protected Compression compression;

    /**
     * The compression used to compress the tile layer<br>
     * data. Tiled Qt supports "gzip" and "zlib".
     *
     * @return possible object is {@link Compression }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Compression getCompression() {
        return compression;
    }

    /**
     * The encoding used to encode the tile layer data.<br>
     * When used, it can be "base64" and "csv" at the<br>
     * moment.
     *
     * @return possible object is {@link Encoding }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public Encoding getEncoding() {
        return encoding;
    }

    /**
     * @return possible object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public String getValue() {
        return value;
    }

    /**
     * The compression used to compress the tile layer<br>
     * data. Tiled Qt supports "gzip" and "zlib".
     *
     * @param value allowed object is {@link Compression }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setCompression(Compression value) {
        compression = value;
    }

    /**
     * The encoding used to encode the tile layer data.<br>
     * When used, it can be "base64" and "csv" at the<br>
     * moment.
     *
     * @param value allowed object is {@link Encoding }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setEncoding(Encoding value) {
        encoding = value;
    }

    /**
     * @param value allowed object is {@link String }
     */
    @Generated(
            value = "com.sun.tools.xjc.Driver",
            comments = "JAXB RI v2.3.7",
            date = "2023-01-22T18:49:20-05:00")
    public void setValue(String value) {
        this.value = value;
    }
}
