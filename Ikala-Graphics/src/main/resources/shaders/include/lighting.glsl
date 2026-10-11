#ifndef LIGHTING_GLSL
#define LIGHTING_GLSL

// Lighting a surface: the Disney BRDF, the sun with its filtered shadow cascades, the point lights
// and spotlights light_cull.comp sorted into the surface's cluster, ambient light and fog. All in
// view space, in linear light.

#include "color.glsl"
#include "light_inputs.glsl"

// How many lights in one cluster show as full red on the heat map. Must match
// DebugVisualizers.HEAT_MAP_FULL.
const float HEAT_MAP_FULL = 32.0;
// How much of the heat map color covers the lit color
const float HEAT_MAP_OPACITY = 0.6;
// How far to move a point out along its normal before looking it up in a shadow map, in texels of
// that map, so a lit surface doesn't shadow itself
const float NORMAL_OFFSET_TEXELS = 1.5;
// How much of each cascade's slice, at its far end, blends into the next cascade, or for the last
// one into no shadow, so the change in detail doesn't show as a line
const float CASCADE_BLEND = 0.1;
const float PI = 3.1415926535897932384626433832795;

float sqr(float x) {
    return x * x;
}

float generalizedTrowbridgeReitz1(float angleHalf, float a) {
    float a2 = a * a;
    return (a2 - 1) / (PI * log2(a2) * (1 + (a2 - 1) * sqr(angleHalf)));
}

float generalizedTrowbridgeReitzAnisotropic(float angleHalf, float HdotX, float HdotY, float ax, float ay) {
    return 1 / (PI * ax * ay * sqr(sqr(HdotX / ax) + sqr(HdotY / ay) + sqr(angleHalf)));
}

float smithGGX(float angle, float a) {
    float a2 = sqr(a);
    float angle2 = sqr(angle);
    return 1 / (angle + sqrt(a2 + angle2 - a2 * angle2));
}

float smithGGXAnisotropic(float angleView, float VdotX, float VdotY, float ax, float ay) {
    return 1 / (angleView + sqrt(sqr(VdotX * ax) + sqr(VdotY * ay) + sqr(angleView)));
}


float schlickFresnel(float angle) {
    float m = clamp(1 - angle, 0 , 1);
    float m2 = sqr(m);
    return m2 * m2 * m;// pow(m, 5)
}

