#ifndef LIGHTING_GLSL
#define LIGHTING_GLSL

// Lighting a surface: the Disney BRDF, the sun with its filtered shadow cascades, the point lights
// and spotlights light_cull.comp sorted into the surface's cluster, and ambient light; fog is laid
// over it afterward, see fog.glsl. All in
// view space, in linear light. AmbientMath has the ambient reflection in Java, with tests.

#include "color.glsl"
#include "light_inputs.glsl"
#ifdef ENVIRONMENT_SET
#include "environment.glsl"
#endif

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
// How far below the highest thing over it a point must be before it loses sky light, in meters,
// and over how many meters below that it goes from all of it to none. Match SkyOcclusion.
const float SKY_BIAS = 0.5;
const float SKY_FADE = 4.0;
// How far to move a point out along its normal before looking it up in the sky map, in meters, so
// a pit's walls look up through the pit rather than into the ground beside it
const float SKY_NORMAL_OFFSET = 0.25;
const float PI = 3.1415926535897932384626433832795;
// How strong a fully specular dielectric's reflection is head on, per unit of the material's
// specular value: 0.5 gives 4%. Matches AmbientMath.DIELECTRIC_SPECULAR_SCALE.
const float DIELECTRIC_SPECULAR_SCALE = 0.08;
// How much a clearcoat reflects head on, as a dielectric with an index of refraction of 1.5
const float CLEARCOAT_SPECULAR = 0.04;
// The smallest N.V used for ambient light, so surfaces seen edge on don't divide by nothing
const float MIN_N_DOT_V = 1e-4;
// The average of Schlick's Fresnel over the hemisphere is F0 + (1 - F0) / 21
const float FRESNEL_AVERAGE_DIVISOR = 21.0;
// The roughness from which a reflection's ray through the occupancy volume no longer counts, the
// ambient occlusion covering what hides a lobe that wide
const float REFLECTION_ROUGHNESS = 0.8;

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

// The color a surface reflects specularly head on: a dielectric reflects a little, tinted toward its
// base color by its specular tint, and a metal reflects its base color
vec3 specularColor(vec3 baseLinear, Material material) {
    float baseLuminance = 0.3 * baseLinear.x + 0.6 * baseLinear.y + 0.1 * baseLinear.z;
    vec3 baseTint = baseLuminance > 0 ? baseLinear / baseLuminance : vec3(1);
    return mix(
        material.specular * DIELECTRIC_SPECULAR_SCALE * mix(vec3(1), baseTint, material.specularTint),
        baseLinear, material.metallic
    );
}

