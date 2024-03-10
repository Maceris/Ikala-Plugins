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

import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/**
 * The core class for our tiles.
 *
 * @version 1.4.2
 */
@XmlAccessorType(XmlAccessType.NONE)
@Slf4j
public class Tile extends TileData {

    private BufferedImage image;
    private String source;
    private TileSet tileset;

    /** Constructor for Tile. */
    public Tile() {
        id = -1;
    }

    /**
     * Copy constructor
     *
     * @param t tile to copy
     */
    public Tile(Tile t) {
        tileset = t.tileset;

        Properties tileProperties = t.properties;
        if (tileProperties != null) {
            try {
                properties = tileProperties.clone();
            } catch (CloneNotSupportedException ex) {
                log.error("Clone not supported", ex);
            }
        }
    }

    /**
     * Constructor for Tile.
     *
     * @param set a {@link org.mapeditor.core.TileSet} object.
     */
    public Tile(TileSet set) {
        tileset = set;
    }

    /**
     * getHeight.
     *
     * @return a int.
     */
    public int getHeight() {
        if (image != null) {
            return image.getHeight();
        }
        return 0;
    }

    /**
     * Returns the tile image for this Tile.
     *
     * @return a {@link java.awt.image.BufferedImage} object.
     */
    public BufferedImage getImage() {
        return image;
    }

    /** {@inheritDoc} */
    @Override
    public Properties getProperties() {
        if (properties == null) {
            properties = new Properties();
        }
        return super.getProperties();
    }

    /**
     * Getter for the field <code>source</code>.
     *
     * @return a {@link java.lang.String} object.
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the {@link org.mapeditor.core.TileSet} that this tile is part of.
     *
     * @return TileSet
     */
    public TileSet getTileSet() {
        return tileset;
    }

    /**
     * getWidth.
     *
     * @return a int.
     */
    public int getWidth() {
        if (image != null) {
            return image.getWidth();
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sets the id of the tile as long as it is at least 0.
     */
    @Override
    public void setId(Integer value) {
        if (value >= 0) {
            id = value;
        }
    }

    /**
     * Sets the image of the tile.
     *
     * @param image the new image of the tile
     */
    public void setImage(BufferedImage image) {
        this.image = image;
    }

    /**
     * Sets the URI path of the external source of this tile set. By setting this, the set is
     * implied to be external in all other operations.
     *
     * @param source a URI of the tileset image file
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * Sets the parent tileset for a tile.
     *
     * @param set a {@link org.mapeditor.core.TileSet} object.
     */
    public void setTileSet(TileSet set) {
        tileset = set;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Tile " + id + " (" + getWidth() + "x" + getHeight() + ")";
    }
}
