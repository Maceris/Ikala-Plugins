package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;

/** Bindings and buffer positions for Vulkan shaders. */
public class ShaderBindings {

    /** Instance transform shader variables, see {@code instance_transform.comp}. */
    public static class Instances {
        /*
         * Push constant offsets. Every buffer is passed by device address. The vec4s are aligned to
         * 16 bytes, which the two addresses before them already are.
         */

        /** Device address of the instance table. */
        public static final int PUSH_CONSTANT_TABLE_OFFSET = 0;

        /** Device address of this frame's model matrices. */
        public static final int PUSH_CONSTANT_MATRICES_OFFSET = Long.BYTES;

        /** The high part of the camera's world position, a vec4. */
        public static final int PUSH_CONSTANT_CAMERA_HIGH_OFFSET = 2 * Long.BYTES;

        /** The low part of the camera's world position, a vec4. */
        public static final int PUSH_CONSTANT_CAMERA_LOW_OFFSET =
                PUSH_CONSTANT_CAMERA_HIGH_OFFSET + 4 * Float.BYTES;

        /** How many slots the instance table has. */
        public static final int PUSH_CONSTANT_COUNT_OFFSET =
                PUSH_CONSTANT_CAMERA_LOW_OFFSET + 4 * Float.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE = PUSH_CONSTANT_COUNT_OFFSET + Integer.BYTES;

        /** The number of instances each compute workgroup handles. */
        public static final int WORKGROUP_SIZE = 64;

        /** Private constructor so this class is not instantiated. */
        private Instances() {
            cutItOut();
        }
    }

    /** Culling shader variables, see {@code cull.comp}. Every buffer is passed by address. */
    public static class Cull {
        /** Device address of the instance table. */
        public static final int PUSH_CONSTANT_INSTANCES_OFFSET = 0;

        /** Device address of the model mesh list. */
        public static final int PUSH_CONSTANT_MODEL_MESHES_OFFSET = Long.BYTES;

        /** Device address of the material override list. */
        public static final int PUSH_CONSTANT_OVERRIDES_OFFSET = 2 * Long.BYTES;

        /** Device address of the mesh table. */
        public static final int PUSH_CONSTANT_MESHES_OFFSET = 3 * Long.BYTES;

        /** Device address of this frame's model matrices. */
        public static final int PUSH_CONSTANT_MATRICES_OFFSET = 4 * Long.BYTES;

        /** Device address of this frame's draw commands. */
        public static final int PUSH_CONSTANT_COMMANDS_OFFSET = 5 * Long.BYTES;

        /** Device address of this frame's visible instance list. */
        public static final int PUSH_CONSTANT_VISIBLE_OFFSET = 6 * Long.BYTES;

        /** Device address of this frame's frustum planes. */
        public static final int PUSH_CONSTANT_FRUSTA_OFFSET = 7 * Long.BYTES;

        /** Device address of this frame's per-pass counters. */
        public static final int PUSH_CONSTANT_COUNTERS_OFFSET = 8 * Long.BYTES;

        /** How many instance slots there are. */
        public static final int PUSH_CONSTANT_SLOT_COUNT_OFFSET = 9 * Long.BYTES;

        /** How many mesh slots there are, which is how many commands each list has. */
        public static final int PUSH_CONSTANT_MESH_SLOT_COUNT_OFFSET =
                PUSH_CONSTANT_SLOT_COUNT_OFFSET + Integer.BYTES;

        /** Device address of the visibility history, aligned to 8 after the two counts. */
        public static final int PUSH_CONSTANT_HISTORY_OFFSET =
                PUSH_CONSTANT_MESH_SLOT_COUNT_OFFSET + Integer.BYTES;

        /** Device address of this frame's occlusion view, see {@link #VIEW_SIZE}. */
        public static final int PUSH_CONSTANT_VIEW_OFFSET =
                PUSH_CONSTANT_HISTORY_OFFSET + Long.BYTES;

