package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.util.Rect;

import org.joml.Vector2f;

import java.util.ArrayList;

public class TabBar {
    private Window window;
    private ArrayList<TabItem> tabs;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.TabBarFlags
     */
    private int flags;

    private int ID;
    private int selectedTabID;
    private int nextSelectedTabID;
    private int visibleTabID;
    private int currentFrameVisible;
    private int previousFrameVisible;
    private Rect bounds;
    private float currentTabContentHeight;
    private float previousTabContentHeight;
    private float widthAllTabs;
    private float widthAllTabsIdeal;
    private float scrollingAnimation;
    private float scrollingTarget;
    private float scrollingTargetDistanceUntilVisible;
    private float scrollingSpeed;
    private float scrollingRectMinX;
    private float scrollingRectMaxX;
    private float separatorMinX;
    private float separatorMaxX;
    private int reorderRequestTabID;
    private IkShort reorderRequestOffset;
    private IkByte beginCount;
    private boolean layoutRequested;
    private boolean visibleTabSubmitted;
    private boolean tabHadNewItemAdded;
    private IkShort tabsSubmittedThisFrame;
    private IkShort lastTabIndex;
    private float itemSpacingY;
    private Vector2f framePadding;
    private Vector2f backupCursorPosition;
    private StringBuilder tabNames;
}
