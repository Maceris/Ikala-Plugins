#version 460
#extension GL_EXT_buffer_reference : require
#extension GL_GOOGLE_include_directive : require

// The box of a volume brick to march through, see volume.glsl: 36 vertices a brick, its corners
// from the vertex index, one instance per draw in the frame's list. Both the splat and the color
// pass use it. Bricks are only drawn past the volume distance, so the camera is never inside one.

#include "volume.glsl"

// The scene stage's set, as the transparent stage binds it
layout(set = 2, binding = 0) uniform SceneUniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
};

layout(push_constant) uniform VolumeConstants {
    VolumeDraws draws;
};

layout(location = 0) out vec3 outRenderPosition;
layout(location = 1) flat out uint outDraw;
// The projection's terms for depth, so volume_depth.frag can turn a view space z into a depth:
// depth = (x * z + y) / (z * z' + w), from its third and fourth columns' third and fourth rows
layout(location = 2) flat out vec4 outDepthTerms;

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
    vec3 offset = vec3(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1) * float(VOLUME_BRICK)
        * volumeDrawScale(draw);
    vec3 renderPosition = draw.corner + offset;
    outRenderPosition = renderPosition;
    outDraw = gl_InstanceIndex;
    outDepthTerms = vec4(projectionMatrix[2][2], projectionMatrix[3][2], projectionMatrix[2][3],
        projectionMatrix[3][3]);
    gl_Position = projectionMatrix * viewMatrix * vec4(renderPosition, 1.0);
}
