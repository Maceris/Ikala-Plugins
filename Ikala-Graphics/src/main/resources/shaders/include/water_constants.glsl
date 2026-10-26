#ifndef WATER_CONSTANTS_GLSL
#define WATER_CONSTANTS_GLSL

// The water stage's push constants, which its vertex and fragment shaders share, see WaterRender
// and ShaderBindings.Water. Include after water.glsl.

layout(push_constant) uniform Constants {
    WaterTileList tileList;
    WaveClimateList climateList;
    WaveProfileList profileList;
    // The wave amplitude grid, as this frame's step left it
    WaveGridList gridList;
    // The drawn area, in pixels
    vec2 screenSize;
    // The scene's color and depth behind the water, bindless
    int behindColorIndex;
    int behindDepthIndex;
    // A calm surface's roughness, for the sun's highlight and the sky's reflection
    float roughness;
    // How wide a pixel is seen from the camera, in radians
    float pixelAngle;
    // The grid's corner relative to the camera, in meters, and in world cells
    vec2 gridCorner;
    ivec2 gridCornerCell;
    // The ripples' climate, and nonzero if the grid has been stepped this frame
    int rippleClimate;
    int gridOn;
};

#endif
