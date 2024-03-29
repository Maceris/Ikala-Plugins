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
 * A generic interface to a class that implements tile cutting behaviour.
 *
 * @version 1.4.2
 */
public interface TileCutter {

    /**
     * Returns the name of this tile cutter.
     *
     * @return the name of this tile cutter
     */
    String getName();

    /**
     * Retrieves the next tile image.
     *
     * @return the next tile image, or <code>null</code> when no more tile images are available
     */
    BufferedImage getNextTile();

    /**
     * Returns the default tile height of tiles cut by this cutter.
     *
     * @return the default tile height of tiles cut by this cutter.
     */
    int getTileHeight();

    /**
     * Returns the default tile width of tiles cut by this cutter.
     *
     * @return the default tile width of tiles cut by this cutter.
     */
    int getTileWidth();

    /**
     * Resets the tile cutter so that the next call to <code>getNextTile</code> will return the
     * first tile.
     */
    void reset();

    /**
     * Sets the image that this cutter should cut in tile images.
     *
     * @param image the image that this cutter should cut
     */
    void setImage(BufferedImage image);
}
