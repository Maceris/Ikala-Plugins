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
import javax.xml.bind.annotation.XmlValue;

/**
 * Used to mark an object as a text object. Contains the actual<br>
 * text as character data.
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Text", propOrder = {"value"})
@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
	date = "2023-01-22T18:49:20-05:00")
public class Text {

	/**
	 * 
	 */
	@XmlValue
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected String value;
	/**
	 * The font family used (default: "sand-serif")
	 * 
	 */
	@XmlAttribute(name = "fontfamily")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected String fontfamily;
	/**
	 * The size of the font in pixels (not using points,<br>
	 * because other sizes in the TMX format are also using<br>
	 * pixels) (default: 16)
	 * 
	 */
	@XmlAttribute(name = "pixelsize")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Integer pixelsize;
	/**
	 * Whether word wrapping is enabled (1) or disabled<br>
	 * (0). Defaults to 0.
	 * 
	 */
	@XmlAttribute(name = "wrap")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Boolean wrap;
	/**
	 * Color of the text in `#AARRGGBB` or `#RRGGBB` format<br>
	 * (default: #000000)
	 * 
	 */
	@XmlAttribute(name = "color")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected String color;
	/**
	 * Whether the font is bold (1) or not (0). Defaults to<br>
	 * 0.
	 * 
	 */
	@XmlAttribute(name = "bold")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Boolean bold;
	/**
	 * Whether the font is italic (1) or not (0). Defaults<br>
	 * to 0.
	 * 
	 */
	@XmlAttribute(name = "italic")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Boolean italic;
	/**
	 * Whether a line should be drawn below the text (1) or<br>
	 * not (0). Defaults to 0.
	 * 
	 */
	@XmlAttribute(name = "underline")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Boolean underline;
	/**
	 * Whether a line should be drawn through the text (1)<br>
	 * or not (0). Defaults to 0.
	 * 
	 */
	@XmlAttribute(name = "strikeout")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Boolean strikeout;
	/**
	 * Whether kerning should be used while rendering the<br>
	 * text (1) or not (0). Default to 1.
	 * 
	 */
	@XmlAttribute(name = "kerning")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Boolean kerning;
	/**
	 * Horizontal alignment of the text within the object<br>
	 * (`left` (default), `center` or `right`)
	 * 
	 */
	@XmlAttribute(name = "halign")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected HorizontalAlignment halign;
	/**
	 * Vertical alignment of the text within the object<br>
	 * (`left` (default), `center` or `right`)
	 * 
	 */
	@XmlAttribute(name = "valign")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected VerticalAlignment valign;

	/**
	 * Color of the text in `#AARRGGBB` or `#RRGGBB` format<br>
	 * (default: #000000)
	 * 
	 * @return possible object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public String getColor() {
		return this.color;
	}

	/**
	 * The font family used (default: "sand-serif")
	 * 
	 * @return possible object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public String getFontfamily() {
		return this.fontfamily;
	}

	/**
	 * Horizontal alignment of the text within the object<br>
	 * (`left` (default), `center` or `right`)
	 * 
	 * @return possible object is {@link HorizontalAlignment }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public HorizontalAlignment getHalign() {
		return this.halign;
	}

	/**
	 * The size of the font in pixels (not using points,<br>
	 * because other sizes in the TMX format are also using<br>
	 * pixels) (default: 16)
	 * 
	 * @return possible object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Integer getPixelsize() {
		return this.pixelsize;
	}

	/**
	 * Vertical alignment of the text within the object<br>
	 * (`left` (default), `center` or `right`)
	 * 
	 * @return possible object is {@link VerticalAlignment }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public VerticalAlignment getValign() {
		return this.valign;
	}

	/**
	 * 
	 * @return possible object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public String getValue() {
		return this.value;
	}

	/**
	 * Whether the font is bold (1) or not (0). Defaults to<br>
	 * 0.
	 * 
	 * @return possible object is {@link Boolean }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Boolean isBold() {
		return this.bold;
	}

	/**
	 * Whether the font is italic (1) or not (0). Defaults<br>
	 * to 0.
	 * 
	 * @return possible object is {@link Boolean }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Boolean isItalic() {
		return this.italic;
	}

	/**
	 * Whether kerning should be used while rendering the<br>
	 * text (1) or not (0). Default to 1.
	 * 
	 * @return possible object is {@link Boolean }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Boolean isKerning() {
		return this.kerning;
	}

	/**
	 * Whether a line should be drawn through the text (1)<br>
	 * or not (0). Defaults to 0.
	 * 
	 * @return possible object is {@link Boolean }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Boolean isStrikeout() {
		return this.strikeout;
	}

	/**
	 * Whether a line should be drawn below the text (1) or<br>
	 * not (0). Defaults to 0.
	 * 
	 * @return possible object is {@link Boolean }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Boolean isUnderline() {
		return this.underline;
	}

	/**
	 * Whether word wrapping is enabled (1) or disabled<br>
	 * (0). Defaults to 0.
	 * 
	 * @return possible object is {@link Boolean }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Boolean isWrap() {
		return this.wrap;
	}

	/**
	 * Whether the font is bold (1) or not (0). Defaults to<br>
	 * 0.
	 * 
	 * @param value allowed object is {@link Boolean }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setBold(Boolean value) {
		this.bold = value;
	}

	/**
	 * Color of the text in `#AARRGGBB` or `#RRGGBB` format<br>
	 * (default: #000000)
	 * 
	 * @param value allowed object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setColor(String value) {
		this.color = value;
	}

	/**
	 * The font family used (default: "sand-serif")
	 * 
	 * @param value allowed object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setFontfamily(String value) {
		this.fontfamily = value;
	}

	/**
	 * Horizontal alignment of the text within the object<br>
	 * (`left` (default), `center` or `right`)
	 * 
	 * @param value allowed object is {@link HorizontalAlignment }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setHalign(HorizontalAlignment value) {
		this.halign = value;
	}

	/**
	 * Whether the font is italic (1) or not (0). Defaults<br>
	 * to 0.
	 * 
	 * @param value allowed object is {@link Boolean }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setItalic(Boolean value) {
		this.italic = value;
	}

	/**
	 * Whether kerning should be used while rendering the<br>
	 * text (1) or not (0). Default to 1.
	 * 
	 * @param value allowed object is {@link Boolean }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setKerning(Boolean value) {
		this.kerning = value;
	}

	/**
	 * The size of the font in pixels (not using points,<br>
	 * because other sizes in the TMX format are also using<br>
	 * pixels) (default: 16)
	 * 
	 * @param value allowed object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setPixelsize(Integer value) {
		this.pixelsize = value;
	}

	/**
	 * Whether a line should be drawn through the text (1)<br>
	 * or not (0). Defaults to 0.
	 * 
	 * @param value allowed object is {@link Boolean }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setStrikeout(Boolean value) {
		this.strikeout = value;
	}

	/**
	 * Whether a line should be drawn below the text (1) or<br>
	 * not (0). Defaults to 0.
	 * 
	 * @param value allowed object is {@link Boolean }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setUnderline(Boolean value) {
		this.underline = value;
	}

	/**
	 * Vertical alignment of the text within the object<br>
	 * (`left` (default), `center` or `right`)
	 * 
	 * @param value allowed object is {@link VerticalAlignment }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setValign(VerticalAlignment value) {
		this.valign = value;
	}

	/**
	 * 
	 * @param value allowed object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setValue(String value) {
		this.value = value;
	}

	/**
	 * Whether word wrapping is enabled (1) or disabled<br>
	 * (0). Defaults to 0.
	 * 
	 * @param value allowed object is {@link Boolean }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setWrap(Boolean value) {
		this.wrap = value;
	}

}