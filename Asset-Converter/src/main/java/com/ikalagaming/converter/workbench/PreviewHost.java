package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.ui.UiFrame;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

/**
 * Draws the frame every viewer shares: a toolbar with what is shown and a capture button, then the
 * viewer's own controls, and under them the viewer's content. The properties panel shows the
 * viewer's details. One viewer at a time; showing another closes the last. Render thread only.
 */
@Slf4j
public class PreviewHost {
    /** The UI, for the capture's save dialog. */
    private final UI ui;

    /** What to show while no viewer is. */
    private final String emptyText;

    /**
     * The viewer shown.
     *
     * @return The viewer, or null if nothing is shown.
     */
    @Getter private @Nullable Viewer viewer;

    /** What the last capture did, for the toolbar. */
    private String captureStatus = "";

    /**
     * Set up the host, showing nothing.
     *
     * @param ui The UI, for the capture's save dialog.
     * @param emptyText What to show while no viewer is, like how to open something.
     */
    public PreviewHost(@NonNull UI ui, @NonNull String emptyText) {
        this.ui = ui;
        this.emptyText = emptyText;
    }

    /**
     * Show a viewer, closing the one shown before.
     *
     * @param newViewer The viewer.
     */
    public void show(@NonNull Viewer newViewer) {
        if (viewer != null && viewer != newViewer) {
            viewer.close();
        }
        viewer = newViewer;
        captureStatus = "";
    }

    /** Close the viewer shown, if any. */
    public void clear() {
        if (viewer != null) {
            viewer.close();
            viewer = null;
        }
        captureStatus = "";
    }

    /**
     * The viewer shown, if it is of a kind.
     *
     * @param type The kind of viewer.
     * @param <T> The kind of viewer.
     * @return The viewer, or null if nothing or something else is shown.
     */
    public <T extends Viewer> @Nullable T viewer(@NonNull Class<T> type) {
        return type.isInstance(viewer) ? type.cast(viewer) : null;
    }

    /**
     * Save what the viewer shows to a PNG file.
     *
     * @param file Where to save it.
     * @return Completes with the file once saved, or exceptionally if nothing is shown or the
     *     viewer can't capture.
     */
    public CompletableFuture<Path> capture(@NonNull Path file) {
        final Viewer shown = viewer;
        if (shown == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Nothing is shown"));
        }
        if (!shown.canCapture()) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException(shown.kind() + " views can't be captured"));
        }
        return shown.capture(file)
                .whenComplete(
                        (saved, error) -> {
                            if (error != null) {
                                log.warn("Could not capture to {}", file, error);
                                captureStatus = "Capture failed";
                            } else {
                                captureStatus = "Saved " + saved.getFileName();
                            }
                        });
    }

    /**
     * Draw the toolbar and the viewer's content, filling the immediate area.
     *
     * @param frame The current frame.
     */
    public void draw(@NonNull UiFrame frame) {
        final Viewer shown = viewer;
        if (shown == null) {
            IkGui.textDisabled(emptyText);
            return;
        }
        final Path file = shown.file();
        IkGui.textUnformatted(
                shown.kind() + (file == null ? "" : ": " + file.getFileName().toString()));
        if (shown.canCapture()) {
            IkGui.sameLine();
            if (IkGui.button("Capture")) {
                frame.fire(() -> askToCapture(shown));
            }
            IkGui.setItemTooltip("Save what is shown to a PNG file");
            if (!captureStatus.isEmpty()) {
                IkGui.sameLine();
                IkGui.textDisabled(captureStatus);
            }
        }
        shown.drawToolbar(frame);
        IkGui.separator();
        shown.drawContent(frame);
    }

    /**
     * Draw the viewer's details for the properties panel.
     *
     * @param frame The current frame.
     */
    public void drawProperties(@NonNull UiFrame frame) {
        final Viewer shown = viewer;
        if (shown == null) {
            IkGui.textDisabled("Nothing open");
            return;
        }
        shown.drawProperties(frame);
    }

    /**
     * Ask where to save a capture, then save it.
     *
     * @param shown The viewer to capture.
     */
    private void askToCapture(Viewer shown) {
        final Path file = shown.file();
        final String name =
                file == null
                        ? shown.kind().toLowerCase(Locale.ROOT)
                        : stem(file.getFileName().toString());
        ui.saveFile(
                "Save capture",
                file == null ? null : file.toAbsolutePath().getParent(),
                name + "-capture.png",
                chosen -> {
                    if (chosen != null && viewer == shown) {
                        capture(chosen);
                    }
                },
                "png");
    }

    /**
     * A file name without its extension.
     *
     * @param name The file name.
     * @return The name up to its last dot.
     */
    static String stem(@NonNull String name) {
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
