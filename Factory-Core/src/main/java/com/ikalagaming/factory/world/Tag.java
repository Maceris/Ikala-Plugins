package com.ikalagaming.factory.world;

import lombok.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Additional information used to categorize things and encode information. Tags describe either a
 * property of something, like an adjective describing it, or how something can be used.
 *
 * @param name The name of the tag.
 * @param parent The parent tag.
 * @author Ches Burks
 */
public record Tag(@NonNull String name, Tag parent) {

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Tag other) {
            return Objects.equals(name, other.name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Create a tag without a parent.
     *
     * @param name The name of the tag.
     */
    public Tag(String name) {
        this(name, null);
    }

    @Override
    public String toString() {
        if (parent == null) {
            return "Tag[" + "name=" + name + ']';
        }
        return "Tag[" + "name=" + name + ", parent=" + parent.name() + ']';
    }

    /**
     * Checks if the tag is in the provided list. If any tag matches the expected tag, or inherits
     * from a tag that does, this will return true.
     *
     * @param tag The tag we are looking for.
     * @param list The list of tags that might contain the desired tag.
     * @return Whether the tag can be found in the list.
     */
    public static boolean containsTag(@NonNull String tag, List<Tag> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (Tag potential : list) {
            if (tag.equals(potential.name())) {
                return true;
            }
            // Check all parent tags too
            Tag parent = potential.parent();
            while (parent != null) {
                if (tag.equals(parent.name())) {
                    return true;
                }
                parent = parent.parent();
            }
        }
        return false;
    }

    /**
     * Looks for any tag in the provided list that matches the specified tag. If any tag matches the
     * expected tag, or inherits from a tag that does, it will be returned. Otherwise, an empty
     * optional will be returned.
     *
     * @param tag The tag we are looking for.
     * @param list The list of tags that might contain the desired tag.
     * @return An optional that will contain the matching element, or will be empty if there is no
     *     match in the list.
     */
    public static Optional<Tag> findTag(@NonNull Tag tag, List<Tag> list) {
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }

        for (Tag potential : list) {
            if (tag.name().equals(potential.name())) {
                return Optional.of(potential);
            }
            // Check all parent tags too
            Tag parent = tag.parent();
            while (parent != null) {
                if (tag.name().equals(parent.name())) {
                    return Optional.of(potential);
                }
                parent = parent.parent();
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if this tag is in the provided list. If any tag matches this tag, or inherits from a
     * tag that does, this will return true.
     *
     * @param list The list to check.
     * @return Whether this tag can be found in the list.
     * @see Tag#containsTag(String, List)
     */
    public boolean isContainedBy(List<Tag> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        return containsTag(name, list);
    }
}