        /** Which phase this is, {@link #PHASE_EARLY} or {@link #PHASE_LATE}. */
        public static final int PUSH_CONSTANT_PHASE_OFFSET = PUSH_CONSTANT_VIEW_OFFSET + Long.BYTES;

        /** How many slots the baked mesh table has. */
        public static final int PUSH_CONSTANT_BAKED_SLOT_COUNT_OFFSET =
                PUSH_CONSTANT_PHASE_OFFSET + Integer.BYTES;

        /** Device address of the baked mesh table, aligned to 8 after the phase and count. */
        public static final int PUSH_CONSTANT_BAKED_MESHES_OFFSET =
                PUSH_CONSTANT_BAKED_SLOT_COUNT_OFFSET + Integer.BYTES;

        /** The address of whether each material is translucent, by material index. */
        public static final int PUSH_CONSTANT_MATERIAL_TRANSPARENCY_OFFSET =
                PUSH_CONSTANT_BAKED_MESHES_OFFSET + Long.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE =
                PUSH_CONSTANT_MATERIAL_TRANSPARENCY_OFFSET + Long.BYTES;

        /** The phase that culls the shadow cascades, and the scene by last frame's history. */
        public static final int PHASE_EARLY = 0;

        /** The phase that culls the scene against the depth pyramid. */
        public static final int PHASE_LATE = 1;

        /*
         * The occlusion view (std430): mat4 projectionView; ivec2 size; int levels; uint flags;
         */

        /** The pyramid's projection × view matrix, moved into render space. */
        public static final int VIEW_MATRIX_OFFSET = 0;

        /** The size of the depth buffer the pyramid was built from, two ints. */
        public static final int VIEW_DEPTH_SIZE_OFFSET = 4 * 4 * Float.BYTES;

        /** How many levels the pyramid has. */
        public static final int VIEW_LEVELS_OFFSET = VIEW_DEPTH_SIZE_OFFSET + 2 * Integer.BYTES;

        /** The flags, {@link #FLAG_OCCLUSION} and {@link #FLAG_PYRAMID_VALID}. */
        public static final int VIEW_FLAGS_OFFSET = VIEW_LEVELS_OFFSET + Integer.BYTES;

        /** The size of the occlusion view in bytes. */
        public static final int VIEW_SIZE = VIEW_FLAGS_OFFSET + Integer.BYTES;

        /** Set in the view flags when the scene is culled by occlusion. */
        public static final int FLAG_OCCLUSION = 1;

        /** Set in the view flags when the pyramid holds a depth buffer. */
        public static final int FLAG_PYRAMID_VALID = 2;

        /** The binding of the depth pyramid, every level, in set 0. */
        public static final int PYRAMID_BINDING = 0;

        /** How many planes each pass's frustum has. */
        public static final int PLANES_PER_PASS = 6;

        /** The number of instance slots each compute workgroup handles. */
        public static final int WORKGROUP_SIZE = 64;

        /** Private constructor so this class is not instantiated. */
        private Cull() {
            cutItOut();
        }
    }

    /** Depth pyramid shader variables, see {@code depth_pyramid.comp}. */
    public static class DepthPyramid {
        /** The size of what is read, two ints. */
        public static final int PUSH_CONSTANT_SOURCE_SIZE_OFFSET = 0;

        /** The size of the level being written, two ints. */
        public static final int PUSH_CONSTANT_DESTINATION_SIZE_OFFSET = 2 * Integer.BYTES;

        /** The level being written. */
        public static final int PUSH_CONSTANT_LEVEL_OFFSET = 4 * Integer.BYTES;

        /** The bindless index of this frame's depth buffer. */
        public static final int PUSH_CONSTANT_DEPTH_INDEX_OFFSET = 5 * Integer.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE = 6 * Integer.BYTES;

        /** The binding of the level being written, in set 0. */
        public static final int DESTINATION_BINDING = 0;

