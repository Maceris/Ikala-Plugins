package com.ikalagaming.graphics;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.debug.DebugLabel;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3dc;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws plugins' debug labels, see {@link DebugDraw#label}, over the scene through IkGui's
 * foreground draw list of the scene window's viewport: each point projected as the scene is drawn,
 * without TAA's jitter, the text centered there on a dark backing so it reads over anything. Render
 * thread only.
 */
final class DebugLabelOverlay {

    /** The backing behind each label's text: black, about half see-through. */
    private static final int BACKING = Color.rgba(0, 0, 0, 140);

    /** How far the backing reaches past the text, in pixels. */
    private static final float PADDING = 2;

    /** This frame's labels, scratch. */
    private static final List<DebugLabel> LABELS = new ArrayList<>();

    /** From render space to clip space, scratch. */
    private static final Matrix4f PROJECTION_VIEW = new Matrix4f();

    /** Where a label lands, scratch. */
    private static final Vector2f SCREEN = new Vector2f();

    /** Its text's size, scratch. */
    private static final Vector2f TEXT_SIZE = new Vector2f();

    /** Static drawing only. */
    private DebugLabelOverlay() {}

    /**
     * Draw this frame's labels, taking them whether or not they show, so labels for one frame never
     * pile up. They show while the debug stage is in the pipeline and the scene window has a
     * viewport of IkGui's.
     *
     * @param scene The scene, for its camera and projection, or null for none.
     * @param sceneWindow The window the scene is drawn in, or null for none.
     * @param mainWindow The main window, whose viewport is IkGui's main one.
     */
    static void draw(Scene scene, Window sceneWindow, Window mainWindow) {
        LABELS.clear();
        GraphicsManager.collectDebugLabels(LABELS::add);
        if (LABELS.isEmpty()
                || scene == null
                || sceneWindow == null
                || !GraphicsManager.getPipelineConfig().drawsDebug()) {
            return;
        }
        final Viewport viewport =
                sceneWindow == mainWindow
                        ? IkGui.getMainViewport()
                        : IkGui.findViewportByPlatformHandle(sceneWindow);
        if (viewport == null) {
            return;
        }
        scene.getProjection()
                .getProjectionMatrix()
                .mul(scene.getCamera().getViewMatrix(), PROJECTION_VIEW);
        final Vector3dc camera = scene.getCamera().getPosition();
        final DrawList drawList = IkGui.getForegroundDrawList(viewport);
        final int fontSize = IkGui.getFontSize();
        for (DebugLabel label : LABELS) {
            if (!toScreen(
                    PROJECTION_VIEW,
                    camera,
                    label.position(),
                    viewport.size.x,
                    viewport.size.y,
                    SCREEN)) {
                continue;
            }
            IkGui.calcTextSize(TEXT_SIZE, label.text());
            final float x = viewport.position.x + SCREEN.x - TEXT_SIZE.x / 2;
            final float y = viewport.position.y + SCREEN.y - TEXT_SIZE.y / 2;
            drawList.addRectFilled(
                    x - PADDING,
                    y - PADDING,
                    x + TEXT_SIZE.x + PADDING,
                    y + TEXT_SIZE.y + PADDING,
                    BACKING);
            drawList.addText(fontSize, x, y, label.color(), label.text(), 0);
        }
        LABELS.clear();
    }

    /**
     * Where a point in the world shows on the screen, in pixels from the top left corner.
     *
     * @param projectionView From render space, the world relative to the camera, to clip space.
     * @param camera The camera's position, in the world.
     * @param point The point, in the world.
     * @param width The screen's width, in pixels.
     * @param height Its height.
     * @param out Receives where it shows.
     * @return False if it is behind the camera or off the screen, when out is left undefined.
     */
    static boolean toScreen(
            @NonNull Matrix4fc projectionView,
            @NonNull Vector3dc camera,
            @NonNull Vector3dc point,
            float width,
            float height,
            @NonNull Vector2f out) {
        final Vector4f clip =
                projectionView.transform(
                        new Vector4f(
                                (float) (point.x() - camera.x()),
                                (float) (point.y() - camera.y()),
                                (float) (point.z() - camera.z()),
                                1));
        if (clip.w <= 0) {
            return false;
        }
        final float ndcX = clip.x / clip.w;
        final float ndcY = clip.y / clip.w;
        if (Math.abs(ndcX) > 1 || Math.abs(ndcY) > 1) {
            return false;
        }
        out.set((ndcX + 1) / 2 * width, (1 - ndcY) / 2 * height);
        return true;
    }
}
