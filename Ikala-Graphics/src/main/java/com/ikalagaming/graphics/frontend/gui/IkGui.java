package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.callback.GuiInputTextCallback;
import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.*;
import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;
import com.ikalagaming.graphics.frontend.gui.flags.*;
import com.ikalagaming.graphics.frontend.gui.util.Color;
import com.ikalagaming.graphics.frontend.gui.util.Hash;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.NoSuchElementException;

/** Immediate mode GUI library based on <a href="https://github.com/ocornut/imgui">ImGui</a>. */
@Slf4j
public class IkGui {

    @Getter private static Context context;

    /**
     * Used to wrap a large frame count back to 0, which I like more than accidentally wrapping into
     * the negatives. We just need the numbers to be different each frame anyway, so this shouldn't
     * matter much.
     */
    private static final int FRAME_COUNT_CAP = 2_000_000_000;

    private static Storage storage;

    public static <T> T acceptDragDropPayload(Class<T> aClass) {
        // TODO(ches) complete this
        return null;
    }

    public static <T> T acceptDragDropPayload(Class<T> aClass, int dragDropFlags) {
        // TODO(ches) complete this
        return null;
    }

    public static <T> T acceptDragDropPayload(String dataType) {
        // TODO(ches) complete this
        return null;
    }

    public static <T> T acceptDragDropPayload(String dataType, Class<T> aClass) {
        // TODO(ches) complete this
        return null;
    }

    public static <T> T acceptDragDropPayload(String dataType, int dragDropFlags) {
        // TODO(ches) complete this
        return null;
    }

    public static <T> T acceptDragDropPayload(String dataType, int dragDropFlags, Class<T> aClass) {
        // TODO(ches) complete this
        return null;
    }

    public static void alignTextToFramePadding() {
        // TODO(ches) complete this
    }

    /**
     * Returns a color, represented as an int, with the current global alpha value applied to it.
     * This assumes the element is not disabled.
     *
     * @param color The original color.
     * @return The scaled color.
     */
    public static int applyGlobalAlpha(int color) {
        return applyGlobalAlpha(color, false);
    }

    /**
     * Returns a color, represented as an int, with the current global alpha value applied to it and
     * the disabled alpha if the element is disabled.
     *
     * @param color The original color.
     * @param disabled True if the element is disabled.
     * @return The scaled color.
     */
    public static int applyGlobalAlpha(int color, boolean disabled) {
        int result = color;
        result = Color.multiplyAlpha(result, context.style.variable.alpha);
        if (disabled) {
            result = Color.multiplyAlpha(result, context.style.variable.disabledAlpha);
        }
        return result;
    }

    public static boolean arrowButton(String text, @NonNull Direction direction) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean begin(@NonNull String title) {
        return begin(title, null, WindowFlags.NONE);
    }

    public static boolean begin(@NonNull String title, final IkBoolean open) {
        return begin(title, open, WindowFlags.NONE);
    }

