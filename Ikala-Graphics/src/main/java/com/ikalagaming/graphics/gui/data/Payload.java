package com.ikalagaming.graphics.gui.data;

/**
 * Data payload for drag and drop operations, see acceptDragDropPayload() and getDragDropPayload().
 *
 * <p>Unlike ImGui, the payload data is not copied, the object that was passed to
 * setDragDropPayload() is held directly. Pass a copy if the source may change while dragging.
 */
public class Payload {
    /** The payload data, which may be null. */
    public Object data;

    /**
     * The data type tag, a user-supplied string. Strings starting with '_' are reserved for IkGui
     * internal types.
     */
    public String dataType;

    /** Source item ID. */
    public int sourceID;

    /** Source parent ID, if available. */
    public int sourceParentID;

    /** The frame the data was last set, or -1 if no data has been set yet. */
    public int dataFrameCount;

    /**
     * Set when acceptDragDropPayload() was called and the mouse has been hovering the target item.
     */
    public boolean preview;

    /**
     * Set when acceptDragDropPayload() was called and the mouse button was released over the target
     * item.
     */
    public boolean delivery;

    public Payload() {
        clear();
    }

    /** Reset to the default empty state. */
    public void clear() {
        data = null;
        dataType = "";
        sourceID = 0;
        sourceParentID = 0;
        dataFrameCount = -1;
        preview = false;
        delivery = false;
    }

    /**
     * Fetch the data, cast to the requested type.
     *
     * @param <T> The type of data that is expected.
     * @return The payload data.
     * @throws ClassCastException If the data is not of the expected type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getData() {
        return (T) data;
    }

    /**
     * Check if the payload has data with the given type tag.
     *
     * @param type The type tag.
     * @return True if data has been set, with the given type tag.
     */
    public boolean isDataType(String type) {
        return dataFrameCount != -1 && dataType.equals(type);
    }

    /**
     * Whether the mouse has been hovering the target item that accepted this payload.
     *
     * @return True if previewing over the target.
     */
    public boolean isPreview() {
        return preview;
    }

    /**
     * Whether the mouse button was released over the target item, and the payload should be
     * delivered.
     *
     * @return True if the payload is being delivered.
     */
    public boolean isDelivery() {
        return delivery;
    }
}
