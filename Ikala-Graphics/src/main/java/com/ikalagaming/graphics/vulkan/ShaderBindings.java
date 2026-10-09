package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;

/** Bindings and buffer positions for Vulkan shaders. */
public class ShaderBindings {

    /** Animation shader variables. */
    public static class Animation {
        /*
         * Push constant offsets. The buffers change for every mesh, so the shader gets their device addresses
         * rather than using descriptor sets.
         */

        /** Device address of the model's animation frames buffer. */
        public static final int PUSH_CONSTANT_ANIMATION_DATA_OFFSET = 0;

        /** Device address of this frame's animation offsets buffer. */
        public static final int PUSH_CONSTANT_ANIMATION_OFFSETS_OFFSET = Long.BYTES;

        /** Device address of the mesh vertex buffer. */
        public static final int PUSH_CONSTANT_MODEL_DATA_OFFSET = 2 * Long.BYTES;

        /** Device address of the mesh bone weight buffer. */
        public static final int PUSH_CONSTANT_BONE_WEIGHTS_OFFSET = 3 * Long.BYTES;

        /** Device address of the mesh animation target buffer. */
        public static final int PUSH_CONSTANT_ANIMATION_TARGET_OFFSET = 4 * Long.BYTES;

        /** Where the model's poses start in the animation offsets buffer. */
        public static final int PUSH_CONSTANT_FIRST_POSE_OFFSET = 5 * Long.BYTES;

        /** The number of vertices in the mesh. */
        public static final int PUSH_CONSTANT_VERTEX_COUNT_OFFSET =
                PUSH_CONSTANT_FIRST_POSE_OFFSET + Integer.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE =
                PUSH_CONSTANT_VERTEX_COUNT_OFFSET + Integer.BYTES;

        /** The number of vertices each compute workgroup handles. */
        public static final int WORKGROUP_SIZE = 64;

        /** Private constructor so this class is not instantiated. */
        private Animation() {
            cutItOut();
        }
    }

    /**
     * The global bindless texture array, shared by every stage that samples textures.
     *
     * @see BindlessTextures
     */
    public static class BindlessTextures {
        /** The descriptor set index of the bindless textures, in every pipeline layout using it. */
        public static final int SET = 1;

        /** The binding of the texture array within the set. */
        public static final int BINDING = 0;

        /**
         * The slot of the default texture, which always exists. Materials treat this index as
         * having no texture.
         */
        public static final int DEFAULT_TEXTURE_INDEX = 0;

        /** Private constructor so this class is not instantiated. */
        private BindlessTextures() {
            cutItOut();
        }
    }

    /**
     * Post-processing filter variables.
     *
     * @author Ches Burks
     */
    public static class Filter {
        /** The binding of the texture containing the rendered scene. */
        public static final int SCREEN_TEXTURE = 0;

        /** The binding of the uniform buffer. */
        public static final int UNIFORMS_BINDING = 1;

        /** Offset in the uniforms of the camera projection matrix. */
        public static final int PROJECTION_MATRIX_OFFSET = 0;

        /** Offset in the uniforms of the inverse projection matrix. */
        public static final int INVERSE_PROJECTION_MATRIX_OFFSET = 4 * 4 * Float.BYTES;

        /** Offset in the uniforms of the camera view matrix. */
        public static final int VIEW_MATRIX_OFFSET = 2 * 4 * 4 * Float.BYTES;

        /** Offset in the uniforms of the inverse view matrix. */
        public static final int INVERSE_VIEW_MATRIX_OFFSET = 3 * 4 * 4 * Float.BYTES;

        /** Offset in the uniforms of the size of the screen in pixels, a vec2. */
        public static final int SCREEN_SIZE_OFFSET = 4 * 4 * 4 * Float.BYTES;

        /**
         * Offset in the uniforms of the scale from screen texture coordinates to image texture
         * coordinates, a vec2. The images can be larger than the screen.
         */
        public static final int UV_SCALE_OFFSET = SCREEN_SIZE_OFFSET + 2 * Float.BYTES;

        /** Offset in the uniforms of whether the g-buffer was rendered this frame, 1 if so. */
        public static final int HAS_GBUFFER_OFFSET = UV_SCALE_OFFSET + 2 * Float.BYTES;

        /**
         * Offset in the uniforms of the bindless slot of the g-buffer base color texture. The
         * normal, tangent, material, and depth slots follow it in that order.
         */
        public static final int BASE_COLOR_INDEX_OFFSET = HAS_GBUFFER_OFFSET + Integer.BYTES;

