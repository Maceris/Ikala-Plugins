#version 460

layout(location=0) in vec2 inPos;

layout(location = 0) flat out int quadID;

layout(set = 0, binding = 0) uniform Uniforms {
    // Used to convert from pixel coordinates to Normalized Device Coordinates of (-1, 1)
    vec2 scale;
    int fontTexture;
};

void main()
{
    gl_Position = vec4(inPos.x * scale.x - 1, inPos.y * scale.y + 1, 0.0, 1.0);
    quadID = gl_VertexIndex / 6;
}