package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The places to look from in a {@link GateBenchmark} run's world: its preset's, then those the run
 * finds once the world is laid out, which the debug window can jump to. Render thread only.
 */
@Slf4j
public final class GateViews {

    /**
     * A place to look from in a preset's world, which the debug window can jump to.
     *
     * @param name What menus show for it.
     * @param x Where the camera is, along x.
     * @param y Along y.
     * @param z Along z.
     * @param pitch How far it looks down, in radians.
     * @param yaw How far it is turned about y, in radians.
     */
    public record View(
            @NonNull String name, double x, double y, double z, float pitch, float yaw) {}

    /** How far a view stands back from what it looks at, and how high above it, in meters. */
    private static final double VIEW_BACK = 20;

    private static final double VIEW_HEIGHT = 10;

    /** How far above the ground under it a view stands at least, in meters. */
    private static final double VIEW_CLEARANCE = 4;

    /** How high above a lake its view is at least, how far back along z, and how far down. */
    private static final double LAKE_VIEW_HEIGHT = 37;

    private static final double LAKE_VIEW_BACK = 10;

    private static final float LAKE_VIEW_PITCH = 1.2f;

    /** How far above the measuring turn the overview looks from, and how far down, in radians. */
    private static final double OVERVIEW_HEIGHT = 40;

    private static final float OVERVIEW_PITCH = 0.6f;

    /** How far it looks down, in radians. */
    private static final float VIEW_PITCH = 0.35f;

    /** How many tree cells out the hut nearest the world's middle is looked for. */
    private static final int HUT_SEARCH = 4;

    /** Values in a view: x, y, z, pitch and yaw. */
    private static final int VIEW_VALUES = 5;

    /** The views, the preset's first. */
    private final List<View> views = new ArrayList<>();

    /** Whether the camera was placed at the first view or the startup property's. */
    private boolean viewPlaced;

    /**
     * Start with a preset's views.
     *
     * @param preset The preset's views.
     */
    void addAll(@NonNull List<View> preset) {
        views.addAll(preset);
    }

    /**
     * Add the places to look from the run finds: the world's middle, where the measuring turn is,
     * if the preset gave none; a lake and the huts near the middle on the hills.
     *
     * @param world The world.
     * @param lakeSpot Where a lake is near the world's middle, x and z, or null for none.
     */
    void find(@NonNull SyntheticTerrain world, @Nullable int[] lakeSpot) {
        if (views.isEmpty()) {
            views.add(
                    new View(
                            "Overview",
                            0.5,
                            world.cameraY() + OVERVIEW_HEIGHT,
                            0.5 + OVERVIEW_HEIGHT,
                            OVERVIEW_PITCH,
                            0));
            views.add(new View("Middle", 0.5, world.cameraY(), 0.5, GateBenchmark.PITCH, 0));
        }
        if (lakeSpot != null) {
            // From above, looking steeply down: the lakes lie in valleys under the trees
            views.add(
                    new View(
                            "Over a lake",
                            lakeSpot[0] + 0.5,
                            Math.max(
                                    SyntheticTerrain.LAKE_SURFACE + LAKE_VIEW_HEIGHT,
                                    world.top(lakeSpot[0], lakeSpot[1]) + VIEW_HEIGHT),
                            lakeSpot[1] + LAKE_VIEW_BACK,
                            LAKE_VIEW_PITCH,
                            0));
        }
        final int[] hut = world.hutNear(0, 0, HUT_SEARCH);
        if (hut != null) {
            views.add(lookingAt(world, "By the huts", hut[0], hut[1], hut[2]));
        }
        for (View view : views) {
            log.info(
                    "Gate benchmark view {}: {},{},{},{},{}",
                    view.name(),
                    view.x(),
                    view.y(),
                    view.z(),
                    view.pitch(),
                    view.yaw());
        }
    }

    /**
     * A view looking down at a point from back along +z and above it, clear of the ground there.
     *
     * @param world The world.
     * @param name The view's name.
     * @param x The point's x.
     * @param y Its y.
     * @param z Its z.
     * @return The view.
     */
    private static View lookingAt(
            @NonNull SyntheticTerrain world, @NonNull String name, int x, double y, int z) {
        final int backZ = (int) (z + VIEW_BACK);
        final double height = Math.max(y + VIEW_HEIGHT, world.top(x, backZ) + 1 + VIEW_CLEARANCE);
        return new View(name, x + 0.5, height, backZ + 0.5, VIEW_PITCH, 0);
    }

    /**
     * The places to look from: the preset's, then those the run found.
     *
     * @return The views.
     */
    public List<View> getViews() {
        return Collections.unmodifiableList(views);
    }

    /**
     * How many views there are.
     *
     * @return The count.
     */
    int size() {
        return views.size();
    }

    /**
     * Put the camera at a place to look from. Render thread only.
     *
     * @param view Where.
     */
    public void goTo(@NonNull View view) {
        final Scene scene = GraphicsManager.getScene();
        if (scene == null) {
            return;
        }
        scene.getCamera().setPosition(view.x(), view.y(), view.z());
        scene.getCamera().setRotation(view.pitch(), view.yaw());
    }

    /**
     * Once the run is done, put the camera where the {@code view} option says, if it says: {@code
     * x,y,z,pitch,yaw}, the angles in radians, for comparing frames from the same place; else at
     * the first view, if there is one. Only the first call does anything.
     *
     * @param scene The scene.
     * @param view The {@code view} option, or null.
     */
    void placeOnce(@NonNull Scene scene, @Nullable String view) {
        if (viewPlaced || (view == null && views.isEmpty())) {
            return;
        }
        viewPlaced = true;
        if (view == null) {
            goTo(views.getFirst());
            return;
        }
        final String[] parts = view.split(",");
        if (parts.length != VIEW_VALUES) {
            log.warn("The benchmark view must be x,y,z,pitch,yaw, not {}", view);
            return;
        }
        final double[] values = new double[VIEW_VALUES];
        for (int i = 0; i < VIEW_VALUES; ++i) {
            values[i] = Double.parseDouble(parts[i].trim());
        }
        scene.getCamera().setPosition(values[0], values[1], values[2]);
        scene.getCamera().setRotation((float) values[3], (float) values[4]);
    }
}
