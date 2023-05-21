package com.ikalagaming.language.grammar;

import com.ikalagaming.language.Tag;

import lombok.NonNull;

import java.util.List;

/**
 * The substance an object is primarily made of. These serve as groups of tags
 * that can be applied to all objects that share the material.
 * 
 * Materials can also extend from a parent, automatically including all of the
 * tags of the parent. When a child includes a more specific tag than the
 * parent, only the most specific tag will be preserved.
 * 
 * @author Ches Burks
 * @param name The name of the material.
 * @param tags The tags that apply to this material.
 * @param parent The parent tag, null if this is a root material.
 *
 */
public record Material(@NonNull String name, @NonNull List<Tag> tags,
	Material parent) {}
