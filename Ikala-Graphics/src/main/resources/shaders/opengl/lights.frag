#version 460

const int NUM_CASCADES = 3;
const float MAX_LIGHT_DISTANCE = 5;
const float BIAS = 0.0005;
const float SHADOW_FACTOR = 0.25;
const float PI = 3.1415926535897932384626433832795;

in vec2 outTextCoord;
out vec4 fragColor;

struct AmbientLight
{
    vec3 color;
    float intensity;
};

struct PointLight {
    vec3 position;
	float _padding;
    vec3 color;
    float intensity;
};

struct SpotLight
{
    PointLight pointLight;
    vec3 coneDirection;
    float cutoff;
};

struct DirectionalLight
{
    vec3 color;
    vec3 direction;
    float intensity;
};

struct Fog
{
    int enabled;
    vec3 color;
    float density;
};

struct CascadeShadow {
    mat4 projViewMatrix;
    float splitDistance;
};

struct Material
{
    vec4 baseColor;

    float anisotropic;
    float clearcoat;
    float clearcoatGloss;
    float metallic;

    float roughness;
    float sheen;
    float sheenTint;
    float specular;

    float specularTint;
    float subsurface;
    int normalMapIndex;
    int textureIndex;
};

uniform sampler2D baseColorSampler;
uniform sampler2D normalSampler;
uniform sampler2D tangentSampler;
uniform usampler2D materialSampler;
uniform sampler2D depthSampler;

uniform mat4 invProjectionMatrix;
uniform mat4 invViewMatrix;

layout(std430, binding = 0) readonly buffer PointLights {
    PointLight pointLights[];
};

layout(std430, binding = 1) readonly buffer SpotLights {
    SpotLight spotLights[];
};

layout(std430, binding = 2) readonly buffer Materials {
    Material materials[];
};

uniform AmbientLight ambientLight;
uniform DirectionalLight directionalLight;
uniform int pointLightCount;
uniform int spotLightCount;
uniform Fog fog;
uniform CascadeShadow cascadeShadows[NUM_CASCADES];
uniform sampler2D shadowMap_0;
uniform sampler2D shadowMap_1;
uniform sampler2D shadowMap_2;

float sqr(float x) {
    return x * x;
}

float generalizedThrowbridgeReitz1(float angleHalf, float a) {
    if (a >= 1) {
        return 1 / PI;
    }
    float a2 = a * a;
    float t = 1 + (a2 - 1) * sqr(angleHalf);
    return (a2 - 1) / (PI * log(a2) * t);
}

