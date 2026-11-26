package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.scene.PreviewShapes;
import com.ikalagaming.converter.scene.ScenePlacement;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.PreviewView;
import com.ikalagaming.graphics.scene.LightUnits;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Image;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Row;
import com.ikalagaming.graphics.ui.Sizing;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A panel of preview views, each a shape drawn into its own texture by the renderer, as a test bed
 * for previews until the preview host replaces it. The previews exist while the panel is shown.
 */
@Slf4j
public class PreviewPanel {
    /** The shapes shown, one preview each. */
    private static final List<PreviewShapes.Shape> SHAPES =
            List.of(PreviewShapes.Shape.SPHERE, PreviewShapes.Shape.CUBE);

    /** The size each preview is drawn at, in pixels. */
    private static final int SIZE = 320;

    /** Where the camera looks: the middle of a shape resting on the floor. */
    private static final Vector3d PIVOT = new Vector3d(0, 0.5, 0);

    /** How far the Turn button swings the camera, in the orbit camera's pixels. */
    private static final double TURN = 120;

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** Turns the cameras around their shapes. */
    private final OrbitCamera orbit = new OrbitCamera();

    /** The open previews, in the order of {@link #SHAPES}. Render thread. */
    private final List<PreviewView> previews = new ArrayList<>();

    /** The placed shapes, in the same order. Render thread. */
    private final List<ScenePlacement> placements = new ArrayList<>();

    /**
     * Create the panel's logic.
     *
     * @param graphics The plugin's graphics context.
     */
    public PreviewPanel(@NonNull GraphicsContext graphics) {
        this.graphics = graphics;
    }

    /**
     * The panel's content: the previews side by side, and the Turn button.
     *
     * @return The root node.
     */
    public Node<?> content() {
        final Row row = new Row("previews").gap(8);
        for (int i = 0; i < SHAPES.size(); ++i) {
            final int index = i;
            row.add(
                    new Image("preview" + i)
                            .size(SIZE, SIZE)
                            .texture(
                                    () ->
                                            index < previews.size()
                                                    ? previews.get(index).getTexture()
                                                    : null)
                            .tooltip(SHAPES.get(i).label));
        }
        return new Column("root")
                .gap(8)
                .add(
                        row,
                        new Button("turn", "Turn")
                                .tooltip("Swing the cameras around the shapes")
                                .onClick(this::turn)
                                .width(Sizing.fit()));
    }

    /** Make the previews and place their shapes. Render thread. */
    public void open() {
        if (!previews.isEmpty()) {
            return;
        }
        final Scene world = GraphicsManager.getScene();
        for (PreviewShapes.Shape shape : SHAPES) {
            final PreviewView preview = graphics.previews().create(SIZE, SIZE);
            final Scene scene = preview.getScene();
            scene.getSceneLights()
                    .setDirLight(
                            new DirectionalLight(
                                    new Vector3f(1, 0.97f, 0.9f),
                                    new Vector3f(0.4f, -0.8f, 0.45f),
                                    LightUnits.DAYLIGHT_LUX));
            scene.getSceneLights()
                    .setAmbientLight(
                            new AmbientLight(new Vector3f(1, 1, 1), LightUnits.physical(0.3f)));
            scene.setPhysicalSky(world.getPhysicalSky());
            scene.setExposureValue(world.getExposureValue());
            scene.getCamera().setPosition(0, 1.5f, 2.4f);
            scene.getCamera().setRotation(0.4f, 0);
            final ScenePlacement placement =
                    ScenePlacement.prepare(graphics, PreviewShapes.scene(shape));
            placement.placeIn(preview);
            previews.add(preview);
            placements.add(placement);
        }
    }

    /** Close the previews and remove their shapes. Render thread. */
    public void close() {
        placements.forEach(ScenePlacement::remove);
        placements.clear();
        previews.forEach(PreviewView::close);
        previews.clear();
    }

    /** Swing every camera around its shape. Render thread. */
    private void turn() {
        for (PreviewView preview : previews) {
            orbit.turn(preview.getScene().getCamera(), PIVOT, TURN, 0);
            preview.requestFrames();
        }
    }

    /**
     * Save every preview to a PNG file once it settles, named after its shape. Render thread.
     *
     * @param folder Where to save them.
     * @return Completes once all are saved.
     */
    public CompletableFuture<Void> save(@NonNull Path folder) {
        final List<CompletableFuture<Path>> saves = new ArrayList<>();
        for (int i = 0; i < previews.size(); ++i) {
            final Path file =
                    folder.resolve("preview-" + SHAPES.get(i).name().toLowerCase() + ".png");
            saves.add(previews.get(i).capture(file));
        }
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }
}
