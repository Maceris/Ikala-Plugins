package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Style;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Holds every shown surface and draws them each frame. Surfaces are owned by the graphics context
 * of the plugin that showed them, and are removed when that plugin unloads.
 *
 * <p>The surfaces are only touched on the render thread. Other threads post changes, which run at
 * the start of the next {@link #draw()}.
 */
@Slf4j
public class UiManager {

    /**
     * A shown surface and who owns it.
     *
     * @param owner The context of the plugin that showed it.
     * @param surface The surface.
     */
    private record Entry(@NonNull GraphicsContext owner, @NonNull Surface surface) {}

    /** Shown surfaces by ID, in the order they were added. Render thread only. */
    private final Map<String, Entry> surfaces = new LinkedHashMap<>();

    /** Changes posted from any thread, run at the start of the next frame. */
    private final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();

    /**
     * Actions fired by nodes while drawing, run by {@link #dispatchEvents()}. Render thread only.
     */
    private final List<Runnable> events = new ArrayList<>();

    /** Lays out the surfaces. */
    private final LayoutEngine engine = new LayoutEngine();

    /** Measures text through IkGui, at any font size. */
    private final LayoutContext.TextMeasurer measurer =
            (text, fontPixels, out) -> {
                IkGui.pushFontSize(Math.max(1, Math.round(fontPixels)));
                Vector2f size = IkGui.calcTextSize(text);
                IkGui.popFont();
                out[0] = size.x;
                out[1] = size.y;
            };

    /** The screen's content scale, from the OS DPI setting. */
    private volatile float contentScale = 1;

    /** The user's UI scale setting. */
    private volatile float uiScale = 1;

    /** The last layout context, reused while nothing changes so layouts stay cached. */
    private LayoutContext lastContext;

    /**
     * Run a change on the render thread at the start of the next frame. Safe from any thread.
     *
     * @param change The change to make.
     */
    public void post(@NonNull Runnable change) {
        pending.add(change);
    }

    /**
     * Set the screen's content scale, which comes from the OS DPI setting.
     *
     * @param scale The content scale, where 1 is 96 DPI on Windows.
     */
    public void setContentScale(float scale) {
        contentScale = scale > 0 ? scale : 1;
    }

    /**
     * Set the user's UI scale, which multiplies the content scale.
     *
     * @param scale The UI scale, where 1 is the default size.
     */
    public void setUiScale(float scale) {
        uiScale = scale > 0 ? scale : 1;
    }

    /**
     * Show a surface. Render thread only; other threads post this.
     *
     * @param owner The context of the plugin showing it.
     * @param surface The surface.
     */
    public void add(@NonNull GraphicsContext owner, @NonNull Surface surface) {
        if (owner.isClosed()) {
            log.debug("Not showing surface {}, {} has unloaded", surface.getId(), owner.getOwner());
            return;
        }
        surface.setVisible(true);
        Entry previous = surfaces.put(surface.getId(), new Entry(owner, surface));
        if (previous != null && previous.surface() != surface) {
            if (previous.owner().getOwner().equals(owner.getOwner())) {
                log.debug("Surface {} replaced by {}", surface.getId(), owner.getOwnerKey());
            } else {
                log.warn(
                        "Surface {} from {} replaced one from {}",
                        surface.getId(),
                        owner.getOwner(),
                        previous.owner().getOwner());
            }
        }
    }

    /**
     * Find a shown surface. Render thread only.
     *
     * @param id The surface ID.
     * @return The surface, or null if none with that ID is shown.
     */
    public Surface get(@NonNull String id) {
        Entry entry = surfaces.get(id);
        return entry == null ? null : entry.surface();
    }

    /**
     * Show or hide a surface without removing it. Render thread only.
     *
     * @param owner The context of the plugin asking, which must own the surface.
     * @param id The surface ID.
     * @param show Whether to draw it.
     */
    public void setVisible(@NonNull GraphicsContext owner, @NonNull String id, boolean show) {
        Entry entry = surfaces.get(id);
        if (entry != null && entry.owner() == owner) {
            entry.surface().setVisible(show);
        }
    }

    /**
     * Remove a surface. Render thread only.
     *
     * @param owner The context of the plugin asking, which must own the surface.
     * @param id The surface ID.
     */
    public void remove(@NonNull GraphicsContext owner, @NonNull String id) {
        Entry entry = surfaces.get(id);
        if (entry != null && entry.owner() == owner) {
            surfaces.remove(id);
        }
    }

    /**
     * Remove every surface a context owns, at the start of the next frame. Safe from any thread;
     * called when a plugin unloads.
     *
     * @param owner The context whose surfaces should go.
     */
    public void removeAllOwnedBy(@NonNull GraphicsContext owner) {
        post(
                () -> {
                    int before = surfaces.size();
                    surfaces.values().removeIf(entry -> entry.owner() == owner);
                    int removed = before - surfaces.size();
                    if (removed > 0) {
                        log.debug("Removed {} surfaces owned by {}", removed, owner.getOwnerKey());
                    }
                });
    }

    /**
     * How many surfaces are shown or hidden. Render thread only.
     *
     * @return The number of surfaces.
     */
    public int surfaceCount() {
        return surfaces.size();
    }

    /** Run posted changes. Render thread only. */
    void runPending() {
        Runnable change;
        while ((change = pending.poll()) != null) {
            try {
                change.run();
            } catch (RuntimeException e) {
                log.warn("A posted UI change failed", e);
            }
        }
    }

    /**
     * Run posted changes, then lay out and submit every visible surface. Render thread only,
     * between IkGui's new frame and render.
     */
    public void draw() {
        runPending();
        if (surfaces.isEmpty()) {
            return;
        }
        final Style style = IkGui.getStyle();
        final LayoutContext context =
                context(
                        contentScale * uiScale,
                        // The size fonts are pushed with, not the line height
                        IkGui.getFontSize(),
                        style.variable.framePadding.x,
                        style.variable.framePadding.y);
        final Viewport viewport = IkGui.getMainViewport();
        final RectFloat area =
                new RectFloat(
                        viewport.workPosition.x,
                        viewport.workPosition.y,
                        viewport.workPosition.x + viewport.workSize.x,
                        viewport.workPosition.y + viewport.workSize.y);
        final UiFrame frame = new UiFrame(context, events::add, engine);
        for (Entry entry : surfaces.values()) {
            if (entry.surface().isVisible()) {
                entry.surface().draw(area, frame, engine);
            }
        }
    }

    /**
     * Run the actions nodes fired while drawing, such as button clicks. Render thread only, outside
     * rendering, so actions can do things like load models.
     */
    public void dispatchEvents() {
        if (events.isEmpty()) {
            return;
        }
        List<Runnable> fired = new ArrayList<>(events);
        events.clear();
        for (Runnable action : fired) {
            try {
                action.run();
            } catch (RuntimeException e) {
                log.warn("A UI action failed", e);
            }
        }
    }

    /**
     * The layout context for this frame, reusing the last one if nothing changed, so that layouts
     * stay cached.
     *
     * @param scale Pixels per UI unit.
     * @param fontSize The default font size.
     * @param framePaddingX The frame padding, horizontally.
     * @param framePaddingY The frame padding, vertically.
     * @return The layout context.
     */
    private LayoutContext context(
            float scale, float fontSize, float framePaddingX, float framePaddingY) {
        LayoutContext next =
                new LayoutContext(scale, fontSize, framePaddingX, framePaddingY, measurer);
        if (!next.equals(lastContext)) {
            lastContext = next;
        }
        return lastContext;
    }
}
