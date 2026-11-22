package com.ikalagaming.ecs;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A world's simulated state at one moment: every simulated component, which simulated entities
 * existed, and the next id the world would hand out. It never changes, and two snapshots are equal
 * when they hold the same entities with equal values, so a prediction can be checked against the
 * server's answer with {@link #equals}.
 */
public final class Snapshot {

    /**
     * The next simulated id the world would hand out.
     *
     * @return The id.
     */
    @Getter private final long nextId;

    /** The simulated entities that existed. */
    private final Table.State alive;

    /** The simulated types that had any values, in index order. */
    private final ComponentType<?>[] types;

    /** Each type's values, matching {@link #types}. */
    private final Table.State[] states;

    /**
     * A snapshot.
     *
     * @param nextId The next simulated id the world would hand out.
     * @param alive The simulated entities that existed.
     * @param types The simulated types that had any values, in index order.
     * @param states Each type's values.
     */
    Snapshot(long nextId, Table.State alive, ComponentType<?>[] types, Table.State[] states) {
        this.nextId = nextId;
        this.alive = alive;
        this.types = types;
        this.states = states;
    }

    /**
     * The simulated entities that existed.
     *
     * @return Their state.
     */
    Table.State alive() {
        return alive;
    }

    /**
     * How many types have values here.
     *
     * @return The count.
     */
    int typeCount() {
        return types.length;
    }

    /**
     * A type that has values here.
     *
     * @param i Which, below {@link #typeCount()}.
     * @return The type.
     */
    ComponentType<?> type(int i) {
        return types[i];
    }

    /**
     * A type's values.
     *
     * @param i Which, below {@link #typeCount()}.
     * @return The values.
     */
    Table.State state(int i) {
        return states[i];
    }

    /**
     * A type's values.
     *
     * @param type The type.
     * @return The values, empty if there were none.
     */
    private Table.State state(ComponentType<?> type) {
        for (int i = 0; i < types.length; ++i) {
            if (types[i] == type) {
                return states[i];
            }
        }
        return Table.EMPTY;
    }

    /**
     * An entity's component, as it was.
     *
     * @param <T> The type of the value.
     * @param type The component type, which must be simulated to be kept.
     * @param entity The entity.
     * @return The value, or null if it had none.
     */
    public <T> T get(@NonNull ComponentType<T> type, long entity) {
        return type.getValueClass().cast(state(type).get(entity));
    }

    /**
     * Whether an entity had a component.
     *
     * @param type The component type.
     * @param entity The entity.
     * @return True if it did.
     */
    public boolean has(@NonNull ComponentType<?> type, long entity) {
        return state(type).indexOf(entity) >= 0;
    }

    /**
     * Whether a simulated entity existed.
     *
     * @param entity The entity.
     * @return True if it did; always true for the world entity.
     */
    public boolean isAlive(long entity) {
        return entity == World.WORLD || alive.indexOf(entity) >= 0;
    }

    /**
     * How many simulated entities existed, not counting the world entity.
     *
     * @return The count.
     */
    public int entityCount() {
        return alive.size();
    }

    /**
     * What differs from another snapshot, for logging a mispredict or a failed test.
     *
     * @param other The other snapshot.
     * @param limit The most differences to list.
     * @return One line per difference, empty if they are equal.
     */
    public List<String> differences(@NonNull Snapshot other, int limit) {
        final List<String> out = new ArrayList<>();
        if (nextId != other.nextId) {
            out.add("next id " + nextId + " vs " + other.nextId);
        }
        compare("entities", alive, other.alive, out, limit);
        final List<ComponentType<?>> all = new ArrayList<>(List.of(types));
        for (ComponentType<?> type : other.types) {
            if (!all.contains(type)) {
                all.add(type);
            }
        }
        for (ComponentType<?> type : all) {
            compare(type.getName(), state(type), other.state(type), out, limit);
        }
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    /**
     * List the differences between two tables' states.
     *
     * @param what What the table holds, for the lines.
     * @param a This snapshot's state.
     * @param b The other snapshot's state.
     * @param out Where to add lines.
     * @param limit Stop once this many lines are listed.
     */
    private static void compare(
            String what, Table.State a, Table.State b, List<String> out, int limit) {
        int i = 0;
        int j = 0;
        while ((i < a.size() || j < b.size()) && out.size() < limit) {
            final long idA = i < a.size() ? a.ids()[i] : Long.MAX_VALUE;
            final long idB = j < b.size() ? b.ids()[j] : Long.MAX_VALUE;
            if (idA < idB) {
                out.add(what + " " + idA + ": " + a.values()[i] + " vs none");
                ++i;
            } else if (idB < idA) {
                out.add(what + " " + idB + ": none vs " + b.values()[j]);
                ++j;
            } else {
                if (!Objects.equals(a.values()[i], b.values()[j])) {
                    out.add(what + " " + idA + ": " + a.values()[i] + " vs " + b.values()[j]);
                }
                ++i;
                ++j;
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Snapshot other)
                || nextId != other.nextId
                || types.length != other.types.length
                || !alive.sameAs(other.alive)) {
            return false;
        }
        for (int i = 0; i < types.length; ++i) {
            if (types[i] != other.types[i] || !states[i].sameAs(other.states[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = Long.hashCode(nextId);
        hash = 31 * hash + alive.contentHash();
        for (int i = 0; i < types.length; ++i) {
            hash = 31 * hash + types[i].index;
            hash = 31 * hash + states[i].contentHash();
        }
        return hash;
    }

    @Override
    public String toString() {
        return "Snapshot[" + alive.size() + " entities, " + types.length + " types]";
    }
}
