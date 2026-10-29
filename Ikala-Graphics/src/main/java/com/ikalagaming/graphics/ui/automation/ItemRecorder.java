package com.ikalagaming.graphics.ui.automation;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.TestEngineHooks;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.LastItemData;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Records the IkGui items submitted each frame through the test engine hooks, so steps can find
 * items drawn in immediate mode by label. Only hooked into the IkGui context while runs are going,
 * since recording costs a little for every item. Render thread only.
 */
final class ItemRecorder implements TestEngineHooks {
    /** How many frames an item is kept after it was last submitted. */
    private static final int KEEP_FRAMES = 2;

    /** What was recorded about an item. */
    static final class Item {
        /** The item ID. */
        final int id;

        /** The label a widget reported, or null if it reported none. */
        String label;

        /** The window the item was submitted in. */
        Window window;

        /** The item's rectangle, in screen pixels. */
        final RectFloat rect = new RectFloat();

        /** The part of the rectangle inside the clip rectangle it was submitted with. */
        final RectFloat visible = new RectFloat();

        /** The status flags a widget reported, like checked. */
        int statusFlags;

        /** The frame it was last submitted in. */
        int frame;

        /**
         * Create an item.
         *
         * @param id The item ID.
         */
        Item(int id) {
            this.id = id;
        }

        /**
         * Whether part of the item is on screen.
         *
         * @return True if it isn't clipped away.
         */
        boolean isVisible() {
            return visible.getWidth() > 0 && visible.getHeight() > 0;
        }
    }

    /** The items by ID. */
    private final Map<Integer, Item> items = new HashMap<>();

    /**
     * Labels reported this frame before their item was added, by ID. Tabs report theirs first, as
     * Dear ImGui's do, so the label waits here for the add.
     */
    private final Map<Integer, String> early = new HashMap<>();

    /** The frame the early labels were reported in. */
    private int earlyFrame = -1;

    /** The context the hooks are installed in, or null. */
    private Context hooked;

    /**
     * Hook into the current IkGui context, if nothing else is hooked into it.
     *
     * @return True if recording.
     */
    boolean install() {
        Context context = IkGui.getContext();
        if (hooked == context) {
            return true;
        }
        if (context.testEngine != null && context.testEngine != this) {
            // Something else, like an IkGui test, is watching items
            return false;
        }
        uninstall();
        context.testEngine = this;
        context.testEngineHookItems = true;
        hooked = context;
        return true;
    }

    /** Stop recording, and forget the items. */
    void uninstall() {
        if (hooked != null && hooked.testEngine == this) {
            hooked.testEngine = null;
            hooked.testEngineHookItems = false;
        }
        hooked = null;
        items.clear();
        early.clear();
    }

    /**
     * Whether items are being recorded.
     *
     * @return True if hooked into the current context.
     */
    boolean isRecording() {
        return hooked != null && hooked == IkGui.getContext();
    }

    /** Forget items not submitted lately. Called once a frame. */
    void prune() {
        int oldest = IkGui.getContext().frameCount - KEEP_FRAMES;
        items.values().removeIf(item -> item.frame < oldest);
    }

    /**
     * The items submitted this frame or the one before, since windows drawn after the retained UI
     * haven't submitted theirs yet this frame.
     *
     * @return The items.
     */
    List<Item> recent() {
        int since = IkGui.getContext().frameCount - 1;
        List<Item> result = new ArrayList<>();
        for (Item item : items.values()) {
            if (item.frame >= since) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Look up an item by ID.
     *
     * @param id The item ID.
     * @return The item, or null if it wasn't submitted lately.
     */
    Item get(int id) {
        Item item = items.get(id);
        if (item == null || item.frame < IkGui.getContext().frameCount - 1) {
            return null;
        }
        return item;
    }

    @Override
    public void itemAdd(int id, @NonNull RectFloat bb, LastItemData itemData) {
        if (itemData == null && IkGuiInternal.findWindowByID(id) != null) {
            // Windows are registered as items too, but steps look for widgets
            return;
        }
        Context context = IkGui.getContext();
        Window window = context.windowCurrent;
        Item item = items.computeIfAbsent(id, Item::new);
        if (item.frame != context.frameCount) {
            // Most labels are reported after the item is added, so drop last frame's
            item.label = earlyFrame == context.frameCount ? early.remove(id) : null;
            item.statusFlags = 0;
        }
        item.window = window;
        item.rect.set(bb);
        item.visible.set(bb);
        if (window != null) {
            item.visible.clipWithFull(window.rectCurrentClip);
        }
        if (itemData != null) {
            item.statusFlags = itemData.statusFlags;
        }
        item.frame = context.frameCount;
    }

    @Override
    public void itemInfo(int id, String label, int statusFlags) {
        final int frame = IkGui.getContext().frameCount;
        Item item = items.get(id);
        if (item == null || item.frame != frame) {
            // Not added yet this frame: a tab, which is added after, or an item clipped away,
            // which never is. Its status flags are the item before's, so only the label is kept
            if (earlyFrame != frame) {
                early.clear();
                earlyFrame = frame;
            }
            if (label != null) {
                early.put(id, label);
            }
            return;
        }
        item.label = label;
        item.statusFlags = statusFlags;
    }
}
