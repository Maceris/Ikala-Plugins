package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.flags.ChildFlags;

import lombok.NonNull;
import org.joml.Vector2f;

/**
 * A scrolling view of one child, drawn as an IkGui child window, so it gets scrolling, a scrollbar,
 * and scrolling to the item that has keyboard or gamepad focus.
 *
 * <p>The child is laid out as wide as the view and at least as tall as it, and taller if its
 * content needs. A scroll grows to fill its parent by default; give it a fixed size or a grow with
 * a maximum to limit it.
 */
public class Scroll extends Container<Scroll> {
    /** Whether to draw a border around the view. */
    private boolean border;

    /**
     * Create a scroll that grows to fill its parent.
     *
     * @param id The ID, which must be unique among its siblings.
     */
    public Scroll(@NonNull String id) {
        super(id);
        width(Sizing.grow());
        height(Sizing.grow());
    }

    /**
     * Set the content, replacing any existing content.
     *
     * @param node The content, which must not have a parent.
     * @return This scroll.
     */
    public Scroll content(@NonNull Node<?> node) {
        for (Node<?> child : getChildren()) {
            remove(child);
        }
        return add(node);
    }

    @Override
    public Scroll add(@NonNull Node<?>... nodes) {
        if (children.size() + nodes.length > 1) {
            throw new IllegalArgumentException(this + " holds one child, put several in a column");
        }
        return super.add(nodes);
    }

    /**
     * Draw a border around the view.
     *
     * @param show Whether to draw the border.
     * @return This scroll.
     */
    public Scroll border(boolean show) {
        border = show;
        return this;
    }

    @Override
    public String styleType() {
        return "scroll";
    }

    @Override
    protected void submit(@NonNull UiFrame frame) {
        IkGuiStyler.Pushed pushed = IkGuiStyler.push(IkGuiStyler.Kind.CHILD, this, frame);
        IkGui.setCursorScreenPos(rect.getLeft(), rect.getTop());
        boolean open =
                IkGui.beginChild(
                        "scroll",
                        rect.getWidth(),
                        rect.getHeight(),
                        border ? ChildFlags.BORDERS : ChildFlags.NONE);
        // The child window has read its colors, its content uses their own
        pushed.pop();
        if (open) {
            if (!children.isEmpty()) {
                Node<?> content = children.getFirst();
                // The cursor already includes the scroll offset
                Vector2f start = IkGui.getCursorScreenPos();
                Vector2f view = IkGui.getContentRegionAvailable();
                float height = Math.max(view.y, content.fit[1]);
                frame.engine().layout(content, start.x, start.y, view.x, height, frame.context());
                content.submitTree(frame);
            }
        }
        IkGui.endChild();
    }
}
