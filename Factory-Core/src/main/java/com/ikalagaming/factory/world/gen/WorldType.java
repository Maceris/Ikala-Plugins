package com.ikalagaming.factory.world.gen;

import lombok.NonNull;

import java.util.List;

/**
 * Specifications for how a world is generated.
 *
 * @param validBiomes The biomes that are valid for this type of world.
 */
public record WorldType(@NonNull List<String> validBiomes) {}
