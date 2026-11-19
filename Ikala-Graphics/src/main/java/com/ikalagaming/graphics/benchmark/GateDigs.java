package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.LodHandle;
import com.ikalagaming.graphics.SectionHandle;
import com.ikalagaming.graphics.lod.LodStore;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Digs in a {@link GateBenchmark} run's world, as a player would, timing how long until each hole
 * is drawn and until the level of detail has it: from the debug window, on a timer, or a row by row
 * pit for the startup property and the gate's edits. Render thread only.
 */
@Slf4j
public final class GateDigs {

    /** Where in a dig's block the dug block's id is, after its position. */
    private static final int DUG_BLOCK = 3;

    /** How far in front of the camera a dig is, in blocks. */
    private static final double DIG_REACH = 6;

    /** How wide the pit is, in blocks. */
    private static final int PIT_WIDTH = 7;

    /** How long the pit is, in blocks. */
    private static final int PIT_LENGTH = 3;

    /** How high above the pit the camera looks down from, in blocks. */
    private static final double PIT_VIEW_HEIGHT = 10;

    /** How far down the camera looks at the pit, in radians. */
    private static final float PIT_VIEW_PITCH = 1.1f;

    /** How much the ground may rise or fall across the pit's patch, in blocks. */
    private static final int PIT_GROUND_STEP = 1;

    /** How far from where the camera turns to look for an open patch for the pit, in blocks. */
    private static final int PIT_SEARCH = 96;

    /** Nanoseconds in a second. */
    private static final double NANOS_PER_SECOND = 1e9;

    /** Nanoseconds in a millisecond. */
    private static final double NANOS_PER_MILLI = 1e6;

    /** One dig, waiting to be drawn and to reach level of detail. */
    private static final class Dig {
        /** The section the dug block was in. */
        final SectionHandle section;

        /** Where that section is. */
        final LodStore.SectionPos position;

        /** The bake that has the block gone. */
        final long generation;

        /** When it was made, from {@link System#nanoTime()}. */
        final long start;

        /** The block dug: x, y, z, then which block it was, see {@link #DUG_BLOCK}. */
        final int[] block;

        /** When the bake was drawn, or 0. */
        long drawn;

        /** Frames until it was drawn. */
        int frames;

        /**
         * Keep a dig.
         *
         * @param section The section.
         * @param position Its position.
         * @param generation The bake with the block gone.
         * @param block The block dug.
         */
        Dig(SectionHandle section, LodStore.SectionPos position, long generation, int[] block) {
            this.section = section;
            this.position = position;
            this.generation = generation;
            this.block = block;
            start = System.nanoTime();
        }
    }

    /** The run's sections, which digs bake again. */
    private final GateWorld world;

    /** The world being drawn, or null until it is laid out. */
    private final Supplier<SyntheticTerrain> terrain;

    /** Digs waiting to be drawn and to reach level of detail. */
    private final List<Dig> digs = new ArrayList<>();

    /** Pit digs left to make on their own. */
    private int digsLeft;

    /** Pit digs a second. */
    private double pitRate;

    /** Digs a second to make in front of the camera on their own, 0 for none. */
    private double digRate;

    /** When the last dig was made, from {@link System#nanoTime()}. */
    private long lastDig;

    /** Every dig's latency so far: until drawn, then until level of detail, in milliseconds. */
    private final List<double[]> digLatencies = new ArrayList<>();

    /** How many digs were made. */
    private int digCount;

    /** Whether the camera was placed over the pit. */
    private boolean pitView;

    /**
     * The pit's first cell, x and z, once found: an open patch of ground the pit digs make, row by
     * row toward -z. Null until then.
     */
    private int[] pit;

    /**
     * Set up a run's digs.
     *
     * @param world The run's sections.
     * @param terrain The world being drawn, or null until it is laid out.
     */
    GateDigs(@NonNull GateWorld world, @NonNull Supplier<SyntheticTerrain> terrain) {
        this.world = world;
        this.terrain = terrain;
    }

    /**
     * Dig a pit, row by row, at a rate, once digs are made: the camera looks down at an open patch
     * of ground and the digs are made there.
     *
     * @param count How many digs.
     * @param perSecond How many a second.
     */
    void digPit(int count, double perSecond) {
        digsLeft = count;
        pitRate = perSecond;
    }

