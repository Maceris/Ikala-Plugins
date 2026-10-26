package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.ConverterPlugin;
import com.ikalagaming.converter.scene.ImportedScene;
import com.ikalagaming.converter.scene.PreviewShapes;
import com.ikalagaming.converter.scene.SceneImporter;
import com.ikalagaming.converter.scene.ScenePlacement;
import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.TextureWrap;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.texture.DdsFile;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Insets;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Row;
import com.ikalagaming.graphics.ui.Scroll;
import com.ikalagaming.graphics.ui.Selectable;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.TextInput;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.launcher.PluginFolder;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * The asset workbench: browse for raw assets and open them. Images, DDS textures included, show in
 * an {@link ImageViewer}; models and whole scenes are put in the world and shown through a {@link
 * SceneView}, with an outliner of their nodes. Built on retained UI, as separate surfaces around
 * the edges of the screen, so the scene shows in the middle and the mouse reaches it there.
 *
 * <p>Files are read on a background thread, so a large file doesn't stall the editor, and results
 * are handed to the render thread through {@link UI#post}. Everything else is render thread only,
 * except {@link #stop}.
 */
@Slf4j
public class Workbench {
    /** The ID of the top bar surface, with the path and buttons. */
    public static final String SURFACE_ID = "converter/workbench";

    /** The ID of the left surface, with the files and outliner. */
    static final String FILES_ID = "converter/workbench-files";

    /** The ID of the right surface, with the properties. */
    static final String PROPERTIES_ID = "converter/workbench-properties";

    /** The ID of the middle surface, with the image view. */
    static final String IMAGE_ID = "converter/workbench-image";

    /** Every surface, for showing and hiding them together. */
    private static final List<String> SURFACES =
            List.of(SURFACE_ID, FILES_ID, PROPERTIES_ID, IMAGE_ID);

    /** The height of the top bar. */
    private static final float TOP_HEIGHT = 74;

    /** The shader ball model, in the plugin's data folder. */
    private static final String SHADER_BALL = "models/shader_ball.obj";

    /** The width of the left panel. */
    private static final float FILES_WIDTH = 300;

    /** The width of the properties panel. */
    private static final float PROPERTIES_WIDTH = 380;

    /** The gap between panels and the screen's edges. */
    private static final float GAP = 4;

    /** What the middle of the screen shows. */
    enum Mode {
        /** Nothing open. */
        NONE,
        /** An image, over the scene if one is open. */
        IMAGE,
        /** A scene. */
        SCENE
    }

    /** The plugin's graphics context, which owns the textures. */
    private final GraphicsContext graphics;

    /** The UI the workbench is shown through. */
    private final UI ui;

    /** Goes back to the menu. */
    private final Runnable back;

    /** What is being browsed. */
    private final FileBrowser browser;

    /** Shows the open image. */
    private final ImageViewer viewer;

    /** Shows the open scene. */
    private final SceneView sceneView;

    /** Reads files off the render thread, one at a time. */
    private final ExecutorService loader =
            Executors.newSingleThreadExecutor(
                    task -> {
                        Thread thread = new Thread(task, "Workbench loader");
                        thread.setDaemon(true);
                        return thread;
                    });

    /**
     * Counts files asked for, so a slow load that finishes after a newer one was asked for is
     * dropped.
     */
    private final AtomicInteger requests = new AtomicInteger();

    /** Counts scenes, so textures still loading for a closed scene stop. */
    private final AtomicInteger scenes = new AtomicInteger();

    /** The path of the folder or file being looked at. */
    private final TextInput path;

    /** The folder's contents. */
    private final Column files;

    /** Switches between openable files and every file. */
    private final Button filter;

    /** What is happening, or what went wrong. */
    private final Label status;

    /** The open file, if any. */
    private @Nullable Path openFile;

    /** What the middle of the screen shows. */
    private Mode mode = Mode.NONE;

    /** Whether the workbench is showing. */
    private boolean showing;

    /** The environment map lighting the scene, owned here, or null for the plain sky. */
    private TextureHandle skyTexture;

    /**
     * Create the workbench, browsing the working directory.
     *
     * @param graphics The plugin's graphics context.
     * @param ui The UI to show the workbench through.
     * @param back Goes back to the menu, once the workbench has hidden itself.
     */
    public Workbench(@NonNull GraphicsContext graphics, @NonNull UI ui, @NonNull Runnable back) {
        this.graphics = graphics;
        this.ui = ui;
        this.back = back;
        viewer = new ImageViewer(graphics.textures(), this::useAsSky);
        sceneView =
                new SceneView(
                        graphics,
                        new MaterialEditor(
                                this::loadTexture,
                                () -> viewer.getImage() == null ? null : viewer.getImage().path()));
        browser = new FileBrowser(Path.of(""));
        path = new TextInput("path", "").onSubmit(this::openPath).width(Sizing.grow());
        files = new Column("files").align(Align.STRETCH);
        filter = new Button("filter", "").onClick(this::toggleFilter);
        status = new Label("status", "");
        refresh();
    }

    /**
     * Anchors stretching between two fractions of the screen, with offsets in units.
     *
     * @param minX The left anchor.
     * @param minY The top anchor.
     * @param maxX The right anchor.
     * @param maxY The bottom anchor.
     * @param left Moves the left edge.
     * @param top Moves the top edge.
     * @param right Moves the right edge.
     * @param bottom Moves the bottom edge.
     * @return The anchors.
     */
    private static Anchors edges(
            float minX,
            float minY,
            float maxX,
            float maxY,
            float left,
            float top,
            float right,
            float bottom) {
        return new Anchors(
                minX,
                minY,
                maxX,
                maxY,
                Length.u(left),
                Length.u(top),
                Length.u(right),
                Length.u(bottom));
    }

    /** Make the surfaces and show them hidden, so later toggles keep their state. */
    public void install() {
        Row toolbar =
                new Row("toolbar")
                        .gap(8)
                        .add(
                                new Button("back", "Back").onClick(this::goBack),
                                path,
                                new Button("up", "Up").onClick(this::up),
                                new Button("open", "Open").onClick(() -> openPath(path.text())),
                                filter);
        Column top =
                new Column("top")
                        .padding(Insets.all(4))
                        .gap(4)
                        .align(Align.STRETCH)
                        .add(
                                toolbar,
                                new Row("status-bar")
                                        .gap(8)
                                        .add(
                                                status.width(Sizing.grow()),
                                                new Label("preview-label", "Try materials on:"),
                                                previewButton(PreviewShapes.Shape.SPHERE),
                                                previewButton(PreviewShapes.Shape.CUBE),
                                                previewButton(PreviewShapes.Shape.PLANE),
                                                new Button("shader-ball", "Shader ball")
                                                        .onClick(this::openShaderBall),
                                                new Button("particle-stage", "Particles")
                                                        .onClick(this::openParticleStage)));
        ui.show(
                ui.surface(SURFACE_ID)
                        .anchors(edges(0, 0, 1, 0, 0, 0, 0, 0))
                        .height(Sizing.fixed(TOP_HEIGHT))
                        .content(top));

        Row drives = new Row("drives").gap(4);
        List<Path> roots = browser.roots();
        if (roots.size() > 1) {
            for (int i = 0; i < roots.size(); ++i) {
                Path root = roots.get(i);
                drives.add(new Button("root" + i, root.toString()).onClick(() -> navigate(root)));
            }
        }
        Column left =
                new Column("left")
                        .padding(Insets.all(4))
                        .gap(4)
                        .align(Align.STRETCH)
                        .add(
                                drives,
                                new Scroll("list")
                                        .border(true)
                                        .height(Sizing.grow())
                                        .content(files),
                                new Label("outliner-title", "Outliner"),
                                new Immediate("outliner", sceneView::drawOutliner).border(true));
        ui.show(
                ui.surface(FILES_ID)
                        .anchors(edges(0, 0, 0, 1, GAP, TOP_HEIGHT + GAP, 0, -GAP))
                        .width(Sizing.fixed(FILES_WIDTH))
                        .content(left));

        ui.show(
                ui.surface(PROPERTIES_ID)
                        .anchors(edges(1, 0, 1, 1, -GAP, TOP_HEIGHT + GAP, 0, -GAP))
                        .width(Sizing.fixed(PROPERTIES_WIDTH))
                        .content(
                                new Column("right")
                                        .padding(Insets.all(4))
                                        .align(Align.STRETCH)
                                        .add(new Immediate("details", this::drawProperties))));

        ui.show(
                ui.surface(IMAGE_ID)
                        .anchors(
                                edges(
                                        0,
                                        0,
                                        1,
                                        1,
                                        FILES_WIDTH + 2 * GAP,
                                        TOP_HEIGHT + GAP,
                                        -(PROPERTIES_WIDTH + 2 * GAP),
                                        -GAP))
                        .content(
                                new Column("middle")
                                        .padding(Insets.all(4))
                                        .align(Align.STRETCH)
                                        .add(new Immediate("view", viewer::drawView))));
        for (String surface : SURFACES) {
            ui.setVisible(surface, false);
        }
    }

    /**
     * Show or hide the workbench. Hiding it closes any open scene, putting the renderer back.
     *
     * @param visible Whether to show it.
     */
    public void setVisible(boolean visible) {
        showing = visible;
        if (!visible) {
            scenes.incrementAndGet();
            sceneView.close();
            if (mode == Mode.SCENE) {
                mode = Mode.NONE;
            }
        }
        updateSurfaces();
    }

    /** Hide the workbench and go back to the menu. */
    private void goBack() {
        setVisible(false);
        back.run();
    }

    /** Show the surfaces the mode needs, if the workbench is showing. */
    private void updateSurfaces() {
        ui.setVisible(SURFACE_ID, showing);
        ui.setVisible(FILES_ID, showing);
        ui.setVisible(PROPERTIES_ID, showing);
        ui.setVisible(IMAGE_ID, showing && mode != Mode.SCENE);
    }

    /**
     * Draw the properties of whatever the middle shows.
     *
     * @param frame The current frame.
     */
    private void drawProperties(UiFrame frame) {
        if (mode == Mode.SCENE) {
            sceneView.drawProperties(frame, this::closeScene);
            return;
        }
        viewer.drawProperties(frame);
        if (sceneView.getPlacement() != null) {
            IkGui.separator();
            if (IkGui.button("Back to the scene")) {
                frame.fire(this::showScene);
            }
        }
    }

    /** Show the open scene in the middle again, after looking at an image. */
    private void showScene() {
        mode = Mode.SCENE;
        updateSurfaces();
    }

    /** Close the open scene. */
    private void closeScene() {
        scenes.incrementAndGet();
        sceneView.close();
        mode = viewer.getImage() != null ? Mode.IMAGE : Mode.NONE;
        updateSurfaces();
        status.text("Closed the scene");
    }

    /**
     * Browse a folder.
     *
     * @param folder The folder.
     */
    private void navigate(Path folder) {
        browser.navigate(folder);
        refresh();
    }

    /** Browse the parent folder. */
    private void up() {
        browser.up();
        refresh();
    }

    /**
     * Browse a folder or open a file, from a typed path.
     *
     * @param text The path.
     */
    private void openPath(String text) {
        Path typed = browser.parse(text);
        if (typed == null) {
            refresh();
        } else if (Files.isDirectory(typed)) {
            navigate(typed);
        } else {
            open(typed);
        }
    }

    /** Switch between listing openable files and every file. */
    private void toggleFilter() {
        browser.setShowAllFiles(!browser.isShowAllFiles());
        refresh();
    }

    /**
     * Open a file, reading it in the background.
     *
     * @param file The file.
     */
    public void open(@NonNull Path file) {
        final Path absolute = file.toAbsolutePath().normalize();
        final AssetKind kind = AssetKind.of(absolute);
        if (kind == AssetKind.UNKNOWN) {
            status.text("Can't open " + absolute.getFileName() + ", it isn't an image or model");
            return;
        }
        final int request = requests.incrementAndGet();
        openFile = absolute;
        // List the file's folder, for a file opened by a typed path
        final Path folder = absolute.getParent();
        if (folder != null && !folder.equals(browser.getDirectory())) {
            browser.navigate(folder);
        }
        status.text("Loading " + absolute.getFileName() + "...");
        refresh();
        if (kind == AssetKind.MODEL) {
            loader.execute(() -> loadScene(absolute, request));
        } else {
            loader.execute(() -> load(absolute, request));
        }
    }

    /**
     * Post a change to the render thread, unless the plugin unloaded.
     *
     * @param change The change.
     */
    private void post(Runnable change) {
        try {
            ui.post(change);
        } catch (IllegalStateException unloaded) {
            // The plugin unloaded while loading, so there is nothing to tell
        }
    }

    /**
     * A button that shows a preview shape.
     *
     * @param shape The shape.
     * @return The button.
     */
    private Button previewButton(PreviewShapes.Shape shape) {
        return new Button("preview-" + shape.name().toLowerCase(), shape.label)
                .onClick(() -> openPreview(shape));
    }

    /**
     * What a name starts with to open a preview shape instead of a file, as in "preview:sphere".
     */
    static final String PREVIEW_PREFIX = "preview:";

    /** The name after {@link #PREVIEW_PREFIX} that opens the particle stage. */
    static final String PARTICLE_PREVIEW = "particles";

    /**
     * Open what a script names: a file, a preview shape as "preview:sphere", "preview:cube" or
     * "preview:plane", or the particle stage as "preview:particles".
     *
     * @param what The file or preview.
     */
    public void openNamed(@NonNull String what) {
        if (what.equals(PREVIEW_PREFIX + PARTICLE_PREVIEW)) {
            openParticleStage();
            return;
        }
        if (what.startsWith(PREVIEW_PREFIX)) {
            final String name = what.substring(PREVIEW_PREFIX.length()).toUpperCase();
            try {
                openPreview(PreviewShapes.Shape.valueOf(name));
            } catch (IllegalArgumentException e) {
                status.text("No preview shape called " + name.toLowerCase());
            }
            return;
        }
        open(Path.of(what));
    }

    /**
     * Show a preview shape on a floor, to try materials on.
     *
     * @param shape The shape.
     */
    private void openPreview(PreviewShapes.Shape shape) {
        final int request = requests.incrementAndGet();
        status.text("Making a " + shape.label.toLowerCase() + "...");
        loader.execute(
                () -> {
                    final ScenePlacement placement =
                            ScenePlacement.prepare(graphics, PreviewShapes.scene(shape));
                    post(() -> sceneLoaded(request, placement));
                });
    }

    /** Show the stage particles are watched on, with the particle viewer. */
    private void openParticleStage() {
        final int request = requests.incrementAndGet();
        status.text("Making the particle stage...");
        loader.execute(
                () -> {
                    final ScenePlacement placement =
                            ScenePlacement.prepare(graphics, PreviewShapes.particleStage());
                    post(() -> sceneLoaded(request, placement));
                });
    }

    /** Show the shader ball model the plugin ships, to try materials on. */
    private void openShaderBall() {
        open(
                PluginFolder.getResource(
                                ConverterPlugin.PLUGIN_NAME,
                                PluginFolder.ResourceType.DATA,
                                SHADER_BALL)
                        .toPath());
    }

    /**
     * Load a texture in the background for the material editor, handing it over on the render
     * thread. Textures tile, as materials expect.
     *
     * @param file The image.
     * @param colorSpace The color space to read it in.
     * @param loaded Given the texture on the render thread.
     */
    private void loadTexture(Path file, ColorSpace colorSpace, Consumer<TextureHandle> loaded) {
        status.text("Loading " + file.getFileName() + "...");
        loader.execute(
                () -> {
                    try {
                        final String name = file.getFileName().toString().toLowerCase();
                        final TextureHandle texture =
                                name.endsWith(".dds")
                                        ? graphics.textures().load(DdsFile.read(file), colorSpace)
                                        : graphics.textures().load(file.toString(), colorSpace);
                        post(
                                () -> {
                                    loaded.accept(texture);
                                    status.text("Loaded " + file.getFileName());
                                });
                    } catch (IOException | RuntimeException e) {
                        log.warn("Could not load texture {}", file, e);
                        post(() -> status.text("Can't load " + file.getFileName()));
                    }
                });
    }

    /**
     * Read a model and work out how to place it. On the loader thread.
     *
     * @param file The model.
     * @param request Which request this is, to drop it if a newer one was made.
     */
    private void loadScene(Path file, int request) {
        if (request != requests.get()) {
            return;
        }
        try {
            final ImportedScene imported =
                    SceneImporter.read(
                            file,
                            message -> {
                                if (request == requests.get()) {
                                    post(() -> status.text(message));
                                }
                            });
            final ScenePlacement placement = ScenePlacement.prepare(graphics, imported);
            post(() -> sceneLoaded(request, placement));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not open {}", file, e);
            final String message = "Can't open " + file.getFileName() + ": " + e.getMessage();
            post(() -> failed(request, message));
        }
    }

    /**
     * Put a read scene in the world and show it, unless a newer file was asked for, then start
     * loading its textures. Render thread.
     *
     * @param request Which request this was.
     * @param placement The scene, ready to place.
     */
    private void sceneLoaded(int request, ScenePlacement placement) {
        if (request != requests.get() || !showing) {
            return;
        }
        final int scene = scenes.incrementAndGet();
        placement.place();
        sceneView.show(placement);
        mode = Mode.SCENE;
        updateSurfaces();
        final ImportedScene imported = placement.getImported();
        status.text(
                String.format(
                        "Opened %s in %,d ms: %,d triangles",
                        imported.file().getFileName(),
                        imported.readMillis(),
                        imported.drawnTriangles()));
        loader.execute(
                () ->
                        placement.loadTextures(
                                this::post,
                                () -> scene != scenes.get(),
                                progress ->
                                        post(
                                                () -> {
                                                    if (scene == scenes.get()) {
                                                        sceneView.setTextureProgress(progress);
                                                    }
                                                })));
    }

    /**
     * Decode an image and start loading its texture. On the loader thread.
     *
     * @param file The image.
     * @param request Which request this is, to drop it if a newer one was made.
     */
    private void load(Path file, int request) {
        if (request != requests.get()) {
            return;
        }
        final long start = System.nanoTime();
        try {
            final ImageFile image = ImageFile.read(file);
            final ColorSpace colorSpace = image.defaultColorSpace();
            final TextureHandle texture =
                    image.dds() != null
                            // Shown whole, so its edges mustn't blend with the opposite ones
                            ? graphics.textures().load(image.dds(), colorSpace, TextureWrap.CLAMP)
                            : graphics.textures()
                                    .load(
                                            image.pixels(),
                                            colorSpace.apply(image.format()),
                                            image.width(),
                                            image.height());
            final long millis = (System.nanoTime() - start) / 1_000_000;
            post(() -> loaded(request, image, texture, colorSpace, millis));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not open {}", file, e);
            final String message = "Can't open " + file.getFileName() + ": " + e.getMessage();
            post(() -> failed(request, message));
        }
    }

    /**
     * Show a loaded image, unless a newer one was asked for. Render thread.
     *
     * @param request Which request this was.
     * @param image The image.
     * @param texture Its texture.
     * @param colorSpace The color space it was loaded in.
     * @param millis How long decoding took.
     */
    private void loaded(
            int request,
            ImageFile image,
            TextureHandle texture,
            ColorSpace colorSpace,
            long millis) {
        if (request != requests.get()) {
            graphics.textures().release(texture);
            return;
        }
        releaseTexture();
        viewer.show(image, texture, colorSpace);
        mode = Mode.IMAGE;
        updateSurfaces();
        status.text(String.format("Opened %s in %d ms", image.path().getFileName(), millis));
    }

    /**
     * Report a failed load, unless a newer one was asked for. Render thread.
     *
     * @param request Which request this was.
     * @param message What went wrong.
     */
    private void failed(int request, String message) {
        if (request == requests.get()) {
            status.text(message);
        }
    }

    /**
     * Light the scene by an environment map: load its pixels again as the sky's own texture, which
     * outlives the image shown, and show the scene with it. Render thread.
     *
     * @param image The equirectangular image, decoded to half floats.
     */
    private void useAsSky(ImageFile image) {
        final ByteBuffer pixels = image.pixels();
        if (pixels == null) {
            return;
        }
        final TextureHandle texture =
                graphics.textures()
                        .load(
                                pixels.duplicate().rewind(),
                                image.format(),
                                image.width(),
                                image.height(),
                                TextureWrap.REPEAT);
        final TextureHandle old = skyTexture;
        skyTexture = texture;
        sceneView.setSky(texture, image.path().getFileName().toString(), this::clearSky);
        if (old != null) {
            graphics.textures().release(old);
        }
        status.text("Lighting by " + image.path().getFileName());
        if (sceneView.getPlacement() != null) {
            showScene();
        }
    }

    /** Go back to the plain sky, releasing the environment map's texture. Render thread. */
    private void clearSky() {
        final TextureHandle old = skyTexture;
        skyTexture = null;
        sceneView.setSky(null, "", this::clearSky);
        if (old != null) {
            graphics.textures().release(old);
        }
    }

    /** Release the shown image's texture, if there is one. */
    private void releaseTexture() {
        TextureHandle old = viewer.getTexture();
        viewer.clear();
        if (old != null) {
            graphics.textures().release(old);
        }
    }

    /**
     * Stop loading and close the scene. Textures, meshes and the like go with the plugin's graphics
     * context; the renderer is put back through the UI manager's queue, which outlives the plugin.
     * Safe from any thread.
     */
    public void stop() {
        requests.incrementAndGet();
        scenes.incrementAndGet();
        loader.shutdownNow();
        GraphicsManager.getUiManager()
                .post(
                        () -> {
                            try {
                                sceneView.close();
                            } catch (IllegalStateException unloaded) {
                                // What the plugin owned went with it; the renderer is put back
                                log.debug("Closing the scene after unloading", unloaded);
                            }
                        });
    }

    /** Update the retained nodes from the browser, after anything changes. */
    private void refresh() {
        path.text((openFile != null ? openFile : browser.getDirectory()).toString());
        filter.text(browser.isShowAllFiles() ? "Show openable only" : "Show all files");

        files.clear();
        if (browser.getDirectory().getParent() != null) {
            files.add(new Selectable("parent", "../").onClick(this::up));
        }
        int index = 0;
        for (FileBrowser.Entry entry : browser.getEntries()) {
            String id = "entry" + index++;
            if (entry.directory()) {
                files.add(
                        new Selectable(id, entry.name() + "/")
                                .onClick(() -> navigate(entry.path())));
            } else {
                files.add(
                        new Selectable(id, entry.name())
                                .selected(entry.path().equals(openFile))
                                .onClick(() -> open(entry.path())));
            }
        }
        if (browser.getError() != null) {
            status.text(browser.getError());
        }
    }
}
