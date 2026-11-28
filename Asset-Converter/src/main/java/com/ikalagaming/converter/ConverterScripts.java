package com.ikalagaming.converter;

import com.ikalagaming.converter.gui.window.MainMenu;
import com.ikalagaming.converter.scene.ImportedScene;
import com.ikalagaming.converter.scene.MeshExport;
import com.ikalagaming.converter.scene.SceneImporter;
import com.ikalagaming.converter.workbench.AssetBrowser;
import com.ikalagaming.converter.workbench.Comparison;
import com.ikalagaming.converter.workbench.ContainerViewer;
import com.ikalagaming.converter.workbench.GraphTool;
import com.ikalagaming.converter.workbench.PreviewHost;
import com.ikalagaming.converter.workbench.ReferenceViewer;
import com.ikalagaming.converter.workbench.SceneView;
import com.ikalagaming.converter.workbench.Swatch;
import com.ikalagaming.converter.workbench.TextureViewer;
import com.ikalagaming.converter.workbench.Viewer;
import com.ikalagaming.converter.workbench.Workbench;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.PreviewView;
import com.ikalagaming.graphics.asset.MaterialSection;
import com.ikalagaming.graphics.asset.MeshSection;
import com.ikalagaming.graphics.scene.Camera;
import com.ikalagaming.graphics.ui.graph.GraphEditor;
import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.vulkan.FrameTimings;
import com.ikalagaming.plugins.PluginInfo;
import com.ikalagaming.plugins.PluginManager;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * What the converter's scripts can call, through the global {@code converter}, like {@code
 * converter.openWorkbench(file)}. Static methods only, so the global is the class and scripts hold
 * no plugin objects.
 *
 * <p>Scripts run on their own thread, so what changes the scene or the UI is posted to the render
 * thread, and done a frame or so later; scripts wait on what they need to see with {@code
 * ui.test()} steps.
 */
@Slf4j
public final class ConverterScripts {
    /** The name scripts use. */
    public static final String GLOBAL = "converter";

    /**
     * The converter's version.
     *
     * @return The version from its plugin.yml, or an empty string if it isn't loaded.
     */
    public static String version() {
        return PluginManager.getInstance()
                .getInfo(ConverterPlugin.PLUGIN_NAME)
                .map(PluginInfo::getVersion)
                .orElse("");
    }

    /**
     * Show or hide the main menu.
     *
     * @param visible Whether to show it.
     */
    public static void showMenu(boolean visible) {
        GraphicsManager.forPlugin(ConverterPlugin.PLUGIN_NAME)
                .ui()
                .setVisible(MainMenu.SURFACE_ID, visible);
    }

    /**
     * Write a line to the log, for checks to report what they found.
     *
     * @param message The line, in plain ASCII.
     */
    public static void log(String message) {
        log.info("Script: {}", message);
    }

    /**
     * Export a model file's meshes as one mesh in an asset container, as the workbench's export
     * section does for the whole scene, without opening it. For re-exporting a game's models in one
     * go.
     *
     * @param source The model file, such as an OBJ or glTF file.
     * @param target The container to write.
     * @param bottomCenter Whether the file's origin is the middle of the model's bottom, as in
     *     Blockbench and Minecraft models, to be moved to the corner of the block's cell.
     * @return What was written, or why it couldn't be, which is also logged.
     */
    public static String exportMesh(String source, String target, boolean bottomCenter) {
        try {
            final ImportedScene scene = SceneImporter.read(Path.of(source), message -> {});
            final Path file = Path.of(target);
            final String done =
                    MeshExport.describe(
                            MeshExport.export(scene, scene.root(), bottomCenter, file), file);
            log.info("{} from {}", done, source);
            return done;
        } catch (IOException | RuntimeException e) {
            log.warn("Couldn't export {} to {}", source, target, e);
            return "Couldn't export: " + e.getMessage();
        }
    }

    /** Bytes in a kibibyte. */
    private static final double BYTES_PER_KIB = 1024.0;

    /** The render thread's allocated bytes at the last {@link #logAllocation}, -1 before any. */
    private static long lastAllocated = -1;

    /** The frame number at the last {@link #logAllocation}. */
    private static long lastFrame;

