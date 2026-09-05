package com.ikalagaming.graphics.backend.vulkan;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;

/** Bindings and buffer positions for Vulkan shaders. */
public class ShaderBindings {

    /** Animation shader variables. */
    public static class Animation {
        /** The binding point for the buffer of animation frames. */
        public static final int ANIMATION_DATA_BINDING = 0;

        /** The binding point for the buffer of animation offsets. */
        public static final int ANIMATION_OFFSETS_BINDING = 1;

        /** The binding point for the buffer of model data. */
        public static final int MODEL_DATA_BINDING = 2;

        /** The binding point for the buffer of bone weight data. */
        public static final int BONE_WEIGHT_BINDING = 3;

        /** The binding point for the buffer of animation target data. */
        public static final int ANIMATION_TARGET_BINDING = 4;

        /** Private constructor so this class is not instantiated. */
        private Animation() {
            cutItOut();
        }
    }

    /**
     * Post-processing filter variables.
     *
     * @author Ches Burks
     */
    public static class Filter {
        /** The texture containing the rendered scene. */
        public static final int SCREEN_TEXTURE = 0;

        /** Private constructor so this class is not instantiated. */
        private Filter() {
            cutItOut();
        }
    }

    /**
     * GUI shader variables.
     *
     * @author Ches Burks
     */
    public static class GUI {

        /** Binding point for the commands buffer. */
        public static final int COMMANDS_BINDING = 1;

        /** Binding point for the bindless textures. */
        public static final int TEXTURES_BINDING = 4;

        @Deprecated public static final int TEXTURES_BINDING_LEGACY = 1;

        /** Binding point for the points buffer. */
        public static final int POINTS_BINDING = 2;

        /** Binding point for the point details buffer. */
        public static final int POINT_DETAILS_BINDING = 3;

        /** Binding point for the uniform buffer. */
        public static final int UNIFORMS_BINDING = 0;

        /** The offset in the uniform buffer for the font texture. */
        public static final int UNIFORM_BUFFER_FONT_TEXTURE_OFFSET = 2 * Float.BYTES;

        /**
         * The offset in the uniform buffer for the scaling of the UI. Used to convert from pixel
         * coordinates to Normalized Device Coordinates of (-1, 1).
         */
        public static final int UNIFORM_BUFFER_SCALE_OFFSET = 0;

        /** The size of the uniforms buffer. */
        public static final int UNIFORMS_BUFFER_SIZE = 2 * Float.BYTES + Integer.BYTES;

        /** Private constructor so this class is not instantiated. */
        private GUI() {
            cutItOut();
        }
    }

    /**
     * Light shader variables.
     *
     * @author Ches Burks
     */
    public static class Light {
        /**
         * Offsets into the ambient light struct.
         *
         * @author Ches Burks
         */
        public static class AmbientLight {
            /** The color of the light. */
            public static final int COLOR = 0;

            /** The intensity, measured in candela per square meter (cd/m^2). */
            public static final int INTENSITY = 3 * Float.BYTES;

            /** The size of the struct in bytes. */
            public static final int SIZEOF = 4 * Float.BYTES;

            /** Private constructor so this class is not instantiated. */
            private AmbientLight() {
                cutItOut();
            }
        }

        /**
         * Offsets into the cascade shadow struct.
         *
         * @author Ches Burks
         */
        public static class CascadeShadow {
            /** The combined projection and view matrix. */
            public static final int PROJECTION_VIEW_MATRIX = 0;

            /** The distance to the split. */
            public static final int SPLIT_DISTANCE = 4 * 4 * Float.BYTES;

            /** The size of the struct in bytes. */
            public static final int SIZEOF = (4 * 4 + 1) * Float.BYTES;

            /** Private constructor so this class is not instantiated. */
            private CascadeShadow() {
                cutItOut();
            }
        }

        /**
         * Offsets into the directional light struct.
         *
         * @author Ches Burks
         */
        public static class DirectionalLight {
            /** The color of the light. */
            public static final int COLOR = 0;

            /** The direction that the light is coming from. */
            public static final int DIRECTION = 4 * Float.BYTES;

            /** The intensity, measured in candela per square meter (cd/m^2). */
            public static final int INTENSITY = 7 * Float.BYTES;

            /** The size of the struct in bytes. */
            public static final int SIZEOF = (3 + 1 + 3 + 1) * Float.BYTES;

            /** Private constructor so this class is not instantiated. */
            private DirectionalLight() {
                cutItOut();
            }
        }

        /**
         * Offsets into the fog struct.
         *
         * @author Ches Burks
         */
        public static class Fog {
            /** The base color of the fog. */
            public static final int COLOR = 0;

            /** How dense the fog is. */
            public static final int DENSITY = 3 * Float.BYTES;

            /** Whether the fog is enabled, 1 if enabled. */
            public static final int ENABLED = 4 * Float.BYTES;

            /** The size of the struct in bytes. */
            public static final int SIZEOF = (3 + 1 + 1 + 3) * Float.BYTES;

            /** Private constructor so this class is not instantiated. */
            private Fog() {
                cutItOut();
            }
        }