    /**
     * Dig out the ground a few blocks in front of the camera and rebake the sections around it,
     * timing how long until the hole is drawn and until level of detail has it. Render thread only,
     * once the world is laid out.
     *
     * @param scene The scene.
     * @return False if there is no world to dig in.
     */
    public boolean dig(@NonNull Scene scene) {
        final SyntheticTerrain ground = terrain.get();
        if (ground == null || world.isReleased()) {
            return false;
        }
        final Vector3d at = scene.getCamera().getPosition();
        final Vector3fc forward =
                scene.getCamera().getInvViewMatrix().transformDirection(new Vector3f(0, 0, -1));
        final double length = Math.max(1e-6, Math.hypot(forward.x(), forward.z()));
        // Digs side by side, then row by row, so they make a pit
        final int across = digCount % PIT_WIDTH - PIT_WIDTH / 2;
        final int along = digCount / PIT_WIDTH % PIT_LENGTH;
        // Forward by the reach and the row, sideways by the place in the row
        final double ahead = DIG_REACH + along;
        final int x =
                (int) Math.floor(at.x + (forward.x() * ahead - forward.z() * across) / length);
        final int z =
                (int) Math.floor(at.z + (forward.z() * ahead + forward.x() * across) / length);
        return digAt(ground, x, z);
    }

    /**
     * Dig the next cell of the pit, row by row.
     *
     * @return False if there is no world or pit to dig in.
     */
    private boolean digNextInPit() {
        final SyntheticTerrain ground = terrain.get();
        final int[] origin = pit;
        if (ground == null || world.isReleased() || origin == null) {
            return false;
        }
        final int across = digCount % PIT_WIDTH;
        final int along = digCount / PIT_WIDTH % PIT_LENGTH;
        return digAt(ground, origin[0] + across, origin[1] - along);
    }

