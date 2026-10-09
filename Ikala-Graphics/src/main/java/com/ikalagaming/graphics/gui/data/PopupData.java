package com.ikalagaming.graphics.gui.data;

import org.joml.Vector2f;

/** Storage for one entry in the popup stacks. */
public class PopupData {
    /** The ID of the popup, set on openPopup(). */
    public int popupID;

    /**
     * The window for the popup, resolved on beginPopup(). This may stay null if the user never
     * calls beginPopup() after opening it.
     */
    public Window window;

    /** The focused window when the popup was opened, which focus is restored to on close. */
    public Window restoreNavWindow;

    /**
     * The navigation layer of the parent window, resolved on beginPopup(). -1 if not resolved yet.
     */
    public int parentNavLayer;

    /** The frame the popup was opened on, set on openPopup(). -1 if not set. */
    public int openFrameCount;

    /**
     * The ID on top of the parent windows ID stack when the popup was opened, set on openPopup().
     */
    public int openParentID;

    /**
     * The preferred popup position, set on openPopup(). This is typically the mouse position when
     * opening with the mouse.
     */
    public final Vector2f preferredPosition;

    /** A copy of the mouse position at the time of opening the popup, set on openPopup(). */
    public final Vector2f mousePosition;

    public PopupData() {
        popupID = 0;
        window = null;
        restoreNavWindow = null;
        parentNavLayer = -1;
        openFrameCount = -1;
        openParentID = 0;
        preferredPosition = new Vector2f();
        mousePosition = new Vector2f();
    }
}
