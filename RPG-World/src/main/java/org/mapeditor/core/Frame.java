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
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Frame")
@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
	date = "2023-01-22T18:49:20-05:00")
public class Frame {

	/**
	 * The local ID of a tile within the parent tileset.
	 * 
	 */
	@XmlAttribute(name = "tileid")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Integer tileid;
	/**
	 * How long (in milliseconds) this frame should be displayed<br>
	 * before advancing to the next frame.
	 * 
	 */
	@XmlAttribute(name = "duration")
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	protected Integer duration;

	/**
	 * How long (in milliseconds) this frame should be displayed<br>
	 * before advancing to the next frame.
	 * 
	 * @return possible object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Integer getDuration() {
		return this.duration;
	}

	/**
	 * The local ID of a tile within the parent tileset.
	 * 
	 * @return possible object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public Integer getTileid() {
		return this.tileid;
	}

	/**
	 * How long (in milliseconds) this frame should be displayed<br>
	 * before advancing to the next frame.
	 * 
	 * @param value allowed object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setDuration(Integer value) {
		this.duration = value;
	}

	/**
	 * The local ID of a tile within the parent tileset.
	 * 
	 * @param value allowed object is {@link Integer }
	 * 
	 */
	@Generated(value = "com.sun.tools.xjc.Driver", comments = "JAXB RI v2.3.7",
		date = "2023-01-22T18:49:20-05:00")
	public void setTileid(Integer value) {
		this.tileid = value;
	}

}