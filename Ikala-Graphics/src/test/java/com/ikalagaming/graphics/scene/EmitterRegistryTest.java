package com.ikalagaming.graphics.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.EmitterHandle;
import com.ikalagaming.graphics.vulkan.ParticleMath;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Emitters' links, which their particles look up on the GPU. */
class EmitterRegistryTest {

    /** Who owns the emitters. */
    private static final String OWNER = "test";

    /** An effect that needs a link, for its spawn box. */
    private static final ParticleEffect BOXED =
            ParticleEffect.builder().rate(0).spawnBox(new Vector3f(2, 2, 2)).build();

    /** An effect that follows its emitter, which takes a link, so its emitter is always listed. */
    private static final ParticleEffect STEADY =
            ParticleEffect.builder().rate(10).follow(1).build();

    /** How close vectors must be. */
    private static final double TOLERANCE = 1e-9;

    /**
     * This frame's spawns.
     *
     * @param registry The registry.
     * @return Its spawns.
     */
    private static List<EmitterRegistry.Spawn> collect(EmitterRegistry registry) {
        return collect(registry, 1);
    }

    /**
     * This frame's spawns, over a frame of some length.
     *
     * @param registry The registry.
     * @param seconds How long the frame was.
     * @return Its spawns.
     */
    private static List<EmitterRegistry.Spawn> collect(EmitterRegistry registry, double seconds) {
        final List<EmitterRegistry.Spawn> spawns = new ArrayList<>();
        registry.collect(seconds, spawns);
        return spawns;
    }

    /**
     * A moved emitter's spawns spread from where it was to where it is, its velocity and move
     * worked out from the two; its first frame, a jump past the teleport distance and a teleport
     * spawn where it is, still and with nothing moved but what it really moved.
     */
    @Test
    void testSpawnsSpreadAlongTheWay() {
        final EmitterRegistry registry = new EmitterRegistry();
        final EmitterHandle handle = registry.add(OWNER, STEADY, new Vector3d(1, 2, 3));
        EmitterRegistry.Spawn first = collect(registry, 0.5).getFirst();
        assertEquals(new Vector3d(1, 2, 3), first.from());
        assertEquals(new Vector3d(), first.moved());
        assertEquals(new Vector3d(), first.velocity());

        registry.move(OWNER, handle, new Vector3d(2, 2, 3));
        EmitterRegistry.Spawn moved = collect(registry, 0.5).getFirst();
        assertEquals(new Vector3d(1, 2, 3), moved.from());
        assertEquals(new Vector3d(2, 2, 3), moved.position());
        assertEquals(new Vector3d(2, 2, 3), moved.end());
        assertTrue(new Vector3d(1, 0, 0).equals(moved.moved(), TOLERANCE));
        assertTrue(new Vector3d(2, 0, 0).equals(moved.velocity(), TOLERANCE));

        final double far = EmitterRegistry.TELEPORT_DISTANCE + 1;
        registry.move(OWNER, handle, new Vector3d(2 + far, 2, 3));
        EmitterRegistry.Spawn jumped = collect(registry, 0.5).getFirst();
        assertEquals(jumped.position(), jumped.from());
        assertEquals(new Vector3d(), jumped.velocity());
        assertTrue(new Vector3d(far, 0, 0).equals(jumped.moved(), TOLERANCE));

        registry.teleport(OWNER, handle, new Vector3d(2 + far, 2, 4));
        EmitterRegistry.Spawn teleported = collect(registry, 0.5).getFirst();
        assertEquals(teleported.position(), teleported.from());
        assertEquals(new Vector3d(), teleported.velocity());

        // A frame that took no time, as while paused, moves nothing and keeps the way for later
        registry.move(OWNER, handle, new Vector3d(3 + far, 2, 4));
        assertEquals(new Vector3d(), collect(registry, 0).getFirst().moved());
        assertTrue(
                new Vector3d(1, 0, 0).equals(collect(registry, 0.5).getFirst().moved(), TOLERANCE));
    }

