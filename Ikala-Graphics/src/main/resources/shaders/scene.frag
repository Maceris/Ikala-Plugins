#version 460
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_GOOGLE_include_directive : require

#include "color.glsl"
#include "material.glsl"
#include "volume_fade.glsl"
#include "alpha_test.glsl"
#include "roughening.glsl"

layout(location = 0) in vec3 outNormal;
layout(location = 1) in vec3 outTangent;
layout(location = 2) in vec3 outBitangent;
layout(location = 3) in vec2 outTextCoord;
layout(location = 4) in vec4 outViewPosition;
layout(location = 5) in vec4 outWorldPosition;
layout(location = 6) flat in uint outMaterialIdx;
// A model instance's material remaps, from in the low 16 bits and to in the high, see scene_lod.vert
layout(location = 7) flat in uvec4 outRemaps;

// Whether to drop pixels the texture's alpha cuts out. Off for baked opaque geometry, so the
// depth test can run before the shader.
layout(constant_id = 0) const bool ALPHA_TEST = true;

// Whether this is a level of detail cluster from scene_lod.vert. Its textured triangles are drawn
// as meshes are. Its projected ones, which outViewPosition.w marks, are flat and have no texture
// coordinates: each triangle's normal comes from how its position changes across the screen, and
// the texture is projected along its main axis, one repeat per block, with the same rules as
// LodTexturing: the fraction of unwrapped coordinates is sampled with their gradients, so the mip
// level doesn't jump where the fraction wraps. Its averaged ones, with outViewPosition.w 2, carry
// their own color, roughness, metallic and specular instead of textures, which go to the g-buffer
// for lights.frag to use in place of the material's. See scene_lod.vert for what its outputs carry.
layout(constant_id = 1) const bool TRIPLANAR = false;

// Whether these are sections' baked cut out blocks, which hand over to their volume bricks with
// distance, see volume_fade.glsl
layout(constant_id = 2) const bool VOLUME_FADE = false;

// After the level of detail tables' addresses, which only scene_lod.vert reads: where the hand
// over to volume bricks starts and how long it is, in meters, and the frame number for its dither
layout(push_constant) uniform SceneConstants {
    layout(offset = 16) float volumeFadeStart;
    float volumeFadeLength;
    uint frame;
    // Nonzero to color sections' cut out meshes by source, see DebugVisualizers.volumeSources
    uint debugSources;
};

// The flat color sections' cut out meshes take when colored by source
const vec3 MESH_SOURCE_COLOR = vec3(0.0, 0.8, 0.8);

layout(location = 0) out vec4 buffBaseColor;
layout(location = 1) out vec4 buffNormal;
layout(location = 2) out vec4 buffTangent;
layout(location = 3) out uint buffMaterial;
// The light the surface gives off, linear, and how much ambient light reaches it, see lights.frag
layout(location = 4) out vec4 buffEmission;

layout(set = 1, binding = 0) uniform sampler2D bindlessTextures[];

layout(std430, set = 0, binding = 2) readonly buffer Materials {
    Material materials[];
};

#include "material_average.glsl"

// Whether this pixel's texture is projected, on a level of detail cluster's projected triangle
bool projected;

// Whether this pixel is on a level of detail cluster's averaged triangle, which carries its own look
bool averaged;

// What the g-buffer normal's w holds when the material's own roughness, metallic and specular
// stand, see lights.frag
const float NO_OVERRIDE = -1.0;

// This pixel's texture coordinates, with their gradients for projected ones
vec2 textCoord;
vec2 textCoordDx;
vec2 textCoordDy;

// Sample one of the bindless textures at this pixel's texture coordinates
vec4 sampleTexture(int index)
{
    if (projected) {
        return textureGrad(
            bindlessTextures[nonuniformEXT(index)], fract(textCoord), textCoordDx, textCoordDy);
    }
    return texture(bindlessTextures[nonuniformEXT(index)], textCoord);
}