        /** The binding of every level, to read the one before, in set 0. */
        public static final int PYRAMID_BINDING = 1;

        /** The width and height of each compute workgroup, in texels. */
        public static final int WORKGROUP_SIZE = 8;

        /** Private constructor so this class is not instantiated. */
        private DepthPyramid() {
            cutItOut();
        }
    }

    /**
     * Light culling shader variables, see {@code light_cull.comp}. Every buffer is passed by
     * address.
     */
    public static class LightCull {
        /** The inverse of the projection matrix. */
        public static final int PUSH_CONSTANT_INVERSE_PROJECTION_OFFSET = 0;

        /** The address of the lights buffer. */
        public static final int PUSH_CONSTANT_LIGHTS_OFFSET = 4 * 4 * Float.BYTES;

        /** The address of the cluster buffer. */
        public static final int PUSH_CONSTANT_CLUSTERS_OFFSET =
                PUSH_CONSTANT_LIGHTS_OFFSET + Long.BYTES;

        /** The address of the stats buffer. */
        public static final int PUSH_CONSTANT_STATS_OFFSET =
                PUSH_CONSTANT_CLUSTERS_OFFSET + Long.BYTES;

        /** How many lights are in the lights buffer. */
        public static final int PUSH_CONSTANT_LIGHT_COUNT_OFFSET =
                PUSH_CONSTANT_STATS_OFFSET + Long.BYTES;

        /**
         * The scale from the log of a depth to a slice, see {@link ClusterMath#logScale(double)}.
         */
        public static final int PUSH_CONSTANT_LOG_SCALE_OFFSET =
                PUSH_CONSTANT_LIGHT_COUNT_OFFSET + Integer.BYTES;

        /** The far plane, where the last slice ends. */
        public static final int PUSH_CONSTANT_FAR_PLANE_OFFSET =
                PUSH_CONSTANT_LOG_SCALE_OFFSET + Float.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE = PUSH_CONSTANT_FAR_PLANE_OFFSET + Float.BYTES;

        /** How many clusters each workgroup handles. */
        public static final int WORKGROUP_SIZE = 64;

        /**
         * The size of the cluster buffer: a light count for each cluster, then room for each
         * cluster's light indices.
         */
        public static final long CLUSTERS_BUFFER_SIZE =
                (long) ClusterMath.COUNT * (1 + ClusterMath.MAX_LIGHTS_PER_CLUSTER) * Integer.BYTES;

        /** The busiest cluster's light count in the stats buffer. */
        public static final int STATS_BUSIEST = 0;

        /** How many clusters overflowed, in the stats buffer. */
        public static final int STATS_OVERFLOWING = 1;

        /** How many lights were listed across every cluster, in the stats buffer. */
        public static final int STATS_LISTED = 2;

        /** How many uints the stats buffer holds. */
        public static final int STATS_COUNT = 3;

        /** Private constructor so this class is not instantiated. */
        private LightCull() {
            cutItOut();
        }
    }

    /** Transparent stage shader variables, see {@code translucent.frag}. */
    public static class Translucent {
        /** The size of the area drawn, in pixels, two floats. */
        public static final int PUSH_CONSTANT_SCREEN_SIZE_OFFSET = 0;

        /** The scale from the log of a depth to a volume slice, see {@link VoxelOitMath}. */
        public static final int PUSH_CONSTANT_LOG_SCALE_OFFSET =
                PUSH_CONSTANT_SCREEN_SIZE_OFFSET + 2 * Float.BYTES;

        /** Nonzero to weight by the transparency volume, zero for the depth weights. */
        public static final int PUSH_CONSTANT_VOXEL_WEIGHTS_OFFSET =
                PUSH_CONSTANT_LOG_SCALE_OFFSET + Float.BYTES;