vec3 disneyBRDF(vec3 baseColor, Material material, vec3 toViewDirection, vec3 toLightDirection, vec3 normal,
    vec3 x, vec3 y)
{
    // This expects the toViewDirection, toLightDirection, and normal to be normalized
    float angleLight = dot(normal, toLightDirection);
    float angleView = dot(normal, toViewDirection);
    if (angleLight < 0 || angleView < 0) {
        return vec3(0);
    }

    vec3 halfVector = (toLightDirection + toViewDirection) / 2;
    float angleHalf = dot(normal, halfVector);
    float angleDifference = dot(toLightDirection, halfVector);

    vec3 baseLinear = sRGBToLinear(baseColor);
    float baseLuminance = 0.3 * baseLinear.x + 0.6 * baseLinear.y + 0.1 * baseLinear.z;
    vec3 baseTint = baseLuminance > 0 ? baseLinear / baseLuminance : vec3(1);
    vec3 colorSpecular0 = mix(
        material.specular * 0.08 * mix(vec3(1), baseTint, material.specularTint),
        baseLinear, material.metallic
    );
    vec3 colorSheen = mix(vec3(1), baseTint, material.sheenTint);

    float fresnelLight = schlickFresnel(angleLight);
    float fresnelView = schlickFresnel(angleView);
    float FD90 = 0.5f + 2 * material.roughness * angleDifference * angleDifference;
    float diffuseFresnel = mix(1.0, FD90, fresnelLight) * mix(1.0, FD90, fresnelView);

    // Flattens retro-reflection based on roughness
    float subsurfaceFD90 = angleDifference * angleDifference * material.roughness;
    float subsurfaceFresnel = mix(1.0, subsurfaceFD90, fresnelLight) * mix(1.0, subsurfaceFD90, fresnelView);
    // 1.25 scale to preserve albedo
    float subsurfaceScaled = 1.25 * (subsurfaceFresnel * (1 / (angleLight + angleView) - 0.5) + 0.5);

    float aspect = sqrt(1 - material.anisotropic * 0.9);
    float aspectX = max(0.001, sqr(material.roughness) / aspect);
    float aspectY = max(0.001, sqr(material.roughness) * aspect);
    float specularDistribution = generalizedTrowbridgeReitzAnisotropic(
        angleHalf, dot(halfVector, x), dot(halfVector, y), aspectX, aspectY
    );
    float fresnelHalf = schlickFresnel(angleHalf);
    vec3 specularFresnel = mix(colorSpecular0, vec3(1), fresnelHalf);
    float specularShadowing = smithGGXAnisotropic(angleView, dot(toLightDirection, x), dot(toLightDirection, y), aspectX, aspectY);
    specularShadowing *= smithGGXAnisotropic(angleView, dot(toViewDirection, x), dot(toViewDirection, y), aspectX, aspectY);

    vec3 sheen = fresnelHalf * material.sheen * colorSheen;

    float clearcoat = 0.0f;
    if (material.clearcoat > 0) {
        float clearcoatDistribution = generalizedTrowbridgeReitz1(angleHalf, mix(0.1, 0.001, material.clearcoatGloss));
        float clearcoatFresnel = schlickFresnel(mix(0.04, 1.0, fresnelHalf));
        float clearcoatShadowing = smithGGX(angleLight, 0.25) * smithGGX(angleView, 0.25);
        clearcoat = 0.25 * material.clearcoat * clearcoatDistribution * clearcoatFresnel * clearcoatShadowing;
    }

    return ((1/PI) * mix(diffuseFresnel, subsurfaceScaled, material.subsurface) * baseLinear + sheen)
        * (1 - material.metallic)
        + specularDistribution * specularFresnel * specularShadowing
        + clearcoat;
}

// Inverse square, windowed to reach exactly zero at the light's range. Matches LightRange.falloff.
float scaleIntensity(float distance, float range) {
    float attenuation = 1.0f / sqr(distance);
    float ratio = distance / range;
    float r2 = sqr(ratio);
    float cutoff = clamp(1 - sqr(r2), 0.0, 1.0);
    return attenuation * cutoff;
}

vec3 calcLightColor(vec3 baseColor, Material material, vec3 lightColor, float lightIntensity, vec3 viewPosition,
    vec3 toLightDirection, vec3 normal, vec3 tangent, vec3 bitangent)
{
    vec3 toViewDirection = normalize(-viewPosition);
    vec3 brdf = disneyBRDF(baseColor, material, toViewDirection, toLightDirection, normal, tangent, bitangent);
    float lightScaling = dot(normal, toLightDirection);
    return brdf * lightColor * lightScaling * lightIntensity;
}

// Light positions and directions, the surface position, and the normals are all in view space
vec3 calcLight(vec3 baseColor, Material material, Light light, vec3 viewPosition,
    vec3 normal, vec3 tangent, vec3 bitangent)
{
    vec3 directionToLight = light.position - viewPosition;
    float distance = length(directionToLight);
    if (distance >= light.range) {
        return vec3(0);
    }
    vec3 toLightDirection  = normalize(directionToLight);
    float intensity = scaleIntensity(distance, light.range) * light.intensity;

    if (light.type == LIGHT_TYPE_SPOT) {
        float spotAlpha = dot(-toLightDirection, normalize(light.direction));
        if (spotAlpha <= light.cosOuter) {
            return vec3(0);
        }
        // Full strength inside the inner angle, fading linearly to nothing at the outer angle
        float fadeWidth = light.cosInner - light.cosOuter;
        if (fadeWidth > 0) {
            intensity *= clamp((spotAlpha - light.cosOuter) / fadeWidth, 0.0, 1.0);
        }
    }

    return calcLightColor(baseColor, material, light.color, intensity, viewPosition, toLightDirection,
                   normal, tangent, bitangent);
}

