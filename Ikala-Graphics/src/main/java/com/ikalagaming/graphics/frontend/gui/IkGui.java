package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.callback.GuiInputTextCallback;
import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.*;
import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;
import com.ikalagaming.graphics.frontend.gui.flags.*;
import com.ikalagaming.graphics.frontend.gui.util.Color;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.NoSuchElementException;
import java.util.function.IntFunction;

/**
 * Immediate mode GUI library, a customized Java fork of <a
 * href="https://github.com/ocornut/imgui">ImGui</a>.
 */
@Slf4j
public class IkGui {

    @Getter private static Context context;

    private static Storage storage;

    public static <T> T acceptDragDropPayload(Class<T> aClass) {
        return IkGuiImplDragDrop.acceptDragDropPayload(aClass, DragDropFlags.NONE);
    }

    public static <T> T acceptDragDropPayload(Class<T> aClass, int dragDropFlags) {
        return IkGuiImplDragDrop.acceptDragDropPayload(aClass, dragDropFlags);
    }

    public static <T> T acceptDragDropPayload(String dataType) {
        return IkGuiImplDragDrop.acceptDragDropPayload(dataType, DragDropFlags.NONE);
    }

    public static <T> T acceptDragDropPayload(String dataType, int dragDropFlags) {
        return IkGuiImplDragDrop.acceptDragDropPayload(dataType, dragDropFlags);
    }

    public static void alignTextToFramePadding() {
        IkGuiImplText.alignTextToFramePadding();
    }

    /**
     * Returns a color, represented as an int, with the current global alpha value applied to it.
     * This assumes the element is not disabled.
     *
     * @param color The original color.
     * @return The scaled color.
     */
    public static int applyGlobalAlpha(int color) {
        return IkGuiImplUtils.applyGlobalAlpha(color, false);
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
        return IkGuiImplUtils.applyGlobalAlpha(color, disabled);
    }

    public static boolean arrowButton(String text, @NonNull Direction direction) {
        return IkGuiImplButtons.arrowButton(text, direction);
    }

    /**
     * Start a new window to add widgets to. The window name is a unique identifier used to preserve
     * information across frames. Every call to begin should be matched with a call to {@link
     * #end()} even if false is returned.
     *
     * @param title The unique title of the window.
     * @return false if the window is collapsed.
     */
    public static boolean begin(@NonNull String title) {
        return IkGuiImplWindows.begin(title, null, WindowFlags.NONE);
    }

    /**
     * Start a new window to add widgets to. The window name is a unique identifier used to preserve
     * information across frames. Every call to begin should be matched with a call to {@link
     * #end()} even if false is returned.
     *
     * @param title The unique title of the window.
     * @param open Non-null values display a close button on the window, which will be set to false
     *     if the close button is pressed.
     * @return false if the window is collapsed.
     */
    public static boolean begin(@NonNull String title, final IkBoolean open) {
        return IkGuiImplWindows.begin(title, open, WindowFlags.NONE);
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
        return IkGuiImplWindows.begin(title, open, windowFlags);
    }