        /** How many tiles across and down this frame's volume holds, two floats. */
        public static final int PUSH_CONSTANT_TILES_OFFSET =
                PUSH_CONSTANT_VOXEL_WEIGHTS_OFFSET + Integer.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE = PUSH_CONSTANT_TILES_OFFSET + 2 * Float.BYTES;

        /** The set the transparency volume is read through. */
        public static final int VOLUME_SET = 3;

        /** Private constructor so this class is not instantiated. */
        private Translucent() {
            cutItOut();
        }
    }

    /**
     * Voxel-based transparency shader variables, see {@code oit_splat.frag} and {@code
     * oit_integrate.comp}, which share the first ones.
     */
    public static class OitVolume {
        /** Device address of the extinction buffer. */
        public static final int PUSH_CONSTANT_EXTINCTION_OFFSET = 0;

        /** How many tiles across and down this frame's volume holds, two uints. */
        public static final int PUSH_CONSTANT_TILES_OFFSET =
                PUSH_CONSTANT_EXTINCTION_OFFSET + Long.BYTES;

        /** The scale from the log of a depth to a slice, splatting only. */
        public static final int PUSH_CONSTANT_LOG_SCALE_OFFSET =
                PUSH_CONSTANT_TILES_OFFSET + 2 * Integer.BYTES;

        /** The size of the splat's push constants in bytes. */
        public static final int SPLAT_PUSH_CONSTANTS_SIZE =
                PUSH_CONSTANT_LOG_SCALE_OFFSET + Float.BYTES;

        /** The size of the integration's push constants in bytes. */
        public static final int INTEGRATE_PUSH_CONSTANTS_SIZE = PUSH_CONSTANT_LOG_SCALE_OFFSET;

        /** The binding of the transmittance, as a storage image to integrate into or to read. */
        public static final int TRANSMITTANCE_BINDING = 0;

        /** The width and height of the integration's workgroups, in tiles. */
        public static final int WORKGROUP_SIZE = 8;

        /** Private constructor so this class is not instantiated. */
        private OitVolume() {
            cutItOut();
        }
    }

    /** Transparency resolve shader variables, see {@code oit_resolve.frag}. */
    public static class OitResolve {
        /** The bindless slot of the accumulated color and weight. */
        public static final int PUSH_CONSTANT_ACCUM_INDEX_OFFSET = 0;

        /** The bindless slot of the summed extinction. */
        public static final int PUSH_CONSTANT_EXTINCTION_INDEX_OFFSET = Integer.BYTES;

        /** The slice of the transparency volume to show, or negative to composite as usual. */
        public static final int PUSH_CONSTANT_DEBUG_SLICE_OFFSET =
                PUSH_CONSTANT_EXTINCTION_INDEX_OFFSET + Integer.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE =
                PUSH_CONSTANT_DEBUG_SLICE_OFFSET + Integer.BYTES;

        /** The set the transparency volume is read through. */
        public static final int VOLUME_SET = 1;

        /** Private constructor so this class is not instantiated. */
        private OitResolve() {
            cutItOut();
        }
    }

    /** Tone mapping shader variables, see {@code tonemap.frag}. */
    public static class ToneMap {
        /** The bindless slot of the scene color. */
        public static final int PUSH_CONSTANT_SCENE_COLOR_INDEX_OFFSET = 0;

        /** What the scene color is multiplied by before tone mapping. */
        public static final int PUSH_CONSTANT_EXPOSURE_OFFSET = Integer.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE = PUSH_CONSTANT_EXPOSURE_OFFSET + Float.BYTES;

        /** Private constructor so this class is not instantiated. */
        private ToneMap() {
            cutItOut();
        }
    }

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

            /** How wide one texel of the cascade's shadow map is, in meters. */
            public static final int TEXEL_SIZE = SPLIT_DISTANCE + Float.BYTES;

