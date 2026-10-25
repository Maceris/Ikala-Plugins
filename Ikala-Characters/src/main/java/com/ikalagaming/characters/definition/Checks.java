package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Report;

import lombok.NonNull;

import java.util.List;

/**
 * What a definition can see while it is checked: the other definitions, and where problems go.
 *
 * @author Ches Burks
 */
public interface Checks extends Report {
    /**
     * Find another definition, reporting an error if it is missing or has errors itself.
     *
     * @param <T> The kind of definition.
     * @param type The kind of definition.
     * @param id Its ID.
     * @param path Where the reference is, for the error.
     * @return The definition, or null after reporting why.
     */
    <T extends Definition> T require(@NonNull Class<T> type, @NonNull Id id, @NonNull String path);

    /**
     * All the usable definitions of a kind.
     *
     * @param <T> The kind of definition.
     * @param type The kind of definition.
     * @return The definitions, in ID order.
     */
    <T extends Definition> List<T> all(@NonNull Class<T> type);
}
