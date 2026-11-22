package com.ikalagaming.ecs;

import lombok.NonNull;

import java.util.Arrays;
import java.util.function.LongConsumer;

/**
 * Entities and their components.
 *
 * <ul>
 *   <li><b>Entities</b> are {@code long} ids. {@link #create()} makes simulated entities, which are
 *       game state: snapshots keep which exist, and restoring brings back exactly those. {@link
 *       #createLocal()} makes local entities (an effect, a preview), which snapshots ignore. Ids
 *       are handed out in order and never reused, and the next simulated id is part of a snapshot,
 *       so replaying from a snapshot hands out the same ids again.
 *   <li><b>The world entity</b>, {@link #WORLD}, always exists. Its components are the world's
 *       resources: the frame number, the frame's inputs, settings.
 *   <li><b>Components</b> are stored per {@link ComponentType}, sorted by entity, so walking a
 *       query always visits entities in id order.
 *   <li><b>Snapshots</b> ({@link #snapshot()}) keep every simulated component and which simulated
 *       entities exist; {@link #restore} puts them back. Local components are left alone, except
 *       that those of simulated entities that no longer exist are dropped.
 * </ul>
 *
 * <p>A world is not safe to use from more than one thread at a time; each thread that simulates
 * keeps its own.
 */
public final class World {

    /** The world entity, which always exists and holds the resources. */
    public static final long WORLD = 0;

    /** The bit that marks local entities' ids. */
    public static final long LOCAL_BIT = 1L << 62;

    /** The value kept for each entity that exists. */
    private static final Object PRESENT = Boolean.TRUE;

    /** The simulated entities that exist, not counting the world entity. */
    private final Table alive = new Table();

    /** The local entities that exist. */
    private final Table localAlive = new Table();

    /** The next simulated id to hand out. */
    private long nextId = 1;

    /** The next local id to hand out, without the local bit. */
    private long nextLocalId = 1;

    /** Each type's table, by type index; null for types this world hasn't seen. */
    private Table[] tables = new Table[0];

    /** The types, by index, matching {@link #tables}. */
    private ComponentType<?>[] types = new ComponentType<?>[0];

    /**
     * Whether an id is a local entity's.
     *
     * @param entity The id.
     * @return True for local entities.
     */
    public static boolean isLocal(long entity) {
        return (entity & LOCAL_BIT) != 0;
    }

    /**
     * Make a simulated entity.
     *
     * @return Its id.
     */
    public long create() {
        final long id = nextId++;
        alive.put(id, PRESENT);
        return id;
    }

    /**
     * Make a local entity, which snapshots ignore.
     *
     * @return Its id.
     */
    public long createLocal() {
        final long id = LOCAL_BIT | nextLocalId++;
        localAlive.put(id, PRESENT);
        return id;
    }

    /**
     * Whether an entity exists.
     *
     * @param entity The entity.
     * @return True if it exists.
     */
    public boolean isAlive(long entity) {
        if (entity == WORLD) {
            return true;
        }
        return isLocal(entity) ? localAlive.has(entity) : alive.has(entity);
    }

    /**
     * Remove an entity and all its components. Does nothing if it doesn't exist.
     *
     * @param entity The entity.
     * @throws IllegalArgumentException For the world entity.
     */
    public void destroy(long entity) {
        if (entity == WORLD) {
            throw new IllegalArgumentException("The world entity can't be destroyed");
        }
        final boolean removed = isLocal(entity) ? localAlive.remove(entity) : alive.remove(entity);
        if (!removed) {
            return;
        }
        for (Table table : tables) {
            if (table != null) {
                table.remove(entity);
            }
        }
    }

    /**
     * How many simulated entities exist, not counting the world entity.
     *
     * @return The count.
     */
    public int entityCount() {
        return alive.size();
    }

    /**
     * How many local entities exist.
     *
     * @return The count.
     */
    public int localEntityCount() {
        return localAlive.size();
    }

    /**
     * An entity's component.
     *
     * @param <T> The type of the value.
     * @param type The component type.
     * @param entity The entity.
     * @return The value, or null if the entity has none.
     */
    public <T> T get(@NonNull ComponentType<T> type, long entity) {
        final Table table = table(type);
        return table == null ? null : type.getValueClass().cast(table.get(entity));
    }

    /**
     * Whether an entity has a component.
     *
     * @param type The component type.
     * @param entity The entity.
     * @return True if it does.
     */
    public boolean has(@NonNull ComponentType<?> type, long entity) {
        final Table table = table(type);
        return table != null && table.has(entity);
    }

    /**
     * Set an entity's component, adding it if the entity has none.
     *
     * @param <T> The type of the value.
     * @param type The component type.
     * @param entity The entity.
     * @param value The value.
     * @throws IllegalArgumentException If the entity doesn't exist, or the type is simulated and
     *     the entity is local.
     */
    public <T> void set(@NonNull ComponentType<T> type, long entity, @NonNull T value) {
        if (!isAlive(entity)) {
            throw new IllegalArgumentException("Entity " + entity + " doesn't exist");
        }
        if (type.isSimulated() && isLocal(entity)) {
            throw new IllegalArgumentException(
                    "Local entity " + entity + " can't have simulated component " + type);
        }
        tableFor(type).put(entity, type.getValueClass().cast(value));
    }