        /** Offset in the uniforms of the bindless slot of the g-buffer depth texture. */
        public static final int DEPTH_INDEX_OFFSET =
                BASE_COLOR_INDEX_OFFSET + GBuffer.TEXTURE_COUNT * Integer.BYTES;

        /** The size of the uniforms buffer. */
        public static final int UNIFORMS_BUFFER_SIZE = DEPTH_INDEX_OFFSET + Integer.BYTES;

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

        /** Binding point for the points buffer. */
        public static final int POINTS_BINDING = 2;

        /** Binding point for the point details buffer. */
        public static final int POINT_DETAILS_BINDING = 3;

        /**
         * Binding point for the buffer that maps draw data texture IDs to bindless texture slots.
         */
        public static final int TEXTURE_INDICES_BINDING = 4;

        /** Offset in the push constants of the first command of the draw list being drawn. */
        public static final int PUSH_CONSTANT_COMMAND_OFFSET = 0;

        /** Offset in the push constants of the first point of the draw list being drawn. */
        public static final int PUSH_CONSTANT_POINT_OFFSET = Integer.BYTES;

        /** Offset in the push constants of the first point detail of the draw list being drawn. */
        public static final int PUSH_CONSTANT_DETAIL_OFFSET = 2 * Integer.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE = 3 * Integer.BYTES;

        /** Binding point for the uniform buffer. */
        public static final int UNIFORMS_BINDING = 0;

        /** The offset in the uniform buffer for the font texture. */
        public static final int UNIFORM_BUFFER_FONT_TEXTURE_OFFSET = 2 * Float.BYTES;

        /**
         * The offset in the uniform buffer for the scaling of the UI. Used to convert from pixel
         * coordinates to Normalized Device Coordinates of (-1, 1).
         */
        public static final int UNIFORM_BUFFER_SCALE_OFFSET = 0;

        /**
         * The offset in the uniform buffer for the top left of the viewport being rendered, which
         * is subtracted from the absolute positions in the draw lists. Aligned to 8 bytes after the
         * font texture, following std140 rules for a vec2.
         */
        public static final int UNIFORM_BUFFER_DISPLAY_POSITION_OFFSET = 4 * Float.BYTES;

        /** The size of the uniforms buffer. */
        public static final int UNIFORMS_BUFFER_SIZE = 6 * Float.BYTES;

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

            /**
             * The distance between elements of an array of these structs. Array elements are padded
             * out to 16 bytes under std140 rules.
             */
            public static final int ARRAY_STRIDE =
                    (int) SharedBuffer.align(SPLIT_DISTANCE + Float.BYTES);

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

            /**
             * The offset of the trailing vec3 padding, which starts on a 16 byte boundary under
             * std140 rules.
             */
            public static final int PADDING = (int) SharedBuffer.align(ENABLED + Integer.BYTES);

            /** The size of the struct in bytes, padded out to 16 bytes under std140 rules. */
            public static final int SIZEOF = (int) SharedBuffer.align(PADDING + 3 * Float.BYTES);

            /** Private constructor so this class is not instantiated. */
            private Fog() {
                cutItOut();
            }
        }

        /* Uniform buffer offsets, following std140 rules */

        /** The offset into the uniforms for the inverse of the projection matrix. */
        public static final int INVERSE_PROJECTION_MATRIX_OFFSET = 0;

        /** The offset into the uniforms for the inverse of the view matrix. */
        public static final int INVERSE_VIEW_MATRIX_OFFSET = 4 * 4 * Float.BYTES;

        /**
         * The offset into the uniforms for the ambient light struct.
         *
         * @see ShaderUniforms.Light.AmbientLight
         */
        public static final int AMBIENT_LIGHT_OFFSET = 2 * 4 * 4 * Float.BYTES;

        /**
         * The offset into the uniforms for the directional light.
         *
         * @see ShaderUniforms.Light.DirectionalLight
         */
        public static final int DIRECTIONAL_LIGHT_OFFSET =
                AMBIENT_LIGHT_OFFSET + AmbientLight.SIZEOF;

        /**
         * The offset into the uniforms for how many point lights we have in the point light SSBO.
         */
        public static final int POINT_LIGHT_COUNT_OFFSET =
                DIRECTIONAL_LIGHT_OFFSET + DirectionalLight.SIZEOF;

        /** The offset into the uniforms for how many spotlights we have in the spotlight SSBO. */
        public static final int SPOT_LIGHT_COUNT_OFFSET = POINT_LIGHT_COUNT_OFFSET + Integer.BYTES;

        /**
         * The offset into the uniforms for the environmental fog. Structs start on a 16 byte
         * boundary under std140 rules.
         *
         * @see ShaderUniforms.Light.Fog
         */
        public static final int FOG_OFFSET =
                (int) SharedBuffer.align(SPOT_LIGHT_COUNT_OFFSET + Integer.BYTES);

