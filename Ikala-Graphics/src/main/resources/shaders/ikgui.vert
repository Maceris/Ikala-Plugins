#version 460

layout(location=0) in vec2 inPos;

layout(location = 0) flat out int quadID;

// Every draw list of every window is packed into the same buffers, so these are where the
// current draw list starts, and how the window it is drawn in is placed. Must match ikgui.frag
// and ShaderBindings.GUI.
layout(push_constant) uniform DrawListInfo {
    int commandOffset;
    int pointOffset;
    int detailOffset;
    // Used to convert from display coordinates to Normalized Device Coordinates of (-1, 1)
    vec2 scale;
    // The top left of the viewport being rendered, since positions are absolute (desktop
    // coordinates when using multiple viewports)
    vec2 displayPosition;
    // Framebuffer pixels per display unit
    vec2 framebufferScale;
};

void main()
{
    vec2 pos = inPos - displayPosition;
    // Vulkan NDC has y pointing down, matching our pixel coordinates, so scale.y is positive
    gl_Position = vec4(pos.x * scale.x - 1, pos.y * scale.y - 1, 0.0, 1.0);
    quadID = gl_VertexIndex / 6;
}