    /**
     * Start a new window to add widgets to. The window name is a unique identifier used to preserve
     * information across frames. Every call to begin should be matched with a call to {@link
     * #end()} even if false is returned.
     *
     * @param title The unique title of the window.
     * @param open Non-null values display a close button on the window, which will be set to false
     *     if the close button is pressed.
     * @param windowFlags Flags for modifying the window.
     * @return false if the window is collapsed.
     */
    public static boolean begin(@NonNull String title, final IkBoolean open, int windowFlags) {
        // TODO(ches) complete this

        final int ID = pushID(title);

        Window window = context.windowByID.computeIfAbsent(ID, ignored -> new Window(title));

        if (WindowFlags.NONE == windowFlags) {
            window.flags = context.nextWindowData.windowFlags;
        } else {
            window.flags = windowFlags;
        }
        window.flagsAsChildWindow = WindowFlags.NONE;

        window.id = ID;
        window.idMove = Hash.getID("#MOVE", ID);
        window.idWithinParent = 0;
        window.idAsPopupWindow = 0;
        window.childWindows.clear();
        window.parentWindow = null;
        window.rootWindow = null;
        window.rootWindowIncludingPopups = null;
        window.rootWindowForTitleBarHighlight = null;
        window.rootWindowForNavigation = null;
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

        getStyleVarFloat2(StyleVariable.WINDOW_PADDING, window.padding);
        final float scrollbarSize = getStyleVarFloat(StyleVariable.SCROLLBAR_SIZE);
        window.scrollbarSizes.set(scrollbarSize, scrollbarSize);
        final Vector2f framePadding = getStyleVarFloat2(StyleVariable.FRAME_PADDING);

        if ((window.flags & WindowFlags.NO_TITLE_BAR) != 0) {
            window.titleBarHeight = 0;
        } else {
            window.titleBarHeight = getTextLineHeight() + 2 * framePadding.y;
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
        window.rounding = getStyleVarFloat(StyleVariable.WINDOW_ROUNDING);
        window.borderSize = getStyleVarFloat(StyleVariable.WINDOW_BORDER_SIZE);
        window.rectFull.set(window.position, window.sizeFull);
        window.rectInner.set(
                window.position.x + window.padding.x,
                window.position.y + window.padding.y + window.titleBarHeight + window.menuBarHeight,
                window.position.x + window.sizeFull.x - window.padding.x,
                window.position.y + window.sizeFull.y - window.padding.y);
        window.rectInnerClip.set(window.rectInner);
        window.rectContent.set(window.rectInner);
        window.rectCurrentClip.set(window.rectFull);

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
        context.lastItemData.rect.set(window.rectFull);
        context.lastItemData.clipRect.set(window.rectCurrentClip);
        context.lastItemData.displayRect.set(window.rectFull);
        context.lastItemData.shortcut = 0;
        context.windowDisplayOrder.add(window);

        context.drawData.drawLists.add(window.drawList);

        if ((window.flags & WindowFlags.NO_BACKGROUND) == 0) {
            window.drawList.addRectFilled(
                    window.position.x,
                    window.position.y,
                    window.position.x + window.sizeCurrent.x,
                    window.position.y + window.sizeCurrent.y,
                    getColorWithGlobalAlpha(ColorType.WINDOW_BACKGROUND),
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
                    getColorWithGlobalAlpha(titleColor),
                    window.rounding,
                    DrawFlags.ROUND_CORNERS_TOP);

            // TODO(ches) handle special cases of names with # and ##
            // TODO(ches) handle title alignment
            window.drawList.addText(
                    context.fontSize,
                    window.position.x + framePadding.x,
                    window.position.y + framePadding.y,
                    getColorWithGlobalAlpha(ColorType.TEXT),
                    window.name);
        }

        if ((window.flags & WindowFlags.NO_BACKGROUND) == 0 && window.borderSize > 0) {
            window.drawList.addRect(
                    window.position.x,
                    window.position.y,
                    window.position.x + window.sizeCurrent.x,
                    window.position.y + window.sizeCurrent.y,
                    getColorWithGlobalAlpha(ColorType.BORDER),
                    window.rounding,
                    DrawFlags.ROUND_CORNERS_ALL,
                    window.borderSize);
        }
        return true;
    }

    public static boolean begin(@NonNull String title, int windowFlags) {
        return begin(title, null, windowFlags);
    }

    public static void beginChild(int id) {
        // TODO(ches) complete this
    }

    public static void beginChild(int id, float width, float height) {
        // TODO(ches) complete this
    }

    public static void beginChild(int id, float width, float height, boolean border) {
        // TODO(ches) complete this
    }

    public static void beginChild(
            int id, float width, float height, boolean border, int windowFlags) {
        // TODO(ches) complete this
    }

    public static void beginChild(@NonNull String name) {
        // TODO(ches) complete this
    }

    public static void beginChild(@NonNull String name, float width, float height) {
        // TODO(ches) complete this
    }

    public static void beginChild(@NonNull String name, float width, float height, boolean border) {
        // TODO(ches) complete this
    }

    public static void beginChild(
            String name, float width, float height, boolean border, int windowFlags) {
        // TODO(ches) complete this
    }

    public static boolean beginChildFrame(int id, float width, float height) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginChildFrame(int id, float width, float height, int windowFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginCombo(String label, String previewValue) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginCombo(String label, String previewValue, int comboFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static void beginDisabled() {
        beginDisabled(true);
        // TODO(ches) complete this
    }

    public static void beginDisabled(boolean disabled) {
        // TODO(ches) complete this
    }

    public static boolean beginDragDropSource() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginDragDropSource(int dragDropFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginDragDropTarget() {
        // TODO(ches) complete this
        return false;
    }

    public static void beginGroup() {
        // TODO(ches) complete this
    }

    public static boolean beginListBox(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginListBox(String label, float width, float height) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginMainMenuBar() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginMenu(String label) {
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

    public static boolean beginPopup(int id) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopup(@NonNull String name) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopup(int id, int windowFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopup(@NonNull String name, int windowFlags) {
        // TODO(ches) complete this
        return false;
    }

    /**
     * Create a popup associated with the last item. Generally for things that don't have
     * identifiers, like text.
     *
     * @return Whether the popup is open.
     * @see #beginPopupContextItem(int, int)
     */
    public static boolean beginPopupContextItem() {
        return beginPopupContextItem(context.lastItemData.id, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Create a popup with an explicit ID.
     *
     * @param id The ID.
     * @return Whether the popup is open.
     * @see #beginPopupContextItem(int, int)
     */
    public static boolean beginPopupContextItem(int id) {
        return beginPopupContextItem(id, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Create a popup with an explicit ID.
     *
     * @param name The name of the popup.
     * @return Whether the popup is open.
     * @see #beginPopupContextItem(int, int)
     */
    public static boolean beginPopupContextItem(@NonNull String name) {
        return beginPopupContextItem(
                Hash.getID(name, context.idStack.peek()), PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Create a popup with an explicit ID.
     *
     * @param name The name of the popup.
     * @param popupFlags Flags for the popup.
     * @return Whether the popup is open.
     * @see PopupFlags
     * @see #beginPopupContextItem(int, int)
     */
    public static boolean beginPopupContextItem(@NonNull String name, int popupFlags) {
        return beginPopupContextItem(Hash.getID(name, context.idStack.peek()), popupFlags);
    }

    /**
     * Create a popup with an explicit ID.
     *
     * <p>Essentially the same as {@code openPopupOnItemClick(id, popupFlags); return
     * beginPopup(id); }. Which is essentially the same as {@code if (isItemHovered() &&
     * isMouseReleased(MouseButton.RIGHT) {openPopup(id);} return beginPopup(id); }.
     *
     * @param id The ID.
     * @param popupFlags Flags for the popup.
     * @return Whether the popup is open.
     * @see PopupFlags
     */
    public static boolean beginPopupContextItem(int id, int popupFlags) {
        openPopupOnItemClick(id, popupFlags);
        return beginPopup(id, popupFlags);
    }

    public static boolean beginPopupContextVoid() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextVoid(int id) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextVoid(String name) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextVoid(String name, int popupFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextWindow() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextWindow(int id) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextWindow(String name) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextWindow(String name, int popupFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupModal(String name) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupModal(String name, IkBoolean open) {
        return false;
    }

    public static boolean beginPopupModal(String name, IkBoolean open, int windowFlags) {
        return false;
    }

    public static boolean beginPopupModal(String name, int windowFlags) {
        return false;
    }

    public static boolean beginTabBar(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginTabBar(String label, int tabBarFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginTabItem(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginTabItem(String label, IkBoolean open) {
        return false;
    }

    public static boolean beginTabItem(String label, IkBoolean open, int tabItemFlags) {
        return false;
    }

    public static boolean beginTabItem(String label, int tabItemFlags) {
        return false;
    }

    public static boolean beginTable(String name, int columns) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginTable(String name, int columns, int tableFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginTable(
            String name, int columns, int tableFlags, float outerWidth, float outerHeight) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginTable(
            String name,
            int columns,
            int tableFlags,
            float outerWidth,
            float outerHeight,
            float innerWidth) {
        // TODO(ches) complete this
        return false;
    }

    public static void beginTooltip() {
        // TODO(ches) complete this
    }

    public static void bullet() {
        // TODO(ches) complete this
    }

    public static void bulletText(String text) {
        // TODO(ches) complete this
    }

    public static boolean button(String text) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean button(String text, float width, float height) {
        // TODO(ches) complete this
        return false;
    }

    public static Vector2f calcTextSize(String text) {
        Vector2f value = new Vector2f();
        calcTextSize(value, text);
        return value;
    }

    public static Vector2f calcTextSize(String text, boolean hideTextAfterDoubleHash) {
        Vector2f value = new Vector2f();
        calcTextSize(value, text, hideTextAfterDoubleHash);
        return value;
    }

    public static Vector2f calcTextSize(
            String text, boolean hideTextAfterDoubleHash, float wrapWidth) {
        Vector2f value = new Vector2f();
        calcTextSize(value, text, hideTextAfterDoubleHash, wrapWidth);
        return value;
    }

    public static void calcTextSize(Vector2f result, String text) {
        // TODO(ches) complete this
    }

    public static void calcTextSize(Vector2f result, String text, boolean hideTextAfterDoubleHash) {
        // TODO(ches) complete this
    }

    public static void calcTextSize(
            Vector2f result, String text, boolean hideTextAfterDoubleHash, float wrapWidth) {
        // TODO(ches) complete this
    }

    public static void calcTextSize(Vector2f result, String text, float wrapWidth) {
        // TODO(ches) complete this
    }

    public static float calculateItemWidth() {
        // TODO(ches) complete this
        return 0;
    }

    public static void captureKeyboardFromApp() {
        // TODO(ches) complete this
    }

    public static void captureKeyboardFromApp(boolean capture) {
        // TODO(ches) complete this
    }

    public static void captureMouseFromApp() {
        // TODO(ches) complete this
    }

    public static void captureMouseFromApp(boolean wantToCaptureNextFrame) {
        // TODO(ches) complete this
    }

    public static boolean checkbox(String label, boolean initialState) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean checkbox(String label, IkBoolean active) {
        return false;
    }

    public static boolean checkboxFlags(String label, IkInt flags, int flagsValue) {
        return false;
    }

    public static void closeCurrentPopup() {
        // TODO(ches) complete this
    }

    public static boolean collapsingHeader(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean collapsingHeader(String label, IkBoolean visible) {
        return false;
    }

    public static boolean collapsingHeader(String label, IkBoolean visible, int treeNodeFlags) {
        return false;
    }

    public static boolean collapsingHeader(String label, int treeNodeFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorButton(String label, float[] color) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorButton(String label, float[] color, int colorEditFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorButton(
            String label, float[] color, int colorEditFlags, float width, float height) {
        // TODO(ches) complete this
        return false;
    }

    public static int colorConvertFloat4ToU32(float r, float g, float b, float a) {
        // TODO(ches) complete this
        return 0;
    }

    public static void colorConvertHSVtoRGB(float[] in, float[] out) {
        // TODO(ches) complete this
    }

    public static void colorConvertRGBtoHSV(float[] in, float[] out) {
        // TODO(ches) complete this
    }

    public static final Vector4f colorConvertU32ToFloat4(int in) {
        Vector4f value = new Vector4f();
        colorConvertU32ToFloat4(in, value);
        return value;
    }

    public static void colorConvertU32ToFloat4(int in, Vector4f out) {
        // TODO(ches) complete this
    }

    public static boolean colorEdit3(String label, float[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorEdit3(String label, float[] value, int colorEditFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorEdit4(String label, float[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorEdit4(String label, float[] value, int colorEditFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorPicker3(String label, float[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorPicker3(String label, float[] value, int colorEditFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorPicker4(String label, float[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorPicker4(String label, float[] value, int colorEditFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorPicker4(
            String label, float[] value, int colorEditFlags, float referenceColor) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean combo(String label, IkInt currentItem, String itemsSeparatedByZeros) {
        return false;
    }

    public static boolean combo(
            String label,
            IkInt currentItem,
            String itemsSeparatedByZeros,
            int popumaxHeightInItems) {
        return false;
    }

    public static boolean combo(String label, IkInt currentItem, String[] items) {
        return false;
    }

    public static boolean combo(
            String label, IkInt currentItem, String[] items, int popumaxHeightInItems) {
        return false;
    }

    /**
     * Create a context. Must be called before doing anything that would require the context, which
     * is most things. Will only create one context, subsequent calls will just return the existing
     * context.
     *
     * @return The context.
     */
    public static Context createContext() {
        if (context != null) {
            return context;
        }
        context = new Context();
        IkGuiInternal.context = context;
        storage = new Storage();
        return context;
    }

    public static void destroyContext() {
        if (context == null) {
            return;
        }

        if (context.frameCountEnded != context.frameCount) {
            IkGui.endFrame();
        }
        context.io.setAppAcceptingEvents(false);
        context = null;
        IkGuiInternal.context = null;
        storage = null;
    }

    public static void destroyPlatformWindows() {
        // TODO(ches) complete this
    }

    public static int dockSpace(int id) {
        return 0;
    }

    public static int dockSpace(int id, float width, float height) {
        return 0;
    }

    public static int dockSpace(int id, float width, float height, int dockNodeFlags) {
        return 0;
    }

    public static int dockSpaceOverViewport() {
        return 0;
    }

    public static int dockSpaceOverViewport(Viewport viewport) {
        return 0;
    }

    public static int dockSpaceOverViewport(Viewport viewport, int dockNodeFlags) {
        return 0;
    }

    public static boolean dragFloat(String label, float[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat(String label, float[] value, float speed) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat(
            String label, float[] value, float speed, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat(
            String label, float[] value, float speed, float min, float max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat(
            String label,
            float[] value,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat2(String label, float[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat2(String label, float[] value, float speed) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat2(
            String label, float[] value, float speed, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat2(
            String label, float[] value, float speed, float min, float max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat2(
            String label,
            float[] value,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat3(String label, float[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat3(String label, float[] value, float speed) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat3(
            String label, float[] value, float speed, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat3(
            String label, float[] value, float speed, float min, float max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat3(
            String label,
            float[] value,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat4(String label, float[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat4(String label, float[] value, float speed) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat4(
            String label, float[] value, float speed, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat4(
            String label, float[] value, float speed, float min, float max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat4(
            String label,
            float[] value,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloatRange2(String label, float[] currentMin, float[] currentMax) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloatRange2(
            String label, float[] currentMin, float[] currentMax, float speed) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloatRange2(
            String label,
            float[] currentMin,
            float[] currentMax,
            float speed,
            float min,
            float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloatRange2(
            String label,
            float[] currentMin,
            float[] currentMax,
            float speed,
            float min,
            float max,
            String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloatRange2(
            String label,
            float[] currentMin,
            float[] currentMax,
            float speed,
            float min,
            float max,
            String format,
            String formatMax) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloatRange2(
            String label,
            float[] currentMin,
            float[] currentMax,
            float speed,
            float min,
            float max,
            String format,
            String formatMax,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt(String label, int[] values) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt(String label, int[] values, float speed) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt(String label, int[] values, float speed, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt(
            String label, int[] values, float speed, float min, float max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt2(String label, int[] values) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt2(String label, int[] values, float speed) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt2(String label, int[] values, float speed, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt2(
            String label, int[] values, float speed, float min, float max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt3(String label, int[] values) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt3(String label, int[] values, float speed) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt3(String label, int[] values, float speed, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt3(
            String label, int[] values, float speed, float min, float max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt4(String label, int[] values) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt4(String label, int[] values, float speed) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt4(String label, int[] values, float speed, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt4(
            String label, int[] values, float speed, float min, float max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragIntRange2(String label, int[] currentMin, int[] currentMax) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragIntRange2(
            String label, int[] currentMin, int[] currentMax, float speed) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragIntRange2(
            String label, int[] currentMin, int[] currentMax, float speed, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragIntRange2(
            String label,
            int[] currentMin,
            int[] currentMax,
            float speed,
            float min,
            float max,
            String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragIntRange2(
            String label,
            int[] currentMin,
            int[] currentMax,
            float speed,
            float min,
            float max,
            String format,
            String formatMax) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragScalar(String label, int dataType, IkDouble data, float speed) {
        return false;
    }

    public static boolean dragScalar(
            String label, int dataType, IkDouble data, float speed, double min) {
        return false;
    }

    public static boolean dragScalar(
            String label, int dataType, IkDouble data, float speed, double min, double max) {
        return false;
    }

    public static boolean dragScalar(
            String label,
            int dataType,
            IkDouble data,
            float speed,
            double min,
            double max,
            String format) {
        return false;
    }

    public static boolean dragScalar(
            String label,
            int dataType,
            IkDouble data,
            float speed,
            double min,
            double max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean dragScalar(String label, int dataType, IkFloat data, float speed) {
        return false;
    }

    public static boolean dragScalar(
            String label, int dataType, IkFloat data, float speed, float min) {
        return false;
    }

    public static boolean dragScalar(
            String label, int dataType, IkFloat data, float speed, float min, float max) {
        return false;
    }

    public static boolean dragScalar(
            String label,
            int dataType,
            IkFloat data,
            float speed,
            float min,
            float max,
            String format) {
        return false;
    }

    public static boolean dragScalar(
            String label,
            int dataType,
            IkFloat data,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean dragScalar(String label, int dataType, IkInt data, float speed) {
        return false;
    }

    public static boolean dragScalar(String label, int dataType, IkInt data, float speed, int min) {
        return false;
    }

    public static boolean dragScalar(
            String label, int dataType, IkInt data, float speed, int min, int max) {
        return false;
    }

    public static boolean dragScalar(
            String label, int dataType, IkInt data, float speed, int min, int max, String format) {
        return false;
    }

    public static boolean dragScalar(
            String label,
            int dataType,
            IkInt data,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean dragScalar(String label, int dataType, IkLong data, float speed) {
        return false;
    }

    public static boolean dragScalar(
            String label, int dataType, IkLong data, float speed, long min) {
        return false;
    }

    public static boolean dragScalar(
            String label, int dataType, IkLong data, float speed, long min, long max) {
        return false;
    }

    public static boolean dragScalar(
            String label,
            int dataType,
            IkLong data,
            float speed,
            long min,
            long max,
            String format) {
        return false;
    }

    public static boolean dragScalar(
            String label,
            int dataType,
            IkLong data,
            float speed,
            long min,
            long max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean dragScalar(String label, int dataType, IkShort data, float speed) {
        return false;
    }

    public static boolean dragScalar(
            String label, int dataType, IkShort data, float speed, short min) {
        return false;
    }

    public static boolean dragScalar(
            String label, int dataType, IkShort data, float speed, short min, short max) {
        return false;
    }

    public static boolean dragScalar(
            String label,
            int dataType,
            IkShort data,
            float speed,
            short min,
            short max,
            String format) {
        return false;
    }

    public static boolean dragScalar(
            String label,
            int dataType,
            IkShort data,
            float speed,
            short min,
            short max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean dragScalarN(
            String label, int dataType, IkDouble data, int components, float speed) {
        return false;
    }

    public static boolean dragScalarN(
            String label, int dataType, IkDouble data, int components, float speed, double min) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkDouble data,
            int components,
            float speed,
            double min,
            double max) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkDouble data,
            int components,
            float speed,
            double min,
            double max,
            String format) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkDouble data,
            int components,
            float speed,
            double min,
            double max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean dragScalarN(
            String label, int dataType, IkFloat data, int components, float speed) {
        return false;
    }

    public static boolean dragScalarN(
            String label, int dataType, IkFloat data, int components, float speed, float min) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkFloat data,
            int components,
            float speed,
            float min,
            float max) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkFloat data,
            int components,
            float speed,
            float min,
            float max,
            String format) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkFloat data,
            int components,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean dragScalarN(
            String label, int dataType, IkInt data, int components, float speed) {
        return false;
    }

    public static boolean dragScalarN(
            String label, int dataType, IkInt data, int components, float speed, int min) {
        return false;
    }

    public static boolean dragScalarN(
            String label, int dataType, IkInt data, int components, float speed, int min, int max) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkInt data,
            int components,
            float speed,
            int min,
            int max,
            String format) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkInt data,
            int components,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean dragScalarN(
            String label, int dataType, IkLong data, int components, float speed) {
        return false;
    }

    public static boolean dragScalarN(
            String label, int dataType, IkLong data, int components, float speed, long min) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkLong data,
            int components,
            float speed,
            long min,
            long max) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkLong data,
            int components,
            float speed,
            long min,
            long max,
            String format) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkLong data,
            int components,
            float speed,
            long min,
            long max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean dragScalarN(
            String label, int dataType, IkShort data, int components, float speed) {
        return false;
    }

    public static boolean dragScalarN(
            String label, int dataType, IkShort data, int components, float speed, short min) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkShort data,
            int components,
            float speed,
            short min,
            short max) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkShort data,
            int components,
            float speed,
            short min,
            short max,
            String format) {
        return false;
    }

    public static boolean dragScalarN(
            String label,
            int dataType,
            IkShort data,
            int components,
            float speed,
            short min,
            short max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static void dummy(float width, float height) {
        // TODO(ches) complete this
    }

    public static void end() {
        // TODO(ches) complete this
        if (context.windowCurrent == null) {
            log.error("Trying to end a window that has not started");
            return;
        }

        popID();

        context.windowCurrent.drawList.prepareForRender();
        context.windowCurrent = null;
    }

    public static void endChild() {
        // TODO(ches) complete this
    }

    public static void endChildFrame() {
        // TODO(ches) complete this
    }

    public static void endCombo() {
        // TODO(ches) complete this
    }

    public static void endDisabled() {
        // TODO(ches) complete this
    }

    public static void endDragDropSource() {
        // TODO(ches) complete this
    }

    public static void endDragDropTarget() {
        // TODO(ches) complete this
    }

    public static void endFrame() {
        // TODO(ches) complete this

        if (context.frameCountEnded == context.frameCount) {
            log.error("newFrame() must be called before endFrame()");
            return;
        }

        if (!context.styleVariableStack.isEmpty()) {
            log.error("Did not pop off all style variables before ending frame");
            context.styleVariableStack.clear();
        }

        // TODO(ches) update navigation
        // TODO(ches) update docking
        // TODO(ches) update drag and drop
        // TODO(ches) end the frame
        context.backgroundDrawList.prepareForRender();
        context.foregroundDrawList.prepareForRender();
        // TODO(ches) update viewports list
        // TODO(ches) sort the window list
        // TODO(ches) check the window parents/children are sane
        // TODO(ches) update textures
        // TODO(ches) unlock the font atlas
        context.io.mousePositionPrevious.set(context.io.mousePosition);
        context.io.appFocusLost = false;
        context.io.mouseWheel = 0.0f;
        context.io.mouseWheelH = 0.0f;
        // TODO(ches) clear input queue?

        // TODO(ches) call context hooks

        context.frameCountEnded = (context.frameCountEnded + 1) % FRAME_COUNT_CAP;
    }

    public static void endGroup() {
        // TODO(ches) complete this
    }

    public static void endListBox() {
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

    public static void endTabBar() {
        // TODO(ches) complete this
    }

    public static void endTabItem() {
        // TODO(ches) complete this
    }

    public static void endTable() {
        // TODO(ches) complete this
    }

    public static void endTooltip() {
        // TODO(ches) complete this
    }

    public static Viewport findViewportByID(int id) {
        // TODO(ches) complete this
        return null;
    }

    public static Viewport findViewportByPlatformHandle(long platformHandle) {
        // TODO(ches) complete this
        return null;
    }

    public static DrawList getBackgroundDrawList() {
        return null;
    }

    public static DrawList getBackgroundDrawList(Viewport viewport) {
        return null;
    }

    public static String getClipboardText() {
        // TODO(ches) complete this
        return null;
    }

    /**
     * Fetch the current style color as it is stored in the style, after color mod overrides, but
     * without the global alpha values.
     *
     * @param type The type of color we want.
     * @return The color in RGBA format.
     */
    public static int getColor(@NonNull ColorType type) {
        int result =
                switch (type) {
                    case ColorType.BORDER -> context.style.color.border;
                    case ColorType.BORDER_SHADOW -> context.style.color.borderShadow;
                    case ColorType.BUTTON -> context.style.color.button;
                    case ColorType.BUTTON_ACTIVE -> context.style.color.buttonActive;
                    case ColorType.BUTTON_HOVERED -> context.style.color.buttonHovered;
                    case ColorType.CHECK_MARK -> context.style.color.checkMark;
                    case ColorType.CHILD_BACKGROUND -> context.style.color.childBackground;
                    case ColorType.DOCKING_EMPTY_BACKGROUND ->
                            context.style.color.dockingEmptyBackground;
                    case ColorType.DOCKING_PREVIEW -> context.style.color.dockingPreview;
                    case ColorType.DRAG_DROP_TARGET -> context.style.color.dragDropTarget;
                    case ColorType.FRAME_BACKGROUND -> context.style.color.frameBackground;
                    case ColorType.FRAME_BACKGROUND_ACTIVE ->
                            context.style.color.frameBackgroundActive;
                    case ColorType.FRAME_BACKGROUND_HOVERED ->
                            context.style.color.frameBackgroundHovered;
                    case ColorType.HEADER -> context.style.color.header;
                    case ColorType.HEADER_ACTIVE -> context.style.color.headerActive;
                    case ColorType.HEADER_HOVERED -> context.style.color.headerHovered;
                    case ColorType.MENU_BAR_BACKGROUND -> context.style.color.menuBarBackground;
                    case ColorType.MODAL_WINDOWING_DIM_BACKGROUND ->
                            context.style.color.modalWindowingDimBackground;
                    case ColorType.NAV_HIGHLIGHT -> context.style.color.navHighlight;
                    case ColorType.NAV_WINDOWING_DIM_BACKGROUND ->
                            context.style.color.navWindowingDimBackground;
                    case ColorType.NAV_WINDOWING_HIGHLIGHT ->
                            context.style.color.navWindowingHighlight;
                    case ColorType.PLOT_HISTOGRAM -> context.style.color.plotHistogram;
                    case ColorType.PLOT_HISTOGRAM_HOVERED ->
                            context.style.color.plotHistogramHovered;
                    case ColorType.PLOT_LINES -> context.style.color.plotLines;
                    case ColorType.PLOT_LINES_HOVERED -> context.style.color.plotLinesHovered;
                    case ColorType.POPUP_BACKGROUND -> context.style.color.popupBackground;
                    case ColorType.RESIZE_GRIP -> context.style.color.resizeGrip;
                    case ColorType.RESIZE_GRIP_ACTIVE -> context.style.color.resizeGripActive;
                    case ColorType.RESIZE_GRIP_HOVERED -> context.style.color.resizeGripHovered;
                    case ColorType.SCROLLBAR_BACKGROUND -> context.style.color.scrollbarBackground;
                    case ColorType.SCROLLBAR_GRAB -> context.style.color.scrollbarGrab;
                    case ColorType.SCROLLBAR_GRAB_ACTIVE -> context.style.color.scrollbarGrabActive;
                    case ColorType.SCROLLBAR_GRAB_HOVERED ->
                            context.style.color.scrollbarGrabHovered;
                    case ColorType.SEPARATOR -> context.style.color.separator;
                    case ColorType.SEPARATOR_ACTIVE -> context.style.color.separatorActive;
                    case ColorType.SEPARATOR_HOVERED -> context.style.color.separatorHovered;
                    case ColorType.SLIDER_GRAB -> context.style.color.sliderGrab;
                    case ColorType.SLIDER_GRAB_ACTIVE -> context.style.color.sliderGrabActive;
                    case ColorType.TAB -> context.style.color.tab;
                    case ColorType.TABLE_BORDER_LIGHT -> context.style.color.tableBorderLight;
                    case ColorType.TABLE_BORDER_STRONG -> context.style.color.tableBorderStrong;
                    case ColorType.TABLE_HEADER_BACKGROUND ->
                            context.style.color.tableHeaderBackground;
                    case ColorType.TABLE_ROW_BACKGROUND -> context.style.color.tableRowBackground;
                    case ColorType.TABLE_ROW_BACKGROUND_ALT ->
                            context.style.color.tableRowBackgroundAlt;
                    case ColorType.TAB_DIMMED -> context.style.color.tabDimmed;
                    case ColorType.TAB_DIMMED_SELECTED -> context.style.color.tabDimmedSelected;
                    case ColorType.TAB_DIMMED_SELECTED_OVERLINE ->
                            context.style.color.tabDimmedSelectedOverline;
                    case ColorType.TAB_HOVERED -> context.style.color.tabHovered;
                    case ColorType.TAB_SELECTED -> context.style.color.tabSelected;
                    case ColorType.TAB_SELECTED_OVERLINE -> context.style.color.tabSelectedOverline;
                    case ColorType.TEXT -> context.style.color.text;
                    case ColorType.TEXT_DISABLED -> context.style.color.textDisabled;
                    case ColorType.TEXT_SELECTED_BACKGROUND ->
                            context.style.color.textSelectedBackground;
                    case ColorType.TITLE_BACKGROUND -> context.style.color.titleBackground;
                    case ColorType.TITLE_BACKGROUND_ACTIVE ->
                            context.style.color.titleBackgroundActive;
                    case ColorType.TITLE_BACKGROUND_COLLAPSED ->
                            context.style.color.titleBackgroundCollapsed;
                    case ColorType.WINDOW_BACKGROUND -> context.style.color.windowBackground;
                };

        for (var iterator = context.colorStack.descendingIterator(); iterator.hasNext(); ) {
            ColorMod mod = iterator.next();
            if (mod.type() == type) {
                result = mod.color();
                break;
            }
        }

        return result;
    }

    /**
     * Fetch the current style color from the style, after color mod overrides, and after applying
     * the specified alpha value (instead of the global alpha).
     *
     * @param type The color we want.
     * @param alphaMultiplier The alpha multiplier to use.
     * @return The color in RGBA format.
     */
    public static int getColor(@NonNull ColorType type, float alphaMultiplier) {
        int color = getColor(type);
        return Color.multiplyAlpha(color, alphaMultiplier);
    }

    /**
     * Fetch the current style color from the style, after color mod overrides, and after applying
     * the global alpha value.
     *
     * @param styleColor The color we want.
     * @return The color in RGBA format.
     */
    public static int getColorWithGlobalAlpha(@NonNull ColorType styleColor) {
        int color = getColor(styleColor);
        return applyGlobalAlpha(color);
    }

    /**
     * Fetch the current style color from the style, after color mod overrides, and * after applying
     * the global alpha value(s).
     *
     * @param styleColor The color we want.
     * @param disabled True if the element is disabled, so we know to apply the disabled alpha.
     * @return The color in RGBA format.
     */
    public static int getColorWithGlobalAlpha(@NonNull ColorType styleColor, boolean disabled) {
        int color = getColor(styleColor);
        return applyGlobalAlpha(color, disabled);
    }

    /**
     * Convert components to our color representation.
     *
     * @param r The red component, in the range 0-1 inclusive.
     * @param g The green component, in the range 0-1 inclusive.
     * @param b The blue component, in the range 0-1 inclusive.
     * @param a The alpha component, in the range 0-1 inclusive.
     * @return The color as an integer.
     */
    public static int getColor(float r, float g, float b, float a) {
        return Color.rgba(r, g, b, a);
    }

    public static Vector2f getContentRegionAvailable() {
        Vector2f region = new Vector2f();
        getContentRegionAvailable(region);
        return region;
    }

    public static void getContentRegionAvailable(@NonNull Vector2f region) {
        // TODO(ches) complete this
    }

    public static Vector2f getContentRegionAvailableMax() {
        Vector2f region = new Vector2f();
        getContentRegionAvailableMax(region);
        return region;
    }

    public static void getContentRegionAvailableMax(@NonNull Vector2f region) {
        // TODO(ches) complete this
    }

    public static float getContentRegionAvailableMaxX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getContentRegionAvailableMaxY() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getContentRegionAvailableMin() {
        Vector2f region = new Vector2f();
        getContentRegionAvailableMin(region);
        return region;
    }

    public static void getContentRegionAvailableMin(@NonNull Vector2f region) {
        // TODO(ches) complete this
    }

    public static float getContentRegionAvailableMinX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getContentRegionAvailableMinY() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getContentRegionAvailableX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getContentRegionAvailableY() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getCursorPos() {
        Vector2f pos = new Vector2f();
        getCursorPos(pos);
        return pos;
    }

    public static void getCursorPos(@NonNull Vector2f pos) {
        // TODO(ches) complete this
    }

    public static float getCursorPosX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getCursorPosY() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getCursorScreenPos() {
        Vector2f pos = new Vector2f();
        getCursorScreenPos(pos);
        return pos;
    }

    public static void getCursorScreenPos(@NonNull Vector2f pos) {
        // TODO(ches) complete this
    }

    public static float getCursorScreenPosX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getCursorScreenPosY() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getCursorStartPos() {
        Vector2f pos = new Vector2f();
        getCursorStartPos(pos);
        return pos;
    }

    public static void getCursorStartPos(@NonNull Vector2f pos) {
        // TODO(ches) complete this
    }

    public static float getCursorStartPosX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getCursorStartPosY() {
        // TODO(ches) complete this
        return 0;
    }

    public static <T> T getDragDropPayload() {
        // TODO(ches) complete this
        return null;
    }

    public static <T> T getDragDropPayload(Class<T> aClass) {
        // TODO(ches) complete this
        return null;
    }

    public static <T> T getDragDropPayload(String dataType) {
        // TODO(ches) complete this
        return null;
    }

    public static int getFontSize() {
        return context.fontSize;
    }

    public static DrawList getForegroundDrawList() {
        return null;
    }

    public static DrawList getForegroundDrawList(Viewport viewport) {
        return null;
    }

    public static int getFrameCount() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getFrameHeight() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getFrameHeightWithSpacing() {
        // TODO(ches) complete this
        return 0;
    }

    public static int getID(long id) {
        // TODO(ches) complete this
        return 0;
    }

    public static int getID(String name) {
        return Hash.getID(name, context.idStack.peek());
    }

    public static IkIO getIO() {
        return context.io;
    }

    public static float getItemRectHeight() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getItemRectMax() {
        Vector2f value = new Vector2f();
        getItemRectMax(value);
        return value;
    }

    public static void getItemRectMax(Vector2f value) {
        // TODO(ches) complete this
    }

    public static float getItemRectMaxX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getItemRectMaxY() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getItemRectMin() {
        Vector2f value = new Vector2f();
        getItemRectMin(value);
        return value;
    }

    public static void getItemRectMin(Vector2f value) {
        // TODO(ches) complete this
    }

    public static float getItemRectMinX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getItemRectMinY() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getItemRectSize() {
        Vector2f value = new Vector2f();
        getItemRectSize(value);
        return value;
    }

    public static void getItemRectSize(Vector2f value) {
        // TODO(ches) complete this
    }

    public static float getItemRectWidth() {
        // TODO(ches) complete this
        return 0;
    }

    public static int getKeyIndex(@NonNull Key key) {
        // TODO(ches) complete this
        return 0;
    }

    public static boolean getKeyPressedAmount(int userKeyIndex, float repeatDelay, float rate) {
        // TODO(ches) complete this
        return false;
    }

    public static Viewport getMainViewport() {
        return context.mainViewport;
    }

    public static int getMouseClickedCount(@NonNull MouseButton button) {
        // TODO(ches) complete this
        return 0;
    }

    public static int getMouseCursor() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getMouseDragDelta() {
        Vector2f value = new Vector2f();
        getMouseDragDelta(value);
        return value;
    }

    public static Vector2f getMouseDragDelta(@NonNull MouseButton button) {
        Vector2f value = new Vector2f();
        getMouseDragDelta(value, button);
        return value;
    }

    public static Vector2f getMouseDragDelta(@NonNull MouseButton button, float lockThreshold) {
        Vector2f value = new Vector2f();
        getMouseDragDelta(value, button, lockThreshold);
        return value;
    }

    public static void getMouseDragDelta(Vector2f output) {
        // TODO(ches) complete this
    }

    public static void getMouseDragDelta(Vector2f output, @NonNull MouseButton button) {
        // TODO(ches) complete this
    }

    public static void getMouseDragDelta(
            Vector2f output, @NonNull MouseButton button, float lockThreshold) {
        // TODO(ches) complete this
    }

    public static float getMouseDragDeltaX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getMouseDragDeltaX(@NonNull MouseButton button) {
        // TODO(ches) complete this
        return 0;
    }

    public static float getMouseDragDeltaX(@NonNull MouseButton button, float lockThreshold) {
        // TODO(ches) complete this
        return 0;
    }

    public static float getMouseDragDeltaY() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getMouseDragDeltaY(@NonNull MouseButton button) {
        // TODO(ches) complete this
        return 0;
    }

    public static float getMouseDragDeltaY(@NonNull MouseButton button, float lockThreshold) {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getMousePos() {
        Vector2f value = new Vector2f();
        getMousePos(value);
        return value;
    }

    public static void getMousePos(Vector2f output) {
        // TODO(ches) complete this
    }

    public static Vector2f getMousePosOnOpeningCurrentPopup() {
        Vector2f value = new Vector2f();
        getMousePosOnOpeningCurrentPopup(value);
        return value;
    }

    public static void getMousePosOnOpeningCurrentPopup(Vector2f output) {
        // TODO(ches) complete this
    }

    public static float getMousePosOnOpeningCurrentPopupX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getMousePosOnOpeningCurrentPopupY() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getMousePosX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getMousePosY() {
        // TODO(ches) complete this
        return 0;
    }

    public static PlatformIO getPlatformIO() {
        return context.platformIO;
    }

    public static float getScrollX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getScrollY() {
        // TODO(ches) complete this
        return 0;
    }

    public static GuiStorage getStateStorage() {
        // TODO(ches) complete this
        return null;
    }

    /**
     * Fetch the current style variable, inclusive of style mods. If the variable is of a different
     * type or cardinality, this won't work and 0 will be returned.
     *
     * @param variable The variable to read.
     * @return The current value after mods.
     */
    public static float getStyleVarFloat(@NonNull StyleVariable variable) {
        if (variable.getDimensions() != 1) {
            log.error(
                    "Style variable {} has {} dimensions, trying to fetch 1 float",
                    variable,
                    variable.getDimensions());
            return 0;
        }
        if (variable.getExpectedType() != Float.class) {
            log.error(
                    "Style variable {} is a {} value, trying to fetch as float",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return 0;
        }

        float result =
                switch (variable) {
                    case ALPHA -> context.style.variable.alpha;
                    case CHILD_BORDER_SIZE -> context.style.variable.childBorderSize;
                    case CHILD_ROUNDING -> context.style.variable.childRounding;
                    case DISABLED_ALPHA -> context.style.variable.disabledAlpha;
                    case FRAME_BORDER_SIZE -> context.style.variable.frameBorderSize;
                    case FRAME_ROUNDING -> context.style.variable.frameRounding;
                    case GRAB_MIN_SIZE -> context.style.variable.grabMinSize;
                    case GRAB_ROUNDING -> context.style.variable.grabRounding;
                    case INDENT_SPACING -> context.style.variable.indentSpacing;
                    case LOG_SLIDER_DEADZONE -> context.style.variable.logSliderDeadzone;
                    case POPUP_BORDER_SIZE -> context.style.variable.popupBorderSize;
                    case POPUP_ROUNDING -> context.style.variable.popupRounding;
                    case SCROLLBAR_ROUNDING -> context.style.variable.scrollbarRounding;
                    case SCROLLBAR_SIZE -> context.style.variable.scrollbarSize;
                    case SEPARATOR_TEXT_BORDER_SIZE ->
                            context.style.variable.separatorTextBorderSize;
                    case TAB_BAR_BORDER_SIZE -> context.style.variable.tabBarBorderSize;
                    case TAB_ROUNDING -> context.style.variable.tabRounding;
                    case TABLE_ANGLED_HEADERS_ANGLE ->
                            context.style.variable.tableAngledHeadersAngle;
                    case WINDOW_BORDER_SIZE -> context.style.variable.windowBorderSize;
                    case WINDOW_ROUNDING -> context.style.variable.windowRounding;
                    default -> {
                        log.error(
                                "Trying to fetch 1 float value for unexpected style variable {}",
                                variable);
                        yield 0;
                    }
                };

        for (var iterator = context.styleVariableStack.descendingIterator(); iterator.hasNext(); ) {
            StyleMod mod = iterator.next();
            if (mod.type() == variable) {
                result = mod.x();
                break;
            }
        }
        return result;
    }

    /**
     * Fetch the current style variable, inclusive of style mods. Creates a new Vec2 for the
     * results. If the variable is of a different type or cardinality, this won't work and 0 will be
     * returned.
     *
     * @param variable The variable to read.
     * @return The value after style mods.
     */
    public static Vector2f getStyleVarFloat2(@NonNull StyleVariable variable) {
        Vector2f result = new Vector2f(0, 0);
        getStyleVarFloat2(variable, result);
        return result;
    }

    /**
     * Fetch the current style variable, inclusive of style mods, and store it in the target Vec2.
     * If the variable is of a different type or cardinality, this won't work and 0 will be
     * returned.
     *
     * @param variable The variable to read.
     * @param target Where to store the values.
     */
    public static void getStyleVarFloat2(
            @NonNull StyleVariable variable, @NonNull Vector2f target) {
        if (variable.getDimensions() != 2) {
            log.error(
                    "Style variable {} has {} dimensions, trying to fetch 2 floats",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Float.class) {
            log.error(
                    "Style variable {} is a {} value, trying to fetch as 2 floats",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }

        switch (variable) {
            case BUTTON_TEXT_ALIGN:
                target.set(context.style.variable.buttonTextAlign);
                break;
            case CELL_PADDING:
                target.set(context.style.variable.cellPadding);
                break;
            case FRAME_PADDING:
                target.set(context.style.variable.framePadding);
                break;
            case ITEM_INNER_SPACING:
                target.set(context.style.variable.itemInnerSpacing);
                break;
            case ITEM_SPACING:
                target.set(context.style.variable.itemSpacing);
                break;
            case SELECTABLE_TEXT_ALIGN:
                target.set(context.style.variable.selectableTextAlign);
                break;
            case SEPARATOR_TEXT_ALIGN:
                target.set(context.style.variable.separatorTextAlign);
                break;
            case SEPARATOR_TEXT_PADDING:
                target.set(context.style.variable.separatorTextPadding);
                break;
            case TABLE_ANGLED_HEADERS_TEXT_ALIGN:
                target.set(context.style.variable.tableAngledHeadersTextAlign);
                break;
            case TOUCH_EXTRA_PADDING:
                target.set(context.style.variable.touchExtraPadding);
                break;
            case WINDOW_MIN_SIZE:
                target.set(context.style.variable.windowMinSize);
                break;
            case WINDOW_PADDING:
                target.set(context.style.variable.windowPadding);
                break;
            case WINDOW_TITLE_ALIGN:
                target.set(context.style.variable.windowTitleAlign);
                break;
            default:
                log.error(
                        "Trying to fetch 2 float values for unexpected style variable {}",
                        variable);
                break;
        }

        for (var iterator = context.styleVariableStack.descendingIterator(); iterator.hasNext(); ) {
            StyleMod mod = iterator.next();
            if (mod.type() == variable) {
                target.set(mod.x(), mod.y());
                break;
            }
        }
    }

    /**
     * Fetch the current style variable, inclusive of style mods. If the variable is of a different
     * type or cardinality, this won't work and 0 will be returned.
     *
     * @param variable The variable to read.
     * @return The current value after mods.
     */
    public static int getStyleVarInt(@NonNull StyleVariable variable) {
        if (variable.getDimensions() != 1) {
            log.error(
                    "Style variable {} has {} dimensions, trying to fetch 1 int",
                    variable,
                    variable.getDimensions());
            return 0;
        }
        if (variable.getExpectedType() != Integer.class) {
            log.error(
                    "Style variable {} is a {} value, trying to fetch as int",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return 0;
        }
        int result =
                switch (variable) {
                    case COLOR_BUTTON_POSITION ->
                            context.style.variable.colorButtonPosition.getIntValue();
                    case WINDOW_MENU_BUTTON_POSITION ->
                            context.style.variable.windowMenuButtonPosition.getIntValue();
                    default -> {
                        log.error(
                                "Trying to fetch 1 int value for unexpected style variable {}",
                                variable);
                        yield 0;
                    }
                };

        for (var iterator = context.styleVariableStack.descendingIterator(); iterator.hasNext(); ) {
            StyleMod mod = iterator.next();
            if (mod.type() == variable) {
                result = (int) mod.x();
                break;
            }
        }

        return result;
    }

    public static int getTextLineHeight() {
        float fontScale = (float) context.dpiScaleScreen / context.dpiScaleFont;
        return (int) (fontScale * context.fontSize);
    }

    public static int getTextLineHeightWithSpacing() {
        float fontScale = (float) context.dpiScaleScreen / context.dpiScaleFont;
        return (int) (fontScale * 1.1f * context.fontSize);
    }

    /**
     * Return the context time.
     *
     * @return Total time elapsed since the context was initialized, in milliseconds.
     */
    public static long getTime() {
        return context.time;
    }

    public static float getTreeNodeToLabelSpacing() {
        // TODO(ches) complete this
        return 0;
    }

    public static int getWindowDockID() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getWindowHeight() {
        Window window = context.windowCurrent;
        return window == null ? 0 : window.sizeCurrent.y;
    }

    public static Vector2f getWindowPos() {
        Vector2f pos = new Vector2f();
        getWindowPos(pos);
        return pos;
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

    public static Vector2f getWindowSize() {
        Vector2f size = new Vector2f();
        getWindowSize(size);
        return size;
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

    public static void image(int textureID, float width, float height) {
        // TODO(ches) complete this
    }

    public static void image(int textureID, float width, float height, float u, float value) {
        // TODO(ches) complete this
    }

    public static void image(
            int textureID, float width, float height, float u0, float v0, float u1, float v1) {
        // TODO(ches) complete this
        // TODO(ches) variations of parameters
    }

    public static void image(
            int textureID,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            float tintR,
            float tintG,
            float tintB,
            float tintA) {
        // TODO(ches) complete this
        // TODO(ches) variations of parameters
    }

    public static void image(
            int textureID,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            float tintR,
            float tintG,
            float tintB,
            float tintA,
            float borderR,
            float borderG,
            float borderB,
            float borderA) {
        // TODO(ches) complete this
        // TODO(ches) variations of parameters
    }

    public static void image(int textureID, float width, float height, Vector2f uv0) {
        image(textureID, width, height, uv0.x, uv0.y);
    }

    public static void image(int textureID, Vector2f size) {
        image(textureID, size.x, size.y);
    }

    public static void image(int textureID, Vector2f size, float u, float value) {
        image(textureID, size.x, size.y, u, value);
    }

    public static void image(int textureID, Vector2f size, Vector2f uv0) {
        image(textureID, size.x, size.y, uv0.x, uv0.y);
    }

    public static void image(
            int textureID,
            Vector2f size,
            Vector2f uv0,
            Vector2f uv1,
            Vector4f tintColor,
            Vector4f borderColor) {
        image(
                textureID,
                size.x,
                size.y,
                uv0.x,
                uv0.y,
                uv1.x,
                uv1.y,
                tintColor.x,
                tintColor.y,
                tintColor.z,
                tintColor.w,
                borderColor.x,
                borderColor.y,
                borderColor.z,
                borderColor.w);
    }

    public static boolean imageButton(int textureID, float width, float height) {
        // TODO(ches) complete this
        return false;
        // TODO(ches) variations of parameters
    }

    public static boolean imageButton(
            int textureID, float width, float height, float u, float value) {
        // TODO(ches) complete this
        return false;
        // TODO(ches) variations of parameters
    }

    public static boolean imageButton(
            int textureID, float width, float height, float u0, float v0, float u1, float v1) {
        // TODO(ches) complete this
        return false;
        // TODO(ches) variations of parameters
    }

    public static boolean imageButton(
            int textureID,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            int framePadding) {
        // TODO(ches) complete this
        return false;
        // TODO(ches) variations of parameters
    }

    public static boolean imageButton(
            int textureID,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            int framePadding,
            float backgroundR,
            float backgroundG,
            float backgroundB,
            float backgroundA) {
        // TODO(ches) complete this
        return false;
        // TODO(ches) variations of parameters
    }

    public static boolean imageButton(
            int textureID,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            int framePadding,
            float backgroundR,
            float backgroundG,
            float backgroundB,
            float backgroundA,
            float tintR,
            float tintG,
            float tintB,
            float tintA) {
        // TODO(ches) complete this
        return false;
        // TODO(ches) variations of parameters
    }

    public static void indent() {
        // TODO(ches) complete this
    }

    public static void indent(float width) {
        // TODO(ches) complete this
    }

    public static boolean inputDouble(String label, IkDouble value) {
        return false;
    }

    public static boolean inputDouble(String label, IkDouble value, double step) {
        return false;
    }

    public static boolean inputDouble(String label, IkDouble value, double step, double stepFast) {
        return false;
    }

    public static boolean inputDouble(
            String label, IkDouble value, double step, double stepFast, String format) {
        return false;
    }

    public static boolean inputDouble(
            String label,
            IkDouble value,
            double step,
            double stepFast,
            String format,
            int inputTextFlags) {
        return false;
    }

    public static boolean inputFloat(String label, IkFloat value) {
        return false;
    }

    public static boolean inputFloat(String label, IkFloat value, float step) {
        return false;
    }

    public static boolean inputFloat(String label, IkFloat value, float step, float stepFast) {
        return false;
    }

    public static boolean inputFloat(
            String label, IkFloat value, float step, float stepFast, String format) {
        return false;
    }

    public static boolean inputFloat(
            String label,
            IkFloat value,
            float step,
            float stepFast,
            String format,
            int inputTextFlags) {
        return false;
    }

    public static boolean inputFloat2(String label, float[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat2(String label, float[] value, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat2(
            String label, float[] value, String format, int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat2(String label, int[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat2(String label, int[] value, int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat3(String label, float[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat3(String label, float[] value, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat3(
            String label, float[] value, String format, int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat3(String label, int[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat3(String label, int[] value, int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat4(String label, float[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat4(String label, float[] value, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat4(
            String label, float[] value, String format, int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat4(String label, int[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat4(String label, int[] value, int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputInt(String label, IkInt value) {
        return false;
    }

    public static boolean inputInt(String label, IkInt value, int step) {
        return false;
    }

    public static boolean inputInt(String label, IkInt value, int step, int stepFast) {
        return false;
    }

    public static boolean inputInt(
            String label, IkInt value, int step, int stepFast, int inputTextFlags) {
        return false;
    }

    public static boolean inputScalar(String label, int dataType, IkDouble data) {
        return false;
    }

    public static boolean inputScalar(String label, int dataType, IkDouble data, double step) {
        return false;
    }

    public static boolean inputScalar(
            String label, int dataType, IkDouble data, double step, double stepFast) {
        return false;
    }

    public static boolean inputScalar(
            String label,
            int dataType,
            IkDouble data,
            double step,
            double stepFast,
            String format) {
        return false;
    }

    public static boolean inputScalar(
            String label,
            int dataType,
            IkDouble data,
            double step,
            double stepFast,
            String format,
            int inputTextFlags) {
        return false;
    }

    public static boolean inputScalar(String label, int dataType, IkFloat data) {
        return false;
    }

    public static boolean inputScalar(String label, int dataType, IkFloat data, float step) {
        return false;
    }

    public static boolean inputScalar(
            String label, int dataType, IkFloat data, float step, float stepFast) {
        return false;
    }

    public static boolean inputScalar(
            String label, int dataType, IkFloat data, float step, float stepFast, String format) {
        return false;
    }

    public static boolean inputScalar(
            String label,
            int dataType,
            IkFloat data,
            float step,
            float stepFast,
            String format,
            int inputTextFlags) {
        return false;
    }

    public static boolean inputScalar(String label, int dataType, IkInt data) {
        return false;
    }

    public static boolean inputScalar(String label, int dataType, IkInt data, int step) {
        return false;
    }

    public static boolean inputScalar(
            String label, int dataType, IkInt data, int step, int stepFast) {
        return false;
    }

    public static boolean inputScalar(
            String label, int dataType, IkInt data, int step, int stepFast, String format) {
        return false;
    }

    public static boolean inputScalar(
            String label,
            int dataType,
            IkInt data,
            int step,
            int stepFast,
            String format,
            int inputTextFlags) {
        return false;
    }

    public static boolean inputScalar(String label, int dataType, IkLong data) {
        return false;
    }

    public static boolean inputScalar(String label, int dataType, IkLong data, long step) {
        return false;
    }

    public static boolean inputScalar(
            String label, int dataType, IkLong data, long step, long stepFast) {
        return false;
    }

    public static boolean inputScalar(
            String label, int dataType, IkLong data, long step, long stepFast, String format) {
        return false;
    }

    public static boolean inputScalar(
            String label,
            int dataType,
            IkLong data,
            long step,
            long stepFast,
            String format,
            int inputTextFlags) {
        return false;
    }

    public static boolean inputScalar(String label, int dataType, IkShort data) {
        return false;
    }

    public static boolean inputScalar(String label, int dataType, IkShort data, short step) {
        return false;
    }

    public static boolean inputScalar(
            String label, int dataType, IkShort data, short step, short stepFast) {
        return false;
    }

    public static boolean inputScalar(
            String label, int dataType, IkShort data, short step, short stepFast, String format) {
        return false;
    }

    public static boolean inputScalar(
            String label,
            int dataType,
            IkShort data,
            short step,
            short stepFast,
            String format,
            int inputTextFlags) {
        return false;
    }

    public static boolean inputScalarN(String label, int dataType, IkDouble data, int components) {
        return false;
    }

    public static boolean inputScalarN(
            String label, int dataType, IkDouble data, int components, double step) {
        return false;
    }

    public static boolean inputScalarN(
            String label,
            int dataType,
            IkDouble data,
            int components,
            double step,
            double stepFast) {
        return false;
    }

    public static boolean inputScalarN(
            String label,
            int dataType,
            IkDouble data,
            int components,
            double step,
            double stepFast,
            String format) {
        return false;
    }

    public static boolean inputScalarN(
            String label,
            int dataType,
            IkDouble data,
            int components,
            double step,
            double stepFast,
            String format,
            int inputTextFlags) {
        return false;
    }

    public static boolean inputScalarN(String label, int dataType, IkFloat data, int components) {
        return false;
    }

    public static boolean inputScalarN(
            String label, int dataType, IkFloat data, int components, float step) {
        return false;
    }

    public static boolean inputScalarN(
            String label, int dataType, IkFloat data, int components, float step, float stepFast) {
        return false;
    }

    public static boolean inputScalarN(
            String label,
            int dataType,
            IkFloat data,
            int components,
            float step,
            float stepFast,
            String format) {
        return false;
    }

    public static boolean inputScalarN(
            String label,
            int dataType,
            IkFloat data,
            int components,
            float step,
            float stepFast,
            String format,
            int inputTextFlags) {
        return false;
    }

    public static boolean inputScalarN(String label, int dataType, IkInt data, int components) {
        return false;
    }

    public static boolean inputScalarN(
            String label, int dataType, IkInt data, int components, int step) {
        return false;
    }

    public static boolean inputScalarN(
            String label, int dataType, IkInt data, int components, int step, int stepFast) {
        return false;
    }

    public static boolean inputScalarN(
            String label,
            int dataType,
            IkInt data,
            int components,
            int step,
            int stepFast,
            String format) {
        return false;
    }

    public static boolean inputScalarN(
            String label,
            int dataType,
            IkInt data,
            int components,
            int step,
            int stepFast,
            String format,
            int inputTextFlags) {
        return false;
    }

    public static boolean inputScalarN(String label, int dataType, IkLong data, int components) {
        return false;
    }

    public static boolean inputScalarN(
            String label, int dataType, IkLong data, int components, long step) {
        return false;
    }

    public static boolean inputScalarN(
            String label, int dataType, IkLong data, int components, long step, long stepFast) {
        return false;
    }

    public static boolean inputScalarN(
            String label,
            int dataType,
            IkLong data,
            int components,
            long step,
            long stepFast,
            String format) {
        return false;
    }

    public static boolean inputScalarN(
            String label,
            int dataType,
            IkLong data,
            int components,
            long step,
            long stepFast,
            String format,
            int inputTextFlags) {
        return false;
    }

    public static boolean inputScalarN(String label, int dataType, IkShort data, int components) {
        return false;
    }

    public static boolean inputScalarN(
            String label, int dataType, IkShort data, int components, short step) {
        return false;
    }

    public static boolean inputScalarN(
            String label, int dataType, IkShort data, int components, short step, short stepFast) {
        return false;
    }

    public static boolean inputScalarN(
            String label,
            int dataType,
            IkShort data,
            int components,
            short step,
            short stepFast,
            String format) {
        return false;
    }

    public static boolean inputScalarN(
            String label,
            int dataType,
            IkShort data,
            int components,
            short step,
            short stepFast,
            String format,
            int inputTextFlags) {
        return false;
    }

    public static boolean inputText(String label, IkString text) {
        return false;
    }

    public static boolean inputText(String label, IkString text, int inputTextFlags) {
        return false;
    }

    public static boolean inputText(
            String label, IkString text, int inputTextFlags, GuiInputTextCallback callback) {
        return false;
    }

    public static boolean inputTextMultiline(String label, IkString text) {
        return false;
    }

    public static boolean inputTextMultiline(
            String label, IkString text, float width, float height) {
        return false;
    }

    public static boolean inputTextMultiline(
            String label, IkString text, float width, float height, int inputTextFlags) {
        return false;
    }

    public static boolean inputTextMultiline(
            String label,
            IkString text,
            float width,
            float height,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return false;
    }

    public static boolean inputTextMultiline(String label, IkString text, int inputTextFlags) {
        return false;
    }

    public static boolean inputTextMultiline(
            String label, IkString text, int inputTextFlags, GuiInputTextCallback callback) {
        return false;
    }

    public static boolean inputTextWithHint(String label, String hint, IkString text) {
        return false;
    }

    public static boolean inputTextWithHint(
            String label, String hint, IkString text, int inputTextFlags) {
        return false;
    }

    public static boolean inputTextWithHint(
            String label,
            String hint,
            IkString text,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return false;
    }

    public static boolean invisibleButton(String text, float width, float height) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean invisibleButton(String text, float width, float height, int buttonFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isAnyItemActive() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isAnyItemFocused() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isAnyItemHovered() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isAnyMouseDown() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemActivated() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemActive() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemClicked() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemClicked(@NonNull MouseButton button) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemDeactivated() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemDeactivatedAfterEdit() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemEdited() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemFocused() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemHovered() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemHovered(int hoveredFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemToggledOpen() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemVisible() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isKeyDown(int userKeyIndex) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isKeyPressed(int userKeyIndex) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isKeyPressed(int userKeyIndex, boolean repeat) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isKeyReleased(int userKeyIndex) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isMouseClicked(@NonNull MouseButton button) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isMouseClicked(@NonNull MouseButton button, boolean repeat) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isMouseDoubleClicked(@NonNull MouseButton button) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isMouseDown(@NonNull MouseButton button) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isMouseDragging(@NonNull MouseButton button) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isMouseDragging(@NonNull MouseButton button, float lockThreshold) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isMouseHoveringRect(float minX, float minY, float maxX, float maxY) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isMouseHoveringRect(
            float minX, float minY, float maxX, float maxY, boolean clip) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isMousePosValid() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isMousePosValid(float x, float y) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isMouseReleased(@NonNull MouseButton button) {
        return context.io.getMouseReleased(button);
    }

    public static boolean isPopupOpen(int id) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isPopupOpen(String name) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isPopupOpen(int id, int popupFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isPopupOpen(String name, int popupFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isRectVisible(float width, float height) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isRectVisible(float minX, float minY, float maxX, float maxY) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isWindowAppearing() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isWindowCollapsed() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isWindowDocked() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isWindowFocused() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isWindowFocused(int focusedFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isWindowHovered() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isWindowHovered(int hoveredFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static void labelText(String label, String text) {
        // TODO(ches) complete this
    }

    public static void listBox(String label, IkInt currentItem, String[] items) {
        // TODO(ches) complete this
    }

    public static void listBox(String label, IkInt currentItem, String[] items, int heightInItems) {
        // TODO(ches) complete this
    }

    public static void loadIniSettingsFromDisk(String filename) {
        // TODO(ches) complete this
    }

    public static void loadIniSettingsFromMemory(String data) {
        // TODO(ches) complete this
    }

    public static void loadIniSettingsFromMemory(String data, int size) {
        // TODO(ches) complete this
    }

    public static void logButtons() {
        // TODO(ches) complete this
    }

    public static void logFinish() {
        // TODO(ches) complete this
    }

    public static void logText(String text) {
        // TODO(ches) complete this
    }

    public static void logToClipboard() {
        // TODO(ches) complete this
    }

    public static void logToClipboard(int maxDepth) {
        // TODO(ches) complete this
    }

    public static void logToFile() {
        // TODO(ches) complete this
    }

    public static void logToFile(int maxDepth) {
        // TODO(ches) complete this
    }

    public static void logToFile(int maxDepth, String filename) {
        // TODO(ches) complete this
    }

    public static void logToTTY() {
        // TODO(ches) complete this
    }

    public static void logToTTY(int maxDepth) {
        // TODO(ches) complete this
    }

    public static boolean menuItem(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean menuItem(String label, String shortcut) {
        return false;
    }

    public static boolean menuItem(String label, String shortcut, boolean selected) {
        return false;
    }

    public static boolean menuItem(
            String label, String shortcut, boolean selected, boolean enabled) {
        return false;
    }

    public static boolean menuItem(String label, String shortcut, IkBoolean selected) {
        return false;
    }

    public static boolean menuItem(
            String label, String shortcut, IkBoolean selected, boolean enabled) {
        return false;
    }

    public static void newFrame() {
        // TODO(ches) complete this

        // TODO(ches) sanity checks for IO and configuration
        // TODO(ches) update settings
        // Updating frame time and count
        final long lastFrameStart = context.frameStartTime;
        context.frameStartTime = System.currentTimeMillis();
        if (lastFrameStart > 0) {
            context.io.deltaTime = context.frameStartTime - lastFrameStart;
        }
        context.time += context.io.deltaTime;
        context.frameCount = (context.frameCount + 1) % FRAME_COUNT_CAP;
        // TODO(ches) update window counts

        context.windowDisplayOrder.clear();

        // Updating input events
        context.io.clearFrameSpecificValues();
        context.io.processInputEvents();

        // TODO(ches) update viewports

        // Update draw lists
        context.drawData.clear();
        context.backgroundDrawList.clear();
        context.foregroundDrawList.clear();
        context.drawData.drawLists.add(context.backgroundDrawList);
        context.drawData.drawLists.add(context.foregroundDrawList);
        // TODO(ches) update active IDs
        // TODO(ches) update hover delay
        // TODO(ches) update keyboard inputs
        // TODO(ches) update drag and drop
        IkGuiInternal.handleWindowDragging();
        // TODO(ches) update navigation
        context.io.updateMouseInputs();
        // TODO(ches) clean up transient buffers
        // TODO(ches) create fallback window
    }

    public static void newLine() {
        // TODO(ches) complete this
    }

    /**
     * Set a flag indicating a popup should be open, which should not be called every frame. This
     * internal state is read by {@link #beginPopup(int)} to see if the popup is visible.
     *
     * @param id The ID of the popup.
     */
    public static void openPopup(int id) {
        openPopup(id, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Set a flag indicating a popup should be open, which should not be called every frame. This
     * internal state is read by {@link #beginPopup(String)} to see if the popup is visible.
     *
     * @param name The name of the popup.
     */
    public static void openPopup(@NonNull String name) {
        openPopup(Hash.getID(name, context.idStack.peek()), PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Set a flag indicating a popup should be open, which should not be called every frame. This
     * internal state is read by {@link #beginPopup(int, int)} to see if the popup is visible.
     *
     * @param id The ID of the popup.
     * @param popupFlags Flags for the popup.
     */
    public static void openPopup(int id, int popupFlags) {
        // TODO(ches) complete this
    }

    /**
     * Set a flag indicating a popup should be open, which should not be called every frame. This
     * internal state is read by {@link #beginPopup(String, int)} to see if the popup is visible.
     *
     * @param name The name of the popup.
     * @param popupFlags Flags for the popup.
     */
    public static void openPopup(@NonNull String name, int popupFlags) {
        openPopup(Hash.getID(name, context.idStack.peek()), popupFlags);
    }

    /**
     * Open a popup if the last item was right-clicked, or navigation indicates we should open a
     * popup.
     *
     * @see #openPopup(int)
     */
    public static void openPopupOnItemClick() {
        openPopupOnItemClick(context.lastItemData.id, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open a popup if the right mouse button was clicked (or navigation action) indicating we
     * should.
     *
     * @param id The item we are interested in. If 0, we'll use the last item ID.
     * @see #openPopup(int)
     */
    public static void openPopupOnItemClick(int id) {
        openPopupOnItemClick(id, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open a popup if the right mouse button was clicked (or navigation action) indicating we
     * should.
     *
     * @param name The item we are interested in.
     * @see #openPopup(String)
     */
    public static void openPopupOnItemClick(@NonNull String name) {
        openPopupOnItemClick(
                Hash.getID(name, context.idStack.peek()), PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open a popup if the mouse button (or navigation action) indicates we should.
     *
     * @param id The item we are interested in. If 0, we'll use the last item ID.
     * @param popupFlags Flags for the popup.
     * @see #openPopup(int, int)
     */
    public static void openPopupOnItemClick(int id, int popupFlags) {
        if (IkGuiInternal.isPopupRequestOpenForItem(id, popupFlags)) {
            openPopup(id, popupFlags);
        }
    }

    /**
     * Open a popup if the mouse button (or navigation action) indicates we should.
     *
     * @param name The item we are interested in.
     * @param popupFlags Flags for the popup.
     * @see #openPopup(String, int)
     */
    public static void openPopupOnItemClick(@NonNull String name, int popupFlags) {
        openPopupOnItemClick(Hash.getID(name, context.idStack.peek()), popupFlags);
    }

    public static void plotHistogram(String label, float[] values, int count) {
        // TODO(ches) complete this
    }

    public static void plotHistogram(String label, float[] values, int count, int offset) {
        // TODO(ches) complete this
    }

    public static void plotHistogram(
            String label, float[] values, int count, int offset, String overlay) {
        // TODO(ches) complete this
    }

    public static void plotHistogram(
            String label,
            float[] values,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax) {
        // TODO(ches) complete this
    }

    public static void plotHistogram(
            String label,
            float[] values,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax,
            float width,
            float height) {
        // TODO(ches) complete this
    }

    public static void plotHistogram(
            String label,
            float[] values,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax,
            float width,
            float height,
            int stride) {
        // TODO(ches) complete this
    }

    public static void plotLines(String label, float[] values, int count) {
        // TODO(ches) complete this
    }

    public static void plotLines(String label, float[] values, int count, int offset) {
        // TODO(ches) complete this
    }

    public static void plotLines(
            String label, float[] values, int count, int offset, String overlay) {
        // TODO(ches) complete this
    }

    public static void plotLines(
            String label,
            float[] values,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax) {
        // TODO(ches) complete this
    }

    public static void plotLines(
            String label,
            float[] values,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax,
            float width,
            float height) {
        // TODO(ches) complete this
    }

    public static void plotLines(
            String label,
            float[] values,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax,
            float width,
            float height,
            int stride) {
        // TODO(ches) complete this
    }

    public static void popAllowKeyboardFocus() {
        // TODO(ches) complete this
    }

    public static void popButtonRepeat() {
        // TODO(ches) complete this
    }

    public static void popClipRect() {
        // TODO(ches) complete this
    }

    public static void popFont() {
        if (context.fontStack.isEmpty()) {
            log.error("Trying to pop a font when none are pushed");
            return;
        }
        FontBackup backupInfo = context.fontStack.pop();

        if (backupInfo.name() == null) {
            context.font = null;
            context.fontSize = backupInfo.size();
            return;
        }

        if (!context.io.fonts.isFontLoaded(backupInfo.name())) {
            log.warn(
                    "The font {} was unloaded while it was on the font stack, ignoring it",
                    backupInfo.name());
            return;
        }
        context.font = context.io.fonts.getFont(backupInfo.name());
        context.fontSize = backupInfo.size();
    }

    public static void popID() {
        if (context.idStack.isEmpty()) {
            log.error("Trying to pop an ID that does not exist");
            return;
        }
        context.idStack.pop();
    }

    public static void popItemWidth() {
        // TODO(ches) complete this
    }

    public static void popStyleColor() {
        try {
            context.colorStack.pop();
        } catch (NoSuchElementException ignored) {
            log.error("Trying to pop more style colors than we have pushed");
        }
    }

    public static void popStyleColor(int count) {
        try {
            for (int i = 0; i < count; ++i) {
                context.colorStack.pop();
            }
        } catch (NoSuchElementException ignored) {
            log.error("Trying to pop more style colors (pop {}) than we have pushed", count);
        }
    }

    public static void popStyleVar() {
        try {
            context.styleVariableStack.pop();
        } catch (NoSuchElementException ignored) {
            log.error("Trying to pop more style variables than we have pushed");
        }
    }

    public static void popStyleVar(int count) {
        try {
            for (int i = 0; i < count; ++i) {
                context.styleVariableStack.pop();
            }
        } catch (NoSuchElementException ignored) {
            log.error("Trying to pop more style variables (pop {}) than we have pushed", count);
        }
    }

    public static void popTextWrapPos() {
        // TODO(ches) complete this
    }

    /**
     * Renders a progress bar.
     *
     * @param progress The progress, ranging from 0.0 (0%) to 1.0 (100%).
     */
    public static void progressBar(float progress) {
        // TODO(ches) complete this
    }

    public static void progressBar(float progress, float width, float height) {
        // TODO(ches) complete this
    }

    public static void progressBar(float progress, float width, float height, String overlayText) {
        // TODO(ches) complete this
    }

    public static void pushAllowKeyboardFocus(boolean allow) {
        // TODO(ches) complete this
    }

    public static void pushButtonRepeat(boolean repeat) {
        // TODO(ches) complete this
    }

    public static void pushClipRect(
            float minX, float minY, float maxX, float maxY, boolean intersectWithCurrentClipRect) {
        // TODO(ches) complete this
    }

    /**
     * Temporarily change the font by pushing values onto a stack. These must be popped before the
     * end of the frame.
     *
     * @param font The name of the font, which is the path to the font from the plugins data folder.
     * @see #popFont()
     * @see #setFont(String)
     */
    public static void pushFont(@NonNull String font) {
        pushFont(font, context.fontSize);
    }

    /**
     * Temporarily change the font and size by pushing values onto a stack. These must be popped
     * before the end of the frame.
     *
     * @param font The name of the font, which is the path to the font from the plugins data folder.
     * @param size The size of the font.
     * @see #popFont()
     * @see #setFont(String, int)
     */
    public static void pushFont(@NonNull String font, int size) {
        String oldFont = context.font == null ? null : context.font.name;
        context.fontStack.push(new FontBackup(oldFont, context.fontSize));
        context.font = context.io.fonts.getFont(font);
        context.fontSize = size;
    }

    /**
     * Push an ID onto the ID stack.
     *
     * @param id The ID.
     * @return The new ID (which is based on the provided ID, and parent ID if there is one).
     */
    public static int pushID(int id) {
        int result = Hash.getID(id, context.idStack.peek());
        context.idStack.push(result);
        return result;
    }

    /**
     * Push an ID onto the ID stack.
     *
     * @param name The name, which might be null.
     * @return The new ID (which is based on the name and parent ID if there is one).
     */
    public static int pushID(String name) {
        // NOTE(ches) the other pushID will re-hash this ID with the last ID, so we don't need to
        // include it here
        return pushID(Hash.getID(name));
    }

    public static void pushItemWidth(float width) {
        // TODO(ches) complete this
    }

    public static void pushStyleColor(@NonNull ColorType type, float r, float g, float b, float a) {
        pushStyleColor(type, Color.rgba(r, g, b, a));
    }

    public static void pushStyleColor(@NonNull ColorType type, int rgba) {
        context.colorStack.push(new ColorMod(type, rgba));
    }

    public static void pushStyleColor(@NonNull ColorType type, int r, int g, int b, int a) {
        // TODO(ches) complete this
        pushStyleColor(type, Color.rgba(r, g, b, a));
    }

    public static void pushStyleVar(@NonNull StyleVariable variable, float value) {
        if (variable.getDimensions() != 1) {
            log.error(
                    "Style variable {} has {} dimensions, 1 float provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Float.class) {
            log.error(
                    "Style variable {} expects a {} value, Float provided",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }
        if (value < variable.getMinValue() || value > variable.getMaxValue()) {
            log.warn(
                    "Variable {} outside the expected float range ({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        context.styleVariableStack.push(new StyleMod(variable, value, 0));
    }

    public static void pushStyleVar(@NonNull StyleVariable variable, float x, float y) {
        if (variable.getDimensions() != 2) {
            log.error(
                    "Style variable {} has {} dimensions, 2 floats provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Float.class) {
            log.error(
                    "Style variable {} expects {} values, Floats provided",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }
        if (x < variable.getMinValue() || x > variable.getMaxValue()) {
            log.warn(
                    "Variable {} x value outside the expected float range({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        if (y < variable.getMinValue() || y > variable.getMaxValue()) {
            log.warn(
                    "Variable {} y value outside the expected float range({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        context.styleVariableStack.push(new StyleMod(variable, x, y));
    }

    public static void pushStyleVar(@NonNull StyleVariable variable, int value) {
        if (variable.getDimensions() != 1) {
            log.error(
                    "Style variable {} has {} dimensions, 1 int provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Integer.class) {
            log.error(
                    "Style variable {} expects a {} value, Integer provided",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }
        if (value < variable.getMinValue() || value > variable.getMaxValue()) {
            log.warn(
                    "Variable {} outside the expected int range ({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        context.styleVariableStack.push(new StyleMod(variable, value, 0));
    }

    public static void pushStyleVar(@NonNull StyleVariable variable, int x, int y) {
        if (variable.getDimensions() != 2) {
            log.error(
                    "Style variable {} has {} dimensions, 2 integers provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Integer.class) {
            log.error(
                    "Style variable {} expects {} values, Integers provided",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }
        if (x < variable.getMinValue() || x > variable.getMaxValue()) {
            log.warn(
                    "Variable {} x value outside of the expected int range ({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        if (y < variable.getMinValue() || y > variable.getMaxValue()) {
            log.warn(
                    "Variable {} y value outside of the expected int range ({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        context.styleVariableStack.push(new StyleMod(variable, x, y));
    }

    public static void pushTextWrapPos() {
        // TODO(ches) complete this
    }

    public static void pushTextWrapPos(float x) {
        // TODO(ches) complete this
    }

    public static boolean radioButton(String label, boolean initialState) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean radioButton(String label, IkInt selectionStorage, int value) {
        return false;
    }

    public static void render() {
        // TODO(ches) complete this

        if (context.frameCountEnded != context.frameCount) {
            IkGui.endFrame();
        }

        context.frameCountRendered = (context.frameCountRendered + 1) % FRAME_COUNT_CAP;
    }

    public static void renderPlatformWindowsDefault() {
        // TODO(ches) complete this
    }

    public static void resetMouseDragDelta() {
        // TODO(ches) complete this
    }

    public static void resetMouseDragDelta(@NonNull MouseButton button) {
        // TODO(ches) complete this
    }

    /** Keep rendering on the same line. */
    public static void sameLine() {
        sameLine(0, -1);
    }

    /**
     * Keep rendering on the same line.
     *
     * @param offsetFromStartX If positive, the window-local x position. If zero, placed at the end
     *     of the last widget's rectangle.
     */
    public static void sameLine(int offsetFromStartX) {
        sameLine(offsetFromStartX, -1);
    }

    /**
     * Keep rendering on the same line.
     *
     * @param offsetFromStartX If positive, the window-local x position. If zero, placed at the end
     *     of the last widget's rectangle.
     * @param spacingAfterCurrent The horizontal spacing between the current and next widget.
     */
    public static void sameLine(final float offsetFromStartX, float spacingAfterCurrent) {
        Window window = context.windowCurrent;
        Vector2f cursor = window.cursorPosition;

        if (spacingAfterCurrent == -1) {
            spacingAfterCurrent = context.style.variable.itemSpacing.x;
        }

        float newX;
        if (offsetFromStartX == 0) {
            newX = cursor.x + spacingAfterCurrent;
        } else {
            newX = window.cursorStartPosition.x + offsetFromStartX;
        }

        window.sameLine = true;
        cursor.set(newX, cursor.y);
    }

    public static void saveIniSettingsToDisk(String filename) {
        // TODO(ches) complete this
    }

    public static String saveIniSettingsToMemory() {
        // TODO(ches) complete this
        return null;
    }

    public static String saveIniSettingsToMemory(long size) {
        // TODO(ches) complete this
        return null;
    }

    public static boolean selectable(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean selectable(String label, boolean selected) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean selectable(String label, boolean selected, int selectableFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean selectable(
            String label, boolean selected, int selectableFlags, float width, float height) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean selectable(String label, IkBoolean selected) {
        return false;
    }

    public static boolean selectable(String label, IkBoolean selected, int imGuiSelectableFlags) {
        return false;
    }

    public static boolean selectable(
            String label, IkBoolean selected, int imGuiSelectableFlags, float width, float height) {
        return false;
    }

    public static void separator() {
        // TODO(ches) complete this
    }

    public static void setClipboardText(String text) {
        // TODO(ches) complete this
    }

    public static void setColorEditOptions(int colorEditFlags) {
        // TODO(ches) complete this
    }

    public static void setCursorPos(float x, float y) {
        // TODO(ches) complete this
    }

    public static void setCursorPosX(float x) {
        // TODO(ches) complete this
    }

    public static void setCursorPosY(float y) {
        // TODO(ches) complete this
    }

    public static void setCursorScreenPos(float x, float y) {
        // TODO(ches) complete this
    }

    public static boolean setDragDropPayload(Object payload) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean setDragDropPayload(Object payload, @NonNull Condition condition) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean setDragDropPayload(String dataType, Object payload) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean setDragDropPayload(
            String dataType, Object payload, @NonNull Condition condition) {
        // TODO(ches) complete this
        return false;
    }

    /**
     * Set the current font to use for the application across frames.
     *
     * @param fontPath The path to teh font from the resource folder, which must be loaded.
     * @see #pushFont(String)
     * @see #setFontFallbacks(String...)
     */
    public static void setFont(@NonNull String fontPath) {
        setFont(fontPath, context.fontSize);
    }

    /**
     * Set the current font to use for the application across frames.
     *
     * @param fontPath The path to teh font from the resource folder, which must be loaded.
     * @param size The font size to use.
     * @see #pushFont(String, int)
     * @see #setFontFallbacks(String...)
     * @see #setFont(String)
     * @see #setFontSize(int)
     */
    public static void setFont(@NonNull String fontPath, int size) {
        if (!context.io.fonts.isFontLoaded(fontPath)) {
            log.error(
                    "Font {} is not loaded, cannot use it as the current font until loaded.",
                    fontPath);
            return;
        }
        context.font = context.io.fonts.getFont(fontPath);
        context.fontSize = size;
    }

    /**
     * Set (overwrite) the list of font fallbacks. Fonts that are not yet loaded will be loaded.
     *
     * @param fontList The list of font names to use, in order from first to last to check for
     *     glyphs.
     * @see Context#fontFallbacks
     */
    public static void setFontFallbacks(@NonNull String... fontList) {
        context.fontFallbacks.clear();
        for (String fontName : fontList) {
            if (!context.io.fonts.isFontLoaded(fontName) && !context.io.fonts.loadFont(fontName)) {
                continue;
            }
            context.fontFallbacks.add(context.io.fonts.getFont(fontName));
        }
    }

    /**
     * Set the font size to use for the application across frames.
     *
     * @param fontSize The size of the font.
     * @see #pushFont(String, int)
     */
    public static void setFontSize(int fontSize) {
        context.fontSize = fontSize;
    }

    public static void setItemAllowOverlap() {
        // TODO(ches) complete this
    }

    public static void setItemDefaultFocus() {
        // TODO(ches) complete this
    }

    public static void setKeyboardFocusHere() {
        // TODO(ches) complete this
    }

    public static void setKeyboardFocusHere(int offset) {
        // TODO(ches) complete this
    }

    public static void setMouseCursor(@NonNull MouseCursor cursor) {
        // TODO(ches) complete this
    }

    public static void setNextItemOpen(boolean isOpen) {
        // TODO(ches) complete this
    }

    public static void setNextItemOpen(boolean isOpen, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static void setNextItemWidth(float width) {
        // TODO(ches) complete this
    }

    public static void setNextWindowCollapsed(boolean collapsed) {
        // TODO(ches) complete this
    }

    public static void setNextWindowCollapsed(boolean collapsed, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static void setNextWindowDockID(int id) {
        // TODO(ches) complete this
    }

    public static void setNextWindowDockID(int id, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static void setNextWindowFocus() {
        // TODO(ches) complete this
    }

    public static void setNextWindowPos(int x, int y) {
        setNextWindowPos(x, y, Condition.ALWAYS, 0, 0);
    }

    public static void setNextWindowPos(int x, int y, @NonNull Condition condition) {
        setNextWindowPos(x, y, condition, 0, 0);
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

        context.nextWindowData.positionPivot.set(pivotX, pivotY);
        context.nextWindowData.positionValue.set(x, y);
        context.nextWindowData.positionCondition = condition;
    }

    public static void setNextWindowSize(int x, int y) {
        setNextWindowSize(x, y, Condition.ALWAYS);
    }

    public static void setNextWindowSize(int x, int y, @NonNull Condition condition) {
        context.nextWindowData.sizeCondition = condition;
        context.nextWindowData.sizeValue.set(x, y);
    }

    public static void setScrollFromX(float localX) {
        // TODO(ches) complete this
    }

    public static void setScrollFromX(float localX, float centerXRatio) {
        // TODO(ches) complete this
    }

    public static void setScrollFromY(float localY) {
        // TODO(ches) complete this
    }

    public static void setScrollFromY(float localY, float centerYRatio) {
        // TODO(ches) complete this
    }

    public static void setScrollHereX() {
        // TODO(ches) complete this
    }

    public static void setScrollHereX(float centerXRatio) {
        // TODO(ches) complete this
    }

    public static void setScrollHereY() {
        // TODO(ches) complete this
    }

    public static void setScrollHereY(float centerYRatio) {
        // TODO(ches) complete this
    }

    public static void setScrollX(float x) {
        // TODO(ches) complete this
    }

    public static void setScrollY(float x) {
        // TODO(ches) complete this
    }

    public static void setStateStorage(GuiStorage storage) {}

    public static void setTabItemClosed(String label) {
        // TODO(ches) complete this
    }

    public static void setTooltip(String text) {
        // TODO(ches) complete this
    }

    public static void setWindowCollapsed(boolean collapsed) {
        // TODO(ches) complete this
    }

    public static void setWindowCollapsed(boolean collapsed, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static void setWindowFocus() {
        // TODO(ches) complete this
    }

    public static void setWindowPos(float x, float y) {
        // TODO(ches) complete this
    }

    public static void setWindowPos(float x, float y, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static void setWindowPos(
            float x, float y, @NonNull Condition condition, int pivotX, int pivotY) {
        // TODO(ches) complete this
    }

    public static void setWindowSize(float x, float y) {
        // TODO(ches) complete this
    }

    public static void setWindowSize(float x, float y, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static void showDemoWindow() {
        showDemoWindow(null);
    }

    public static void showDemoWindow(final IkBoolean open) {
        IkGuiDemo.showDemoWindow(open);
    }

    public static boolean sliderAngle(String label, float[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderAngle(
            String label, float[] value, float minDegrees, float maxDegrees) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderAngle(
            String label, float[] value, float minDegrees, float maxDegrees, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderFloat(String label, float[] value, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderFloat(
            String label, float[] value, float min, float max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderFloat(
            String label, float[] value, float min, float max, String format, int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderFloat2(String label, float[] value, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderFloat2(
            String label, float[] value, float min, float max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderFloat2(
            String label, float[] value, float min, float max, String format, int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderFloat3(String label, float[] value, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderFloat3(
            String label, float[] value, float min, float max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderFloat3(
            String label, float[] value, float min, float max, String format, int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderFloat4(String label, float[] value, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderFloat4(
            String label, float[] value, float min, float max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderFloat4(
            String label, float[] value, float min, float max, String format, int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderInt(String label, int[] value, int min, int max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderInt(String label, int[] value, int min, int max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderInt2(String label, int[] value, int min, int max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderInt2(String label, int[] value, int min, int max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderInt3(String label, int[] value, int min, int max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderInt3(String label, int[] value, int min, int max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderInt4(String label, int[] value, int min, int max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderInt4(String label, int[] value, int min, int max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean sliderScalar(
            String label, int dataType, IkDouble value, double min, double max) {
        return false;
    }

    public static boolean sliderScalar(
            String label, int dataType, IkDouble value, double min, double max, String format) {
        return false;
    }

    public static boolean sliderScalar(
            String label,
            int dataType,
            IkDouble value,
            double min,
            double max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean sliderScalar(
            String label, int dataType, IkFloat value, float min, float max) {
        return false;
    }

    public static boolean sliderScalar(
            String label, int dataType, IkFloat value, float min, float max, String format) {
        return false;
    }

    public static boolean sliderScalar(
            String label,
            int dataType,
            IkFloat value,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean sliderScalar(String label, int dataType, IkInt value, int min, int max) {
        return false;
    }

    public static boolean sliderScalar(
            String label, int dataType, IkInt value, int min, int max, String format) {
        return false;
    }

    public static boolean sliderScalar(
            String label,
            int dataType,
            IkInt value,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean sliderScalar(
            String label, int dataType, IkLong value, long min, long max) {
        return false;
    }

    public static boolean sliderScalar(
            String label, int dataType, IkLong value, long min, long max, String format) {
        return false;
    }

    public static boolean sliderScalar(
            String label,
            int dataType,
            IkLong value,
            long min,
            long max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean sliderScalar(
            String label, int dataType, IkShort value, short min, short max) {
        return false;
    }

    public static boolean sliderScalar(
            String label, int dataType, IkShort value, short min, short max, String format) {
        return false;
    }

    public static boolean sliderScalar(
            String label,
            int dataType,
            IkShort value,
            short min,
            short max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean sliderScalarN(
            String label, int dataType, int components, IkDouble value, double min, double max) {
        return false;
    }

    public static boolean sliderScalarN(
            String label,
            int dataType,
            int components,
            IkDouble value,
            double min,
            double max,
            String format) {
        return false;
    }

    public static boolean sliderScalarN(
            String label,
            int dataType,
            int components,
            IkDouble value,
            double min,
            double max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean sliderScalarN(
            String label, int dataType, int components, IkFloat value, float min, float max) {
        return false;
    }

    public static boolean sliderScalarN(
            String label,
            int dataType,
            int components,
            IkFloat value,
            float min,
            float max,
            String format) {
        return false;
    }

    public static boolean sliderScalarN(
            String label,
            int dataType,
            int components,
            IkFloat value,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean sliderScalarN(
            String label, int dataType, int components, IkInt value, int min, int max) {
        return false;
    }

    public static boolean sliderScalarN(
            String label,
            int dataType,
            int components,
            IkInt value,
            int min,
            int max,
            String format) {
        return false;
    }

    public static boolean sliderScalarN(
            String label,
            int dataType,
            int components,
            IkInt value,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean sliderScalarN(
            String label, int dataType, int components, IkLong value, long min, long max) {
        return false;
    }

    public static boolean sliderScalarN(
            String label,
            int dataType,
            int components,
            IkLong value,
            long min,
            long max,
            String format) {
        return false;
    }

    public static boolean sliderScalarN(
            String label,
            int dataType,
            int components,
            IkLong value,
            long min,
            long max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean sliderScalarN(
            String label, int dataType, int components, IkShort value, short min, short max) {
        return false;
    }

    public static boolean sliderScalarN(
            String label,
            int dataType,
            int components,
            IkShort value,
            short min,
            short max,
            String format) {
        return false;
    }

    public static boolean sliderScalarN(
            String label,
            int dataType,
            int components,
            IkShort value,
            short min,
            short max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean smallButton(String text) {
        // TODO(ches) complete this
        return false;
    }

    /** Adds vertical space, equal to the current style's y spacing. */
    public static void spacing() {
        // TODO(ches) complete this
    }

    public static boolean tabItemButton(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean tabItemButton(String label, int tabItemFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static int tableGetColumnCount() {
        // TODO(ches) complete this
        return 0;
    }

    public static int tableGetColumnFlags() {
        // TODO(ches) complete this
        return 0;
    }

    public static int tableGetColumnFlags(int index) {
        // TODO(ches) complete this
        return 0;
    }

    public static int tableGetColumnIndex() {
        // TODO(ches) complete this
        return 0;
    }

    public static String tableGetColumnName() {
        // TODO(ches) complete this
        return null;
    }

    public static String tableGetColumnName(int index) {
        // TODO(ches) complete this
        return null;
    }

    public static int tableGetRowIndex() {
        // TODO(ches) complete this
        return 0;
    }

    public static void tableHeader(String label) {
        // TODO(ches) complete this
    }

    public static void tableHeadersRow() {
        // TODO(ches) complete this
    }

    public static boolean tableNextColumn() {
        // TODO(ches) complete this
        return false;
    }

    public static void tableNextRow() {
        // TODO(ches) complete this
    }

    public static void tableNextRow(int tableRowFlags) {
        // TODO(ches) complete this
    }

    public static void tableNextRow(int tableRowFlags, float minHeight) {
        // TODO(ches) complete this
    }

    public static void tableSetBgColor(@NonNull TableBackgroundTarget target, int color) {
        // TODO(ches) complete this
    }

    public static void tableSetBgColor(
            @NonNull TableBackgroundTarget target, int color, int columnIndex) {
        // TODO(ches) complete this
    }

    public static boolean tableSetColumnIndex(int index) {
        // TODO(ches) complete this
        return false;
    }

    public static void tableSetupColumn(String label) {
        // TODO(ches) complete this
    }

    public static void tableSetupColumn(String label, int tableColumnFlags) {
        // TODO(ches) complete this
    }

    public static void tableSetupColumn(String label, int tableColumnFlags, float widthOrWeight) {
        // TODO(ches) complete this
    }

    public static void tableSetupColumn(
            String label, int tableColumnFlags, float widthOrWeight, int userID) {
        // TODO(ches) complete this
    }

    public static void tableSetupScrollFreeze(int frozenRows, int frozenColumns) {
        // TODO(ches) complete this
    }

    public static void text(@NonNull String text) {
        int color = context.style.color.text;
        textColored(color, text);
    }

    public static void textColored(float r, float g, float b, float a, @NonNull String text) {
        int color = Color.rgba(r, g, b, a);
        textColored(color, text);
    }

    public static void textColored(int r, int g, int b, int a, @NonNull String text) {
        int color = Color.rgba(r, g, b, a);
        textColored(color, text);
    }

    public static void textColored(int color, @NonNull String text) {
        Vector2f cursor = context.windowCurrent.cursorPosition;

        Window window = context.windowCurrent;
        window.updateCursorBeforeDrawing();

        int cursorDelta =
                context.windowCurrent.drawList.addText(
                        context.fontSize, cursor.x, cursor.y, color, text);
        window.updateCursorAfterDrawing(cursorDelta, getTextLineHeightWithSpacing());
    }

    public static void textDisabled(@NonNull String text) {
        int color = context.style.color.textDisabled;
        textColored(color, text);
    }

    public static void textUnformatted(@NonNull String text) {
        // TODO(ches) complete this
    }

    public static void textWrapped(@NonNull String text) {
        // TODO(ches) complete this
    }

    public static boolean treeNode(long id, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean treeNode(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean treeNode(String name, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean treeNodeEx(long id, int treeNodeFlags, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean treeNodeEx(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean treeNodeEx(String name, int treeNodeFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean treeNodeEx(String name, int treeNodeFlags, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static void treePop() {
        // TODO(ches) complete this
    }

    public static void treePush() {
        // TODO(ches) complete this
    }

    public static void treePush(long id) {
        // TODO(ches) complete this
    }

    public static void treePush(String name) {
        // TODO(ches) complete this
    }

    public static void unindent() {
        // TODO(ches) complete this
    }

    public static void unindent(float width) {
        // TODO(ches) complete this
    }

    public static void updatePlatformWindows() {
        // TODO(ches) complete this
    }

    public static void value(String prefix, boolean value) {
        // TODO(ches) complete this
    }

    public static void value(String prefix, float value) {
        // TODO(ches) complete this
    }

    public static void value(String prefix, float value, String format) {
        // TODO(ches) complete this
    }

    public static void value(String prefix, int value) {
        // TODO(ches) complete this
    }

    public static void value(String prefix, long value) {
        // TODO(ches) complete this
    }

    public static boolean vSliderFloat(
            String label, float width, float height, float[] value, float min, float max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean vSliderFloat(
            String label,
            float width,
            float height,
            float[] value,
            float min,
            float max,
            String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean vSliderFloat(
            String label,
            float width,
            float height,
            float[] value,
            float min,
            float max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean vSliderInt(
            String label, float width, float height, int[] value, int min, int max) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean vSliderInt(
            String label, float width, float height, int[] value, int min, int max, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkDouble value,
            double min,
            double max) {
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkDouble value,
            double min,
            double max,
            String format) {
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkDouble value,
            double min,
            double max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkFloat value,
            float min,
            float max) {
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkFloat value,
            float min,
            float max,
            String format) {
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkFloat value,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean vSliderScalar(
            String label, float width, float height, int dataType, IkInt value, int min, int max) {
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkInt value,
            int min,
            int max,
            String format) {
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkInt value,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkLong value,
            long min,
            long max) {
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkLong value,
            long min,
            long max,
            String format) {
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkLong value,
            long min,
            long max,
            String format,
            int sliderFlags) {
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkShort value,
            short min,
            short max) {
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkShort value,
            short min,
            short max,
            String format) {
        return false;
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            int dataType,
            IkShort value,
            short min,
            short max,
            String format,
            int sliderFlags) {
        return false;
    }
}
