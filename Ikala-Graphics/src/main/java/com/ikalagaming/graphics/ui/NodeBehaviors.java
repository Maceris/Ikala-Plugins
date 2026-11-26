package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;

import lombok.NonNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What a node does beyond its own widget: a tooltip, a context menu, and dragging and dropping.
 * They act on the IkGui item the node submitted, so nodes that submit no item, like rows and
 * columns, can't have them.
 */
final class NodeBehaviors {
    /** The popup ID of the context menu, under the node's ID. */
    private static final String CONTEXT_POPUP = "context";

    /**
     * Dragging a value out of the node.
     *
     * @param type The payload type, which drop targets must ask for.
     * @param payload Makes the value being dragged.
     * @param label What shows beside the mouse while dragging.
     */
    record DragSource(@NonNull String type, @NonNull Supplier<?> payload, @NonNull String label) {}

    /**
     * Dropping a value onto the node.
     *
     * @param type The payload type it takes.
     * @param payloadClass The class the value must be.
     * @param onDrop What to do with a dropped value.
     */
    record DropTarget(
            @NonNull String type,
            @NonNull Class<?> payloadClass,
            @NonNull Consumer<Object> onDrop) {}

    /** What shows when the mouse rests on the item, or null. */
    String tooltip;

    /** The context menu's entries, empty for none. */
    List<MenuItem> contextMenu = List.of();

    /** Dragging out of the item, or null. */
    DragSource dragSource;

    /** Dropping onto the item, or null. */
    DropTarget dropTarget;

    /**
     * Act on the item just submitted. Called inside the node's ID.
     *
     * @param frame Details about this frame.
     */
    void apply(@NonNull UiFrame frame) {
        if (tooltip != null) {
            IkGui.setItemTooltip(tooltip);
        }
        if (!contextMenu.isEmpty() && IkGui.beginPopupContextItem(CONTEXT_POPUP)) {
            for (MenuItem item : contextMenu) {
                if (IkGui.menuItem(item.label())) {
                    frame.fire(item.action());
                }
            }
            IkGui.endPopup();
        }
        if (dragSource != null && IkGui.beginDragDropSource()) {
            IkGui.setDragDropPayload(dragSource.type(), dragSource.payload().get());
            IkGui.textUnformatted(dragSource.label());
            IkGui.endDragDropSource();
        }
        if (dropTarget != null && IkGui.beginDragDropTarget()) {
            final Object dropped = IkGui.acceptDragDropPayload(dropTarget.type());
            if (dropTarget.payloadClass().isInstance(dropped)) {
                frame.fire(() -> dropTarget.onDrop().accept(dropped));
            }
            IkGui.endDragDropTarget();
        }
    }
}
