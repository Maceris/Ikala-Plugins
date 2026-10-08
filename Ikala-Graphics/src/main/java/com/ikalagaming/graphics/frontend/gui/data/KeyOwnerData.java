package com.ikalagaming.graphics.frontend.gui.data;

/** Which item owns a key, used for key ownership aware input queries. */
public class KeyOwnerData {
    /** The owner for the current frame. */
    public int ownerCurr;

    /** The owner for the next frame. */
    public int ownerNext;

    /** Reading the key requires an explicit owner ID, until the end of the frame. */
    public boolean lockThisFrame;

    /** Reading the key requires an explicit owner ID, until the key is released. */
    public boolean lockUntilRelease;

    public KeyOwnerData() {
        ownerCurr = ownerNext = KeyRoutingData.KEY_OWNER_NO_OWNER;
    }
}
