package com.ikalagaming.graphics.scene;

/** How a skybox texture lays the sky out, see {@link Scene#setSkyboxTexture}. */
public enum SkyboxLayout {
    /**
     * Six square faces, four sides in a row through the middle third, -x, -z, +x and +z from the
     * left, the top above -z and the bottom below it, as the skybox's cube is mapped. Drawn as it
     * is stored, as art.
     */
    CUBE_FACES,
    /**
     * Longitude across, from -z at the middle with +x a quarter to the right, latitude down, from
     * straight up to straight down, as HDR environment maps are. Its light is drawn as bright as it
     * lights the scene, scaled to the ambient light, so the sky seen and the light it gives match.
     */
    EQUIRECTANGULAR
}
