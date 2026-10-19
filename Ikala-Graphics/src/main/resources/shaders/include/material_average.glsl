#ifndef MATERIAL_AVERAGE_GLSL
#define MATERIAL_AVERAGE_GLSL

// A material's look averaged over its surface, for far stand-ins of a model that name their
// material rather than carry a color, since its instances may draw it with others: coverage
// volume voxels and shells, blended corners and indexed bakes. The averages are worked out on the
// CPU from the textures, as level of detail blends materials, see SceneRender.writeMaterial; where
// one couldn't be, as for BC7 textures, it is below zero, and the texture's smallest mip stands in.
// Include after declaring bindlessTextures.

// A texture's average as its smallest mip has it
vec4 smallestMip(int index)
{
    int levels = textureQueryLevels(bindlessTextures[nonuniformEXT(index)]);
    return textureLod(bindlessTextures[nonuniformEXT(index)], vec2(0.5), float(levels - 1));
}

// A material's average color, linear, and its roughness and metalness
void averageLook(Material look, out vec3 linear, out float roughness, out float metallic)
{
    linear = look.average.rgb;
    if (linear.r < 0.0) {
        linear = smallestMip(look.textureIndex).rgb;
    }
    roughness = look.average.a;
    metallic = look.averageMetallic;
    if (roughness < 0.0 || metallic < 0.0) {
        vec3 packedValues = smallestMip(look.occlusionRoughnessMetalIndex).rgb;
        roughness = packedValues.g;
        metallic = packedValues.b;
    }
}

#endif
