package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.*;
import com.ikalagaming.graphics.frontend.gui.flags.*;
import com.ikalagaming.graphics.frontend.gui.util.Hash;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

@Slf4j
class IkGuiImplWindows {

    static Context context;

    public static boolean begin(@NonNull String title, final IkBoolean open, int windowFlags) {
        final int ID = IkGui.pushID(title);

        boolean windowJustCreated = false;
        Window window = context.windowByID.getOrDefault(ID, null);

        if (window == null) {
            windowJustCreated = true;
            window = new Window(title);
            context.windowByID.put(ID, window);
        }

        if ((windowFlags & WindowFlags.NO_INPUTS) == WindowFlags.NO_INPUTS) {
            windowFlags |= WindowFlags.NO_MOVE | WindowFlags.NO_RESIZE;
        }

        final int currentFrame = context.frameCount;
        final boolean firstBeginOfFrame = window.lastFrameActive != currentFrame;

        window.isFallbackWindow =
                context.windowStack.isEmpty() && context.withinFrameScopeWithImplicitWindow;

        boolean windowJustActivatedByUser = window.lastFrameActive < currentFrame - 1;

        if ((windowFlags & WindowFlags.INTERNAL_POPUP) != 0) {
            PopupData popupReference = context.openPopupStack.peek();
            if (popupReference == null) {
                log.error(
                        "Calling begin with a popup flag, instead of going through the popup flow");
                return false;
            } else {
                // We recycle popups, so treat windows as activated if the popup ID changed
                windowJustActivatedByUser =
                        windowJustActivatedByUser
                                || (window.idAsPopupWindow != popupReference.popupID);
                windowJustActivatedByUser =
                        windowJustActivatedByUser || (window != popupReference.window);
            }
        }

        final boolean windowWasAppearing = window.appearing;

        if (firstBeginOfFrame) {
            // Update window in focus order list
            final boolean newIsExplicitChild =
                    (windowFlags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                            && ((windowFlags & WindowFlags.INTERNAL_POPUP) != 0
                                    || (windowFlags & WindowFlags.INTERNAL_CHILD_MENU) != 0);
            final boolean childFlagChanged = newIsExplicitChild != window.isExplicitChild;
            if ((windowJustCreated || childFlagChanged) && !newIsExplicitChild) {
                if (context.windowFocusOrder.contains(window)) {
                    log.error("Window already exists in focus order!");
                    return false;
                }
                context.windowFocusOrder.add(window);
            } else if (!windowJustCreated && childFlagChanged && newIsExplicitChild) {
                context.windowFocusOrder.remove(window);
            }
            window.isExplicitChild = newIsExplicitChild;

            window.appearing = windowJustActivatedByUser;
            if (windowJustActivatedByUser) {
                window.setConditionAllowFlags(ConditionAllowed.APPEARING, true);
            }
            window.flagsPreviousFrame = window.flags;
            window.flags = windowFlags;
            window.flagsAsChildWindow =
                    (context.nextWindowData.fieldFlags & NextWindowFlags.HAS_CHILD_FLAGS) != 0
                            ? context.nextWindowData.childFlags
                            : 0;
            window.lastFrameActive = currentFrame;
            window.lastTimeActive = context.time;
            window.beginOrderWithinParent = 0;
            window.beginOrderWithinContext = (short) context.windowActiveCount;
            context.windowActiveCount += 1;
        } else {
            windowFlags = window.flags;
        }

        if (window.dockNode != null && window.dockNodeAsHost != null) {
            log.error("CAnnot have both dock node and dock node as host set");
            return false;
        }

        // TODO(ches) port the rest of this
        window.id = ID;
        window.idMove = Hash.getID("#MOVE", ID);
        window.idWithinParent = 0;
        window.idAsPopupWindow = 0;
        window.childWindows.clear();
        // TODO(ches) handle parent windows
        window.scrollPosition.set(0, 0);
        window.scrollExtent.set(0, 0);
        window.scrollTarget.set(0, 0);
        if ((window.flags & WindowFlags.NO_SCROLLBAR) != 0) {
            window.showScrollbarX = Visibility.NEVER;
        } else if ((window.flags & WindowFlags.ALWAYS_HORIZONTAL_SCROLLBAR) != 0) {
            window.showScrollbarX = Visibility.ALWAYS;
        } else {
            window.showScrollbarX = Visibility.IF_REQUIRED;
        }
        if ((window.flags & WindowFlags.NO_SCROLLBAR) != 0) {
            window.showScrollbarY = Visibility.NEVER;
        } else if ((window.flags & WindowFlags.ALWAYS_VERTICAL_SCROLLBAR) != 0) {
            window.showScrollbarY = Visibility.ALWAYS;
        } else {
            window.showScrollbarY = Visibility.IF_REQUIRED;
        }
        window.hidden = false;
        window.skipRenderingContents = false;
        window.reuseLastFrameContents = false;
        window.appearing = false; // TODO(ches) calculate appearing
        window.borderBeingHovered = Direction.NONE;
        window.borderBeingDragged = Direction.NONE;
        window.idStack.clear();
        window.drawList.clear();
        window.setPos = false;
        window.treeDepth = 0;
        window.currentTableIndex = 0;
        window.itemWidthStack.clear();
        window.textWrapPositionStack.clear();

        IkGui.getStyleVarFloat2(StyleVariable.WINDOW_PADDING, window.padding);
        final float scrollbarSize = IkGui.getStyleVarFloat(StyleVariable.SCROLLBAR_SIZE);
        window.scrollbarSizes.set(scrollbarSize, scrollbarSize);
        final Vector2f framePadding = IkGui.getStyleVarFloat2(StyleVariable.FRAME_PADDING);

        if ((window.flags & WindowFlags.NO_TITLE_BAR) != 0) {
            window.titleBarHeight = 0;
        } else {
            window.titleBarHeight = IkGui.getTextLineHeight() + 2 * framePadding.y;
        }

        if ((window.flags & WindowFlags.MENU_BAR) != 0) {
            // TODO(ches) set height appropriately
            window.menuBarHeight = 15;
        } else {
            window.menuBarHeight = 0;
        }

        if (context.nextWindowData.viewport != null) {
            window.viewport = context.nextWindowData.viewport;
        } else {
            window.viewport = context.mainViewport;
        }
        if (ConditionAllowed.shouldResolve(
                context.nextWindowData.positionCondition, window.positionConditionAllowed)) {
            window.position.set(context.nextWindowData.positionValue);
            window.positionConditionAllowed =
                    ConditionAllowed.updateFlags(
                            context.nextWindowData.positionCondition,
                            window.positionConditionAllowed);
        }
        if (ConditionAllowed.shouldResolve(
                context.nextWindowData.sizeCondition, window.sizeConditionAllowed)) {
            window.sizeRequested.set(context.nextWindowData.sizeValue);
            window.sizeConditionAllowed =
                    ConditionAllowed.updateFlags(
                            context.nextWindowData.sizeCondition, window.sizeConditionAllowed);
        }
        if (ConditionAllowed.shouldResolve(
                context.nextWindowData.collapsedCondition, window.collapsedConditionAllowed)) {
            window.collapsed = context.nextWindowData.collapsedValue;
            window.sizeConditionAllowed =
                    ConditionAllowed.updateFlags(
                            context.nextWindowData.collapsedCondition,
                            window.collapsedConditionAllowed);
        }
        window.collapseToggleRequested = false;

        // TODO(ches) calculate actual sizes properly
        window.sizeCurrent.set(window.sizeRequested);
        window.sizeFull.set(window.sizeRequested);
        window.sizeDesired.set(window.sizeRequested);
        window.rounding = IkGui.getStyleVarFloat(StyleVariable.WINDOW_ROUNDING);
        window.borderSize = IkGui.getStyleVarFloat(StyleVariable.WINDOW_BORDER_SIZE);
        window.rectOuter.set(window.position, window.sizeFull);
        window.rectOuterClipped.set(window.rectOuter);
        window.rectInner.set(
                window.position.x + window.padding.x,
                window.position.y + window.padding.y + window.titleBarHeight + window.menuBarHeight,
                window.position.x + window.sizeFull.x - window.padding.x,
                window.position.y + window.sizeFull.y - window.padding.y);
        window.rectInnerClip.set(window.rectInner);
        window.rectContent.set(window.rectInner);
        window.rectCurrentClip.set(window.rectOuterClipped);

        window.baseOffsetCurrentLine = 0;
        window.baseOffsetPreviousLine = 0;
        window.currentItemWidth = 0.0f;
        window.currentTextWrapPosition = 0.0f;
        window.indent = 0.0f;
        window.sameLine = false;
        window.lineSizePrevious.set(0, 0);
        window.lineSizeCurrent.set(0, 0);
        window.cursorStartPosition.set(
                window.position.x + window.padding.x,
                window.position.y
                        + window.padding.y
                        + window.titleBarHeight
                        + window.menuBarHeight);
        window.cursorPosition.set(window.cursorStartPosition);
        window.cursorIdealMaxPosition.set(window.cursorPosition);
        window.cursorIdealMaxPosition.add(window.sizeRequested);
        window.cursorMaxPosition.set(window.cursorPosition);
        window.cursorMaxPosition.add(window.sizeCurrent);
        window.cursorPreviousLinePosition.set(window.cursorStartPosition);

        window.open = open;
        window.active = true;

        context.windowCurrent = window;
        context.lastItemData.id = window.id;
        context.lastItemData.statusFlags =
                ItemStatusFlags.HAS_CLIP_RECT
                        | ItemStatusFlags.HAS_DISPLAY_RECT
                        | ItemStatusFlags.VISIBLE;
        context.lastItemData.rect.set(window.rectOuter);
        context.lastItemData.clipRect.set(window.rectCurrentClip);
        context.lastItemData.displayRect.set(window.rectOuterClipped);
        context.lastItemData.shortcut = 0;
        context.windowDisplayOrder.add(window);

        context.drawData.drawLists.add(window.drawList);

        if ((window.flags & WindowFlags.NO_BACKGROUND) == 0) {
            window.drawList.addRectFilled(
                    window.position.x,
                    window.position.y,
                    window.position.x + window.sizeCurrent.x,
                    window.position.y + window.sizeCurrent.y,
                    IkGui.getColorWithGlobalAlpha(ColorType.WINDOW_BACKGROUND),
                    window.rounding);
        }

        if (window.titleBarHeight > 0) {
            ColorType titleColor = ColorType.TITLE_BACKGROUND;
            if (window.collapsed) {
                titleColor = ColorType.TITLE_BACKGROUND_COLLAPSED;
            } else if (window.active) {
                titleColor = ColorType.TITLE_BACKGROUND_ACTIVE;
            }
            window.drawList.addRectFilled(
                    window.position.x,
                    window.position.y,
                    window.position.x + window.sizeCurrent.x,
                    window.position.y + window.titleBarHeight,
                    IkGui.getColorWithGlobalAlpha(titleColor),
                    window.rounding,
                    DrawFlags.ROUND_CORNERS_TOP);

            // TODO(ches) handle special cases of names with # and ##
            // TODO(ches) handle title alignment
            window.drawList.addText(
                    context.fontSize,
                    window.position.x + framePadding.x,
                    window.position.y + framePadding.y,
                    IkGui.getColorWithGlobalAlpha(ColorType.TEXT),
                    window.name);
        }

        if ((window.flags & WindowFlags.NO_BACKGROUND) == 0 && window.borderSize > 0) {
            window.drawList.addRect(
                    window.position.x,
                    window.position.y,
                    window.position.x + window.sizeCurrent.x,
                    window.position.y + window.sizeCurrent.y,
                    IkGui.getColorWithGlobalAlpha(ColorType.BORDER),
                    window.rounding,
                    DrawFlags.ROUND_CORNERS_ALL,
                    window.borderSize);
        }
        return true;
    }

    public static void beginChild(
            int id, float width, float height, int childFlags, int windowFlags) {
        // TODO(ches) complete this
    }

    public static boolean beginMainMenuBar() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginMenu(String label, boolean enabled) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginMenuBar() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopup(int id, int windowFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextItem(int id, int popupFlags) {
        openPopupOnItemClick(id, popupFlags);
        return beginPopup(id, popupFlags);
    }

    public static boolean beginPopupContextVoid(int id, int popupFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextWindow(int id, int popupFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupModal(String name, IkBoolean open, int windowFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static int dockSpace(int id, float width, float height, int dockNodeFlags) {
        // TODO(ches) complete this
        return 0;
    }

    public static int dockSpaceOverViewport(Viewport viewport, int dockNodeFlags) {
        // TODO(ches) complete this
        return 0;
    }

    public static void openPopup(int id, int popupFlags) {
        // TODO(ches) complete this
    }

    public static void openPopupOnItemClick(int id, int popupFlags) {
        if (IkGuiInternal.isPopupRequestOpenForItem(id, popupFlags)) {
            openPopup(id, popupFlags);
        }
    }

    public static void beginTooltip() {
        // TODO(ches) complete this
    }

    public static void closeCurrentPopup() {
        // TODO(ches) complete this
    }

    public static void end() {
        // TODO(ches) complete this
        if (context.windowCurrent == null) {
            log.error("Trying to end a window that has not started");
            return;
        }

        IkGui.popID();

        context.windowCurrent.drawList.prepareForRender();
        context.windowCurrent = null;
    }

    public static void endChild() {
        // TODO(ches) complete this
    }

    public static void endMainMenuBar() {
        // TODO(ches) complete this
    }

    public static void endMenu() {
        // TODO(ches) complete this
    }

    public static void endMenuBar() {
        // TODO(ches) complete this
    }

    public static void endPopup() {
        // TODO(ches) complete this
    }

    public static void endTooltip() {
        // TODO(ches) complete this
    }

    public static int getWindowDockID() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getWindowHeight() {
        Window window = context.windowCurrent;
        return window == null ? 0 : window.sizeCurrent.y;
    }

    public static void getWindowPos(@NonNull Vector2f pos) {
        Window window = context.windowCurrent;
        if (window == null) {
            pos.set(0, 0);
        } else {
            pos.set(window.position);
        }
    }

    public static float getWindowPosX() {
        Window window = context.windowCurrent;
        return window == null ? 0 : window.position.x;
    }

    public static float getWindowPosY() {
        Window window = context.windowCurrent;
        return window == null ? 0 : window.position.y;
    }

    public static void getWindowSize(@NonNull Vector2f size) {
        Window window = context.windowCurrent;
        if (window == null) {
            size.set(0, 0);
        } else {
            size.set(window.sizeCurrent);
        }
    }

    public static float getWindowWidth() {
        Window window = context.windowCurrent;
        return window == null ? 0 : window.sizeCurrent.x;
    }

    public static boolean isPopupOpen(int id, int popupFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean menuItem(
            @NonNull String label, String shortcut, boolean selected, boolean enabled) {
        return false;
    }

    public static boolean menuItem(
            @NonNull String label, String shortcut, IkBoolean selected, boolean enabled) {
        return false;
    }

    public static void setNextWindowCollapsed(boolean collapsed, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static void setNextWindowDockID(int id, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static void setNextWindowFocus() {
        // TODO(ches) complete this
    }

    public static void setNextWindowPos(
            int x, int y, @NonNull Condition condition, int pivotX, int pivotY) {

        if (pivotX < 0f || pivotX > 1.0f) {
            log.error("Invalid pivotX in setNextWindowPos, should be in the range [0, 1]");
            return;
        }
        if (pivotY < 0f || pivotY > 1.0f) {
            log.error("Invalid pivotY in setNextWindowPos, should be in the range [0, 1]");
            return;
        }
        // TODO(ches) complete this

        context.nextWindowData.positionPivot.set(pivotX, pivotY);
        context.nextWindowData.positionValue.set(x, y);
        context.nextWindowData.positionCondition = condition;
    }

    public static void setNextWindowSize(int x, int y, @NonNull Condition condition) {
        // TODO(ches) complete this
        context.nextWindowData.sizeCondition = condition;
        context.nextWindowData.sizeValue.set(x, y);
    }

    public static void setTooltip(String text) {
        // TODO(ches) complete this
    }

    public static void setWindowCollapsed(boolean collapsed, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static void setWindowFocus() {
        // TODO(ches) complete this
    }

    public static void setWindowPos(
            @NonNull Window window, float x, float y, @NonNull Condition condition) {
        if (!ConditionAllowed.shouldResolve(condition, window.positionConditionAllowed)) {
            return;
        }

        window.positionConditionAllowed &=
                ~(ConditionAllowed.ONCE
                        | ConditionAllowed.FIRST_USE_EVER
                        | ConditionAllowed.APPEARING);
        window.setWindowPosValue.set(Float.MAX_VALUE, Float.MAX_VALUE);

        Vector2f oldPos = new Vector2f(window.position);
        window.position.set(x, y);
        IkGuiInternal.truncate(window.position);
        Vector2f offset = new Vector2f(window.position).sub(oldPos);
        if (offset.x == 0.0f && offset.y == 0.0f) {
            return;
        }

        IkGuiInternal.markIniSettingsDirty(window);
        window.cursorPosition.add(offset);
        window.cursorMaxPosition.add(offset);
        window.cursorIdealMaxPosition.add(offset);
        window.cursorStartPosition.add(offset);
    }

    public static void setWindowPos(
            float x, float y, @NonNull Condition condition, int pivotX, int pivotY) {
        // TODO(ches) complete this
    }

    public static void setWindowSize(float x, float y, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplWindows() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
