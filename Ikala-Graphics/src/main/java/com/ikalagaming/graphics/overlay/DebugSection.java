package com.ikalagaming.graphics.overlay;

import com.ikalagaming.graphics.ui.UiFrame;

import lombok.Getter;
import lombok.NonNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A collapsible part of the {@link DebugOverlay}, filled in by one plugin: lines of text that are
 * read again a few times a second, toggles, buttons, and custom IkGui content. Add items once; the
 * suppliers and callbacks run on the main thread while the section is open and the overlay shows.
 */
public final class DebugSection {

    /** Something in a section. */
    sealed interface Item permits Line, Toggle, Action, Custom {
        /**
         * Its ID, unique in the section.
         *
         * @return The ID.
         */
        String id();
    }

    /**
     * A line of text.
     *
     * @param id The ID.
     * @param text Gives the text.
     */
    record Line(@NonNull String id, @NonNull Supplier<String> text) implements Item {}

    /**
     * A switch.
     *
     * @param id The ID.
     * @param label What it switches.
     * @param get Whether it is on.
     * @param set Turns it on or off.
     */
    record Toggle(
            @NonNull String id,
            @NonNull String label,
            @NonNull BooleanSupplier get,
            @NonNull Consumer<Boolean> set)
            implements Item {}

    /**
     * A button.
     *
     * @param id The ID.
     * @param label Its text.
     * @param action What it does.
     */
    record Action(@NonNull String id, @NonNull String label, @NonNull Runnable action)
            implements Item {}

    /**
     * IkGui content drawn every frame.
     *
     * @param id The ID.
     * @param height How tall it is, in UI units.
     * @param draw Draws it.
     */
    record Custom(@NonNull String id, float height, @NonNull Consumer<UiFrame> draw)
            implements Item {}

    /**
     * The section's ID, unique in the overlay.
     *
     * @return The ID.
     */
    @Getter private final String id;

    /**
     * The heading.
     *
     * @return The title.
     */
    @Getter private final String title;

    /**
     * Where it goes: lower comes first.
     *
     * @return The order.
     */
    @Getter private final int order;

    /**
     * Who added it, so it goes when they unload.
     *
     * @return The owner key.
     */
    @Getter private final String owner;

    /** Whether it is expanded. */
    private volatile boolean open;

    /** The items, in order. */
    private final List<Item> items = new CopyOnWriteArrayList<>();

    /** Tells the overlay to rebuild. */
    private final Runnable changed;

    /**
     * A section.
     *
     * @param id The ID.
     * @param title The heading.
     * @param order Where it goes.
     * @param owner Who added it.
     * @param open Whether it starts expanded.
     * @param changed Tells the overlay to rebuild.
     */
    DebugSection(
            @NonNull String id,
            @NonNull String title,
            int order,
            @NonNull String owner,
            boolean open,
            @NonNull Runnable changed) {
        this.id = id;
        this.title = title;
        this.order = order;
        this.owner = owner;
        this.open = open;
        this.changed = changed;
    }

    /**
     * Add a line of text, read again a few times a second while shown.
     *
     * @param itemId The ID, unique in the section.
     * @param text Gives the text; may return several lines.
     * @return This section.
     */
    public DebugSection line(@NonNull String itemId, @NonNull Supplier<String> text) {
        return add(new Line(itemId, text));
    }

    /**
     * Add a switch.
     *
     * @param itemId The ID, unique in the section.
     * @param label What it switches.
     * @param get Whether it is on.
     * @param set Turns it on or off.
     * @return This section.
     */
    public DebugSection toggle(
            @NonNull String itemId,
            @NonNull String label,
            @NonNull BooleanSupplier get,
            @NonNull Consumer<Boolean> set) {
        return add(new Toggle(itemId, label, get, set));
    }

    /**
     * Add a button.
     *
     * @param itemId The ID, unique in the section.
     * @param label Its text.
     * @param action What it does.
     * @return This section.
     */
    public DebugSection button(
            @NonNull String itemId, @NonNull String label, @NonNull Runnable action) {
        return add(new Action(itemId, label, action));
    }

    /**
     * Add IkGui content, like a graph, drawn every frame while shown.
     *
     * @param itemId The ID, unique in the section.
     * @param height How tall it is, in UI units.
     * @param draw Draws it.
     * @return This section.
     */
    public DebugSection custom(
            @NonNull String itemId, float height, @NonNull Consumer<UiFrame> draw) {
        return add(new Custom(itemId, height, draw));
    }

    /**
     * Whether the section is expanded.
     *
     * @return True if open.
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * Expand or collapse the section.
     *
     * @param expand True to expand.
     */
    public void setOpen(boolean expand) {
        if (open != expand) {
            open = expand;
            changed.run();
        }
    }

    /**
     * The items, in order.
     *
     * @return The items.
     */
    List<Item> items() {
        return List.copyOf(items);
    }

    /**
     * Add an item, replacing one with the same ID.
     *
     * @param item The item.
     * @return This section.
     */
    private DebugSection add(@NonNull Item item) {
        items.removeIf(existing -> existing.id().equals(item.id()));
        items.add(item);
        changed.run();
        return this;
    }
}
