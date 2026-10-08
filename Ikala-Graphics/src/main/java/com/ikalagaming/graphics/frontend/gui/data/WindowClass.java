package com.ikalagaming.graphics.frontend.gui.data;

import lombok.NonNull;

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

    /** Set to true to allow windows of this class to be docked/merged with an unclassed window. */
    public boolean dockingAllowUnclassed;

    /** Opaque data for platform backend to handle icons. */
    public Object platformIconData;

    public WindowClass() {
        classID = 0;
        parentViewportID = -1;
        focusRouteParentWindowID = 0;
        viewportFlagsOverrideSet = 0;
        viewportFlagsOverrideClear = 0;
        tabItemFlagsOverrideSet = 0;
        dockNodeFlagsOverrideSet = 0;
        dockingAlwaysTabBar = false;
        dockingAllowUnclassed = true;
        platformIconData = null;
    }

    /**
     * Copy all the values from another window class into this one.
     *
     * @param other The class to copy from.
     * @return This class, for chaining.
     */
    public WindowClass set(@NonNull WindowClass other) {
        classID = other.classID;
        parentViewportID = other.parentViewportID;
        focusRouteParentWindowID = other.focusRouteParentWindowID;
        viewportFlagsOverrideSet = other.viewportFlagsOverrideSet;
        viewportFlagsOverrideClear = other.viewportFlagsOverrideClear;
        tabItemFlagsOverrideSet = other.tabItemFlagsOverrideSet;
        dockNodeFlagsOverrideSet = other.dockNodeFlagsOverrideSet;
        dockingAlwaysTabBar = other.dockingAlwaysTabBar;
        dockingAllowUnclassed = other.dockingAllowUnclassed;
        platformIconData = other.platformIconData;
        return this;
    }

    /**
     * Reset all values to their defaults.
     *
     * @return This class, for chaining.
     */
    public WindowClass reset() {
        return set(new WindowClass());
    }
}
