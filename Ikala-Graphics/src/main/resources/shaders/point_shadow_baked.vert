#version 460

// Draws a baked section into a point light shadow map's face. The section is placed by the push
// constants rather than an instance, since a section fed to level of detail has no instance for
// its opaque blocks. See PointShadows.

layout(location = 0) in ivec4 position; // xyz: steps from the section's origin, w: user data

layout(push_constant) uniform PointShadowConstants {
    mat4 projViewMatrix;
    // xyz: the section's corner in render space, w: meters per step
    vec4 origin;
};

void main()
{
    gl_Position = projViewMatrix * vec4(origin.xyz + vec3(position.xyz) * origin.w, 1.0);
}
