package com.ikalagaming.factory.world;

import lombok.NonNull;

import java.util.*;

/**
 * The substance an object is primarily made of. These serve as groups of tags that can be applied
 * to all objects that share the material, and also as a name for the material.
 *
 * <p>Materials can also extend from a parent, automatically including all of the tags of the
 * parent. When a child includes a more specific tag than the parent, only the most specific tag
 * will be preserved.
 *
 * @author Ches Burks
 * @param name The name of the material.
 * @param tags The tags that apply to this material.
 * @param parent The parent material, null if this is a root material.
 */
public record Material(@NonNull String name, @NonNull List<Tag> tags, Material parent) {

    /**
     * Create a new material record. This won't automatically de-deduplicate tags.
     *
     * @param name The name of the material.
     * @param tags The tags that apply to this material. If this is not a modifiable list, we will
     *     create a new list with a copy of the provided elements instead of using the provided one.
     * @param parent The parent material, null if this is a root material.
     * @see #deduplicateTags()
     */
    public Material(@NonNull String name, @NonNull List<Tag> tags, Material parent) {

        boolean tagsModifiable = true;
        try {
            tags.addAll(Collections.emptyList());
        } catch (UnsupportedOperationException ignored) {
            tagsModifiable = false;
        }

        this.name = name;
        this.tags = tagsModifiable ? tags : new ArrayList<>(tags);
        this.parent = parent;
    }

    /**
     * Create a new material record without a parent. This won't automatically de-deduplicate tags.
     *
     * @param name The name of the material.
     * @param tags The tags that apply to this material. If this is not a modifiable list, we will
     *     create a new list with a copy of the provided elements instead of using the provided one.
     * @see #deduplicateTags()
     */
    public Material(@NonNull String name, @NonNull List<Tag> tags) {
        this(name, tags, null);
    }

    /**
     * Create a new material record without any tags or a parent.
     *
     * @param name The name of the material.
     * @param parent The parent material.
     */
    public Material(@NonNull String name, Material parent) {
        this(name, new ArrayList<>(), parent);
    }

    /**
     * Create a new material record without any tags or a parent.
     *
     * @param name The name of the material.
     */
    public Material(@NonNull String name) {
        this(name, new ArrayList<>(), null);
    }

    /**
     * Remove any tags that are on the material which are the parent of another tag on the material.
     */
    void deduplicateTags() {
        Set<Tag> toRemove = new HashSet<>();
        for (Tag tag : tags) {
            var parent = tag.parent();
            while (parent != null) {
                if (tags.contains(parent)) {
                    toRemove.add(parent);
                }
                parent = parent.parent();
            }
        }
        var deduplicated = tags.stream().distinct().filter(tag -> !toRemove.contains(tag)).toList();
        tags.clear();
        tags.addAll(deduplicated);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }

        Material other = (Material) o;
        return name.equals(other.name());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        var result = new StringBuilder("Material[name=");

        result.append(name);
        result.append(", tags=");
        result.append(tags);
        if (parent != null) {
            result.append(", parent=");
            result.append(parent.name());
        }
        result.append(']');

        return result.toString();
    }
}
