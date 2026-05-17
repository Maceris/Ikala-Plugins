package com.ikalagaming.graphics.frontend.gui.data;

public class WindowClass {
    /**
     * User data. 0 is default class (unclassed). Windows of different classes cannot be docked with
     * each other.
     */
    public int classID;

    /**
     * Hint for the platform backend. -1: Use default, 0: request platform backend to not parent the
     * platform, != 0: request platform backend to create a parent-child relationship between the
     * platform windows.
     */
    public int parentViewportID;

    /**
     * ID of parent window for shortcut focus route evaluation, e.g. shortcut() call from parent
     * window will succeed when this window is focused.
     */
    public int focusRouteParentWindowID;

    /**
     * Viewport flags to set when a window of this class owns a viewport. This allows you to enforce
     * OS decoration or task bar icon, override the defaults on a per-window basis.
     *
     * @see com.ikalagaming.graphics.frontend.gui.flags.ViewportFlags
     */
    public int viewportFlagsOverrideSet;

    /**
     * Viewport flags to clear when a window of this class owns a viewport. This allows you to
     * enforce OS decoration or task bar icon, override the defaults on a per-window basis.
     *
     * @see com.ikalagaming.graphics.frontend.gui.flags.ViewportFlags
     */
    public int viewportFlagsOverrideClear;

    /**
     * TabItem flags to set when a window of this class gets submitted into a dock node tab bar. May
     * use with {@link com.ikalagaming.graphics.frontend.gui.flags.TabItemFlags#LEADING} or {@link
     * com.ikalagaming.graphics.frontend.gui.flags.TabItemFlags#TRAILING}.;
     *
     * @see com.ikalagaming.graphics.frontend.gui.flags.TabItemFlags
     */
    public int tabItemFlagsOverrideSet;

    /**
     * Dock node flags to set when a window of this class is hosted by a dock node (it doesn't have
     * to be selected!)
     *
     * @see com.ikalagaming.graphics.frontend.gui.flags.DockNodeFlags
     */
    public int dockNodeFlagsOverrideSet;

    /**
     * Set to true to enforce single floating windows of this class always having their own docking
     * node (equivalent of setting the global {@link IkIO#configDockingAlwaysTabBar}).
     */
    public boolean dockingAlwaysTabBar;

    /** Opaque data for platform backend to handle icons. */
    public Object platformIconData;
}
