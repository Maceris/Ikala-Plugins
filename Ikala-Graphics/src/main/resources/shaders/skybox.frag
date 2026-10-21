#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

#include "color.glsl"
// The physical sky's tables are in the environment's set, set 2, see SkyboxRender
#define ENVIRONMENT_SET 2
#include "atmosphere.glsl"

layout(location = 0) in vec2 outTextCoord;
layout(location = 1) in vec3 outDirection;

layout(location = 0) out vec4 fragColor;

layout(set = 0, binding = 0) uniform Uniforms {
    mat4 projectionMatrix;
    mat4 viewMatrix;
    vec4 diffuse;
    int hasTexture;
    int textureIndex;
    // Nonzero to draw the physical sky, from its sky view table
    int physicalSky;
    // Toward the sun, y up, and in w the camera's distance from the planet's center in kilometers
    vec4 sunAndRadius;
    // What the physical sky is scaled by: the sun's color times its intensity times the sky's
    // strength
    vec4 skyScale;
};

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];

// The sun's disk: its light through the atmosphere spread over its solid angle, with an edge a
// fraction of its radius wide so it doesn't alias
const float SUN_EDGE = 0.1;

// The scene color is linear light, tone mapped later. The sky's texture samples in linear light
// already; its color is given sRGB encoded, as material colors are.
void main()
{
    if (physicalSky != 0) {
        vec3 d = normalize(outDirection);
        vec3 sun = sunAndRadius.xyz;
        float r = sunAndRadius.w;
        vec3 color = skyRadiance(d, sun, r);
        float angle = acos(clamp(dot(d, sun), -1.0, 1.0));
        float disk = 1.0 - smoothstep(SUN_ANGULAR_RADIUS * (1.0 - SUN_EDGE),
            SUN_ANGULAR_RADIUS * (1.0 + SUN_EDGE), angle);
        if (disk > 0.0 && distanceToGround(r, d.y) < 0.0) {
            float solidAngle = ATMOSPHERE_PI * SUN_ANGULAR_RADIUS * SUN_ANGULAR_RADIUS;
            color += disk * sunTransmittance(r, sun.y) / solidAngle;
        }
        fragColor = vec4(color * skyScale.rgb, 1.0);
        return;
    }
    vec3 color = hasTexture == 1
        ? texture(bindlessTextures[nonuniformEXT(textureIndex)], outTextCoord).rgb
        : sRGBToLinear(diffuse.rgb);
    fragColor = vec4(color, 1.0);
}
