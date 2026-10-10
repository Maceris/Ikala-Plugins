package com.ikalagaming.graphics.scene.lights;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** The kinds of light a plugin can place. The sun and ambient light are scene-wide instead. */
@AllArgsConstructor
@Getter
public enum LightType {
    /** Shines equally in every direction from a point. */
    POINT(0),
    /** Shines in a cone from a point. */
    SPOT(1);

    /**
     * The type's value in the light buffer. Must match the LIGHT_TYPE constants in lights.frag.
     *
     * @return The shader value.
     */
    private final int shaderValue;
}
