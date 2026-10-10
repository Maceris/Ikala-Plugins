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
     * Whether the fog is active.
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
    @NonNull private final Vector3f color;

    /**
     * How much of a pixel the fog leaves visible is {@code e^-(distance * density *
     * DENSITY_SCALE)²}, so at a density of 1 the scene is about a third visible ({@code 1/e}) at 10
     * m and almost gone by 20 m.
     */
    public static final float DENSITY_SCALE = 0.1f;

    /**
     * How thick the fog is, from 0 for none to 1 for a dense fog, see {@link #DENSITY_SCALE}.
     *
     * @param density The density of the fog.
     * @return The density of the fog.
     */
    private float density;

    /** Set up fog. */
    public Fog() {
        active = false;
        color = new Vector3f(1, 1, 1);
        density = 0;
    }
}
