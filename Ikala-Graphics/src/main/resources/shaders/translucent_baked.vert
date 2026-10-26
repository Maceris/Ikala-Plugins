#version 460
#extension GL_GOOGLE_include_directive : require

// The baked scene vertex shader for the transparent stage, whose sets 0 and 1 are the light stage's
// and the bindless textures, so the scene stage's bindings move to set 2. Its fragment shaders read
// only the surface's own outputs.
#define SCENE_SET 2
#define SCENE_TRANSPARENT
#include "scene_baked_vertex.glsl"
