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

import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/**
 * An object occupying an {@link org.mapeditor.core.ObjectGroup}.
 *
 * @version 1.4.2
 */
@XmlAccessorType(XmlAccessType.NONE)
public class MapObject extends MapObjectData implements Cloneable {

    private ObjectGroup objectGroup;
    private Shape shape = new Rectangle();
    private String imageSource;
    private Image image;
    private Image scaledImage;
    private Tile tile;
    private boolean flipHorizontal;
    private boolean flipVertical;
    private boolean flipDiagonal;
    private String clazz;

    /** Constructor for MapObject. */
    public MapObject() {
        properties = new Properties();
        name = "";
        type = "";
        imageSource = "";
        flipHorizontal = false;
        flipVertical = false;
    }

    /**
     * Constructor for MapObject.
     *
     * @param x a double.
     * @param y a double.
     * @param width a double.
     * @param height a double.
     * @param rotation a double.
     */
    public MapObject(double x, double y, double width, double height, double rotation) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
    }

    /** {@inheritDoc} */
    @Override
    public Object clone() throws CloneNotSupportedException {
        MapObject clone = (MapObject) super.clone();
        clone.properties = properties.clone();
        return clone;
    }

    /**
     * Getter for the field <code>bounds</code>.
     *
     * @return a {@link java.awt.geom.Rectangle2D.Double} object.
     */
    public Rectangle2D.Double getBounds() {
        return new Rectangle2D.Double(x, y, width, height);
    }

    /**
     * Get the value of the class that the map object is classified as.
     *
     * @return The class in the map file.
     */
    public String getClassValue() {
        return clazz;
    }

    public boolean getFlipDiagonal() {
        return flipDiagonal;
    }

    public boolean getFlipHorizontal() {
        return flipHorizontal;
    }

    public boolean getFlipVertical() {
        return flipVertical;
    }

    /**
     * Returns the image to be used when drawing this object. This image is scaled to the size of
     * the object.
     *
     * @param zoom the requested zoom level of the image
     * @return the image to be used when drawing this object
     */
    public Image getImage(double zoom) {
        if (image == null) {
            return null;
        }

        final int zoomedWidth = (int) (getWidth() * zoom);
        final int zoomedHeight = (int) (getHeight() * zoom);

        if (scaledImage == null
                || scaledImage.getWidth(null) != zoomedWidth
                || scaledImage.getHeight(null) != zoomedHeight) {
            scaledImage = image.getScaledInstance(zoomedWidth, zoomedHeight, Image.SCALE_SMOOTH);
        }

        return scaledImage;
    }

    /**
     * Getter for the field <code>imageSource</code>.
     *
     * @return a {@link java.lang.String} object.
     */
    public String getImageSource() {
        return imageSource;
    }

    /**
     * Getter for the field <code>objectGroup</code>.
     *
     * @return the object group this object is part of
     */
    public ObjectGroup getObjectGroup() {
        return objectGroup;
    }

    /**
     * Getter for the field <code>shape</code>.
     *
     * @return a {@link java.awt.Shape} object.
     */
    public Shape getShape() {
        return shape;
    }

    /**
     * Getter for the field <code>tile</code>.
     *
     * @return a {@link org.mapeditor.core.Tile} object.
     */
    public Tile getTile() {
        return tile;
    }

    /**
     * Setter for the field <code>bounds</code>.
     *
     * @param bounds a {@link java.awt.geom.Rectangle2D.Double} object.
     */
    public void setBounds(Rectangle2D.Double bounds) {
        x = bounds.getX();
        y = bounds.getY();
        width = bounds.getWidth();
        height = bounds.getHeight();
    }

    /**
     * Setter for the field <code>class</code>.
     *
     * @param clazz a String object.
     */
    public void setClass(String clazz) {
        this.clazz = clazz;
    }

    public void setFlipDiagonal(boolean flip) {
        flipDiagonal = flip;
    }

    public void setFlipHorizontal(boolean flip) {
        flipHorizontal = flip;
    }

    public void setFlipVertical(boolean flip) {
        flipVertical = flip;
    }

    /**
     * Setter for the field <code>imageSource</code>.
     *
     * @param source a {@link java.lang.String} object.
     */
    public void setImageSource(String source) {
        if (imageSource.equals(source)) {
            return;
        }

        imageSource = source;

        // Attempt to read the image
        if (imageSource.length() > 0) {
            try {
                image = ImageIO.read(new File(imageSource));
            } catch (IOException e) {
                image = null;
            }
        } else {
            image = null;
        }

        scaledImage = null;
    }

    /**
     * Sets the object group this object is part of. Should only be called by the object group.
     *
     * @param objectGroup the object group this object is part of
     */
    public void setObjectGroup(ObjectGroup objectGroup) {
        this.objectGroup = objectGroup;
    }

    /**
     * Setter for the field <code>shape</code>.
     *
     * @param shape a {@link java.awt.Shape} object.
     */
    public void setShape(Shape shape) {
        this.shape = shape;
    }

    /**
     * Setter for the field <code>tile</code>.
     *
     * @param tile a {@link org.mapeditor.core.Tile} object.
     */
    public void setTile(Tile tile) {
        this.tile = tile;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return type + " (" + getX() + "," + getY() + ")";
    }

    /**
     * translate.
     *
     * @param dx a double.
     * @param dy a double.
     */
    public void translate(double dx, double dy) {
        x += dx;
        y += dy;
    }
}
