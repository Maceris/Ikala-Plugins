#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

// Lights the g-buffer, one full screen quad. The lighting itself is in lighting.glsl, shared with
// the transparent stage.

#include "lighting.glsl"

layout(location = 0) in vec2 outTextCoord;
layout(location = 0) out vec4 fragColor;

void main()
{
    // The g-buffer can be larger than the screen, but we draw 1:1 with it, so read by pixel
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 baseColor = texelFetch(bindlessTextures[nonuniformEXT(baseColorSamplerIndex)], pixel, 0);
    vec3 normal = texelFetch(bindlessTextures[nonuniformEXT(normalSamplerIndex)], pixel, 0).rgb;
    vec3 tangent = texelFetch(bindlessTextures[nonuniformEXT(tangentSamplerIndex)], pixel, 0).rgb;
    vec3 bitangent = cross(normal, tangent);// Hopefully close enough, normal isn't the "real" normal
    uint materialIndex = texelFetch(bindlessUintTextures[nonuniformEXT(materialSamplerIndex)], pixel, 0).r;

    Material material = materials[materialIndex];

    // Retrieve position from depth, which is already [0, 1] like Vulkan clip space
    float depth = texelFetch(bindlessTextures[nonuniformEXT(depthSamplerIndex)], pixel, 0).x;
    if (depth == 1) {
        discard;
    }
    // The scene is drawn with OpenGL style y up, but this quad isn't flipped, so flip y back
    vec4 clip = vec4(outTextCoord.x * 2.0 - 1.0, 1.0 - outTextCoord.y * 2.0, depth, 1.0);
    vec4 viewW = invProjectionMatrix * clip;
    vec3 viewPosition = viewW.xyz / viewW.w;
    // Render space is world space moved so the camera is at the origin
    vec4 renderPosition = invViewMatrix * vec4(viewPosition, 1);

    uint clusterLights;
    vec3 finalColor = lightSurface(baseColor, material, viewPosition, renderPosition.xyz, normal, tangent,
        bitangent, outTextCoord, clusterLights);

    if (clusterHeatMap != 0 && clusterLights > 0) {
        finalColor = mix(finalColor, heatColor(clusterLights), HEAT_MAP_OPACITY);
    }

    // Linear light into the HDR scene color, tone mapped later
    fragColor = vec4(finalColor, 1.0);
}
