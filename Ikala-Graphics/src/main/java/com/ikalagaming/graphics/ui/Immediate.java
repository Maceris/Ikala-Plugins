package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.flags.ChildFlags;

import lombok.NonNull;

import java.util.function.Consumer;

/**
 * Plain IkGui calls inside a retained layout, for tool content like tables that is easier to write
 * in immediate mode. The calls run inside an IkGui child window at this node's rectangle, so they
 * can scroll and lay themselves out as usual.
 *
 * <p>It grows to fill its parent by default, since there is nothing to measure. Actions that should
 * run outside rendering, like loading files, go through {@link UiFrame#fire(Runnable)}.
 */
public class Immediate extends Node<Immediate> {
    /** The IkGui calls to make each frame. */
    private final Consumer<UiFrame> content;

    /** Whether to draw a border around the area. */
    private boolean border;

    /**
     * Create an immediate area that grows to fill its parent.
     *
     * @param id The ID, which must be unique among its siblings.
     * @param content The IkGui calls to make each frame, on the render thread.
     */
    public Immediate(@NonNull String id, @NonNull Consumer<UiFrame> content) {
        super(id);
        this.content = content;
        width(Sizing.grow());
        height(Sizing.grow());
    }

    /**
     * Draw a border around the area.
     *
     * @param show Whether to draw the border.
     * @return This node.
     */
    public Immediate border(boolean show) {
        border = show;
        return this;
    }

    @Override
    public String styleType() {
        return "immediate";
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        IkGuiStyler.Pushed pushed = IkGuiStyler.push(IkGuiStyler.Kind.CHILD, this, frame);
        IkGui.setCursorScreenPos(rect.getLeft(), rect.getTop());
        boolean open =
                IkGui.beginChild(
                        "immediate",
                        rect.getWidth(),
                        rect.getHeight(),
                        border ? ChildFlags.BORDERS : ChildFlags.NONE);
        // The child window has read its colors, the content uses its own
        pushed.pop();
        if (open) {
            content.accept(frame);
        }
        IkGui.endChild();
    }
}
