#ifndef MATERIAL_AVERAGE_GLSL
#define MATERIAL_AVERAGE_GLSL

// A material's look averaged over its surface, for far stand-ins of a model that name their
// material rather than carry a color, since its instances may draw it with others: coverage
// volume voxels and shells. Include after declaring bindlessTextures.

// A texture's average, its smallest mip
vec4 averageTexel(int index)
{
    int levels = textureQueryLevels(bindlessTextures[nonuniformEXT(index)]);
    return textureLod(bindlessTextures[nonuniformEXT(index)], vec2(0.5), float(levels - 1));
}

// A material's average color, linear, and its roughness and metalness, from its packed texture's
// average if it has one
void averageLook(Material look, out vec3 linear, out float roughness, out float metallic)
{
    linear = sRGBToLinear(look.baseColor.rgb);
    if (look.textureIndex > 0) {
        linear = averageTexel(look.textureIndex).rgb;
    }
    roughness = look.roughness;
    metallic = look.metallic;
    if (look.occlusionRoughnessMetalIndex > 0) {
        vec3 packedValues = averageTexel(look.occlusionRoughnessMetalIndex).rgb;
        roughness = packedValues.g;
        metallic = packedValues.b;
    }
}

#endif
