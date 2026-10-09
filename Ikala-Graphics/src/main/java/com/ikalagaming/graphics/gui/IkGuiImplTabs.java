package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.enums.*;
import com.ikalagaming.graphics.gui.flags.*;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.MathUtil;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.Comparator;
import java.util.List;

/** Tab bars and tab items. */
@Slf4j
class IkGuiImplTabs {

    /** Index of the leading section. */
    private static final int SECTION_LEADING = 0;

    /** Index of the central (scrolling) section. */
    private static final int SECTION_CENTRAL = 1;

    /** Index of the trailing section. */
    private static final int SECTION_TRAILING = 2;

    /** Sort tabs by section, then by their index during layout. */
    private static final Comparator<TabItem> COMPARE_BY_SECTION =
            Comparator.<TabItem>comparingInt(IkGuiImplTabs::getSectionIndex)
                    .thenComparingInt(tab -> tab.indexDuringLayout);

    /** Sort tabs by the order they were submitted. */
    private static final Comparator<TabItem> COMPARE_BY_BEGIN_ORDER =
            Comparator.comparingInt(tab -> tab.beginOrder);

    static Context context;

    /** Layout data for one section of a tab bar (leading, central, trailing). */
    private static class TabBarSection {
        /** Number of tabs in this section. */
        int tabCount;

        /** Sum of the width of tabs in this section (after shrinking down). */
        float width;

        /** Sum of the width of the tabs, if they were shrunk down to the minimum width. */
        float widthAfterShrinkMinWidth;

        /** Horizontal spacing at the end of the section. */
        float spacing;
    }

    // ---------------------------------------------------------------------------------------
    // Tab bars
    // ---------------------------------------------------------------------------------------

    /**
     * Create and append into a tab bar.
     *
     * @param stringID The string ID of the tab bar.
     * @param tabBarFlags Tab bar flags.
     * @return True if the tab bar is visible, and endTabBar() needs to be called.
     * @see TabBarFlags
     */
    public static boolean beginTabBar(@NonNull String stringID, int tabBarFlags) {
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return false;
        }