    /**
     * Remove an entity's component.
     *
     * @param type The component type.
     * @param entity The entity.
     * @return True if it had one.
     */
    public boolean remove(@NonNull ComponentType<?> type, long entity) {
        final Table table = table(type);
        return table != null && table.remove(entity);
    }

    /**
     * How many entities have a component.
     *
     * @param type The component type.
     * @return The count.
     */
    public int count(@NonNull ComponentType<?> type) {
        final Table table = table(type);
        return table == null ? 0 : table.size();
    }

    /**
     * A resource: a component of the world entity.
     *
     * @param <T> The type of the value.
     * @param type The component type.
     * @return The value, or null if it isn't set.
     */
    public <T> T getResource(@NonNull ComponentType<T> type) {
        return get(type, WORLD);
    }

    /**
     * Set a resource: a component of the world entity.
     *
     * @param <T> The type of the value.
     * @param type The component type.
     * @param value The value.
     */
    public <T> void setResource(@NonNull ComponentType<T> type, @NonNull T value) {
        set(type, WORLD, value);
    }

    /**
     * Remove a resource.
     *
     * @param type The component type.
     * @return True if it was set.
     */
    public boolean removeResource(@NonNull ComponentType<?> type) {
        return remove(type, WORLD);
    }

    /**
     * Visit every entity a query matches, in id order.
     *
     * <p>The action may change the world, including making and destroying entities and adding and
     * removing components. The walk covers the entities that matched when it started. Each one is
     * checked again just before it is visited, so an entity that stopped matching is skipped.
     *
     * @param query The query.
     * @param action Told each matching entity.
     */
    public void each(@NonNull Query query, @NonNull LongConsumer action) {
        Table smallest = null;
        for (ComponentType<?> type : query.all) {
            final Table table = table(type);
            if (table == null || table.size() == 0) {
                return;
            }
            if (smallest == null || table.size() < smallest.size()) {
                smallest = table;
            }
        }
        final Table.State start = smallest.freeze();
        for (int i = 0; i < start.size(); ++i) {
            final long entity = start.ids()[i];
            if (matches(query, entity)) {
                action.accept(entity);
            }
        }
    }

    /**
     * Whether an entity matches a query now.
     *
     * @param query The query.
     * @param entity The entity.
     * @return True if it has all the query's types and none of the ones it leaves out.
     */
    public boolean matches(@NonNull Query query, long entity) {
        for (ComponentType<?> type : query.all) {
            if (!has(type, entity)) {
                return false;
            }
        }
        for (ComponentType<?> type : query.none) {
            if (has(type, entity)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Keep the simulated state as it is now: every simulated component, which simulated entities
     * exist, and the next id to hand out. Cheap: nothing is copied until it next changes.
     *
     * @return The snapshot.
     */
    public Snapshot snapshot() {
        int count = 0;
        for (int i = 0; i < tables.length; ++i) {
            if (tables[i] != null && types[i].isSimulated() && tables[i].size() > 0) {
                ++count;
            }
        }
        final ComponentType<?>[] keptTypes = new ComponentType<?>[count];
        final Table.State[] states = new Table.State[count];
        int at = 0;
        for (int i = 0; i < tables.length; ++i) {
            if (tables[i] != null && types[i].isSimulated() && tables[i].size() > 0) {
                keptTypes[at] = types[i];
                states[at] = tables[i].freeze();
                ++at;
            }
        }
        return new Snapshot(nextId, alive.freeze(), keptTypes, states);
    }

    /**
     * Put the simulated state back as a snapshot kept it. Local components stay as they are, except
     * that those of simulated entities that no longer exist are dropped.
     *
     * @param snapshot The snapshot, from this world or one with the same component types.
     */
    public void restore(@NonNull Snapshot snapshot) {
        nextId = snapshot.getNextId();
        alive.load(snapshot.alive());
        for (int i = 0; i < tables.length; ++i) {
            if (tables[i] != null && types[i].isSimulated()) {
                tables[i].clear();
            }
        }
        for (int i = 0; i < snapshot.typeCount(); ++i) {
            tableFor(snapshot.type(i)).load(snapshot.state(i));
        }
        for (int i = 0; i < tables.length; ++i) {
            if (tables[i] != null && !types[i].isSimulated()) {
                dropOrphans(tables[i]);
            }
        }
    }

    /**
     * Remove a local table's entries for simulated entities that no longer exist.
     *
     * @param table The table.
     */
    private void dropOrphans(Table table) {
        final Table.State state = table.freeze();
        for (int i = 0; i < state.size(); ++i) {
            final long entity = state.ids()[i];
            if (entity != WORLD && !isLocal(entity) && !alive.has(entity)) {
                table.remove(entity);
            }
        }
    }

    /**
     * A type's table, if this world has one.
     *
     * @param type The type.
     * @return The table, or null.
     */
    private Table table(ComponentType<?> type) {
        return type.index < tables.length ? tables[type.index] : null;
    }

    /**
     * A type's table, made if this world has none yet.
     *
     * @param type The type.
     * @return The table.
     */
    private Table tableFor(ComponentType<?> type) {
        if (type.index >= tables.length) {
            final int length = Math.max(type.index + 1, tables.length * 2);
            tables = Arrays.copyOf(tables, length);
            types = Arrays.copyOf(types, length);
        }
        Table table = tables[type.index];
        if (table == null) {
            table = new Table();
            tables[type.index] = table;
            types[type.index] = type;
        }
        return table;
    }
}
