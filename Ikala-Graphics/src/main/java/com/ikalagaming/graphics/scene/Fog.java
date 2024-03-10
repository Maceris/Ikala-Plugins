/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.scene;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector3f;

/** Fog configuration. */
@Getter
@Setter
@AllArgsConstructor
public class Fog {
    /**
     * Whether or not the fog is active.
     *
     * @param active If the fog should be active.
     * @return Whether the fog is active.
     */
    private boolean active;

    /**
     * The base color of the fog.
     *
     * @param color The color of the fog.
     * @return The color of the fog.
     */
    @NonNull private Vector3f color;

    /**
     * How thick the fog is, modeled as 1/(e^(distance*density)).
     *
     * @param density The density of the fog.
     * @return The density of the fog.
     */
    private float density;

    /** Set up fog. */
    public Fog() {
        active = false;
        color = new Vector3f();
    }

    /**
     * Set the color.
     *
     * @param r The red component.
     * @param g The green component.
     * @param b The blue component.
     */
    public void setColor(float r, float g, float b) {
        color.set(r, g, b);
    }
}
