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
package org.mapeditor.util;

import java.awt.image.BufferedImage;

/**
 * Cuts tiles from a tileset image according to a regular rectangular pattern.
 * Supports a variable spacing between tiles and a margin around them.
 *
 * @version 1.4.2
 */
public class BasicTileCutter implements TileCutter {

	private int nextX, nextY;
	private BufferedImage image;
	private final int tileWidth;
	private final int tileHeight;
	private final int tileSpacing;
	private final int tileMargin;

	/**
	 * Constructor for BasicTileCutter.
	 *
	 * @param tileWidth a int.
	 * @param tileHeight a int.
	 * @param tileSpacing a int.
	 * @param tileMargin a int.
	 */
	public BasicTileCutter(int tileWidth, int tileHeight, int tileSpacing,
		int tileMargin) {
		this.tileWidth = tileWidth;
		this.tileHeight = tileHeight;
		this.tileSpacing = tileSpacing;
		this.tileMargin = tileMargin;

		this.reset();
	}

	/**
	 * Returns the number of tiles per row in the tileset image.
	 *
	 * @return the number of tiles per row in the tileset image.
	 */
	public int getColumns() {
		return (this.image.getWidth() - 2 * this.tileMargin + this.tileSpacing)
			/ (this.tileWidth + this.tileSpacing);
	}

	/** {@inheritDoc} */
	@Override
	public String getName() {
		return "Basic";
	}

	/** {@inheritDoc} */
	@Override
	public BufferedImage getNextTile() {
		if (this.nextY + this.tileHeight + this.tileMargin <= this.image
			.getHeight()) {
			BufferedImage tile = this.image.getSubimage(this.nextX, this.nextY,
				this.tileWidth, this.tileHeight);
			this.nextX += this.tileWidth + this.tileSpacing;

			if (this.nextX + this.tileWidth + this.tileMargin > this.image
				.getWidth()) {
				this.nextX = this.tileMargin;
				this.nextY += this.tileHeight + this.tileSpacing;
			}

			return tile;
		}

		return null;
	}

	/** {@inheritDoc} */
	@Override
	public int getTileHeight() {
		return this.tileHeight;
	}

	/**
	 * Returns the margin around the tile images.
	 *
	 * @return the margin around the tile images.
	 */
	public int getTileMargin() {
		return this.tileMargin;
	}

	/**
	 * Returns the spacing between tile images.
	 *
	 * @return the spacing between tile images.
	 */
	public int getTileSpacing() {
		return this.tileSpacing;
	}

	/** {@inheritDoc} */
	@Override
	public int getTileWidth() {
		return this.tileWidth;
	}

	/** {@inheritDoc} */
	@Override
	public final void reset() {
		this.nextX = this.tileMargin;
		this.nextY = this.tileMargin;
	}

	/** {@inheritDoc} */
	@Override
	public void setImage(BufferedImage image) {
		this.image = image;
	}
}
