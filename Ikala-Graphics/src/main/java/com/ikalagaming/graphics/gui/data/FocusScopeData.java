package com.ikalagaming.graphics.gui.data;

/** A focus scope, which groups items for navigation and shortcut routing. */
public class FocusScopeData {
    /** The ID of the focus scope. */
    public int id;

    /** The ID of the window the focus scope is in. */
    public int windowID;

    public FocusScopeData() {}

    /**
     * Create a focus scope.
     *
     * @param id The ID of the focus scope.
     * @param windowID The ID of the window the focus scope is in.
     */
    public FocusScopeData(int id, int windowID) {
        this.id = id;
        this.windowID = windowID;
    }
}
