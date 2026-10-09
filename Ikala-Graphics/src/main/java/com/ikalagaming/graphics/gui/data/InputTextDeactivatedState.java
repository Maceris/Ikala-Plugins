package com.ikalagaming.graphics.gui.data;

/**
 * Temporary storage for the text of a deactivating inputText(), while another input text is
 * stealing the active ID. This lets the deactivating widget apply its final value to the user
 * string on the frame it reports being deactivated.
 */
public class InputTextDeactivatedState {
    /** The widget ID owning the text state (which just got deactivated). */
    public int id;

    /** The frame after which this data is no longer valid. */
    public int elapseFrame;

    /** The text at the time of deactivation. */
    public String text = "";

    /** Clear the stored data. */
    public void clearFreeMemory() {
        id = 0;
        elapseFrame = 0;
        text = "";
    }
}
