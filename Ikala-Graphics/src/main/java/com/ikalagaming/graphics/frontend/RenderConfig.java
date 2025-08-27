package com.ikalagaming.graphics.frontend;

/**
 * Specifies a format for unique handles that specify a particular configuration of the rendering
 * pipeline.
 *
 * <p>The bits of the internal handle format are set up as follows: <br>
 * xasg bpg_ ____ t___ w___ ____ ____ ____<br>
 * _ - reserved for future use, but will be zero<br>
 * x - 1 if there was an error, 0 for valid handles<br>
 * a - 1 if the animation stage is enabled<br>
 * s - 1 if the shadow stage is enabled<br>
 * g - 1 if the scene (geometry+lighting) stage is enabled<br>
 * b - 1 if the skybox stage is enabled<br>
 * p - 1 if the (post-process) filter stage is enabled<br>
 * g - 1 if the gui stage is enabled<br>
 * t - 1 if forward rendering for transparency is enabled<br>
 * w - 1 if the scene should render in wireframe<br>
 */
public class RenderConfig {

    private static final int ERROR_MASK = 0b1000_0000_0000_0000_0000_0000_0000_0000;
    private static final int ANIMATION_ENABLED_MASK = 0b0100_0000_0000_0000_0000_0000_0000_0000;
    private static final int SHADOW_ENABLED_MASK = 0b0010_0000_0000_0000_0000_0000_0000_0000;
    private static final int SCENE_ENABLED_MASK = 0b0001_0000_0000_0000_0000_0000_0000_0000;
    private static final int SKYBOX_ENABLED_MASK = 0b0000_1000_0000_0000_0000_0000_0000_0000;
    private static final int FILTER_ENABLED_MASK = 0b0000_0100_0000_0000_0000_0000_0000_0000;
    private static final int GUI_ENABLED_MASK = 0b0000_0010_0000_0000_0000_0000_0000_0000;
    private static final int TRANSPARENCY_PASS_MASK = 0b0000_0000_0000_1000_0000_0000_0000_0000;
    private static final int SCENE_WIREFRAME_MASK = 0b0000_0000_0000_0000_1000_0000_0000_0000;

    /** Used for constructing render config from scratch. */
    public static class ConfigBuilder {
        private int currentValue;

        ConfigBuilder(int initialValue) {
            currentValue = initialValue;
        }

        /**
         * Toggle the animation stage.
         *
         * @return The builder.
         */
        public ConfigBuilder toggleAnimation() {
            currentValue ^= ANIMATION_ENABLED_MASK;
            return this;
        }

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
         * Disable the animation stage.
         *
         * @return The builder.
         */
        public ConfigBuilder withoutAnimation() {
            currentValue &= (~ANIMATION_ENABLED_MASK);
            return this;
        }

        /**
         * Toggle scene rendering (including shadows and lighting).
         *
         * @return The builder.
         */
        public ConfigBuilder toggleScene() {
            currentValue ^= SHADOW_ENABLED_MASK;
            currentValue ^= SCENE_ENABLED_MASK;
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
            return this;
        }

        /**
         * Disable scene rendering (including shadows and lighting).
         *
         * @return The builder.
         */
        public ConfigBuilder withoutScene() {
            currentValue &= (~SHADOW_ENABLED_MASK);
            currentValue &= (~SCENE_ENABLED_MASK);
            return this;
        }

        /**
         * Toggle skybox rendering.
         *
         * @return The builder.
         */
        public ConfigBuilder toggleSkybox() {
            currentValue ^= SKYBOX_ENABLED_MASK;
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
         * Disable skybox rendering.
         *
         * @return The builder.
         */
        public ConfigBuilder withoutSkybox() {
            currentValue &= (~SKYBOX_ENABLED_MASK);
            return this;
        }

        /**
         * Toggle the post-processing filter rendering.
         *
         * @return The builder.
         */
        public ConfigBuilder toggleFilter() {
            currentValue ^= FILTER_ENABLED_MASK;
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
         * Disable the post-processing filter rendering.
         *
         * @return The builder.
         */
        public ConfigBuilder withoutFilter() {
            currentValue &= (~FILTER_ENABLED_MASK);
            return this;
        }

        /**
         * Toggle GUI rendering.
         *
         * @return The builder.
         */
        public ConfigBuilder toggleGui() {
            currentValue ^= GUI_ENABLED_MASK;
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
         * Disable GUI rendering.
         *
         * @return The builder.
         */
        public ConfigBuilder withoutGui() {
            currentValue &= (~GUI_ENABLED_MASK);
            return this;
        }

        /**
         * Toggle between rendering the scene in wireframe and normal solid rendering.
         *
         * @return The builder.
         */
        public ConfigBuilder toggleWireframe() {
            currentValue ^= SCENE_WIREFRAME_MASK;
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
         * Set the scene to render normal solid rendering instead of wireframe.
         *
         * @return The builder.
         */
        public ConfigBuilder withoutWireframe() {
            currentValue &= (~SCENE_WIREFRAME_MASK);
            return this;
        }

        /**
         * Take all options and convert them to an int.
         *
         * @return The final result.
         */
        public int build() {
            checkError();
            return currentValue;
        }

        private void checkError() {
            if ((currentValue & SCENE_ENABLED_MASK) == 0
                    && (currentValue & (ANIMATION_ENABLED_MASK | SHADOW_ENABLED_MASK)) != 0) {
                currentValue |= ERROR_MASK;
            }
        }
    }

    /**
     * Create a new builder for specifying configuration options.
     *
     * @return The builder.
     */
    public static ConfigBuilder builder() {
        return new ConfigBuilder(0);
    }

    /**
     * Create a new builder for specifying configuration options, given initial setup.
     *
     * @param initialValue The config value to start with.
     * @return The builder.
     */
    public static ConfigBuilder builder(int initialValue) {
        return new ConfigBuilder(initialValue);
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
