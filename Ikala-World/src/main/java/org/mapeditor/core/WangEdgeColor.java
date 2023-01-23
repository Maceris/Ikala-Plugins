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
 * A color that can be used to define the edge of a Wang tile.
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WangEdgeColor")
@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
	date = "2023-01-22T18:49:20-05:00")
public class WangEdgeColor {

	/**
	 * The name of this color.
	 * 
	 */
	@XmlAttribute(name = "name")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected String name;
	/**
	 * The color in `#RRGGBB` format (example: `#c17d11`).
	 * 
	 */
	@XmlAttribute(name = "color")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected String color;
	/**
	 * The tile ID of the tile representing this color.
	 * 
	 */
	@XmlAttribute(name = "tile")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Integer tile;
	/**
	 * The relative probability that this color is chosen over<br>
	 * others in case of multiple options.
	 * 
	 */
	@XmlAttribute(name = "probability")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Integer probability;

	/**
	 * The color in `#RRGGBB` format (example: `#c17d11`).
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
	 * The name of this color.
	 * 
	 * @return possible object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public String getName() {
		return this.name;
	}

	/**
	 * The relative probability that this color is chosen over<br>
	 * others in case of multiple options.
	 * 
	 * @return possible object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Integer getProbability() {
		return this.probability;
	}

	/**
	 * The tile ID of the tile representing this color.
	 * 
	 * @return possible object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Integer getTile() {
		return this.tile;
	}

	/**
	 * The color in `#RRGGBB` format (example: `#c17d11`).
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
	 * The name of this color.
	 * 
	 * @param value allowed object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setName(String value) {
		this.name = value;
	}

	/**
	 * The relative probability that this color is chosen over<br>
	 * others in case of multiple options.
	 * 
	 * @param value allowed object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setProbability(Integer value) {
		this.probability = value;
	}

	/**
	 * The tile ID of the tile representing this color.
	 * 
	 * @param value allowed object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setTile(Integer value) {
		this.tile = value;
	}

}
