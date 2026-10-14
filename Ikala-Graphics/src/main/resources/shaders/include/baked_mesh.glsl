#ifndef BAKED_MESH_GLSL
#define BAKED_MESH_GLSL

// Reading baked section meshes straight from the baked geometry buffers, see BakedVertex: 24-byte
// vertices, so 6 words; positions in 1/1024 steps from the section's corner, an octahedral normal
// at bytes 8 to 11, and the material at bytes 20 and 21. Needs GL_EXT_buffer_reference.

// The vertex or index buffer, as words
layout(buffer_reference, std430, buffer_reference_align = 4) readonly buffer BakedWords {
    uint words[];
};

const uint BAKED_VERTEX_WORDS = 6;
const float BAKED_STEP = 1.0 / 1024.0;

int bakedSignExtend(uint bits)
{
    return int(bits << 16) >> 16;
}

// A vertex's position from its section's corner, in meters
vec3 bakedPosition(BakedWords vertices, uint vertex)
{
    uint w0 = vertices.words[vertex * BAKED_VERTEX_WORDS];
    uint w1 = vertices.words[vertex * BAKED_VERTEX_WORDS + 1];
    return vec3(bakedSignExtend(w0 & 0xFFFFu), bakedSignExtend(w0 >> 16), bakedSignExtend(w1 & 0xFFFFu))
        * BAKED_STEP;
}

// A vertex's normal, decoded as BakedVertex.fromOctahedral does
vec3 bakedNormal(BakedWords vertices, uint vertex)
{
    uint w = vertices.words[vertex * BAKED_VERTEX_WORDS + 2];
    vec2 o = max(vec2(-1.0), vec2(bakedSignExtend(w & 0xFFFFu), bakedSignExtend(w >> 16)) / 32767.0);
    vec3 n = vec3(o, 1.0 - abs(o.x) - abs(o.y));
    if (n.z < 0) {
        n.xy = (1.0 - abs(o.yx)) * vec2(o.x >= 0 ? 1.0 : -1.0, o.y >= 0 ? 1.0 : -1.0);
    }
    return normalize(n);
}

// A vertex's material index
uint bakedMaterial(BakedWords vertices, uint vertex)
{
    return vertices.words[vertex * BAKED_VERTEX_WORDS + 5] & 0xFFFFu;
}

#endif
