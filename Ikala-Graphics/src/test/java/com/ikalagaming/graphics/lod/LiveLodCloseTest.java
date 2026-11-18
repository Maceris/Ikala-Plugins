package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.benchmark.SyntheticTerrain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests that closing a live world ends its builder thread. */
class LiveLodCloseTest {

    /** Options for a test build, on two threads. */
    private static final DagBuilder.Options OPTIONS =
            new DagBuilder.Options(0.5, false, 2, false, 0);

    /** The builder thread's name, as the plugin names it. */
    private static final String BUILDER = GraphicsPlugin.PLUGIN_NAME + "/LOD builder";

    /**
     * The live builder threads.
     *
     * @return The threads.
     */
    private static List<Thread> builders() {
        final List<Thread> found = new ArrayList<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (BUILDER.equals(thread.getName()) && thread.isAlive()) {
                found.add(thread);
            }
        }
        return found;
    }

    @Test
    void closeWaitsForTheBuilder() {
        final List<Thread> before = builders();
        final List<LiveLod.Batch> batches = new ArrayList<>();
        final LiveLod live =
                new LiveLod(new double[3], SyntheticTerrain.SECTION, 1, OPTIONS, batches::add);
        live.start();

        live.close();

        final List<Thread> after = builders();
        after.removeAll(before);
        assertTrue(after.isEmpty(), "The builder is still running after close");
    }

    @Test
    void closeWithoutStartingIsFine() {
        final LiveLod live =
                new LiveLod(new double[3], SyntheticTerrain.SECTION, 1, OPTIONS, batch -> {});
        live.close();
        // Twice, as stopping the workers and cleaning up both do
        assertDoesNotThrow(live::close);
    }
}
