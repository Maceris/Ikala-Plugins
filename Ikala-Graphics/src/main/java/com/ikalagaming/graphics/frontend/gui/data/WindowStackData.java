package com.ikalagaming.graphics.frontend.gui.data;

/** Information stored for each window in the begin stack, so it can be restored when it ends. */
public class WindowStackData {
    public Window window;
    public final LastItemData parentLastItemDataBackup;
    public final ErrorRecoveryState stackSizesInBegin;
    public boolean disabledOverrideReenable;
    public float disabledOverrideReenableBackup;

    public WindowStackData(Window window) {
        this.window = window;
        parentLastItemDataBackup = new LastItemData();
        stackSizesInBegin = new ErrorRecoveryState();
        disabledOverrideReenable = false;
        disabledOverrideReenableBackup = 0.0f;
    }
}
