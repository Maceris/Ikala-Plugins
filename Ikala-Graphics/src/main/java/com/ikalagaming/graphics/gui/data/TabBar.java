package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.flags.TabBarFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/** Storage for a tab bar, which persists between frames. */
public class TabBar {
    /** The window the tab bar is in. */
    public Window window;

    /** The tabs, in display order. */
    public final List<TabItem> tabs;

    /**
     * @see TabBarFlags
     */
    public int flags;

    /** Zero for tab bars used by docking. */
    public int id;

    /** The selected tab ID. */
    public int selectedTabID;

    /** The next selected tab ID, applied during the next layout. */
    public int nextSelectedTabID;

    /** A tab to scroll to during the next layout. */
    public int nextScrollToTabID;

    /** The tab whose contents are visible, which can differ from the selected one. */
    public int visibleTabID;

    /** The frame the tab bar was last submitted on. */
    public int currentFrameVisible;

    /** The frame the tab bar was submitted on before the current one. */
    public int previousFrameVisible;

    /** The area the tabs are drawn in. */
    public final RectFloat barRect;

    /** The width of the bar rect on the previous frame, to detect shrinking. */
    public float barRectPreviousWidth;

    /** The height of the contents of the visible tab this frame. */
    public float currentTabsContentsHeight;

    /** The height of the contents of the visible tab last frame. */
    public float previousTabsContentsHeight;

    /** Actual width of all tabs (locked during layout). */
    public float widthAllTabs;

    /** Ideal width if all tabs were visible and not clipped. */
    public float widthAllTabsIdeal;

    /** The current scrolling position, animated toward the target. */
    public float scrollingAnimation;

    /** The scrolling position we are moving toward. */
    public float scrollingTarget;

    /** How far the target tab is from being visible, used to decide whether to teleport. */
    public float scrollingTargetDistanceToVisibility;

    /** Scrolling speed in pixels per second. */
    public float scrollingSpeed;

    /** The left edge of the scrolling (central) section. */
    public float scrollingRectMinX;

    /** The right edge of the scrolling (central) section. */
    public float scrollingRectMaxX;

    /** The left edge of the separator line under the tabs. */
    public float separatorMinX;

    /** The right edge of the separator line under the tabs. */
    public float separatorMaxX;

    /** A tab that has requested to be moved, 0 if none. */
    public int reorderRequestTabID;

    /** How many positions the reorder request tab should move. */
    public int reorderRequestOffset;

    /** How many times the tab bar was begun this frame. */
    public int beginCount;

    /** Whether we need to lay out the tabs before the next tab item. */
    public boolean wantLayout;

    /** Whether the visible tab was submitted this frame. */
    public boolean visibleTabWasSubmitted;

    /** Set to true when a new tab item or button has been added to the tab bar. */
    public boolean tabsAddedNew;

    /** Whether the scrolling buttons are enabled. */
    public boolean scrollButtonEnabled;

    /** Number of tabs submitted this frame. */
    public int tabsActiveCount;

    /** Index of the last beginTabItem() tab, for use by endTabItem(). -1 if none. */
    public int lastTabItemIndex;

    /** Locked item spacing y for the frame. */
    public float itemSpacingY;

    /** Locked frame padding for the frame. */
    public final Vector2f framePadding;

    /** Cursor position before the tab bar, restored when appending to it again. */
    public final Vector2f backupCursorPosition;

    public TabBar() {
        window = null;
        tabs = new ArrayList<>();
        flags = TabBarFlags.NONE;
        id = 0;
        selectedTabID = 0;
        nextSelectedTabID = 0;
        nextScrollToTabID = 0;
        visibleTabID = 0;
        currentFrameVisible = -1;
        previousFrameVisible = -1;
        barRect = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        barRectPreviousWidth = 0.0f;
        currentTabsContentsHeight = 0.0f;
        previousTabsContentsHeight = 0.0f;
        widthAllTabs = 0.0f;
        widthAllTabsIdeal = 0.0f;
        scrollingAnimation = 0.0f;
        scrollingTarget = 0.0f;
        scrollingTargetDistanceToVisibility = 0.0f;
        scrollingSpeed = 0.0f;
        scrollingRectMinX = 0.0f;
        scrollingRectMaxX = 0.0f;
        separatorMinX = 0.0f;
        separatorMaxX = 0.0f;
        reorderRequestTabID = 0;
        reorderRequestOffset = 0;
        beginCount = 0;
        wantLayout = false;
        visibleTabWasSubmitted = false;
        tabsAddedNew = false;
        scrollButtonEnabled = false;
        tabsActiveCount = 0;
        lastTabItemIndex = -1;
        itemSpacingY = 0.0f;
        framePadding = new Vector2f(0.0f, 0.0f);
        backupCursorPosition = new Vector2f(0.0f, 0.0f);
    }
}
