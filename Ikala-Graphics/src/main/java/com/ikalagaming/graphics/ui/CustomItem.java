package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.style.StyleKey;

import lombok.NonNull;

/**
 * An item that draws itself with the DrawList but is still a real IkGui item, so it can be clicked,
 * reached with Tab or a gamepad, and activated like a button. Use it for things like inventory
 * slots and skinned buttons.
 *
 * <p>Subclasses only draw; the behavior comes from IkGui. Give it a fixed size or override {@link
 * #measure(LayoutContext, float[])}.
 *
 * @param <S> The item's own type, so chained setters keep it.
 */
public abstract class CustomItem<S extends CustomItem<S>> extends Node<S> {
    /** Runs on the render thread after the frame the item is activated in. */
    private Runnable onActivate;

    /** Whether the item takes navigation focus when its surface first gets focus. */
    private boolean autofocus;

    /**
     * Create an item.
     *
     * @param id The ID, which must be unique among its siblings.
     */
    protected CustomItem(@NonNull String id) {
        super(id);
    }

    /**
     * Set what happens when the item is activated, by a click, Space, Enter or the gamepad's accept
     * button. It runs on the render thread after the GUI is drawn, outside rendering.
     *
     * @param action The action, or null for none.
     * @return This item.
     */
    public S onActivate(Runnable action) {
        onActivate = action;
        return self();
    }

    /**
     * Take navigation focus when the surface first gets focus.
     *
     * @param focus Whether to take focus.
     * @return This item.
     */
    public S autofocus(boolean focus) {
        autofocus = focus;
        return self();
    }

    /**
     * Draw the item.
     *
     * @param drawList The draw list of the surface's window.
     * @param bounds Where to draw, in screen pixels.
     * @param state The interaction state this frame.
     */
    protected abstract void draw(
            @NonNull DrawList drawList, @NonNull RectFloat bounds, @NonNull ItemState state);

    @Override
    public String styleType() {
        return "custom";
    }

    /**
     * A style value for an interaction state, for drawing.
     *
     * @param key The property.
     * @param state The item's state this frame.
     * @return The value from the computed style, or null if the theme doesn't set it.
     */
    protected Object style(@NonNull StyleKey key, @NonNull ItemState state) {
        return style.get(key, state.styleStates());
    }

    /**
     * A color for an interaction state, for drawing.
     *
     * @param key A color property.
     * @param state The item's state this frame.
     * @param fallback The color to use if the theme doesn't set one.
     * @return The packed color.
     */
    protected int color(@NonNull StyleKey key, @NonNull ItemState state, int fallback) {
        return style(key, state) instanceof Integer color ? color : fallback;
    }

    @Override
    protected final void submit(@NonNull UiFrame frame) {
        final RectFloat bounds = new RectFloat(rect);
        final int id = IkGui.getID("item");
        IkGui.setCursorScreenPos(bounds.getLeft(), bounds.getTop());
        IkGuiInternal.itemSize(bounds.getWidth(), bounds.getHeight());
        if (!IkGuiInternal.itemAdd(bounds, id)) {
            return;
        }
        final IkBoolean hovered = new IkBoolean(false);
        final IkBoolean held = new IkBoolean(false);
        final boolean pressed = IkGuiInternal.buttonBehavior(bounds, id, hovered, held, 0);
        IkGuiInternal.renderNavCursor(bounds, id);
        draw(
                IkGui.getWindowDrawList(),
                bounds,
                new ItemState(hovered.get(), held.get(), IkGui.isItemFocused(), pressed));
        if (autofocus) {
            IkGui.setItemDefaultFocus();
        }
        if (pressed) {
            frame.fire(onActivate);
        }
    }
}
