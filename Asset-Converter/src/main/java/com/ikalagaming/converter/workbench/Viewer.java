package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.ui.UiFrame;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

/**
 * One kind of viewer the {@link PreviewHost} shows: a texture, a container, a swatch. The host
 * draws the frame around it, the toolbar with the file's name and the capture button, and asks the
 * viewer only for its own content and controls. Render thread only.
 */
public interface Viewer {
    /**
     * What kind of viewer this is, for the toolbar, like "Texture".
     *
     * @return The name.
     */
    String kind();

    /**
     * The file shown, for the toolbar and the material editor.
     *
     * @return The file, or null if what is shown isn't a file, like a swatch.
     */
    @Nullable Path file();

    /**
     * Draw the viewer's own toolbar controls, after the host's, on the same line.
     *
     * @param frame The current frame.
     */
    default void drawToolbar(@NonNull UiFrame frame) {
        // Most viewers have none
    }

    /**
     * Draw what the viewer shows, filling the space left under the toolbar.
     *
     * @param frame The current frame.
     */
    void drawContent(@NonNull UiFrame frame);

    /**
     * Draw details and settings in the properties panel.
     *
     * @param frame The current frame.
     */
    void drawProperties(@NonNull UiFrame frame);

    /**
     * Whether {@link #capture} can save what is shown.
     *
     * @return True if it can.
     */
    default boolean canCapture() {
        return false;
    }

    /**
     * Save what is shown to a PNG file.
     *
     * @param file Where to save it.
     * @return Completes with the file once saved, or exceptionally if it can't be.
     */
    default CompletableFuture<Path> capture(@NonNull Path file) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException(kind() + " views can't be captured"));
    }

    /** Stop showing, releasing what the viewer owns, like its texture. */
    void close();
}
