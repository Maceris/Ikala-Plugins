package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.scene.ImportedScene;
import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.converter.scene.ScenePlacement;
import com.ikalagaming.graphics.DebugShapeHandle;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.PreviewView;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.scene.debug.DebugBox;
import com.ikalagaming.graphics.scene.debug.DebugLine;
import com.ikalagaming.graphics.scene.debug.DebugShape;
import com.ikalagaming.graphics.scene.debug.DebugSphere;
import com.ikalagaming.graphics.scene.debug.DebugVisualizers;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * What the 3D viewer can draw over a scene, to check it against the block grid: a grid of 1/16 m
 * squares on the ground under it, its bounds, the vertices that are off that grid, its normals and
 * its wireframe. Drawn as debug shapes in the viewer's preview only. Render thread only.
 */
public class SceneOverlays {
    /** The grid's step, in meters: a block's pixel. */
    public static final double GRID_STEP = 1.0 / 16;

    /** How far from the grid a vertex may be and still count as on it, in meters. */
    static final double GRID_TOLERANCE = 1e-4;

    /** The most fine grid lines drawn each way; past it, lines every meter instead. */
    static final int MAX_GRID_LINES = 256;

    /** The most off-grid vertices marked; the rest are only counted. */
    static final int MAX_MARKERS = 2000;

    /** A fine grid line. */
    private static final int GRID_COLOR = Color.rgba(0.5f, 0.55f, 0.6f, 0.6f);

    /** A grid line on a whole meter. */
    private static final int METER_COLOR = Color.rgba(0.85f, 0.9f, 1.0f, 0.9f);

    /** The bounds' outline. */
    private static final int BOUNDS_COLOR = Color.rgba(0.3f, 0.9f, 1.0f, 1.0f);

    /** An off-grid vertex's marker. */
    private static final int OFF_GRID_COLOR = Color.rgba(1.0f, 0.2f, 0.6f, 1.0f);

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** Whether the grid is drawn. */
    private boolean grid;

    /** Whether the bounds are drawn. */
    private boolean bounds;

    /** Whether off-grid vertices are marked. */
    private boolean offGrid;

    /** The shapes drawn now. */
    private final List<DebugShapeHandle> shapes = new ArrayList<>();

    /** The scene the shapes were made for, so a new one is redrawn. */
    private ScenePlacement drawnFor;

    /** The preview the shapes are in. */
    private PreviewView drawnIn;

    /**
     * How many vertices are off the grid, from the last check, or -1 if not checked. -- GETTER --
     * The count of off-grid vertices.
     *
     * @return The count, or -1.
     */
    @Getter private int offGridCount = -1;

    /** Whether the shapes need making again. */
    private boolean dirty;

    /**
     * Set up the overlays, all off.
     *
     * @param graphics The plugin's graphics context.
     */
    public SceneOverlays(@NonNull GraphicsContext graphics) {
        this.graphics = graphics;
    }

    /**
     * Draw the overlays' checkboxes.
     *
     * @param view The preview, whose normals and wireframe are toggled directly.
     */
    public void drawControls(@NonNull PreviewView view) {
        if (IkGui.checkbox("1/16 m grid", grid)) {
            setGrid(!grid);
        }
        IkGui.sameLine();
        if (IkGui.checkbox("Bounds", bounds)) {
            setBounds(!bounds);
        }
        IkGui.sameLine();
        if (IkGui.checkbox("Off-grid vertices", offGrid)) {
            setOffGrid(!offGrid);
        }
        IkGui.setItemTooltip(
                "Mark the vertices that aren't on the 1/16 m grid blocks are drawn on, in pink");
        final DebugVisualizers visualizers = view.getScene().getDebugVisualizers();
        if (IkGui.checkbox("Normals", visualizers.isNormals())) {
            visualizers.setNormals(!visualizers.isNormals());
        }
        IkGui.sameLine();
        if (IkGui.checkbox("Wireframe", visualizers.isWireframe())) {
            visualizers.setWireframe(!visualizers.isWireframe());
        }
        if (offGrid && offGridCount >= 0) {
            IkGui.textDisabled(
                    offGridCount == 0
                            ? "Every vertex is on the grid"
                            : String.format("%,d vertices off the grid", offGridCount));
        }
    }

    /**
     * Turn the grid on or off.
     *
     * @param on Whether to draw it.
     */
    public void setGrid(boolean on) {
        grid = on;
        dirty = true;
    }

    /**
     * Turn the bounds on or off.
     *
     * @param on Whether to draw them.
     */
    public void setBounds(boolean on) {
        bounds = on;
        dirty = true;
    }

    /**
     * Turn the off-grid markers on or off.
     *
     * @param on Whether to mark them.
     */
    public void setOffGrid(boolean on) {
        offGrid = on;
        dirty = true;
    }

