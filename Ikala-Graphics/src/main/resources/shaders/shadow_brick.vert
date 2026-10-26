#version 460
#extension GL_EXT_buffer_reference : require
#extension GL_GOOGLE_include_directive : require

// Volume bricks in a shadow map, for sections whose cut out and translucent meshes are off the GPU:
// each brick's box, 36 vertices from the vertex index as volume.vert places them, one instance a
// brick in the frame's shadow list, which shadow_brick.frag walks along the map's direction.

#include "volume.glsl"

layout(push_constant) uniform ShadowConstants {
    mat4 projViewMatrix;
    layout(offset = 72) VolumeDraws draws;
};

// Where the vertex is, in render space
layout(location = 0) out vec3 outRender;
// The box's lowest corner, in render space
layout(location = 1) flat out vec3 outCorner;
// The brick's slot in the atlas
layout(location = 2) flat out uint outSlot;
// Which way the map looks, in render space
layout(location = 3) flat out vec3 outDirection;
// How much the map's depth grows per meter along its direction: the maps are orthographic, so the
// fragment shader finds a point's depth from its own without the matrix
layout(location = 4) flat out float outDepthStep;
// How wide the brick's voxels are, in meters
layout(location = 5) flat out float outScale;

// The 8 corners by bit (x, y, z), and the 12 triangles facing out, counter-clockwise from outside
const int CORNERS[36] = int[36](
    1, 3, 7, 1, 7, 5,
    0, 4, 6, 0, 6, 2,
    2, 6, 7, 2, 7, 3,
    0, 1, 5, 0, 5, 4,
    4, 5, 7, 4, 7, 6,
    0, 2, 3, 0, 3, 1);

void main()
{
    VolumeDraw draw = draws.volumeDraws[gl_InstanceIndex];
    int corner = CORNERS[gl_VertexIndex];
    float scale = volumeDrawScale(draw);
    vec3 offset = vec3(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1) * float(VOLUME_BRICK)
        * scale;
    vec3 render = draw.corner + offset;
    outRender = render;
    outCorner = draw.corner;
    outSlot = volumeDrawSlot(draw);
    outScale = scale;
    gl_Position = projViewMatrix * vec4(render, 1.0);
    // The map's direction: from its near end to its far end at this vertex's spot
    mat4 inverseMatrix = inverse(projViewMatrix);
    vec4 near = inverseMatrix * vec4(gl_Position.xy / gl_Position.w, 0.0, 1.0);
    vec4 far = inverseMatrix * vec4(gl_Position.xy / gl_Position.w, 1.0, 1.0);
    outDirection = normalize(far.xyz / far.w - near.xyz / near.w);
    outDepthStep = (projViewMatrix * vec4(outDirection, 0.0)).z;
}
