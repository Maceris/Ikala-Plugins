package com.ikalagaming.ecs;

import lombok.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * Which entities a system works on: those with every one of some component types and none of
 * others. Make each query once, as a constant, and walk it with {@link World#each}.
 */
public final class Query {

    /** The types an entity must have. */
    final ComponentType<?>[] all;

    /** The types an entity must not have. */
    final ComponentType<?>[] none;

    /**
     * A query.
     *
     * @param all The types an entity must have.
     * @param none The types an entity must not have.
     */
    private Query(ComponentType<?>[] all, ComponentType<?>[] none) {
        this.all = all;
        this.none = none;
    }

    /**
     * Entities with all of some types.
     *
     * @param all The types, at least one.
     * @return The query.
     * @throws IllegalArgumentException If there are no types.
     */
    public static Query of(@NonNull ComponentType<?>... all) {
        if (all.length == 0) {
            throw new IllegalArgumentException("A query needs at least one type to have");
        }
        for (ComponentType<?> type : all) {
            if (type == null) {
                throw new NullPointerException("A query's types can't be null");
            }
        }
        return new Query(all.clone(), new ComponentType<?>[0]);
    }

    /**
     * This query, also leaving out entities with any of some types.
     *
     * @param types The types to leave out.
     * @return A new query.
     */
    public Query without(@NonNull ComponentType<?>... types) {
        for (ComponentType<?> type : types) {
            if (type == null) {
                throw new NullPointerException("A query's types can't be null");
            }
        }
        final ComponentType<?>[] joined = Arrays.copyOf(none, none.length + types.length);
        System.arraycopy(types, 0, joined, none.length, types.length);
        return new Query(all, joined);
    }

    /**
     * The types an entity must have.
     *
     * @return The types.
     */
    public List<ComponentType<?>> getAll() {
        return List.of(all);
    }

    /**
     * The types an entity must not have.
     *
     * @return The types.
     */
    public List<ComponentType<?>> getNone() {
        return List.of(none);
    }

    @Override
    public String toString() {
        return "Query"
                + Arrays.toString(all)
                + (none.length == 0 ? "" : " without " + Arrays.toString(none));
    }
}
