package com.ikalagaming.graphics.overlay;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Checkbox;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Layer;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Selectable;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The debug overlay: a panel in the top left corner, toggled with a key, made of collapsible
 * sections that plugins add through {@code GraphicsContext.debugOverlay()}. It is retained UI; the
 * lines of text are read again {@value #REFRESHES_PER_SECOND} times a second while shown. A
 * plugin's sections go when it unloads. The panel doesn't take keyboard navigation, so clicking a
 * toggle doesn't stop the game from reading the keys.
 */
@Slf4j
public final class DebugOverlay {

    /** The overlay's surface. */
    public static final String SURFACE_ID = "graphics/debug-overlay";

    /** How often the lines are read again while shown. */
    public static final int REFRESHES_PER_SECOND = 10;

    /** The time between refreshes. */
    private static final long REFRESH_NANOS = TimeUnit.SECONDS.toNanos(1) / REFRESHES_PER_SECOND;

    /** How wide the panel is, in UI units. */
    private static final float WIDTH = 420;

    /** The gap to the corner of the screen, in UI units. */
    private static final float MARGIN = 8;

    /** The most of the screen's height the panel takes. */
    private static final float MAX_HEIGHT = 0.92f;

    /** Shown instead of a line whose supplier threw. */
    private static final String BROKEN = "(error)";

    /** Guards the sections and flags. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The sections, by ID. */
    private final Map<String, DebugSection> sections = new HashMap<>();

    /** Whether the panel shows. */
    private volatile boolean visible;

    /** Whether the sections changed since the panel was built. */
    private volatile boolean dirty = true;

    /** Whether the surface is up, as last built. */
    private boolean shown;

    /** The labels showing lines, to refresh. */
    private final Map<Label, DebugSection.Line> lines = new HashMap<>();

    /** The checkboxes showing toggles, to refresh. */
    private final Map<Checkbox, DebugSection.Toggle> toggles = new HashMap<>();

    /** When the lines were last read. */
    private long lastRefresh;

    /**
     * Add a section, or get the one already added with that ID by the same owner.
     *
     * @param owner Who adds it, so it goes when they unload.
     * @param id The ID, unique in the overlay.
     * @param title The heading.
     * @param order Where it goes; lower comes first.
     * @param open Whether it starts expanded.
     * @return The section, to fill in.
     * @throws IllegalArgumentException If another owner added that ID.
     */
    public DebugSection section(
            @NonNull String owner,
            @NonNull String id,
            @NonNull String title,
            int order,
            boolean open) {
        lock.lock();
        try {
            final DebugSection existing = sections.get(id);
            if (existing != null) {
                if (!existing.getOwner().equals(owner)) {
                    throw new IllegalArgumentException(
                            "The debug overlay section "
                                    + id
                                    + " belongs to "
                                    + existing.getOwner());
                }
                return existing;
            }
            final DebugSection section =
                    new DebugSection(id, title, order, owner, open, this::markDirty);
            sections.put(id, section);
            dirty = true;
            return section;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove a section.
     *
     * @param id Its ID.
     */
    public void removeSection(@NonNull String id) {
        lock.lock();
        try {
            if (sections.remove(id) != null) {
                dirty = true;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove every section an owner added.
     *
     * @param owner The owner.
     * @return How many were removed.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            final int before = sections.size();
            sections.values().removeIf(section -> section.getOwner().equals(owner));
            final int removed = before - sections.size();
            if (removed > 0) {
                dirty = true;
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The sections, in the order shown.
     *
     * @return A copy of the list.
     */
    public List<DebugSection> sections() {
        lock.lock();
        try {
            final List<DebugSection> list = new ArrayList<>(sections.values());
            list.sort(
                    Comparator.comparingInt(DebugSection::getOrder)
                            .thenComparing(DebugSection::getId));
            return list;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether the panel shows.
     *
     * @return True if shown.
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Show or hide the panel.
     *
     * @param show True to show it.
     */
    public void setVisible(boolean show) {
        visible = show;
    }

    /** Show the panel if hidden, or hide it if shown. */
    public void toggle() {
        visible = !visible;
    }

    /** Rebuild the panel before it next shows. */
    private void markDirty() {
        dirty = true;
    }

    /**
     * Show, hide, rebuild and refresh the panel as needed. Once per frame on the main thread.
     *
     * @param graphics The graphics that own the surface.
     */
    public void update(@NonNull GraphicsContext graphics) {
        if (graphics.isClosed()) {
            return;
        }
        if (!visible) {
            if (shown) {
                graphics.ui().setVisible(SURFACE_ID, false);
                shown = false;
            }
            return;
        }
        final long now = System.nanoTime();
        if (dirty || !shown) {
            dirty = false;
            graphics.ui().show(build(graphics));
            shown = true;
            refresh();
            lastRefresh = now;
        } else if (now - lastRefresh >= REFRESH_NANOS) {
            refresh();
            lastRefresh = now;
        }
    }

    /**
     * Build the panel from the sections.
     *
     * @param graphics The graphics that own the surface.
     * @return The surface.
     */
    private Surface build(@NonNull GraphicsContext graphics) {
        lines.clear();
        toggles.clear();
        final Column content = new Column("sections").gap(2).align(Align.STRETCH);
        int sectionIndex = 0;
        for (DebugSection section : sections()) {
            final String prefix = "s" + sectionIndex++;
            content.add(
                    new Selectable(prefix, (section.isOpen() ? "- " : "+ ") + section.getTitle())
                            .classes("title")
                            .onClick(() -> section.setOpen(!section.isOpen())));
            if (!section.isOpen()) {
                continue;
            }
            int itemIndex = 0;
            for (DebugSection.Item item : section.items()) {
                final String id = prefix + "-" + itemIndex++;
                content.add(node(id, item));
            }
        }
        if (sectionIndex == 0) {
            content.add(new Label("empty", "Nothing to show").classes("muted"));
        }
        final Surface surface =
                graphics.ui()
                        .surface(SURFACE_ID)
                        .anchors(Anchors.topLeft().offset(Length.u(MARGIN), Length.u(MARGIN)))
                        .width(Sizing.fixed(WIDTH))
                        // As tall as the open sections, so it covers no more of the game
                        .height(Sizing.fit(Length.ZERO, Length.percent(MAX_HEIGHT)))
                        .layer(Layer.NORMAL)
                        .keepsKeyboard(false)
                        .content(content);
        return Objects.requireNonNull(surface);
    }

    /**
     * The node for an item.
     *
     * @param id The node's ID.
     * @param item The item.
     * @return The node.
     */
    private com.ikalagaming.graphics.ui.Node<?> node(
            @NonNull String id, @NonNull DebugSection.Item item) {
        return switch (item) {
            case DebugSection.Line line -> {
                final Label label = new Label(id, "");
                lines.put(label, line);
                yield label;
            }
            case DebugSection.Toggle toggle -> {
                final Checkbox checkbox =
                        new Checkbox(id, toggle.label()).onChange(on -> toggle.set().accept(on));
                toggles.put(checkbox, toggle);
                yield checkbox;
            }
            case DebugSection.Action action ->
                    new Button(id, action.label()).onClick(action.action());
            case DebugSection.Custom custom ->
                    new Immediate(id, custom.draw())
                            .width(Sizing.grow())
                            .height(Sizing.fixed(custom.height()));
        };
    }

    /** Read every line and toggle again. */
    private void refresh() {
        lines.forEach(
                (label, line) -> {
                    String text;
                    try {
                        text = Objects.requireNonNullElse(line.text().get(), "");
                    } catch (RuntimeException e) {
                        log.debug("A debug overlay line failed", e);
                        text = BROKEN;
                    }
                    label.text(text);
                });
        toggles.forEach(DebugOverlay::refreshToggle);
    }

    /**
     * Show a toggle's state, which may change elsewhere.
     *
     * @param checkbox Its node.
     * @param toggle The toggle.
     */
    private static void refreshToggle(
            @NonNull Checkbox checkbox, @NonNull DebugSection.Toggle toggle) {
        boolean on;
        try {
            on = toggle.get().getAsBoolean();
        } catch (RuntimeException e) {
            log.debug("A debug overlay toggle failed", e);
            on = false;
        }
        checkbox.checked(on);
    }
}
