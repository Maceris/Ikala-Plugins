package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.PreviewView;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.Textures;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IkInt;
import com.ikalagaming.graphics.gui.util.Color;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A/B comparison in the 3D viewer: the view as it is now (A) beside a pinned image of it (B), side
 * by side or under a split line the mouse moves. B is a frozen image, taken from the preview once
 * it has settled, so it holds the camera and lighting it was pinned with: pin, change a material,
 * and compare without moving the camera. Render thread only.
 */
@Slf4j
public class Comparison {
    /** How A and B are shown. */
    public enum Mode {
        /** Only A. */
        OFF("Off"),
        /** B on the left half, A on the right, each squeezed to half the width. */
        SIDE_BY_SIDE("Side by side"),
        /** B left of the split line, A right of it, each where it would be on its own. */
        SPLIT("Split");

        /** The name in the toolbar's list. */
        final String label;

        /**
         * Name a mode.
         *
         * @param label The name in the toolbar's list.
         */
        Mode(String label) {
            this.label = label;
        }
    }

    /** The modes' names, for the toolbar's list. */
    private static final String[] MODE_NAMES = {
        Mode.OFF.label, Mode.SIDE_BY_SIDE.label, Mode.SPLIT.label
    };

    /** The split line. */
    private static final int SPLIT_COLOR = Color.rgba(1.0f, 1.0f, 1.0f, 0.9f);

    /** The A and B labels. */
    private static final int LABEL_COLOR = Color.rgba(1.0f, 1.0f, 1.0f, 1.0f);

    /** How far the labels sit in from the corners. */
    private static final float LABEL_INSET = 6;

    /** Loads the pinned image. */
    private final Textures textures;

    /** Runs work back on the render thread. */
    private final UI ui;

    /** How A and B are shown, an index into {@link Mode}. */
    private final IkInt mode = new IkInt(Mode.OFF.ordinal());

    /**
     * Where the split line is, from 0 at the left to 1 at the right. -- GETTER -- Where the split
     * line is.
     *
     * @return The fraction across.
     */
    @Getter private float split = 0.5f;

    /** The pinned image, or null before one is pinned. */
    private TextureHandle pinned;

    /** Counts pins, so a slow one finishing after a newer one is dropped. */
    private final AtomicInteger pins = new AtomicInteger();

    /** What the last pin did, for the toolbar. */
    private String status = "";

    /**
     * Set up a comparison with nothing pinned.
     *
     * @param textures Loads the pinned image.
     * @param ui Runs work back on the render thread.
     */
    public Comparison(@NonNull Textures textures, @NonNull UI ui) {
        this.textures = textures;
        this.ui = ui;
    }

    /**
     * Pin the view as it is once it settles as B, and show A and B side by side if they were not
     * shown yet.
     *
     * @param view The preview.
     */
    public void pin(@NonNull PreviewView view) {
        final int pin = pins.incrementAndGet();
        final Path file;
        try {
            file = Files.createTempFile("ikala-pinned-", ".png");
        } catch (IOException e) {
            log.warn("Could not make a file for the pinned image", e);
            status = "Can't pin: " + e.getMessage();
            return;
        }
        status = "Pinning...";
        view.capture(file)
                .whenComplete(
                        (saved, error) -> {
                            if (error != null) {
                                deleteQuietly(file);
                                post(() -> status = "Can't pin: " + error.getMessage());
                                return;
                            }
                            post(() -> loadPinned(pin, file));
                        });
    }

    /**
     * Load a captured image as B, unless a newer pin was asked for. Render thread.
     *
     * @param pin Which pin this is.
     * @param file The image.
     */
    private void loadPinned(int pin, Path file) {
        try {
            if (pin != pins.get()) {
                return;
            }
            // The preview's own bytes, shown as they are, as its texture is
            final TextureHandle loaded = textures.load(file.toString(), ColorSpace.LINEAR);
            release();
            pinned = loaded;
            status = "Pinned as B";
            if (mode.get() == Mode.OFF.ordinal()) {
                mode.set(Mode.SIDE_BY_SIDE.ordinal());
            }
        } catch (RuntimeException e) {
            log.warn("Could not load the pinned image {}", file, e);
            status = "Can't pin: " + e.getMessage();
        } finally {
            deleteQuietly(file);
        }
    }

