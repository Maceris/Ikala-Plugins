package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.graph.OccupancyClipmap;

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

    /**
     * Level of detail culling shader variables, see {@code lod_cull.comp}. Every buffer is passed
     * by address.
     */
    public static class LodCull {
        /** Device address of the cluster table, see {@link LodCullMath}. */
        public static final int PUSH_CONSTANT_CLUSTERS_OFFSET = 0;

        /** Device address of whether each cluster was visible last frame. */
        public static final int PUSH_CONSTANT_HISTORY_OFFSET = Long.BYTES;

        /** Device address of the clusters' mesh table. */
        public static final int PUSH_CONSTANT_MESHES_OFFSET = 2 * Long.BYTES;

        /** Device address of this frame's world table, see {@link LodCullMath}. */
        public static final int PUSH_CONSTANT_WORLDS_OFFSET = 3 * Long.BYTES;

        /** Device address of this frame's draw commands. */
        public static final int PUSH_CONSTANT_COMMANDS_OFFSET = 4 * Long.BYTES;

        /** Device address of this frame's draw counts and statistics. */
        public static final int PUSH_CONSTANT_COUNTS_OFFSET = 5 * Long.BYTES;

        /** Device address of this frame's frustum planes, the same as {@link Cull}'s. */
        public static final int PUSH_CONSTANT_FRUSTA_OFFSET = 6 * Long.BYTES;

        /** Device address of this frame's occlusion view, the same as {@link Cull}'s. */
        public static final int PUSH_CONSTANT_VIEW_OFFSET = 7 * Long.BYTES;

        /** Device address of each cell slot's flags. */
        public static final int PUSH_CONSTANT_CELL_FLAGS_OFFSET = 8 * Long.BYTES;

        /** Device address of the hidden section set, one bit per section slot. */
        public static final int PUSH_CONSTANT_HIDDEN_OFFSET = 9 * Long.BYTES;

        /** Device address of the shown streaming unit set, one bit per unit slot. */
        public static final int PUSH_CONSTANT_SHOWN_UNITS_OFFSET = 10 * Long.BYTES;

        /** Which phase this is, {@link Cull#PHASE_EARLY} or {@link Cull#PHASE_LATE}. */
        public static final int PUSH_CONSTANT_PHASE_OFFSET = 11 * Long.BYTES;

        /** Device address of the pair table, see {@link LodCullMath}. */
        public static final int PUSH_CONSTANT_PAIRS_OFFSET = 12 * Long.BYTES;

        /** How many entries the pair table has. */
        public static final int PUSH_CONSTANT_PAIR_CAPACITY_OFFSET = 13 * Long.BYTES;

        /**
         * How many entries the cluster table has; each list has room for this many commands plus
         * the pair table's.
         */
        public static final int PUSH_CONSTANT_CAPACITY_OFFSET =
                PUSH_CONSTANT_PHASE_OFFSET + Integer.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE =
                PUSH_CONSTANT_PAIR_CAPACITY_OFFSET + Integer.BYTES;

        /** The number of clusters each compute workgroup handles. */
        public static final int WORKGROUP_SIZE = 64;

        /** Private constructor so this class is not instantiated. */
        private LodCull() {
            cutItOut();
        }
    }

    /** Environment shader variables, see {@code environment.comp} and {@link Environment}. */
    public static class Environment {
        /** The skybox's color, sRGB encoded, a vec4. */
        public static final int SKY_COLOR_OFFSET = 0;

        /** Which pass runs. */
        public static final int MODE_OFFSET = 4 * Float.BYTES;

        /** The level written. */
        public static final int LEVEL_OFFSET = MODE_OFFSET + Integer.BYTES;

        /** The skybox texture's bindless slot, or -1 for its color. */
        public static final int SKY_TEXTURE_OFFSET = LEVEL_OFFSET + Integer.BYTES;

        /** The roughness of the level written, when prefiltering. */
        public static final int ROUGHNESS_OFFSET = SKY_TEXTURE_OFFSET + Integer.BYTES;

        /**
         * For the physical sky, toward the sun and the camera's distance from the planet's center,
         * a vec4.
         */
        public static final int SUN_AND_RADIUS_OFFSET = ROUGHNESS_OFFSET + Float.BYTES;

        /**
         * How the skybox texture lays the sky out, the {@link
         * com.ikalagaming.graphics.scene.SkyboxLayout} ordinal.
         */
        public static final int LAYOUT_OFFSET = SUN_AND_RADIUS_OFFSET + 4 * Float.BYTES;

        /**
         * The size of the push constants in bytes, which the atmosphere shader shares, see {@link
         * Atmosphere}.
         */
        public static final int PUSH_CONSTANTS_SIZE = LAYOUT_OFFSET + Integer.BYTES;

        /** Capture the sky into the source cube's first level. */
        public static final int MODE_CAPTURE = 0;

        /** Fill in a level of the source cube from the one before. */
        public static final int MODE_DOWNSAMPLE = 1;

        /** Prefilter a level of the environment cube. */
        public static final int MODE_PREFILTER = 2;

        /** Take the harmonics, one workgroup. */
        public static final int MODE_HARMONICS = 3;

        /** The width and height of each compute workgroup, in texels. */
        public static final int WORKGROUP_SIZE = 8;

        /** Private constructor so this class is not instantiated. */
        private Environment() {
            cutItOut();
        }
    }

    /**
     * Physical sky shader variables, see {@code atmosphere.comp}. Its pipeline shares the layout of
     * {@link Environment}'s.
     */
    public static class Atmosphere {
        /** Toward the sun and the camera's distance from the planet's center, a vec4. */
        public static final int SUN_AND_RADIUS_OFFSET = 0;

        /** The ground's albedo, a vec4. */
        public static final int GROUND_ALBEDO_OFFSET = 4 * Float.BYTES;

        /** Which table is filled. */
        public static final int MODE_OFFSET = 8 * Float.BYTES;

        /** Fill the transmittance table. */
        public static final int MODE_TRANSMITTANCE = 0;

        /** Fill the multiple scattering table. */
        public static final int MODE_MULTIPLE_SCATTERING = 1;

        /** Fill the sky view table. */
        public static final int MODE_SKY_VIEW = 2;

        /** Private constructor so this class is not instantiated. */
        private Atmosphere() {
            cutItOut();
        }
    }

    /** Automatic exposure shader variables, see {@code auto_exposure.comp}. */
    public static class AutoExposure {
        /** The histogram's address. */
        public static final int HISTOGRAM_OFFSET = 0;

        /** The state's address. */
        public static final int STATE_OFFSET = Long.BYTES;

        /** The scene color's bindless slot. */
        public static final int SCENE_COLOR_INDEX_OFFSET = 2 * Long.BYTES;

        /** The width metered. */
        public static final int WIDTH_OFFSET = SCENE_COLOR_INDEX_OFFSET + Integer.BYTES;

        /** The height metered. */
        public static final int HEIGHT_OFFSET = WIDTH_OFFSET + Integer.BYTES;

        /** Which pass runs. */
        public static final int MODE_OFFSET = HEIGHT_OFFSET + Integer.BYTES;

        /** Seconds since the last frame. */
        public static final int DELTA_SECONDS_OFFSET = MODE_OFFSET + Integer.BYTES;

        /** How fast it adapts toward brighter. */
        public static final int SPEED_UP_OFFSET = DELTA_SECONDS_OFFSET + Float.BYTES;

        /** How fast it adapts toward darker. */
        public static final int SPEED_DOWN_OFFSET = SPEED_UP_OFFSET + Float.BYTES;

        /** The least exposure. */
        public static final int MIN_EXPOSURE_OFFSET = SPEED_DOWN_OFFSET + Float.BYTES;

        /** The most exposure. */
        public static final int MAX_EXPOSURE_OFFSET = MIN_EXPOSURE_OFFSET + Float.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE = MAX_EXPOSURE_OFFSET + Float.BYTES;

        /** Count the scene's luminance into the histogram. */
        public static final int MODE_HISTOGRAM = 0;

        /** Meter the histogram and adapt, one workgroup. */
        public static final int MODE_ADAPT = 1;

        /** The width and height of a histogram workgroup, in pixels. */
        public static final int WORKGROUP_SIZE = 16;

        /** Private constructor so this class is not instantiated. */
        private AutoExposure() {
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

    /** Water surface shader variables, see {@code water.vert} and {@code water.frag}. */
    public static class Water {
        /** The tile list's address, see {@link #TILE_SIZE}. */
        public static final int PUSH_CONSTANT_TILES_OFFSET = 0;

        /** The wave climates' address, see {@link #CLIMATE_SIZE}. */
        public static final int PUSH_CONSTANT_CLIMATES_OFFSET =
                PUSH_CONSTANT_TILES_OFFSET + Long.BYTES;

        /** The wave profiles' address. */
        public static final int PUSH_CONSTANT_PROFILES_OFFSET =
                PUSH_CONSTANT_CLIMATES_OFFSET + Long.BYTES;

        /** The wave amplitude grid's address. */
        public static final int PUSH_CONSTANT_GRID_OFFSET =
                PUSH_CONSTANT_PROFILES_OFFSET + Long.BYTES;

        /** The size of the area drawn, in pixels, two floats. */
        public static final int PUSH_CONSTANT_SCREEN_SIZE_OFFSET =
                PUSH_CONSTANT_GRID_OFFSET + Long.BYTES;

        /** The bindless index of the scene's color behind the water. */
        public static final int PUSH_CONSTANT_BEHIND_COLOR_OFFSET =
                PUSH_CONSTANT_SCREEN_SIZE_OFFSET + 2 * Float.BYTES;

        /** The bindless index of the scene's depth behind the water. */
        public static final int PUSH_CONSTANT_BEHIND_DEPTH_OFFSET =
                PUSH_CONSTANT_BEHIND_COLOR_OFFSET + Integer.BYTES;

        /** The surface's roughness. */
        public static final int PUSH_CONSTANT_ROUGHNESS_OFFSET =
                PUSH_CONSTANT_BEHIND_DEPTH_OFFSET + Integer.BYTES;

        /** How wide a pixel is seen from the camera, in radians. */
        public static final int PUSH_CONSTANT_PIXEL_ANGLE_OFFSET =
                PUSH_CONSTANT_ROUGHNESS_OFFSET + Float.BYTES;

        /** The grid's corner relative to the camera, in meters, two floats. */
        public static final int PUSH_CONSTANT_GRID_CORNER_OFFSET =
                PUSH_CONSTANT_PIXEL_ANGLE_OFFSET + Float.BYTES;

        /** The grid's corner in world cells, two ints. */
        public static final int PUSH_CONSTANT_GRID_CELL_OFFSET =
                PUSH_CONSTANT_GRID_CORNER_OFFSET + 2 * Float.BYTES;

        /** The ripples' climate. */
        public static final int PUSH_CONSTANT_RIPPLE_CLIMATE_OFFSET =
                PUSH_CONSTANT_GRID_CELL_OFFSET + 2 * Integer.BYTES;

        /** Nonzero if the grid was stepped this frame. */
        public static final int PUSH_CONSTANT_GRID_ON_OFFSET =
                PUSH_CONSTANT_RIPPLE_CLIMATE_OFFSET + Integer.BYTES;

        /** The waves' time, in seconds, kept to a flow cycle. */
        public static final int PUSH_CONSTANT_WAVE_TIME_OFFSET =
                PUSH_CONSTANT_GRID_ON_OFFSET + Integer.BYTES;

        /** The frame's number. */
        public static final int PUSH_CONSTANT_FRAME_OFFSET =
                PUSH_CONSTANT_WAVE_TIME_OFFSET + Float.BYTES;

        /** The size of the push constants, which the vertex and fragment shaders share. */
        public static final int PUSH_CONSTANTS_SIZE = PUSH_CONSTANT_FRAME_OFFSET + Integer.BYTES;

        /** The grid's step: the last grid's address, then the next's, the mask's, disturbances'. */
        public static final int WAVELETS_LAST_OFFSET = 0;

        public static final int WAVELETS_NEXT_OFFSET = Long.BYTES;

        public static final int WAVELETS_MASK_OFFSET = 2 * Long.BYTES;

        public static final int WAVELETS_DISTURBANCES_OFFSET = 3 * Long.BYTES;

        /** The grid's corner now and at the last step, in world cells, two ints each. */
        public static final int WAVELETS_CORNER_OFFSET = 4 * Long.BYTES;

        public static final int WAVELETS_LAST_CORNER_OFFSET =
                WAVELETS_CORNER_OFFSET + 2 * Integer.BYTES;

        /** How long the step is, in seconds. */
        public static final int WAVELETS_SECONDS_OFFSET =
                WAVELETS_LAST_CORNER_OFFSET + 2 * Integer.BYTES;

        /** How many disturbances there are. */
        public static final int WAVELETS_DISTURBANCE_COUNT_OFFSET =
                WAVELETS_SECONDS_OFFSET + Float.BYTES;

        /** Nonzero if the last grid can be stepped from. */
        public static final int WAVELETS_LAST_VALID_OFFSET =
                WAVELETS_DISTURBANCE_COUNT_OFFSET + Integer.BYTES;

        /** The wind climates' address, after 4 bytes of padding, see {@link #CLIMATE_SIZE}. */
        public static final int WAVELETS_CLIMATES_OFFSET =
                WAVELETS_LAST_VALID_OFFSET + Integer.BYTES + Integer.BYTES;

        /** The wave paints' address. */
        public static final int WAVELETS_PAINTS_OFFSET = WAVELETS_CLIMATES_OFFSET + Long.BYTES;

        /** How many wave paints there are. */
        public static final int WAVELETS_PAINT_COUNT_OFFSET = WAVELETS_PAINTS_OFFSET + Long.BYTES;

        /** The size of the step's push constants. */
        public static final int WAVELETS_CONSTANTS_SIZE =
                WAVELETS_PAINT_COUNT_OFFSET + Integer.BYTES;

        /** Where in a wave climate each direction's amplitude is, 16 floats. */
        public static final int CLIMATE_AMPLITUDES = 0;

        /** Where in it how far along each direction's profile the camera stands is, 16 floats. */
        public static final int CLIMATE_OFFSETS = WaveMath.DIRECTIONS * Float.BYTES;

        /** Where in it the slope variance each layer leaves out is, a vec4. */
        public static final int CLIMATE_MISSING_VARIANCE = 2 * WaveMath.DIRECTIONS * Float.BYTES;

        /**
         * Where in it which depth's profiles each of {@link WaveMath#DEPTH_LEVELS} reads is, an
         * ivec4.
         */
        public static final int CLIMATE_LEVELS = CLIMATE_MISSING_VARIANCE + 4 * Float.BYTES;

        /**
         * Where in it the amplitude of its strongest direction is, the first of a vec4: the wave
         * grid keeps the wind's waves as shares of it.
         */
        public static final int CLIMATE_PEAK = CLIMATE_LEVELS + 4 * Integer.BYTES;

        /** Where in it the Jacobian its waves break into foam under is, after the peak. */
        public static final int CLIMATE_FOAM_JACOBIAN = CLIMATE_PEAK + Float.BYTES;

        /** The size of one wave climate. */
        public static final int CLIMATE_SIZE = CLIMATE_PEAK + 4 * Float.BYTES;

        /**
         * The waves pass: the samples' address, the profiles', the climates', and how many waves a
         * climate has.
         */
        public static final int WAVES_SAMPLES_OFFSET = 0;

        /** The profiles' address. */
        public static final int WAVES_PROFILES_OFFSET = Long.BYTES;

        /** The climates' address, see {@link #CLIMATE_SIZE}. */
        public static final int WAVES_CLIMATES_OFFSET = 2 * Long.BYTES;

        /** How many waves each climate has. */
        public static final int WAVES_COUNT_OFFSET = 3 * Long.BYTES;

        /** The size of the waves pass's push constants. */
        public static final int WAVES_CONSTANTS_SIZE = WAVES_COUNT_OFFSET + Integer.BYTES;

        /** The size of one wave sample: its wavenumber, amplitude, phase and wavelength. */
        public static final int WAVE_SAMPLE_SIZE = 4 * Float.BYTES;

        /**
         * The size of one profile sample: its height, slope, along-profile displacement and
         * Jacobian sum, as half floats.
         */
        public static final int PROFILE_SAMPLE_SIZE = 2 * Float.BYTES;

        /** Where in a tile's entry its corner relative to the camera is, a vec4. */
        public static final int TILE_OFFSET = 0;

        /** Where its absorption at red, green and blue is, a vec4. */
        public static final int TILE_ABSORPTION = 16;

        /** Where its scattering is, a vec4. */
        public static final int TILE_SCATTERING = 32;

        /** Where its backscattering is, a vec4. */
        public static final int TILE_BACKSCATTERING = 48;

        /** The size of one tile's entry. */
        public static final int TILE_SIZE = 64;

        /**
         * The size of a vertex: its place relative to its tile's corner, its tile, its calm factor
         * and its side's normal, see {@link WaterMesh}.
         */
        public static final int VERTEX_SIZE = WaterMesh.VERTEX_FLOATS * Float.BYTES;

        /** Private constructor so this class is not instantiated. */
        private Water() {
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

        /**
         * Where the hand over from sections' translucent blocks to their volume bricks starts and
         * how long it is, two floats.
         */
        public static final int PUSH_CONSTANT_VOLUME_FADE_OFFSET =
                PUSH_CONSTANT_TILES_OFFSET + 2 * Float.BYTES;

        /** The frame number, for the hand over's dither. */
        public static final int PUSH_CONSTANT_FRAME_OFFSET =
                PUSH_CONSTANT_VOLUME_FADE_OFFSET + 2 * Float.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE = PUSH_CONSTANT_FRAME_OFFSET + Integer.BYTES;

        /**
         * The constant_id of {@code VOLUME_FADE} in {@code translucent.frag} and {@code
         * oit_splat.frag}: whether they draw sections' baked blocks, which hand over to bricks.
         */
        public static final int VOLUME_FADE_CONSTANT_ID = 0;

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

        /**
         * Where the hand over from sections' translucent blocks to their volume bricks starts and
         * how long it is, two floats, splatting only.
         */
        public static final int PUSH_CONSTANT_VOLUME_FADE_OFFSET =
                PUSH_CONSTANT_LOG_SCALE_OFFSET + Float.BYTES;

        /** The frame number, for the hand over's dither, splatting only. */
        public static final int PUSH_CONSTANT_FRAME_OFFSET =
                PUSH_CONSTANT_VOLUME_FADE_OFFSET + 2 * Float.BYTES;

        /** The size of the splat's push constants in bytes. */
        public static final int SPLAT_PUSH_CONSTANTS_SIZE =
                PUSH_CONSTANT_FRAME_OFFSET + Integer.BYTES;

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

    /** Temporal anti-aliasing shader variables, see {@code taa.frag}. */
    public static class Taa {
        /** From this frame's clip space, without jitter, to last frame's: a mat4. */
        public static final int PUSH_CONSTANT_REPROJECTION_OFFSET = 0;

        /** The bindless slot of the new frame's color. */
        public static final int PUSH_CONSTANT_COLOR_INDEX_OFFSET = 4 * 4 * Float.BYTES;

        /** The bindless slot of the history. */
        public static final int PUSH_CONSTANT_HISTORY_INDEX_OFFSET =
                PUSH_CONSTANT_COLOR_INDEX_OFFSET + Integer.BYTES;

        /** The bindless slot of this frame's depth. */
        public static final int PUSH_CONSTANT_DEPTH_INDEX_OFFSET =
                PUSH_CONSTANT_HISTORY_INDEX_OFFSET + Integer.BYTES;

        /** The bindless slot of last frame's depth. */
        public static final int PUSH_CONSTANT_PREVIOUS_DEPTH_INDEX_OFFSET =
                PUSH_CONSTANT_DEPTH_INDEX_OFFSET + Integer.BYTES;

        /** The bindless slot of the transparency pass's summed extinction. */
        public static final int PUSH_CONSTANT_EXTINCTION_INDEX_OFFSET =
                PUSH_CONSTANT_PREVIOUS_DEPTH_INDEX_OFFSET + Integer.BYTES;

        /** The flags, {@link #FLAG_HISTORY} and {@link #FLAG_VIEW}. */
        public static final int PUSH_CONSTANT_FLAGS_OFFSET =
                PUSH_CONSTANT_EXTINCTION_INDEX_OFFSET + Integer.BYTES;

        /** The size of the area drawn, in pixels: a vec2. */
        public static final int PUSH_CONSTANT_SCREEN_SIZE_OFFSET =
                PUSH_CONSTANT_FLAGS_OFFSET + Integer.BYTES;

        /** What the colors are multiplied by before tone mapping. */
        public static final int PUSH_CONSTANT_EXPOSURE_OFFSET =
                PUSH_CONSTANT_SCREEN_SIZE_OFFSET + 2 * Float.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE = PUSH_CONSTANT_EXPOSURE_OFFSET + Float.BYTES;

        /** The flag for when the history can be read. */
        public static final int FLAG_HISTORY = 1;

        /** The flag for showing how much history each pixel has. */
        public static final int FLAG_VIEW = 2;

        /** Private constructor so this class is not instantiated. */
        private Taa() {
            cutItOut();
        }
    }

    /** Tone mapping shader variables, see {@code tonemap.frag}. */
    public static class ToneMap {
        /** The bindless slot of the scene color. */
        public static final int PUSH_CONSTANT_SCENE_COLOR_INDEX_OFFSET = 0;

        /** What the scene color is multiplied by before tone mapping. */
        public static final int PUSH_CONSTANT_EXPOSURE_OFFSET = Integer.BYTES;

        /** The automatic exposure's state buffer's address, see {@code AutoExposure}. */
        public static final int PUSH_CONSTANT_EXPOSURE_STATE_OFFSET = 2 * Integer.BYTES;

        /** Whether to multiply by the automatic exposure too, 1 if so. */
        public static final int PUSH_CONSTANT_AUTO_EXPOSURE_OFFSET =
                PUSH_CONSTANT_EXPOSURE_STATE_OFFSET + Long.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE =
                PUSH_CONSTANT_AUTO_EXPOSURE_OFFSET + Integer.BYTES;

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
                BASE_COLOR_INDEX_OFFSET + (GBuffer.MATERIAL + 1) * Integer.BYTES;

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

        /**
         * Offset in the push constants of the scaling of the window's UI, which converts from
         * display coordinates to Normalized Device Coordinates of (-1, 1). Aligned to 8 bytes after
         * the three offsets, as a vec2 is.
         */
        public static final int PUSH_CONSTANT_SCALE_OFFSET = 4 * Integer.BYTES;

        /**
         * Offset in the push constants of the top left of the viewport being rendered, which is
         * subtracted from the absolute positions in the draw lists.
         */
        public static final int PUSH_CONSTANT_DISPLAY_POSITION_OFFSET = 6 * Integer.BYTES;

        /** Offset in the push constants of the framebuffer pixels per display unit. */
        public static final int PUSH_CONSTANT_FRAMEBUFFER_SCALE_OFFSET = 8 * Integer.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE = 10 * Integer.BYTES;

        /** Binding point for the uniform buffer. */
        public static final int UNIFORMS_BINDING = 0;

        /** The offset in the uniform buffer for the font texture. */
        public static final int UNIFORM_BUFFER_FONT_TEXTURE_OFFSET = 0;

        /** The size of the uniforms buffer, a single int padded out to a std140 vec4. */
        public static final int UNIFORMS_BUFFER_SIZE = 4 * Integer.BYTES;

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
             * The bindless index of the cascade's map of what moves, at the same place as its
             * shadow map, or -1 for none this frame.
             */
            public static final int DYNAMIC_MAP_INDEX = TEXEL_SIZE + Float.BYTES;

            /**
             * The distance between elements of an array of these structs. Array elements are padded
             * out to 16 bytes under std140 rules.
             */
            public static final int ARRAY_STRIDE =
                    (int) SharedBuffer.align(DYNAMIC_MAP_INDEX + Integer.BYTES);

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

            /** The index of the point shadow map the light uses, or -1 for none. */
            public static final int SHADOW_MAP = 14 * Float.BYTES;

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
            /** The fog's color, its scattering albedo. */
            public static final int COLOR = 0;

            /** Its extinction at and below its base height, per meter. */
            public static final int DENSITY = 3 * Float.BYTES;

            /** {@link #ENABLED_FOG}, with {@link #ENABLED_HAZE} for the physical sky's haze. */
            public static final int ENABLED = 4 * Float.BYTES;

            /** The bit of {@link #ENABLED} for the fog volume being drawn. */
            public static final int ENABLED_FOG = 1;

            /** The bit of {@link #ENABLED} for the physical sky's air and haze being added in. */
            public static final int ENABLED_HAZE = 2;

            /** The bit of {@link #ENABLED} for the scene's own height fog being in the volume. */
            public static final int ENABLED_GLOBAL = 4;

            /** The fog volume's slices' log scale, {@code VoxelOitMath.logScale}. */
            public static final int LOG_SCALE = 5 * Float.BYTES;

            /**
             * Its height falloff per meter, base height relative to the camera, and phase
             * anisotropy, a vec3 on a 16 byte boundary under std140 rules.
             */
            public static final int SHAPE = (int) SharedBuffer.align(LOG_SCALE + 2 * Float.BYTES);

            /** The size of the struct in bytes, padded out to 16 bytes under std140 rules. */
            public static final int SIZEOF = (int) SharedBuffer.align(SHAPE + 3 * Float.BYTES);

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

        /**
         * The offset into the uniforms for the sky occlusion map's matrix, from render space to the
         * map. A matrix starts on a 16 byte boundary under std140 rules.
         */
        public static final int SKY_PROJECTION_VIEW_MATRIX_OFFSET =
                (int) SharedBuffer.align(CLUSTER_LOG_SCALE_OFFSET + Float.BYTES);

        /** The offset into the uniforms for the sky map's bindless index, -1 for none. */
        public static final int SKY_MAP_INDEX_OFFSET =
                SKY_PROJECTION_VIEW_MATRIX_OFFSET + 4 * 4 * Float.BYTES;

        /** The offset into the uniforms for how far the sky map reaches down, in meters. */
        public static final int SKY_DEPTH_RANGE_OFFSET = SKY_MAP_INDEX_OFFSET + Integer.BYTES;

        /** The offset into the uniforms for the least sky light a covered point gets. */
        public static final int SKY_LIGHT_FLOOR_OFFSET = SKY_DEPTH_RANGE_OFFSET + Float.BYTES;

        /** The offset into the uniforms for whether to show sky light in gray. */
        public static final int SKY_OCCLUSION_VIEW_OFFSET = SKY_LIGHT_FLOOR_OFFSET + Float.BYTES;

        /** The offset into the uniforms for the point shadow atlas of what doesn't move. */
        public static final int POINT_ATLAS_INDEX_OFFSET =
                SKY_OCCLUSION_VIEW_OFFSET + Integer.BYTES;

        /** The offset into the uniforms for the point shadow atlas of what moves. */
        public static final int POINT_DYNAMIC_ATLAS_INDEX_OFFSET =
                POINT_ATLAS_INDEX_OFFSET + Integer.BYTES;

        /**
         * The offset into the uniforms for each occupancy level's origin in render space and its
         * voxel size, an array of vec4 starting on 16 bytes.
         */
        public static final int OCCUPANCY_ORIGINS_OFFSET =
                (int) SharedBuffer.align(POINT_DYNAMIC_ATLAS_INDEX_OFFSET + Integer.BYTES);

        /** The offset into the uniforms for each occupancy level's origin texel, ivec4s. */
        public static final int OCCUPANCY_TEXELS_OFFSET =
                OCCUPANCY_ORIGINS_OFFSET + OccupancyClipmap.LEVELS * 4 * Float.BYTES;

        /** The offset into the uniforms for whether the occupancy volume can be read. */
        public static final int OCCUPANCY_ENABLED_OFFSET =
                OCCUPANCY_TEXELS_OFFSET + OccupancyClipmap.LEVELS * 4 * Integer.BYTES;

        /** The offset into the uniforms for whether to show the occupancy view. */
        public static final int OCCUPANCY_VIEW_OFFSET = OCCUPANCY_ENABLED_OFFSET + Integer.BYTES;

        /**
         * The offset into the uniforms for how far the occupancy volume's ambient occlusion
         * reaches, in meters, 0 for none.
         */
        public static final int OCCUPANCY_AO_REACH_OFFSET = OCCUPANCY_VIEW_OFFSET + Integer.BYTES;

        /** The offset into the uniforms for whether to show the ambient occlusion view. */
        public static final int OCCUPANCY_AO_VIEW_OFFSET = OCCUPANCY_AO_REACH_OFFSET + Float.BYTES;

        /**
         * The offset into the uniforms for how many lights without a shadow map march a ray through
         * the occupancy volume a pixel, at most {@link #MAX_OCCUPANCY_RAYS}.
         */
        public static final int OCCUPANCY_RAYS_OFFSET = OCCUPANCY_AO_VIEW_OFFSET + Integer.BYTES;

        /** The offset into the uniforms for whether to show the occupancy rays view. */
        public static final int OCCUPANCY_RAY_VIEW_OFFSET = OCCUPANCY_RAYS_OFFSET + Integer.BYTES;

        /**
         * The most lights a pixel marches occupancy rays for. Matches OCCUPANCY_MAX_RAYS in
         * occupancy.glsl.
         */
        public static final int MAX_OCCUPANCY_RAYS = 8;

        /**
         * The offset into the uniforms for the bindless slot of the g-buffer's emission and
         * occlusion texture.
         */
        public static final int EMISSION_SAMPLER_INDEX_OFFSET =
                OCCUPANCY_RAY_VIEW_OFFSET + Integer.BYTES;

        /**
         * The offset into the uniforms for whether ambient light comes from the sky's environment,
         * see {@link com.ikalagaming.graphics.vulkan.Environment}, rather than the same from every
         * direction.
         */
        public static final int ENVIRONMENT_ENABLED_OFFSET =
                EMISSION_SAMPLER_INDEX_OFFSET + Integer.BYTES;

        /**
         * The offset into the uniforms for what the environment is scaled by, a vec4: in xyz the
         * scale, used when w is 1, as for the physical sky, whose environment is per unit of the
         * sun's light; with w 0 the light stage scales it to the ambient light instead.
         */
        public static final int ENVIRONMENT_SCALE_OFFSET =
                (int) SharedBuffer.align(ENVIRONMENT_ENABLED_OFFSET + Integer.BYTES);

        /**
         * The offset into the uniforms for whether what the occupancy volume holds bends the sky's
         * diffuse light toward where the sky shows and hides its reflections, 1 if so.
         */
        public static final int ENVIRONMENT_OCCLUSION_OFFSET =
                ENVIRONMENT_SCALE_OFFSET + 4 * Float.BYTES;

        /** The size of the uniforms buffer, padded out to 16 bytes under std140 rules. */
        public static final int UNIFORMS_BUFFER_SIZE =
                (int) SharedBuffer.align(ENVIRONMENT_OCCLUSION_OFFSET + Integer.BYTES);

        /** Uniforms buffer binding. */
        public static final int UNIFORMS_BINDING = 0;

        /** The lights buffer binding, holding the point lights and spotlights. */
        public static final int LIGHTS_BINDING = 1;

        /** Binding for the materials buffer. */
        public static final int MATERIALS_BINDING = 2;

        /** Binding for the cluster buffer that light culling fills. */
        public static final int CLUSTERS_BINDING = 3;

        /** The point shadow map entries binding, see {@link PointShadows}. */
        public static final int POINT_SHADOWS_BINDING = 4;

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

        /**
         * Push constant offset for the device address of this frame's level of detail world table,
         * where each world's camera is, which only {@code scene_lod.vert} reads.
         */
        public static final int PUSH_CONSTANT_LOD_WORLDS_OFFSET = 0;

        /**
         * Push constant offset for the device address of the level of detail cluster table, right
         * after the world table's, which {@code scene_lod.vert} reads each draw's cluster from.
         */
        public static final int PUSH_CONSTANT_LOD_CLUSTERS_OFFSET =
                PUSH_CONSTANT_LOD_WORLDS_OFFSET + Long.BYTES;

        /** The size of the push constants the vertex shaders read, the level of detail's. */
        public static final int LOD_PUSH_CONSTANTS_SIZE =
                PUSH_CONSTANT_LOD_CLUSTERS_OFFSET + Long.BYTES;

        /**
         * Push constant offset for where the hand over from sections' cut out blocks to their
         * volume bricks starts and how long it is, two floats, which {@code scene.frag} reads.
         */
        public static final int PUSH_CONSTANT_VOLUME_FADE_OFFSET = LOD_PUSH_CONSTANTS_SIZE;

        /** Push constant offset for the frame number, for the hand over's dither. */
        public static final int PUSH_CONSTANT_FRAME_OFFSET =
                PUSH_CONSTANT_VOLUME_FADE_OFFSET + 2 * Float.BYTES;

        /**
         * Push constant offset for the debug flags {@code scene.frag} reads: nonzero to color
         * sections' cut out meshes by source, see {@code DebugVisualizers.volumeSources}.
         */
        public static final int PUSH_CONSTANT_DEBUG_OFFSET =
                PUSH_CONSTANT_FRAME_OFFSET + Integer.BYTES;

        /**
         * Push constant offset for the device address of the coverage volume brick table, which
         * {@code scene_volume.frag} reads.
         */
        public static final int PUSH_CONSTANT_BRICKS_OFFSET =
                PUSH_CONSTANT_DEBUG_OFFSET + Integer.BYTES;

        /** The size of the push constants in bytes. */
        public static final int PUSH_CONSTANTS_SIZE = PUSH_CONSTANT_BRICKS_OFFSET + Long.BYTES;

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

            /** Offset in bytes to the emitted light, a linear vec3. */
            public static final int EMISSIVE = (4 + 4 + 4 + 4) * Float.BYTES;

            /** Offset in bytes to the occlusion, 1 unless a pixel's texture says otherwise. */
            public static final int OCCLUSION = EMISSIVE + 3 * Float.BYTES;

            /** Offset in bytes to the packed occlusion, roughness and metal map index. */
            public static final int OCCLUSION_ROUGHNESS_METAL_INDEX = OCCLUSION + Float.BYTES;

            /** Offset in bytes to the emissive map index. */
            public static final int EMISSIVE_INDEX =
                    OCCLUSION_ROUGHNESS_METAL_INDEX + Integer.BYTES;

            /** Offset in bytes to the flags, see {@link #FLAG_NORMAL_GREEN_DOWN}. */
            public static final int FLAGS = EMISSIVE_INDEX + Integer.BYTES;

            /** Flag: the normal map's green channel points down the texture, as in DirectX. */
            public static final int FLAG_NORMAL_GREEN_DOWN = 1;

            /** Flag: the packed occlusion, roughness and metal map's red channel is occlusion. */
            public static final int FLAG_PACKED_OCCLUSION = 2;

            /** Flag: the normal map's blue and alpha are roughness and metalness, baked. */
            public static final int FLAG_BAKED_DATA = 4;

            /** Flag: the color texture names a material and a shade per texel, see Material. */
            public static final int FLAG_INDEXED_BAKE = 8;

            /** Offset in bytes to the average metalness, see {@link #AVERAGE}. */
            public static final int AVERAGE_METALLIC = FLAGS + Integer.BYTES;

            /**
             * Offset in bytes to the average look: linear red, green and blue, then roughness, see
             * {@code material.glsl}.
             */
            public static final int AVERAGE = AVERAGE_METALLIC + Float.BYTES;

            /** Total size of the struct in bytes, padded to 16 bytes. */
            public static final int SIZEOF = AVERAGE + 4 * Float.BYTES;

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

        /**
         * Push constant offset for the device address of this frame's level of detail world table,
         * where each world's camera is, which only {@code shadow_lod.vert} reads, after the visible
         * list address.
         */
        public static final int PUSH_CONSTANT_LOD_WORLDS_OFFSET =
                PUSH_CONSTANT_VISIBLE_OFFSET + Long.BYTES;

        /**
         * Push constant offset for the device address of the level of detail cluster table, right
         * after the world table's, which {@code shadow_lod.vert} reads each draw's cluster from.
         */
        public static final int PUSH_CONSTANT_LOD_CLUSTERS_OFFSET =
                PUSH_CONSTANT_LOD_WORLDS_OFFSET + Long.BYTES;

        /** The size of the push constants the vertex shaders read, in bytes. */
        public static final int PUSH_CONSTANTS_SIZE =
                PUSH_CONSTANT_LOD_CLUSTERS_OFFSET + Long.BYTES;

        /**
         * Push constant offset for the device address of the coverage volume brick table, which
         * {@code shadow_volume.frag} reads, after what the vertex shaders read.
         */
        public static final int PUSH_CONSTANT_BRICKS_OFFSET = PUSH_CONSTANTS_SIZE;

        /** Push constant offset for the frame number, for the volumes' stochastic coverage. */
        public static final int PUSH_CONSTANT_FRAME_OFFSET =
                PUSH_CONSTANT_BRICKS_OFFSET + Long.BYTES;

        /** The size of the push constants {@code shadow_volume.frag} reads, in bytes. */
        public static final int VOLUME_PUSH_CONSTANTS_SIZE =
                PUSH_CONSTANT_FRAME_OFFSET + Integer.BYTES - PUSH_CONSTANT_BRICKS_OFFSET;

        /**
         * Push constant offset for the bindless index of the copy of a map that {@code
         * shadow_scroll.frag} shifts along, in the same range as the volumes' constants.
         */
        public static final int PUSH_CONSTANT_SCROLL_SOURCE_OFFSET = PUSH_CONSTANT_BRICKS_OFFSET;

        /** Push constant offset for the shift in texels, x then y, as two ints. */
        public static final int PUSH_CONSTANT_SCROLL_SHIFT_OFFSET =
                PUSH_CONSTANT_SCROLL_SOURCE_OFFSET + Integer.BYTES;

        /** The binding point for the model matrices buffer. */
        public static final int MODEL_MATRICES_BINDING = 0;

        /**
         * The binding point for the materials buffer, which cut out meshes' fragment shader reads
         * for their textures.
         */
        public static final int MATERIALS_BINDING = 1;

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

        /** Offset in the uniform buffer in bytes for whether to draw the physical sky, 1 if so. */
        public static final int PHYSICAL_SKY_OFFSET = TEXTURE_INDEX_OFFSET + Integer.BYTES;

        /**
         * Offset in the uniform buffer in bytes for how the texture lays the sky out, the {@link
         * com.ikalagaming.graphics.scene.SkyboxLayout} ordinal.
         */
        public static final int LAYOUT_OFFSET = PHYSICAL_SKY_OFFSET + Integer.BYTES;

        /**
         * Offset in the uniform buffer in bytes for the physical sky's sun and the camera's
         * distance from the planet's center, a vec4, see {@code atmosphere.glsl}.
         */
        public static final int SUN_AND_RADIUS_OFFSET =
                (int) SharedBuffer.align(PHYSICAL_SKY_OFFSET + Integer.BYTES);

        /** Offset in the uniform buffer in bytes for what the physical sky is scaled by, a vec4. */
        public static final int SKY_SCALE_OFFSET = SUN_AND_RADIUS_OFFSET + 4 * Float.BYTES;

        /** The size of the uniforms buffer. */
        /** The offset of whether the fog is drawn over the sky, 1 if it is. */
        public static final int FOG_ENABLED_OFFSET = SKY_SCALE_OFFSET + 4 * Float.BYTES;

        /** The size of the uniforms, padded to 16 bytes under std140 rules. */
        public static final int UNIFORMS_BUFFER_SIZE =
                (int) SharedBuffer.align(FOG_ENABLED_OFFSET + Integer.BYTES);

        /** Offset in the uniform buffer in bytes for the cameras view matrix. */
        public static final int VIEW_MATRIX_OFFSET = 4 * 4 * Float.BYTES;

        /** Private constructor so this class is not instantiated. */
        private Skybox() {
            cutItOut();
        }
    }

    /**
     * The particle passes' push constants, see {@code particle_control.comp}, {@code
     * particle_simulate.comp}, {@code particle_emit.comp} and {@code particle_draw.glsl}. Buffers
     * are device addresses, 8 bytes each.
     */
    public static class Particles {
        /** The control pass: the control buffer's address. */
        public static final int CONTROL_CONTROL_OFFSET = 0;

        /** The free list's address. */
        public static final int CONTROL_DEAD_OFFSET = CONTROL_CONTROL_OFFSET + Long.BYTES;

        /** What it does: reset, prepare or finish. */
        public static final int CONTROL_MODE_OFFSET = CONTROL_DEAD_OFFSET + Long.BYTES;

        /** How many particles the pool holds. */
        public static final int CONTROL_CAPACITY_OFFSET = CONTROL_MODE_OFFSET + Integer.BYTES;

        /** Which alive list the simulation reads. */
        public static final int CONTROL_CURRENT_OFFSET = CONTROL_CAPACITY_OFFSET + Integer.BYTES;

        /** The size of the control pass's push constants. */
        public static final int CONTROL_CONSTANTS_SIZE = CONTROL_CURRENT_OFFSET + Integer.BYTES;

        /** The control pass's mode that frees every particle. */
        public static final int MODE_RESET = 0;

        /** The mode that sets up the simulation's dispatch. */
        public static final int MODE_PREPARE = 1;

        /** The mode that sets up the billboards' draw. */
        public static final int MODE_FINISH = 2;

        /**
         * The simulation: the pool's address, then the alive list read, the one written, the free
         * list, the control buffer and the links.
         */
        public static final int SIMULATE_POOL_OFFSET = 0;

        /** The links table's address, see ParticleMath.LINKS_HEADER. */
        public static final int SIMULATE_LINKS_OFFSET = 5 * Long.BYTES;

        /** How far particles move when the anchor steps, a vec3 on 16 bytes. */
        public static final int SIMULATE_SHIFT_OFFSET = 6 * Long.BYTES;

        /** How long the frame was. */
        public static final int SIMULATE_SECONDS_OFFSET = SIMULATE_SHIFT_OFFSET + 3 * Float.BYTES;

        /** Which alive list it reads. */
        public static final int SIMULATE_CURRENT_OFFSET = SIMULATE_SECONDS_OFFSET + Float.BYTES;

        /** The hits list's address, on 8 bytes. */
        public static final int SIMULATE_HITS_OFFSET = SIMULATE_CURRENT_OFFSET + Long.BYTES;

        /**
         * The occupancy volume's finest level's corner relative to the anchor and its voxel size, a
         * vec4 on 16 bytes.
         */
        public static final int SIMULATE_OCCUPANCY_ORIGIN_OFFSET = 80;

        /** Its corner's texel and whether it can be read, an ivec4. */
        public static final int SIMULATE_OCCUPANCY_TEXEL_OFFSET =
                SIMULATE_OCCUPANCY_ORIGIN_OFFSET + 4 * Float.BYTES;

        /** The size of the simulation's push constants. */
        public static final int SIMULATE_CONSTANTS_SIZE =
                SIMULATE_OCCUPANCY_TEXEL_OFFSET + 4 * Integer.BYTES;

        /**
         * The emission: the pool's address, then the alive list written, the free list, the control
         * buffer, the frame's emitters and the links.
         */
        public static final int EMIT_POOL_OFFSET = 0;

        /** The links table's address. */
        public static final int EMIT_LINKS_OFFSET = 5 * Long.BYTES;

        /** How many emitters spawn this frame. */
        public static final int EMIT_EMITTER_COUNT_OFFSET = 6 * Long.BYTES;

        /** How many particles they spawn in all. */
        public static final int EMIT_TOTAL_OFFSET = EMIT_EMITTER_COUNT_OFFSET + Integer.BYTES;

        /** Which alive list it adds to. */
        public static final int EMIT_NEXT_OFFSET = EMIT_TOTAL_OFFSET + Integer.BYTES;

        /** The size of the emission's push constants. */
        public static final int EMIT_CONSTANTS_SIZE = EMIT_NEXT_OFFSET + Integer.BYTES;

        /**
         * The splashes: the pool's address, then the alive list written, the free list, the control
         * buffer, the links and the hits.
         */
        public static final int SPLASH_POOL_OFFSET = 0;

        /** Which alive list it adds to. */
        public static final int SPLASH_NEXT_OFFSET = 6 * Long.BYTES;

        /** The size of the splashes' push constants. */
        public static final int SPLASH_CONSTANTS_SIZE = SPLASH_NEXT_OFFSET + Integer.BYTES;

        /** The billboards: the pool's address, then the alive list drawn. */
        public static final int DRAW_POOL_OFFSET = 0;

        /** The anchor in render space, a vec3 on 16 bytes. */
        public static final int DRAW_ANCHOR_OFFSET = 2 * Long.BYTES;

        /** How far from the opaque scene particles fade, per meter of size. */
        public static final int DRAW_SOFT_SCALE_OFFSET = DRAW_ANCHOR_OFFSET + 3 * Float.BYTES;

        /** The size of the area drawn, in pixels, two floats. */
        public static final int DRAW_SCREEN_SIZE_OFFSET = DRAW_SOFT_SCALE_OFFSET + Float.BYTES;

        /** The transparency volume's tiles across and down, two floats. */
        public static final int DRAW_TILES_OFFSET = DRAW_SCREEN_SIZE_OFFSET + 2 * Float.BYTES;

        /** The slices' log scale. */
        public static final int DRAW_LOG_SCALE_OFFSET = DRAW_TILES_OFFSET + 2 * Float.BYTES;

        /** Nonzero to weight by the transparency volume. */
        public static final int DRAW_VOXEL_WEIGHTS_OFFSET = DRAW_LOG_SCALE_OFFSET + Float.BYTES;

        /** Pixels per fragment along each side. */
        public static final int DRAW_PIXEL_SCALE_OFFSET = DRAW_VOXEL_WEIGHTS_OFFSET + Integer.BYTES;

        /** The transparency volume's extinction buffer's address, for the splat. */
        public static final int DRAW_SPLAT_OFFSET = DRAW_PIXEL_SCALE_OFFSET + 2 * Integer.BYTES;

        /** Each particle's light's address. */
        public static final int DRAW_RADIANCE_OFFSET = DRAW_SPLAT_OFFSET + Long.BYTES;

        /** The size of the billboards' push constants. */
        public static final int DRAW_CONSTANTS_SIZE = DRAW_RADIANCE_OFFSET + Long.BYTES;

        /**
         * The lighting: the pool's address, then the alive list drawn, the control buffer and the
         * light list.
         */
        public static final int LIGHT_POOL_OFFSET = 0;

        /** The anchor in render space, a vec3 on 16 bytes after the four addresses. */
        public static final int LIGHT_ANCHOR_OFFSET = 4 * Long.BYTES;

        /** The size of the lighting's push constants, padded past the anchor. */
        public static final int LIGHT_CONSTANTS_SIZE = LIGHT_ANCHOR_OFFSET + 4 * Float.BYTES;

        /** Private constructor so this class is not instantiated. */
        private Particles() {
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
