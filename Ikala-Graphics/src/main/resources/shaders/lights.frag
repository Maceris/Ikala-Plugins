#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

// Lights the g-buffer, one full screen quad. The lighting itself is in lighting.glsl, shared with
// the transparent stage.

// The occupancy volume is the light stage's set 2 and the sky's environment set 3, see
// LightRender
#define OCCUPANCY_SET 2
#define ENVIRONMENT_SET 3
#include "lighting.glsl"
// The fog volume, set 4, filled just before, see FogRender
#define FOG_SET 4
#include "fog.glsl"

layout(location = 0) in vec2 outTextCoord;
layout(location = 0) out vec4 fragColor;

// The least cosine between the normal rebuilt from depth and the g-buffer's for the first to be
// trusted
const float GEOMETRIC_NORMAL_AGREEMENT = 0.5;

// The view space position of the surface at a pixel, from depth, which is already [0, 1] like
// Vulkan clip space. The scene is drawn with OpenGL style y up, but this quad isn't flipped, so y
// is flipped back.
vec3 viewPositionAt(ivec2 pixel, vec2 textCoord)
{
    float depth = texelFetch(bindlessTextures[nonuniformEXT(depthSamplerIndex)], pixel, 0).x;
    vec4 clip = vec4(textCoord.x * 2.0 - 1.0, 1.0 - textCoord.y * 2.0, depth, 1.0);
    vec4 viewW = invProjectionMatrix * clip;
    return viewW.xyz / viewW.w;
}

// Of the steps to the surface at the pixels on either side, the one that changes depth least, so a
// step across an edge onto another surface is passed over
vec3 smootherStep(vec3 center, vec3 before, vec3 after)
{
    vec3 forward = after - center;
    vec3 backward = center - before;
    return abs(forward.z) < abs(backward.z) ? forward : backward;
}

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
    // An averaged level of detail surface's own look, in place of its material's, see scene.frag
    float override = texelFetch(bindlessTextures[nonuniformEXT(normalSamplerIndex)], pixel, 0).w;
    if (override >= 0.0) {
        material.roughness = override;
        material.metallic =
            texelFetch(bindlessTextures[nonuniformEXT(tangentSamplerIndex)], pixel, 0).w;
        material.specular = baseColor.a;
        baseColor.a = 1.0;
    }
    // The light the surface gives off and how much ambient light reaches it, from its textures
    vec4 emission = texelFetch(bindlessTextures[nonuniformEXT(emissionSamplerIndex)], pixel, 0);
    material.emissive = emission.rgb;
    material.occlusion = emission.a;

    // How far the texture coordinates move a pixel, before any pixel of the quad is discarded
    vec2 pixelStep = vec2(dFdx(outTextCoord.x), dFdy(outTextCoord.y));
    float depth = texelFetch(bindlessTextures[nonuniformEXT(depthSamplerIndex)], pixel, 0).x;
    if (depth == 1) {
        discard;
    }
    vec3 viewPosition = viewPositionAt(pixel, outTextCoord);
    // The surface's own normal, from the positions at the pixels around it, taking the side of
    // each axis that stays on this surface. Where it still disagrees with the g-buffer's normal,
    // which normal maps tilt, the g-buffer's is used instead.
    ivec2 last = textureSize(bindlessTextures[nonuniformEXT(depthSamplerIndex)], 0) - 1;
    ivec2 left = ivec2(max(pixel.x - 1, 0), pixel.y);
    ivec2 right = ivec2(min(pixel.x + 1, last.x), pixel.y);
    ivec2 up = ivec2(pixel.x, max(pixel.y - 1, 0));
    ivec2 down = ivec2(pixel.x, min(pixel.y + 1, last.y));
    vec3 stepX = smootherStep(viewPosition,
        viewPositionAt(left, outTextCoord + vec2(float(left.x - pixel.x) * pixelStep.x, 0)),
        viewPositionAt(right, outTextCoord + vec2(float(right.x - pixel.x) * pixelStep.x, 0)));
    vec3 stepY = smootherStep(viewPosition,
        viewPositionAt(up, outTextCoord + vec2(0, float(up.y - pixel.y) * pixelStep.y)),
        viewPositionAt(down, outTextCoord + vec2(0, float(down.y - pixel.y) * pixelStep.y)));
    vec3 geometricNormal = normalize(cross(stepX, stepY));
    if (dot(geometricNormal, viewPosition) > 0) {
        geometricNormal = -geometricNormal;
    }
    if (!(dot(geometricNormal, normal) >= GEOMETRIC_NORMAL_AGREEMENT)) {
        geometricNormal = normal;
    }
    // Render space is world space moved so the camera is at the origin
    vec4 renderPosition = invViewMatrix * vec4(viewPosition, 1);

    uint clusterLights;
    vec3 finalColor = lightSurface(baseColor, material, viewPosition, renderPosition.xyz, normal,
        geometricNormal, tangent, bitangent, outTextCoord, clusterLights);

    // Seen through the fog in front of it
    if ((fog.enabled & FOG_ENABLED) != 0) {
        finalColor = fogApply(finalColor, gl_FragCoord.xy, -viewPosition.z, fog.logScale);
    }

    if (clusterHeatMap != 0 && clusterLights > 0) {
        finalColor = mix(finalColor, heatColor(clusterLights), HEAT_MAP_OPACITY);
    }
    if (occupancyView != 0) {
        vec3 renderNormal = normalize((invViewMatrix * vec4(normal, 0)).xyz);
        finalColor = occupancyViewColor(renderPosition.xyz, renderNormal);
    }
    if (occupancyAoView != 0) {
        vec3 renderGeometric = normalize((invViewMatrix * vec4(geometricNormal, 0)).xyz);
        finalColor = vec3(occupancyAmbient(renderPosition.xyz, renderGeometric));
    }
    if (occupancyRayView != 0) {
        // Gray for how much of the marched lights' light gets through, blue where none reaches
        finalColor = lastRayVisibility < 0.0 ? vec3(0.0, 0.0, 0.25) : vec3(lastRayVisibility);
    }
    if (skyOcclusionView != 0) {
        vec3 renderNormal = normalize((invViewMatrix * vec4(normal, 0)).xyz);
        float sky = mix(skyLightFloor, 1.0, skyVisibility(renderPosition.xyz, renderNormal));
        finalColor = vec3(sky);
    }

    // Linear light into the HDR scene color, tone mapped later
    fragColor = vec4(finalColor, 1.0);
}