        final int id = window.getID(stringID);
        final TabBar tabBar = context.tabBars.computeIfAbsent(id, key -> new TabBar());
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat barBB =
                new RectFloat(
                        x,
                        y,
                        window.rectWork.getRight(),
                        y
                                + IkGuiInternal.getFontSize()
                                + context.style.variable.framePadding.y * 2);
        tabBar.id = id;
        tabBar.separatorMinX = barBB.getLeft() - IkGuiInternal.truncate(window.padding.x * 0.5f);
        tabBar.separatorMaxX = barBB.getRight() + IkGuiInternal.truncate(window.padding.x * 0.5f);
        tabBarFlags |= TabBarFlags.INTERNAL_IS_FOCUSED;
        return beginTabBarEx(tabBar, barBB, tabBarFlags);
    }

    /**
     * Begin a tab bar, given the tab bar storage and bounding box.
     *
     * @param tabBar The tab bar.
     * @param barBB The bounding box of the tab bar.
     * @param tabBarFlags Tab bar flags.
     * @return True if the tab bar is visible, and endTabBar() needs to be called.
     */
    static boolean beginTabBarEx(
            @NonNull TabBar tabBar, @NonNull RectFloat barBB, int tabBarFlags) {
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return false;
        }
        if (tabBar.id == 0) {
            IkGuiImplDebugTools.reportError(log, "Tab bars require a non-zero ID");
            return false;
        }
        if ((tabBarFlags & TabBarFlags.INTERNAL_DOCK_NODE) == 0) {
            IkGuiInternal.pushOverrideID(tabBar.id);
        }

        // Add to the stack
        context.currentTabBarStack.add(tabBar);
        context.currentTabBar = tabBar;
        tabBar.window = window;

        // Append with multiple beginTabBar()/endTabBar() pairs
        tabBar.backupCursorPosition.set(window.cursorPosition);
        if (tabBar.currentFrameVisible == context.frameCount) {
            window.cursorPosition.set(
                    tabBar.barRect.getLeft(), tabBar.barRect.getBottom() + tabBar.itemSpacingY);
            tabBar.beginCount++;
            return true;
        }

        // Ensure correct ordering when toggling the REORDERABLE flag, or when a new tab was added
        // while not reorderable
        if (((tabBarFlags & TabBarFlags.REORDERABLE) != (tabBar.flags & TabBarFlags.REORDERABLE)
                        || (tabBar.tabsAddedNew && (tabBarFlags & TabBarFlags.REORDERABLE) == 0))
                && (tabBarFlags & TabBarFlags.INTERNAL_DOCK_NODE) == 0) {
            tabBar.tabs.sort(COMPARE_BY_BEGIN_ORDER);
        }
        tabBar.tabsAddedNew = false;

        // Flags
        if ((tabBarFlags & TabBarFlags.FITTING_POLICY_MASK) == 0) {
            tabBarFlags |= TabBarFlags.FITTING_POLICY_DEFAULT;
        }

        final StyleVariables style = context.style.variable;
        tabBar.flags = tabBarFlags;
        tabBar.barRect.set(barBB);
        // Layout will be done on the first call to tabItemEx()
        tabBar.wantLayout = true;
        tabBar.previousFrameVisible = tabBar.currentFrameVisible;
        tabBar.currentFrameVisible = context.frameCount;
        tabBar.previousTabsContentsHeight = tabBar.currentTabsContentsHeight;
        tabBar.currentTabsContentsHeight = 0.0f;
        tabBar.itemSpacingY = style.itemSpacing.y;
        tabBar.framePadding.set(style.framePadding);
        tabBar.tabsActiveCount = 0;
        tabBar.lastTabItemIndex = -1;
        tabBar.beginCount = 1;

        // Set the cursor position in a way which is only used in the off chance the user
        // erroneously submits items before beginTabItem(): items will overlap
        window.cursorPosition.set(
                tabBar.barRect.getLeft(), tabBar.barRect.getBottom() + tabBar.itemSpacingY);

        // Draw the separator. It would be misleading to draw this in endTabBar() suggesting that it
        // may be drawn over tabs, as tab bars are appendable.
        if (style.tabBarBorderSize > 0.0f) {
            final int color =
                    IkGuiImplUtils.getColorWithGlobalAlpha(
                            (tabBarFlags & TabBarFlags.INTERNAL_IS_FOCUSED) != 0
                                    ? ColorType.TAB_SELECTED
                                    : ColorType.TAB_DIMMED_SELECTED);
            final float y = tabBar.barRect.getBottom();
            window.drawList.addRectFilled(
                    tabBar.separatorMinX,
                    y - style.tabBarBorderSize,
                    tabBar.separatorMaxX,
                    y,
                    color);
        }
        return true;
    }

    /** End a tab bar, only call this if beginTabBar() returned true. */
    public static void endTabBar() {
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return;
        }

        final TabBar tabBar = context.currentTabBar;
        if (tabBar == null) {
            IkGuiImplDebugTools.reportError(log, "Mismatched beginTabBar()/endTabBar()!");
            return;
        }

        // Fallback in case no tab items have been submitted
        if (tabBar.wantLayout) {
            tabBarLayout(tabBar);
        }

        // Restore the last visible height if no tab is visible, this reduces vertical
        // flicker/movement when a tab gets removed without calling setTabItemClosed()
        final boolean tabBarAppearing = tabBar.previousFrameVisible + 1 < context.frameCount;
        if (tabBar.visibleTabWasSubmitted || tabBar.visibleTabID == 0 || tabBarAppearing) {
            tabBar.currentTabsContentsHeight =
                    Math.max(
                            window.cursorPosition.y - tabBar.barRect.getBottom(),
                            tabBar.currentTabsContentsHeight);
            window.cursorPosition.y = tabBar.barRect.getBottom() + tabBar.currentTabsContentsHeight;
        } else {
            window.cursorPosition.y =
                    tabBar.barRect.getBottom() + tabBar.previousTabsContentsHeight;
        }
        if (tabBar.beginCount > 1) {
            window.cursorPosition.set(tabBar.backupCursorPosition);
        }

        tabBar.lastTabItemIndex = -1;
        if ((tabBar.flags & TabBarFlags.INTERNAL_DOCK_NODE) == 0) {
            IkGuiImplUtils.popID();
        }

        final List<TabBar> stack = context.currentTabBarStack;
        stack.removeLast();
        context.currentTabBar = stack.isEmpty() ? null : stack.getLast();
    }

    // ---------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------

    /**
     * Find which section a tab is in.
     *
     * @param tab The tab.
     * @return The section index.
     */
    private static int getSectionIndex(@NonNull TabItem tab) {
        if ((tab.flags & TabItemFlags.LEADING) != 0) {
            return SECTION_LEADING;
        }
        if ((tab.flags & TabItemFlags.TRAILING) != 0) {
            return SECTION_TRAILING;
        }
        return SECTION_CENTRAL;
    }

    /**
     * Scrolling only happens in the central section (leading/trailing sections don't scroll).
     *
     * @param tabBar The tab bar.
     * @param sections The layout sections.
     * @return The width available for scrolling tabs.
     */
    private static float calcScrollableWidth(
            @NonNull TabBar tabBar, @NonNull TabBarSection[] sections) {
        return tabBar.barRect.getWidth()
                - sections[SECTION_LEADING].width
                - sections[SECTION_TRAILING].width
                - sections[SECTION_CENTRAL].spacing;
    }

    /**
     * Lay out the tabs. This is called once a frame by the first tab item (or endTabBar()). We
     * don't do this in beginTabBar() so the user has a chance to call setTabItemClosed().
     *
     * @param tabBar The tab bar.
     */
    private static void tabBarLayout(@NonNull TabBar tabBar) {
        final StyleVariables style = context.style.variable;
        tabBar.wantLayout = false;

        // Track the selected tab when resizing our parent down
        final boolean scrollToSelectedTab = tabBar.barRectPreviousWidth > tabBar.barRect.getWidth();
        tabBar.barRectPreviousWidth = tabBar.barRect.getWidth();

        // Garbage collect by compacting the list, and detect if we need to sort the tab list (e.g.
        // in the rare case where a tab changed section)
        final TabBarSection[] sections = {
            new TabBarSection(), new TabBarSection(), new TabBarSection()
        };
        boolean needSortBySection = false;
        final List<TabItem> tabs = tabBar.tabs;
        for (int i = 0; i < tabs.size(); ) {
            final TabItem tab = tabs.get(i);
            if (tab.lastFrameVisible < tabBar.previousFrameVisible || tab.wantClose) {
                // Remove the tab
                if (tabBar.visibleTabID == tab.id) {
                    tabBar.visibleTabID = 0;
                }
                if (tabBar.selectedTabID == tab.id) {
                    tabBar.selectedTabID = 0;
                }
                if (tabBar.nextSelectedTabID == tab.id) {
                    tabBar.nextSelectedTabID = 0;
                }
                tabs.remove(i);
                continue;
            }
            tab.indexDuringLayout = i;

            // We will need sorting if tabs have changed section
            final int currentSection = getSectionIndex(tab);
            if (i > 0) {
                final int previousSection = getSectionIndex(tabs.get(i - 1));
                if (currentSection == SECTION_LEADING && previousSection != SECTION_LEADING) {
                    needSortBySection = true;
                }
                if (previousSection == SECTION_TRAILING && currentSection != SECTION_TRAILING) {
                    needSortBySection = true;
                }
            }
            sections[currentSection].tabCount++;
            ++i;
        }
        if (needSortBySection) {
            tabs.sort(COMPARE_BY_SECTION);
        }

        // Calculate the spacing between sections
        final float tabSpacing = style.itemInnerSpacing.x;
        sections[SECTION_LEADING].spacing =
                sections[SECTION_LEADING].tabCount > 0
                                && (sections[SECTION_CENTRAL].tabCount
                                                + sections[SECTION_TRAILING].tabCount)
                                        > 0
                        ? tabSpacing
                        : 0.0f;
        sections[SECTION_CENTRAL].spacing =
                sections[SECTION_CENTRAL].tabCount > 0 && sections[SECTION_TRAILING].tabCount > 0
                        ? tabSpacing
                        : 0.0f;

        // Set up the next selected tab
        int scrollToTabID = 0;
        if (tabBar.nextScrollToTabID != 0) {
            scrollToTabID = tabBar.nextScrollToTabID;
            tabBar.nextScrollToTabID = 0;
        }
        if (tabBar.nextSelectedTabID != 0) {
            tabBar.selectedTabID = tabBar.nextSelectedTabID;
            tabBar.nextSelectedTabID = 0;
            scrollToTabID = tabBar.selectedTabID;
        }

        // Process order change requests (we could probably process them when requested, but it's
        // saner to do it in a single spot)
        if (tabBar.reorderRequestTabID != 0) {
            if (tabBarProcessReorder(tabBar)
                    && tabBar.reorderRequestTabID == tabBar.selectedTabID) {
                scrollToTabID = tabBar.reorderRequestTabID;
            }
            tabBar.reorderRequestTabID = 0;
        }

        // Tab list popup (will alter the bar rect and therefore the available width!)
        if ((tabBar.flags & TabBarFlags.TAB_LIST_POPUP_BUTTON) != 0) {
            final TabItem tabToSelect = tabBarTabListPopupButton(tabBar);
            if (tabToSelect != null) {
                tabBar.selectedTabID = tabToSelect.id;
                scrollToTabID = tabToSelect.id;
            }
        }

        // Leading/trailing tabs will be shrunk only if central ones aren't visible anymore, so lay
        // out the shrink data as: leading, trailing, central (whereas our tabs are stored as:
        // leading, central, trailing)
        final int[] shrinkBufferIndexes = {
            0,
            sections[SECTION_LEADING].tabCount + sections[SECTION_TRAILING].tabCount,
            sections[SECTION_LEADING].tabCount
        };
        final List<ShrinkWidthItem> shrinkBuffer = context.shrinkWidthBuffer;
        while (shrinkBuffer.size() < tabs.size()) {
            shrinkBuffer.add(new ShrinkWidthItem());
        }

        // Minimum shrink width
        final float shrinkMinWidth =
                (tabBar.flags & TabBarFlags.FITTING_POLICY_MIXED) != 0
                        ? style.tabMinWidthShrink
                        : 1.0f;

        // Compute ideal tab widths and store them in the shrink buffer
        TabItem mostRecentlySelectedTab = null;
        int currentSectionIndex = -1;
        boolean foundSelectedTabID = false;
        for (int i = 0; i < tabs.size(); ++i) {
            final TabItem tab = tabs.get(i);
            if ((mostRecentlySelectedTab == null
                            || mostRecentlySelectedTab.lastFrameSelected < tab.lastFrameSelected)
                    && (tab.flags & TabItemFlags.INTERNAL_BUTTON) == 0) {
                mostRecentlySelectedTab = tab;
            }
            if (tab.id == tabBar.selectedTabID) {
                foundSelectedTabID = true;
            }

            // Refresh the tab width immediately, otherwise changes of style would noticeably lag
            final String tabName = tabBarGetTabName(tab);
            final boolean hasCloseButtonOrUnsavedMarker =
                    (tab.flags & TabItemFlags.INTERNAL_NO_CLOSE_BUTTON) == 0
                            || (tab.flags & TabItemFlags.UNSAVED_DOCUMENT) != 0;
            tab.contentWidth =
                    tab.requestedWidth >= 0.0f
                            ? tab.requestedWidth
                            : tabItemCalcSize(
                                            tabName, hasCloseButtonOrUnsavedMarker, new Vector2f())
                                    .x;
            if ((tab.flags & TabItemFlags.INTERNAL_BUTTON) == 0) {
                tab.contentWidth = Math.max(tab.contentWidth, style.tabMinWidthBase);
            }

            final int sectionIndex = getSectionIndex(tab);
            final TabBarSection section = sections[sectionIndex];
            final float spacing = sectionIndex == currentSectionIndex ? tabSpacing : 0.0f;
            section.width += tab.contentWidth + spacing;
            section.widthAfterShrinkMinWidth +=
                    Math.min(tab.contentWidth, shrinkMinWidth) + spacing;
            currentSectionIndex = sectionIndex;

            // Store data so we can build an array sorted by width if we need to shrink tabs down
            final ShrinkWidthItem shrinkItem =
                    shrinkBuffer.get(shrinkBufferIndexes[sectionIndex]++);
            shrinkItem.index = i;
            shrinkItem.width = tab.contentWidth;
            shrinkItem.initialWidth = tab.contentWidth;
            tab.width = Math.max(tab.contentWidth, 1.0f);
        }

        // Compute the total ideal width (used for e.g. auto-resizing a window)
        float widthAllTabsAfterMinWidthShrink = 0.0f;
        tabBar.widthAllTabsIdeal = 0.0f;
        for (TabBarSection section : sections) {
            tabBar.widthAllTabsIdeal += section.width + section.spacing;
            widthAllTabsAfterMinWidthShrink += section.widthAfterShrinkMinWidth + section.spacing;
        }

        // Horizontal scrolling buttons, which alter the bar rect
        final boolean canScroll =
                (tabBar.flags
                                & (TabBarFlags.FITTING_POLICY_SCROLL
                                        | TabBarFlags.FITTING_POLICY_MIXED))
                        != 0;
        final float widthAllTabsToUseForScroll =
                (tabBar.flags & TabBarFlags.FITTING_POLICY_SCROLL) != 0
                        ? tabBar.widthAllTabs
                        : widthAllTabsAfterMinWidthShrink;
        tabBar.scrollButtonEnabled =
                widthAllTabsToUseForScroll > tabBar.barRect.getWidth()
                        && tabs.size() > 1
                        && (tabBar.flags & TabBarFlags.NO_TAB_LIST_SCROLLING_BUTTONS) == 0
                        && canScroll;
        if (tabBar.scrollButtonEnabled) {
            final TabItem scrollAndSelectTab = tabBarScrollingButtons(tabBar);
            if (scrollAndSelectTab != null) {
                if ((scrollAndSelectTab.flags & TabItemFlags.INTERNAL_BUTTON) == 0) {
                    tabBar.selectedTabID = scrollAndSelectTab.id;
                }
                scrollToTabID = scrollAndSelectTab.id;
            }
        }
        if (scrollToTabID == 0 && scrollToSelectedTab) {
            scrollToTabID = tabBar.selectedTabID;
        }

        // Shrink widths if full tabs don't fit in their allocated space
        final float section0Width =
                sections[SECTION_LEADING].width + sections[SECTION_LEADING].spacing;
        final float section1Width =
                sections[SECTION_CENTRAL].width + sections[SECTION_CENTRAL].spacing;
        final float section2Width =
                sections[SECTION_TRAILING].width + sections[SECTION_TRAILING].spacing;
        final boolean centralSectionIsVisible =
                (section0Width + section2Width) < tabBar.barRect.getWidth();
        final float widthExcess;
        if (centralSectionIsVisible) {
            // Excess used to shrink the central section
            widthExcess =
                    Math.max(
                            section1Width
                                    - (tabBar.barRect.getWidth() - section0Width - section2Width),
                            0.0f);
        } else {
            // Excess used to shrink the leading/trailing sections
            widthExcess = (section0Width + section2Width) - tabBar.barRect.getWidth();
        }

        // With the scroll policy, we only shrink leading/trailing if the central section is not
        // visible anymore
        final boolean canShrink =
                (tabBar.flags
                                & (TabBarFlags.FITTING_POLICY_SHRINK
                                        | TabBarFlags.FITTING_POLICY_MIXED))
                        != 0;
        if (widthExcess >= 1.0f && (canShrink || !centralSectionIsVisible)) {
            final int shrinkDataCount =
                    centralSectionIsVisible
                            ? sections[SECTION_CENTRAL].tabCount
                            : sections[SECTION_LEADING].tabCount
                                    + sections[SECTION_TRAILING].tabCount;
            final int shrinkDataOffset =
                    centralSectionIsVisible
                            ? sections[SECTION_LEADING].tabCount
                                    + sections[SECTION_TRAILING].tabCount
                            : 0;
            IkGuiInternal.shrinkWidths(
                    shrinkBuffer, shrinkDataOffset, shrinkDataCount, widthExcess, shrinkMinWidth);

            // Apply the shrunk values to tabs and sections
            for (int i = shrinkDataOffset; i < shrinkDataOffset + shrinkDataCount; ++i) {
                final ShrinkWidthItem shrinkItem = shrinkBuffer.get(i);
                final TabItem tab = tabs.get(shrinkItem.index);
                float shrunkWidth = IkGuiInternal.truncate(shrinkItem.width);
                if (shrunkWidth < 0.0f) {
                    continue;
                }
                shrunkWidth = Math.max(1.0f, shrunkWidth);
                sections[getSectionIndex(tab)].width -= tab.width - shrunkWidth;
                tab.width = shrunkWidth;
            }
        }

        // Lay out all active tabs
        int sectionTabIndex = 0;
        float tabOffset = 0.0f;
        tabBar.widthAllTabs = 0.0f;
        for (int sectionIndex = 0; sectionIndex < 3; ++sectionIndex) {
            final TabBarSection section = sections[sectionIndex];
            if (sectionIndex == SECTION_TRAILING) {
                tabOffset =
                        Math.min(
                                Math.max(0.0f, tabBar.barRect.getWidth() - section.width),
                                tabOffset);
            }
            for (int i = 0; i < section.tabCount; ++i) {
                final TabItem tab = tabs.get(sectionTabIndex + i);
                tab.offset = tabOffset;
                tabOffset += tab.width + (i < section.tabCount - 1 ? tabSpacing : 0.0f);
            }
            tabBar.widthAllTabs += Math.max(section.width + section.spacing, 0.0f);
            tabOffset += section.spacing;
            sectionTabIndex += section.tabCount;
        }

        // If we have lost the selected tab, select the next most recently active one
        final boolean tabBarAppearing = tabBar.previousFrameVisible + 1 < context.frameCount;
        if (!foundSelectedTabID && !tabBarAppearing) {
            tabBar.selectedTabID = 0;
        }
        if (tabBar.selectedTabID == 0
                && tabBar.nextSelectedTabID == 0
                && mostRecentlySelectedTab != null) {
            tabBar.selectedTabID = mostRecentlySelectedTab.id;
            scrollToTabID = tabBar.selectedTabID;
        }

        // Lock in the visible tab
        tabBar.visibleTabID = tabBar.selectedTabID;
        tabBar.visibleTabWasSubmitted = false;

        // Ctrl+Tab can override the visible tab temporarily
        if (context.navWindowingTarget != null
                && context.navWindowingTarget.dockNode != null
                && context.navWindowingTarget.dockNode.tabBar == tabBar) {
            tabBar.visibleTabID = context.navWindowingTarget.idTab;
            scrollToTabID = tabBar.visibleTabID;
        }

        // Apply requests
        if (scrollToTabID != 0) {
            tabBarScrollToTab(tabBar, scrollToTabID, sections);
        } else if (tabBar.scrollButtonEnabled
                && IkGuiInternal.isMouseHoveringRect(
                        tabBar.barRect.getLeft(),
                        tabBar.barRect.getTop(),
                        tabBar.barRect.getRight(),
                        tabBar.barRect.getBottom(),
                        true)
                && IkGuiInternal.isWindowContentHoverable(
                        context.windowCurrent, HoveredFlags.NONE)) {
            final float wheel =
                    context.io.mouseWheelRequestAxisSwap
                            ? context.io.mouseWheel
                            : context.io.mouseWheelH;
            final Key wheelKey =
                    context.io.mouseWheelRequestAxisSwap ? Key.MOUSE_WHEEL_Y : Key.MOUSE_WHEEL_X;
            if (IkGuiImplKeys.testKeyOwner(wheelKey, tabBar.id) && wheel != 0.0f) {
                final float scrollStep = wheel * calcScrollableWidth(tabBar, sections) / 3.0f;
                tabBar.scrollingTargetDistanceToVisibility = 0.0f;
                tabBar.scrollingTarget =
                        tabBarScrollClamp(tabBar, tabBar.scrollingTarget - scrollStep);
            }
            IkGuiImplKeys.setKeyOwner(wheelKey, tabBar.id, InputFlags.NONE);
        }

        // Update scrolling
        tabBar.scrollingAnimation = tabBarScrollClamp(tabBar, tabBar.scrollingAnimation);
        tabBar.scrollingTarget = tabBarScrollClamp(tabBar, tabBar.scrollingTarget);
        if (tabBar.scrollingAnimation != tabBar.scrollingTarget) {
            // The scrolling speed adjusts itself so we can always reach our target in 1/3 seconds,
            // and we teleport if we are aiming far off the visible line
            final float fontSize = IkGuiInternal.getFontSize();
            tabBar.scrollingSpeed = Math.max(tabBar.scrollingSpeed, 70.0f * fontSize);
            tabBar.scrollingSpeed =
                    Math.max(
                            tabBar.scrollingSpeed,
                            Math.abs(tabBar.scrollingTarget - tabBar.scrollingAnimation) / 0.3f);
            final boolean teleport =
                    tabBarAppearing
                            || tabBar.scrollingTargetDistanceToVisibility > 10.0f * fontSize;
            if (teleport) {
                tabBar.scrollingAnimation = tabBar.scrollingTarget;
            } else {
                tabBar.scrollingAnimation =
                        linearSweep(
                                tabBar.scrollingAnimation,
                                tabBar.scrollingTarget,
                                context.io.deltaTime / 1000.0f * tabBar.scrollingSpeed);
            }
        } else {
            tabBar.scrollingSpeed = 0.0f;
        }
        tabBar.scrollingRectMinX =
                tabBar.barRect.getLeft()
                        + sections[SECTION_LEADING].width
                        + sections[SECTION_LEADING].spacing;
        tabBar.scrollingRectMaxX =
                tabBar.barRect.getRight()
                        - sections[SECTION_TRAILING].width
                        - sections[SECTION_CENTRAL].spacing;

        // Actual layout in the host window (we don't do it in beginTabBar() so as not to waste an
        // extra frame)
        final Window window = context.windowCurrent;
        window.cursorPosition.set(tabBar.barRect.getLeft(), tabBar.barRect.getTop());
        IkGuiInternal.itemSize(
                tabBar.widthAllTabs, tabBar.barRect.getHeight(), tabBar.framePadding.y);
        window.cursorIdealMaxPosition.x =
                Math.max(
                        window.cursorIdealMaxPosition.x,
                        tabBar.barRect.getLeft() + tabBar.widthAllTabsIdeal);
    }

    /**
     * Move a value toward a target by at most a certain speed.
     *
     * @param current The current value.
     * @param target The target value.
     * @param speed The maximum amount to move.
     * @return The new value.
     */
    private static float linearSweep(float current, float target, float speed) {
        if (current < target) {
            return Math.min(current + speed, target);
        }
        if (current > target) {
            return Math.max(current - speed, target);
        }
        return current;
    }

    /**
     * Calculate the ID of a tab. Docked windows use their own tab ID, other tabs use the ID stack.
     *
     * @param label The label of the tab.
     * @param dockedWindow The docked window, may be null.
     * @return The tab ID.
     */
    private static int tabBarCalcTabID(@NonNull String label, Window dockedWindow) {
        if (dockedWindow != null) {
            final int id = dockedWindow.idTab;
            IkGuiInternal.keepAliveID(id);
            return id;
        }
        return context.windowCurrent.getID(label);
    }

    /**
     * The maximum width of a tab.
     *
     * @return The maximum width in pixels.
     */
    private static float tabBarCalcMaxTabWidth() {
        return IkGuiInternal.getFontSize() * 20.0f;
    }

    /**
     * Find the tab in a given visible position (not submission order).
     *
     * @param tabBar The tab bar.
     * @param order The visible position.
     * @return The tab, or null if out of range.
     */
    static TabItem tabBarFindTabByOrder(@NonNull TabBar tabBar, int order) {
        if (order < 0 || order >= tabBar.tabs.size()) {
            return null;
        }
        return tabBar.tabs.get(order);
    }

    /**
     * Find the visible position of a tab.
     *
     * @param tabBar The tab bar.
     * @param tab The tab.
     * @return The position, or -1 if not found.
     */
    static int tabBarGetTabOrder(@NonNull TabBar tabBar, @NonNull TabItem tab) {
        return tabBar.tabs.indexOf(tab);
    }

    /**
     * Find the most recently selected tab whose docked window was active, used to restore focus to
     * dock nodes.
     *
     * @param tabBar The tab bar.
     * @return The tab, or null if there are no tabs with active windows.
     */
    static TabItem tabBarFindMostRecentlySelectedTabForActiveWindow(@NonNull TabBar tabBar) {
        TabItem mostRecentlySelectedTab = null;
        for (TabItem tab : tabBar.tabs) {
            if ((mostRecentlySelectedTab == null
                            || mostRecentlySelectedTab.lastFrameSelected < tab.lastFrameSelected)
                    && tab.window != null
                    && tab.window.wasActive) {
                mostRecentlySelectedTab = tab;
            }
        }
        return mostRecentlySelectedTab;
    }

    /**
     * Fetch the tab most recently submitted with beginTabItem().
     *
     * @param tabBar The tab bar.
     * @return The current tab, or null if there isn't one.
     */
    static TabItem tabBarGetCurrentTab(@NonNull TabBar tabBar) {
        if (tabBar.lastTabItemIndex < 0 || tabBar.lastTabItemIndex >= tabBar.tabs.size()) {
            return null;
        }
        return tabBar.tabs.get(tabBar.lastTabItemIndex);
    }

    /**
     * Fetch the name of a tab.
     *
     * @param tab The tab.
     * @return The name of the tab, or the name of its window if it is a docked window.
     */
    static String tabBarGetTabName(@NonNull TabItem tab) {
        if (tab.window != null) {
            return tab.window.name;
        }
        return tab.name != null ? tab.name : "N/A";
    }

    /**
     * Calculate the screen position of a tab.
     *
     * @param tabBar The tab bar.
     * @param tab The tab.
     * @param output Where to store the position.
     * @return The output vector, for convenience.
     */
    static Vector2f tabBarGetTabPos(
            @NonNull TabBar tabBar, @NonNull TabItem tab, @NonNull Vector2f output) {
        if ((tab.flags & TabItemFlags.INTERNAL_SECTION_MASK) == 0) {
            return output.set(
                    tabBar.barRect.getLeft()
                            + IkGuiInternal.truncate(tab.offset - tabBar.scrollingAnimation),
                    tabBar.barRect.getTop());
        }
        return output.set(tabBar.barRect.getLeft() + tab.offset, tabBar.barRect.getTop());
    }

    /**
     * Called on a manual closure attempt.
     *
     * @param tabBar The tab bar.
     * @param tab The tab being closed.
     */
    static void tabBarCloseTab(@NonNull TabBar tabBar, @NonNull TabItem tab) {
        if ((tab.flags & TabItemFlags.INTERNAL_BUTTON) != 0) {
            // A button appended with tabItemButton()
            return;
        }

        if ((tab.flags & (TabItemFlags.UNSAVED_DOCUMENT | TabItemFlags.NO_ASSUMED_CLOSURE)) == 0) {
            // This will remove a frame of lag for selecting another tab on closure. However we
            // don't do it when the unsaved flag is set, so the user gets a chance to fully undo
            // the closure.
            tab.wantClose = true;
            if (tabBar.visibleTabID == tab.id) {
                tab.lastFrameVisible = -1;
                tabBar.selectedTabID = 0;
                tabBar.nextSelectedTabID = 0;
            }
        } else if (tabBar.visibleTabID != tab.id) {
            // Actually select before expecting a closure attempt (on an unsaved document tab the
            // user is expected to e.g. show a popup)
            tabBarQueueFocus(tabBar, tab);
        }
    }

    /**
     * Clamp a scrolling value to the valid range.
     *
     * @param tabBar The tab bar.
     * @param scrolling The scrolling value.
     * @return The clamped value.
     */
    private static float tabBarScrollClamp(@NonNull TabBar tabBar, float scrolling) {
        scrolling = Math.min(scrolling, tabBar.widthAllTabs - tabBar.barRect.getWidth());
        return Math.max(scrolling, 0.0f);
    }

    /**
     * Scroll so that a tab is visible. We may scroll to tabs that are not selected.
     *
     * @param tabBar The tab bar.
     * @param tabID The ID of the tab.
     * @param sections The layout sections.
     */
    private static void tabBarScrollToTab(
            @NonNull TabBar tabBar, int tabID, @NonNull TabBarSection[] sections) {
        TabItem tab = IkGuiInternal.tabBarFindTabByID(tabBar, tabID);
        if (tab == null) {
            return;
        }

        // Clamp attempts to scroll to leading/trailing section items
        if ((tab.flags & TabItemFlags.LEADING) != 0) {
            tab = tabBar.tabs.get(sections[SECTION_LEADING].tabCount);
        } else if ((tab.flags & TabItemFlags.TRAILING) != 0) {
            tab =
                    tabBar.tabs.get(
                            sections[SECTION_LEADING].tabCount
                                    + sections[SECTION_CENTRAL].tabCount);
        }
        if ((tab.flags & TabItemFlags.INTERNAL_SECTION_MASK) != 0) {
            return;
        }

        // When scrolling to make tab N+1 visible, always make a bit of N visible to suggest more
        // scrolling area (since we don't have a scrollbar). Disable the margin if the scrolling
        // section is too small for the target tab.
        final float margin =
                MathUtil.clamp(
                        tabBar.scrollingRectMaxX - tabBar.scrollingRectMinX - tab.width,
                        context.style.variable.itemInnerSpacing.x,
                        Math.max(
                                context.style.variable.itemInnerSpacing.x,
                                IkGuiInternal.getFontSize()));
        final int order = tabBarGetTabOrder(tabBar, tab);

        final float scrollableWidth = calcScrollableWidth(tabBar, sections);

        // We make all tab positions relative to the leading section width to make the code simpler
        final float tabX1 =
                tab.offset
                        - sections[SECTION_LEADING].width
                        + (order > sections[SECTION_LEADING].tabCount - 1 ? -margin : 0.0f);
        final float tabX2 =
                tab.offset
                        - sections[SECTION_LEADING].width
                        + tab.width
                        + (order + 1 < tabBar.tabs.size() - sections[SECTION_TRAILING].tabCount
                                ? margin
                                : 1.0f);
        tabBar.scrollingTargetDistanceToVisibility = 0.0f;
        if (tabBar.scrollingTarget > tabX1 || (tabX2 - tabX1 >= scrollableWidth)) {
            // Scroll to the left
            tabBar.scrollingTargetDistanceToVisibility =
                    Math.max(tabBar.scrollingAnimation - tabX2, 0.0f);
            tabBar.scrollingTarget = tabX1;
        } else if (tabBar.scrollingTarget < tabX2 - scrollableWidth) {
            // Scroll to the right
            tabBar.scrollingTargetDistanceToVisibility =
                    Math.max((tabX1 - scrollableWidth) - tabBar.scrollingAnimation, 0.0f);
            tabBar.scrollingTarget = tabX2 - scrollableWidth;
        }
    }

    /**
     * Request a tab be selected during the next layout.
     *
     * @param tabBar The tab bar.
     * @param tab The tab to select.
     */
    static void tabBarQueueFocus(@NonNull TabBar tabBar, @NonNull TabItem tab) {
        tabBar.nextSelectedTabID = tab.id;
    }

    /**
     * Request a tab be selected during the next layout, by name.
     *
     * @param tabBar The tab bar, which must not be part of a dock node.
     * @param tabName The name of the tab.
     */
    static void tabBarQueueFocus(@NonNull TabBar tabBar, @NonNull String tabName) {
        if ((tabBar.flags & TabBarFlags.INTERNAL_DOCK_NODE) != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Focusing tabs by name is only supported for explicit tab bars");
            return;
        }
        tabBar.nextSelectedTabID = tabBarCalcTabID(tabName, null);
    }

    /**
     * Request a tab be moved during the next layout.
     *
     * @param tabBar The tab bar.
     * @param tab The tab to move.
     * @param offset How many positions to move the tab, non-zero.
     */
    static void tabBarQueueReorder(@NonNull TabBar tabBar, @NonNull TabItem tab, int offset) {
        if (offset == 0 || tabBar.reorderRequestTabID != 0) {
            return;
        }
        tabBar.reorderRequestTabID = tab.id;
        tabBar.reorderRequestOffset = offset;
    }

    /**
     * Request a tab be moved to the position under the mouse.
     *
     * @param tabBar The tab bar.
     * @param sourceTab The tab being dragged.
     * @param mouseX The x position of the mouse.
     */
    static void tabBarQueueReorderFromMousePos(
            @NonNull TabBar tabBar, @NonNull TabItem sourceTab, float mouseX) {
        if (tabBar.reorderRequestTabID != 0 || (tabBar.flags & TabBarFlags.REORDERABLE) == 0) {
            return;
        }

        final float tabSpacing = context.style.variable.itemInnerSpacing.x;
        final boolean isCentralSection =
                (sourceTab.flags & TabItemFlags.INTERNAL_SECTION_MASK) == 0;
        final float barOffset =
                tabBar.barRect.getLeft() - (isCentralSection ? tabBar.scrollingTarget : 0);

        // Count the number of contiguous tabs we are crossing over
        final int direction = (barOffset + sourceTab.offset) > mouseX ? -1 : +1;
        final int sourceIndex = tabBar.tabs.indexOf(sourceTab);
        int destinationIndex = sourceIndex;
        for (int i = sourceIndex; i >= 0 && i < tabBar.tabs.size(); i += direction) {
            // Reordered tabs must share the same section
            final TabItem destinationTab = tabBar.tabs.get(i);
            if ((destinationTab.flags & TabItemFlags.NO_REORDER) != 0) {
                break;
            }
            if ((destinationTab.flags & TabItemFlags.INTERNAL_SECTION_MASK)
                    != (sourceTab.flags & TabItemFlags.INTERNAL_SECTION_MASK)) {
                break;
            }
            destinationIndex = i;

            // Include the spacing after the tab, so when the mouse is between tabs we don't
            // continue checking further tabs that are not hovered
            final float x1 = barOffset + destinationTab.offset - tabSpacing;
            final float x2 = barOffset + destinationTab.offset + destinationTab.width + tabSpacing;
            if ((direction < 0 && mouseX > x1) || (direction > 0 && mouseX < x2)) {
                break;
            }
        }

        if (destinationIndex != sourceIndex) {
            tabBarQueueReorder(tabBar, sourceTab, destinationIndex - sourceIndex);
        }
    }

    /**
     * Apply a reorder request.
     *
     * @param tabBar The tab bar.
     * @return True if a tab was moved.
     */
    static boolean tabBarProcessReorder(@NonNull TabBar tabBar) {
        final TabItem tab1 = IkGuiInternal.tabBarFindTabByID(tabBar, tabBar.reorderRequestTabID);
        if (tab1 == null || (tab1.flags & TabItemFlags.NO_REORDER) != 0) {
            return false;
        }

        final int tab1Order = tabBarGetTabOrder(tabBar, tab1);
        final int tab2Order = tab1Order + tabBar.reorderRequestOffset;
        if (tab2Order < 0 || tab2Order >= tabBar.tabs.size()) {
            return false;
        }

        // Reordered tabs must share the same section
        final TabItem tab2 = tabBar.tabs.get(tab2Order);
        if ((tab2.flags & TabItemFlags.NO_REORDER) != 0) {
            return false;
        }
        if ((tab1.flags & TabItemFlags.INTERNAL_SECTION_MASK)
                != (tab2.flags & TabItemFlags.INTERNAL_SECTION_MASK)) {
            return false;
        }

        tabBar.tabs.remove(tab1Order);
        tabBar.tabs.add(tab2Order, tab1);

        if ((tabBar.flags & TabBarFlags.INTERNAL_SAVE_SETTINGS) != 0) {
            IkGuiInternal.markIniSettingsDirty();
        }
        return true;
    }

    /**
     * Push the colors used for the scrolling and tab list buttons.
     *
     * @return How many colors were pushed.
     */
    private static int pushTabBarButtonColors() {
        final int arrowColor = Color.multiplyAlpha(IkGuiImplUtils.getColor(ColorType.TEXT), 0.5f);
        IkGuiImplUtils.pushStyleColor(ColorType.TEXT, arrowColor);
        IkGuiImplUtils.pushStyleColor(ColorType.BUTTON, Color.CLEAR);
        return 2;
    }

    /**
     * Draw the scrolling buttons on the right of the tab bar, which reduces the width of the bar.
     *
     * @param tabBar The tab bar.
     * @return The tab to scroll to, or null if neither button was pressed.
     */
    private static TabItem tabBarScrollingButtons(@NonNull TabBar tabBar) {
        final Window window = context.windowCurrent;
        final float fontSize = IkGuiInternal.getFontSize();
        final float arrowButtonWidth = fontSize - 2.0f;
        final float arrowButtonHeight = fontSize + context.style.variable.framePadding.y * 2.0f;
        final float scrollingButtonsWidth = arrowButtonWidth * 2.0f;

        final Vector2f backupCursorPosition = new Vector2f(window.cursorPosition);

        int selectDirection = 0;
        final int pushedColors = pushTabBarButtonColors();
        IkGuiImplUtils.pushItemFlag(ItemFlags.BUTTON_REPEAT | ItemFlags.NO_NAV, true);
        final long backupRepeatDelay = context.io.keyRepeatDelay;
        final long backupRepeatRate = context.io.keyRepeatRate;
        context.io.keyRepeatDelay = 250;
        context.io.keyRepeatRate = 200;
        final float x =
                Math.max(
                        tabBar.barRect.getLeft(),
                        tabBar.barRect.getRight() - scrollingButtonsWidth);
        window.cursorPosition.set(x, tabBar.barRect.getTop());
        if (IkGuiImplButtons.arrowButtonEx(
                "##<",
                Direction.LEFT,
                arrowButtonWidth,
                arrowButtonHeight,
                ButtonFlags.INTERNAL_PRESSED_ON_CLICK)) {
            selectDirection = -1;
        }
        window.cursorPosition.set(x + arrowButtonWidth, tabBar.barRect.getTop());
        if (IkGuiImplButtons.arrowButtonEx(
                "##>",
                Direction.RIGHT,
                arrowButtonWidth,
                arrowButtonHeight,
                ButtonFlags.INTERNAL_PRESSED_ON_CLICK)) {
            selectDirection = +1;
        }
        IkGuiImplUtils.popItemFlag();
        IkGuiImplUtils.popStyleColor(pushedColors);
        context.io.keyRepeatRate = backupRepeatRate;
        context.io.keyRepeatDelay = backupRepeatDelay;

        TabItem tabToScrollTo = null;
        if (selectDirection != 0) {
            final TabItem selected = IkGuiInternal.tabBarFindTabByID(tabBar, tabBar.selectedTabID);
            if (selected != null) {
                int selectedOrder = tabBarGetTabOrder(tabBar, selected);
                int targetOrder = selectedOrder + selectDirection;

                // Skip tab item buttons until another tab item is found or the end is reached
                while (tabToScrollTo == null) {
                    // If we are at the end of the list, still scroll to make our tab visible
                    tabToScrollTo =
                            tabBar.tabs.get(
                                    (targetOrder >= 0 && targetOrder < tabBar.tabs.size())
                                            ? targetOrder
                                            : selectedOrder);

                    // Cross through buttons (even if the first/last item is a button, return it
                    // so we can update the scroll)
                    if ((tabToScrollTo.flags & TabItemFlags.INTERNAL_BUTTON) != 0) {
                        targetOrder += selectDirection;
                        selectedOrder += selectDirection;
                        if (targetOrder >= 0 && targetOrder < tabBar.tabs.size()) {
                            tabToScrollTo = null;
                        }
                    }
                }
            }
        }
        window.cursorPosition.set(backupCursorPosition);
        tabBar.barRect.set(
                tabBar.barRect.getLeft(),
                tabBar.barRect.getTop(),
                tabBar.barRect.getRight() - (scrollingButtonsWidth + 1.0f),
                tabBar.barRect.getBottom());

        return tabToScrollTo;
    }

    /**
     * Draw the tab list popup button on the left of the tab bar, which reduces the width of the
     * bar.
     *
     * @param tabBar The tab bar.
     * @return The tab that was picked from the list, or null if none were.
     */
    private static TabItem tabBarTabListPopupButton(@NonNull TabBar tabBar) {
        final Window window = context.windowCurrent;
        final StyleVariables style = context.style.variable;

        // We use frame padding y to match the square arrow button size
        final float buttonWidth = IkGuiInternal.getFontSize() + style.framePadding.y;
        final Vector2f backupCursorPosition = new Vector2f(window.cursorPosition);
        window.cursorPosition.set(
                tabBar.barRect.getLeft() - style.framePadding.y, tabBar.barRect.getTop());
        tabBar.barRect.set(
                tabBar.barRect.getLeft() + buttonWidth,
                tabBar.barRect.getTop(),
                tabBar.barRect.getRight(),
                tabBar.barRect.getBottom());

        final int pushedColors = pushTabBarButtonColors();
        final boolean open =
                IkGuiImplCombo.beginCombo(
                        "##v", null, ComboFlags.NO_PREVIEW | ComboFlags.HEIGHT_LARGEST);
        IkGuiImplUtils.popStyleColor(pushedColors);

        TabItem tabToSelect = null;
        if (open) {
            for (TabItem tab : tabBar.tabs) {
                if ((tab.flags & TabItemFlags.INTERNAL_BUTTON) != 0) {
                    continue;
                }
                if (IkGuiImplMiscWidgets.selectable(
                        tabBarGetTabName(tab),
                        tabBar.selectedTabID == tab.id,
                        SelectableFlags.NONE,
                        0,
                        0)) {
                    tabToSelect = tab;
                }
            }
            IkGuiImplCombo.endCombo();
        }

        window.cursorPosition.set(backupCursorPosition);
        return tabToSelect;
    }

    // ---------------------------------------------------------------------------------------
    // Tab items
    // ---------------------------------------------------------------------------------------

    /**
     * Create a tab.
     *
     * @param label The label, which is also used for the ID.
     * @param open If not null, a close button is shown and this is set to false when clicked.
     * @param tabItemFlags Tab item flags.
     * @return True if the tab is selected, and endTabItem() needs to be called.
     * @see TabItemFlags
     */
    public static boolean beginTabItem(@NonNull String label, IkBoolean open, int tabItemFlags) {
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return false;
        }

        final TabBar tabBar = context.currentTabBar;
        if (tabBar == null) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "beginTabItem() needs to be called between beginTabBar() and endTabBar()!");
            return false;
        }
        if ((tabItemFlags & TabItemFlags.INTERNAL_BUTTON) != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "beginTabItem() can't be used with button flags, use tabItemButton()");
            return false;
        }

        final boolean result = tabItemEx(tabBar, label, open, tabItemFlags, null);
        if (result && (tabItemFlags & TabItemFlags.NO_PUSH_ID) == 0) {
            // We already hashed the label so push into the ID stack directly instead of doing
            // another hash through pushID(label)
            final TabItem tab = tabBar.tabs.get(tabBar.lastTabItemIndex);
            IkGuiInternal.pushOverrideID(tab.id);
        }
        return result;
    }

    /** End a tab, only call this if beginTabItem() returned true. */
    public static void endTabItem() {
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return;
        }

        final TabBar tabBar = context.currentTabBar;
        if (tabBar == null) {
            IkGuiImplDebugTools.reportError(
                    log, "endTabItem() needs to be called between beginTabBar() and endTabBar()!");
            return;
        }
        final TabItem tab = tabBarGetCurrentTab(tabBar);
        if (tab == null) {
            IkGuiImplDebugTools.reportError(log, "Mismatched beginTabItem()/endTabItem()!");
            return;
        }
        if ((tab.flags & TabItemFlags.NO_PUSH_ID) == 0) {
            IkGuiImplUtils.popID();
        }
    }

    /**
     * Create a tab that behaves like a button. It can't be selected in the tab bar.
     *
     * @param label The label, which is also used for the ID.
     * @param tabItemFlags Tab item flags.
     * @return True when clicked.
     */
    public static boolean tabItemButton(@NonNull String label, int tabItemFlags) {
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return false;
        }

        final TabBar tabBar = context.currentTabBar;
        if (tabBar == null) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "tabItemButton() needs to be called between beginTabBar() and endTabBar()!");
            return false;
        }
        return tabItemEx(
                tabBar,
                label,
                null,
                tabItemFlags | TabItemFlags.INTERNAL_BUTTON | TabItemFlags.NO_REORDER,
                null);
    }

    /**
     * Reserve some empty space in the tab bar, like an invisible tab.
     *
     * @param stringID The string ID of the spacing.
     * @param tabItemFlags Tab item flags, e.g. {@link TabItemFlags#LEADING}.
     * @param width The width of the space.
     */
    static void tabItemSpacing(@NonNull String stringID, int tabItemFlags, float width) {
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return;
        }

        final TabBar tabBar = context.currentTabBar;
        if (tabBar == null) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "tabItemSpacing() needs to be called between beginTabBar() and endTabBar()!");
            return;
        }
        IkGuiImplUtils.setNextItemWidth(width);
        tabItemEx(
                tabBar,
                stringID,
                null,
                tabItemFlags
                        | TabItemFlags.INTERNAL_BUTTON
                        | TabItemFlags.NO_REORDER
                        | TabItemFlags.INTERNAL_INVISIBLE,
                null);
    }

    /**
     * Submit a tab.
     *
     * @param tabBar The tab bar.
     * @param label The label, which is also used for the ID.
     * @param open If not null, a close button is shown and this is set to false when clicked.
     * @param tabItemFlags Tab item flags.
     * @param dockedWindow The docked window this tab represents, may be null.
     * @return For buttons, true when clicked. Otherwise true if the contents are visible.
     */
    static boolean tabItemEx(
            @NonNull TabBar tabBar,
            @NonNull String label,
            IkBoolean open,
            int tabItemFlags,
            Window dockedWindow) {
        // Lay out the whole tab bar if not already done
        if (tabBar.wantLayout) {
            final NextItemData backupNextItemData = new NextItemData();
            copyNextItemData(context.nextItemData, backupNextItemData);
            tabBarLayout(tabBar);
            copyNextItemData(backupNextItemData, context.nextItemData);
        }
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return false;
        }

        final StyleVariables style = context.style.variable;
        final int id = tabBarCalcTabID(label, dockedWindow);
        IkGuiInternal.testEngineItemInfo(id, label, context.lastItemData.statusFlags);

        // If the user called us with open == false, we early out and don't render. We make a call
        // to itemAdd() so that attempts to use a contextual popup menu with an implicit ID won't
        // use an older ID.
        if (open != null && !open.get()) {
            IkGuiInternal.itemAdd(new RectFloat(0, 0, 0, 0), id, null, ItemFlags.NO_NAV);
            return false;
        }

        if (open != null && (tabItemFlags & TabItemFlags.INTERNAL_BUTTON) != 0) {
            IkGuiImplDebugTools.reportError(log, "Tab buttons can't have a close button");
            open = null;
        }
        if ((tabItemFlags & TabItemFlags.INTERNAL_SECTION_MASK)
                == TabItemFlags.INTERNAL_SECTION_MASK) {
            IkGuiImplDebugTools.reportError(log, "Tabs can't be both leading and trailing");
            tabItemFlags &= ~TabItemFlags.TRAILING;
        }

        // Store into NO_CLOSE_BUTTON, also honor the flag if passed by the user
        if ((tabItemFlags & TabItemFlags.INTERNAL_NO_CLOSE_BUTTON) != 0) {
            open = null;
        } else if (open == null) {
            tabItemFlags |= TabItemFlags.INTERNAL_NO_CLOSE_BUTTON;
        }

        // Acquire the tab data
        TabItem tab = IkGuiInternal.tabBarFindTabByID(tabBar, id);
        boolean tabIsNew = false;
        if (tab == null) {
            tab = new TabItem();
            tab.id = id;
            tabBar.tabs.add(tab);
            tabBar.tabsAddedNew = true;
            tabIsNew = true;
        }
        tabBar.lastTabItemIndex = tabBar.tabs.indexOf(tab);

        // Calculate the tab contents size
        final Vector2f size =
                tabItemCalcSize(
                        label,
                        open != null || (tabItemFlags & TabItemFlags.UNSAVED_DOCUMENT) != 0,
                        new Vector2f());
        tab.requestedWidth = -1.0f;
        if ((context.nextItemData.fieldFlags & NextItemFlags.HAS_WIDTH) != 0) {
            size.x = context.nextItemData.width;
            tab.requestedWidth = size.x;
        }
        if (tabIsNew) {
            tab.width = Math.max(1.0f, size.x);
        }
        tab.contentWidth = size.x;
        tab.beginOrder = tabBar.tabsActiveCount++;

        final boolean tabBarAppearing = tabBar.previousFrameVisible + 1 < context.frameCount;
        final boolean tabBarFocused = (tabBar.flags & TabBarFlags.INTERNAL_IS_FOCUSED) != 0;
        final boolean tabAppearing = tab.lastFrameVisible + 1 < context.frameCount;
        final boolean tabJustUnsaved =
                (tabItemFlags & TabItemFlags.UNSAVED_DOCUMENT) != 0
                        && (tab.flags & TabItemFlags.UNSAVED_DOCUMENT) == 0;
        final boolean isTabButton = (tabItemFlags & TabItemFlags.INTERNAL_BUTTON) != 0;
        tab.lastFrameVisible = context.frameCount;
        tab.flags = tabItemFlags;
        tab.window = dockedWindow;

        // Regular tabs are permitted in a dock node tab bar, but window tabs are not permitted in a
        // non-dock node tab bar
        if (dockedWindow != null) {
            if ((tabBar.flags & TabBarFlags.INTERNAL_DOCK_NODE) == 0) {
                IkGuiImplDebugTools.reportError(
                        log, "Docked window tabs are only allowed in dock node tab bars");
            }
            tab.name = null;
        } else {
            tab.name = label;
        }

        // Update the selected tab
        if (!isTabButton) {
            if (tabAppearing
                    && (tabBar.flags & TabBarFlags.AUTO_SELECT_NEW_TABS) != 0
                    && tabBar.nextSelectedTabID == 0
                    && (!tabBarAppearing || tabBar.selectedTabID == 0)) {
                // New tabs get activated
                tabBarQueueFocus(tabBar, tab);
            }
            if ((tabItemFlags & TabItemFlags.SET_SELECTED) != 0 && tabBar.selectedTabID != id) {
                tabBarQueueFocus(tabBar, tab);
            }
        }

        // Lock visibility. The contents being visible is not the same as being selected, because
        // ctrl+tab operations may preview some tabs without selecting them.
        boolean tabContentsVisible = tabBar.visibleTabID == id;
        if (tabContentsVisible) {
            tabBar.visibleTabWasSubmitted = true;
        }

        // On the very first frame of a tab bar we let the first tab contents be visible to
        // minimize appearing glitches
        if (!tabContentsVisible
                && tabBar.selectedTabID == 0
                && tabBarAppearing
                && dockedWindow == null
                && tabBar.tabs.size() == 1
                && (tabBar.flags & TabBarFlags.AUTO_SELECT_NEW_TABS) == 0) {
            tabContentsVisible = true;
        }

        // Note that a new tab is not necessarily the same as an appearing tab! When a tab bar stops
        // being submitted and then gets submitted again, the tabs will be appearing but not new.
        if (tabAppearing && (!tabBarAppearing || tabIsNew)) {
            IkGuiInternal.itemAdd(new RectFloat(0, 0, 0, 0), id, null, ItemFlags.NO_NAV);
            if (isTabButton) {
                return false;
            }
            return tabContentsVisible;
        }

        if (tabBar.selectedTabID == id) {
            tab.lastFrameSelected = context.frameCount;
        }

        // Back up the current layout position
        final Vector2f backupMainCursorPosition = new Vector2f(window.cursorPosition);

        // Layout
        final boolean isCentralSection = (tab.flags & TabItemFlags.INTERNAL_SECTION_MASK) == 0;
        size.x = tab.width;
        tabBarGetTabPos(tabBar, tab, window.cursorPosition);
        final float posX = window.cursorPosition.x;
        final float posY = window.cursorPosition.y;
        final RectFloat bb = new RectFloat(posX, posY, posX + size.x, posY + size.y);

        // Clip tabs that scroll out of the central section
        final boolean wantClipRect =
                isCentralSection
                        && (bb.getLeft() < tabBar.scrollingRectMinX
                                || bb.getRight() > tabBar.scrollingRectMaxX);
        if (wantClipRect) {
            IkGuiImplLayout.pushClipRect(
                    MathUtil.clamp(
                            bb.getLeft(), tabBar.scrollingRectMinX, tabBar.scrollingRectMaxX),
                    bb.getTop() - 1,
                    tabBar.scrollingRectMaxX,
                    bb.getBottom(),
                    true);
        }

        final Vector2f backupCursorMaxPosition = new Vector2f(window.cursorMaxPosition);
        IkGuiInternal.itemSize(bb.getWidth(), bb.getHeight(), style.framePadding.y);
        window.cursorMaxPosition.set(backupCursorMaxPosition);

        if (!IkGuiInternal.itemAdd(bb, id)) {
            if (wantClipRect) {
                IkGuiImplLayout.popClipRect();
            }
            window.cursorPosition.set(backupMainCursorPosition);
            return tabContentsVisible;
        }

        // Click to select a tab
        int buttonFlags =
                (isTabButton
                                ? ButtonFlags.INTERNAL_PRESSED_ON_CLICK_RELEASE
                                : ButtonFlags.INTERNAL_PRESSED_ON_CLICK)
                        | ButtonFlags.ALLOW_OVERLAP;
        // Hold to select tabs while dragging a payload, other than docking windows
        if (context.dragDropActive
                && !context.dragDropPayload.isDataType(IkGuiImplDragDrop.PAYLOAD_TYPE_WINDOW)) {
            buttonFlags |= ButtonFlags.INTERNAL_PRESSED_ON_DRAG_DROP_HOLD;
        }
        final IkBoolean hovered = new IkBoolean();
        final IkBoolean held = new IkBoolean();
        boolean pressed = false;
        if ((tabItemFlags & TabItemFlags.INTERNAL_INVISIBLE) == 0) {
            pressed = IkGuiInternal.buttonBehavior(bb, id, hovered, held, buttonFlags);
        }
        if (pressed && !isTabButton) {
            tabBarQueueFocus(tabBar, tab);
        }

        // Transfer the active ID window so the active ID is not owned by the dock host
        if (held.get()
                && dockedWindow != null
                && context.activeID == id
                && context.activeIDIsJustActivated) {
            context.activeIDWindow = dockedWindow;
        }

        // Drag and drop a single floating window node moves it
        final DockNode node = dockedWindow != null ? dockedWindow.dockNode : null;
        final boolean singleFloatingWindowNode =
                node != null && node.isFloatingNode() && node.windows.size() == 1;
        if (held.get()
                && singleFloatingWindowNode
                && IkGuiImplUtils.isMouseDragging(MouseButton.LEFT, 0.0f)) {
            // Move
            IkGuiInternal.startMouseMovingWindow(dockedWindow);
        } else if (held.get()
                && !tabAppearing
                && IkGuiImplUtils.isMouseDragging(MouseButton.LEFT, -1.0f)) {
            // Drag and drop: reorder tabs
            int dragDirection = 0;
            float dragDistanceFromEdgeX = 0.0f;
            if (!context.dragDropActive
                    && ((tabBar.flags & TabBarFlags.REORDERABLE) != 0 || dockedWindow != null)) {
                // While moving a tab it will jump to the other side of the mouse, so we also test
                // the mouse delta
                final Vector2f mouse = context.io.mousePosition;
                if (context.io.mouseDelta.x < 0.0f && mouse.x < bb.getLeft()) {
                    dragDirection = -1;
                    dragDistanceFromEdgeX = bb.getLeft() - mouse.x;
                    tabBarQueueReorderFromMousePos(tabBar, tab, mouse.x);
                } else if (context.io.mouseDelta.x > 0.0f && mouse.x > bb.getRight()) {
                    dragDirection = 1;
                    dragDistanceFromEdgeX = mouse.x - bb.getRight();
                    tabBarQueueReorderFromMousePos(tabBar, tab, mouse.x);
                }
            }

            // Extract a dockable window out of its tab bar
            final boolean canUndock =
                    dockedWindow != null
                            && (dockedWindow.flags & WindowFlags.NO_MOVE) == 0
                            && (node.mergedFlags & DockNodeFlags.NO_UNDOCKING) == 0;
            if (canUndock) {
                // We use a variable threshold to distinguish dragging tabs within a tab bar and
                // extracting them out of the tab bar
                boolean undockingTab =
                        context.dragDropActive && context.dragDropPayload.sourceID == id;
                if (!undockingTab) {
                    final float thresholdBase = IkGuiInternal.getFontSize();
                    final float thresholdX = thresholdBase * 2.2f;
                    final float thresholdY =
                            thresholdBase * 1.5f
                                    + MathUtil.clamp(
                                            (Math.abs(
                                                                    context.io
                                                                            .mouseDragMaxDistanceAbsolute[
                                                                            MouseButton.LEFT.index]
                                                                            .x)
                                                            - thresholdBase * 2.0f)
                                                    * 0.20f,
                                            0.0f,
                                            thresholdBase * 4.0f);

                    final Vector2f mouse = context.io.mousePosition;
                    final float distanceFromEdgeY =
                            Math.max(bb.getTop() - mouse.y, mouse.y - bb.getBottom());
                    if (distanceFromEdgeY >= thresholdY) {
                        undockingTab = true;
                    }
                    if (dragDistanceFromEdgeX > thresholdX
                            && ((dragDirection < 0 && tabBarGetTabOrder(tabBar, tab) == 0)
                                    || (dragDirection > 0
                                            && tabBarGetTabOrder(tabBar, tab)
                                                    == tabBar.tabs.size() - 1))) {
                        undockingTab = true;
                    }
                }

                if (undockingTab) {
                    // Undock
                    IkGuiImplDocking.dockContextQueueUndockWindow(dockedWindow);
                    context.windowMoving = dockedWindow;
                    IkGuiInternal.setActiveID(dockedWindow.idMove, dockedWindow);
                    context.activeIDClickOffset.sub(
                            dockedWindow.position.x - bb.getLeft(),
                            dockedWindow.position.y - bb.getTop());
                    context.activeIDNoClearOnFocusLost = true;
                    IkGuiImplKeys.setActiveIDUsingAllKeyboardKeys();
                }
            }
        }

        // Render the tab shape
        final boolean isVisible =
                (context.lastItemData.statusFlags & ItemStatusFlags.VISIBLE) != 0
                        && (tabItemFlags & TabItemFlags.INTERNAL_INVISIBLE) == 0;
        if (isVisible) {
            final DrawList drawList = window.drawList;
            final ColorType tabColorType;
            if (held.get() || hovered.get()) {
                tabColorType = ColorType.TAB_HOVERED;
            } else if (tabContentsVisible) {
                tabColorType =
                        tabBarFocused ? ColorType.TAB_SELECTED : ColorType.TAB_DIMMED_SELECTED;
            } else {
                tabColorType = tabBarFocused ? ColorType.TAB : ColorType.TAB_DIMMED;
            }
            tabItemBackground(
                    drawList,
                    bb,
                    tabItemFlags,
                    IkGuiImplUtils.getColorWithGlobalAlpha(tabColorType));
            if (tabContentsVisible
                    && (tabBar.flags & TabBarFlags.DRAW_SELECTED_OVERLINE) != 0
                    && style.tabBarOverlineSize > 0.0f) {
                renderSelectedOverline(drawList, bb, tabBarFocused);
            }
            IkGuiImplNav.renderNavCursor(bb, id, NavRenderCursorFlags.NONE, -1.0f);

            // Select with the right mouse button. This is so the common idiom for context menus
            // automatically highlights the current widget.
            final boolean hoveredUnblocked =
                    IkGuiImplUtils.isItemHovered(HoveredFlags.ALLOW_WHEN_BLOCKED_BY_POPUP);
            if (tabBar.selectedTabID != tab.id
                    && hoveredUnblocked
                    && (IkGuiImplUtils.isMouseClicked(MouseButton.RIGHT, false)
                            || IkGuiImplUtils.isMouseReleased(MouseButton.RIGHT))
                    && !isTabButton) {
                tabBarQueueFocus(tabBar, tab);
            }

            if ((tabBar.flags & TabBarFlags.NO_CLOSE_WITH_MIDDLE_MOUSE_BUTTON) != 0) {
                tabItemFlags |= TabItemFlags.NO_CLOSE_WITH_MIDDLE_MOUSE_BUTTON;
            }

            // Render the tab label, process the close button
            final int closeButtonID =
                    open != null
                            ? Hash.getID("#CLOSE", dockedWindow != null ? dockedWindow.id : id)
                            : 0;
            final boolean[] closedAndClipped = new boolean[2];
            tabItemLabelAndCloseButton(
                    bb,
                    tabJustUnsaved ? (tabItemFlags & ~TabItemFlags.UNSAVED_DOCUMENT) : tabItemFlags,
                    tabBar.framePadding,
                    label,
                    id,
                    closeButtonID,
                    tabContentsVisible,
                    closedAndClipped);
            final boolean justClosed = closedAndClipped[0];
            final boolean textClipped = closedAndClipped[1];
            if (justClosed && open != null) {
                open.set(false);
                tabBarCloseTab(tabBar, tab);
            }

            // Forward the hovered state so isItemHovered() after begin() can work (even though we
            // are technically hovering our parent)
            if (dockedWindow != null && (hovered.get() || context.hoveredID == closeButtonID)) {
                context.lastItemData.statusFlags |= ItemStatusFlags.HOVERED_WINDOW;
            }

            // Tooltip for clipped labels
            if (textClipped
                    && context.hoveredID == id
                    && !held.get()
                    && (tabBar.flags & TabBarFlags.NO_TOOLTIP) == 0
                    && (tab.flags & TabItemFlags.NO_TOOLTIP) == 0) {
                IkGuiImplPopups.setItemTooltip(Hash.getDisplayedText(label));
            }
        }

        // Restore the main window position so the user can draw there
        if (wantClipRect) {
            IkGuiImplLayout.popClipRect();
        }
        window.cursorPosition.set(backupMainCursorPosition);

        if (isTabButton) {
            return pressed;
        }
        return tabContentsVisible;
    }

    /**
     * Copy next item data, used to preserve it across layout which may submit items.
     *
     * @param source The data to copy.
     * @param destination Where to copy it to.
     */
    private static void copyNextItemData(
            @NonNull NextItemData source, @NonNull NextItemData destination) {
        destination.itemFlags = source.itemFlags;
        destination.fieldFlags = source.fieldFlags;
        destination.focusScopeID = source.focusScopeID;
        destination.selectionUserData = source.selectionUserData;
        destination.width = source.width;
        destination.shortcut = source.shortcut;
        destination.shortcutFlags = source.shortcutFlags;
        destination.openValue = source.openValue;
        destination.openCondition = source.openCondition;
        destination.storageID = source.storageID;
    }

    /**
     * Draw the overline marker on the selected tab.
     *
     * @param drawList The draw list.
     * @param bb The tab bounding box.
     * @param tabBarFocused Whether the tab bar is focused.
     */
    private static void renderSelectedOverline(
            @NonNull DrawList drawList, @NonNull RectFloat bb, boolean tabBarFocused) {
        final StyleVariables style = context.style.variable;
        final float leftX = bb.getLeft();
        final float rightX = bb.getRight();
        final float topY = bb.getTop() + 1.0f;
        final int color =
                IkGuiImplUtils.getColorWithGlobalAlpha(
                        tabBarFocused
                                ? ColorType.TAB_SELECTED_OVERLINE
                                : ColorType.TAB_DIMMED_SELECTED_OVERLINE);
        if (style.tabRounding > 0.0f) {
            final float rounding = style.tabRounding;
            drawList.pathArcToFast(leftX + rounding, topY + rounding, rounding, 7, 9);
            drawList.pathArcToFast(rightX - rounding, topY + rounding, rounding, 9, 11);
            drawList.pathStroke(color, false, style.tabBarOverlineSize);
        } else {
            drawList.addLine(
                    leftX - 0.5f,
                    topY - 0.5f,
                    rightX - 0.5f,
                    topY - 0.5f,
                    color,
                    style.tabBarOverlineSize);
        }
    }

    /**
     * Notify a tab bar or the docking system of a closed tab/window ahead of time, which is useful
     * to reduce visual flicker on reorderable tab bars. For tab bars, call after beginTabBar() and
     * before tab submission. Otherwise call with a window name.
     *
     * @param label The tab label or docked window name.
     */
    public static void setTabItemClosed(@NonNull String label) {
        final TabBar tabBar = context.currentTabBar;
        final boolean isWithinManualTabBar =
                tabBar != null && (tabBar.flags & TabBarFlags.INTERNAL_DOCK_NODE) == 0;
        if (isWithinManualTabBar) {
            final TabItem tab =
                    IkGuiInternal.tabBarFindTabByID(tabBar, tabBarCalcTabID(label, null));
            if (tab != null) {
                // Will be processed by the next layout
                tab.wantClose = true;
            }
            return;
        }
        final Window window = IkGuiInternal.findWindowByName(label);
        if (window != null && window.dockIsActive && window.dockNode != null) {
            final TabBar nodeTabBar = window.dockNode.tabBar;
            if (nodeTabBar != null) {
                IkGuiInternal.tabBarRemoveTab(nodeTabBar, tabBarCalcTabID(label, window));
            }
            window.dockTabWantClose = true;
        }
    }

    /**
     * Calculate the size of a tab.
     *
     * @param label The label of the tab.
     * @param hasCloseButtonOrUnsavedMarker Whether there is a close button or unsaved marker.
     * @param output Where to store the size.
     * @return The output vector, for convenience.
     */
    static Vector2f tabItemCalcSize(
            @NonNull String label,
            boolean hasCloseButtonOrUnsavedMarker,
            @NonNull Vector2f output) {
        final StyleVariables style = context.style.variable;
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, label, true, -1.0f);
        float width = labelSize.x + style.framePadding.x;
        final float height = labelSize.y + style.framePadding.y * 2.0f;
        if (hasCloseButtonOrUnsavedMarker) {
            // We use the font size intentionally to fit the close button circle
            width +=
                    style.framePadding.x + (style.itemInnerSpacing.x + IkGuiInternal.getFontSize());
        } else {
            width += style.framePadding.x + 1.0f;
        }
        return output.set(Math.min(width, tabBarCalcMaxTabWidth()), height);
    }

    /**
     * Calculate the size of a tab for a docked window.
     *
     * @param window The window.
     * @param output Where to store the size.
     * @return The output vector, for convenience.
     */
    static Vector2f tabItemCalcSize(@NonNull Window window, @NonNull Vector2f output) {
        return tabItemCalcSize(
                window.name,
                window.hasCloseButton || (window.flags & WindowFlags.UNSAVED_DOCUMENT) != 0,
                output);
    }

    /**
     * Draw the background of a tab. We trim 1 pixel off the top of the bounding box so tabs can fit
     * within a regular frame height while looking "detached" from it.
     *
     * @param drawList The draw list.
     * @param bb The bounding box of the tab.
     * @param tabItemFlags Tab item flags.
     * @param color The color of the tab.
     */
    static void tabItemBackground(
            @NonNull DrawList drawList, @NonNull RectFloat bb, int tabItemFlags, int color) {
        final StyleVariables style = context.style.variable;
        final float width = bb.getWidth();
        if (width <= 0.0f) {
            return;
        }
        final float rounding =
                Math.max(
                        0.0f,
                        Math.min(
                                (tabItemFlags & TabItemFlags.INTERNAL_BUTTON) != 0
                                        ? style.frameRounding
                                        : style.tabRounding,
                                width * 0.5f - 1.0f));
        // Leave a bit of room in title bars
        final float y1 = bb.getTop() + 1.0f;
        final float y2 = bb.getBottom() - style.tabBarBorderSize;
        drawList.addRectFilled(
                bb.getLeft(),
                bb.getTop(),
                bb.getRight(),
                y2,
                color,
                rounding,
                DrawFlags.ROUND_CORNERS_TOP);
        if (style.tabBorderSize > 0.0f) {
            drawList.pathLineTo(bb.getLeft() + 0.5f, y2);
            drawList.pathArcToFast(
                    bb.getLeft() + rounding + 0.5f, y1 + rounding + 0.5f, rounding, 6, 9);
            drawList.pathArcToFast(
                    bb.getRight() - rounding - 0.5f, y1 + rounding + 0.5f, rounding, 9, 12);
            drawList.pathLineTo(bb.getRight() - 0.5f, y2);
            drawList.pathStroke(
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.BORDER),
                    false,
                    style.tabBorderSize);
        }
    }

    /**
     * Render the text label (with custom clipping), the unsaved document marker, and process the
     * close button.
     *
     * @param bb The bounding box of the tab.
     * @param tabItemFlags Tab item flags.
     * @param framePadding The frame padding, locked for the tab bar.
     * @param label The label.
     * @param tabID The ID of the tab.
     * @param closeButtonID The ID of the close button, 0 if there is no close button.
     * @param isContentsVisible Whether the tab contents are visible.
     * @param outClosedAndClipped Set to whether the tab was just closed (index 0), and whether the
     *     text was clipped (index 1).
     */
    static void tabItemLabelAndCloseButton(
            @NonNull RectFloat bb,
            int tabItemFlags,
            @NonNull Vector2f framePadding,
            @NonNull String label,
            int tabID,
            int closeButtonID,
            boolean isContentsVisible,
            boolean @NonNull [] outClosedAndClipped) {
        final StyleVariables style = context.style.variable;
        final String displayedLabel = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayedLabel, false, -1.0f);

        outClosedAndClipped[0] = false;
        outClosedAndClipped[1] = false;

        if (bb.getWidth() <= 1.0f) {
            return;
        }

        // Render the text label (with clipping) and unsaved marker
        float textMinX = bb.getLeft() + framePadding.x;
        final float textMinY = bb.getTop() + framePadding.y;
        float textMaxX = bb.getRight() - framePadding.x;
        final float textMaxY = bb.getBottom();

        // Return the clipped state, ignoring the close button
        outClosedAndClipped[1] = (textMinX + labelSize.x) > textMaxX;

        final float buttonSize = IkGuiInternal.getFontSize();
        final float buttonX = Math.max(bb.getLeft(), bb.getRight() - framePadding.x - buttonSize);
        final float buttonY = bb.getTop() + framePadding.y;

        // Close button and unsaved marker. We rely on a subtle distinction between 'hovered' and
        // the hovered ID which happens because we are using allow overlap mode:
        // - 'hovered' will be true when hovering the tab but NOT when hovering the close button
        // - 'hoveredID == id' will be true when hovering the tab including the close button
        // - 'activeID == closeButtonID' will be true when we are holding the close button, in
        //   which case both hovered values are false
        boolean closeButtonPressed = false;
        boolean closeButtonVisible = false;
        final boolean isHovered =
                context.hoveredID == tabID
                        || context.hoveredID == closeButtonID
                        || context.activeID == tabID
                        || context.activeID == closeButtonID;

        if (closeButtonID != 0) {
            final float minWidth =
                    isContentsVisible
                            ? style.tabCloseButtonMinWidthSelected
                            : style.tabCloseButtonMinWidthUnselected;
            closeButtonVisible =
                    minWidth < 0.0f
                            || (isHovered && bb.getWidth() >= Math.max(buttonSize, minWidth));
        }

        // When the tab/document is unsaved, the unsaved marker takes priority over the close
        // button
        final boolean unsavedMarkerVisible =
                (tabItemFlags & TabItemFlags.UNSAVED_DOCUMENT) != 0
                        && (buttonX + buttonSize <= bb.getRight())
                        && (!closeButtonVisible || !isHovered);
        if (unsavedMarkerVisible) {
            IkGuiInternal.renderBullet(
                    context.windowCurrent.drawList,
                    buttonX + buttonSize * 0.5f,
                    buttonY + buttonSize * 0.5f,
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.UNSAVED_MARKER));
        } else if (closeButtonVisible) {
            final LastItemData lastItemBackup = new LastItemData();
            lastItemBackup.set(context.lastItemData);
            if (IkGuiImplWindows.closeButton(closeButtonID, buttonX, buttonY)) {
                closeButtonPressed = true;
            }
            context.lastItemData.set(lastItemBackup);

            // Close with the middle mouse button
            if (isHovered
                    && (tabItemFlags & TabItemFlags.NO_CLOSE_WITH_MIDDLE_MOUSE_BUTTON) == 0
                    && IkGuiImplUtils.isMouseClicked(MouseButton.MIDDLE, false)) {
                closeButtonPressed = true;
            }
        }

        // Because the close button only appears on hover, we don't want it to alter the ellipsis
        // position
        float ellipsisMaxX = textMaxX;
        if (closeButtonVisible || unsavedMarkerVisible) {
            final float minWidth =
                    isContentsVisible
                            ? style.tabCloseButtonMinWidthSelected
                            : style.tabCloseButtonMinWidthUnselected;
            final boolean visibleWithoutHover = unsavedMarkerVisible || minWidth < 0.0f;
            if (visibleWithoutHover) {
                textMaxX -= buttonSize * 0.90f;
                ellipsisMaxX -= buttonSize * 0.90f;
            } else {
                textMaxX -= buttonSize * 1.00f;
            }
        }
        IkGuiImplLogging.logSetNextTextDecoration("/", "\\");
        IkGuiInternal.renderTextEllipsis(
                textMinX, textMinY, textMaxX, textMaxY, ellipsisMaxX, displayedLabel, labelSize);

        outClosedAndClipped[0] = closeButtonPressed;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplTabs() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
