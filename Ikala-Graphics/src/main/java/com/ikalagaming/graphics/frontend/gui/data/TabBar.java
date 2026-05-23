package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.flags.TabBarFlags;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class TabBar {
    public Window window;
    public List<TabItem> tabs;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.TabBarFlags
     */
    public int flags;

    public int id;
    public int selectedTabID;
    public int nextSelectedTabID;
    public int visibleTabID;
    public int currentFrameVisible;
    public int previousFrameVisible;
    public RectFloat bounds;
    public float currentTabContentHeight;
    public float previousTabContentHeight;
    public float widthAllTabs;
    public float widthAllTabsIdeal;
    public float scrollingAnimation;
    public float scrollingTarget;
    public float scrollingTargetDistanceUntilVisible;
    public float scrollingSpeed;
    public float scrollingRectMinX;
    public float scrollingRectMaxX;
    public float separatorMinX;
    public float separatorMaxX;
    public int reorderRequestTabID;
    public IkShort reorderRequestOffset;
    public IkByte beginCount;
    public boolean layoutRequested;
    public boolean visibleTabSubmitted;
    public boolean tabHadNewItemAdded;
    public IkShort tabsSubmittedThisFrame;
    public IkShort lastTabIndex;
    public float itemSpacingY;
    public Vector2f framePadding;
    public Vector2f backupCursorPosition;
    public StringBuilder tabNames;

    public TabBar() {
        window = null;
        tabs = new ArrayList<>();
        flags = TabBarFlags.NONE;
        id = 0;
        selectedTabID = 0;
        nextSelectedTabID = 0;
        visibleTabID = 0;
        currentFrameVisible = 0;
        previousFrameVisible = 0;
        bounds = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        currentTabContentHeight = 0.0f;
        previousTabContentHeight = 0.0f;
        widthAllTabs = 0.0f;
        widthAllTabsIdeal = 0.0f;
        scrollingAnimation = 0.0f;
        scrollingTarget = 0.0f;
        scrollingTargetDistanceUntilVisible = 0.0f;
        scrollingSpeed = 0.0f;
        scrollingRectMinX = 0.0f;
        scrollingRectMaxX = 0.0f;
        separatorMinX = 0.0f;
        separatorMaxX = 0.0f;
        reorderRequestTabID = 0;
        reorderRequestOffset = new IkShort();
        beginCount = new IkByte();
        layoutRequested = false;
        visibleTabSubmitted = false;
        tabHadNewItemAdded = false;
        tabsSubmittedThisFrame = new IkShort();
        lastTabIndex = new IkShort();
        itemSpacingY = 0.0f;
        framePadding = new Vector2f(0.0f, 0.0f);
        backupCursorPosition = new Vector2f(0.0f, 0.0f);
        tabNames = new StringBuilder();
    }
}