            /**
             * The distance between elements of an array of these structs. Array elements are padded
             * out to 16 bytes under std140 rules.
             */
            public static final int ARRAY_STRIDE =
                    (int) SharedBuffer.align(TEXEL_SIZE + Float.BYTES);

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
         * Offsets into the light struct of the lights buffer, one per point light or spotlight,
         * following std430 rules.
         *
         * @author Ches Burks
         */
        public static class LightStruct {
            /** The position in view space. */
            public static final int POSITION = 0;

            /** How far the light reaches. */
            public static final int RANGE = 3 * Float.BYTES;

            /** The color. */
            public static final int COLOR = 4 * Float.BYTES;

            /** The intensity. */
            public static final int INTENSITY = 7 * Float.BYTES;

            /** Which way a spotlight points, in view space. */
            public static final int DIRECTION = 8 * Float.BYTES;

            /** The cosine of the angle from a spotlight's axis where it ends. */
            public static final int COS_OUTER = 11 * Float.BYTES;

            /** The cosine of the angle from a spotlight's axis where it starts to fade. */
            public static final int COS_INNER = 12 * Float.BYTES;

            /** The light's {@link com.ikalagaming.graphics.scene.lights.LightType} shader value. */
            public static final int TYPE = 13 * Float.BYTES;

            /** The size of the struct in bytes, padded out to a multiple of 16. */
            public static final int SIZEOF = 16 * Float.BYTES;

            /** Private constructor so this class is not instantiated. */
            private LightStruct() {
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

        /** The offset into the uniforms for how many lights are in the lights buffer. */
        public static final int LIGHT_COUNT_OFFSET =
                DIRECTIONAL_LIGHT_OFFSET + DirectionalLight.SIZEOF;

        /** The offset into the uniforms for whether to tint pixels by their cluster's lights. */
        public static final int CLUSTER_HEAT_MAP_OFFSET = LIGHT_COUNT_OFFSET + Integer.BYTES;

        /**
         * The offset into the uniforms for the environmental fog. Structs start on a 16 byte
         * boundary under std140 rules.
         *
         * @see ShaderUniforms.Light.Fog
         */
        public static final int FOG_OFFSET =
                (int) SharedBuffer.align(CLUSTER_HEAT_MAP_OFFSET + Integer.BYTES);

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

        /**
         * The offset into the uniforms for the scale from the log of a depth to a cluster slice,
         * see {@link ClusterMath#logScale(double)}.
         */
        public static final int CLUSTER_LOG_SCALE_OFFSET =
                SHADOW_MAP_0_INDEX_OFFSET
                        + CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT * Integer.BYTES;

        /** The size of the uniforms buffer, padded out to 16 bytes under std140 rules. */
        public static final int UNIFORMS_BUFFER_SIZE =
                (int) SharedBuffer.align(CLUSTER_LOG_SCALE_OFFSET + Float.BYTES);

        /** Uniforms buffer binding. */
        public static final int UNIFORMS_BINDING = 0;

        /** The lights buffer binding, holding the point lights and spotlights. */
        public static final int LIGHTS_BINDING = 1;

        /** Binding for the materials buffer. */
        public static final int MATERIALS_BINDING = 2;

        /** Binding for the cluster buffer that light culling fills. */
        public static final int CLUSTERS_BINDING = 3;

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

        /** The offset into the uniforms for the position when projected onto the screen space. */
        public static final int PROJECTION_MATRIX_OFFSET = 0;

        /** The binding point for the model matrices buffer. */
        public static final int MODEL_MATRICES_BINDING = 1;

        /** The binding point for the materials buffer. */
        public static final int MATERIALS_BINDING = 2;

        /**
         * The binding point for this frame's visible instance list, (instance slot, material) for
         * each instance drawn.
         */
        public static final int VISIBLE_BINDING = 3;

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

        /** Push constant offset for the device address of this frame's visible instance list. */
        public static final int PUSH_CONSTANT_VISIBLE_OFFSET = 4 * 4 * Float.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE = PUSH_CONSTANT_VISIBLE_OFFSET + Long.BYTES;

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
