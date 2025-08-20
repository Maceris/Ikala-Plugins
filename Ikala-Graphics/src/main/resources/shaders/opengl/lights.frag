#version 460

const float SPECULAR_POWER = 10;
const int NUM_CASCADES = 3;
const float BIAS = 0.0005;
const float SHADOW_FACTOR = 0.25;

in vec2 outTextCoord;
out vec4 fragColor;

struct Attenuation
{
    float constant;
    float linear;
    float exponent;
	float padding;
};
struct AmbientLight
{
    float intensity;
    vec3 color;
};
struct PointLight {
    vec3 position;
	float padding;
    vec3 color;
    float intensity;
    Attenuation attenuation;
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

uniform sampler2D albedoSampler;
uniform sampler2D normalSampler;
uniform sampler2D specularSampler;
uniform sampler2D depthSampler;

uniform mat4 invProjectionMatrix;
uniform mat4 invViewMatrix;

layout (std430, binding=0) readonly buffer PointLights {
    PointLight pointLights[];

};

layout (std430, binding=1) readonly buffer SpotLights {
    SpotLight spotLights[];
};

uniform AmbientLight ambientLight;
uniform DirectionalLight directionalLight;
uniform int pointLightCount;
uniform int spotLightCount;
uniform Fog fog;
uniform CascadeShadow cascadeshadows[NUM_CASCADES];
uniform sampler2D shadowMap_0;
uniform sampler2D shadowMap_1;
uniform sampler2D shadowMap_2;

vec4 calcAmbient(AmbientLight ambientLight, vec4 ambient) {
    return vec4(ambientLight.intensity * ambientLight.color, 1) * ambient;
}

vec4 calcLightColor(vec4 diffuse, vec4 specular, float reflectance, vec3 lightColor, float lightIntensity, vec3 view_position, vec3 toLightDirection, vec3 normal) {
    vec4 diffuseColor = vec4(0, 0, 0, 1);
    vec4 specColor = vec4(0, 0, 0, 1);

    // Diffuse Light
    float diffuseFactor = max(dot(normal, toLightDirection), 0.0);
    diffuseColor = diffuse * vec4(lightColor, 1.0) * lightIntensity * diffuseFactor;

    // Specular Light
    vec3 cameraDirection = normalize(-view_position);
    vec3 fromLightDirection = -toLightDirection;
    vec3 reflectedLight = normalize(reflect(fromLightDirection, normal));
    float specularFactor = max(dot(cameraDirection, reflectedLight), 0.0);
    specularFactor = pow(specularFactor, SPECULAR_POWER);
    specColor = specular * lightIntensity  * specularFactor * reflectance * vec4(lightColor, 1.0);

    return (diffuseColor + specColor);
}

vec4 calcPointLight(vec4 diffuse, vec4 specular, float reflectance, PointLight light, vec3 view_position, vec3 normal) {
    vec3 directionToLight = light.position - view_position;
    vec3 toLightDirection  = normalize(directionToLight);
    vec4 lightColor = calcLightColor(diffuse, specular, reflectance, light.color, light.intensity, view_position, toLightDirection, normal);

    // Apply Attenuation
    float distance = length(directionToLight);
    float attenuationInv = light.attenuation.constant + light.attenuation.linear * distance +
    light.attenuation.exponent * distance * distance;
    return lightColor / attenuationInv;
}

vec4 calcSpotLight(vec4 diffuse, vec4 specular, float reflectance, SpotLight light, vec3 view_position, vec3 normal) {
    vec3 directionToLight = light.pointLight.position - view_position;
    vec3 toLightDirection  = normalize(directionToLight);
    vec3 fromLightDirection  = -toLightDirection;
    float spotAlpha = dot(fromLightDirection, normalize(light.coneDirection));

    vec4 color = vec4(0, 0, 0, 0);

    if (spotAlpha > light.cutoff)
    {
        color = calcPointLight(diffuse, specular, reflectance, light.pointLight, view_position, normal);
        color *= (1.0 - (1.0 - spotAlpha)/(1.0 - light.cutoff));
    }
    return color;
}

vec4 calcDirLight(vec4 diffuse, vec4 specular, float reflectance, DirectionalLight light, vec3 view_position, vec3 normal) {
    return calcLightColor(diffuse, specular, reflectance, light.color, light.intensity, view_position, normalize(light.direction), normal);
}

vec4 calcFog(vec3 pos, vec4 color, Fog fog, vec3 ambientLight, DirectionalLight directionalLight) {
    vec3 fogColor = fog.color * (ambientLight + directionalLight.color * directionalLight.intensity);
    float distance = length(pos);
    float fogFactor = 1.0 / exp((distance * fog.density) * (distance * fog.density));
    fogFactor = clamp(fogFactor, 0.0, 1.0);

    vec3 resultColor = mix(fogColor, color.xyz, fogFactor);
    return vec4(resultColor.xyz, color.w);
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
    vec4 shadowMapPosition = cascadeshadows[idx].projViewMatrix * worldPosition;
    float shadow = 1.0;
    vec4 shadowCoord = (shadowMapPosition / shadowMapPosition.w) * 0.5 + 0.5;
    shadow = textureProj(shadowCoord, vec2(0, 0), idx);
    return shadow;
}

void main()
{
    vec4 albedoSamplerValue = texture(albedoSampler, outTextCoord);
    vec3 albedo  = albedoSamplerValue.rgb;
    vec4 diffuse = vec4(albedo, 1);

    float reflectance = albedoSamplerValue.a;
    vec3 normal = normalize(2.0 * texture(normalSampler, outTextCoord).rgb  - 1.0);
    vec4 specular = texture(specularSampler, outTextCoord);

    // Retrieve position from depth
    float depth = texture(depthSampler, outTextCoord).x * 2.0 - 1.0;
    if (depth == 1) {
        discard;
    }
    vec4 clip      = vec4(outTextCoord.x * 2.0 - 1.0, outTextCoord.y * 2.0 - 1.0, depth, 1.0);
    vec4 view_w    = invProjectionMatrix * clip;
    vec3 viewPosition  = view_w.xyz / view_w.w;
    vec4 worldPosition = invViewMatrix * vec4(viewPosition, 1);

    vec4 diffuseSpecularComp = calcDirLight(diffuse, specular, reflectance, directionalLight, viewPosition, normal);

    int cascadeIndex;
    for (int i=0; i < NUM_CASCADES - 1; i++) {
        if (viewPosition.z < cascadeshadows[i].splitDistance) {
            cascadeIndex = i + 1;
            break;
        }
    }
    float shadowFactor = calcShadow(worldPosition, cascadeIndex);

    for (int i = 0; i < pointLightCount; ++i) {
        if (pointLights[i].intensity > 0) {
            diffuseSpecularComp += calcPointLight(diffuse, specular, reflectance, pointLights[i], viewPosition, normal);
        }
    }

    for (int i = 0; i < spotLightCount; ++i) {
        if (spotLights[i].pointLight.intensity > 0) {
            diffuseSpecularComp += calcSpotLight(diffuse, specular, reflectance, spotLights[i], viewPosition, normal);
        }
    }
    vec4 ambient = calcAmbient(ambientLight, diffuse);
    fragColor = ambient + diffuseSpecularComp;
    fragColor.rgb = fragColor.rgb * shadowFactor;

    if (fog.enabled == 1) {
        fragColor = calcFog(viewPosition, fragColor, fog, ambientLight.color, directionalLight);
    }
}