    /**
     * Dig out a column's ground block, and any plant standing on it, and rebake the sections around
     * it, timing how long until the hole is drawn and until level of detail has it.
     *
     * @param ground The world.
     * @param x The column's x.
     * @param z The column's z.
     * @return False if the column has no ground to dig.
     */
    private boolean digAt(@NonNull SyntheticTerrain ground, int x, int z) {
        final int y = ground.ground(x, z);
        if (y < ground.minY()) {
            return false;
        }
        final byte dug = ground.block(x, y, z);
        ground.setBlock(x, y, z, SyntheticTerrain.AIR);
        if (ground.block(x, y + 1, z) == SyntheticTerrain.PLANT) {
            // It would float
            ground.setBlock(x, y + 1, z, SyntheticTerrain.AIR);
        }
        final int size = SyntheticTerrain.SECTION;
        final LodStore.SectionPos home =
                new LodStore.SectionPos(
                        Math.floorDiv(x, size), Math.floorDiv(y, size), Math.floorDiv(z, size));
        // Its section, and any neighbor whose face masks it changes
        final Set<LodStore.SectionPos> touched = new LinkedHashSet<>();
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dy = -1; dy <= 1; ++dy) {
                for (int dz = -1; dz <= 1; ++dz) {
                    touched.add(
                            new LodStore.SectionPos(
                                    Math.floorDiv(x + dx, size),
                                    Math.floorDiv(y + dy, size),
                                    Math.floorDiv(z + dz, size)));
                }
            }
        }
        SectionHandle homeHandle = null;
        for (LodStore.SectionPos position : touched) {
            final SectionHandle handle = world.rebake(ground, position);
            if (position.equals(home)) {
                homeHandle = handle;
            }
        }
        if (homeHandle != null) {
            digs.add(
                    new Dig(
                            homeHandle,
                            home,
                            GraphicsManager.getRenderInstance()
                                    .getState()
                                    .sections
                                    .requestedGeneration(homeHandle.id()),
                            new int[] {x, y, z, dug}));
        }
        lastDig = System.nanoTime();
        digCount += 1;
        return true;
    }

    /**
     * Dig on a timer, as a quick player would. Render thread only.
     *
     * @param perSecond Digs a second, 0 to stop.
     */
    public void setDigRate(double perSecond) {
        digRate = Math.max(0, perSecond);
    }

    /**
     * Every dig's latency so far.
     *
     * @return For each dig, milliseconds until its hole was drawn and until level of detail had it,
     *     then frames until it was drawn.
     */
    public List<double[]> getDigLatencies() {
        return List.copyOf(digLatencies);
    }

    /**
     * How many digs were made.
     *
     * @return The count.
     */
    int count() {
        return digCount;
    }

    /**
     * Whether every pit dig was made and has reached the level of detail.
     *
     * @return True if nothing is left or waiting.
     */
    boolean isDone() {
        return digsLeft == 0 && digs.isEmpty();
    }

    /**
     * Stop digging the pit and stop timing the digs in flight.
     *
     * @return How many pit digs weren't made or didn't reach the level of detail.
     */
    int abandon() {
        final int unfinished = digs.size() + digsLeft;
        digs.clear();
        digsLeft = 0;
        return unfinished;
    }

    /** Forget the digs in flight, as the world is released. */
    void clear() {
        digs.clear();
    }

    /**
     * Find an open patch of ground for the pit, with a column to spare around it and open ground
     * under the camera, nearest in front of where the camera turns: every column's ground open to
     * the sky, so the camera above sees each dig. Searches outward in squares.
     *
     * @param ground The world.
     * @return The pit's first cell, x and z; in front of the camera if nothing nearby is open.
     */
    private static int[] findPit(@NonNull SyntheticTerrain ground) {
        final int startX = -PIT_WIDTH / 2;
        final int startZ = (int) -DIG_REACH;
        for (int ring = 0; ring <= PIT_SEARCH; ++ring) {
            for (int dz = -ring; dz <= ring; ++dz) {
                for (int dx = -ring; dx <= ring; ++dx) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    if (isOpenPatch(ground, startX + dx, startZ + dz)) {
                        return new int[] {startX + dx, startZ + dz};
                    }
                }
            }
        }
        return new int[] {startX, startZ};
    }

    /**
     * Whether the pit would be open with its first cell somewhere, a column around it and the
     * ground toward the camera included: grass open to the sky, nearly level.
     *
     * @param ground The world.
     * @param x The first cell's x.
     * @param z The first cell's z.
     * @return True if every column is open.
     */
    private static boolean isOpenPatch(@NonNull SyntheticTerrain ground, int x, int z) {
        final int level = ground.ground(x, z);
        for (int column = x - 1; column <= x + PIT_WIDTH; ++column) {
            // Up to under the camera, so nothing between it and the pit hides the digs
            for (int row = z - PIT_LENGTH; row <= z + (int) DIG_REACH + 1; ++row) {
                if (!ground.isOpen(column, row)) {
                    return false;
                }
                // Natural ground, nearly level: no huts, no cliffs
                final int top = ground.ground(column, row);
                final byte surface = ground.block(column, top, row);
                if ((surface != SyntheticTerrain.GRASS && surface != SyntheticTerrain.SLAB)
                        || Math.abs(top - level) > PIT_GROUND_STEP) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Make digs that are due, and time the ones in flight. Render thread, once a frame. */
    void update() {
        final SyntheticTerrain ground = terrain.get();
        if (digsLeft > 0 && !pitView && ground != null) {
            // Look down at an open patch of ground where the pit goes
            pitView = true;
            pit = findPit(ground);
            final Scene scene = GraphicsManager.getScene();
            final int middle = pit[0] + PIT_WIDTH / 2;
            final int top = ground.ground(middle, pit[1]);
            // Back from the middle row by as far as the view reaches looking down, so it is
            // in the middle of the frame
            final double z =
                    pit[1] - PIT_LENGTH / 2 + 0.5 + PIT_VIEW_HEIGHT / Math.tan(PIT_VIEW_PITCH);
            scene.getCamera().setPosition(middle + 0.5, top + PIT_VIEW_HEIGHT, z);
            scene.getCamera().setRotation(PIT_VIEW_PITCH, 0);
            log.info(
                    "Gate benchmark digging a pit at ({}, {}, {}), {} by {} blocks",
                    pit[0],
                    top,
                    pit[1],
                    PIT_WIDTH,
                    PIT_LENGTH);
        }
        final double rate = digsLeft > 0 ? pitRate : digRate;
        if (rate > 0 && (System.nanoTime() - lastDig) / NANOS_PER_SECOND >= 1 / rate) {
            final Scene scene = GraphicsManager.getScene();
            if (digsLeft > 0) {
                if (digNextInPit()) {
                    --digsLeft;
                }
            } else if (scene != null) {
                dig(scene);
            }
        }
        final var state = GraphicsManager.getRenderInstance().getState();
        final var manager = state.sections;
        final LodHandle lodWorld = world.lodWorld();
        final Iterator<Dig> it = digs.iterator();
        while (it.hasNext()) {
            final Dig dig = it.next();
            final long now = System.nanoTime();
            if (dig.drawn == 0) {
                dig.frames += 1;
                if (manager.currentGeneration(dig.section.id()) >= dig.generation) {
                    dig.drawn = now;
                }
            }
            final boolean inLod =
                    lodWorld == null
                            || state.lods.coveredGeneration(lodWorld.id(), dig.position)
                                    >= dig.generation;
            if (dig.drawn != 0 && inLod) {
                final double drawnMillis = (dig.drawn - dig.start) / NANOS_PER_MILLI;
                final double lodMillis = (now - dig.start) / NANOS_PER_MILLI;
                digLatencies.add(new double[] {drawnMillis, lodMillis, dig.frames});
                log.info(
                        "Dig of block {} at ({}, {}, {}): drawn after {} ms ({} frames), in level of"
                                + " detail after {} ms; {} sections drawing themselves; last bake"
                                + " on the render thread {} ms",
                        dig.block[DUG_BLOCK],
                        dig.block[0],
                        dig.block[1],
                        dig.block[2],
                        String.format("%.1f", drawnMillis),
                        dig.frames,
                        String.format("%.1f", lodMillis),
                        manager.getOverrideCount(),
                        String.format("%.2f", manager.getLastUrgentNanos() / NANOS_PER_MILLI));
                it.remove();
            }
        }
    }
}
