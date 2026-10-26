#ifndef PARTICLE_COVER_GLSL
#define PARTICLE_COVER_GLSL

// Cover over particles around the camera, from the sky occlusion map, see SkyOcclusion: the map
// looks straight down and holds the highest thing over each place. Includers include
// particles.glsl and enable GL_EXT_nonuniform_qualifier, and bind the bindless textures as set 1.

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];

// How far below the highest thing over it a point relative to the anchor is, in meters: negative
// above it, and very negative where the map doesn't reach or there is none
float coverDepth(LinkList list, vec3 position) {
    const float OPEN = -1e9;
    if (list.skyMap < 0) {
        return OPEN;
    }
    vec4 at = list.skyMatrix * vec4(position, 1.0);
    vec3 ndc = at.xyz / at.w;
    vec2 uv = ndc.xy * 0.5 + 0.5;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThanEqual(uv, vec2(1.0)))) {
        return OPEN;
    }
    ivec2 size = textureSize(bindlessTextures[nonuniformEXT(list.skyMap)], 0);
    float stored = texelFetch(bindlessTextures[nonuniformEXT(list.skyMap)],
        ivec2(uv * vec2(size)), 0).r;
    // Depth grows downward from the map's eye
    return (ndc.z - stored) * list.skyDepthRange;
}

#endif
