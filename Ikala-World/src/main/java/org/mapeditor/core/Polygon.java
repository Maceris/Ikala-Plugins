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
 * Each `polygon` object is made up of a space-delimited list of<br>
 * x,y coordinates. The origin for these coordinates is the<br>
 * location of the parent `object`. By default, the first point is<br>
 * created as 0,0 denoting that the point will originate exactly<br>
 * where the `object` is placed.
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Polygon")
@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
	date = "2023-01-22T18:49:20-05:00")
public class Polygon {

	/**
	 * A list of x,y coordinates in pixels.
	 * 
	 */
	@XmlAttribute(name = "points")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected String points;

	/**
	 * A list of x,y coordinates in pixels.
	 * 
	 * @return possible object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public String getPoints() {
		return this.points;
	}

	/**
	 * A list of x,y coordinates in pixels.
	 * 
	 * @param value allowed object is {@link String }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setPoints(String value) {
		this.points = value;
	}

}