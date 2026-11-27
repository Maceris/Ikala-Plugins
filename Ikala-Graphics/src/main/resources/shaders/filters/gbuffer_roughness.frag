#version 460
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference_uvec2 : require
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

// Debug view of the roughness each pixel is lit with, black smooth to white rough. Empty pixels are
// dark blue.

#include "filter_surface.glsl"

layout(location = 0) in vec2 outTextCoord;
layout(location = 0) out vec4 color;

// Behind everything, apart from black and white
const vec3 EMPTY = vec3(0.02, 0.03, 0.08);

void main()
{
    Material material;
    if (!surfaceAt(ivec2(gl_FragCoord.xy), material)) {
        color = vec4(EMPTY, 1);
        return;
    }
    color = vec4(vec3(material.roughness), 1);
}