    /**
     * Run something on the render thread, unless the plugin unloaded.
     *
     * @param change What to run.
     */
    private void post(Runnable change) {
        try {
            ui.post(change);
        } catch (IllegalStateException unloaded) {
            // Nothing to show it in any more
        }
    }

    /**
     * Delete a temporary file, ignoring failure.
     *
     * @param file The file.
     */
    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.debug("Could not delete {}", file, e);
        }
    }

    /**
     * How A and B are shown now.
     *
     * @return The mode; off while nothing is pinned.
     */
    public Mode getMode() {
        return pinned == null ? Mode.OFF : Mode.values()[mode.get()];
    }

    /**
     * Show A and B a given way.
     *
     * @param newMode The mode.
     */
    public void setMode(@NonNull Mode newMode) {
        mode.set(newMode.ordinal());
    }

    /**
     * Whether an image is pinned.
     *
     * @return True once pinned.
     */
    public boolean isPinned() {
        return pinned != null;
    }

    /**
     * Draw the toolbar's controls: pin, and how to show A and B.
     *
     * @param view The preview to pin.
     */
    public void drawControls(@NonNull PreviewView view) {
        if (IkGui.button("Pin as B")) {
            pin(view);
        }
        IkGui.setItemTooltip(
                "Keep the view as it is now, to compare with after a change. Keep the camera and"
                        + " lighting as they are.");
        if (pinned != null) {
            IkGui.sameLine();
            IkGui.setNextItemWidth(130);
            IkGui.combo("A/B##compare", mode, MODE_NAMES);
        }
        if (!status.isEmpty()) {
            IkGui.sameLine();
            IkGui.textDisabled(status);
        }
    }

    /**
     * Draw A, and B beside it or under the split, filling a rectangle.
     *
     * @param drawList Where to draw.
     * @param live A, the preview's texture.
     * @param left The rectangle's left edge on screen.
     * @param top Its top edge.
     * @param width Its width.
     * @param height Its height.
     * @param mouseX The mouse across the screen, for moving the split while dragged.
     * @param dragging Whether the split is being dragged.
     */
    public void draw(
            @NonNull DrawList drawList,
            @NonNull TextureHandle live,
            float left,
            float top,
            float width,
            float height,
            float mouseX,
            boolean dragging) {
        final float right = left + width;
        final float bottom = top + height;
        final TextureHandle b = pinned;
        switch (getMode()) {
            case OFF -> drawList.addImage(live, left, top, right, bottom);
            case SIDE_BY_SIDE -> {
                final float middle = left + width / 2;
                drawList.addImage(b, left, top, middle, bottom);
                drawList.addImage(live, middle, top, right, bottom);
                drawList.addLine(middle, top, middle, bottom, SPLIT_COLOR, 1);
                labels(drawList, left, top, middle);
            }
            case SPLIT -> {
                if (dragging) {
                    split = Math.clamp((mouseX - left) / width, 0, 1);
                }
                final float at = left + width * split;
                drawList.addImage(b, left, top, at, bottom, 0, 0, split, 1);
                drawList.addImage(live, at, top, right, bottom, split, 0, 1, 1);
                drawList.addLine(at, top, at, bottom, SPLIT_COLOR, 2);
                labels(drawList, left, top, at);
            }
        }
    }

    /**
     * Label B at the left and A at the line between them.
     *
     * @param drawList Where to draw.
     * @param left The left edge.
     * @param top The top edge.
     * @param between Where A starts.
     */
    private static void labels(DrawList drawList, float left, float top, float between) {
        drawList.addText(
                (int) IkGui.getFontSize(),
                left + LABEL_INSET,
                top + LABEL_INSET,
                LABEL_COLOR,
                "B",
                0);
        drawList.addText(
                (int) IkGui.getFontSize(),
                between + LABEL_INSET,
                top + LABEL_INSET,
                LABEL_COLOR,
                "A",
                0);
    }

    /** Release the pinned image, showing only A. */
    public void release() {
        if (pinned != null) {
            textures.release(pinned);
            pinned = null;
        }
    }
}