        /**
         * The offset into the uniforms for the ambient light struct.
         *
         * @see ShaderUniforms.Light.AmbientLight
         */
        public static final int AMBIENT_LIGHT = 4 * 4 * 2 * Float.BYTES;

        /** Sampler for the base color of a material. */
        public static final int BASE_COLOR_SAMPLER_BINDING = 1;

        /** The offset into the uniforms for the cascade shadows. */
        public static final int CASCADE_SHADOWS =
                4 * 4 * 2 * Float.BYTES
                        + AmbientLight.SIZEOF
                        + DirectionalLight.SIZEOF
                        + 2 * Integer.BYTES
                        + Fog.SIZEOF;

        /**
         * Used to reconstruct the world position using the inverse projection matrix to help
         * calculate lighting.
         */
        public static final int DEPTH_SAMPLER_BINDING = 5;

        /**
         * The offset into the uniforms for the directional light.
         *
         * @see ShaderUniforms.Light.DirectionalLight
         */
        public static final int DIRECTIONAL_LIGHT = 4 * 4 * 2 * Float.BYTES + AmbientLight.SIZEOF;

        /**
         * The offset into the uniforms for the environmental fog.
         *
         * @see ShaderUniforms.Light.Fog
         */
        public static final int FOG =
                4 * 4 * 2 * Float.BYTES
                        + AmbientLight.SIZEOF
                        + DirectionalLight.SIZEOF
                        + 2 * Integer.BYTES;

        /** The offset into the uniforms for the inverse of the projection matrix. */
        public static final int INVERSE_PROJECTION_MATRIX = 0;

        /** The offset into the uniforms for the inverse of the view matrix. */
        public static final int INVERSE_VIEW_MATRIX = 4 * 4 * Float.BYTES;

        /** Sampler for material IDs. */
        public static final int MATERIAL_SAMPLER_BINDING = 4;

        /** Binding for the materials buffer. */
        public static final int MATERIALS_BINDING = 11;

        /** A sampler for the normal values. */
        public static final int NORMAL_SAMPLER_BINDING = 2;

        /**
         * The offset into the uniforms for how many point lights we have in the point light SSBO.
         */
        public static final int POINT_LIGHT_COUNT =
                4 * 4 * 2 * Float.BYTES + AmbientLight.SIZEOF + DirectionalLight.SIZEOF;

        /** The point light buffer binding. */
        public static final int POINT_LIGHT_BINDING = 9;

        /** The spotlight buffer binding. */
        public static final int SPOT_LIGHT_BINDING = 10;

        /** The first shadow map texture binding. */
        public static final int SHADOW_MAP_0_BINDING = 6;

        /** The second shadow map texture binding. */
        public static final int SHADOW_MAP_1_BINDING = 7;

        /** The third shadow map texture binding. */
        public static final int SHADOW_MAP_2_BINDING = 8;

        /** The offset into the uniforms for how many spotlights we have in the spotlight SSBO. */
        public static final int SPOT_LIGHT_COUNT =
                4 * 4 * 2 * Float.BYTES
                        + AmbientLight.SIZEOF
                        + DirectionalLight.SIZEOF
                        + Integer.BYTES;

        /** Sampler for the tangent values. */
        public static final int TANGENT_SAMPLER_BINDING = 3;

        /** Uniforms buffer binding. */
        public static final int UNIFORMS_BINDING = 0;

        /** The size of the uniforms buffer. */
        public static final int UNIFORMS_BUFFER_SIZE =
                4 * 4 * 2 * Float.BYTES
                        + AmbientLight.SIZEOF
                        + DirectionalLight.SIZEOF
                        + 2 * Integer.BYTES
                        + Fog.SIZEOF
                        + CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT * CascadeShadow.SIZEOF;

        /** Private constructor so this class is not instantiated. */
        private Light() {
            cutItOut();
        }
    }

    /**
     * Fragment shader variables.
     *
     * @author Ches Burks
     */
    public static class Scene {

        /** Offsets into the material struct. */
        public static class Material {
            /** Offset in bytes to the base color. */
            public static final int BASE_COLOR = 0;

            /** Offset in bytes to the anisotropic. */
            public static final int ANISOTROPIC = 4 * Float.BYTES;

            /** Offset in bytes to the clearcoat. */
            public static final int CLEARCOAT = (4 + 1) * Float.BYTES;

            /** Offset in bytes to the clearcoat gloss. */
            public static final int CLEARCOAT_GLOSS = (4 + 2) * Float.BYTES;

            /** Offset in bytes to the metallic. */
            public static final int METALLIC = (4 + 3) * Float.BYTES;

            /** Offset in bytes to the roughness. */
            public static final int ROUGHNESS = (4 + 4) * Float.BYTES;

            /** Offset in bytes to the sheen. */
            public static final int SHEEN = (4 + 4 + 1) * Float.BYTES;

            /** Offset in bytes to the sheen tint. */
            public static final int SHEEN_TINT = (4 + 4 + 2) * Float.BYTES;

