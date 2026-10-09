package com.ikalagaming.graphics.gui.flags;

/** Flags for beginDragDropSource() and acceptDragDropPayload(). */
public class DragDropFlags {
    public static final int NONE = 0;

    // beginDragDropSource() flags
    /**
     * Disable the preview tooltip. By default, a successful call to beginDragDropSource() opens a
     * tooltip so you can display a preview or description of the source contents.
     */
    public static final int SOURCE_NO_PREVIEW_TOOLTIP = 1;

    /**
     * By default, when dragging we clear data so that isItemHovered() will return false, to avoid
     * subsequent user code submitting tooltips. This flag disables that so you can still call
     * isItemHovered() on the source item.
     */
    public static final int SOURCE_NO_DISABLE_HOVER = 1 << 1;

    /**
     * Disable the behavior that allows opening tree nodes and collapsing headers by holding over
     * them while dragging a source item.
     */
    public static final int SOURCE_NO_HOLD_TO_OPEN_OTHERS = 1 << 2;

    /**
     * Allow items such as text() and image() that have no unique identifier to be used as a drag
     * source, by manufacturing a temporary identifier based on their window-relative position. This
     * is extremely unusual, so it has to be explicit.
     */
    public static final int SOURCE_ALLOW_NULL_ID = 1 << 3;

    /**
     * External source (from outside IkGui), won't attempt to read current item/window info. Will
     * always return true. Only one external source can be active at a time.
     */
    public static final int SOURCE_EXTERN = 1 << 4;

    /**
     * Automatically expire the payload if the source stops being submitted (otherwise payloads
     * persist while being dragged).
     */
    public static final int PAYLOAD_AUTO_EXPIRE = 1 << 5;

    /** Hint that the payload may not be copied outside the current IkGui context. */
    public static final int PAYLOAD_NO_CROSS_CONTEXT = 1 << 6;

    /** Hint that the payload may not be copied outside the current process. */
    public static final int PAYLOAD_NO_CROSS_PROCESS = 1 << 7;

    // acceptDragDropPayload() flags
    /**
     * acceptDragDropPayload() will return the payload even before the mouse button is released. You
     * can then check getDragDropPayloadInfo().isDelivery() to test if the payload needs to be
     * delivered.
     */
    public static final int ACCEPT_BEFORE_DELIVERY = 1 << 10;

    /** Do not draw the default highlight rectangle when hovering over the target. */
    public static final int ACCEPT_NO_DRAW_DEFAULT_RECT = 1 << 11;

    /** Request hiding the beginDragDropSource() tooltip from the beginDragDropTarget() site. */
    public static final int ACCEPT_NO_PREVIEW_TOOLTIP = 1 << 12;

    /** The accepting item will render as if hovered. Useful for e.g. a button() as a target. */
    public static final int ACCEPT_DRAW_AS_HOVERED = 1 << 13;

    // Combined flags
    /** For peeking ahead and inspecting the payload before delivery. */
    public static final int ACCEPT_PEEK_ONLY = ACCEPT_BEFORE_DELIVERY | ACCEPT_NO_DRAW_DEFAULT_RECT;

    /** Private constructor so this is not instantiated. */
    private DragDropFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
