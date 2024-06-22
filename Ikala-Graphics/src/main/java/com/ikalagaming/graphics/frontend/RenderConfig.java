package com.ikalagaming.graphics.frontend;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Specifies a format for unique handles that specify a particular configuration of the rendering
 * pipeline.
 *
 * <p>The bits of the internal handle format are set up as follows: <br>
 * xasg lbpg ____ t___ w___ ____ ____ ____<br>
 * _ - reserved for future use, but will be zero<br>
 * x - 1 if there was an error, 0 for valid handles<br>
 * a - 1 if the animation stage is enabled<br>
 * s - 1 if the shadow stage is enabled<br>
 * g - 1 if the scene (geometry) stage is enabled<br>
 * l - 1 if the lighting stage is enabled<br>
 * b - 1 if the skybox stage is enabled<br>
 * p - 1 if the (post-process) filter stage is enabled<br>
 * g - 1 if the gui stage is enabled<br>
 * t - 1 if forward rendering for transparency is enabled<br>
 * w - 1 if the scene should render in wireframe<br>
 *
 * @return The handle associated with the specified details.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RenderConfig {

    private static final int ERROR_MASK = 0b1000_0000_0000_0000_0000_0000_0000_0000;
    private static final int ANIMATION_ENABLED_MASK = 0b0100_0000_0000_0000_0000_0000_0000_0000;
    private static final int SHADOW_ENABLED_MASK = 0b0010_0000_0000_0000_0000_0000_0000_0000;
    private static final int SCENE_ENABLED_MASK = 0b0001_0000_0000_0000_0000_0000_0000_0000;
    private static final int LIGHTING_ENABLED_MASK = 0b0000_1000_0000_0000_0000_0000_0000_0000;
    private static final int SKYBOX_ENABLED_MASK = 0b0000_0100_0000_0000_0000_0000_0000_0000;
    private static final int FILTER_ENABLED_MASK = 0b0000_0010_0000_0000_0000_0000_0000_0000;
    private static final int GUI_ENABLED_MASK = 0b0000_0001_0000_0000_0000_0000_0000_0000;
    private static final int TRANSPARENCY_PASS_MASK = 0b0000_0000_0000_1000_0000_0000_0000_0000;
    private static final int SCENE_WIREFRAME_MASK = 0b0000_0000_0000_0000_1000_0000_0000_0000;

    /** The integer representation of this particular configuration. */
    private final int intValue;

    /** Used for constructing render config from scratch. */
    public static class ConfigBuilder {
        private int currentValue = 0;

        /**
         * Enable the animation stage.
         *
         * @return The builder.
         */
        public ConfigBuilder withAnimation() {
            currentValue |= ANIMATION_ENABLED_MASK;
            return this;
        }

        /**
         * Enable scene rendering (including shadows and lighting).
         *
         * @return The builder.
         */
        public ConfigBuilder withScene() {
            currentValue |= SHADOW_ENABLED_MASK;
            currentValue |= SCENE_ENABLED_MASK;
            currentValue |= LIGHTING_ENABLED_MASK;
            return this;
        }

        /**
         * Enable skybox rendering.
         *
         * @return The builder.
         */
        public ConfigBuilder withSkybox() {
            currentValue |= SKYBOX_ENABLED_MASK;
            return this;
        }

        /**
         * Enable the post-processing filter rendering.
         *
         * @return The builder.
         */
        public ConfigBuilder withFilter() {
            currentValue |= FILTER_ENABLED_MASK;
            return this;
        }

        /**
         * Enable GUI rendering.
         *
         * @return The builder.
         */
        public ConfigBuilder withGui() {
            currentValue |= GUI_ENABLED_MASK;
            return this;
        }

        /**
         * Set the scene to render in wireframe instead of normal solid rendering.
         *
         * @return The builder.
         */
        public ConfigBuilder withWireframe() {
            currentValue |= SCENE_WIREFRAME_MASK;
            return this;
        }

        /**
         * Take all options and convert them to an int.
         *
         * @return The final result.
         */
        public int build() {
            return currentValue;
        }
    }

    /**
     * Create a new builder for specifying configuration options.
     *
     * @return The builder.
     */
    public static ConfigBuilder builder() {
        return new ConfigBuilder();
    }

    /**
     * Check if the provided configuration has the error flag set.
     *
     * @param configuration The configuration.
     * @return True if the error flag is set.
     */
    public static boolean hasError(final int configuration) {
        return (configuration & ERROR_MASK) != 0;
    }

    /**
     * Check if the provided configuration has the animation stage enabled.
     *
     * @param configuration The configuration.
     * @return True if the animation stage should run.
     */
    public static boolean hasAnimationStage(final int configuration) {
        return (configuration & ANIMATION_ENABLED_MASK) != 0;
    }

    /**
     * Check if the provided configuration has the shadow stage enabled.
     *
     * @param configuration The configuration.
     * @return True if the shadow stage should run.
     */
    public static boolean hasShadowStage(final int configuration) {
        return (configuration & SHADOW_ENABLED_MASK) != 0;
    }

    /**
     * Check if the provided configuration has the scene stage enabled.
     *
     * @param configuration The configuration.
     * @return True if the scene stage should run.
     */
    public static boolean hasSceneStage(final int configuration) {
        return (configuration & SCENE_ENABLED_MASK) != 0;
    }

    /**
     * Check if the provided configuration has the lighting stage enabled.
     *
     * @param configuration The configuration.
     * @return True if the lighting stage should run.
     */
    public static boolean hasLightingStage(final int configuration) {
        return (configuration & LIGHTING_ENABLED_MASK) != 0;
    }

    /**
     * Check if the provided configuration has the skybox stage enabled.
     *
     * @param configuration The configuration.
     * @return True if the skybox stage should run.
     */
    public static boolean hasSkyboxStage(final int configuration) {
        return (configuration & SKYBOX_ENABLED_MASK) != 0;
    }

    /**
     * Check if the provided configuration has the post-processing filter stage enabled.
     *
     * @param configuration The configuration.
     * @return True if the post-processing filter stage should run.
     */
    public static boolean hasFilterStage(final int configuration) {
        return (configuration & FILTER_ENABLED_MASK) != 0;
    }

    /**
     * Check if the provided configuration has the GUI stage enabled.
     *
     * @param configuration The configuration.
     * @return True if the GUI stage should run.
     */
    public static boolean hasGuiStage(final int configuration) {
        return (configuration & GUI_ENABLED_MASK) != 0;
    }

    /**
     * Check if the provided configuration has the forward rendered transparency pass enabled.
     *
     * @param configuration The configuration.
     * @return True if the forward rendered transparency pass should run.
     */
    public static boolean hasTransparencyPass(final int configuration) {
        return (configuration & TRANSPARENCY_PASS_MASK) != 0;
    }

    /**
     * Check if the scene should render in wireframe mode.
     *
     * @param configuration The configuration.
     * @return True if the scene should be rendered in wireframe mode.
     */
    public static boolean sceneIsWireframe(final int configuration) {
        return (configuration & SCENE_WIREFRAME_MASK) != 0;
    }

    /** Private constructor so this is not instantiated. */
    private RenderConfig() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
