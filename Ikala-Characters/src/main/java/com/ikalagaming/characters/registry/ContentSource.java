package com.ikalagaming.characters.registry;

import lombok.NonNull;

import java.nio.file.Path;

/**
 * A folder of character definitions from one plugin or mod. Each kind of definition is in a
 * subfolder named after it, like {@code bodyPlan/} and {@code part/}, as JSON files that hold one
 * definition or a list of them. Every ID it defines must be in its namespace, so mods add
 * definitions and never replace someone else's.
 *
 * @param namespace The namespace its IDs use, like "ikala" or "goblinmod".
 * @param root The folder.
 * @param name What to call it in messages, which file paths are shown relative to.
 * @author Ches Burks
 */
public record ContentSource(@NonNull String namespace, @NonNull Path root, @NonNull String name) {
    /**
     * Create a source named after its folder.
     *
     * @param namespace The namespace its IDs use.
     * @param root The folder.
     */
    public ContentSource(@NonNull String namespace, @NonNull Path root) {
        this(namespace, root, root.toString());
    }
}
