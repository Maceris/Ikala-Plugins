package com.ikalagaming.graphics.frontend;

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

    /** Private constructor so this is not instantiated. */
    private RenderConfig() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
