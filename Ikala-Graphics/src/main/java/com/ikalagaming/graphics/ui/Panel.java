package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.Getter;
import lombok.NonNull;

/**
 * A titled window of retained UI that the user can move, resize, dock into a {@link DockSpace} and
 * drag out of the main window. IkGui saves where the user put it, so its anchors and sizing only
 * place it the first time it is ever shown, when it isn't docked by a layout.
 *
 * <p>The window's ID comes from the panel's ID alone, so the title can change without losing the
 * saved layout.
 */
public class Panel extends Surface {
    /** Window flags every panel uses. The content scrolls itself, through {@link Scroll}. */
    private static final int BASE_FLAGS =
            WindowFlags.NO_SCROLLBAR | WindowFlags.NO_SCROLL_WITH_MOUSE;

    /**
     * What the title bar or dock tab shows. -- GETTER -- What the title bar or dock tab shows.
     *
     * @return The title.
     */
    @Getter private volatile String title;

    /** Runs when the user closes the panel, or null for a panel without a close button. */
    private Runnable onClose;

    /** Cleared by IkGui when the close button is pressed. */
    private final IkBoolean open = new IkBoolean(true);

    /** Whether to focus the panel the next time it is drawn. */
    private volatile boolean focusRequested;

    /**
     * Create a panel with an empty column as its content.
     *
     * @param id The ID, unique among all surfaces and panels.
     * @param title What the title bar or dock tab shows.
     */
    public Panel(@NonNull String id, @NonNull String title) {
        super(id);
        this.title = title;
    }

    /**
     * Change what the title bar or dock tab shows. The saved layout is kept.
     *
     * @param newTitle The title.
     * @return This panel.
     */
    public Panel title(@NonNull String newTitle) {
        title = newTitle;
        return this;
    }

    /**
     * Give the panel a close button. Closing hides the panel, then runs the action on the render
     * thread, outside drawing.
     *
     * @param action What to do once the panel is hidden, like unchecking a menu item.
     * @return This panel.
     */
    public Panel onClose(@NonNull Runnable action) {
        onClose = action;
        return this;
    }

    /**
     * Focus the panel the next time it is drawn: a docked panel's tab is picked, a floating one
     * comes in front of the others. For showing what a panel holds after something opens in it.
     *
     * @return This panel.
     */
    public Panel bringToFront() {
        focusRequested = true;
        return this;
    }

    @Override
    int windowFlags() {
        return BASE_FLAGS | extraFlags();
    }

    @Override
    String windowName() {
        return title + windowID(getId());
    }

    /**
     * The part of a panel's window name that IkGui makes its ID from, which is also the name the
     * dock builder docks it by.
     *
     * @param panelID The panel's ID.
     * @return The ID part of the window name.
     */
    static String windowID(@NonNull String panelID) {
        return "###ui/" + panelID;
    }

    @Override
    void placeWindow(@NonNull RectFloat window) {
        // Only for a window IkGui has never seen: placing it later would undock it, and a window a
        // layout docks already has settings by now
        final int windowID = Hash.getID(windowName());
        if (IkGuiInternal.findWindowByID(windowID) == null
                && IkGuiInternal.findWindowSettingsByID(windowID) == null) {
            IkGui.setNextWindowPos(window.getLeft(), window.getTop(), Condition.FIRST_USE_EVER);
            IkGui.setNextWindowSize(
                    window.getWidth(), window.getHeight(), Condition.FIRST_USE_EVER);
        }
    }

    @Override
    boolean beginWindow(@NonNull UiFrame frame) {
        if (focusRequested) {
            focusRequested = false;
            IkGui.setNextWindowFocus();
        }
        if (onClose == null) {
            return IkGui.begin(windowName(), windowFlags());
        }
        open.set(true);
        final boolean shown = IkGui.begin(windowName(), open, windowFlags());
        if (!open.get()) {
            setVisible(false);
            frame.fire(onClose);
        }
        return shown;
    }
}
