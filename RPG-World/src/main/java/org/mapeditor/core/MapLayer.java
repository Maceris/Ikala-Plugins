/*-
 * #%L
 * This file is part of libtiled-java.
 * %%
 * Copyright (C) 2004 - 2020 Thorbjørn Lindeijer <thorbjorn@lindeijer.nl>
 * Copyright (C) 2004 - 2020 Adam Turk <aturk@biggeruniverse.com>
 * Copyright (C) 2016 - 2020 Mike Thomas <mikepthomas@outlook.com>
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.mapeditor.core;

import java.awt.Rectangle;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/**
 * A layer of a map.
 *
 * @see org.mapeditor.core.Map
 * @version 1.4.2
 */
@XmlAccessorType(XmlAccessType.NONE)
public class MapLayer extends LayerData implements Cloneable {

	/**
	 * MIRROR_HORIZONTAL
	 */
	public static final int MIRROR_HORIZONTAL = 1;
	/**
	 * MIRROR_VERTICAL
	 */
	public static final int MIRROR_VERTICAL = 2;

	/**
	 * ROTATE_90
	 */
	public static final int ROTATE_90 = 90;
	/**
	 * ROTATE_180
	 */
	public static final int ROTATE_180 = 180;
	/**
	 * ROTATE_270
	 */
	public static final int ROTATE_270 = 270;

	protected Map map;

	/**
	 * Constructor for MapLayer.
	 */
	public MapLayer() {
		this.setMap(null);
	}

	/**
	 * Constructor for MapLayer.
	 *
	 * @param w width in tiles
	 * @param h height in tiles
	 */
	public MapLayer(int w, int h) {
		this(new Rectangle(0, 0, w, h));
	}

	/**
	 * @param map the map this layer is part of
	 */
	MapLayer(Map map) {
		this();
		this.setMap(map);
	}

	/**
	 * Constructor for MapLayer.
	 *
	 * @param map the map this layer is part of
	 * @param w width in tiles
	 * @param h height in tiles
	 */
	public MapLayer(Map map, int w, int h) {
		this(w, h);
		this.setMap(map);
	}

	/**
	 * Constructor for MapLayer.
	 *
	 * @param r a {@link java.awt.Rectangle} object.
	 */
	public MapLayer(Rectangle r) {
		this();
		this.setBounds(r);
	}

	/**
	 * {@inheritDoc}
	 *
	 * Creates a copy of this layer.
	 *
	 * @see Object#clone
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		MapLayer clone = (MapLayer) super.clone();

		// Create a new bounds object
		clone.setBounds(new Rectangle(this.getBounds()));
		clone.properties = this.properties.clone();

		return clone;
	}

	/**
	 * A convenience method to check if a point in tile-space is within the
	 * layer boundaries.
	 *
	 * @param x the x-coordinate of the point
	 * @param y the y-coordinate of the point
	 * @return <code>true</code> if the point (x,y) is within the layer
	 *         boundaries, <code>false</code> otherwise.
	 */
	public boolean contains(int x, int y) {
		return this.getBounds().contains(x, y);
	}

	/**
	 * Returns the layer bounds in tiles.
	 *
	 * @return the layer bounds in tiles
	 */
	public Rectangle getBounds() {
		return new Rectangle(this.x == null ? 0 : this.x,
			this.y == null ? 0 : this.y, this.width, this.height);
	}

	/**
	 * Assigns the layer bounds in tiles to the given rectangle.
	 *
	 * @param rect the rectangle to which the layer bounds are assigned
	 */
	public void getBounds(Rectangle rect) {
		rect.x = this.x;
		rect.y = this.y;
		rect.width = this.width;
		rect.height = this.height;
	}

	/**
	 * getMap.
	 *
	 * @return a {@link org.mapeditor.core.Map} object.
	 */
	public Map getMap() {
		return this.map;
	}

	/** {@inheritDoc} */
	@Override
	public Properties getProperties() {
		if (this.properties == null) {
			this.properties = new Properties();
		}
		return super.getProperties();
	}

	/**
	 * resize.
	 *
	 * @param width the new width of the layer
	 * @param height the new height of the layer
	 * @param dx the shift in x direction
	 * @param dy the shift in y direction
	 */
	public void resize(int width, int height, int dx, int dy) {
		// TODO: Translate contained objects by the change of origin
	}

	/**
	 * Sets the bounds (in tiles) to the specified Rectangle.
	 *
	 * @param bounds a {@link java.awt.Rectangle} object.
	 */
	protected void setBounds(Rectangle bounds) {
		this.x = bounds.x;
		this.y = bounds.y;
		this.width = bounds.width;
		this.height = bounds.height;
	}

	/**
	 * Sets the map this layer is part of.
	 *
	 * @param map the Map object
	 */
	public final void setMap(Map map) {
		this.map = map;
	}

	/**
	 * Sets the offset of this map layer. The offset is a distance by which to
	 * shift this layer from the origin of the map.
	 *
	 * @param x x offset in tiles
	 * @param y y offset in tiles
	 */
	public void setOffset(int x, int y) {
		this.x = x;
		this.y = y;
	}

	/**
	 * Performs a linear translation of this layer by (<i>x, y</i>).
	 *
	 * @param x distance over x axis
	 * @param y distance over y axis
	 */
	public void translate(int x, int y) {
		this.x += x;
		this.y += y;
	}
}