    /**
     * Start a new window to add widgets to. The window name is a unique identifier used to preserve
     * information across frames. Every call to begin should be matched with a call to {@link
     * #end()} even if false is returned.
     *
     * @param title The unique title of the window.
     * @param windowFlags Flags for modifying the window.
     * @return false if the window is collapsed.
     */
    public static boolean begin(@NonNull String title, int windowFlags) {
        return IkGuiImplWindows.begin(title, null, windowFlags);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window.
     *
     * @param id The ID of the child.
     */
    public static void beginChild(int id) {
        IkGuiImplWindows.beginChild(id, 0, 0, ChildFlags.NONE, WindowFlags.NONE);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window.
     *
     * @param id The ID of the child.
     * @param width The width in pixels.
     * @param height The height in pixels.
     */
    public static void beginChild(int id, float width, float height) {
        IkGuiImplWindows.beginChild(id, width, height, ChildFlags.NONE, WindowFlags.NONE);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window.
     *
     * @param id The ID of the child.
     * @param size The size in pixels.
     */
    public static void beginChild(int id, @NonNull Vector2f size) {
        IkGuiImplWindows.beginChild(id, size.x, size.y, ChildFlags.NONE, WindowFlags.NONE);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window.
     *
     * @param id The ID of the child.
     * @param size The size in pixels.
     * @param childFlags Child flags.
     * @param windowFlags Window flags.
     * @see ChildFlags
     * @see WindowFlags
     */
    public static void beginChild(int id, @NonNull Vector2f size, int childFlags, int windowFlags) {
        IkGuiImplWindows.beginChild(id, size.x, size.y, childFlags, windowFlags);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window.
     *
     * @param id The ID of the child.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param childFlags Child flags.
     * @param windowFlags Window flags.
     * @see ChildFlags
     * @see WindowFlags
     */
    public static void beginChild(
            int id, float width, float height, int childFlags, int windowFlags) {
        IkGuiImplWindows.beginChild(id, width, height, childFlags, windowFlags);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window.
     *
     * @param name The name of the child.
     */
    public static void beginChild(@NonNull String name) {
        IkGuiImplWindows.beginChild(Hash.getID(name), 0, 0, ChildFlags.NONE, WindowFlags.NONE);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window.
     *
     * @param name The name of the child.
     * @param size The size in pixels.
     */
    public static void beginChild(@NonNull String name, @NonNull Vector2f size) {
        IkGuiImplWindows.beginChild(
                Hash.getID(name), size.x, size.y, ChildFlags.NONE, WindowFlags.NONE);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window.
     *
     * @param name The name of the child.
     * @param width The width in pixels.
     * @param height The height in pixels.
     */
    public static void beginChild(@NonNull String name, float width, float height) {
        IkGuiImplWindows.beginChild(
                Hash.getID(name), width, height, ChildFlags.NONE, WindowFlags.NONE);
    }

    public static boolean beginCombo(String label, String previewValue) {
        return IkGuiImplMiscWidgets.beginCombo(label, previewValue, ComboFlags.NONE);
    }

    public static boolean beginCombo(String label, String previewValue, int comboFlags) {
        return IkGuiImplMiscWidgets.beginCombo(label, previewValue, comboFlags);
    }

    public static void beginDisabled() {
        IkGuiImplUtils.beginDisabled(true);
    }

    public static void beginDisabled(boolean disabled) {
        IkGuiImplUtils.beginDisabled(disabled);
    }

    public static boolean beginDragDropSource() {
        return IkGuiImplDragDrop.beginDragDropSource(DragDropFlags.NONE);
    }

    public static boolean beginDragDropSource(int dragDropFlags) {
        return IkGuiImplDragDrop.beginDragDropSource(dragDropFlags);
    }

    public static boolean beginDragDropTarget() {
        return IkGuiImplDragDrop.beginDragDropTarget();
    }

    public static void beginGroup() {
        IkGuiImplLayout.beginGroup();
    }

    public static boolean beginListBox(String label) {
        return IkGuiImplMiscWidgets.beginListBox(label, 0, 0);
    }

    public static boolean beginListBox(String label, float width, float height) {
        return IkGuiImplMiscWidgets.beginListBox(label, width, height);
    }

    public static boolean beginMainMenuBar() {
        return IkGuiImplWindows.beginMainMenuBar();
    }

    public static boolean beginMenu(String label) {
        return IkGuiImplWindows.beginMenu(label, true);
    }

    public static boolean beginMenu(String label, boolean enabled) {
        return IkGuiImplWindows.beginMenu(label, enabled);
    }

    public static boolean beginMenuBar() {
        return IkGuiImplWindows.beginMenuBar();
    }

    public static boolean beginPopup(int id) {
        return IkGuiImplWindows.beginPopup(id, PopupFlags.NONE);
    }

    public static boolean beginPopup(@NonNull String name) {
        return IkGuiImplWindows.beginPopup(Hash.getID(name), PopupFlags.NONE);
    }

    public static boolean beginPopup(int id, int windowFlags) {
        return IkGuiImplWindows.beginPopup(id, windowFlags);
    }

    public static boolean beginPopup(@NonNull String name, int windowFlags) {
        return IkGuiImplWindows.beginPopup(Hash.getID(name), windowFlags);
    }

    /**
     * Create a popup associated with the last item. Generally for things that don't have
     * identifiers, like text.
     *
     * @return Whether the popup is open.
     * @see #beginPopupContextItem(int, int)
     */
    public static boolean beginPopupContextItem() {
        return IkGuiImplWindows.beginPopupContextItem(
                context.lastItemData.id, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Create a popup with an explicit ID.
     *
     * @param id The ID.
     * @return Whether the popup is open.
     * @see #beginPopupContextItem(int, int)
     */
    public static boolean beginPopupContextItem(int id) {
        return IkGuiImplWindows.beginPopupContextItem(id, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Create a popup with an explicit ID.
     *
     * @param name The name of the popup.
     * @return Whether the popup is open.
     * @see #beginPopupContextItem(int, int)
     */
    public static boolean beginPopupContextItem(@NonNull String name) {
        return IkGuiImplWindows.beginPopupContextItem(
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
        return IkGuiImplWindows.beginPopupContextItem(
                Hash.getID(name, context.idStack.peek()), popupFlags);
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
        return IkGuiImplWindows.beginPopupContextItem(id, popupFlags);
    }

    public static boolean beginPopupContextVoid() {
        return IkGuiImplWindows.beginPopupContextVoid(0, PopupFlags.NONE);
    }

    public static boolean beginPopupContextVoid(int id) {
        return IkGuiImplWindows.beginPopupContextVoid(id, PopupFlags.NONE);
    }

    public static boolean beginPopupContextVoid(String name) {
        return IkGuiImplWindows.beginPopupContextVoid(Hash.getID(name), PopupFlags.NONE);
    }

    public static boolean beginPopupContextVoid(int id, int popupFlags) {
        return IkGuiImplWindows.beginPopupContextVoid(id, popupFlags);
    }

    public static boolean beginPopupContextVoid(String name, int popupFlags) {
        return IkGuiImplWindows.beginPopupContextVoid(Hash.getID(name), popupFlags);
    }

    public static boolean beginPopupContextWindow() {
        return IkGuiImplWindows.beginPopupContextWindow(0, PopupFlags.NONE);
    }

    public static boolean beginPopupContextWindow(int id) {
        return IkGuiImplWindows.beginPopupContextWindow(id, PopupFlags.NONE);
    }

    public static boolean beginPopupContextWindow(String name) {
        return IkGuiImplWindows.beginPopupContextWindow(Hash.getID(name), PopupFlags.NONE);
    }

    public static boolean beginPopupContextWindow(int id, int popupFlags) {
        return IkGuiImplWindows.beginPopupContextWindow(id, popupFlags);
    }

    public static boolean beginPopupContextWindow(String name, int popupFlags) {
        return IkGuiImplWindows.beginPopupContextWindow(Hash.getID(name), popupFlags);
    }

    public static boolean beginPopupModal(String name) {
        return IkGuiImplWindows.beginPopupModal(name, null, WindowFlags.NONE);
    }

    public static boolean beginPopupModal(String name, IkBoolean open) {
        return IkGuiImplWindows.beginPopupModal(name, open, WindowFlags.NONE);
    }

    public static boolean beginPopupModal(String name, IkBoolean open, int windowFlags) {
        return IkGuiImplWindows.beginPopupModal(name, open, windowFlags);
    }

    public static boolean beginPopupModal(String name, int windowFlags) {
        return IkGuiImplWindows.beginPopupModal(name, null, windowFlags);
    }

    public static boolean beginTabBar(String label) {
        return IkGuiImplTabs.beginTabBar(label, TabBarFlags.NONE);
    }

    public static boolean beginTabBar(String label, int tabBarFlags) {
        return IkGuiImplTabs.beginTabBar(label, tabBarFlags);
    }

    public static boolean beginTabItem(String label) {
        return IkGuiImplTabs.beginTabItem(label, null, TabItemFlags.NONE);
    }

    public static boolean beginTabItem(String label, IkBoolean open) {
        return IkGuiImplTabs.beginTabItem(label, open, TabItemFlags.NONE);
    }

    public static boolean beginTabItem(String label, IkBoolean open, int tabItemFlags) {
        return IkGuiImplTabs.beginTabItem(label, open, tabItemFlags);
    }

    public static boolean beginTabItem(String label, int tabItemFlags) {
        return IkGuiImplTabs.beginTabItem(label, null, tabItemFlags);
    }

    public static boolean beginTable(String name, int columns) {
        return IkGuiImplTables.beginTable(name, columns, TableFlags.NONE, 0.0f, 0.0f, 0.0f);
    }

    public static boolean beginTable(String name, int columns, int tableFlags) {
        return IkGuiImplTables.beginTable(name, columns, tableFlags, 0.0f, 0.0f, 0.0f);
    }

    public static boolean beginTable(
            String name, int columns, int tableFlags, @NonNull Vector2f outerSize) {
        return IkGuiImplTables.beginTable(
                name, columns, tableFlags, outerSize.x, outerSize.y, 0.0f);
    }

    public static boolean beginTable(
            String name, int columns, int tableFlags, float outerWidth, float outerHeight) {
        return IkGuiImplTables.beginTable(name, columns, tableFlags, outerWidth, outerHeight, 0.0f);
    }

    public static boolean beginTable(
            String name,
            int columns,
            int tableFlags,
            @NonNull Vector2f outerSize,
            float innerWidth) {
        return IkGuiImplTables.beginTable(
                name, columns, tableFlags, outerSize.x, outerSize.y, innerWidth);
    }

    public static boolean beginTable(
            String name,
            int columns,
            int tableFlags,
            float outerWidth,
            float outerHeight,
            float innerWidth) {
        return IkGuiImplTables.beginTable(
                name, columns, tableFlags, outerWidth, outerHeight, innerWidth);
    }

    public static void beginTooltip() {
        IkGuiImplWindows.beginTooltip();
    }

    public static void bullet() {
        IkGuiImplText.bullet();
    }

    public static void bulletText(String text) {
        IkGuiImplText.bulletText(text);
    }

    public static boolean button(String text) {
        return IkGuiImplMiscWidgets.button(text, 0, 0);
    }

    public static boolean button(String text, float width, float height) {
        return IkGuiImplMiscWidgets.button(text, width, height);
    }

    public static Vector2f calcTextSize(String text) {
        Vector2f value = new Vector2f();
        IkGuiImplUtils.calcTextSize(value, text, false, -1.0f);
        return value;
    }

    public static Vector2f calcTextSize(String text, boolean hideTextAfterDoubleHash) {
        Vector2f value = new Vector2f();
        IkGuiImplUtils.calcTextSize(value, text, hideTextAfterDoubleHash, -1.0f);
        return value;
    }

    public static Vector2f calcTextSize(
            String text, boolean hideTextAfterDoubleHash, float wrapWidth) {
        Vector2f value = new Vector2f();
        IkGuiImplUtils.calcTextSize(value, text, hideTextAfterDoubleHash, wrapWidth);
        return value;
    }

    public static void calcTextSize(Vector2f result, String text) {
        IkGuiImplUtils.calcTextSize(result, text, false, -1.0f);
    }

    public static void calcTextSize(Vector2f result, String text, boolean hideTextAfterDoubleHash) {
        IkGuiImplUtils.calcTextSize(result, text, hideTextAfterDoubleHash, -1.0f);
    }

    public static void calcTextSize(
            Vector2f result, String text, boolean hideTextAfterDoubleHash, float wrapWidth) {
        IkGuiImplUtils.calcTextSize(result, text, hideTextAfterDoubleHash, wrapWidth);
    }

    public static void calcTextSize(Vector2f result, String text, float wrapWidth) {
        IkGuiImplUtils.calcTextSize(result, text, false, wrapWidth);
    }

    public static float calculateItemWidth() {
        return IkGuiImplUtils.calculateItemWidth();
    }

    public static boolean checkbox(String label, boolean initialState) {
        return IkGuiImplMiscWidgets.checkbox(label, initialState);
    }

    public static boolean checkbox(String label, IkBoolean active) {
        return IkGuiImplMiscWidgets.checkbox(label, active);
    }

    public static boolean checkboxFlags(String label, IkInt flags, int flagsValue) {
        return IkGuiImplMiscWidgets.checkboxFlags(label, flags, flagsValue);
    }

    public static void closeCurrentPopup() {
        IkGuiImplWindows.closeCurrentPopup();
    }

    public static boolean collapsingHeader(String label) {
        return IkGuiImplTrees.collapsingHeader(label, null, TreeNodeFlags.NONE);
    }

    public static boolean collapsingHeader(String label, IkBoolean visible) {
        return IkGuiImplTrees.collapsingHeader(label, visible, TreeNodeFlags.NONE);
    }

    public static boolean collapsingHeader(String label, IkBoolean visible, int treeNodeFlags) {
        return IkGuiImplTrees.collapsingHeader(label, visible, treeNodeFlags);
    }

    public static boolean collapsingHeader(String label, int treeNodeFlags) {
        return IkGuiImplTrees.collapsingHeader(label, null, treeNodeFlags);
    }

    public static boolean colorButton(String label, float[] color) {
        return IkGuiImplMiscWidgets.colorButton(label, color, ColorEditFlags.NONE, 0, 0);
    }

    public static boolean colorButton(String label, float[] color, int colorEditFlags) {
        return IkGuiImplMiscWidgets.colorButton(label, color, colorEditFlags, 0, 0);
    }

    public static boolean colorButton(
            String label, float[] color, int colorEditFlags, float width, float height) {
        return IkGuiImplMiscWidgets.colorButton(label, color, colorEditFlags, width, height);
    }

    public static int colorConvertFloat4ToU32(float r, float g, float b, float a) {
        return Color.rgba(r, g, b, a);
    }

    public static void colorConvertHSVtoRGB(float[] in, float[] out) {
        IkGuiImplUtils.colorConvertHSVtoRGB(in, out);
    }

    public static void colorConvertRGBtoHSV(float[] in, float[] out) {
        IkGuiImplUtils.colorConvertRGBtoHSV(in, out);
    }

    public static Vector4f colorConvertU32ToFloat4(int in) {
        Vector4f value = new Vector4f();
        IkGuiImplUtils.colorConvertU32ToFloat4(in, value);
        return value;
    }

    public static void colorConvertU32ToFloat4(int in, Vector4f out) {
        IkGuiImplUtils.colorConvertU32ToFloat4(in, out);
    }

    public static boolean colorEdit3(String label, float[] value) {
        return IkGuiImplMiscWidgets.colorEdit3(label, value, ColorEditFlags.NONE);
    }

    public static boolean colorEdit3(String label, float[] value, int colorEditFlags) {
        return IkGuiImplMiscWidgets.colorEdit3(label, value, colorEditFlags);
    }

    public static boolean colorEdit4(String label, float[] value) {
        return IkGuiImplMiscWidgets.colorEdit4(label, value, ColorEditFlags.NONE);
    }

    public static boolean colorEdit4(String label, float[] value, int colorEditFlags) {
        return IkGuiImplMiscWidgets.colorEdit4(label, value, colorEditFlags);
    }

    public static boolean colorPicker3(String label, float[] value) {
        return IkGuiImplMiscWidgets.colorPicker3(label, value, ColorEditFlags.NONE);
    }

    public static boolean colorPicker3(String label, float[] value, int colorEditFlags) {
        return IkGuiImplMiscWidgets.colorPicker3(label, value, colorEditFlags);
    }

    public static boolean colorPicker4(String label, float[] value) {
        return IkGuiImplMiscWidgets.colorPicker4(label, value, ColorEditFlags.NONE, null);
    }

    public static boolean colorPicker4(String label, float[] value, int colorEditFlags) {
        return IkGuiImplMiscWidgets.colorPicker4(label, value, colorEditFlags, null);
    }

    public static boolean colorPicker4(
            String label, float[] value, int colorEditFlags, float[] referenceColor) {
        return IkGuiImplMiscWidgets.colorPicker4(label, value, colorEditFlags, referenceColor);
    }

    public static boolean combo(String label, IkInt currentItem, String[] items) {
        return IkGuiImplMiscWidgets.combo(label, currentItem, items, -1);
    }

    public static boolean combo(
            String label, IkInt currentItem, String[] items, int popumaxHeightInItems) {
        return IkGuiImplMiscWidgets.combo(label, currentItem, items, popumaxHeightInItems);
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
        IkGuiImplButtons.context = context;
        IkGuiImplConfig.context = context;
        IkGuiImplDragDrop.context = context;
        IkGuiImplLayout.context = context;
        IkGuiImplLogging.context = context;
        IkGuiImplMiscWidgets.context = context;
        IkGuiImplTables.context = context;
        IkGuiImplTabs.context = context;
        IkGuiImplText.context = context;
        IkGuiImplTrees.context = context;
        IkGuiImplUtils.context = context;
        IkGuiImplWindows.context = context;
        IkGuiInternal.context = context;

        storage = new Storage();

        // TODO(ches) create a dummy window in the background as a fallback
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
        IkGuiImplButtons.context = null;
        IkGuiImplConfig.context = null;
        IkGuiImplDragDrop.context = null;
        IkGuiImplLayout.context = null;
        IkGuiImplLogging.context = null;
        IkGuiImplMiscWidgets.context = null;
        IkGuiImplTables.context = null;
        IkGuiImplTabs.context = null;
        IkGuiImplText.context = null;
        IkGuiImplTrees.context = null;
        IkGuiImplUtils.context = null;
        IkGuiImplWindows.context = null;
        IkGuiInternal.context = null;
        storage = null;
    }

    public static int dockSpace(int id) {
        return IkGuiImplWindows.dockSpace(id, 0, 0, DockNodeFlags.NONE);
    }

    public static int dockSpace(int id, float width, float height) {
        return IkGuiImplWindows.dockSpace(id, width, height, DockNodeFlags.NONE);
    }

    public static int dockSpace(int id, float width, float height, int dockNodeFlags) {
        return IkGuiImplWindows.dockSpace(id, width, height, dockNodeFlags);
    }

    public static int dockSpaceOverViewport() {
        return IkGuiImplWindows.dockSpaceOverViewport(context.mainViewport, DockNodeFlags.NONE);
    }

    public static int dockSpaceOverViewport(Viewport viewport) {
        return IkGuiImplWindows.dockSpaceOverViewport(viewport, DockNodeFlags.NONE);
    }

    public static int dockSpaceOverViewport(Viewport viewport, int dockNodeFlags) {
        return IkGuiImplWindows.dockSpaceOverViewport(viewport, dockNodeFlags);
    }

    public static boolean dragFloat(String label, float[] value) {
        return IkGuiImplMiscWidgets.dragFloat(
                label,
                value,
                1.0f,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat(String label, float[] value, float speed) {
        return IkGuiImplMiscWidgets.dragFloat(
                label,
                value,
                speed,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat(
            String label, float[] value, float speed, float min, float max) {
        return IkGuiImplMiscWidgets.dragFloat(
                label,
                value,
                speed,
                min,
                max,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat(
            String label, float[] value, float speed, float min, float max, String format) {
        return IkGuiImplMiscWidgets.dragFloat(
                label, value, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragFloat(
            String label,
            float[] value,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return IkGuiImplMiscWidgets.dragFloat(label, value, speed, min, max, format, sliderFlags);
    }

    public static boolean dragFloat2(String label, float[] values) {
        return IkGuiImplMiscWidgets.dragFloat2(
                label,
                values,
                1.0f,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat2(String label, float[] values, float speed) {
        return IkGuiImplMiscWidgets.dragFloat2(
                label,
                values,
                speed,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat2(
            String label, float[] values, float speed, float min, float max) {
        return IkGuiImplMiscWidgets.dragFloat2(
                label,
                values,
                speed,
                min,
                max,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat2(
            String label, float[] values, float speed, float min, float max, String format) {
        return IkGuiImplMiscWidgets.dragFloat2(
                label, values, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragFloat2(
            String label,
            float[] values,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return IkGuiImplMiscWidgets.dragFloat2(label, values, speed, min, max, format, sliderFlags);
    }

    public static boolean dragFloat3(String label, float[] values) {
        return IkGuiImplMiscWidgets.dragFloat3(
                label,
                values,
                1.0f,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat3(String label, float[] values, float speed) {
        return IkGuiImplMiscWidgets.dragFloat3(
                label,
                values,
                speed,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat3(
            String label, float[] values, float speed, float min, float max) {
        return IkGuiImplMiscWidgets.dragFloat3(
                label,
                values,
                speed,
                min,
                max,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat3(
            String label, float[] values, float speed, float min, float max, String format) {
        return IkGuiImplMiscWidgets.dragFloat3(
                label, values, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragFloat3(
            String label,
            float[] values,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return IkGuiImplMiscWidgets.dragFloat3(label, values, speed, min, max, format, sliderFlags);
    }

    public static boolean dragFloat4(String label, float[] values) {
        return IkGuiImplMiscWidgets.dragFloat4(
                label,
                values,
                1.0f,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat4(String label, float[] values, float speed) {
        return IkGuiImplMiscWidgets.dragFloat4(
                label,
                values,
                speed,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat4(
            String label, float[] values, float speed, float min, float max) {
        return IkGuiImplMiscWidgets.dragFloat4(
                label,
                values,
                speed,
                min,
                max,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat4(
            String label, float[] values, float speed, float min, float max, String format) {
        return IkGuiImplMiscWidgets.dragFloat4(
                label, values, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragFloat4(
            String label,
            float[] values,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return IkGuiImplMiscWidgets.dragFloat4(label, values, speed, min, max, format, sliderFlags);
    }

    public static boolean dragFloatRange2(String label, float[] currentMin, float[] currentMax) {
        return IkGuiImplMiscWidgets.dragFloatRange2(
                label,
                currentMin,
                currentMax,
                1.0f,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                null,
                SliderFlags.NONE);
    }

    public static boolean dragFloatRange2(
            String label, float[] currentMin, float[] currentMax, float speed) {
        return IkGuiImplMiscWidgets.dragFloatRange2(
                label,
                currentMin,
                currentMax,
                speed,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                null,
                SliderFlags.NONE);
    }

    public static boolean dragFloatRange2(
            String label,
            float[] currentMin,
            float[] currentMax,
            float speed,
            float min,
            float max) {
        return IkGuiImplMiscWidgets.dragFloatRange2(
                label,
                currentMin,
                currentMax,
                speed,
                min,
                max,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                null,
                SliderFlags.NONE);
    }

    public static boolean dragFloatRange2(
            String label,
            float[] currentMin,
            float[] currentMax,
            float speed,
            float min,
            float max,
            String format) {
        return IkGuiImplMiscWidgets.dragFloatRange2(
                label, currentMin, currentMax, speed, min, max, format, null, SliderFlags.NONE);
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
        return IkGuiImplMiscWidgets.dragFloatRange2(
                label,
                currentMin,
                currentMax,
                speed,
                min,
                max,
                format,
                formatMax,
                SliderFlags.NONE);
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
        return IkGuiImplMiscWidgets.dragFloatRange2(
                label, currentMin, currentMax, speed, min, max, format, formatMax, sliderFlags);
    }

    public static boolean dragInt(String label, int[] value) {
        return IkGuiImplMiscWidgets.dragInt(
                label,
                value,
                1.0f,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt(String label, int[] value, float speed) {
        return IkGuiImplMiscWidgets.dragInt(
                label,
                value,
                speed,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt(String label, int[] value, float speed, int min, int max) {
        return IkGuiImplMiscWidgets.dragInt(
                label,
                value,
                speed,
                min,
                max,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt(
            String label, int[] value, float speed, int min, int max, String format) {
        return IkGuiImplMiscWidgets.dragInt(
                label, value, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragInt(
            String label,
            int[] value,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return IkGuiImplMiscWidgets.dragInt(label, value, speed, min, max, format, sliderFlags);
    }

    public static boolean dragInt2(String label, int[] values) {
        return IkGuiImplMiscWidgets.dragInt2(
                label,
                values,
                1.0f,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt2(String label, int[] values, float speed) {
        return IkGuiImplMiscWidgets.dragInt2(
                label,
                values,
                speed,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt2(String label, int[] values, float speed, int min, int max) {
        return IkGuiImplMiscWidgets.dragInt2(
                label,
                values,
                speed,
                min,
                max,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt2(
            String label, int[] values, float speed, float min, float max, String format) {
        return IkGuiImplMiscWidgets.dragInt2(
                label, values, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragInt2(
            String label,
            int[] values,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return IkGuiImplMiscWidgets.dragInt2(label, values, speed, min, max, format, sliderFlags);
    }

    public static boolean dragInt3(String label, int[] values) {
        return IkGuiImplMiscWidgets.dragInt3(
                label,
                values,
                1.0f,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt3(String label, int[] values, float speed) {
        return IkGuiImplMiscWidgets.dragInt3(
                label,
                values,
                speed,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt3(String label, int[] values, float speed, int min, int max) {
        return IkGuiImplMiscWidgets.dragInt3(
                label,
                values,
                speed,
                min,
                max,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt3(
            String label, int[] values, float speed, int min, int max, String format) {
        return IkGuiImplMiscWidgets.dragInt3(
                label, values, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragInt3(
            String label,
            int[] values,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return IkGuiImplMiscWidgets.dragInt3(label, values, speed, min, max, format, sliderFlags);
    }

    public static boolean dragInt4(String label, int[] values) {
        return IkGuiImplMiscWidgets.dragInt4(
                label,
                values,
                1.0f,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt4(String label, int[] values, float speed) {
        return IkGuiImplMiscWidgets.dragInt4(
                label,
                values,
                speed,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt4(String label, int[] values, float speed, int min, int max) {
        return IkGuiImplMiscWidgets.dragInt4(
                label,
                values,
                speed,
                min,
                max,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt4(
            String label, int[] values, float speed, int min, int max, String format) {
        return IkGuiImplMiscWidgets.dragInt4(
                label, values, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragInt4(
            String label,
            int[] values,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return IkGuiImplMiscWidgets.dragInt4(label, values, speed, min, max, format, sliderFlags);
    }

    public static boolean dragIntRange2(String label, int[] currentMin, int[] currentMax) {
        return IkGuiImplMiscWidgets.dragIntRange2(
                label,
                currentMin,
                currentMax,
                1.0f,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                null,
                SliderFlags.NONE);
    }

    public static boolean dragIntRange2(
            String label, int[] currentMin, int[] currentMax, float speed) {
        return IkGuiImplMiscWidgets.dragIntRange2(
                label,
                currentMin,
                currentMax,
                speed,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                null,
                SliderFlags.NONE);
    }

    public static boolean dragIntRange2(
            String label, int[] currentMin, int[] currentMax, float speed, int min, int max) {
        return IkGuiImplMiscWidgets.dragIntRange2(
                label,
                currentMin,
                currentMax,
                speed,
                min,
                max,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                null,
                SliderFlags.NONE);
    }

    public static boolean dragIntRange2(
            String label,
            int[] currentMin,
            int[] currentMax,
            float speed,
            int min,
            int max,
            String format) {
        return IkGuiImplMiscWidgets.dragIntRange2(
                label, currentMin, currentMax, speed, min, max, format, null, SliderFlags.NONE);
    }

    public static boolean dragIntRange2(
            String label,
            int[] currentMin,
            int[] currentMax,
            float speed,
            int min,
            int max,
            String format,
            String formatMax) {
        return IkGuiImplMiscWidgets.dragIntRange2(
                label,
                currentMin,
                currentMax,
                speed,
                min,
                max,
                format,
                formatMax,
                SliderFlags.NONE);
    }

    public static boolean dragIntRange2(
            String label,
            int[] currentMin,
            int[] currentMax,
            float speed,
            int min,
            int max,
            String format,
            String formatMax,
            int sliderFlags) {
        return IkGuiImplMiscWidgets.dragIntRange2(
                label, currentMin, currentMax, speed, min, max, format, formatMax, sliderFlags);
    }

    public static boolean dragScalar(
            String label, @NonNull SliderDataType dataType, @NonNull Object data) {
        return IkGuiImplMiscWidgets.dragScalar(
                label, dataType, data, 1.0f, 0, 0, null, SliderFlags.NONE);
    }

    public static boolean dragScalar(
            String label, @NonNull SliderDataType dataType, @NonNull Object data, float speed) {
        return IkGuiImplMiscWidgets.dragScalar(
                label, dataType, data, speed, 0, 0, null, SliderFlags.NONE);
    }

    public static boolean dragScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            float speed,
            double min,
            double max) {
        return IkGuiImplMiscWidgets.dragScalar(
                label, dataType, data, speed, min, max, null, SliderFlags.NONE);
    }

    public static boolean dragScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            float speed,
            double min,
            double max,
            String format) {
        return IkGuiImplMiscWidgets.dragScalar(
                label, dataType, data, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            float speed,
            double min,
            double max,
            String format,
            int sliderFlags) {
        return IkGuiImplMiscWidgets.dragScalar(
                label, dataType, data, speed, min, max, format, sliderFlags);
    }

    public static boolean dragScalarN(
            String label, @NonNull SliderDataType dataType, @NonNull Object data, int components) {
        return IkGuiImplMiscWidgets.dragScalarN(
                label, dataType, data, components, 1.0f, 0, 0, null, SliderFlags.NONE);
    }

    public static boolean dragScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            float speed) {
        return IkGuiImplMiscWidgets.dragScalarN(
                label, dataType, data, components, speed, 0, 0, null, SliderFlags.NONE);
    }

    public static boolean dragScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            float speed,
            double min,
            double max) {
        return IkGuiImplMiscWidgets.dragScalarN(
                label, dataType, data, components, speed, min, max, null, SliderFlags.NONE);
    }

    public static boolean dragScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            float speed,
            double min,
            double max,
            String format) {
        return IkGuiImplMiscWidgets.dragScalarN(
                label, dataType, data, components, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            float speed,
            double min,
            double max,
            String format,
            int sliderFlags) {
        return IkGuiImplMiscWidgets.dragScalarN(
                label, dataType, data, components, speed, min, max, format, sliderFlags);
    }

    public static void dummy(@NonNull Vector2f size) {
        IkGuiImplMiscWidgets.dummy(size.x, size.y);
    }

    public static void dummy(float width, float height) {
        IkGuiImplMiscWidgets.dummy(width, height);
    }

    public static void end() {
        IkGuiImplWindows.end();
    }

    public static void endChild() {
        IkGuiImplWindows.endChild();
    }

    public static void endCombo() {
        IkGuiImplMiscWidgets.endCombo();
    }

    public static void endDisabled() {
        IkGuiImplUtils.endDisabled();
    }

    public static void endDragDropSource() {
        IkGuiImplDragDrop.endDragDropSource();
    }

    public static void endDragDropTarget() {
        IkGuiImplDragDrop.endDragDropTarget();
    }

    public static void endFrame() {
        IkGuiImplUtils.endFrame();
    }

    public static void endGroup() {
        IkGuiImplLayout.endGroup();
    }

    public static void endListBox() {
        IkGuiImplMiscWidgets.endListBox();
    }

    public static void endMainMenuBar() {
        IkGuiImplWindows.endMainMenuBar();
    }

    public static void endMenu() {
        IkGuiImplWindows.endMenu();
    }

    public static void endMenuBar() {
        IkGuiImplWindows.endMenuBar();
    }

    public static void endPopup() {
        IkGuiImplWindows.endPopup();
    }

    public static void endTabBar() {
        IkGuiImplTabs.endTabBar();
    }

    public static void endTabItem() {
        IkGuiImplTabs.endTabItem();
    }

    public static void endTable() {
        IkGuiImplTables.endTable();
    }

    public static void endTooltip() {
        IkGuiImplWindows.endTooltip();
    }

    public static Viewport findViewportByID(int id) {
        return IkGuiImplUtils.findViewportByID(id);
    }

    public static DrawList getBackgroundDrawList() {
        return IkGuiImplUtils.getBackgroundDrawList(context.mainViewport);
    }

    public static DrawList getBackgroundDrawList(Viewport viewport) {
        return IkGuiImplUtils.getBackgroundDrawList(viewport);
    }

    public static String getClipboardText() {
        return IkGuiImplUtils.getClipboardText();
    }

    /**
     * Fetch the current style color as it is stored in the style, after color mod overrides, but
     * without the global alpha values.
     *
     * @param type The type of color we want.
     * @return The color in RGBA format.
     */
    public static int getColor(@NonNull ColorType type) {
        return IkGuiImplUtils.getColor(type);
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
        return IkGuiImplUtils.getColor(type, alphaMultiplier);
    }

    /**
     * Fetch the current style color from the style, after color mod overrides, and after applying
     * the global alpha value.
     *
     * @param styleColor The color we want.
     * @return The color in RGBA format.
     */
    public static int getColorWithGlobalAlpha(@NonNull ColorType styleColor) {
        return IkGuiImplUtils.getColorWithGlobalAlpha(styleColor);
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
        return IkGuiImplUtils.getColorWithGlobalAlpha(styleColor, disabled);
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
        return IkGuiImplUtils.getContentRegionAvailable();
    }

    public static void getContentRegionAvailable(@NonNull Vector2f region) {
        IkGuiImplUtils.getContentRegionAvailable(region);
    }

    public static Vector2f getContentRegionAvailableMax() {
        return IkGuiImplUtils.getContentRegionAvailableMax();
    }

    public static void getContentRegionAvailableMax(@NonNull Vector2f region) {
        IkGuiImplUtils.getContentRegionAvailableMax(region);
    }

    public static float getContentRegionAvailableMaxX() {
        return IkGuiImplUtils.getContentRegionAvailableMaxX();
    }

    public static float getContentRegionAvailableMaxY() {
        return IkGuiImplUtils.getContentRegionAvailableMaxY();
    }

    public static Vector2f getContentRegionAvailableMin() {
        return IkGuiImplUtils.getContentRegionAvailableMin();
    }

    public static void getContentRegionAvailableMin(@NonNull Vector2f region) {
        IkGuiImplUtils.getContentRegionAvailableMin(region);
    }

    public static float getContentRegionAvailableMinX() {
        return IkGuiImplUtils.getContentRegionAvailableMinX();
    }

    public static float getContentRegionAvailableMinY() {
        return IkGuiImplUtils.getContentRegionAvailableMinY();
    }

    public static float getContentRegionAvailableX() {
        return IkGuiImplUtils.getContentRegionAvailableX();
    }

    public static float getContentRegionAvailableY() {
        return IkGuiImplUtils.getContentRegionAvailableY();
    }

    public static Vector2f getCursorPos() {
        return IkGuiImplUtils.getCursorPos();
    }

    public static void getCursorPos(@NonNull Vector2f pos) {
        IkGuiImplUtils.getCursorPos(pos);
    }

    public static float getCursorPosX() {
        return IkGuiImplUtils.getCursorPosX();
    }

    public static float getCursorPosY() {
        return IkGuiImplUtils.getCursorPosY();
    }

    public static Vector2f getCursorScreenPos() {
        return IkGuiImplUtils.getCursorScreenPos();
    }

    public static void getCursorScreenPos(@NonNull Vector2f pos) {
        IkGuiImplUtils.getCursorScreenPos(pos);
    }

    public static float getCursorScreenPosX() {
        return IkGuiImplUtils.getCursorScreenPosX();
    }

    public static float getCursorScreenPosY() {
        return IkGuiImplUtils.getCursorScreenPosY();
    }

    public static Vector2f getCursorStartPos() {
        return IkGuiImplUtils.getCursorStartPos();
    }

    public static void getCursorStartPos(@NonNull Vector2f pos) {
        IkGuiImplUtils.getCursorStartPos(pos);
    }

    public static float getCursorStartPosX() {
        return IkGuiImplUtils.getCursorStartPosX();
    }

    public static float getCursorStartPosY() {
        return IkGuiImplUtils.getCursorStartPosY();
    }

    public static <T> T getDragDropPayload() {
        return IkGuiImplDragDrop.getDragDropPayload();
    }

    public static <T> T getDragDropPayload(Class<T> aClass) {
        return IkGuiImplDragDrop.getDragDropPayload(aClass);
    }

    public static <T> T getDragDropPayload(String dataType) {
        return IkGuiImplDragDrop.getDragDropPayload(dataType);
    }

    public static int getFontSize() {
        return context.fontSize;
    }

    public static DrawList getForegroundDrawList() {
        return IkGuiImplUtils.getForegroundDrawList(context.mainViewport);
    }

    public static DrawList getForegroundDrawList(Viewport viewport) {
        return IkGuiImplUtils.getForegroundDrawList(viewport);
    }

    public static int getFrameCount() {
        return IkGuiImplUtils.getFrameCount();
    }

    public static float getFrameHeight() {
        return IkGuiImplLayout.getFrameHeight();
    }

    public static float getFrameHeightWithSpacing() {
        return IkGuiImplLayout.getFrameHeightWithSpacing();
    }

    /**
     * Get an ID given the current ID stack.
     *
     * @param name The name of the element.
     * @return The new hash.
     * @see Hash#getID(String)
     * @see Hash#getID(String, int)
     */
    public static int getID(String name) {
        return Hash.getID(name, context.idStack.peek());
    }

    public static IkIO getIO() {
        return context.io;
    }

    public static float getItemRectHeight() {
        return IkGuiImplLayout.getItemRectHeight();
    }

    public static Vector2f getItemRectMax() {
        Vector2f value = new Vector2f();
        IkGuiImplLayout.getItemRectMax(value);
        return value;
    }

    public static void getItemRectMax(@NonNull Vector2f value) {
        IkGuiImplLayout.getItemRectMax(value);
    }

    public static float getItemRectMaxX() {
        return IkGuiImplLayout.getItemRectMaxX();
    }

    public static float getItemRectMaxY() {
        return IkGuiImplLayout.getItemRectMaxY();
    }

    public static Vector2f getItemRectMin() {
        Vector2f value = new Vector2f();
        IkGuiImplLayout.getItemRectMin(value);
        return value;
    }

    public static void getItemRectMin(@NonNull Vector2f value) {
        IkGuiImplLayout.getItemRectMin(value);
    }

    public static float getItemRectMinX() {
        return IkGuiImplLayout.getItemRectMinX();
    }

    public static float getItemRectMinY() {
        return IkGuiImplLayout.getItemRectMinY();
    }

    public static Vector2f getItemRectSize() {
        Vector2f value = new Vector2f();
        IkGuiImplLayout.getItemRectSize(value);
        return value;
    }

    public static void getItemRectSize(@NonNull Vector2f value) {
        IkGuiImplLayout.getItemRectSize(value);
    }

    public static float getItemRectWidth() {
        return IkGuiImplLayout.getItemRectWidth();
    }

    public static boolean getKeyPressedAmount(int userKeyIndex, float repeatDelay, float rate) {
        // TODO(ches) complete this
        return false;
    }

    public static Viewport getMainViewport() {
        return context.mainViewport;
    }

    public static int getMouseClickedCount(@NonNull MouseButton button) {
        return context.io.mouseClickedCount[button.index];
    }

    public static int getMouseCursor() {
        return IkGuiImplUtils.getMouseCursor();
    }

    public static Vector2f getMouseDragDelta() {
        Vector2f value = new Vector2f();
        IkGuiImplUtils.getMouseDragDelta(value, MouseButton.LEFT, -1.0f);
        return value;
    }

    public static Vector2f getMouseDragDelta(@NonNull MouseButton button) {
        Vector2f value = new Vector2f();
        IkGuiImplUtils.getMouseDragDelta(value, button, -1.0f);
        return value;
    }

    public static Vector2f getMouseDragDelta(@NonNull MouseButton button, float lockThreshold) {
        Vector2f value = new Vector2f();
        IkGuiImplUtils.getMouseDragDelta(value, button, lockThreshold);
        return value;
    }

    public static void getMouseDragDelta(@NonNull Vector2f output) {
        IkGuiImplUtils.getMouseDragDelta(output, MouseButton.LEFT, -1.0f);
    }

    public static void getMouseDragDelta(@NonNull Vector2f output, @NonNull MouseButton button) {
        IkGuiImplUtils.getMouseDragDelta(output, button, -1.0f);
    }

    public static void getMouseDragDelta(
            @NonNull Vector2f output, @NonNull MouseButton button, float lockThreshold) {
        IkGuiImplUtils.getMouseDragDelta(output, button, lockThreshold);
    }

    public static float getMouseDragDeltaX() {
        return IkGuiImplUtils.getMouseDragDeltaX(MouseButton.LEFT, -1.0f);
    }

    public static float getMouseDragDeltaX(@NonNull MouseButton button) {
        return IkGuiImplUtils.getMouseDragDeltaX(button, -1.0f);
    }

    public static float getMouseDragDeltaX(@NonNull MouseButton button, float lockThreshold) {
        return IkGuiImplUtils.getMouseDragDeltaX(button, lockThreshold);
    }

    public static float getMouseDragDeltaY() {
        return IkGuiImplUtils.getMouseDragDeltaY(MouseButton.LEFT, -1.0f);
    }

    public static float getMouseDragDeltaY(@NonNull MouseButton button) {
        return IkGuiImplUtils.getMouseDragDeltaY(button, -1.0f);
    }

    public static float getMouseDragDeltaY(@NonNull MouseButton button, float lockThreshold) {
        return IkGuiImplUtils.getMouseDragDeltaY(button, lockThreshold);
    }

    public static Vector2f getMousePos() {
        Vector2f value = new Vector2f();
        IkGuiImplUtils.getMousePos(value);
        return value;
    }

    public static void getMousePos(@NonNull Vector2f output) {
        IkGuiImplUtils.getMousePos(output);
    }

    public static Vector2f getMousePosOnOpeningCurrentPopup() {
        Vector2f value = new Vector2f();
        IkGuiImplUtils.getMousePosOnOpeningCurrentPopup(value);
        return value;
    }

    public static void getMousePosOnOpeningCurrentPopup(@NonNull Vector2f output) {
        IkGuiImplUtils.getMousePosOnOpeningCurrentPopup(output);
    }

    public static float getMousePosOnOpeningCurrentPopupX() {
        return IkGuiImplUtils.getMousePosOnOpeningCurrentPopupX();
    }

    public static float getMousePosOnOpeningCurrentPopupY() {
        return IkGuiImplUtils.getMousePosOnOpeningCurrentPopupY();
    }

    public static float getMousePosX() {
        return context.io.mousePosition.x;
    }

    public static float getMousePosY() {
        return context.io.mousePosition.y;
    }

    public static PlatformIO getPlatformIO() {
        return context.platformIO;
    }

    public static float getScrollX() {
        return IkGuiImplUtils.getScrollX();
    }

    public static float getScrollY() {
        return IkGuiImplUtils.getScrollY();
    }

    public static Storage getStateStorage() {
        return storage;
    }

    /**
     * Fetch the current style variable, inclusive of style mods. If the variable is of a different
     * type or cardinality, this won't work and 0 will be returned.
     *
     * @param variable The variable to read.
     * @return The current value after mods.
     */
    public static float getStyleVarFloat(@NonNull StyleVariable variable) {
        return IkGuiImplUtils.getStyleVarFloat(variable);
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
        IkGuiImplUtils.getStyleVarFloat2(variable, result);
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
        IkGuiImplUtils.getStyleVarFloat2(variable, target);
    }

    /**
     * Fetch the current style variable, inclusive of style mods. If the variable is of a different
     * type or cardinality, this won't work and 0 will be returned.
     *
     * @param variable The variable to read.
     * @return The current value after mods.
     */
    public static int getStyleVarInt(@NonNull StyleVariable variable) {
        return IkGuiImplUtils.getStyleVarInt(variable);
    }

    public static int getTextLineHeight() {
        return IkGuiImplLayout.getTextLineHeight();
    }

    public static int getTextLineHeightWithSpacing() {
        return IkGuiImplLayout.getTextLineHeightWithSpacing();
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
        return IkGuiImplLayout.getTreeNodeToLabelSpacing();
    }

    public static int getWindowDockID() {
        return IkGuiImplWindows.getWindowDockID();
    }

    public static float getWindowHeight() {
        return IkGuiImplWindows.getWindowHeight();
    }

    public static Vector2f getWindowPos() {
        Vector2f pos = new Vector2f();
        IkGuiImplWindows.getWindowPos(pos);
        return pos;
    }

    public static void getWindowPos(@NonNull Vector2f pos) {
        IkGuiImplWindows.getWindowPos(pos);
    }

    public static float getWindowPosX() {
        return IkGuiImplWindows.getWindowPosX();
    }

    public static float getWindowPosY() {
        return IkGuiImplWindows.getWindowPosY();
    }

    public static Vector2f getWindowSize() {
        Vector2f size = new Vector2f();
        IkGuiImplWindows.getWindowSize(size);
        return size;
    }

    public static void getWindowSize(@NonNull Vector2f size) {
        IkGuiImplWindows.getWindowSize(size);
    }

    public static float getWindowWidth() {
        return IkGuiImplWindows.getWindowWidth();
    }

    public static void image(int textureID, float width, float height) {
        IkGuiImplMiscWidgets.image(textureID, width, height, 0.0f, 0.0f, 1.0f, 1.0f);
    }

    public static void image(
            int textureID, float width, float height, float u0, float v0, float u1, float v1) {
        IkGuiImplMiscWidgets.image(textureID, width, height, u0, v0, u1, v1);
    }

    public static void image(
            int textureID, @NonNull Vector2f size, @NonNull Vector2f uv0, @NonNull Vector2f uv1) {
        IkGuiImplMiscWidgets.image(textureID, size.x, size.y, uv0.x, uv0.y, uv1.x, uv1.y);
    }

    public static void image(
            int textureID,
            float width,
            float height,
            @NonNull Vector2f uv0,
            @NonNull Vector2f uv1) {
        IkGuiImplMiscWidgets.image(textureID, width, height, uv0.x, uv0.y, uv1.x, uv1.y);
    }

    public static void image(int textureID, @NonNull Vector2f size) {
        IkGuiImplMiscWidgets.image(textureID, size.x, size.y, 0.0f, 0.0f, 1.0f, 1.0f);
    }

    public static void image(
            int textureID, @NonNull Vector2f size, float u0, float v0, float u1, float v1) {
        IkGuiImplMiscWidgets.image(textureID, size.x, size.y, u0, v0, u1, v1);
    }

    public static boolean imageButton(int textureID, @NonNull Vector2f size) {
        return IkGuiImplMiscWidgets.imageButton(
                textureID, size.x, size.y, 0.0f, 0.0f, 1.0f, 1.0f, Color.CLEAR, Color.WHITE);
    }

    public static boolean imageButton(int textureID, float width, float height) {
        return IkGuiImplMiscWidgets.imageButton(
                textureID, width, height, 0.0f, 0.0f, 1.0f, 1.0f, Color.CLEAR, Color.WHITE);
    }

    public static boolean imageButton(
            int textureID, @NonNull Vector2f size, @NonNull Vector2f uv0, @NonNull Vector2f uv1) {
        return IkGuiImplMiscWidgets.imageButton(
                textureID, size.x, size.y, uv0.x, uv0.y, uv1.x, uv1.y, Color.CLEAR, Color.WHITE);
    }

    public static boolean imageButton(
            int textureID, float width, float height, float u0, float v0, float u1, float v1) {
        return IkGuiImplMiscWidgets.imageButton(
                textureID, width, height, u0, v0, u1, v1, Color.CLEAR, Color.WHITE);
    }

    public static boolean imageButton(
            int textureID,
            @NonNull Vector2f size,
            @NonNull Vector2f uv0,
            @NonNull Vector2f uv1,
            int background) {
        return IkGuiImplMiscWidgets.imageButton(
                textureID, size.x, size.y, uv0.x, uv0.y, uv1.x, uv1.y, background, Color.WHITE);
    }

    public static boolean imageButton(
            int textureID,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            int background) {
        return IkGuiImplMiscWidgets.imageButton(
                textureID, width, height, u0, v0, u1, v1, background, Color.WHITE);
    }

    public static boolean imageButton(
            int textureID,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            float backgroundR,
            float backgroundG,
            float backgroundB,
            float backgroundA) {
        return IkGuiImplMiscWidgets.imageButton(
                textureID,
                width,
                height,
                u0,
                v0,
                u1,
                v1,
                Color.rgba(backgroundR, backgroundG, backgroundB, backgroundA),
                Color.WHITE);
    }

    public static boolean imageButton(
            int textureID,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            int background,
            int tint) {
        return IkGuiImplMiscWidgets.imageButton(
                textureID, width, height, u0, v0, u1, v1, background, tint);
    }

    public static boolean imageButton(
            int textureID,
            @NonNull Vector2f size,
            @NonNull Vector2f uv0,
            @NonNull Vector2f uv1,
            @NonNull Vector4f backgroundColor,
            @NonNull Vector4f tintColor) {
        return IkGuiImplMiscWidgets.imageButton(
                textureID,
                size.x,
                size.y,
                uv0.x,
                uv0.y,
                uv1.x,
                uv1.y,
                Color.rgba(backgroundColor),
                Color.rgba(tintColor));
    }

    public static boolean imageButton(
            int textureID,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            float backgroundR,
            float backgroundG,
            float backgroundB,
            float backgroundA,
            float tintR,
            float tintG,
            float tintB,
            float tintA) {
        return IkGuiImplMiscWidgets.imageButton(
                textureID,
                width,
                height,
                u0,
                v0,
                u1,
                v1,
                Color.rgba(backgroundR, backgroundG, backgroundB, backgroundA),
                Color.rgba(tintR, tintG, tintB, tintA));
    }

    public static void imageWithBackground(
            int textureID, float width, float height, int backgroundColor) {
        IkGuiImplMiscWidgets.imageWithBackground(
                textureID, width, height, 0.0f, 0.0f, 1.0f, 1.0f, backgroundColor, Color.WHITE);
    }

    public static void imageWithBackground(
            int textureID, @NonNull Vector2f size, int backgroundColor) {
        IkGuiImplMiscWidgets.imageWithBackground(
                textureID, size.x, size.y, 0.0f, 0.0f, 1.0f, 1.0f, backgroundColor, Color.WHITE);
    }

    public static void imageWithBackground(
            int textureID,
            @NonNull Vector2f size,
            @NonNull Vector2f uv0,
            @NonNull Vector2f uv1,
            int backgroundColor) {
        IkGuiImplMiscWidgets.imageWithBackground(
                textureID,
                size.x,
                size.y,
                uv0.x,
                uv0.y,
                uv1.x,
                uv1.y,
                backgroundColor,
                Color.WHITE);
    }

    public static void imageWithBackground(
            int textureID,
            @NonNull Vector2f size,
            @NonNull Vector2f uv0,
            @NonNull Vector2f uv1,
            @NonNull Vector4f backgroundColor) {
        IkGuiImplMiscWidgets.imageWithBackground(
                textureID,
                size.x,
                size.y,
                uv0.x,
                uv0.y,
                uv1.x,
                uv1.y,
                Color.rgba(backgroundColor),
                Color.WHITE);
    }

    public static void imageWithBackground(
            int textureID, @NonNull Vector2f size, int backgroundColor, int tintColor) {
        IkGuiImplMiscWidgets.imageWithBackground(
                textureID, size.x, size.y, 0.0f, 0.0f, 1.0f, 1.0f, backgroundColor, tintColor);
    }

    public static void imageWithBackground(
            int textureID,
            @NonNull Vector2f size,
            @NonNull Vector2f uv0,
            @NonNull Vector2f uv1,
            int backgroundColor,
            int tintColor) {
        IkGuiImplMiscWidgets.imageWithBackground(
                textureID, size.x, size.y, uv0.x, uv0.y, uv1.x, uv1.y, backgroundColor, tintColor);
    }

    public static void imageWithBackground(
            int textureID,
            @NonNull Vector2f size,
            @NonNull Vector2f uv0,
            @NonNull Vector2f uv1,
            @NonNull Vector4f backgroundColor,
            @NonNull Vector4f tintColor) {
        IkGuiImplMiscWidgets.imageWithBackground(
                textureID,
                size.x,
                size.y,
                uv0.x,
                uv0.y,
                uv1.x,
                uv1.y,
                Color.rgba(backgroundColor),
                Color.rgba(tintColor));
    }

    public static void indent() {
        IkGuiImplLayout.indent(0);
    }

    /**
     * Move content position towards the right by width, or by the style's indent spacing if width
     * less than or equal to 0.
     *
     * @param width The width in pixels.
     */
    public static void indent(float width) {
        IkGuiImplLayout.indent(width);
    }

    public static boolean inputDouble(String label, IkDouble value) {
        return IkGuiImplMiscWidgets.inputDouble(
                label,
                value.getData(),
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.DOUBLE_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputDouble(String label, double[] value) {
        return IkGuiImplMiscWidgets.inputDouble(
                label,
                value,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.DOUBLE_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputDouble(String label, IkDouble value, double step) {
        return IkGuiImplMiscWidgets.inputDouble(
                label,
                value.getData(),
                step,
                0.0f,
                IkGuiImplMiscWidgets.DOUBLE_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputDouble(String label, double[] value, double step) {
        return IkGuiImplMiscWidgets.inputDouble(
                label,
                value,
                step,
                0.0f,
                IkGuiImplMiscWidgets.DOUBLE_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputDouble(String label, IkDouble value, double step, double stepFast) {
        return IkGuiImplMiscWidgets.inputDouble(
                label,
                value.getData(),
                step,
                stepFast,
                IkGuiImplMiscWidgets.DOUBLE_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputDouble(String label, double[] value, double step, double stepFast) {
        return IkGuiImplMiscWidgets.inputDouble(
                label,
                value,
                step,
                stepFast,
                IkGuiImplMiscWidgets.DOUBLE_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputDouble(
            String label, IkDouble value, double step, double stepFast, String format) {
        return IkGuiImplMiscWidgets.inputDouble(
                label, value.getData(), step, stepFast, format, InputTextFlags.NONE);
    }

    public static boolean inputDouble(
            String label, double[] value, double step, double stepFast, String format) {
        return IkGuiImplMiscWidgets.inputDouble(
                label, value, step, stepFast, format, InputTextFlags.NONE);
    }

    public static boolean inputDouble(
            String label,
            IkDouble value,
            double step,
            double stepFast,
            String format,
            int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputDouble(
                label, value.getData(), step, stepFast, format, inputTextFlags);
    }

    public static boolean inputDouble(
            String label,
            double[] value,
            double step,
            double stepFast,
            String format,
            int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputDouble(
                label, value, step, stepFast, format, inputTextFlags);
    }

    public static boolean inputFloat(String label, IkFloat value) {
        return IkGuiImplMiscWidgets.inputFloat(
                label,
                value.getData(),
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputFloat(String label, float[] value) {
        return IkGuiImplMiscWidgets.inputFloat(
                label,
                value,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputFloat(String label, IkFloat value, float step) {
        return IkGuiImplMiscWidgets.inputFloat(
                label,
                value.getData(),
                step,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputFloat(String label, float[] value, float step) {
        return IkGuiImplMiscWidgets.inputFloat(
                label,
                value,
                step,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputFloat(String label, IkFloat value, float step, float stepFast) {
        return IkGuiImplMiscWidgets.inputFloat(
                label,
                value.getData(),
                step,
                stepFast,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputFloat(String label, float[] value, float step, float stepFast) {
        return IkGuiImplMiscWidgets.inputFloat(
                label,
                value,
                step,
                stepFast,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputFloat(
            String label, IkFloat value, float step, float stepFast, String format) {
        return IkGuiImplMiscWidgets.inputFloat(
                label, value.getData(), step, stepFast, format, InputTextFlags.NONE);
    }

    public static boolean inputFloat(
            String label, float[] value, float step, float stepFast, String format) {
        return IkGuiImplMiscWidgets.inputFloat(
                label, value, step, stepFast, format, InputTextFlags.NONE);
    }

    public static boolean inputFloat(
            String label,
            IkFloat value,
            float step,
            float stepFast,
            String format,
            int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputFloat(
                label, value.getData(), step, stepFast, format, inputTextFlags);
    }

    public static boolean inputFloat(
            String label,
            float[] value,
            float step,
            float stepFast,
            String format,
            int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputFloat(
                label, value, step, stepFast, format, inputTextFlags);
    }

    public static boolean inputFloat2(String label, float[] values) {
        return IkGuiImplMiscWidgets.inputFloat2(
                label, values, IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT, InputTextFlags.NONE);
    }

    public static boolean inputFloat2(String label, float[] values, String format) {
        return IkGuiImplMiscWidgets.inputFloat2(label, values, format, InputTextFlags.NONE);
    }

    public static boolean inputFloat2(String label, float[] values, int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputFloat2(
                label, values, IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT, inputTextFlags);
    }

    public static boolean inputFloat2(
            String label, float[] values, String format, int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputFloat2(label, values, format, inputTextFlags);
    }

    public static boolean inputFloat3(String label, float[] values) {
        return IkGuiImplMiscWidgets.inputFloat3(
                label, values, IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT, InputTextFlags.NONE);
    }

    public static boolean inputFloat3(String label, float[] values, String format) {
        return IkGuiImplMiscWidgets.inputFloat3(label, values, format, InputTextFlags.NONE);
    }

    public static boolean inputFloat3(String label, float[] values, int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputFloat3(
                label, values, IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT, inputTextFlags);
    }

    public static boolean inputFloat3(
            String label, float[] values, String format, int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputFloat3(label, values, format, inputTextFlags);
    }

    public static boolean inputFloat4(String label, float[] values) {
        return IkGuiImplMiscWidgets.inputFloat4(
                label, values, IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT, InputTextFlags.NONE);
    }

    public static boolean inputFloat4(String label, float[] values, String format) {
        return IkGuiImplMiscWidgets.inputFloat4(label, values, format, InputTextFlags.NONE);
    }

    public static boolean inputFloat4(String label, float[] values, int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputFloat4(
                label, values, IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT, inputTextFlags);
    }

    public static boolean inputFloat4(
            String label, float[] values, String format, int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputFloat4(label, values, format, inputTextFlags);
    }

    public static boolean inputInt(String label, IkInt value) {
        return IkGuiImplMiscWidgets.inputInt(label, value.getData(), 1, 100, InputTextFlags.NONE);
    }

    public static boolean inputInt(String label, int[] value) {
        return IkGuiImplMiscWidgets.inputInt(label, value, 1, 100, InputTextFlags.NONE);
    }

    public static boolean inputInt(String label, IkInt value, int step) {
        return IkGuiImplMiscWidgets.inputInt(
                label, value.getData(), step, 100, InputTextFlags.NONE);
    }

    public static boolean inputInt(String label, int[] value, int step) {
        return IkGuiImplMiscWidgets.inputInt(label, value, step, 100, InputTextFlags.NONE);
    }

    public static boolean inputInt(String label, IkInt value, int step, int stepFast) {
        return IkGuiImplMiscWidgets.inputInt(
                label, value.getData(), step, stepFast, InputTextFlags.NONE);
    }

    public static boolean inputInt(String label, int[] value, int step, int stepFast) {
        return IkGuiImplMiscWidgets.inputInt(label, value, step, stepFast, InputTextFlags.NONE);
    }

    public static boolean inputInt(
            String label, IkInt value, int step, int stepFast, int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputInt(
                label, value.getData(), step, stepFast, inputTextFlags);
    }

    public static boolean inputInt(
            String label, int[] value, int step, int stepFast, int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputInt(label, value, step, stepFast, inputTextFlags);
    }

    public static boolean inputInt2(String label, int[] values) {
        return IkGuiImplMiscWidgets.inputInt2(label, values, InputTextFlags.NONE);
    }

    public static boolean inputInt2(String label, int[] values, int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputInt2(label, values, inputTextFlags);
    }

    public static boolean inputInt3(String label, int[] values) {
        return IkGuiImplMiscWidgets.inputInt3(label, values, InputTextFlags.NONE);
    }

    public static boolean inputInt3(String label, int[] values, int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputInt3(label, values, inputTextFlags);
    }

    public static boolean inputInt4(String label, int[] values) {
        return IkGuiImplMiscWidgets.inputInt4(label, values, InputTextFlags.NONE);
    }

    public static boolean inputInt4(String label, int[] values, int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputInt4(label, values, inputTextFlags);
    }

    public static boolean inputScalar(
            String label, @NonNull SliderDataType dataType, @NonNull Object data) {
        return IkGuiImplMiscWidgets.inputScalar(
                label, dataType, data, 0.0d, 0.0d, null, InputTextFlags.NONE);
    }

    public static boolean inputScalar(
            String label, @NonNull SliderDataType dataType, @NonNull Object data, double step) {
        return IkGuiImplMiscWidgets.inputScalar(
                label, dataType, data, step, 0.0d, null, InputTextFlags.NONE);
    }

    public static boolean inputScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            double step,
            double stepFast) {
        return IkGuiImplMiscWidgets.inputScalar(
                label, dataType, data, step, stepFast, null, InputTextFlags.NONE);
    }

    public static boolean inputScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            double step,
            double stepFast,
            String format) {
        return IkGuiImplMiscWidgets.inputScalar(
                label, dataType, data, step, stepFast, format, InputTextFlags.NONE);
    }

    public static boolean inputScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            float step,
            float stepFast,
            String format,
            int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputScalar(
                label, dataType, data, step, stepFast, format, inputTextFlags);
    }

    public static boolean inputScalarN(
            String label, @NonNull SliderDataType dataType, @NonNull Object data, int components) {
        return IkGuiImplMiscWidgets.inputScalarN(
                label, dataType, data, components, 0.0d, 0.0d, null, InputTextFlags.NONE);
    }

    public static boolean inputScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            double step) {
        return IkGuiImplMiscWidgets.inputScalarN(
                label, dataType, data, components, step, 0.0d, null, InputTextFlags.NONE);
    }

    public static boolean inputScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            double step,
            double stepFast) {
        return IkGuiImplMiscWidgets.inputScalarN(
                label, dataType, data, components, step, stepFast, null, InputTextFlags.NONE);
    }

    public static boolean inputScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            double step,
            double stepFast,
            String format) {
        return IkGuiImplMiscWidgets.inputScalarN(
                label, dataType, data, components, step, stepFast, format, InputTextFlags.NONE);
    }

    public static boolean inputScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            double step,
            double stepFast,
            String format,
            int inputTextFlags) {
        return IkGuiImplMiscWidgets.inputScalarN(
                label, dataType, data, components, step, stepFast, format, inputTextFlags);
    }

    public static boolean inputText(String label, IkString text) {
        return IkGuiImplText.inputText(label, text, InputTextFlags.NONE, null);
    }

    public static boolean inputText(String label, IkString text, int inputTextFlags) {
        return IkGuiImplText.inputText(label, text, inputTextFlags, null);
    }

    public static boolean inputText(
            String label,
            @NonNull IkString text,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return IkGuiImplText.inputText(label, text, inputTextFlags, callback);
    }

    public static boolean inputTextMultiline(String label, @NonNull IkString text) {
        return IkGuiImplText.inputTextMultiline(label, text, 0.0f, 0.0f, InputTextFlags.NONE, null);
    }

    public static boolean inputTextMultiline(
            String label, @NonNull IkString text, float width, float height) {
        return IkGuiImplText.inputTextMultiline(
                label, text, width, height, InputTextFlags.NONE, null);
    }

    public static boolean inputTextMultiline(
            String label, @NonNull IkString text, float width, float height, int inputTextFlags) {
        return IkGuiImplText.inputTextMultiline(label, text, width, height, inputTextFlags, null);
    }

    public static boolean inputTextMultiline(
            String label,
            @NonNull IkString text,
            float width,
            float height,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return IkGuiImplText.inputTextMultiline(
                label, text, width, height, inputTextFlags, callback);
    }

    public static boolean inputTextMultiline(
            String label, @NonNull IkString text, int inputTextFlags) {
        return IkGuiImplText.inputTextMultiline(label, text, 0.0f, 0.0f, inputTextFlags, null);
    }

    public static boolean inputTextMultiline(
            String label,
            @NonNull IkString text,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return IkGuiImplText.inputTextMultiline(label, text, 0.0f, 0.0f, inputTextFlags, callback);
    }

    public static boolean inputTextWithHint(String label, String hint, @NonNull IkString text) {
        return IkGuiImplText.inputTextWithHint(label, hint, text, InputTextFlags.NONE, null);
    }

    public static boolean inputTextWithHint(
            String label, String hint, @NonNull IkString text, int inputTextFlags) {
        return IkGuiImplText.inputTextWithHint(label, hint, text, inputTextFlags, null);
    }

    public static boolean inputTextWithHint(
            String label,
            String hint,
            @NonNull IkString text,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return IkGuiImplText.inputTextWithHint(label, hint, text, inputTextFlags, callback);
    }

    public static boolean invisibleButton(String text, @NonNull Vector2f size) {
        return IkGuiImplMiscWidgets.invisibleButton(text, size.x, size.y, ButtonFlags.NONE);
    }

    public static boolean invisibleButton(String text, float width, float height) {
        return IkGuiImplMiscWidgets.invisibleButton(text, width, height, ButtonFlags.NONE);
    }

    public static boolean invisibleButton(String text, @NonNull Vector2f size, int buttonFlags) {
        return IkGuiImplMiscWidgets.invisibleButton(text, size.x, size.y, buttonFlags);
    }

    public static boolean invisibleButton(String text, float width, float height, int buttonFlags) {
        return IkGuiImplMiscWidgets.invisibleButton(text, width, height, buttonFlags);
    }

    public static boolean isAnyItemActive() {
        return IkGuiImplUtils.isAnyItemActive();
    }

    public static boolean isAnyItemFocused() {
        return IkGuiImplUtils.isAnyItemFocused();
    }

    public static boolean isAnyItemHovered() {
        return IkGuiImplUtils.isAnyItemHovered();
    }

    public static boolean isAnyMouseDown() {
        return IkGuiImplUtils.isAnyMouseDown();
    }

    public static boolean isItemActivated() {
        return IkGuiImplUtils.isItemActivated();
    }

    public static boolean isItemActive() {
        return IkGuiImplUtils.isItemActive();
    }

    public static boolean isItemClicked() {
        return IkGuiImplUtils.isItemClicked(MouseButton.LEFT);
    }

    public static boolean isItemClicked(@NonNull MouseButton button) {
        return IkGuiImplUtils.isItemClicked(button);
    }

    public static boolean isItemDeactivated() {
        return IkGuiImplUtils.isItemDeactivated();
    }

    public static boolean isItemDeactivatedAfterEdit() {
        return IkGuiImplUtils.isItemDeactivatedAfterEdit();
    }

    public static boolean isItemEdited() {
        return IkGuiImplUtils.isItemEdited();
    }

    public static boolean isItemFocused() {
        return IkGuiImplUtils.isItemFocused();
    }

    public static boolean isItemHovered() {
        return IkGuiImplUtils.isItemHovered(HoveredFlags.NONE);
    }

    public static boolean isItemHovered(int hoveredFlags) {
        return IkGuiImplUtils.isItemHovered(hoveredFlags);
    }

    public static boolean isItemToggledOpen() {
        return IkGuiImplUtils.isItemToggledOpen();
    }

    public static boolean isItemVisible() {
        return IkGuiImplUtils.isItemVisible();
    }

    public static boolean isKeyDown(@NonNull Key key) {
        return IkGuiImplUtils.isKeyDown(key);
    }

    public static boolean isKeyPressed(@NonNull Key key) {
        return IkGuiImplUtils.isKeyPressed(key, true);
    }

    public static boolean isKeyPressed(@NonNull Key key, boolean repeat) {
        return IkGuiImplUtils.isKeyPressed(key, repeat);
    }

    public static boolean isKeyReleased(@NonNull Key key) {
        return IkGuiImplUtils.isKeyReleased(key);
    }

    public static boolean isMouseClicked(@NonNull MouseButton button) {
        return IkGuiImplUtils.isMouseClicked(button, false);
    }

    public static boolean isMouseClicked(@NonNull MouseButton button, boolean repeat) {
        return IkGuiImplUtils.isMouseClicked(button, repeat);
    }

    public static boolean isMouseDoubleClicked(@NonNull MouseButton button) {
        return IkGuiImplUtils.isMouseDoubleClicked(button);
    }

    public static boolean isMouseDown(@NonNull MouseButton button) {
        return IkGuiImplUtils.isMouseDown(button);
    }

    public static boolean isMouseDragging(@NonNull MouseButton button) {
        return IkGuiImplUtils.isMouseDragging(button, -1.0f);
    }

    public static boolean isMouseDragging(@NonNull MouseButton button, float lockThreshold) {
        return IkGuiImplUtils.isMouseDragging(button, lockThreshold);
    }

    public static boolean isMouseHoveringRect(@NonNull RectFloat rect) {
        return IkGuiImplUtils.isMouseHoveringRect(
                rect.getLeft(), rect.getTop(), rect.getRight(), rect.getBottom(), true);
    }

    public static boolean isMouseHoveringRect(float minX, float minY, float maxX, float maxY) {
        return IkGuiImplUtils.isMouseHoveringRect(minX, minY, maxX, maxY, true);
    }

    public static boolean isMouseHoveringRect(@NonNull RectFloat rect, boolean clip) {
        return IkGuiImplUtils.isMouseHoveringRect(
                rect.getLeft(), rect.getTop(), rect.getRight(), rect.getBottom(), clip);
    }

    public static boolean isMouseHoveringRect(
            float minX, float minY, float maxX, float maxY, boolean clip) {
        return IkGuiImplUtils.isMouseHoveringRect(minX, minY, maxX, maxY, clip);
    }

    public static boolean isMousePosValid() {
        Vector2f position = context.io.mousePosition;
        return IkGuiImplUtils.isMousePosValid(position.x, position.y);
    }

    public static boolean isMousePosValid(@NonNull Vector2f position) {
        return IkGuiImplUtils.isMousePosValid(position.x, position.y);
    }

    public static boolean isMousePosValid(float x, float y) {
        return IkGuiImplUtils.isMousePosValid(x, y);
    }

    public static boolean isMouseReleased(@NonNull MouseButton button) {
        return context.io.getMouseReleased(button);
    }

    public static boolean isPopupOpen(int id) {
        return IkGuiImplWindows.isPopupOpen(id, PopupFlags.NONE);
    }

    public static boolean isPopupOpen(String name) {
        return IkGuiImplWindows.isPopupOpen(Hash.getID(name), PopupFlags.NONE);
    }

    public static boolean isPopupOpen(int id, int popupFlags) {
        return IkGuiImplWindows.isPopupOpen(id, popupFlags);
    }

    public static boolean isPopupOpen(String name, int popupFlags) {
        return IkGuiImplWindows.isPopupOpen(Hash.getID(name), popupFlags);
    }

    public static boolean isRectVisible(@NonNull Vector2f size) {
        return IkGuiImplUtils.isRectVisible(size.x, size.y);
    }

    public static boolean isRectVisible(float width, float height) {
        return IkGuiImplUtils.isRectVisible(width, height);
    }

    public static boolean isRectVisible(@NonNull RectFloat rect) {
        return IkGuiImplUtils.isRectVisible(
                rect.getLeft(), rect.getTop(), rect.getRight(), rect.getBottom());
    }

    public static boolean isRectVisible(float minX, float minY, float maxX, float maxY) {
        return IkGuiImplUtils.isRectVisible(minX, minY, maxX, maxY);
    }

    public static boolean isWindowAppearing() {
        return IkGuiImplUtils.isWindowAppearing();
    }

    public static boolean isWindowCollapsed() {
        return IkGuiImplUtils.isWindowCollapsed();
    }

    public static boolean isWindowDocked() {
        return IkGuiImplUtils.isWindowDocked();
    }

    public static boolean isWindowFocused() {
        return IkGuiImplUtils.isWindowFocused(FocusedFlags.NONE);
    }

    public static boolean isWindowFocused(int focusedFlags) {
        return IkGuiImplUtils.isWindowFocused(focusedFlags);
    }

    public static boolean isWindowHovered() {
        return IkGuiImplUtils.isWindowHovered(HoveredFlags.NONE);
    }

    public static boolean isWindowHovered(int hoveredFlags) {
        return IkGuiImplUtils.isWindowHovered(hoveredFlags);
    }

    public static void labelText(String label, @NonNull String text) {
        IkGuiImplMiscWidgets.labelText(label, text);
    }

    public static void listBox(String label, @NonNull IkInt currentItem, @NonNull String[] items) {
        IkGuiImplMiscWidgets.listBox(label, currentItem, items, -1);
    }

    public static void listBox(
            String label, @NonNull IkInt currentItem, @NonNull String[] items, int heightInItems) {
        IkGuiImplMiscWidgets.listBox(label, currentItem, items, heightInItems);
    }

    public static void loadIniSettingsFromDisk(@NonNull String filename) {
        IkGuiImplConfig.loadIniSettingsFromDisk(filename);
    }

    public static void loadIniSettingsFromMemory(@NonNull String data) {
        IkGuiImplConfig.loadIniSettingsFromMemory(data);
    }

    public static void logButtons() {
        IkGuiImplLogging.logButtons();
    }

    public static void logFinish() {
        IkGuiImplLogging.logFinish();
    }

    public static void logText(@NonNull String text) {
        IkGuiImplLogging.logText(text);
    }

    public static void logToClipboard() {
        IkGuiImplLogging.logToClipboard(-1);
    }

    public static void logToClipboard(int maxDepth) {
        IkGuiImplLogging.logToClipboard(maxDepth);
    }

    public static void logToFile() {
        IkGuiImplLogging.logToFile(-1, null);
    }

    public static void logToFile(int maxDepth) {
        IkGuiImplLogging.logToFile(maxDepth, null);
    }

    public static void logToFile(int maxDepth, String filename) {
        IkGuiImplLogging.logToFile(maxDepth, filename);
    }

    public static void logToTTY() {
        IkGuiImplLogging.logToTTY(-1);
    }

    public static void logToTTY(int maxDepth) {
        IkGuiImplLogging.logToTTY(maxDepth);
    }

    public static boolean menuItem(@NonNull String label) {
        return IkGuiImplWindows.menuItem(label, null, false, true);
    }

    public static boolean menuItem(@NonNull String label, String shortcut) {
        return IkGuiImplWindows.menuItem(label, shortcut, false, true);
    }

    public static boolean menuItem(@NonNull String label, String shortcut, boolean selected) {
        return IkGuiImplWindows.menuItem(label, shortcut, selected, true);
    }

    public static boolean menuItem(
            @NonNull String label, String shortcut, boolean selected, boolean enabled) {
        return IkGuiImplWindows.menuItem(label, shortcut, selected, enabled);
    }

    public static boolean menuItem(@NonNull String label, String shortcut, IkBoolean selected) {
        return IkGuiImplWindows.menuItem(label, shortcut, selected, true);
    }

    public static boolean menuItem(
            @NonNull String label, String shortcut, IkBoolean selected, boolean enabled) {
        return IkGuiImplWindows.menuItem(label, shortcut, selected, enabled);
    }

    public static void newFrame() {
        IkGuiImplUtils.newFrame();
    }

    public static void newLine() {
        IkGuiImplLayout.newLine();
    }

    /**
     * Set a flag indicating a popup should be open, which should not be called every frame. This
     * internal state is read by {@link #beginPopup(int)} to see if the popup is visible.
     *
     * @param id The ID of the popup.
     */
    public static void openPopup(int id) {
        IkGuiImplWindows.openPopup(id, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Set a flag indicating a popup should be open, which should not be called every frame. This
     * internal state is read by {@link #beginPopup(String)} to see if the popup is visible.
     *
     * @param name The name of the popup.
     */
    public static void openPopup(@NonNull String name) {
        IkGuiImplWindows.openPopup(
                Hash.getID(name, context.idStack.peek()), PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Set a flag indicating a popup should be open, which should not be called every frame. This
     * internal state is read by {@link #beginPopup(int, int)} to see if the popup is visible.
     *
     * @param id The ID of the popup.
     * @param popupFlags Flags for the popup.
     */
    public static void openPopup(int id, int popupFlags) {
        IkGuiImplWindows.openPopup(id, popupFlags);
    }

    /**
     * Set a flag indicating a popup should be open, which should not be called every frame. This
     * internal state is read by {@link #beginPopup(String, int)} to see if the popup is visible.
     *
     * @param name The name of the popup.
     * @param popupFlags Flags for the popup.
     */
    public static void openPopup(@NonNull String name, int popupFlags) {
        IkGuiImplWindows.openPopup(Hash.getID(name, context.idStack.peek()), popupFlags);
    }

    /**
     * Open a popup if the last item was right-clicked, or navigation indicates we should open a
     * popup.
     *
     * @see #openPopup(int)
     */
    public static void openPopupOnItemClick() {
        IkGuiImplWindows.openPopupOnItemClick(
                context.lastItemData.id, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open a popup if the right mouse button was clicked (or navigation action) indicating we
     * should.
     *
     * @param id The item we are interested in. If 0, we'll use the last item ID.
     * @see #openPopup(int)
     */
    public static void openPopupOnItemClick(int id) {
        IkGuiImplWindows.openPopupOnItemClick(id, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open a popup if the right mouse button was clicked (or navigation action) indicating we
     * should.
     *
     * @param name The item we are interested in.
     * @see #openPopup(String)
     */
    public static void openPopupOnItemClick(@NonNull String name) {
        IkGuiImplWindows.openPopupOnItemClick(
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
        IkGuiImplWindows.openPopupOnItemClick(id, popupFlags);
    }

    /**
     * Open a popup if the mouse button (or navigation action) indicates we should.
     *
     * @param name The item we are interested in.
     * @param popupFlags Flags for the popup.
     * @see #openPopup(String, int)
     */
    public static void openPopupOnItemClick(@NonNull String name, int popupFlags) {
        IkGuiImplWindows.openPopupOnItemClick(Hash.getID(name, context.idStack.peek()), popupFlags);
    }

    public static void plotHistogram(
            String label, @NonNull IntFunction<Float> valuesGetter, int count) {
        IkGuiImplMiscWidgets.plotHistogram(
                label, valuesGetter, count, 0, null, Float.MAX_VALUE, Float.MAX_VALUE, 0, 0);
    }

    public static void plotHistogram(String label, float[] values, int count) {
        IkGuiImplMiscWidgets.plotHistogram(
                label, values, count, 0, null, Float.MAX_VALUE, Float.MAX_VALUE, 0, 0);
    }

    public static void plotHistogram(
            String label, @NonNull IntFunction<Float> valuesGetter, int count, int offset) {
        IkGuiImplMiscWidgets.plotHistogram(
                label, valuesGetter, count, offset, null, Float.MAX_VALUE, Float.MAX_VALUE, 0, 0);
    }

    public static void plotHistogram(String label, float[] values, int count, int offset) {
        IkGuiImplMiscWidgets.plotHistogram(
                label, values, count, offset, null, Float.MAX_VALUE, Float.MAX_VALUE, 0, 0);
    }

    public static void plotHistogram(
            String label,
            @NonNull IntFunction<Float> valuesGetter,
            int count,
            int offset,
            String overlay) {
        IkGuiImplMiscWidgets.plotHistogram(
                label,
                valuesGetter,
                count,
                offset,
                overlay,
                Float.MAX_VALUE,
                Float.MAX_VALUE,
                0,
                0);
    }

    public static void plotHistogram(
            String label, float[] values, int count, int offset, String overlay) {
        IkGuiImplMiscWidgets.plotHistogram(
                label, values, count, offset, overlay, Float.MAX_VALUE, Float.MAX_VALUE, 0, 0);
    }

    public static void plotHistogram(
            String label,
            @NonNull IntFunction<Float> valuesGetter,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax) {
        IkGuiImplMiscWidgets.plotHistogram(
                label, valuesGetter, count, offset, overlay, scaleMin, scaleMax, 0, 0);
    }

    public static void plotHistogram(
            String label,
            float[] values,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax) {
        IkGuiImplMiscWidgets.plotHistogram(
                label, values, count, offset, overlay, scaleMin, scaleMax, 0, 0);
    }

    public static void plotHistogram(
            String label,
            @NonNull IntFunction<Float> valuesGetter,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax,
            float width,
            float height) {
        IkGuiImplMiscWidgets.plotHistogram(
                label, valuesGetter, count, offset, overlay, scaleMin, scaleMax, width, height);
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
        IkGuiImplMiscWidgets.plotHistogram(
                label, values, count, offset, overlay, scaleMin, scaleMax, width, height);
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
        IkGuiImplUtils.render();
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

    public static void setWindowPos(@NonNull Window window, @NonNull Vector2f position) {
        setWindowPos(window, position.x, position.y, Condition.ALWAYS);
    }

    public static void setWindowPos(
            @NonNull Window window, @NonNull Vector2f position, @NonNull Condition condition) {
        setWindowPos(window, position.x, position.y, condition);
    }

    public static void setWindowPos(@NonNull Window window, float x, float y) {
        setWindowPos(window, x, y, Condition.ALWAYS);
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

    public static void setWindowPos(@NonNull Vector2f position) {
        setWindowPos(context.windowCurrent, position.x, position.y, Condition.ALWAYS);
    }

    public static void setWindowPos(@NonNull Vector2f position, @NonNull Condition condition) {
        setWindowPos(context.windowCurrent, position.x, position.y, condition);
    }

    public static void setWindowPos(float x, float y) {
        setWindowPos(context.windowCurrent, x, y, Condition.ALWAYS);
    }

    public static void setWindowPos(float x, float y, @NonNull Condition condition) {
        setWindowPos(context.windowCurrent, x, y, condition);
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

    /** Private constructor so this is not instantiated. */
    private IkGui() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
