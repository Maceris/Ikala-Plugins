package com.ikalagaming.factory.render;

import com.ikalagaming.factory.world.WorldTime;
import com.ikalagaming.graphics.scene.PhysicalSky;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;

import lombok.NonNull;
import org.joml.Vector3f;

/**
 * Lights the world by its clock: the physical sky in place of a skybox, a sun that moves with the
 * time of day, and auto exposure so the eye adapts from noon to dusk. The sun's light fades out as
 * it reaches the horizon; at night only the sky's own light is left. Main thread only.
 */
public final class SkyLight {

    /** The sun's color. The physical sky reddens it near the horizon. */
    private static final Vector3f SUN_COLOR = new Vector3f(1, 1, 1);

    /** The sun's intensity when it is well above the horizon. */
    static final float SUN_INTENSITY = 3;

    /** How high the sun is, as the up part of its direction, when its light starts to show. */
    static final float FADE_START = -0.02f;

    /** How high the sun is when its light is full. */
    static final float FADE_END = 0.06f;

    /** The scene being lit. */
    private final Scene scene;

    /** The sun, changed in place as time passes. */
    private final DirectionalLight sun =
            new DirectionalLight(new Vector3f(SUN_COLOR), new Vector3f(0, -1, 0), 0);

    /** What the scene had before, to put back. */
    private final PhysicalSky previousSky;

    private final DirectionalLight previousSun;
    private final boolean previousAutoExposure;

    /**
     * Light a scene by the world's clock.
     *
     * @param scene The scene.
     */
    public SkyLight(@NonNull Scene scene) {
        this.scene = scene;
        previousSky = scene.getPhysicalSky();
        previousSun = scene.getSceneLights().getDirLight();
        previousAutoExposure = scene.isAutoExposure();
        scene.setPhysicalSky(new PhysicalSky());
        scene.getSceneLights().setDirLight(sun);
        scene.setAutoExposure(true);
    }

    /**
     * Move the sun to a time.
     *
     * @param ticks The world's clock, in ticks with a fraction.
     */
    public void update(double ticks) {
        final double[] toward = WorldTime.towardSun(ticks);
        // The light travels away from the sun
        sun.getDirection().set((float) -toward[0], (float) -toward[1], (float) -toward[2]);
        sun.setIntensity(SUN_INTENSITY * fade((float) toward[1]));
    }

    /**
     * How much of the sun's light shows at a height above the horizon.
     *
     * @param up The up part of the direction toward the sun.
     * @return From 0 below the horizon to 1 once it is clear of it.
     */
    static float fade(float up) {
        final float t = Math.clamp((up - FADE_START) / (FADE_END - FADE_START), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /** Put the scene's light back as it was. */
    public void restore() {
        scene.setPhysicalSky(previousSky);
        scene.getSceneLights().setDirLight(previousSun);
        scene.setAutoExposure(previousAutoExposure);
    }
}
