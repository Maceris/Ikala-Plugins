#version 460
#extension GL_EXT_nonuniform_qualifier : enable

// Shifts a cached sun cascade's map along as the cascade moves across the light by whole texels
// (see SunShadowCache.Redraw.SCROLL): each texel takes the depth of the texel its contents moved
// from, in a copy of the map, or the far plane where that was outside the map, in the strip that is
// drawn next. Moving across the light leaves every depth as it is.

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];

// After what the shadow vertex shaders read, see ShaderBindings.Shadow
layout(push_constant) uniform ScrollConstants {
    layout(offset = 88) uint source;
    int shiftX;
    int shiftY;
};

void main()
{
    ivec2 from = ivec2(gl_FragCoord.xy) - ivec2(shiftX, shiftY);
    ivec2 size = textureSize(bindlessTextures[nonuniformEXT(source)], 0);
    if (any(lessThan(from, ivec2(0))) || any(greaterThanEqual(from, size))) {
        gl_FragDepth = 1.0;
        return;
    }
    gl_FragDepth = texelFetch(bindlessTextures[nonuniformEXT(source)], from, 0).r;
}