    /** A line keeps both ends, and where each was; moving it as a point makes it one again. */
    @Test
    void testLines() {
        final EmitterRegistry registry = new EmitterRegistry();
        final EmitterHandle handle = registry.add(OWNER, STEADY, new Vector3d());
        registry.moveLine(OWNER, handle, new Vector3d(0, 1, 0), new Vector3d(0, 2, 0));
        collect(registry);
        registry.moveLine(OWNER, handle, new Vector3d(1, 1, 0), new Vector3d(1, 2, 0));
        final EmitterRegistry.Spawn swept = collect(registry).getFirst();
        assertEquals(new Vector3d(0, 1, 0), swept.from());
        assertEquals(new Vector3d(0, 2, 0), swept.fromEnd());
        assertEquals(new Vector3d(1, 2, 0), swept.end());
        registry.move(OWNER, handle, new Vector3d(1, 1, 0));
        final EmitterRegistry.Spawn point = collect(registry).getFirst();
        assertEquals(point.position(), point.end());
    }

    /** An emitter turns as told, but not one around the camera. */
    @Test
    void testOrient() {
        final EmitterRegistry registry = new EmitterRegistry();
        final Quaternionf turn = new Quaternionf().rotateY(1);
        final EmitterHandle handle = registry.add(OWNER, STEADY, new Vector3d());
        final EmitterHandle weather =
                registry.add(
                        OWNER,
                        ParticleEffect.builder().rate(10).aroundCamera(true).build(),
                        new Vector3d());
        registry.orient(OWNER, handle, turn);
        registry.orient(OWNER, weather, turn);
        final List<EmitterRegistry.Spawn> spawns = collect(registry);
        assertTrue(turn.equals(spawns.get(0).rotation(), (float) TOLERANCE));
        assertEquals(new Quaternionf(), spawns.get(1).rotation());
    }

    /**
     * Linked emitters are listed with their own link even when they spawn nothing; others aren't
     * listed until they spawn, and have none.
     */
    @Test
    void testLinkedEmittersAreListed() {
        final EmitterRegistry registry = new EmitterRegistry();
        registry.add(OWNER, ParticleEffect.builder().rate(0).build(), new Vector3d());
        registry.add(OWNER, BOXED, new Vector3d());
        registry.add(OWNER, BOXED, new Vector3d());
        final List<EmitterRegistry.Spawn> spawns = collect(registry);
        assertEquals(2, spawns.size());
        assertEquals(0, spawns.get(0).count());
        assertTrue(spawns.get(0).link() > 0);
        assertNotEquals(spawns.get(0).link(), spawns.get(1).link());
        assertEquals(spawns.get(0).link(), collect(registry).get(0).link());
    }

    /** Links are let go of with their emitter or effect, and run out past the most there are. */
    @Test
    void testLinksAreReused() {
        final EmitterRegistry registry = new EmitterRegistry();
        final List<EmitterHandle> handles = new ArrayList<>();
        for (int i = 0; i < ParticleMath.MAX_LINKS + 1; ++i) {
            handles.add(registry.add(OWNER, BOXED, new Vector3d()));
        }
        final List<EmitterRegistry.Spawn> full = collect(registry);
        assertEquals(ParticleMath.MAX_LINKS, full.size());
        registry.remove(OWNER, handles.getFirst());
        registry.setEffect(OWNER, handles.get(1), ParticleEffect.builder().rate(0).build());
        // The one left without a link takes a freed one; the other freed link waits
        final List<EmitterRegistry.Spawn> after = collect(registry);
        assertEquals(ParticleMath.MAX_LINKS - 1, after.size());
        for (EmitterRegistry.Spawn spawn : after) {
            assertTrue(spawn.link() > 0 && spawn.link() <= ParticleMath.MAX_LINKS);
        }
        assertEquals(
                after.size(),
                after.stream().mapToInt(EmitterRegistry.Spawn::link).distinct().count());
    }
}
