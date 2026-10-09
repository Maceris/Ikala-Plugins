#version 460

layout(location=0) in vec2 inPos;

layout(location = 0) flat out int quadID;

layout(set = 0, binding = 0) uniform Uniforms {
    // Used to convert from pixel coordinates to Normalized Device Coordinates of (-1, 1)
    vec2 scale;
    int fontTexture;
    // The top left of the viewport being rendered, since positions are absolute (desktop
    // coordinates when using multiple viewports)
    vec2 displayPosition;
};

void main()
{
    vec2 pos = inPos - displayPosition;
    // Vulkan NDC has y pointing down, matching our pixel coordinates, so scale.y is positive
    gl_Position = vec4(pos.x * scale.x - 1, pos.y * scale.y - 1, 0.0, 1.0);
    quadID = gl_VertexIndex / 6;
}