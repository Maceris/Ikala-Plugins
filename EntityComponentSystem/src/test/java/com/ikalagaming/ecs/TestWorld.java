package com.ikalagaming.ecs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for entities, components, queries and schedules. */
class TestWorld {

    /** Where something is. */
    record Position(long x, long y) {}

    /** How fast something moves. */
    record Velocity(long dx, long dy) {}

    /** A marker for things that don't move. */
    enum Frozen {
        /** The only value. */
        YES
    }

    /** The position type. */
    static final ComponentType<Position> POSITION =
            ComponentType.simulated("test:position", Position.class);

    /** The velocity type. */
    static final ComponentType<Velocity> VELOCITY =
            ComponentType.simulated("test:velocity", Velocity.class);

    /** The frozen marker type. */
    static final ComponentType<Frozen> FROZEN =
            ComponentType.simulated("test:frozen", Frozen.class);

    /** A local type, with a mutable value. */
    static final ComponentType<StringBuilder> LABEL =
            ComponentType.local("test:label", StringBuilder.class);

    /** Entities come and go, and their components with them. */
    @Test
    void testEntities() {
        final World world = new World();
        final long a = world.create();
        final long b = world.create();
        assertTrue(a != b && a != World.WORLD && b != World.WORLD);
        assertTrue(world.isAlive(a));
        assertEquals(2, world.entityCount());
        world.set(POSITION, a, new Position(1, 2));
        world.set(POSITION, b, new Position(3, 4));
        assertEquals(new Position(1, 2), world.get(POSITION, a));
        world.set(POSITION, a, new Position(5, 6));
        assertEquals(new Position(5, 6), world.get(POSITION, a));
        assertNull(world.get(VELOCITY, a));
        assertTrue(world.remove(POSITION, a));
        assertFalse(world.has(POSITION, a));
        world.destroy(b);
        assertFalse(world.isAlive(b));
        assertNull(world.get(POSITION, b));
        assertEquals(0, world.count(POSITION));
        assertThrows(
                IllegalArgumentException.class, () -> world.set(POSITION, b, new Position(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> world.destroy(World.WORLD));
    }

    /** Local entities have their own ids and can't hold simulated components. */
    @Test
    void testLocalEntities() {
        final World world = new World();
        final long local = world.createLocal();
        assertTrue(World.isLocal(local));
        assertTrue(world.isAlive(local));
        assertEquals(1, world.localEntityCount());
        assertEquals(0, world.entityCount());
        world.set(LABEL, local, new StringBuilder("spark"));
        assertThrows(
                IllegalArgumentException.class,
                () -> world.set(POSITION, local, new Position(0, 0)));
        final long simulated = world.create();
        assertFalse(World.isLocal(simulated));
        world.set(LABEL, simulated, new StringBuilder("player"));
        assertEquals("player", world.get(LABEL, simulated).toString());
    }

    /** Only records and enums can be simulated. */
    @Test
    void testSimulatedTypes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ComponentType.simulated("test:bad", StringBuilder.class));
        assertTrue(FROZEN.isSimulated());
        assertFalse(LABEL.isSimulated());
    }

    /** Resources live on the world entity. */
    @Test
    void testResources() {
        final World world = new World();
        assertNull(world.getResource(POSITION));
        world.setResource(POSITION, new Position(7, 8));
        assertEquals(new Position(7, 8), world.getResource(POSITION));
        assertTrue(world.removeResource(POSITION));
        assertNull(world.getResource(POSITION));
    }

    /** Queries visit matching entities in id order, whatever order they were given parts. */
    @Test
    void testQueryOrder() {
        final World world = new World();
        final long[] ids = new long[6];
        for (int i = 0; i < ids.length; ++i) {
            ids[i] = world.create();
        }
        for (int i = ids.length - 1; i >= 0; --i) {
            world.set(POSITION, ids[i], new Position(i, 0));
            if (i % 2 == 0) {
                world.set(VELOCITY, ids[i], new Velocity(1, 0));
            }
        }
        world.set(FROZEN, ids[2], Frozen.YES);
        final List<Long> seen = new ArrayList<>();
        world.each(Query.of(POSITION, VELOCITY).without(FROZEN), seen::add);
        assertEquals(List.of(ids[0], ids[4]), seen);
        final List<Long> all = new ArrayList<>();
        world.each(Query.of(POSITION), all::add);
        assertEquals(6, all.size());
        for (int i = 1; i < all.size(); ++i) {
            assertTrue(all.get(i - 1) < all.get(i));
        }
    }

    /**
     * A query's action can change the world: entities it destroys or changes so they no longer
     * match are skipped, and ones it makes wait for the next walk.
     */
    @Test
    void testChangesDuringQuery() {
        final World world = new World();
        final long a = world.create();
        final long b = world.create();
        final long c = world.create();
        for (long id : new long[] {a, b, c}) {
            world.set(POSITION, id, new Position(0, 0));
        }
        final List<Long> seen = new ArrayList<>();
        world.each(
                Query.of(POSITION),
                entity -> {
                    seen.add(entity);
                    world.set(POSITION, entity, new Position(1, 1));
                    if (entity == a) {
                        world.destroy(b);
                        world.set(POSITION, world.create(), new Position(9, 9));
                    }
                });
        assertEquals(List.of(a, c), seen);
        assertEquals(new Position(1, 1), world.get(POSITION, c));
        assertEquals(3, world.count(POSITION));
    }

    /** Systems run in the order they were added, and names must be unique. */
    @Test
    void testSchedule() {
        final World world = new World();
        final long a = world.create();
        world.set(POSITION, a, new Position(0, 0));
        world.set(VELOCITY, a, new Velocity(2, 3));
        final Query moving = Query.of(POSITION, VELOCITY);
        final Schedule schedule =
                new Schedule()
                        .add(
                                "speed up",
                                w ->
                                        w.each(
                                                moving,
                                                e -> {
                                                    final Velocity v = w.get(VELOCITY, e);
                                                    w.set(
                                                            VELOCITY,
                                                            e,
                                                            new Velocity(v.dx() * 2, v.dy() * 2));
                                                }))
                        .add(
                                "move",
                                w ->
                                        w.each(
                                                moving,
                                                e -> {
                                                    final Position p = w.get(POSITION, e);
                                                    final Velocity v = w.get(VELOCITY, e);
                                                    w.set(
                                                            POSITION,
                                                            e,
                                                            new Position(
                                                                    p.x() + v.dx(),
                                                                    p.y() + v.dy()));
                                                }));
        schedule.run(world);
        assertEquals(new Position(4, 6), world.get(POSITION, a));
        assertEquals(List.of("speed up", "move"), schedule.names());
        assertThrows(IllegalArgumentException.class, () -> schedule.add("move", w -> {}));
    }
}
