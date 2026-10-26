package com.ikalagaming.graphics.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.EmitterHandle;
import com.ikalagaming.graphics.vulkan.ParticleMath;

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

    /**
     * This frame's spawns.
     *
     * @param registry The registry.
     * @return Its spawns.
     */
    private static List<EmitterRegistry.Spawn> collect(EmitterRegistry registry) {
        final List<EmitterRegistry.Spawn> spawns = new ArrayList<>();
        registry.collect(1, spawns);
        return spawns;
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