void main() {
    Material material = materials[outMaterialIdx];

    vec3 surfaceNormal = outNormal;
    vec3 tangent = outTangent;
    vec3 bitangent = outBitangent;
    textCoord = outTextCoord;
    // scene_lod.vert's debug coloring, a hue, or below zero for none
    float debugHue = TRIPLANAR ? outWorldPosition.w : -1.0;
    projected = TRIPLANAR && outViewPosition.w > 0.5 && outViewPosition.w < 1.5;
    averaged = TRIPLANAR && outViewPosition.w > 1.5;
    if (projected) {
        // The flat triangle's normal in render space, turned to face the camera at the origin
        vec3 position = outWorldPosition.xyz;
        vec3 normal = normalize(cross(dFdx(position), dFdy(position)));
        if (dot(normal, position) > 0.0) {
            normal = -normal;
        }
        // Coordinates from the camera's block, so their fractions are the world's. The main axis
        // prefers up and down on ties, as LodTexturing.axis does.
        vec3 local = position + outViewPosition.xyz;
        vec3 size = abs(normal);
        vec3 worldTangent;
        vec3 worldBitangent;
        if (size.y >= size.x && size.y >= size.z) {
            textCoord = local.xz;
            worldTangent = vec3(1.0, 0.0, 0.0);
            worldBitangent = vec3(0.0, 0.0, 1.0);
        } else if (size.x >= size.z) {
            // Seen from +x, right is -z; from -x, +z
            float side = normal.x > 0.0 ? -1.0 : 1.0;
            textCoord = vec2(side * local.z, -local.y);
            worldTangent = vec3(0.0, 0.0, side);
            worldBitangent = vec3(0.0, -1.0, 0.0);
        } else {
            // Seen from +z, right is +x; from -z, -x
            float side = normal.z > 0.0 ? 1.0 : -1.0;
            textCoord = vec2(side * local.x, -local.y);
            worldTangent = vec3(side, 0.0, 0.0);
            worldBitangent = vec3(0.0, -1.0, 0.0);
        }
        // The world's axes in view space, from scene_lod.vert
        mat3 toView = mat3(outNormal, outTangent, outBitangent);
        surfaceNormal = normalize(toView * normal);
        tangent = normalize(toView * worldTangent);
        bitangent = normalize(toView * worldBitangent);
    }
    textCoordDx = dFdx(textCoord);
    textCoordDy = dFdy(textCoord);

    vec4 baseColor = material.baseColor;
    // A model's indexed bake: each texel names a material and a shade, read as it was written, and
    // the material is drawn as the instance draws it, its average look times the shade, its
    // roughness widened by how much the baked normals agreed
    bool indexed = (material.flags & MATERIAL_FLAG_INDEXED_BAKE) != 0;
    Material named = material;
    float namedRoughness = 0.0;
    float namedMetallic = 0.0;
    if (indexed) {
        // The four nearest texels' colors, filtered as a color texture would be: each names its
        // own material, which can't be blended, but their looks can
        int page = material.textureIndex;
        ivec2 size = textureSize(bindlessTextures[nonuniformEXT(page)], 0);
        vec2 at = textCoord * vec2(size) - 0.5;
        ivec2 low = ivec2(floor(at));
        vec2 blend = at - vec2(low);
        vec3 linear = vec3(0.0);
        float most = -1.0;
        for (int corner = 0; corner < 4; ++corner) {
            ivec2 offset = ivec2(corner & 1, corner >> 1);
            ivec2 texel = clamp(low + offset, ivec2(0), size - ivec2(1));
            vec4 names = texelFetch(bindlessTextures[nonuniformEXT(page)], texel, 0);
            uint own = uint(round(names.g * 255.0)) | (uint(round(names.b * 255.0)) << 8);
            uint id = own;
            for (int i = 0; i < 4; ++i) {
                if ((outRemaps[i] & 0xFFFFu) == own) {
                    id = outRemaps[i] >> 16;
                }
            }
            float weight = (offset.x == 1 ? blend.x : 1.0 - blend.x)
                * (offset.y == 1 ? blend.y : 1.0 - blend.y);
            vec3 average;
            float roughness;
            float metallic;
            averageLook(materials[id], average, roughness, metallic);
            linear += weight * average * names.r * MAX_BAKE_SHADE;
            // The surface's other values from the texel weighing most
            if (weight > most) {
                most = weight;
                named = materials[id];
                namedRoughness = roughness;
                namedMetallic = metallic;
            }
        }
        baseColor = vec4(linearToSRGB(clamp(linear, 0.0, 1.0)), 1.0);
    }
    // An averaged triangle's specular, roughness and metallic
    vec3 averagedLook = vec3(outBitangent.x, outTextCoord);
    if (averaged) {
        baseColor = vec4(outTangent, 1.0);
        if (outBitangent.z > 1.5) {
            // A model's coverage volume shell names its material, perhaps remapped by the
            // instance, and carries its shade against the material's average look
            vec3 linear;
            averageLook(material, linear, averagedLook.y, averagedLook.z);
            baseColor = vec4(linearToSRGB(linear * outTangent.x), 1.0);
        }
        surfaceNormal = normalize(outNormal);
        // Any direction at right angles will do for the tangent
        vec3 across = abs(surfaceNormal.x) < 0.9 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
        tangent = normalize(cross(surfaceNormal, across));
        bitangent = cross(surfaceNormal, tangent);
    } else if (!indexed && material.textureIndex > 0) {
        // Sampled in linear light (sRGB textures are decoded by the GPU); the g-buffer holds base
        // colors sRGB encoded, as material colors are given
        baseColor = sampleTexture(material.textureIndex);
        baseColor.rgb = linearToSRGB(baseColor.rgb);
    }
    if (ALPHA_TEST && averaged && outBitangent.z > 0.5) {
        // A coverage volume's shell covers as much of the pixel as its volume stops light, by a
        // threshold of the pixel's own each frame, which temporal antialiasing averages
        if (outBitangent.y <= shellThreshold(gl_FragCoord.xy, frame, uint(gl_PrimitiveID))) {
            discard;
        }
    } else if (ALPHA_TEST) {
        bool covered = baseColor.a >= ALPHA_CUT;
        if (material.textureIndex > 0) {
            vec2 size = vec2(textureSize(bindlessTextures[nonuniformEXT(material.textureIndex)], 0));
            covered = alphaTestPasses(baseColor.a, alphaTestMip(textCoordDx, textCoordDy, size),
                textCoord, size);
        }
        if (!covered) {
            discard;
        }
    }
    if (VOLUME_FADE && volumeTakes(gl_FragCoord.xy, frame, length(outViewPosition.xyz),
            volumeFadeStart, volumeFadeLength)) {
        discard;
    }

    vec3 normal = surfaceNormal;
    vec4 normalTexel = vec4(0.5, 0.5, 1.0, 1.0);
    if (!averaged && material.normalMapIndex > 0) {
        mat3 TBN = mat3(tangent, bitangent, surfaceNormal);
        normalTexel = sampleTexture(material.normalMapIndex);
        normal = normalize(TBN * normalFromMap(normalTexel, material));
    }

    // Light given off, and roughness, metalness and occlusion from a packed texture, which stand
    // in for the material's own
    vec3 emission = material.emissive;
    float occlusion = 1.0;
    bool packedLook = false;
    vec3 packedValues = vec3(1.0);
    if (!averaged) {
        if (material.emissiveIndex > 0) {
            vec4 emissionTexel = sampleTexture(material.emissiveIndex);
            // A bake's emission page holds the light itself, see encodeEmission
            emission = (material.flags & MATERIAL_FLAG_BAKED_DATA) != 0
                ? decodeEmission(emissionTexel)
                : emission * emissionTexel.rgb;
        }
        if (material.occlusionRoughnessMetalIndex > 0) {
            packedValues = sampleTexture(material.occlusionRoughnessMetalIndex).rgb;
            if ((material.flags & MATERIAL_FLAG_PACKED_OCCLUSION) != 0) {
                occlusion = packedValues.r;
            }
            packedLook = true;
        } else if ((material.flags & MATERIAL_FLAG_BAKED_DATA) != 0
                && material.normalMapIndex > 0) {
            // A bake's roughness and metalness ride along with its normal; an indexed bake's
            // material's own, its roughness widened by how much the baked normals agreed
            packedValues = indexed
                ? vec3(1.0, roughenBySpread(namedRoughness, normalTexel.b), namedMetallic)
                : vec3(1.0, normalTexel.b, normalTexel.a);
            packedLook = true;
        }
    }

    if (VOLUME_FADE && debugSources != 0u) {
        baseColor.rgb = MESH_SOURCE_COLOR;
    }
    if (debugHue >= 0.0) {
        // A bright, fully saturated hue, still lit like the surface
        vec3 hue = clamp(abs(fract(debugHue + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0,
            0.0, 1.0);
        baseColor = vec4(hue, 1.0);
    }
    if (averaged && debugHue < 0.0) {
        // The look it carries, in place of the material's: roughness, metallic and specular
        buffBaseColor = vec4(baseColor.rgb, averagedLook.x);
        buffNormal = vec4(normal, averagedLook.y);
        buffTangent = vec4(normalize(tangent), averagedLook.z);
    } else if (packedLook && debugHue < 0.0) {
        // The packed texture's roughness and metalness, with the material's specular
        buffBaseColor = vec4(baseColor.rgb, named.specular);
        buffNormal = vec4(normal, packedValues.g);
        buffTangent = vec4(normalize(tangent), packedValues.b);
    } else {
        buffBaseColor = baseColor;
        buffNormal = vec4(normal, NO_OVERRIDE);
        buffTangent = vec4(normalize(tangent), 1.0);
    }
    buffMaterial = outMaterialIdx;
    buffEmission = vec4(emission, occlusion);
}