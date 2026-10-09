package com.ikalagaming.graphics.asset;

import lombok.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A set of section tags and versions a reader knows how to read. A required section that is not
 * known makes a container unreadable. Graphics lists the sections it reads in {@link #graphics()};
 * a plugin that stores its own sections, for example in a cache, adds them with {@link #with(int,
 * int...)}.
 */
public final class KnownSections {
    /** The sections graphics reads. New section types are added here as they are defined. */
    private static final KnownSections GRAPHICS =
            new KnownSections(Map.of(SectionTag.META, Set.of(AssetMetadata.VERSION)));

    /** Supported versions of each known tag. */
    private final Map<Integer, Set<Integer>> versions;

    /**
     * Create a set of known sections.
     *
     * @param versions The supported versions of each tag.
     */
    private KnownSections(Map<Integer, Set<Integer>> versions) {
        this.versions = versions;
    }

    /**
     * The sections graphics knows how to read.
     *
     * @return The known sections.
     */
    public static KnownSections graphics() {
        return GRAPHICS;
    }

    /**
     * Create a copy that also knows some versions of a tag.
     *
     * @param tag The section tag.
     * @param supported The versions of that tag the reader supports.
     * @return The new set of known sections.
     */
    public KnownSections with(int tag, @NonNull int... supported) {
        Map<Integer, Set<Integer>> copy = new HashMap<>(versions);
        Set<Integer> tagVersions = new HashSet<>(copy.getOrDefault(tag, Set.of()));
        for (int version : supported) {
            tagVersions.add(version);
        }
        copy.put(tag, Set.copyOf(tagVersions));
        return new KnownSections(Map.copyOf(copy));
    }

    /**
     * Check whether a section can be read.
     *
     * @param tag The section tag.
     * @param version The section version.
     * @return Whether this tag and version are known.
     */
    public boolean isKnown(int tag, int version) {
        Set<Integer> tagVersions = versions.get(tag);
        return tagVersions != null && tagVersions.contains(version);
    }
}