            /** Offset in bytes to the specular. */
            public static final int SPECULAR = (4 + 4 + 3) * Float.BYTES;

            /** Offset in bytes to the specular tint. */
            public static final int SPECULAR_TINT = (4 + 4 + 4) * Float.BYTES;

            /** Offset in bytes to the subsurface. */
            public static final int SUBSURFACE = (4 + 4 + 4 + 1) * Float.BYTES;

            /** Offset in bytes to the normal map index. */
            public static final int NORMAL_MAP_INDEX = (4 + 4 + 4 + 2) * Float.BYTES;

            /** Offset in bytes to the texture map index. */
            public static final int TEXTURE_INDEX = (4 + 4 + 4 + 2) * Float.BYTES + Integer.BYTES;

            /** Total size of the struct in bytes. */
            public static final int SIZEOF = (4 + 4 + 4 + 2) * Float.BYTES + 2 * Integer.BYTES;

            /** Private constructor so this class is not instantiated. */
            private Material() {
                cutItOut();
            }
        }

        /**
         * The offset into the uniforms for the index of the current material, for the vertex
         * shader.
         */
        public static final int MATERIAL_INDEX_OFFSET = 4 * 4 * 2 * Float.BYTES;

        /**
         * The offset into the uniforms for the index of the current mesh, for the vertex shader,
         * used to pick out a material override.
         */
        public static final int MESH_INDEX_OFFSET = 4 * 4 * 2 * Float.BYTES + Integer.BYTES;

        /** The offset into the uniforms for the position when projected onto the screen space. */
        public static final int PROJECTION_MATRIX_OFFSET = 0;

        /** The binding point for the model matrices buffer. */
        public static final int MODEL_MATRICES_BINDING = 1;

        /** The binding point for the materials buffer. */
        public static final int MATERIALS_BINDING = 2;

        /** The binding point for the material overrides buffer. */
        public static final int MATERIAL_OVERRIDES_BINDING = 3;

        /** The binding point for the textures buffer. */
        public static final int TEXTURES_BINDING = 4;

        /** The binding point for the uniforms buffer. */
        public static final int UNIFORMS_BINDING = 0;

        /** The size of the uniforms buffer. */
        public static final int UNIFORMS_BUFFER_SIZE =
                2 * (4 * 4 * Float.BYTES) + 2 * Integer.BYTES;

        /** The offset into the uniforms for the cameras view matrix. */
        public static final int VIEW_MATRIX_OFFSET = 4 * 4 * Float.BYTES;

        /** Private constructor so this class is not instantiated. */
        private Scene() {
            cutItOut();
        }
    }

    /**
     * Shadow shader variables.
     *
     * @author Ches Burks
     */
    public static class Shadow {
        /** The offset into the uniforms buffer for the combined projection and view matrix. */
        public static final int PROJECTION_VIEW_MATRIX_OFFSET = 0;

        /** The binding point for the model matrices buffer. */
        public static final int MODEL_MATRICES_BINDING = 1;

        /** The binding point for the uniforms buffer. */
        public static final int UNIFORMS_BINDING = 0;

        /** The size of the uniforms buffer. */
        public static final int UNIFORMS_BUFFER_SIZE = 4 * 4 * Float.BYTES;

        /** Private constructor so this class is not instantiated. */
        private Shadow() {
            cutItOut();
        }
    }

    /**
     * Skybox shader variables.
     *
     * @author Ches Burks
     */
    public static class Skybox {
        /** Offset in the uniform buffer in bytes for the color used for the diffuse component. */
        public static final int DIFFUSE_OFFSET = 4 * 4 * 2 * Float.BYTES;

        /** Offset in the uniform buffer in bytes for whether there is a texture, 1 if enabled. */
        public static final int HAS_TEXTURE_OFFSET = (4 * 4 * 2 + 4) * Float.BYTES;

        /** Offset in the uniform buffer in bytes for the projection matrix. */
        public static final int PROJECTION_MATRIX_OFFSET = 0;

        /** Offset in the uniform buffer in bytes for the projection matrix. */
        public static final int TEXTURE_INDEX_OFFSET =
                (4 * 4 * 2 + 4) * Float.BYTES + Integer.BYTES;

        /** Binding point for the bindless textures array. */
        public static final int TEXTURES_BINDING = 1;

        /** The binding point for the uniforms buffer. */
        public static final int UNIFORMS_BINDING = 0;

        /** The size of the uniforms buffer. */
        public static final int UNIFORMS_BUFFER_SIZE =
                (4 * 4 * 2 + 4) * Float.BYTES + 2 * Integer.BYTES;

        /** Offset in the uniform buffer in bytes for the cameras view matrix. */
        public static final int VIEW_MATRIX_OFFSET = 4 * 4 * Float.BYTES;

        /** Private constructor so this class is not instantiated. */
        private Skybox() {
            cutItOut();
        }
    }

    /** Private constructor so this class is not instantiated. */
    private ShaderBindings() {
        cutItOut();
    }

    /** Throw an exception. Tired of warnings about duplicate ints. */
    private static void cutItOut() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