    /**
     * Write how much the render thread allocated per frame since the last call to the log, for
     * checks that look for per-frame garbage. The first call only starts counting.
     *
     * @param label What the line is about, in plain ASCII.
     */
    public static void logAllocation(String label) {
        final var renderer = GraphicsManager.getRenderInstance();
        if (renderer == null
                || !(ManagementFactory.getThreadMXBean()
                        instanceof com.sun.management.ThreadMXBean threads)) {
            log.info("Allocation {}: none", label);
            return;
        }
        // The render thread is the one that started the app
        long renderThread = -1;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if ("main".equals(thread.getName())) {
                renderThread = thread.threadId();
            }
        }
        final long allocated = threads.getThreadAllocatedBytes(renderThread);
        final long frame = renderer.getState().frameNumber;
        if (lastAllocated >= 0 && frame > lastFrame) {
            log.info(
                    "Allocation {}: {} KiB per frame over {} frames",
                    label,
                    String.format(
                            "%.2f",
                            (allocated - lastAllocated) / BYTES_PER_KIB / (frame - lastFrame)),
                    frame - lastFrame);
        } else {
            log.info("Allocation {}: counting from frame {}", label, frame);
        }
        lastAllocated = allocated;
        lastFrame = frame;
    }

    /**
     * Write how long each part of a frame takes to the log, averaged over the last couple of
     * seconds, for checks that measure.
     *
     * @param label What the line is about, in plain ASCII.
     */
    public static void logTimings(String label) {
        final var renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().frameTimings == null) {
            log.info("Timings {}: none", label);
            return;
        }
        final StringBuilder line = new StringBuilder();
        for (FrameTimings.Timing timing : renderer.getState().frameTimings.snapshot()) {
            line.append(
                    String.format(
                            "%n    %-28s %8.3f ms avg %8.3f ms p95 %8.3f ms max",
                            timing.name(), timing.average(), timing.p95(), timing.max()));
        }
        log.info("Timings {}:{}", label, line);
    }

    /**
     * Open the workbench on a file, or on a preview shape named as "preview:sphere", "preview:cube"
     * or "preview:plane", hiding the main menu.
     *
     * @param what The file or preview.
     */
    public static void openWorkbench(String what) {
        onWorkbench(
                workbench -> {
                    showMenu(false);
                    workbench.setVisible(true);
                    workbench.openNamed(what);
                });
    }

    /**
     * Open the workbench on a file in a folder, as {@link #openWorkbench(String)} does.
     *
     * @param folder The folder.
     * @param name The file's path in it.
     */
    public static void openWorkbench(String folder, String name) {
        openWorkbench(Path.of(folder, name).toString());
    }

    /**
     * Save the workbench's preview views to PNG files once they settle, logging "Script: Saved the
     * previews" when done, for checks of preview rendering.
     *
     * @param folder Where to save them.
     * @param label The folder inside it to save them in, made if missing, like "before".
     */
    public static void savePreviews(String folder, String label) {
        onWorkbench(
                workbench -> {
                    final Path target = Path.of(folder, label);
                    try {
                        Files.createDirectories(target);
                    } catch (IOException e) {
                        log.warn("Script: can't make {}: {}", target, e.getMessage());
                        return;
                    }
                    workbench
                            .getPreviewPanel()
                            .save(target)
                            .whenComplete(
                                    (done, error) -> {
                                        if (error == null) {
                                            log.info("Script: Saved the previews to {}", target);
                                        } else {
                                            log.warn(
                                                    "Script: saving the previews failed: {}",
                                                    error.getMessage());
                                        }
                                    });
                });
    }

    /**
     * Log what the workbench's viewer shows, as "Script: Viewer ..." with its kind and file; for a
     * container, also whether it is readable and how many sections it has.
     */
    public static void logViewer() {
        onWorkbench(
                workbench -> {
                    final Viewer viewer = workbench.getHost().getViewer();
                    if (viewer == null) {
                        log.info("Script: Viewer shows nothing");
                        return;
                    }
                    final Path file = viewer.file();
                    final String name = file == null ? "" : " " + file.getFileName();
                    if (viewer instanceof ContainerViewer container) {
                        log.info(
                                "Script: Viewer {}{}: {}, {} sections",
                                viewer.kind(),
                                name,
                                container.getContainer().status(),
                                container.getContainer().inspection().entries().size());
                    } else {
                        log.info("Script: Viewer {}{}", viewer.kind(), name);
                    }
                });
    }

    /**
     * Measure the average linear albedo of the texture the workbench's viewer shows, logging
     * "Script: Measured ..." with red, green, blue and luminance.
     */
    public static void measureViewer() {
        onWorkbench(
                workbench -> {
                    final TextureViewer viewer = textureViewer(workbench);
                    if (viewer == null) {
                        log.warn("Script: no texture is shown to measure");
                        return;
                    }
                    viewer.measure();
                    final float[] measured = viewer.getMeasured();
                    if (measured == null) {
                        log.warn("Script: the texture can't be measured");
                        return;
                    }
                    log.info(
                            String.format(
                                    "Script: Measured %s: R %.4f G %.4f B %.4f luminance %.4f",
                                    viewer.getImage().path().getFileName(),
                                    measured[0],
                                    measured[1],
                                    measured[2],
                                    measured[3]));
                });
    }

    /**
     * Show the texture in the workbench's viewer 3x3 times, or once.
     *
     * @param tiled Whether to tile it.
     */
    public static void tileViewer(boolean tiled) {
        onWorkbench(
                workbench -> {
                    final TextureViewer viewer = textureViewer(workbench);
                    if (viewer != null) {
                        viewer.setTiled(tiled);
                    }
                });
    }

    /**
     * Save what the workbench's viewer shows to a PNG file, logging "Script: Captured ..." when
     * done.
     *
     * @param folder The folder to save it in, made if missing.
     * @param name The file's name, like "texture.png".
     */
    public static void captureViewer(String folder, String name) {
        onWorkbench(workbench -> capture(workbench.getHost(), folder, name));
    }

    /**
     * Light the workbench's 3D viewer by a lighting preset, as its toolbar list does, and log
     * "Script: Lit by" with the preset, or that there is none by that name.
     *
     * @param name The preset's name, like "Noon" or "Torch cave".
     */
    public static void lightScene(String name) {
        onWorkbench(
                workbench -> {
                    if (workbench.getSceneView().usePreset(name)) {
                        log.info("Script: Lit by {}", name);
                    } else {
                        log.warn("Script: no scene, or no lighting preset called {}", name);
                    }
                });
    }

    /**
     * Show the lit scene in the workbench's 3D viewer, or what its pixels are lit with, as its
     * toolbar list does, and log "Script: Showing" with the mode, or that there is none by that
     * name.
     *
     * @param mode The mode, like "Lit", "Base color", "Roughness" or "Far flat color".
     */
    public static void showScene(String mode) {
        onWorkbench(
                workbench -> {
                    if (workbench.getSceneView().useViewMode(mode)) {
                        log.info("Script: Showing {}", mode);
                    } else {
                        log.warn("Script: no scene, or no view mode called {}", mode);
                    }
                });
    }

    /**
     * Supersample the workbench's 3D viewer, with temporal anti-aliasing off, or go back to it.
     *
     * @param on Whether to supersample.
     */
    public static void supersampleScene(boolean on) {
        onWorkbench(
                workbench -> {
                    workbench.getSceneView().setSupersampled(on);
                    log.info("Script: Supersampling {}", on ? "on" : "off");
                });
    }

    /**
     * Draw one of the workbench's 3D viewer overlays, or stop: "grid", "bounds", "offgrid",
     * "normals" or "wireframe". Logs "Script: Overlay" with the overlay, and for "offgrid" the
     * count of vertices off the grid a frame later.
     *
     * @param name The overlay.
     * @param on Whether to draw it.
     */
    public static void overlayScene(String name, boolean on) {
        onWorkbench(
                workbench -> {
                    final SceneView view = workbench.getSceneView();
                    final PreviewView preview = view.getPreview();
                    switch (name) {
                        case "grid" -> view.getOverlays().setGrid(on);
                        case "bounds" -> view.getOverlays().setBounds(on);
                        case "offgrid" -> view.getOverlays().setOffGrid(on);
                        case "normals", "wireframe" -> {
                            if (preview == null) {
                                log.warn("Script: no scene for the {} overlay", name);
                                return;
                            }
                            if ("normals".equals(name)) {
                                preview.getScene().getDebugVisualizers().setNormals(on);
                            } else {
                                preview.getScene().getDebugVisualizers().setWireframe(on);
                            }
                        }
                        default -> {
                            log.warn("Script: no overlay called {}", name);
                            return;
                        }
                    }
                    log.info("Script: Overlay {} {}", name, on ? "on" : "off");
                    if ("offgrid".equals(name) && on) {
                        // Checked now rather than on the next frame the view is drawn
                        view.getOverlays().update(view.getPlacement(), preview);
                        log.info(
                                "Script: {} vertices off the grid",
                                view.getOverlays().getOffGridCount());
                    }
                });
    }

    /**
     * Pin the workbench's 3D view as B, for A/B comparison, once it settles, and show A and B a
     * given way: "side by side" or "split".
     *
     * @param mode How to show A and B.
     */
    public static void pinScene(String mode) {
        onWorkbench(
                workbench -> {
                    final SceneView view = workbench.getSceneView();
                    if (view.getPreview() == null) {
                        log.warn("Script: no scene to pin");
                        return;
                    }
                    view.getComparison().pin(view.getPreview());
                    view.getComparison()
                            .setMode(
                                    "split".equalsIgnoreCase(mode)
                                            ? Comparison.Mode.SPLIT
                                            : Comparison.Mode.SIDE_BY_SIDE);
                    log.info("Script: Pinning as B, {}", mode);
                });
    }

    /**
     * Show the open container's mesh in the 3D viewer, or its material on a sphere, as the
     * container viewer's buttons do, and log "Script: Showing the container's" with which, or that
     * it can't.
     *
     * @param what "mesh" or "material".
     */
    public static void containerInThreeD(String what) {
        onWorkbench(
                workbench -> {
                    final ContainerViewer viewer =
                            workbench.getHost().viewer(ContainerViewer.class);
                    final int tag = "material".equals(what) ? MaterialSection.TAG : MeshSection.TAG;
                    if (viewer != null && viewer.showInThreeD(tag)) {
                        log.info("Script: Showing the container's {}", what);
                    } else {
                        log.warn("Script: no container with a {} is open", what);
                    }
                });
    }

    /**
     * Show a scene from the 3D view's list, as picking it does: a shape like "Sphere", "Shader
     * ball", "Particle stage", or a context scene like "Wall". Logs "Script: Picked" with the
     * scene, or that there is none by that name.
     *
     * @param name The scene's name.
     */
    public static void pickScene(String name) {
        onWorkbench(
                workbench -> {
                    showMenu(false);
                    workbench.setVisible(true);
                    if (workbench.pickScene(name)) {
                        log.info("Script: Picked {}", name);
                    } else {
                        log.warn("Script: no scene called {}", name);
                    }
                });
    }

    /**
     * Change the workbench's graph as the editor does, for checks, and log "Script: Graph" with
     * what happened: "new"; "add type x y"; "link fromNode.pin toNode.pin"; "select id id...";
     * "delete"; "duplicate"; "undo"; "redo"; "set node param value"; "save path"; "log"; "reroute
     * fromNode.pin toNode.pin x y"; "frame title"; "collapse id..."; "expand id..."; "hide id...";
     * "show id..."; "comment id text"; "align left|right|top|middle"; "straighten id"; "upstream
     * id"; "downstream id"; "unused"; "find text"; "focus id"; "minimap on|off"; "describe id",
     * which logs a node's or frame's state and the selection; "setting
     * width|height|seed|data|texture|model|maps value", a texture graph setting; "bake", which
     * bakes the saved graph and logs what it wrote; "display id|none", which shows a node in the
     * Viewer; "measure id"; "pin node pin in|out", which logs its tooltip; "seeds on|off|other|pick
     * n"; "map name", which map a shown Output shows; "shape on|off", the Output on the 3D shape;
     * "previews", which logs the swatches, the shown node and each node's cost; "swatches on|off";
     * "starter name", opening a starter graph as a new one; "view x y zoom", centering the canvas;
     * "where", logging the view's middle; "param add name kind", "param set name value" and "param
     * shared file...", which change the graph's parameters and log them; "subgraph make file
     * id...", making a subgraph file (in the graph's folder) of nodes; "subgraph open id", opening
     * the subgraph a node places; "subgraph back"; "subgraph check", reading changed subgraph files
     * now; "instance new file [texture]", making an instance of the open graph beside it; "instance
     * parent", opening the open instance's parent; "instances", logging each instance's swatch near
     * the open graph, measured.
     *
     * @param command The command and its words, separated by spaces.
     */
    public static void graph(String command) {
        onWorkbench(
                workbench -> {
                    showMenu(false);
                    workbench.setVisible(true);
                    final GraphTool tool = workbench.getGraphTool();
                    final GraphEditor editor = tool.getEditor();
                    final String[] words = command.trim().split("\\s+");
                    switch (words[0]) {
                        case "new" -> tool.newGraph();
                        case "add" ->
                                log.info(
                                        "Script: Graph added {}",
                                        editor.addNode(
                                                words[1],
                                                Float.parseFloat(words[2]),
                                                Float.parseFloat(words[3])));
                        case "link" -> {
                            final String[] from = words[1].split("\\.");
                            final String[] to = words[2].split("\\.");
                            log.info(
                                    "Script: Graph link {}",
                                    editor.link(from[0], from[1], to[0], to[1]));
                        }
                        case "select" -> editor.select(List.of(words).subList(1, words.length));
                        case "delete" -> editor.deleteSelection();
                        case "duplicate" ->
                                log.info(
                                        "Script: Graph duplicated {}", editor.duplicateSelection());
                        case "undo" -> editor.undo();
                        case "redo" -> editor.redo();
                        case "set" -> editor.setParam(words[1], words[2], words[3]);
                        case "save" -> tool.save(Path.of(words[1]));
                        case "reroute" -> {
                            final String[] from = words[1].split("\\.");
                            final String[] to = words[2].split("\\.");
                            final Graph.Link link =
                                    tool.getHistory().getGraph().linkInto(to[0], to[1]);
                            if (link == null
                                    || !link.fromNode().equals(from[0])
                                    || !link.fromPin().equals(from[1])) {
                                log.warn("Script: no graph link {} > {}", words[1], words[2]);
                            } else {
                                log.info(
                                        "Script: Graph added {}",
                                        editor.insertReroute(
                                                link,
                                                Float.parseFloat(words[3]),
                                                Float.parseFloat(words[4])));
                            }
                        }
                        case "frame" ->
                                log.info(
                                        "Script: Graph added {}",
                                        editor.frameSelection(rest(words, 1), 0, 0));
                        case "collapse", "expand" ->
                                editor.setCollapsed(
                                        List.of(words).subList(1, words.length),
                                        "collapse".equals(words[0]));
                        case "hide", "show" ->
                                editor.setHideUnused(
                                        List.of(words).subList(1, words.length),
                                        "hide".equals(words[0]));
                        case "comment" -> editor.setComment(words[1], rest(words, 2));
                        case "align" ->
                                editor.align(
                                        GraphEditor.Align.valueOf(
                                                words[1].toUpperCase(java.util.Locale.ROOT)));
                        case "straighten" ->
                                log.info(
                                        "Script: Graph straightened {}",
                                        editor.straighten(words[1]));
                        case "upstream" -> {
                            editor.selectUpstream(words[1]);
                            log.info("Script: Graph selected {}", editor.getSelection());
                        }
                        case "downstream" -> {
                            editor.selectDownstream(words[1]);
                            log.info("Script: Graph selected {}", editor.getSelection());
                        }
                        case "unused" ->
                                log.info("Script: Graph removed {} unused", editor.removeUnused());
                        case "find" ->
                                log.info("Script: Graph found {}", editor.find(rest(words, 1)));
                        case "focus" -> editor.focus(words[1]);
                        case "minimap" -> editor.setMinimap("on".equals(words[1]));
                        case "setting" -> {
                            final var texture = tool.getTexture();
                            final String value = rest(words, 2);
                            switch (words[1]) {
                                case "width" -> texture.setWidth(Integer.parseInt(value));
                                case "height" -> texture.setHeight(Integer.parseInt(value));
                                case "seed" -> texture.setSeed(Long.parseLong(value));
                                case "data" -> texture.setData(value);
                                case "texture" -> texture.setTexture(value);
                                case "model" -> texture.setModel(value);
                                case "maps" -> texture.setMaps(value);
                                default -> log.warn("Script: no graph setting {}", words[1]);
                            }
                            tool.getHistory().markDirty();
                        }
                        case "bake" -> {
                            final var result = tool.bake();
                            log.info(
                                    "Script: Graph baked {}, {} written, {} unchanged, problems {}",
                                    result.ok(),
                                    result.written().stream()
                                            .map(file -> file.getFileName().toString())
                                            .toList(),
                                    result.unchanged().size(),
                                    result.problems());
                        }
                        case "display" -> {
                            tool.getPreviews().show("none".equals(words[1]) ? null : words[1]);
                            log.info("Script: Graph shows {}", tool.getPreviews().getShown());
                        }
                        case "measure" ->
                                log.info(
                                        "Script: Graph measured {}",
                                        tool.getPreviews().measure(words[1]));
                        case "pin" ->
                                log.info(
                                        "Script: Graph pin {}",
                                        tool.getPreviews()
                                                .describePin(
                                                        words[1], words[2], "in".equals(words[3])));
                        case "seeds" -> {
                            final var previews = tool.getPreviews();
                            switch (words[1]) {
                                case "on" -> previews.setSeedGrid(true);
                                case "off" -> previews.setSeedGrid(false);
                                case "other" -> previews.otherSeeds();
                                case "pick" ->
                                        previews.pickSeed(
                                                previews.seedOf(Integer.parseInt(words[2])));
                                default -> log.warn("Script: no seeds command {}", words[1]);
                            }
                            log.info("Script: Graph seed {}", tool.getTexture().getSeed());
                        }
                        case "map" -> tool.getPreviews().showMap(words[1]);
                        case "shape" -> tool.getPreviews().setOnShape("on".equals(words[1]));
                        case "param" -> {
                            final var texture = tool.getTexture();
                            switch (words[1]) {
                                case "add" ->
                                        tool.getParameterPanel()
                                                .add(
                                                        words[2],
                                                        com.ikalagaming.converter.texgraph
                                                                .GraphParameter.Kind.valueOf(
                                                                words[3].toUpperCase(
                                                                        java.util.Locale.ROOT)));
                                case "set" -> {
                                    final var parameter = texture.parameter(words[2]);
                                    if (parameter == null) {
                                        log.warn("Script: no graph parameter {}", words[2]);
                                    } else {
                                        texture.putParameter(parameter.withValue(rest(words, 3)));
                                        tool.getHistory().markDirty();
                                    }
                                }
                                case "shared" -> {
                                    texture.setShared(List.of(words).subList(2, words.length));
                                    tool.getHistory().markDirty();
                                }
                                default -> log.warn("Script: no param command {}", words[1]);
                            }
                            log.info(
                                    "Script: Graph parameters {}",
                                    texture.getParameters().stream()
                                            .map(p -> p.name() + "=" + p.value())
                                            .toList());
                        }
                        case "view" ->
                                editor.setView(
                                        Float.parseFloat(words[1]),
                                        Float.parseFloat(words[2]),
                                        Float.parseFloat(words[3]));
                        case "where" ->
                                log.info(
                                        "Script: Graph view at {}, zoom {}, canvas {} x {}",
                                        editor.getViewCenter(),
                                        editor.getCanvas().getZoom(),
                                        editor.getCanvas().getRect().getWidth(),
                                        editor.getCanvas().getRect().getHeight());
                        case "starter" ->
                                tool.starters().stream()
                                        .filter(
                                                f ->
                                                        GraphTool.starterName(f)
                                                                .equals(rest(words, 1)))
                                        .findFirst()
                                        .ifPresentOrElse(
                                                f ->
                                                        log.info(
                                                                "Script: Graph from starter {}",
                                                                tool.openStarter(f)),
                                                () ->
                                                        log.warn(
                                                                "Script: no starter {} among {}",
                                                                rest(words, 1),
                                                                tool.starters().size()));
                        case "swatches" -> tool.setSwatches("on".equals(words[1]));
                        case "instance" -> {
                            switch (words[1]) {
                                case "new" -> {
                                    final Path graphFile = tool.getFile();
                                    log.info(
                                            "Script: Graph instance made {}",
                                            graphFile != null
                                                    && tool.newInstance(
                                                            graphFile.resolveSibling(words[2]),
                                                            words.length > 3 ? words[3] : null));
                                }
                                case "parent" -> {
                                    tool.openParent();
                                    log.info("Script: Graph opened the parent {}", tool.getFile());
                                }
                                case "show" -> {
                                    final Path graphFile = tool.getFile();
                                    try {
                                        log.info(
                                                "Script: Graph instance file {}: {}",
                                                words[2],
                                                Files.readString(graphFile.resolveSibling(words[2]))
                                                        .strip()
                                                        .replace('\n', '|'));
                                    } catch (IOException | RuntimeException e) {
                                        log.warn(
                                                "Script: can't read {}: {}",
                                                words[2],
                                                e.getMessage());
                                    }
                                }
                                default -> log.warn("Script: no instance command {}", words[1]);
                            }
                        }
                        case "instances" -> {
                            tool.refreshInstances();
                            final List<String> lines = new java.util.ArrayList<>();
                            tool.getInstanceSwatches()
                                    .forEach(
                                            (file, plane) -> {
                                                final float[] average =
                                                        com.ikalagaming.converter.texgraph
                                                                .PlaneViews.average(plane);
                                                lines.add(
                                                        String.format(
                                                                java.util.Locale.ROOT,
                                                                "%s r %.3f g %.3f b %.3f",
                                                                file.getFileName(),
                                                                average[0],
                                                                average[1],
                                                                average[2]));
                                            });
                            java.util.Collections.sort(lines);
                            log.info("Script: Graph instances {}", lines);
                        }
                        case "subgraph" -> {
                            switch (words[1]) {
                                case "make" -> {
                                    final Path graphFile = tool.getFile();
                                    final String made =
                                            graphFile == null
                                                    ? null
                                                    : tool.makeSubgraph(
                                                            new java.util.LinkedHashSet<>(
                                                                    List.of(words)
                                                                            .subList(
                                                                                    3,
                                                                                    words.length)),
                                                            graphFile.resolveSibling(words[2]));
                                    log.info("Script: Graph subgraph made {}", made);
                                }
                                case "open" ->
                                        log.info(
                                                "Script: Graph subgraph opened {}",
                                                tool.openSubgraphOf(words[2]));
                                case "back" -> {
                                    tool.back();
                                    log.info("Script: Graph back to {}", tool.getFile());
                                }
                                case "check" -> tool.refreshSubgraphs();
                                default -> log.warn("Script: no subgraph command {}", words[1]);
                            }
                        }
                        case "previews" ->
                                log.info(
                                        "Script: Graph previews {}", tool.getPreviews().describe());
                        case "describe" -> {
                            final var graph = tool.getHistory().getGraph();
                            final Graph.Node node = graph.node(words[1]);
                            final Graph.Frame frame = graph.frame(words[1]);
                            if (node != null) {
                                log.info(
                                        "Script: Graph {} at {}, {} collapsed {}, hide unused {},"
                                                + " comment '{}', error '{}', selected {}",
                                        words[1],
                                        Math.round(node.getX()),
                                        Math.round(node.getY()),
                                        node.isCollapsed(),
                                        node.isHideUnused(),
                                        node.getComment(),
                                        editor.errorOf(words[1]),
                                        editor.getSelection());
                            } else if (frame != null) {
                                log.info(
                                        "Script: Graph {} '{}' at {}, {} holds {}",
                                        words[1],
                                        frame.getText(),
                                        Math.round(frame.getX()),
                                        Math.round(frame.getY()),
                                        graph.nodesIn(words[1]));
                            } else {
                                log.info(
                                        "Script: Graph has no {}, selected {}",
                                        words[1],
                                        editor.getSelection());
                            }
                        }
                        case "log" -> {
                            final var graph = tool.getHistory().getGraph();
                            log.info(
                                    "Script: Graph has {} nodes, {} links, {} errors",
                                    graph.nodes().size(),
                                    graph.links().size(),
                                    graph.nodes().stream()
                                            .filter(node -> editor.errorOf(node.getId()) != null)
                                            .count());
                        }
                        default -> log.warn("Script: no graph command {}", words[0]);
                    }
                });
    }

    /**
     * The words from one on, joined by spaces.
     *
     * @param words The words.
     * @param from The first to keep.
     * @return The text, empty if there are none.
     */
    private static String rest(String[] words, int from) {
        return from >= words.length
                ? ""
                : String.join(" ", List.of(words).subList(from, words.length));
    }

    /**
     * Drive the workbench's asset index, logging "Script: Index ..." with what it did: "roots",
     * listing them; "add folder" and "remove folder", changing them; "scan", reading them now and
     * waiting; "uses file" and "usedby file", listing a file's references; "problems"; "go n",
     * showing the n-th root, from 0, in the file list.
     *
     * @param command The command and its words, separated by spaces; a path is the rest.
     */
    public static void index(String command) {
        onWorkbench(
                workbench -> {
                    final var assets = workbench.getAssets();
                    assets.start();
                    final String[] words = command.trim().split("\\s+");
                    final String rest = rest(words, 1);
                    switch (words[0]) {
                        case "roots" -> log.info("Script: Index roots {}", assets.getRoots());
                        case "add" ->
                                log.info("Script: Index added {}", assets.addRoot(Path.of(rest)));
                        case "remove" ->
                                log.info(
                                        "Script: Index removed {}",
                                        assets.removeRoot(Path.of(rest)));
                        case "scan" -> {
                            final var snapshot = assets.scan().join();
                            log.info(
                                    "Script: Index read {} files, {} problems",
                                    snapshot.entries().size(),
                                    snapshot.problems().size());
                        }
                        case "uses" ->
                                log.info(
                                        "Script: Index {} uses {}",
                                        Path.of(rest).getFileName(),
                                        assets.getSnapshot().uses(Path.of(rest)).stream()
                                                .map(
                                                        link ->
                                                                link.label()
                                                                        + " "
                                                                        + link.name()
                                                                        + (link.file() == null
                                                                                ? " (missing)"
                                                                                : ""))
                                                .toList());
                        case "usedby" ->
                                log.info(
                                        "Script: Index {} is used by {}",
                                        Path.of(rest).getFileName(),
                                        assets.getSnapshot().usedBy(Path.of(rest)).stream()
                                                .map(link -> link.file().getFileName().toString())
                                                .sorted()
                                                .toList());
                        case "problems" ->
                                log.info(
                                        "Script: Index problems {}",
                                        assets.getSnapshot().problems().stream()
                                                .map(
                                                        problem ->
                                                                problem.file().getFileName()
                                                                        + ": "
                                                                        + problem.label()
                                                                        + " "
                                                                        + problem.name())
                                                .toList());
                        case "go" -> {
                            workbench.showRoot(Integer.parseInt(words[1]));
                            log.info(
                                    "Script: Index showing {}",
                                    workbench.getBrowser().getDirectory());
                        }
                        default -> log.warn("Script: no index command {}", words[0]);
                    }
                });
    }

    /**
     * Drive the workbench's Assets panel, logging "Script: Browser ..." with what it did: "search
     * text", "type name|any", "folder path|all", "collection name|none", "mode list|tiles", "select
     * file name", "save name" (the search shown as a collection), "pick name" (the selected file
     * into a set), "list" (what is shown), "cached file name" (whether its thumbnail is in the
     * cache), "renders label" (how many shown files have thumbnails and how many renders wait),
     * "thumbnails" (waits for the shown files' thumbnails), "uploaded file name" (whether its
     * thumbnail is shown).
     *
     * @param command The command and its words, separated by spaces; a name or path is the rest.
     */
    public static void browser(String command) {
        onWorkbench(
                workbench -> {
                    workbench.getAssets().start();
                    final AssetBrowser assets = workbench.getAssetBrowser();
                    final String[] words = command.trim().split("\\s+");
                    final String rest = rest(words, 1);
                    switch (words[0]) {
                        case "search" -> assets.search(rest);
                        case "type" -> assets.setType("any".equals(rest) ? null : rest);
                        case "folder" ->
                                assets.setFolder("all".equals(rest) ? null : Path.of(rest));
                        case "collection" ->
                                assets.showCollection("none".equals(rest) ? null : rest);
                        case "mode" -> assets.setListed("list".equals(rest));
                        case "select" -> {
                            final Path file = shownNamed(assets, rest);
                            assets.select(file);
                            log.info("Script: Browser selected {}", file);
                        }
                        case "save" -> assets.saveSearch(rest);
                        case "pick" -> {
                            if (assets.getSelected() != null) {
                                assets.pick(rest, assets.getSelected());
                            }
                        }
                        case "list" ->
                                log.info(
                                        "Script: Browser shows {} files: {}",
                                        assets.getShown().size(),
                                        assets.getShown().stream()
                                                .limit(12)
                                                .map(entry -> entry.file().getFileName().toString())
                                                .toList());
                        case "thumbnails" ->
                                log.info(
                                        "Script: Browser has {} thumbnails, {} made this run",
                                        assets.awaitThumbnails(),
                                        assets.thumbnailsMade());
                        case "renders" ->
                                log.info(
                                        "Script: Browser {} {} files: {} cached, {} renders"
                                                + " left",
                                        rest,
                                        assets.getShown().size(),
                                        assets.cachedCount(),
                                        assets.rendersLeft());
                        case "cached" -> {
                            final Path file = shownNamed(assets, rest);
                            log.info(
                                    "Script: Browser thumbnail of {} cached {}",
                                    rest,
                                    file != null && assets.isCached(file));
                        }
                        case "uploaded" -> {
                            final Path file = shownNamed(assets, rest);
                            log.info(
                                    "Script: Browser thumbnail of {} shown {}",
                                    rest,
                                    file != null && assets.hasThumbnail(file));
                        }
                        default -> log.warn("Script: no browser command {}", words[0]);
                    }
                });
    }

    /**
     * Drive the workbench's References panel, logging "Script: References ..." with what it shows:
     * "show path" (a file in the middle, the panel in front), "select title" (a node, as a click
     * would, so its file becomes the middle), "back", "log label" (the middle, its users and uses,
     * missing names and bubbles), "grep" (whether the looks using the middle are the looks whose
     * text names its ID).
     *
     * @param command The command and its words, separated by spaces; a name or path is the rest.
     */
    public static void references(String command) {
        onWorkbench(
                workbench -> {
                    workbench.getAssets().start();
                    final ReferenceViewer viewer = workbench.getReferenceViewer();
                    final String[] words = command.trim().split("\\s+");
                    final String rest = rest(words, 1);
                    switch (words[0]) {
                        case "show" -> workbench.showReferences(Path.of(rest));
                        case "select" ->
                                log.info(
                                        "Script: References selected {} {}",
                                        rest,
                                        viewer.selectTitled(rest));
                        case "back" -> viewer.goBack();
                        case "log" -> logReferences(viewer, rest);
                        case "grep" -> grepLooks(workbench, viewer);
                        default -> log.warn("Script: no references command {}", words[0]);
                    }
                });
    }

    /**
     * Drive the asset slots, logging "Script: Slots ..." with what they show: "log", or an action
     * on a slot by its label, done the next frame it draws, as if by hand.
     *
     * @param action "log".
     */
    public static void slot(String action) {
        onWorkbench(
                workbench -> {
                    if (!"log".equals(action)) {
                        log.warn("Script: no slot command {}", action);
                        return;
                    }
                    log.info(
                            "Script: Slots {}",
                            workbench.getAssetSlots().shown().stream()
                                    .map(
                                            shown ->
                                                    shown.label()
                                                            + "="
                                                            + (shown.file() == null
                                                                    ? "empty"
                                                                    : shown.file().getFileName()
                                                                            + (shown.missing()
                                                                                    ? " (missing)"
                                                                                    : "")))
                                    .toList());
                });
    }

    /**
     * Act on a slot by its label: "pick text" takes the picker's first match, "use" the Assets
     * panel's selection, "clear" empties it.
     *
     * @param action The action.
     * @param label The slot's label.
     * @param argument The search text for "pick"; ignored otherwise.
     */
    public static void slot(String action, String label, String argument) {
        onWorkbench(workbench -> workbench.getAssetSlots().request(label, action, argument));
    }

    /**
     * Act on a slot by its label: "use" the Assets panel's selection, or "clear".
     *
     * @param action The action.
     * @param label The slot's label.
     */
    public static void slot(String action, String label) {
        slot(action, label, "");
    }

    /**
     * Drop a file in a folder on a slot, as a drag from the Assets panel does, if the slot takes
     * it.
     *
     * @param label The slot's label.
     * @param folder The folder.
     * @param name The file's path in it.
     */
    public static void slotDrop(String label, String folder, String name) {
        slot("drop", label, Path.of(folder, name).toString());
    }

    /**
     * Save the material the 3D view's material editor edits to a file, and log its text, as
     * "Script: Material saved ...".
     *
     * @param folder The folder.
     * @param name The file name, ending in .material.yml for text.
     */
    public static void materialSave(String folder, String name) {
        onWorkbench(
                workbench -> {
                    final SceneView view = workbench.getSceneView();
                    final var placement = view.getPlacement();
                    final Path file = Path.of(folder, name);
                    if (placement == null || !view.getMaterials().saveSelected(placement, file)) {
                        log.info("Script: Material saved nothing, as none is edited");
                        return;
                    }
                    try {
                        log.info(
                                "Script: Material saved {}: {}",
                                name,
                                java.nio.file.Files.readString(file)
                                        .replace("\r", "")
                                        .replace('\n', '|'));
                    } catch (java.io.IOException e) {
                        log.info("Script: Material saved {}, unreadable: {}", name, e.getMessage());
                    }
                });
    }

    /**
     * Log what the References panel shows.
     *
     * @param viewer The panel.
     * @param label What to call this point in the log.
     */
    private static void logReferences(ReferenceViewer viewer, String label) {
        final ReferenceViewer.Around around = viewer.getAround();
        final ReferenceViewer.Built built = viewer.getBuilt();
        if (around == null || built == null) {
            log.info("Script: References {} shows nothing", label);
            return;
        }
        final java.util.List<String> comments = new java.util.ArrayList<>();
        for (var node : built.graph().nodes()) {
            if (node.getComment() != null) {
                comments.add(node.getComment());
            }
        }
        log.info(
                "Script: References {} of {}: used by {}, uses {}, missing {}, bubbles {}, canvas"
                        + " {}",
                label,
                around.center().getFileName(),
                names(around.users()),
                names(around.uses()),
                built.errors().values(),
                comments,
                rectText(viewer.getEditor().getCanvas().getRect()));
    }

    /**
     * A rectangle as text, its corners in pixels.
     *
     * @param rect The rectangle.
     * @return The text.
     */
    private static String rectText(com.ikalagaming.graphics.gui.util.RectFloat rect) {
        return String.format(
                java.util.Locale.ROOT,
                "(%.0f, %.0f)-(%.0f, %.0f)",
                rect.getLeft(),
                rect.getTop(),
                rect.getRight(),
                rect.getBottom());
    }

    /**
     * The names of the files beside the middle, or what a missing one names.
     *
     * @param sides The files.
     * @return The names.
     */
    private static java.util.List<String> names(java.util.List<ReferenceViewer.Side> sides) {
        return sides.stream()
                .map(
                        side ->
                                side.file() == null
                                        ? side.name()
                                        : side.file().getFileName().toString())
                .toList();
    }

    /**
     * Log whether the looks shown using the middle file are the looks, under the asset roots, whose
     * text names its ID: a search of the looks, as by hand.
     *
     * @param workbench The workbench.
     * @param viewer The References panel.
     */
    private static void grepLooks(Workbench workbench, ReferenceViewer viewer) {
        final ReferenceViewer.Around around = viewer.getAround();
        final var snapshot = workbench.getAssets().getSnapshot();
        final var entry = around == null ? null : snapshot.entry(around.center());
        if (entry == null || entry.id() == null) {
            log.info("Script: References grep has no ID to search for");
            return;
        }
        final java.util.Set<Path> grep = new java.util.TreeSet<>();
        for (var other : snapshot.entries()) {
            if (!other.file().toString().endsWith(".kvt")
                    || !other.file().toString().replace('\\', '/').contains("/looks/")) {
                continue;
            }
            try {
                final String text = java.nio.file.Files.readString(other.file());
                if (text.contains("\"" + entry.id() + "\"")
                        || text.contains("\"" + entry.id() + "#")) {
                    grep.add(other.file());
                }
            } catch (java.io.IOException e) {
                log.warn("Script: References grep can't read {}", other.file());
            }
        }
        final java.util.Set<Path> shown = new java.util.TreeSet<>();
        for (ReferenceViewer.Side side : around.users()) {
            if (side.file() != null && grep.contains(side.file())) {
                shown.add(side.file());
            } else if (side.file() != null
                    && side.file().toString().replace('\\', '/').contains("/looks/")) {
                shown.add(side.file());
            }
        }
        log.info(
                "Script: References grep of {}: {} looks name it, {} shown, same {}",
                entry.id(),
                grep.size(),
                shown.size(),
                grep.equals(shown));
    }

    /**
     * A shown file with a name.
     *
     * @param assets The Assets panel.
     * @param name The file's name.
     * @return The file, or null if none shown has it.
     */
    private static @Nullable Path shownNamed(AssetBrowser assets, String name) {
        return assets.getShown().stream()
                .map(entry -> entry.file())
                .filter(file -> file.getFileName().toString().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Drive the workbench's asset index with a folder, as {@link #index(String)} does with the
     * command and the folder.
     *
     * @param command The command.
     * @param folder The folder.
     */
    public static void index(String command, String folder) {
        index(command + " " + folder);
    }

    /**
     * Drive the workbench's asset index with a file in a folder, as {@link #index(String)} does
     * with the command and the file's path.
     *
     * @param command The command.
     * @param folder The folder.
     * @param name The file's path in it.
     */
    public static void index(String command, String folder, String name) {
        index(command + " " + Path.of(folder, name));
    }

    /**
     * Drive the workbench's References panel with a file in a folder, as {@link
     * #references(String)} does with the command and the file's path.
     *
     * @param command The command.
     * @param folder The folder.
     * @param name The file's path in it.
     */
    public static void references(String command, String folder, String name) {
        references(command + " " + Path.of(folder, name));
    }

    /**
     * Save the workbench's graph to a file, logging "Script: Graph saved" with it.
     *
     * @param folder The folder.
     * @param name The file name.
     */
    public static void graphSave(String folder, String name) {
        onWorkbench(
                workbench -> {
                    if (workbench.getGraphTool().save(Path.of(folder, name))) {
                        log.info("Script: Graph saved {}", name);
                    }
                });
    }

    /**
     * Open a graph file in the workbench's graph panel, logging "Script: Graph opened" with it.
     *
     * @param folder The folder.
     * @param name The file name.
     */
    public static void graphOpen(String folder, String name) {
        onWorkbench(
                workbench -> {
                    showMenu(false);
                    workbench.setVisible(true);
                    workbench.open(Path.of(folder, name));
                    log.info("Script: Graph opened {}", name);
                });
    }

    /**
     * Save what the workbench's 3D viewer shows to a PNG file, once its preview has settled, and
     * log "Script: Captured" with the file, or why it failed.
     *
     * @param folder The folder to save in.
     * @param name The file name.
     */
    public static void captureScene(String folder, String name) {
        onWorkbench(workbench -> capture(workbench.getSceneHost(), folder, name));
    }

    /**
     * Save what a host shows to a PNG file, logging how it went.
     *
     * @param host The host.
     * @param folder The folder to save in.
     * @param name The file name.
     */
    private static void capture(PreviewHost host, String folder, String name) {
        host.capture(Path.of(folder, name))
                .whenComplete(
                        (saved, error) -> {
                            if (error == null) {
                                log.info("Script: Captured {}", saved);
                            } else {
                                log.warn("Script: capture failed: {}", error.getMessage());
                            }
                        });
    }

    /**
     * Open one of the sample containers in the workbench, writing them first, as the Files panel's
     * Write samples button does.
     *
     * @param name The sample's file name, like "valid.ika" or "bad-checksum.ika".
     */
    public static void openSample(String name) {
        onWorkbench(
                workbench -> {
                    showMenu(false);
                    workbench.setVisible(true);
                    workbench.openSample(name);
                });
    }

    /**
     * Show a swatch in the workbench's viewer: a square of linear red rising across and green
     * rising down, for checks of swatches.
     *
     * @param size The width and height in pixels.
     */
    public static void showSwatch(int size) {
        final float[] rgba = new float[size * size * 4];
        for (int y = 0; y < size; ++y) {
            for (int x = 0; x < size; ++x) {
                final int first = (y * size + x) * 4;
                rgba[first] = (x + 0.5f) / size;
                rgba[first + 1] = (y + 0.5f) / size;
                rgba[first + 3] = 1;
            }
        }
        onWorkbench(
                workbench -> {
                    showMenu(false);
                    workbench.setVisible(true);
                    workbench.showSwatch("gradient", rgba, size, size);
                });
    }

    /**
     * Open a tool another plugin offers, as the main menu's button for it does, for checks that
     * can't click the menu.
     *
     * @param id The tool's ID, like "characters/editor".
     */
    public static void openTool(String id) {
        final var ui = graphics().ui();
        ui.post(
                () -> {
                    final var tool =
                            ui.tools().get().stream()
                                    .filter(offered -> offered.id().equals(id))
                                    .findFirst();
                    if (tool.isEmpty()) {
                        log.warn("Script: no tool {}", id);
                        return;
                    }
                    showMenu(false);
                    tool.get().open().accept(() -> showMenu(true));
                });
    }

    /**
     * Put the camera somewhere, for checks to look at a scene from a known place: the workbench's
     * 3D viewer's, while it shows a scene, else the world's. The viewer's orbit follows it.
     *
     * @param x Where it is.
     * @param y Where it is.
     * @param z Where it is.
     * @param pitch How far it looks down, in radians.
     * @param yaw How far it turns, in radians.
     */
    public static void placeCamera(double x, double y, double z, double pitch, double yaw) {
        graphics()
                .ui()
                .post(
                        () -> {
                            final var camera = camera();
                            camera.setPosition(x, y, z);
                            camera.setRotation((float) pitch, (float) yaw);
                        });
    }

    /**
     * Set how small a coverage volume must be on screen to be drawn as its shell, for checks that
     * compare shells with volumes.
     *
     * @param pixels The sphere's radius on screen, in pixels; 0 never draws shells.
     */
    public static void setShellPixels(double pixels) {
        graphics().ui().post(() -> GraphicsManager.getSettings().lodShellPixels = (float) pixels);
    }

    /**
     * Move the camera by some amount, for checks that fly through a scene a frame at a time: the
     * workbench's 3D viewer's, while it shows a scene, else the world's.
     *
     * @param dx How far along x.
     * @param dy How far along y.
     * @param dz How far along z.
     */
    public static void moveCamera(double dx, double dy, double dz) {
        graphics()
                .ui()
                .post(
                        () -> {
                            final var camera = camera();
                            final var at = camera.getPosition();
                            camera.setPosition(at.x + dx, at.y + dy, at.z + dz);
                        });
    }

    /**
     * The camera checks move: the workbench's 3D viewer's, while it has a preview, else the
     * world's. Render thread.
     *
     * @return The camera.
     */
    private static Camera camera() {
        final PreviewView preview =
                PluginManager.getInstance()
                        .getPlugin(ConverterPlugin.PLUGIN_NAME)
                        .filter(ConverterPlugin.class::isInstance)
                        .map(plugin -> ((ConverterPlugin) plugin).getWorkbench())
                        .map(workbench -> workbench.getSceneView().getPreview())
                        .orElse(null);
        return preview != null
                ? preview.getScene().getCamera()
                : GraphicsManager.getScene().getCamera();
    }

    /**
     * The texture viewer the workbench's viewer panel shows, on its own or inside a swatch.
     *
     * @param workbench The workbench.
     * @return The viewer, or null if neither is shown.
     */
    private static TextureViewer textureViewer(Workbench workbench) {
        final Swatch swatch = workbench.getHost().viewer(Swatch.class);
        return swatch != null
                ? swatch.getViewer()
                : workbench.getHost().viewer(TextureViewer.class);
    }

    /**
     * Run something with the workbench on the render thread, if the plugin is enabled.
     *
     * @param action What to do.
     */
    private static void onWorkbench(Consumer<Workbench> action) {
        graphics()
                .ui()
                .post(
                        () ->
                                PluginManager.getInstance()
                                        .getPlugin(ConverterPlugin.PLUGIN_NAME)
                                        .filter(ConverterPlugin.class::isInstance)
                                        .map(plugin -> ((ConverterPlugin) plugin).getWorkbench())
                                        .ifPresent(action));
    }

    /**
     * The converter's graphics context.
     *
     * @return The context.
     */
    private static GraphicsContext graphics() {
        return GraphicsManager.forPlugin(ConverterPlugin.PLUGIN_NAME);
    }

    /** Static methods only. */
    private ConverterScripts() {}
}
