package com.ikalagaming.ecs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.ecs.TestWorld.Position;
import com.ikalagaming.ecs.TestWorld.Velocity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Tests for snapshots: keeping, comparing, restoring and replaying. */
class TestSnapshot {

    /** The position type. */
    private static final ComponentType<Position> POSITION = TestWorld.POSITION;

    /** The velocity type. */
    private static final ComponentType<Velocity> VELOCITY = TestWorld.VELOCITY;

    /** The local label type. */
    private static final ComponentType<StringBuilder> LABEL = TestWorld.LABEL;

    /** One frame's input: a push for every moving entity. */
    record Push(long dx, long dy) {}

    /** The input type, a resource. */
    private static final ComponentType<Push> PUSH =
            ComponentType.simulated("test:push", Push.class);

    /** Things that move. */
    private static final Query MOVING = Query.of(POSITION, VELOCITY);

    /**
     * A step: push everything by the frame's input, move it, and every so often spawn something or
     * destroy something, so replays must hand out the same ids.
     */
    private static final Schedule STEP =
            new Schedule()
                    .add(
                            "push",
                            w -> {
                                final Push push = w.getResource(PUSH);
                                w.each(
                                        MOVING,
                                        e -> {
                                            final Velocity v = w.get(VELOCITY, e);
                                            w.set(
                                                    VELOCITY,
                                                    e,
                                                    new Velocity(
                                                            v.dx() + push.dx(),
                                                            v.dy() + push.dy()));
                                        });
                            })
                    .add(
                            "move",
                            w ->
                                    w.each(
                                            MOVING,
                                            e -> {
                                                final Position p = w.get(POSITION, e);
                                                final Velocity v = w.get(VELOCITY, e);
                                                w.set(
                                                        POSITION,
                                                        e,
                                                        new Position(
                                                                p.x() + v.dx(), p.y() + v.dy()));
                                            }))
                    .add(
                            "spawn and despawn",
                            w -> {
                                final Push push = w.getResource(PUSH);
                                if (push.dx() > 2) {
                                    final long spawned = w.create();
                                    w.set(POSITION, spawned, new Position(push.dx(), push.dy()));
                                    w.set(VELOCITY, spawned, new Velocity(0, 0));
                                }
                                if (push.dy() < -2) {
                                    final List<Long> found = new ArrayList<>();
                                    w.each(MOVING, found::add);
                                    if (found.size() > 1) {
                                        w.destroy(found.get(0));
                                    }
                                }
                            });

    /**
     * Run some frames.
     *
     * @param world The world.
     * @param inputs Each frame's input.
     * @param from The first frame.
     * @param to The frame after the last.
     */
    private static void run(World world, Push[] inputs, int from, int to) {
        for (int frame = from; frame < to; ++frame) {
            world.setResource(PUSH, inputs[frame]);
            STEP.run(world);
        }
    }

    /**
     * A world with a few moving things.
     *
     * @return The world.
     */
    private static World start() {
        final World world = new World();
        for (int i = 0; i < 4; ++i) {
            final long e = world.create();
            world.set(POSITION, e, new Position(i * 10L, 0));
            world.set(VELOCITY, e, new Velocity(0, 0));
            world.set(LABEL, e, new StringBuilder("thing " + i));
        }
        return world;
    }

    /**
     * Random inputs.
     *
     * @param count How many frames.
     * @return The inputs.
     */
    private static Push[] inputs(int count) {
        final SplittableRandom random = new SplittableRandom(42);
        final Push[] out = new Push[count];
        for (int i = 0; i < count; ++i) {
            out[i] = new Push(random.nextLong(-3, 4), random.nextLong(-3, 4));
        }
        return out;
    }

    /** Changes after a snapshot don't reach it, and restoring brings the old state back. */
    @Test
    void testKeepAndRestore() {
        final World world = start();
        final long first = 1;
        final Snapshot before = world.snapshot();
        world.set(POSITION, first, new Position(99, 99));
        final long added = world.create();
        world.set(POSITION, added, new Position(5, 5));
        world.destroy(2);
        assertEquals(new Position(0, 0), before.get(POSITION, first));
        assertFalse(before.isAlive(added));
        assertTrue(before.isAlive(2));
        assertNotEquals(before, world.snapshot());

        world.restore(before);
        assertEquals(new Position(0, 0), world.get(POSITION, first));
        assertFalse(world.isAlive(added));
        assertTrue(world.isAlive(2));
        assertEquals(new Position(10, 0), world.get(POSITION, 2));
        assertEquals(before, world.snapshot());
        assertEquals(before.hashCode(), world.snapshot().hashCode());

        // Writes after a restore must not reach the snapshot that was restored
        world.set(POSITION, first, new Position(-1, -1));
        assertEquals(new Position(0, 0), before.get(POSITION, first));
    }

    /**
     * Restoring keeps local components of entities that still exist, drops those of simulated
     * entities that no longer do, and never touches local entities.
     */
    @Test
    void testLocalComponents() {
        final World world = start();
        final long local = world.createLocal();
        world.set(LABEL, local, new StringBuilder("spark"));
        final Snapshot before = world.snapshot();
        world.get(LABEL, 1).append(" changed");
        final long added = world.create();
        world.set(LABEL, added, new StringBuilder("new"));

        world.restore(before);
        assertEquals("thing 0 changed", world.get(LABEL, 1).toString());
        assertNull(world.get(LABEL, added));
        assertTrue(world.isAlive(local));
        assertEquals("spark", world.get(LABEL, local).toString());
    }

    /**
     * Restoring a frame from the middle and replaying the same inputs gives exactly the state a
     * straight run gives, ids of spawned entities included.
     */
    @Test
    void testReplay() {
        final Push[] inputs = inputs(200);
        final World straight = start();
        run(straight, inputs, 0, inputs.length);
        final Snapshot end = straight.snapshot();

        final World rewound = start();
        final Snapshot[] frames = new Snapshot[inputs.length];
        for (int frame = 0; frame < inputs.length; ++frame) {
            run(rewound, inputs, frame, frame + 1);
            frames[frame] = rewound.snapshot();
        }
        assertEquals(end, rewound.snapshot());
        assertTrue(end.getNextId() > 5, "nothing spawned");
        assertTrue(end.entityCount() < end.getNextId() - 1, "nothing destroyed");

        for (int from : new int[] {0, 57, 120, 198}) {
            rewound.restore(frames[from]);
            run(rewound, inputs, from + 1, inputs.length);
            assertEquals(end, rewound.snapshot(), () -> "replay from " + from);
        }
    }

    /** A changed input shows up as a difference, which replaying from before it repairs. */
    @Test
    void testMispredict() {
        final Push[] truth = inputs(60);
        final Push[] guess = truth.clone();
        guess[30] = new Push(guess[30].dx() + 1, 0);

        final World server = start();
        run(server, truth, 0, 40);
        final World client = start();
        final Snapshot[] frames = new Snapshot[60];
        for (int frame = 0; frame < 40; ++frame) {
            run(client, guess, frame, frame + 1);
            frames[frame] = client.snapshot();
        }
        final Snapshot serverAt40 = server.snapshot();
        assertNotEquals(serverAt40, frames[39]);
        assertFalse(serverAt40.differences(frames[39], 10).isEmpty());
        assertTrue(serverAt40.differences(serverAt40, 10).isEmpty());

        client.restore(frames[29]);
        run(client, truth, 30, 40);
        assertEquals(serverAt40, client.snapshot());
    }
}