float generalizedThrowbridgeReitzAnisotropic(float angleHalf, float HdotX, float HdotY, float ax, float ay) {
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

vec3 sRGBToLinear(vec3 color) {
    return vec3(pow(color.x, 2.2), pow(color.y, 2.2), pow(color.z, 2.2));
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
    float specularDistribution = generalizedThrowbridgeReitzAnisotropic(
        angleHalf, dot(halfVector, x), dot(halfVector, y), aspectX, aspectY
    );
    float fresnelHalf = schlickFresnel(angleHalf);
    vec3 specularFresnel = mix(colorSpecular0, vec3(1), fresnelHalf);
    float specularShadowing = smithGGXAnisotropic(angleView, dot(toLightDirection, x), dot(toLightDirection, y), aspectX, aspectY);
    specularShadowing *= smithGGXAnisotropic(angleView, dot(toViewDirection, x), dot(toViewDirection, y), aspectX, aspectY);

    vec3 sheen = fresnelHalf * material.sheen * colorSheen;

    float clearcoatDistribution = generalizedThrowbridgeReitz1(angleHalf, mix(0.1, 0.001, material.clearcoatGloss));
    float clearcoatFresnel = mix(0.04, 1.0, fresnelHalf);
    float clearcoatShadowing = smithGGX(angleLight, 0.25) * smithGGX(angleView, 0.25);

    return ((1/PI) * mix(diffuseFresnel, subsurfaceScaled, material.subsurface) * baseLinear + sheen)
        * (1 - material.metallic)
        + specularDistribution * specularFresnel * specularShadowing
        + 0.25 * material.clearcoat * clearcoatDistribution * clearcoatFresnel * clearcoatShadowing;
}

float scaleIntensity(float distance) {
    float ratio = distance / MAX_LIGHT_DISTANCE;
    float r2 = sqr(ratio);
    float clamped = max(1 - sqr(r2), 0);
    return sqr(clamped);
}

vec3 calcLightColor(vec3 baseColor, Material material, vec3 lightColor, float lightIntensity, vec3 viewPosition,
    vec3 toLightDirection, vec3 normal, vec3 tangent, vec3 bitangent)
{
    vec3 toViewDirection = normalize(-viewPosition);
    vec3 brdf = disneyBRDF(baseColor, material, toViewDirection, toLightDirection, normal, tangent, bitangent);
    float lightScaling = dot(normal, toLightDirection);
    return brdf * lightColor * lightScaling;
}

vec3 calcPointLight(vec3 baseColor, Material material, PointLight light, vec3 viewPosition, vec3 normal, vec3 tangent,
    vec3 bitangent)
{
    vec3 directionToLight = light.position - viewPosition;
    vec3 toLightDirection  = normalize(directionToLight);
    float intensity = scaleIntensity(light.intensity);

    return calcLightColor(baseColor, material, light.color, intensity, viewPosition, toLightDirection,
                   normal, tangent, bitangent);
}

vec3 calcSpotLight(vec3 baseColor, Material material, SpotLight light, vec3 viewPosition, vec3 normal, vec3 tangent,
    vec3 bitangent)
{
    vec3 directionToLight = light.pointLight.position - viewPosition;
    vec3 toLightDirection  = normalize(directionToLight);
    vec3 fromLightDirection  = -toLightDirection;
    float spotAlpha = dot(fromLightDirection, normalize(light.coneDirection));

    float intensity = scaleIntensity(light.pointLight.intensity);
    if (spotAlpha > light.cutoff) {
        intensity *= (1.0 - (1.0 - spotAlpha)/(1.0 - light.cutoff));
    }
    else {
        return vec3(0);
    }

    return calcLightColor(baseColor, material, light.pointLight.color, intensity, viewPosition, toLightDirection,
                   normal, tangent, bitangent);
}

vec3 calcDirLight(vec3 baseColor, Material material, DirectionalLight light, vec3 viewPosition, vec3 normal,
    vec3 tangent, vec3 bitangent)
{
    return calcLightColor(baseColor, material, light.color, light.intensity, viewPosition, normalize(-light.direction),
        normal, tangent, bitangent);
}

vec3 calcFog(vec3 pos, vec3 color, Fog fog, vec3 ambientLight, DirectionalLight directionalLight) {
    vec3 fogColor = fog.color * (ambientLight + directionalLight.color * directionalLight.intensity);
    float distance = length(pos);
    float fogFactor = 1.0 / exp((distance * fog.density) * (distance * fog.density));
    fogFactor = clamp(fogFactor, 0.0, 1.0);

    vec3 resultColor = mix(fogColor, color, fogFactor);
    return resultColor;
}

float textureProj(vec4 shadowCoord, vec2 offset, int idx) {
    float shadow = 1.0;

    if (shadowCoord.z > -1.0 && shadowCoord.z < 1.0) {
        float dist = 0.0;
        if (idx == 0) {
            dist = texture(shadowMap_0, vec2(shadowCoord.xy + offset)).r;
        } else if (idx == 1) {
            dist = texture(shadowMap_1, vec2(shadowCoord.xy + offset)).r;
        } else {
            dist = texture(shadowMap_2, vec2(shadowCoord.xy + offset)).r;
        }
        if (shadowCoord.w > 0 && dist < shadowCoord.z - BIAS) {
            shadow = SHADOW_FACTOR;
        }
    }
    return shadow;
}

float calcShadow(vec4 worldPosition, int idx) {
    vec4 shadowMapPosition = cascadeShadows[idx].projViewMatrix * worldPosition;
    float shadow = 1.0;
    vec4 shadowCoord = (shadowMapPosition / shadowMapPosition.w) * 0.5 + 0.5;
    shadow = textureProj(shadowCoord, vec2(0, 0), idx);
    return shadow;
}

void main()
{
    vec4 baseColor = texture(baseColorSampler, outTextCoord);
    vec3 normal = texture(normalSampler, outTextCoord).rgb;
    vec3 tangent = texture(tangentSampler, outTextCoord).rgb;
    vec3 bitangent = cross(normal, tangent);// Hopefully close enough, normal isn't the "real" normal
    uint materialIndex = texture(materialSampler, outTextCoord).r;
    Material material = materials[materialIndex];

    // Retrieve position from depth
    float depth = texture(depthSampler, outTextCoord).x * 2.0 - 1.0;
    if (depth == 1) {
        discard;
    }
    vec4 clip = vec4(outTextCoord.x * 2.0 - 1.0, outTextCoord.y * 2.0 - 1.0, depth, 1.0);
    vec4 viewW = invProjectionMatrix * clip;
    vec3 viewPosition = viewW.xyz / viewW.w;
    vec4 worldPosition = invViewMatrix * vec4(viewPosition, 1);

    vec3 color = calcDirLight(baseColor.xyz, material, directionalLight, viewPosition, normal, tangent, bitangent);

    int cascadeIndex;
    for (int i=0; i < NUM_CASCADES - 1; i++) {
        if (viewPosition.z < cascadeShadows[i].splitDistance) {
            cascadeIndex = i + 1;
            break;
        }
    }
    float shadowFactor = calcShadow(worldPosition, cascadeIndex);

    for (int i = 0; i < pointLightCount; ++i) {
        if (pointLights[i].intensity > 0) {
            color += calcPointLight(baseColor.xyz, material, pointLights[i], viewPosition, normal,
                tangent, bitangent);
        }
    }

    for (int i = 0; i < spotLightCount; ++i) {
        if (spotLights[i].pointLight.intensity > 0) {
            color += calcSpotLight(baseColor.xyz, material, spotLights[i], viewPosition, normal,
                tangent, bitangent);
        }
    }
    vec3 ambient = ambientLight.intensity * ambientLight.color;
    vec3 finalColor = ambient + color;

    if (fog.enabled == 1) {
        finalColor = calcFog(viewPosition, finalColor, fog, ambientLight.color, directionalLight);
    }

    fragColor.a = baseColor.a;
    fragColor.rgb = finalColor * (1 + 0.000001 * shadowFactor);
}