vec3 calcDirLight(vec3 baseColor, Material material, DirectionalLight light, vec3 viewPosition, vec3 normal,
    vec3 tangent, vec3 bitangent)
{
    return calcLightColor(baseColor, material, light.color, light.intensity, viewPosition, normalize(-light.direction),
        normal, tangent, bitangent);
}

vec3 calcFog(vec3 pos, vec3 color, Fog fog, vec3 ambientLight, DirectionalLight directionalLight) {
    vec3 fogColor = sRGBToLinear(fog.color);
    float distance = length(pos);
    float fogFactor = 1.0 / exp(sqr(distance * fog.density));
    fogFactor = clamp(fogFactor, 0.0, 1.0);

    vec3 resultColor = mix(fogColor, color, fogFactor);
    return resultColor;
}

// The cluster a pixel is in, from where it is on the screen and how far in front of the camera
uint findCluster(vec2 screen, float depth) {
    uint slice = 0;
    if (depth >= FIRST_SLICE_DEPTH) {
        slice = min(1 + uint(floor(log(depth / FIRST_SLICE_DEPTH) * clusterLogScale)), CLUSTERS_Z - 1);
    }
    uvec2 tile = min(uvec2(screen * vec2(CLUSTERS_X, CLUSTERS_Y)), uvec2(CLUSTERS_X - 1, CLUSTERS_Y - 1));
    return tile.x + CLUSTERS_X * (tile.y + CLUSTERS_Y * slice);
}

// From blue for one light, through green, to red at HEAT_MAP_FULL or more
vec3 heatColor(uint count) {
    float heat = clamp(float(count) / HEAT_MAP_FULL, 0.0, 1.0);
    return heat < 0.5
        ? mix(vec3(0, 0, 1), vec3(0, 1, 0), heat * 2.0)
        : mix(vec3(0, 1, 0), vec3(1, 0, 0), heat * 2.0 - 1.0);
}

// How much of the sun reaches a point through one cascade, from 0 in full shadow to 1, filtered over
// a 3 by 3 grid of bilinear taps, which read a 4 by 4 block of texels with four gathers. Below 0 if
// the point is outside the cascade's shadow map.
float cascadeVisibility(vec3 renderPosition, vec3 renderNormal, int idx) {
    CascadeShadow cascade = cascadeShadowSplits[idx];
    vec3 offset = renderNormal * (cascade.texelSize * NORMAL_OFFSET_TEXELS);
    vec4 shadowMapPosition = cascade.projViewMatrix * vec4(renderPosition + offset, 1);
    // The shadow maps aren't drawn with a flipped viewport, and Vulkan depth is already [0, 1]
    vec3 ndc = shadowMapPosition.xyz / shadowMapPosition.w;
    vec2 uv = ndc.xy * 0.5 + 0.5;
    if (any(lessThan(uv, vec2(0))) || any(greaterThan(uv, vec2(1)))) {
        return -1.0;
    }
    // Further toward the sun than the map reaches, nothing can shadow it; past its far end,
    // nothing was drawn
    if (ndc.z <= 0.0 || ndc.z >= 1.0) {
        return 1.0;
    }
    int map = idx == 0 ? shadowMap0Index : (idx == 1 ? shadowMap1Index : shadowMap2Index);
    vec2 size = vec2(textureSize(bindlessTextures[nonuniformEXT(map)], 0));
    vec2 texel = uv * size - 0.5;
    vec2 base = floor(texel);
    vec2 fraction = texel - base;

    // Whether each texel of the 4 by 4 block from base - 1 lets the sun through
    float lit[4][4];
    for (int gy = 0; gy < 2; ++gy) {
        for (int gx = 0; gx < 2; ++gx) {
            // Between texels base - 1 + 2g and base + 2g, so the gather reads those four
            vec2 at = (base + vec2(2 * gx, 2 * gy)) / size;
            vec4 stored = textureGather(bindlessTextures[nonuniformEXT(map)], at, 0);
            vec4 through = step(vec4(ndc.z), stored);
            // Gathers come back as (left, bottom), (right, bottom), (right, top), (left, top),
            // counting rows up from the lower one
            lit[2 * gx][2 * gy + 1] = through.x;
            lit[2 * gx + 1][2 * gy + 1] = through.y;
            lit[2 * gx + 1][2 * gy] = through.z;
            lit[2 * gx][2 * gy] = through.w;
        }
    }
    float sum = 0.0;
    for (int y = 0; y < 3; ++y) {
        for (int x = 0; x < 3; ++x) {
            sum += mix(
                mix(lit[x][y], lit[x + 1][y], fraction.x),
                mix(lit[x][y + 1], lit[x + 1][y + 1], fraction.x),
                fraction.y);
        }
    }
    return sum / 9.0;
}