    /**
     * Make the shapes again if the scene or the choices changed. Call once a frame.
     *
     * @param shown The scene, or null if none is shown.
     * @param view The preview it is shown in, or null.
     */
    public void update(ScenePlacement shown, PreviewView view) {
        if (!dirty && shown == drawnFor && view == drawnIn) {
            return;
        }
        clear();
        dirty = false;
        drawnFor = shown;
        drawnIn = view;
        offGridCount = -1;
        if (shown == null || view == null) {
            return;
        }
        final ImportedScene imported = shown.getImported();
        final Vector3d min = new Vector3d();
        final Vector3d max = new Vector3d();
        final boolean hasBounds = imported.bounds(imported.root(), min, max);
        final List<DebugShape> made = new ArrayList<>();
        if (grid && hasBounds) {
            gridLines(min, max, made);
        }
        if (bounds && hasBounds) {
            final Vector3d center = new Vector3d(min).add(max).mul(0.5);
            final Vector3d half = new Vector3d(max).sub(min).mul(0.5);
            made.add(new DebugBox(center, half, BOUNDS_COLOR));
        }
        if (offGrid) {
            final List<Vector3d> off = new ArrayList<>();
            offGridCount = offGridVertices(imported, off, MAX_MARKERS);
            final double radius = GRID_STEP / 4;
            for (Vector3d vertex : off) {
                made.add(new DebugSphere(vertex, radius, OFF_GRID_COLOR, true));
            }
        }
        for (DebugShape shape : made) {
            shapes.add(graphics.debug().add(shape, view));
        }
    }

    /**
     * The grid's lines on the ground under the bounds: every 1/16 m, and brighter on whole meters,
     * or only on whole meters when the fine lines would be too many.
     *
     * @param min The bounds' low corner.
     * @param max The bounds' high corner.
     * @param out Receives the lines.
     */
    static void gridLines(Vector3d min, Vector3d max, List<DebugShape> out) {
        final double y = min.y;
        // A step past the bounds each way, on the grid
        final double x0 = Math.floor(min.x) - 1;
        final double x1 = Math.ceil(max.x) + 1;
        final double z0 = Math.floor(min.z) - 1;
        final double z1 = Math.ceil(max.z) + 1;
        final boolean fine =
                (x1 - x0) / GRID_STEP <= MAX_GRID_LINES && (z1 - z0) / GRID_STEP <= MAX_GRID_LINES;
        final double step = fine ? GRID_STEP : 1;
        final long xs = Math.round((x1 - x0) / step);
        final long zs = Math.round((z1 - z0) / step);
        for (long i = 0; i <= xs; ++i) {
            final double x = x0 + i * step;
            final int color = (fine && i % 16 != 0) ? GRID_COLOR : METER_COLOR;
            out.add(new DebugLine(new Vector3d(x, y, z0), new Vector3d(x, y, z1), color));
        }
        for (long i = 0; i <= zs; ++i) {
            final double z = z0 + i * step;
            final int color = (fine && i % 16 != 0) ? GRID_COLOR : METER_COLOR;
            out.add(new DebugLine(new Vector3d(x0, y, z), new Vector3d(x1, y, z), color));
        }
    }

    /**
     * Find the vertices, placed where the scene puts them, that are off the 1/16 m grid.
     *
     * @param imported The scene.
     * @param out Receives up to the limit of them.
     * @param limit The most to hand out.
     * @return How many there are in all.
     */
    static int offGridVertices(ImportedScene imported, List<Vector3d> out, int limit) {
        int count = 0;
        final Deque<Node> open = new ArrayDeque<>();
        open.push(imported.root());
        final Vector3d position = new Vector3d();
        while (!open.isEmpty()) {
            final Node node = open.pop();
            node.children().forEach(open::push);
            for (int index : node.meshes()) {
                final MeshData mesh = imported.meshes().get(index).data();
                final float[] v = mesh.getVertexData();
                for (int i = 0; i + 2 < v.length; i += MeshData.VERTEX_SIZE_IN_FLOATS) {
                    node.world().transformPosition(position.set(v[i], v[i + 1], v[i + 2]));
                    if (!onGrid(position.x) || !onGrid(position.y) || !onGrid(position.z)) {
                        ++count;
                        if (out.size() < limit) {
                            out.add(new Vector3d(position));
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Whether a coordinate is on the 1/16 m grid.
     *
     * @param value The coordinate, in meters.
     * @return True if it is within the tolerance of a grid line.
     */
    static boolean onGrid(double value) {
        final double steps = value / GRID_STEP;
        return Math.abs(steps - Math.rint(steps)) * GRID_STEP <= GRID_TOLERANCE;
    }

    /** Remove the shapes drawn. */
    public void clear() {
        for (DebugShapeHandle handle : shapes) {
            graphics.debug().remove(handle);
        }
        shapes.clear();
        drawnFor = null;
        drawnIn = null;
    }
}
