package com.ikalagaming.graphics.frontend.gui.data;

public class DeactivatedItemData {
    public int id;
    public int elapsedFrame;
    public boolean hasBeenEditedBefore;
    public boolean isAlive;

    public DeactivatedItemData() {
        id = 0;
        elapsedFrame = 0;
        hasBeenEditedBefore = false;
        isAlive = false;
    }
}