// How much of the sun reaches a point, from 0 in full shadow to 1. Past the shadow distance,
// nothing is shadowed.
float sunVisibility(vec3 viewPosition, vec3 renderPosition, vec3 renderNormal) {
    float depth = -viewPosition.z;
    float shadowDistance = -cascadeShadowSplits[NUM_CASCADES - 1].splitDistance;
    if (depth >= shadowDistance) {
        return 1.0;
    }
    // The splits get further away (more negative in view space), so use the last one we're past
    int idx = 0;
    for (int i = 0; i < NUM_CASCADES - 1; i++) {
        if (viewPosition.z < cascadeShadowSplits[i].splitDistance) {
            idx = i + 1;
        }
    }
    float visibility = cascadeVisibility(renderPosition, renderNormal, idx);
    if (visibility < 0) {
        return 1.0;
    }
    float sliceStart = idx == 0 ? 0.0 : -cascadeShadowSplits[idx - 1].splitDistance;
    float sliceEnd = -cascadeShadowSplits[idx].splitDistance;
    float blendStart = sliceEnd - CASCADE_BLEND * (sliceEnd - sliceStart);
    if (depth > blendStart) {
        float next = idx + 1 < NUM_CASCADES ? cascadeVisibility(renderPosition, renderNormal, idx + 1) : 1.0;
        if (next >= 0) {
            visibility = mix(visibility, next, (depth - blendStart) / (sliceEnd - blendStart));
        }
    }
    return visibility;
}

// Light a surface, in linear light. Positions, normals and tangents are in view space, except the
// render space position for the shadow lookup.
//
// screen: where the pixel is on the screen, from 0 to 1, y down, to find its cluster
// clusterLights: how many lights the pixel's cluster lists, for the heat map
vec3 lightSurface(vec4 baseColor, Material material, vec3 viewPosition, vec3 renderPosition,
    vec3 normal, vec3 tangent, vec3 bitangent, vec2 screen, out uint clusterLights)
{
    // Only the directional light casts shadows, and in full shadow none of it arrives
    vec3 renderNormal = normalize((invViewMatrix * vec4(normal, 0)).xyz);
    float sunVisible = sunVisibility(viewPosition, renderPosition, renderNormal);
    vec3 color = calcDirLight(baseColor.xyz, material, directionalLight, viewPosition, normal, tangent, bitangent)
        * sunVisible;

    // Only the lights light_cull.comp found can reach this pixel's cluster
    uint cluster = findCluster(screen, -viewPosition.z);
    clusterLights = clusterData[cluster];
    uint firstIndex = CLUSTER_COUNT + cluster * MAX_LIGHTS_PER_CLUSTER;
    for (uint i = 0; i < clusterLights; ++i) {
        Light light = lights[clusterData[firstIndex + i]];
        color += calcLight(baseColor.xyz, material, light, viewPosition, normal, tangent, bitangent);
    }
    // Ambient light is light arriving from everywhere, so the surface reflects it by its own color
    vec3 ambient = ambientLight.intensity * ambientLight.color * sRGBToLinear(baseColor.rgb);
    vec3 finalColor = ambient + color;

    if (fog.enabled == 1 && fog.density > 0) {
        finalColor = calcFog(viewPosition, finalColor, fog, ambientLight.color, directionalLight);
    }
    return finalColor;
}

#endif