        /** The offset into the uniforms for the cascade shadows array. */
        public static final int CASCADE_SHADOWS_OFFSET = FOG_OFFSET + Fog.SIZEOF;

        /** The offset into the uniforms for the bindless slot of the base color texture. */
        public static final int BASE_COLOR_SAMPLER_INDEX_OFFSET =
                CASCADE_SHADOWS_OFFSET
                        + CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT * CascadeShadow.ARRAY_STRIDE;

        /** The offset into the uniforms for the bindless slot of the normal texture. */
        public static final int NORMAL_SAMPLER_INDEX_OFFSET =
                BASE_COLOR_SAMPLER_INDEX_OFFSET + Integer.BYTES;

        /** The offset into the uniforms for the bindless slot of the tangent texture. */
        public static final int TANGENT_SAMPLER_INDEX_OFFSET =
                BASE_COLOR_SAMPLER_INDEX_OFFSET + 2 * Integer.BYTES;

        /** The offset into the uniforms for the bindless slot of the material index texture. */
        public static final int MATERIAL_SAMPLER_INDEX_OFFSET =
                BASE_COLOR_SAMPLER_INDEX_OFFSET + 3 * Integer.BYTES;

        /**
         * The offset into the uniforms for the bindless slot of the depth texture, used to
         * reconstruct the position with the inverse projection matrix to calculate lighting.
         */
        public static final int DEPTH_SAMPLER_INDEX_OFFSET =
                BASE_COLOR_SAMPLER_INDEX_OFFSET + 4 * Integer.BYTES;

        /** The offset into the uniforms for the bindless slot of the first shadow map. */
        public static final int SHADOW_MAP_0_INDEX_OFFSET =
                BASE_COLOR_SAMPLER_INDEX_OFFSET + 5 * Integer.BYTES;

        /** The size of the uniforms buffer. */
        public static final int UNIFORMS_BUFFER_SIZE =
                SHADOW_MAP_0_INDEX_OFFSET
                        + CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT * Integer.BYTES;

        /** Uniforms buffer binding. */
        public static final int UNIFORMS_BINDING = 0;

        /** The point light buffer binding. */
        public static final int POINT_LIGHT_BINDING = 1;

        /** The spotlight buffer binding. */
        public static final int SPOT_LIGHT_BINDING = 2;

        /** Binding for the materials buffer. */
        public static final int MATERIALS_BINDING = 3;

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

        /** Push constant offset for the index of the first model matrix of the model. */
        public static final int PUSH_CONSTANT_FIRST_MATRIX_OFFSET = 0;

        /** Push constant offset for the index of the first material override of the model. */
        public static final int PUSH_CONSTANT_FIRST_OVERRIDE_OFFSET = Integer.BYTES;

        /** Push constant offset for the index of the material assigned to the mesh. */
        public static final int PUSH_CONSTANT_MATERIAL_INDEX_OFFSET = 2 * Integer.BYTES;

        /**
         * Push constant offset for the index of the mesh within the model, used to pick out a
         * material override.
         */
        public static final int PUSH_CONSTANT_MESH_INDEX_OFFSET = 3 * Integer.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE = 4 * Integer.BYTES;

        /** The offset into the uniforms for the position when projected onto the screen space. */
        public static final int PROJECTION_MATRIX_OFFSET = 0;

        /** The binding point for the model matrices buffer. */
        public static final int MODEL_MATRICES_BINDING = 1;

        /** The binding point for the materials buffer. */
        public static final int MATERIALS_BINDING = 2;

        /** The binding point for the material overrides buffer. */
        public static final int MATERIAL_OVERRIDES_BINDING = 3;

        /** The binding point for the uniforms buffer. */
        public static final int UNIFORMS_BINDING = 0;

        /** The size of the uniforms buffer. */
        public static final int UNIFORMS_BUFFER_SIZE = 2 * (4 * 4 * Float.BYTES);

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
        /** Push constant offset for the cascade's combined projection and view matrix. */
        public static final int PUSH_CONSTANT_PROJECTION_VIEW_MATRIX_OFFSET = 0;

        /** Push constant offset for the index of the first model matrix of the model. */
        public static final int PUSH_CONSTANT_FIRST_MATRIX_OFFSET = 4 * 4 * Float.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE =
                PUSH_CONSTANT_FIRST_MATRIX_OFFSET + Integer.BYTES;

        /** The binding point for the model matrices buffer. */
        public static final int MODEL_MATRICES_BINDING = 0;

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
