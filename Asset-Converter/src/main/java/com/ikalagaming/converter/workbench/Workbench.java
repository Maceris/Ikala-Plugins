package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Insets;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Row;
import com.ikalagaming.graphics.ui.Scroll;
import com.ikalagaming.graphics.ui.Selectable;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.TextInput;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

/**
 * The asset workbench: browse for raw assets and open them. For now it opens images, shown in an
 * {@link ImageViewer}; meshes, scenes and materials come later. Built on retained UI, with the
 * viewer drawn in {@link Immediate} areas.
 *
 * <p>Files are decoded on a background thread, so a large image doesn't stall the editor, and the
 * result is handed to the render thread through {@link UI#post}. Everything else is render thread
 * only, except {@link #stop}.
 */
@Slf4j
public class Workbench {
    /** The ID of the workbench surface. */
    public static final String SURFACE_ID = "converter/workbench";

    /** The width of the file list. */
    private static final float BROWSER_WIDTH = 260;

    /** The width of the properties panel. */
    private static final float PROPERTIES_WIDTH = 330;

    /** The plugin's graphics context, which owns the textures. */
    private final GraphicsContext graphics;

    /** The UI the workbench is shown through. */
    private final UI ui;

    /** Goes back to the menu. */
    private final Runnable back;

    /** What is being browsed. */
    private final FileBrowser browser;

    /** Shows the open image. */
    private final ImageViewer viewer = new ImageViewer();

    /** Decodes files off the render thread, one at a time. */
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

    /**
     * Create the workbench, browsing the working directory.
     *
     * @param graphics The plugin's graphics context.
     * @param ui The UI to show the workbench through.
     * @param back Goes back to the menu.
     */
    public Workbench(@NonNull GraphicsContext graphics, @NonNull UI ui, @NonNull Runnable back) {
        this.graphics = graphics;
        this.ui = ui;
        this.back = back;
        browser = new FileBrowser(Path.of(""));
        path = new TextInput("path", "").onSubmit(this::openPath).width(Sizing.grow());
        files = new Column("files").align(Align.STRETCH);
        filter = new Button("filter", "").onClick(this::toggleFilter);
        status = new Label("status", "");
        refresh();
    }

    /**
     * Build the workbench surface, ready to show.
     *
     * @return The surface.
     */
    public Surface build() {
        Row toolbar =
                new Row("toolbar")
                        .gap(8)
                        .add(
                                new Button("back", "Back").onClick(back),
                                path,
                                new Button("up", "Up").onClick(this::up),
                                new Button("open", "Open").onClick(() -> openPath(path.text())),
                                filter);

        Row drives = new Row("drives").gap(4);
        List<Path> roots = browser.roots();
        if (roots.size() > 1) {
            for (int i = 0; i < roots.size(); ++i) {
                Path root = roots.get(i);
                drives.add(new Button("root" + i, root.toString()).onClick(() -> navigate(root)));
            }
        }

        Column browserColumn =
                new Column("browser")
                        .width(Sizing.fixed(BROWSER_WIDTH))
                        .height(Sizing.grow())
                        .gap(4)
                        .align(Align.STRETCH)
                        .add(drives, new Scroll("list").border(true).content(files));

        Immediate view = new Immediate("view", viewer::drawView).border(true);

        Column properties =
                new Column("properties")
                        .width(Sizing.fixed(PROPERTIES_WIDTH))
                        .height(Sizing.grow())
                        .align(Align.STRETCH)
                        .add(new Immediate("details", viewer::drawProperties));

        Column content =
                new Column("content")
                        .padding(Insets.all(4))
                        .gap(8)
                        .align(Align.STRETCH)
                        .add(
                                toolbar,
                                new Row("body")
                                        .height(Sizing.grow())
                                        .gap(8)
                                        .align(Align.STRETCH)
                                        .add(browserColumn, view, properties),
                                status);

        return ui.surface(SURFACE_ID).anchors(Anchors.fill()).content(content);
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
     * Open a file, decoding it in the background.
     *
     * @param file The file.
     */
    public void open(@NonNull Path file) {
        final Path absolute = file.toAbsolutePath().normalize();
        final AssetKind kind = AssetKind.of(absolute);
        if (kind != AssetKind.IMAGE) {
            status.text("Can't open " + absolute.getFileName() + " yet, only images");
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
        loader.execute(() -> load(absolute, request));
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
            final TextureHandle texture =
                    graphics.textures()
                            .load(
                                    image.pixels(),
                                    image.depth().format,
                                    image.width(),
                                    image.height());
            final long millis = (System.nanoTime() - start) / 1_000_000;
            ui.post(() -> loaded(request, image, texture, millis));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not open {}", file, e);
            final String message = "Can't open " + file.getFileName() + ": " + e.getMessage();
            try {
                ui.post(() -> failed(request, message));
            } catch (IllegalStateException unloaded) {
                // The plugin unloaded while loading, so there is nothing to tell
            }
        }
    }

    /**
     * Show a loaded image, unless a newer one was asked for. Render thread.
     *
     * @param request Which request this was.
     * @param image The image.
     * @param texture Its texture.
     * @param millis How long decoding took.
     */
    private void loaded(int request, ImageFile image, TextureHandle texture, long millis) {
        if (request != requests.get()) {
            graphics.textures().release(texture);
            return;
        }
        releaseTexture();
        viewer.show(image, texture);
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

    /** Release the shown image's texture, if there is one. */
    private void releaseTexture() {
        TextureHandle old = viewer.getTexture();
        viewer.clear();
        if (old != null) {
            graphics.textures().release(old);
        }
    }

    /**
     * Stop loading and let go of the image. The texture itself goes with the plugin's graphics
     * context. Safe from any thread.
     */
    public void stop() {
        requests.incrementAndGet();
        loader.shutdownNow();
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