vec3 disneyBRDF(vec3 baseColor, Material material, vec3 toViewDirection, vec3 toLightDirection, vec3 normal,
    vec3 x, vec3 y)
{
    // This expects the toViewDirection, toLightDirection, and normal to be normalized
    float angleLight = dot(normal, toLightDirection);
    float angleView = dot(normal, toViewDirection);
    // Grazing light adds nothing, and leaving it out keeps L + V from being zero below
    if (angleLight <= 0 || angleView < 0) {
        return vec3(0);
    }

    // Normalized, or the distribution below is divided by |L + V|^4 / 16, which goes toward zero for
    // a light behind a surface seen nearly edge on
    vec3 halfVector = normalize(toLightDirection + toViewDirection);
    float angleHalf = dot(normal, halfVector);
    float angleDifference = dot(toLightDirection, halfVector);

    vec3 baseLinear = sRGBToLinear(baseColor);
    float baseLuminance = 0.3 * baseLinear.x + 0.6 * baseLinear.y + 0.1 * baseLinear.z;
    vec3 baseTint = baseLuminance > 0 ? baseLinear / baseLuminance : vec3(1);
    vec3 colorSpecular0 = specularColor(baseLinear, material);
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
    // Fresnel goes by the angle between the light and the microfacet normal, L.H, not N.H
    float fresnelHalf = schlickFresnel(angleDifference);
    vec3 specularFresnel = mix(colorSpecular0, vec3(1), fresnelHalf);
    float specularShadowing = smithGGXAnisotropic(angleLight, dot(toLightDirection, x), dot(toLightDirection, y), aspectX, aspectY);
    specularShadowing *= smithGGXAnisotropic(angleView, dot(toViewDirection, x), dot(toViewDirection, y), aspectX, aspectY);

    vec3 sheen = fresnelHalf * material.sheen * colorSheen;

    float clearcoat = 0.0f;
    if (material.clearcoat > 0) {
        float clearcoatDistribution = generalizedTrowbridgeReitz1(angleHalf, mix(0.1, 0.001, material.clearcoatGloss));
        float clearcoatFresnel = mix(CLEARCOAT_SPECULAR, 1.0, fresnelHalf);
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

// Karis's fit to the split-sum environment BRDF ("Physically Based Shading on Mobile", 2014): the
// scale (x) and bias (y) that turn the color at normal incidence into how much light the surface
// reflects specularly. The bias is kept at or above zero; the fit dips a fraction of a percent
// below it on rough surfaces seen head on.
vec2 environmentBRDF(float roughness, float nDotV) {
    const vec4 c0 = vec4(-1, -0.0275, -0.572, 0.022);
    const vec4 c1 = vec4(1, 0.0425, 1.04, -0.04);
    vec4 r = roughness * c0 + c1;
    float a004 = min(r.x * r.x, exp2(-9.28 * max(nDotV, MIN_N_DOT_V))) * r.x + r.y;
    return vec2(-1.04 * a004 + r.z, max(1.04 * a004 + r.w, 0.0));
}

// The two parts of how much ambient light a surface reflects toward the viewer, see
// ambientReflectance: what single scattering reflects, through the surface's lobe around the
// mirror direction, and returned, what more bounces and the diffuse reflect, from all over the
// hemisphere. Specular by the split sum with multiple scattering compensated after Fdez-Aguera ("A
// Multiple-Scattering Microfacet Model for Real-Time Image-based Lighting", 2019), so rough
// surfaces don't lose the light that bounces more than once; the diffuse gets what the specular
// doesn't reflect, and metals have none.
vec3 ambientParts(vec3 baseLinear, Material material, float nDotV, out vec3 singleScatter) {
    vec3 f0 = specularColor(baseLinear, material);
    vec2 scaleBias = environmentBRDF(material.roughness, nDotV);
    // Single scattering, for white and for this color. The fit can give a color more than white at
    // grazing angles on smooth surfaces, where its scale goes negative, so it is kept to white's.
    float singleWhite = scaleBias.x + scaleBias.y;
    singleScatter = min(f0 * scaleBias.x + scaleBias.y, vec3(singleWhite));
    float missed = 1.0 - singleWhite;
    // What the missed light does over more bounces, by the average Fresnel
    vec3 average = f0 + (1.0 - f0) / FRESNEL_AVERAGE_DIVISOR;
    vec3 multiScatter = singleScatter * average / (1.0 - missed * average) * missed;
    vec3 specular = singleScatter + multiScatter;
    vec3 diffuse = (1.0 - specular) * baseLinear * (1.0 - material.metallic);
    return diffuse + multiScatter;
}

// How much of the ambient light, the same from every direction, a surface reflects toward the
// viewer: both parts of ambientParts.
vec3 ambientReflectance(vec3 baseLinear, Material material, float nDotV) {
    vec3 singleScatter;
    vec3 spread = ambientParts(baseLinear, material, nDotV, singleScatter);
    return singleScatter + spread;
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

// How much of a shadow map lets the sun through at a point, filtered over a 3 by 3 grid of
// bilinear taps, which read a 4 by 4 block of texels with four gathers
float filteredVisibility(int map, vec2 uv, float depth) {
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
            vec4 through = step(vec4(depth), stored);
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

// How much of the sun reaches a point through one cascade, from 0 in full shadow to 1, filtered as
// filteredVisibility does. Below 0 if the point is outside the cascade's shadow map.
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
    float visibility = filteredVisibility(map, uv, ndc.z);
    // What moves is drawn into its own map at the same place; either can block the sun
    if (cascade.dynamicMapIndex >= 0) {
        visibility *= filteredVisibility(cascade.dynamicMapIndex, uv, ndc.z);
    }
    return visibility;
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

// How much of the sky reaches a point, from 0 well under cover to 1 in the open, from how far below
// the highest thing over it the point is, averaged over a 4 by 4 block of the sky map's texels with
// four gathers so the edges of cover are soft
float skyVisibility(vec3 renderPosition, vec3 renderNormal) {
    if (skyMapIndex < 0) {
        return 1.0;
    }
    vec4 at = skyProjViewMatrix * vec4(renderPosition + renderNormal * SKY_NORMAL_OFFSET, 1);
    vec3 ndc = at.xyz / at.w;
    vec2 uv = ndc.xy * 0.5 + 0.5;
    if (any(lessThan(uv, vec2(0))) || any(greaterThan(uv, vec2(1)))) {
        return 1.0;
    }
    vec2 size = vec2(textureSize(bindlessTextures[nonuniformEXT(skyMapIndex)], 0));
    vec2 base = floor(uv * size - 0.5);
    float sum = 0.0;
    for (int gy = 0; gy < 2; ++gy) {
        for (int gx = 0; gx < 2; ++gx) {
            vec2 gather = (base + vec2(2 * gx, 2 * gy)) / size;
            vec4 stored = textureGather(bindlessTextures[nonuniformEXT(skyMapIndex)], gather, 0);
            // Depth grows downward from the map's eye, so below the top is positive
            vec4 covered = (vec4(ndc.z) - stored) * skyDepthRange;
            vec4 shade = smoothstep(vec4(SKY_BIAS), vec4(SKY_BIAS + SKY_FADE), covered);
            sum += dot(vec4(1.0) - shade, vec4(0.25));
        }
    }
    return sum / 4.0;
}

// The point shadow atlases' layout, see PointShadows
const float POINT_ATLAS_SIZE = 4096.0;
const float POINT_TILE_SIZE = 512.0;
const int POINT_TILES_ACROSS = 8;
const float POINT_GUARD_TEXELS = 3.0;
// How far to move a point out along its normal before looking it up in a point shadow map, in
// texels of the face at that distance
const float POINT_NORMAL_OFFSET_TEXELS = 1.5;

// How much of a point light reaches a surface past what its shadow map holds, from 0 to 1: the
// face looking toward the point, filtered like the sun's maps, times the layer of what moves
float pointVisibility(Light light, vec3 renderPosition, vec3 renderNormal) {
    if (light.shadowMap < 0 || pointAtlasIndex < 0) {
        return 1.0;
    }
    PointShadowMap map = pointShadowMaps[light.shadowMap];
    float distance = length(renderPosition - map.center.xyz);
    // A texel of a face is about twice the distance over the tile's size wide
    float texel = 2.0 * distance / (POINT_TILE_SIZE - 2.0 * POINT_GUARD_TEXELS);
    vec3 at = renderPosition + renderNormal * (texel * POINT_NORMAL_OFFSET_TEXELS);
    vec3 d = at - map.center.xyz;
    vec3 a = abs(d);
    int face = a.x >= a.y && a.x >= a.z ? (d.x > 0 ? 0 : 1)
        : (a.y >= a.z ? (d.y > 0 ? 2 : 3) : (d.z > 0 ? 4 : 5));
    vec4 clip = map.faces[face] * vec4(at, 1);
    vec3 ndc = clip.xyz / clip.w;
    if (ndc.z <= 0.0 || ndc.z >= 1.0) {
        return 1.0;
    }
    int tile = map.info.x + face;
    vec2 origin = vec2(tile % POINT_TILES_ACROSS, tile / POINT_TILES_ACROSS) * POINT_TILE_SIZE;
    vec2 uv = (origin + (ndc.xy * 0.5 + 0.5) * POINT_TILE_SIZE) / POINT_ATLAS_SIZE;
    float visibility = filteredVisibility(pointAtlasIndex, uv, ndc.z);
    if (map.info.y != 0 && pointDynamicAtlasIndex >= 0) {
        visibility *= filteredVisibility(pointDynamicAtlasIndex, uv, ndc.z);
    }
    return visibility;
}

#ifdef OCCUPANCY_SET
#include "occupancy.glsl"
// How much of the light from the lights the last lit surface marched rays for got through, from 0
// to 1, or -1 when none did, for the rays view
float lastRayVisibility = -1.0;
#endif

// Light a surface, in linear light. Positions, normals and tangents are in view space, except the
// render space position for the shadow lookup.
//
// geometricNormal: the surface's own normal, without normal maps, for looking around it in the
// occupancy volume
// screen: where the pixel is on the screen, from 0 to 1, y down, to find its cluster
// clusterLights: how many lights the pixel's cluster lists, for the heat map
vec3 lightSurface(vec4 baseColor, Material material, vec3 viewPosition, vec3 renderPosition,
    vec3 normal, vec3 geometricNormal, vec3 tangent, vec3 bitangent, vec2 screen,
    out uint clusterLights)
{
    // Only the directional light casts shadows, and in full shadow none of it arrives
    vec3 renderNormal = normalize((invViewMatrix * vec4(normal, 0)).xyz);
    float sunVisible = sunVisibility(viewPosition, renderPosition, renderNormal);
    // Past the cascades' reach, the sky map is all that can tell the sun is covered
    float sky = skyVisibility(renderPosition, renderNormal);
    if (-viewPosition.z >= -cascadeShadowSplits[NUM_CASCADES - 1].splitDistance) {
        sunVisible = min(sunVisible, sky);
    }
    vec3 color = calcDirLight(baseColor.xyz, material, directionalLight, viewPosition, normal, tangent, bitangent)
        * sunVisible;

#ifdef OCCUPANCY_SET
    vec3 renderGeometric = normalize((invViewMatrix * vec4(geometricNormal, 0)).xyz);
    // The strongest light from lights without a shadow map, strongest first, to march rays for
    int rayBudget = occupancyEnabled != 0 ? min(occupancyRays, OCCUPANCY_MAX_RAYS) : 0;
    int rayCount = 0;
    uint rayLights[OCCUPANCY_MAX_RAYS];
    vec3 rayLit[OCCUPANCY_MAX_RAYS];
    float rayStrength[OCCUPANCY_MAX_RAYS];
#endif

    // Only the lights light_cull.comp found can reach this pixel's cluster
    uint cluster = findCluster(screen, -viewPosition.z);
    clusterLights = clusterData[cluster];
    uint firstIndex = CLUSTER_COUNT + cluster * MAX_LIGHTS_PER_CLUSTER;
    for (uint i = 0; i < clusterLights; ++i) {
        uint index = clusterData[firstIndex + i];
        Light light = lights[index];
        vec3 lit = calcLight(baseColor.xyz, material, light, viewPosition, normal, tangent, bitangent);
        if (!any(greaterThan(lit, vec3(0)))) {
            continue;
        }
        if (light.shadowMap >= 0) {
            lit *= pointVisibility(light, renderPosition, renderNormal);
        }
#ifdef OCCUPANCY_SET
        else if (rayBudget > 0) {
            // Keep it among the strongest, in order, if it is strong enough
            float strength = dot(lit, RAY_LUMINANCE);
            if (rayCount < rayBudget || strength > rayStrength[rayCount - 1]) {
                int at = min(rayCount, rayBudget - 1);
                while (at > 0 && rayStrength[at - 1] < strength) {
                    rayLights[at] = rayLights[at - 1];
                    rayLit[at] = rayLit[at - 1];
                    rayStrength[at] = rayStrength[at - 1];
                    --at;
                }
                rayLights[at] = index;
                rayLit[at] = lit;
                rayStrength[at] = strength;
                rayCount = min(rayCount + 1, rayBudget);
            }
        }
#endif
        color += lit;
    }
#ifdef OCCUPANCY_SET
    // Take away what the volume keeps from reaching the surface
    float rayLight = 0.0;
    float rayThrough = 0.0;
    for (int r = 0; r < rayCount; ++r) {
        vec3 renderLight = (invViewMatrix * vec4(lights[rayLights[r]].position, 1)).xyz;
        float through = occupancyRayVisibility(renderPosition, renderGeometric, renderLight);
        color -= rayLit[r] * (1.0 - through);
        rayLight += rayStrength[r];
        rayThrough += rayStrength[r] * through;
    }
    lastRayVisibility = rayLight > 0.0 ? rayThrough / rayLight : -1.0;
#endif
    // Ambient light arrives equally from everywhere the sky can be seen, and dimly under cover, less
    // what is in the way nearby
#ifdef OCCUPANCY_SET
    vec3 bentGeometric;
    float open = occupancyAmbient(renderPosition, renderGeometric, bentGeometric);
#else
    float open = 1.0;
#endif
    float nDotV = max(dot(normal, normalize(-viewPosition)), MIN_N_DOT_V);
    vec3 ambientColor = ambientLight.intensity * ambientLight.color;
    float ambientVisible = mix(skyLightFloor, 1.0, sky) * open * material.occlusion;
    vec3 ambient;
#ifdef ENVIRONMENT_SET
    if (environmentEnabled != 0) {
        // The sky's light, scaled by the sun for the physical sky, else so its average is the
        // ambient light
        vec3 scale = environmentScale.w > 0.5
            ? environmentScale.rgb
            : ambientColor / max(dot(environmentAverage(), ENVIRONMENT_LUMINANCE), 1e-4);
        vec3 worldNormal = mat3(invViewMatrix) * normal;
        vec3 reflected = mat3(invViewMatrix) * reflect(normalize(viewPosition), normal);
        // Diffuse sky light comes from where the sky shows: the shading normal turned as the bent
        // normal leans from the geometric one
        vec3 diffuseNormal = worldNormal;
        float reflectionVisible = 1.0;
#ifdef OCCUPANCY_SET
        if (occupancyAoReach > 0.0 && environmentOcclusion != 0) {
            diffuseNormal = normalize(worldNormal + bentGeometric - renderGeometric);
            // Smooth surfaces mirror what is along the reflection; rough ones spread past it,
            // which the ambient occlusion covers
            if (material.roughness < REFLECTION_ROUGHNESS) {
                float seen = occupancyReflectionVisibility(renderPosition, renderGeometric, reflected);
                reflectionVisible = mix(seen, 1.0, material.roughness / REFLECTION_ROUGHNESS);
            }
        }
#endif
        vec3 mirrored;
        vec3 spread = ambientParts(sRGBToLinear(baseColor.rgb), material, nDotV, mirrored);
        // Lagarde's specular occlusion: what hides the hemisphere hides more of a narrow lobe
        float ao = open * material.occlusion;
        float specularVisible = clamp(
            pow(nDotV + ao, exp2(-16.0 * material.roughness - 1.0)) - 1.0 + ao, 0.0, 1.0);
        ambient = scale * mix(skyLightFloor, 1.0, sky)
            * (spread * environmentDiffuse(diffuseNormal) * ao
                + mirrored * environmentSpecular(reflected, material.roughness) * specularVisible
                    * reflectionVisible);
    } else
#endif
    {
        ambient = ambientColor * ambientVisible
            * ambientReflectance(sRGBToLinear(baseColor.rgb), material, nDotV);
    }
    // Light the surface gives off isn't shadowed or occluded
    vec3 finalColor = ambient + color + material.emissive;

    return finalColor;
}

#endif
