package com.ikalagaming.language.grammar;

import com.ikalagaming.language.Tag;

import lombok.NonNull;

import java.util.List;

/**
 * Represents a noun. This is not an instance of the noun, only the
 * specifications of a type of noun.
 * 
 * @author Ches Burks
 * @param name The name of the noun.
 * @param material The material the noun is primarily made of.
 * @param tags Tags that are related to the noun, possibly in addition to those
 *            inherited from the material.
 */
public record Noun(@NonNull String name, Material material, List<Tag> tags) {}
