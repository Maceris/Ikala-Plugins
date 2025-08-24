package com.ikalagaming.graphics;

/**
 * Common definitions of the fields that we need when interacting with shaders. Located centrally so
 * there is an official source of all uniforms shaders need, as well as documentation for what each
 * uniform is used for.
 *
 * @author Ches Burks
 */
public class ShaderUniforms {

    /**
     * Post-processing filter variables.
     *
     * @author Ches Burks
     */
    public static class Filter {
        /** The texture containing the rendered scene. */
        public static final String SCREEN_TEXTURE = "screenTexture";

        /** Private constructor so this class is not instantiated. */
        private Filter() {}
    }

    /**
     * GUI shader variables.
     *
     * @author Ches Burks
     */
    public static class GUI {
        /** The scaling of the UI. */
        public static final String SCALE = "scale";

        /** Private constructor so this class is not instantiated. */
        private GUI() {}
    }

    /**
     * Light shader variables.
     *
     * @author Ches Burks
     */
    public static class Light {
        /**
         * The ambient light.
         *
         * @author Ches Burks
         */
        public static class AmbientLight {
            /** The color of the light. */
            public static final String COLOR = "color";

            /** The intensity, measured in candela per square meter (cd/m^2). */
            public static final String INTENSITY = "intensity";

            /** Private constructor so this class is not instantiated. */
            private AmbientLight() {}
        }

        /**
         * A cascade shadow.
         *
         * @author Ches Burks
         */
        public static class CascadeShadow {
            /** The combined projection and view matrix. */
            public static final String PROJECTION_VIEW_MATRIX = "projViewMatrix";

            /** The distance to the split. */
            public static final String SPLIT_DISTANCE = "splitDistance";

            /** Private constructor so this class is not instantiated. */
            private CascadeShadow() {}
        }

        /**
         * A directional light.
         *
         * @author Ches Burks
         */
        public static class DirectionalLight {
            /** The color of the light. */
            public static final String COLOR = "color";

            /** The direction that the light is coming from. */
            public static final String DIRECTION = "direction";

            /** The intensity, measured in candela per square meter (cd/m^2). */
            public static final String INTENSITY = "intensity";

            /** Private constructor so this class is not instantiated. */
            private DirectionalLight() {}
        }

        /**
         * Fog to render over the scene.
         *
         * @author Ches Burks
         */
        public static class Fog {
            /** The base color of the fog. */
            public static final String COLOR = "color";

            /** How dense the fog is. */
            public static final String DENSITY = "density";

            /** Whether the fog is enabled, 1 if enabled. */
            public static final String ENABLED = "enabled";

            /** Private constructor so this class is not instantiated. */
            private Fog() {}
        }

        /**
         * A sampler that measures the proportion of incident light that is reflected away from a
         * surface at a given point.
         */
        public static final String ALBEDO_SAMPLER = "albedoSampler";

        /**
         * The ambient light that affects every fragment the same way.
         *
         * @see AmbientLight
         */
        public static final String AMBIENT_LIGHT = "ambientLight";

        /** The cascade shadows. */
        public static final String CASCADE_SHADOWS = "cascadeshadows";

        /**
         * Used to reconstruct the world position using the inverse projection matrix to help
         * calculate lighting.
         */
        public static final String DEPTH_SAMPLER = "depthSampler";

        /**
         * A directional light.
         *
         * @see DirectionalLight
         */
        public static final String DIRECTIONAL_LIGHT = "directionalLight";

        /**
         * Environmental fog.
         *
         * @see Fog
         */
        public static final String FOG = "fog";

        /** The inverse of the projection matrix. */
        public static final String INVERSE_PROJECTION_MATRIX = "invProjectionMatrix";

        /** The inverse of the view matrix. */
        public static final String INVERSE_VIEW_MATRIX = "invViewMatrix";

        /** A sampler for the normal values. */
        public static final String NORMAL_SAMPLER = "normalSampler";

        /** How many point lights we have in the point light SSBO. */
        public static final String POINT_LIGHT_COUNT = "pointLightCount";

        /**
         * The prefix for shadow maps. There are several of these numbered uniforms, based on the
         * shadow map cascade count.
         */
        public static final String SHADOW_MAP_PREFIX = "shadowMap_";

        /** A sampler for the specular values. */
        public static final String SPECULAR_SAMPLER = "specularSampler";

        /** How many spotlights we have in the spotlight SSBO. */
        public static final String SPOT_LIGHT_COUNT = "spotLightCount";

        /** Private constructor so this class is not instantiated. */
        private Light() {}
    }

    /**
     * Fragment shader variables.
     *
     * @author Ches Burks
     */
    public static class Scene {
        /**
         * Materials buffer.
         */
        public static final String MATERIALS = "materials";

        /** Used to calculate the position when projected onto the screen space. */
        public static final String PROJECTION_MATRIX = "projectionMatrix";

        /** Used to sample a 2d texture. */
        public static final String TEXTURE_SAMPLER = "textureSampler";

        /** The cameras view matrix. */
        public static final String VIEW_MATRIX = "viewMatrix";

        /** Private constructor so this class is not instantiated. */
        private Scene() {}
    }

    /**
     * Shadow shader variables.
     *
     * @author Ches Burks
     */
    public static class Shadow {
        /** The combined projection and view matrix. */
        public static final String PROJECTION_VIEW_MATRIX = "projViewMatrix";

        /** Private constructor so this class is not instantiated. */
        private Shadow() {}
    }

    /**
     * Skybox shader variables.
     *
     * @author Ches Burks
     */
    public static class Skybox {
        /** The color used for the diffuse component. */
        public static final String DIFFUSE = "diffuse";

        /** Whether there is a texture, 1 if enabled. */
        public static final String HAS_TEXTURE = "hasTexture";

        /** Used to calculate the position when projected onto the screen space. */
        public static final String PROJECTION_MATRIX = "projectionMatrix";

        /** Used to sample a 2d texture. */
        public static final String TEXTURE_SAMPLER = "textureSampler";

        /** The cameras view matrix. */
        public static final String VIEW_MATRIX = "viewMatrix";

        /** Private constructor so this class is not instantiated. */
        private Skybox() {}
    }

    /** Private constructor so this class is not instantiated. */
    private ShaderUniforms() {}
}
