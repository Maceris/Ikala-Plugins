#version 460

// Debug shape lines. Positions are in render space, world space relative to the camera.

layout(location = 0) in vec3 position;
// RGBA with red in the highest byte, like the GUI colors
layout(location = 1) in uint color;

layout(location = 0) out vec4 outColor;

layout(push_constant) uniform PushConstants {
    mat4 projectionViewMatrix;
};

void main()
{
    // unpackUnorm4x8 puts the lowest byte in x, which is alpha here
    outColor = unpackUnorm4x8(color).wzyx;
    gl_Position = projectionViewMatrix * vec4(position, 1.0);
}
