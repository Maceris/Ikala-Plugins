package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Id;

/**
 * A loaded character definition, like a rig, body plan or part.
 *
 * @author Ches Burks
 */
public interface Definition {
    /** The field every definition has for its ID. */
    String FIELD_ID = "id";

    /**
     * The definition's ID.
     *
     * @return The ID.
     */
    Id id();

    /**
     * Check this definition against itself and the others it refers to, after all of them are
     * loaded. Any error reported here keeps the definition out of the registry.
     *
     * @param checks The other definitions, and where problems go.
     */
    default void check(Checks checks) {}
}
