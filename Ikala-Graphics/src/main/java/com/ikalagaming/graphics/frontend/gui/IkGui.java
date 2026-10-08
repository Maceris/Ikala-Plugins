package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.TextureInfo;
import com.ikalagaming.graphics.frontend.gui.callback.GuiInputTextCallback;
import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.*;
import com.ikalagaming.graphics.frontend.gui.flags.*;
import com.ikalagaming.graphics.frontend.gui.util.Color;
import com.ikalagaming.graphics.frontend.gui.util.KeyChord;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Immediate mode GUI library, a customized Java fork of <a
 * href="https://github.com/ocornut/imgui">ImGui</a>.
 */
@Slf4j
public class IkGui {

    /**
     * Standard drag and drop payload type for colors without alpha, as a float[3]. User code may
     * use this type.
     */
    public static final String PAYLOAD_TYPE_COLOR_3F = IkGuiImplColor.PAYLOAD_TYPE_COLOR_3F;

    /**
     * Standard drag and drop payload type for colors, as a float[4]. User code may use this type.
     */
    public static final String PAYLOAD_TYPE_COLOR_4F = IkGuiImplColor.PAYLOAD_TYPE_COLOR_4F;

    @Getter private static Context context;

    /**
     * Accept a payload whose type is the fully qualified name of the class, as set by
     * setDragDropPayload(Object). Call between beginDragDropTarget() and endDragDropTarget().
     *
     * @param aClass The class of the payload.
     * @param <T> The type of payload data.
     * @return The payload data when it is delivered, otherwise null.
     */
    public static <T> T acceptDragDropPayload(@NonNull Class<T> aClass) {
        return IkGuiImplDragDrop.acceptDragDropPayload(aClass, DragDropFlags.NONE);
    }

    /**
     * Accept a payload whose type is the fully qualified name of the class, as set by
     * setDragDropPayload(Object). Call between beginDragDropTarget() and endDragDropTarget(). If
     * {@link DragDropFlags#ACCEPT_BEFORE_DELIVERY} is set you can peek into the payload before the
     * mouse button is released, and check getDragDropPayloadInfo().isDelivery().
     *
     * @param aClass The class of the payload.
     * @param dragDropFlags Flags for accepting the payload.
     * @param <T> The type of payload data.
     * @return The payload data when it is delivered (or previewed, depending on flags), otherwise
     *     null.
     * @see DragDropFlags
     */
    public static <T> T acceptDragDropPayload(@NonNull Class<T> aClass, int dragDropFlags) {
        return IkGuiImplDragDrop.acceptDragDropPayload(aClass, dragDropFlags);
    }

    /**
     * Accept contents of a given type. Call between beginDragDropTarget() and endDragDropTarget().
     *
     * @param dataType The payload type, or null to accept any type.
     * @param <T> The type of payload data.
     * @return The payload data when it is delivered, otherwise null.
     */
    public static <T> T acceptDragDropPayload(String dataType) {
        return IkGuiImplDragDrop.acceptDragDropPayload(dataType, DragDropFlags.NONE);
    }

    /**
     * Accept contents of a given type. Call between beginDragDropTarget() and endDragDropTarget().
     * If {@link DragDropFlags#ACCEPT_BEFORE_DELIVERY} is set you can peek into the payload before
     * the mouse button is released, and check getDragDropPayloadInfo().isDelivery().
     *
     * @param dataType The payload type, or null to accept any type.
     * @param dragDropFlags Flags for accepting the payload.
     * @param <T> The type of payload data.
     * @return The payload data when it is delivered (or previewed, depending on flags), otherwise
     *     null.
     * @see DragDropFlags
     */
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
     * within a host window. Child windows can embed their own child windows. Always call {@link
     * #endChild()} even if false is returned.
     *
     * <p>For each independent axis of the size:
     *
     * <ul>
     *   <li>0: Use remaining host window size
     *   <li>Greater than 0: Fixed size
     *   <li>Less than 0: Use remaining window size minus abs(size)
     * </ul>
     *
     * @param name The name of the child, which is used for the ID.
     * @return False if the child is collapsed or clipped, so items can be skipped.
     */
    public static boolean beginChild(@NonNull String name) {
        return IkGuiImplWindows.beginChild(
                name, IkGui.getID(name), 0, 0, ChildFlags.NONE, WindowFlags.NONE);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window. Child windows can embed their own child windows. Always call {@link
     * #endChild()} even if false is returned.
     *
     * <p>For each independent axis of the size:
     *
     * <ul>
     *   <li>0: Use remaining host window size
     *   <li>Greater than 0: Fixed size
     *   <li>Less than 0: Use remaining window size minus abs(size)
     * </ul>
     *
     * @param name The name of the child, which is used for the ID.
     * @param size The size in pixels.
     * @return False if the child is collapsed or clipped, so items can be skipped.
     */
    public static boolean beginChild(@NonNull String name, @NonNull Vector2f size) {
        return IkGuiImplWindows.beginChild(
                name, IkGui.getID(name), size.x, size.y, ChildFlags.NONE, WindowFlags.NONE);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window. Child windows can embed their own child windows. Always call {@link
     * #endChild()} even if false is returned.
     *
     * <p>For each independent axis of the size:
     *
     * <ul>
     *   <li>0: Use remaining host window size
     *   <li>Greater than 0: Fixed size
     *   <li>Less than 0: Use remaining window size minus abs(size)
     * </ul>
     *
     * @param name The name of the child, which is used for the ID.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @return False if the child is collapsed or clipped, so items can be skipped.
     */
    public static boolean beginChild(@NonNull String name, float width, float height) {
        return IkGuiImplWindows.beginChild(
                name, IkGui.getID(name), width, height, ChildFlags.NONE, WindowFlags.NONE);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window. Child windows can embed their own child windows. Always call {@link
     * #endChild()} even if false is returned.
     *
     * <p>For each independent axis of the size:
     *
     * <ul>
     *   <li>0: Use remaining host window size
     *   <li>Greater than 0: Fixed size
     *   <li>Less than 0: Use remaining window size minus abs(size)
     * </ul>
     *
     * @param name The name of the child, which is used for the ID.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param childFlags Child flags.
     * @return False if the child is collapsed or clipped, so items can be skipped.
     * @see ChildFlags
     */
    public static boolean beginChild(
            @NonNull String name, float width, float height, int childFlags) {
        return IkGuiImplWindows.beginChild(
                name, IkGui.getID(name), width, height, childFlags, WindowFlags.NONE);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window. Child windows can embed their own child windows. Always call {@link
     * #endChild()} even if false is returned.
     *
     * <p>For each independent axis of the size:
     *
     * <ul>
     *   <li>0: Use remaining host window size
     *   <li>Greater than 0: Fixed size
     *   <li>Less than 0: Use remaining window size minus abs(size)
     * </ul>
     *
     * @param name The name of the child, which is used for the ID.
     * @param size The size in pixels.
     * @param childFlags Child flags.
     * @param windowFlags Window flags.
     * @return False if the child is collapsed or clipped, so items can be skipped.
     * @see ChildFlags
     * @see WindowFlags
     */
    public static boolean beginChild(
            @NonNull String name, @NonNull Vector2f size, int childFlags, int windowFlags) {
        return IkGuiImplWindows.beginChild(
                name, IkGui.getID(name), size.x, size.y, childFlags, windowFlags);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window. Child windows can embed their own child windows. Always call {@link
     * #endChild()} even if false is returned.
     *
     * <p>For each independent axis of the size:
     *
     * <ul>
     *   <li>0: Use remaining host window size
     *   <li>Greater than 0: Fixed size
     *   <li>Less than 0: Use remaining window size minus abs(size)
     * </ul>
     *
     * @param name The name of the child, which is used for the ID.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param childFlags Child flags.
     * @param windowFlags Window flags.
     * @return False if the child is collapsed or clipped, so items can be skipped.
     * @see ChildFlags
     * @see WindowFlags
     */
    public static boolean beginChild(
            @NonNull String name, float width, float height, int childFlags, int windowFlags) {
        return IkGuiImplWindows.beginChild(
                name, IkGui.getID(name), width, height, childFlags, windowFlags);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window. Child windows can embed their own child windows. Always call {@link
     * #endChild()} even if false is returned.
     *
     * <p>For each independent axis of the size:
     *
     * <ul>
     *   <li>0: Use remaining host window size
     *   <li>Greater than 0: Fixed size
     *   <li>Less than 0: Use remaining window size minus abs(size)
     * </ul>
     *
     * @param id The ID of the child.
     * @return False if the child is collapsed or clipped, so items can be skipped.
     */
    public static boolean beginChild(int id) {
        return IkGuiImplWindows.beginChild(null, id, 0, 0, ChildFlags.NONE, WindowFlags.NONE);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window. Child windows can embed their own child windows. Always call {@link
     * #endChild()} even if false is returned.
     *
     * <p>For each independent axis of the size:
     *
     * <ul>
     *   <li>0: Use remaining host window size
     *   <li>Greater than 0: Fixed size
     *   <li>Less than 0: Use remaining window size minus abs(size)
     * </ul>
     *
     * @param id The ID of the child.
     * @param size The size in pixels.
     * @return False if the child is collapsed or clipped, so items can be skipped.
     */
    public static boolean beginChild(int id, @NonNull Vector2f size) {
        return IkGuiImplWindows.beginChild(
                null, id, size.x, size.y, ChildFlags.NONE, WindowFlags.NONE);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window. Child windows can embed their own child windows. Always call {@link
     * #endChild()} even if false is returned.
     *
     * <p>For each independent axis of the size:
     *
     * <ul>
     *   <li>0: Use remaining host window size
     *   <li>Greater than 0: Fixed size
     *   <li>Less than 0: Use remaining window size minus abs(size)
     * </ul>
     *
     * @param id The ID of the child.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @return False if the child is collapsed or clipped, so items can be skipped.
     */
    public static boolean beginChild(int id, float width, float height) {
        return IkGuiImplWindows.beginChild(
                null, id, width, height, ChildFlags.NONE, WindowFlags.NONE);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window. Child windows can embed their own child windows. Always call {@link
     * #endChild()} even if false is returned.
     *
     * <p>For each independent axis of the size:
     *
     * <ul>
     *   <li>0: Use remaining host window size
     *   <li>Greater than 0: Fixed size
     *   <li>Less than 0: Use remaining window size minus abs(size)
     * </ul>
     *
     * @param id The ID of the child.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param childFlags Child flags.
     * @return False if the child is collapsed or clipped, so items can be skipped.
     * @see ChildFlags
     */
    public static boolean beginChild(int id, float width, float height, int childFlags) {
        return IkGuiImplWindows.beginChild(null, id, width, height, childFlags, WindowFlags.NONE);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window. Child windows can embed their own child windows. Always call {@link
     * #endChild()} even if false is returned.
     *
     * <p>For each independent axis of the size:
     *
     * <ul>
     *   <li>0: Use remaining host window size
     *   <li>Greater than 0: Fixed size
     *   <li>Less than 0: Use remaining window size minus abs(size)
     * </ul>
     *
     * @param id The ID of the child.
     * @param size The size in pixels.
     * @param childFlags Child flags.
     * @param windowFlags Window flags.
     * @return False if the child is collapsed or clipped, so items can be skipped.
     * @see ChildFlags
     * @see WindowFlags
     */
    public static boolean beginChild(
            int id, @NonNull Vector2f size, int childFlags, int windowFlags) {
        return IkGuiImplWindows.beginChild(null, id, size.x, size.y, childFlags, windowFlags);
    }

    /**
     * Creates a child window, which is a self-contained independent scrolling/clipping region
     * within a host window. Child windows can embed their own child windows. Always call {@link
     * #endChild()} even if false is returned.
     *
     * <p>For each independent axis of the size:
     *
     * <ul>
     *   <li>0: Use remaining host window size
     *   <li>Greater than 0: Fixed size
     *   <li>Less than 0: Use remaining window size minus abs(size)
     * </ul>
     *
     * @param id The ID of the child.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param childFlags Child flags.
     * @param windowFlags Window flags.
     * @return False if the child is collapsed or clipped, so items can be skipped.
     * @see ChildFlags
     * @see WindowFlags
     */
    public static boolean beginChild(
            int id, float width, float height, int childFlags, int windowFlags) {
        return IkGuiImplWindows.beginChild(null, id, width, height, childFlags, windowFlags);
    }

    public static boolean beginCombo(String label, String previewValue) {
        return IkGuiImplCombo.beginCombo(label, previewValue, ComboFlags.NONE);
    }

    public static boolean beginCombo(String label, String previewValue, int comboFlags) {
        return IkGuiImplCombo.beginCombo(label, previewValue, comboFlags);
    }

    public static void beginDisabled() {
        IkGuiImplUtils.beginDisabled(true);
    }

    public static void beginDisabled(boolean disabled) {
        IkGuiImplUtils.beginDisabled(disabled);
    }

    /**
     * Call after submitting an item which may be dragged. When this returns true, call
     * setDragDropPayload(), optionally submit a preview of the payload (it is in a tooltip), then
     * call endDragDropSource().
     *
     * @return True if the item is being dragged.
     */
    public static boolean beginDragDropSource() {
        return IkGuiImplDragDrop.beginDragDropSource(DragDropFlags.NONE);
    }

    /**
     * Call after submitting an item which may be dragged. When this returns true, call
     * setDragDropPayload(), optionally submit a preview of the payload, then call
     * endDragDropSource().
     *
     * @param dragDropFlags Flags for the source.
     * @return True if the item is being dragged.
     * @see DragDropFlags
     */
    public static boolean beginDragDropSource(int dragDropFlags) {
        return IkGuiImplDragDrop.beginDragDropSource(dragDropFlags);
    }

    /**
     * Call after submitting an item that may receive a payload. If this returns true, call
     * acceptDragDropPayload() and then endDragDropTarget().
     *
     * @return True if a payload is being dragged over the item.
     */
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

    /**
     * Create and append to a full screen menu bar.
     *
     * @return True if the menu bar is visible, and endMainMenuBar() needs to be called.
     */
    public static boolean beginMainMenuBar() {
        return IkGuiImplMenus.beginMainMenuBar();
    }

    /**
     * Create a sub-menu entry.
     *
     * @param label The label, which is also used for the ID.
     * @return True if the menu is open, and endMenu() needs to be called.
     */
    public static boolean beginMenu(@NonNull String label) {
        return IkGuiImplMenus.beginMenu(label, true);
    }

    /**
     * Create a sub-menu entry.
     *
     * @param label The label, which is also used for the ID.
     * @param enabled Whether the menu is enabled.
     * @return True if the menu is open, and endMenu() needs to be called.
     */
    public static boolean beginMenu(@NonNull String label, boolean enabled) {
        return IkGuiImplMenus.beginMenu(label, enabled);
    }

    /**
     * Append to the menu bar of the current window, which requires {@link WindowFlags#MENU_BAR} to
     * be set on the window.
     *
     * @return True if the menu bar is visible, and endMenuBar() needs to be called.
     */
    public static boolean beginMenuBar() {
        return IkGuiImplMenus.beginMenuBar();
    }

    /**
     * Begin a popup, if it is open.
     *
     * @param id The ID of the popup.
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    public static boolean beginPopup(int id) {
        return IkGuiImplPopups.beginPopup(id, WindowFlags.NONE);
    }

    /**
     * Begin a popup, if it is open.
     *
     * @param stringID The string ID of the popup, relative to the current ID stack.
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    public static boolean beginPopup(@NonNull String stringID) {
        return IkGuiImplPopups.beginPopup(stringID, WindowFlags.NONE);
    }

    /**
     * Begin a popup, if it is open.
     *
     * @param id The ID of the popup.
     * @param windowFlags Window flags.
     * @return True if the popup is open, and endPopup() needs to be called.
     * @see WindowFlags
     */
    public static boolean beginPopup(int id, int windowFlags) {
        return IkGuiImplPopups.beginPopup(id, windowFlags);
    }

    /**
     * Begin a popup, if it is open.
     *
     * @param stringID The string ID of the popup, relative to the current ID stack.
     * @param windowFlags Window flags.
     * @return True if the popup is open, and endPopup() needs to be called.
     * @see WindowFlags
     */
    public static boolean beginPopup(@NonNull String stringID, int windowFlags) {
        return IkGuiImplPopups.beginPopup(stringID, windowFlags);
    }

    /**
     * Open and begin a popup when the last item is right-clicked. The popup is associated with the
     * last item, so it needs to have an ID. For items without one (e.g. text()), pass in an
     * explicit ID.
     *
     * @return True if the popup is open, and endPopup() needs to be called.
     * @see #beginPopupContextItem(String, int)
     */
    public static boolean beginPopupContextItem() {
        return IkGuiImplPopups.beginPopupContextItem(
                (String) null, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open and begin a popup when the last item is right-clicked.
     *
     * @param id The ID of the popup.
     * @return True if the popup is open, and endPopup() needs to be called.
     * @see #beginPopupContextItem(String, int)
     */
    public static boolean beginPopupContextItem(int id) {
        return IkGuiImplPopups.beginPopupContextItem(id, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open and begin a popup when the last item is right-clicked.
     *
     * @param stringID The string ID of the popup, or null to associate it with the last item.
     * @return True if the popup is open, and endPopup() needs to be called.
     * @see #beginPopupContextItem(String, int)
     */
    public static boolean beginPopupContextItem(String stringID) {
        return IkGuiImplPopups.beginPopupContextItem(stringID, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open and begin a popup when the last item is clicked.
     *
     * @param id The ID of the popup.
     * @param popupFlags Popup flags, including which mouse button opens it.
     * @return True if the popup is open, and endPopup() needs to be called.
     * @see PopupFlags
     * @see #beginPopupContextItem(String, int)
     */
    public static boolean beginPopupContextItem(int id, int popupFlags) {
        return IkGuiImplPopups.beginPopupContextItem(id, popupFlags);
    }

    /**
     * Open and begin a popup when the last item is clicked.
     *
     * <p>This is essentially the same as {@code openPopupOnItemClick(stringID, popupFlags); return
     * beginPopup(id);}, which is essentially the same as {@code if (isItemHovered() &&
     * isMouseReleased(MouseButton.RIGHT)) {openPopup(id);} return beginPopup(id);}.
     *
     * @param stringID The string ID of the popup, or null to associate it with the last item.
     * @param popupFlags Popup flags, including which mouse button opens it.
     * @return True if the popup is open, and endPopup() needs to be called.
     * @see PopupFlags
     */
    public static boolean beginPopupContextItem(String stringID, int popupFlags) {
        return IkGuiImplPopups.beginPopupContextItem(stringID, popupFlags);
    }

    /**
     * Open and begin a popup when right-clicking in the void (where there are no windows).
     *
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    public static boolean beginPopupContextVoid() {
        return IkGuiImplPopups.beginPopupContextVoid(null, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open and begin a popup when right-clicking in the void (where there are no windows).
     *
     * @param stringID The string ID of the popup, or null to use a default.
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    public static boolean beginPopupContextVoid(String stringID) {
        return IkGuiImplPopups.beginPopupContextVoid(stringID, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open and begin a popup when clicking in the void (where there are no windows).
     *
     * @param stringID The string ID of the popup, or null to use a default.
     * @param popupFlags Popup flags, including which mouse button opens it.
     * @return True if the popup is open, and endPopup() needs to be called.
     * @see PopupFlags
     */
    public static boolean beginPopupContextVoid(String stringID, int popupFlags) {
        return IkGuiImplPopups.beginPopupContextVoid(stringID, popupFlags);
    }

    /**
     * Open and begin a popup when right-clicking on the current window.
     *
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    public static boolean beginPopupContextWindow() {
        return IkGuiImplPopups.beginPopupContextWindow(null, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open and begin a popup when right-clicking on the current window.
     *
     * @param stringID The string ID of the popup, or null to use a default.
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    public static boolean beginPopupContextWindow(String stringID) {
        return IkGuiImplPopups.beginPopupContextWindow(stringID, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open and begin a popup when clicking on the current window.
     *
     * @param stringID The string ID of the popup, or null to use a default.
     * @param popupFlags Popup flags, including which mouse button opens it.
     * @return True if the popup is open, and endPopup() needs to be called.
     * @see PopupFlags
     */
    public static boolean beginPopupContextWindow(String stringID, int popupFlags) {
        return IkGuiImplPopups.beginPopupContextWindow(stringID, popupFlags);
    }

    /**
     * Begin a modal popup, if it is open. Modals block interaction with windows behind them.
     *
     * @param name The name of the modal, which is also used for the ID.
     * @return True if the modal is open, and endPopup() needs to be called.
     */
    public static boolean beginPopupModal(@NonNull String name) {
        return IkGuiImplPopups.beginPopupModal(name, null, WindowFlags.NONE);
    }

    /**
     * Begin a modal popup, if it is open. Modals block interaction with windows behind them.
     *
     * @param name The name of the modal, which is also used for the ID.
     * @param open If not null, a close button is shown. This is set to false when the popup is not
     *     open, and setting it to false closes the popup.
     * @return True if the modal is open, and endPopup() needs to be called.
     */
    public static boolean beginPopupModal(@NonNull String name, IkBoolean open) {
        return IkGuiImplPopups.beginPopupModal(name, open, WindowFlags.NONE);
    }

    /**
     * Begin a modal popup, if it is open. Modals block interaction with windows behind them.
     *
     * @param name The name of the modal, which is also used for the ID.
     * @param open If not null, a close button is shown. This is set to false when the popup is not
     *     open, and setting it to false closes the popup.
     * @param windowFlags Window flags.
     * @return True if the modal is open, and endPopup() needs to be called.
     * @see WindowFlags
     */
    public static boolean beginPopupModal(@NonNull String name, IkBoolean open, int windowFlags) {
        return IkGuiImplPopups.beginPopupModal(name, open, windowFlags);
    }

    /**
     * Begin a modal popup, if it is open. Modals block interaction with windows behind them.
     *
     * @param name The name of the modal, which is also used for the ID.
     * @param windowFlags Window flags.
     * @return True if the modal is open, and endPopup() needs to be called.
     * @see WindowFlags
     */
    public static boolean beginPopupModal(@NonNull String name, int windowFlags) {
        return IkGuiImplPopups.beginPopupModal(name, null, windowFlags);
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

    /**
     * Begin/append a tooltip window if the preceding item was hovered.
     *
     * @return True if the tooltip was started, and endTooltip() needs to be called.
     */
    public static boolean beginItemTooltip() {
        return IkGuiImplPopups.beginItemTooltip();
    }

    /**
     * Begin/append a tooltip window.
     *
     * @return True if the tooltip was started, and endTooltip() needs to be called.
     */
    public static boolean beginTooltip() {
        return IkGuiImplPopups.beginTooltip();
    }

    public static void bullet() {
        IkGuiImplText.bullet();
    }

    public static void bulletText(String text) {
        IkGuiImplText.bulletText(text);
    }

    public static boolean button(String text) {
        return IkGuiImplButtons.button(text, 0, 0);
    }

    public static boolean button(String text, float width, float height) {
        return IkGuiImplButtons.button(text, width, height);
    }

    /**
     * The width of an item given the pushed settings and the current cursor position. This is not
     * necessarily the width of the last item, unlike most item functions.
     *
     * @return The width of the next item.
     */
    public static float calcItemWidth() {
        return IkGuiImplUtils.calculateItemWidth();
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

    /**
     * A checkbox that doesn't store its own state.
     *
     * @param label The label, which is also used for the ID.
     * @param checked Whether the checkbox is currently checked.
     * @return True if the checkbox was clicked, so the caller should toggle their state.
     */
    public static boolean checkbox(String label, boolean checked) {
        return IkGuiImplMiscWidgets.checkbox(label, checked);
    }

    public static boolean checkbox(String label, IkBoolean active) {
        return IkGuiImplMiscWidgets.checkbox(label, active);
    }

    public static boolean checkboxFlags(String label, IkInt flags, int flagsValue) {
        return IkGuiImplMiscWidgets.checkboxFlags(label, flags, flagsValue);
    }

    /** Manually close the popup we have begun into. */
    public static void closeCurrentPopup() {
        IkGuiImplPopups.closeCurrentPopup();
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
        return IkGuiImplColor.colorButton(label, color, ColorEditFlags.NONE, 0, 0);
    }

    public static boolean colorButton(String label, float[] color, int colorEditFlags) {
        return IkGuiImplColor.colorButton(label, color, colorEditFlags, 0, 0);
    }

    public static boolean colorButton(
            String label, float[] color, int colorEditFlags, float width, float height) {
        return IkGuiImplColor.colorButton(label, color, colorEditFlags, width, height);
    }

    /**
     * Convert a color to the packed RGBA format, rounding each component and clamping it to the
     * range 0-1.
     *
     * @param r The red component.
     * @param g The green component.
     * @param b The blue component.
     * @param a The alpha component.
     * @return The packed color.
     */
    public static int colorConvertFloat4ToU32(float r, float g, float b, float a) {
        return IkGuiImplColor.float4ToRGBA(r, g, b, a);
    }

    /**
     * Convert a color from HSV to RGB, with each component in the range 0-1. Only the first 3
     * elements of each array are used, so RGBA/HSVA arrays keep their alpha. The arrays may be the
     * same array to convert in place.
     *
     * @param in The HSV color, with at least 3 elements.
     * @param out Where to store the RGB color, with at least 3 elements.
     */
    public static void colorConvertHSVtoRGB(float[] in, float[] out) {
        IkGuiImplUtils.colorConvertHSVtoRGB(in, out);
    }

    /**
     * Convert a color from RGB to HSV, with each component in the range 0-1. Only the first 3
     * elements of each array are used, so RGBA/HSVA arrays keep their alpha. The arrays may be the
     * same array to convert in place.
     *
     * @param in The RGB color, with at least 3 elements.
     * @param out Where to store the HSV color, with at least 3 elements.
     */
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
        return IkGuiImplColor.colorEdit3(label, value, ColorEditFlags.NONE);
    }

    public static boolean colorEdit3(String label, float[] value, int colorEditFlags) {
        return IkGuiImplColor.colorEdit3(label, value, colorEditFlags);
    }

    public static boolean colorEdit4(String label, float[] value) {
        return IkGuiImplColor.colorEdit4(label, value, ColorEditFlags.NONE);
    }

    public static boolean colorEdit4(String label, float[] value, int colorEditFlags) {
        return IkGuiImplColor.colorEdit4(label, value, colorEditFlags);
    }

    public static boolean colorPicker3(String label, float[] value) {
        return IkGuiImplColor.colorPicker3(label, value, ColorEditFlags.NONE);
    }

    public static boolean colorPicker3(String label, float[] value, int colorEditFlags) {
        return IkGuiImplColor.colorPicker3(label, value, colorEditFlags);
    }

    public static boolean colorPicker4(String label, float[] value) {
        return IkGuiImplColor.colorPicker4(label, value, ColorEditFlags.NONE, null);
    }

    public static boolean colorPicker4(String label, float[] value, int colorEditFlags) {
        return IkGuiImplColor.colorPicker4(label, value, colorEditFlags, null);
    }

    public static boolean colorPicker4(
            String label, float[] value, int colorEditFlags, float[] referenceColor) {
        return IkGuiImplColor.colorPicker4(label, value, colorEditFlags, referenceColor);
    }

    public static boolean combo(String label, IkInt currentItem, String[] items) {
        return IkGuiImplCombo.combo(label, currentItem, items, -1);
    }

    public static boolean combo(
            String label, IkInt currentItem, String[] items, int popupMaxHeightInItems) {
        return IkGuiImplCombo.combo(label, currentItem, items, popupMaxHeightInItems);
    }

    /**
     * A simple combo box, where the item names are fetched as needed.
     *
     * @param label The label, which is also used for the ID.
     * @param currentItem The index of the selected item, updated when the selection changes.
     * @param getter Fetches the name of the item at an index.
     * @param itemsCount The number of items to choose from.
     * @return True if the selection changed.
     */
    public static boolean combo(
            String label, IkInt currentItem, IntFunction<String> getter, int itemsCount) {
        return IkGuiImplCombo.combo(label, currentItem, getter, itemsCount, -1);
    }

    /**
     * A simple combo box, where the item names are fetched as needed.
     *
     * @param label The label, which is also used for the ID.
     * @param currentItem The index of the selected item, updated when the selection changes.
     * @param getter Fetches the name of the item at an index.
     * @param itemsCount The number of items to choose from.
     * @param popupMaxHeightInItems The maximum height of the popup in items, -1 for the default.
     * @return True if the selection changed.
     */
    public static boolean combo(
            String label,
            IkInt currentItem,
            IntFunction<String> getter,
            int itemsCount,
            int popupMaxHeightInItems) {
        return IkGuiImplCombo.combo(label, currentItem, getter, itemsCount, popupMaxHeightInItems);
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
        IkGuiImplCombo.context = context;
        IkGuiImplConfig.context = context;
        IkGuiImplDragDrop.context = context;
        IkGuiImplInputText.context = context;
        IkGuiImplKeys.context = context;
        IkGuiImplMouseCursor.context = context;
        IkGuiImplNav.context = context;
        IkGuiImplLayout.context = context;
        IkGuiImplListClipper.context = context;
        IkGuiImplLogging.context = context;
        IkGuiImplMenus.context = context;
        IkGuiImplColor.context = context;
        IkGuiImplMiscWidgets.context = context;
        IkGuiImplMultiSelect.context = context;
        IkGuiImplSliders.context = context;
        IkGuiImplPopups.context = context;
        IkGuiImplTableHeaders.context = context;
        IkGuiImplTableSettings.context = context;
        IkGuiImplTables.context = context;
        IkGuiImplTabs.context = context;
        IkGuiImplText.context = context;
        IkGuiImplTrees.context = context;
        IkGuiImplTypingSelect.context = context;
        IkGuiImplUtils.context = context;
        IkGuiImplWindows.context = context;
        IkGuiImplDocking.context = context;
        IkGuiImplDockBuilder.context = context;
        IkGuiImplDockSettings.context = context;
        IkGuiImplViewports.context = context;
        IkGuiImplMetrics.context = context;
        IkGuiImplDebugTools.context = context;
        IkGuiInternal.context = context;

        // Add .ini handlers
        IkGuiImplConfig.addWindowSettingsHandler();
        IkGuiImplTableSettings.tableSettingsAddSettingsHandler();
        IkGuiImplDocking.dockContextInitialize();
        context.initialized = true;

        return context;
    }

    /**
     * Add text to the debug log, which is displayed by the debug log window.
     *
     * @param text The text to log.
     * @see #showDebugLogWindow(IkBoolean)
     */
    public static void debugLog(@NonNull String text) {
        IkGuiImplDebugTools.debugLog(text);
    }

    /**
     * Flash a style color for a short time, to help find where it is used. While flashing, pushing
     * the style color has no effect.
     *
     * @param type The style color to flash.
     */
    public static void debugFlashStyleColor(@NonNull ColorType type) {
        IkGuiImplMetrics.debugFlashStyleColor(type);
    }

    /**
     * Start the item picker, to visually select an item with the mouse and call debugBreak() when
     * it is next submitted. Put a breakpoint in IkGuiInternal.debugBreak() to see the call stack.
     */
    public static void debugStartItemPicker() {
        IkGuiImplDebugTools.debugStartItemPicker();
    }

    /**
     * Display the code points and UTF-8 bytes of a string, to diagnose text encoding issues versus
     * font loading issues.
     *
     * @param text The text to inspect.
     */
    public static void debugTextEncoding(@NonNull String text) {
        IkGuiImplMetrics.debugTextEncoding(text);
    }

    /**
     * Call the destroy window callbacks for every viewport (including the main viewport), to give
     * the backend a chance to clear any data they may have stored. Call this when shutting down the
     * backend, before destroying the context.
     */
    public static void destroyPlatformWindows() {
        IkGuiImplViewports.destroyPlatformWindows();
    }

    public static void destroyContext() {
        if (context == null) {
            return;
        }

        if (context.frameCountEnded != context.frameCount) {
            IkGui.endFrame();
        }

        // Save settings, unless we haven't attempted to load them. Creating and destroying a
        // context without a call to newFrame() shouldn't save an empty file.
        if (context.settingsLoaded && context.io.iniFilename != null) {
            IkGuiImplConfig.saveIniSettingsToDisk(context.io.iniFilename);
        }
        IkGuiImplLogging.logShutdown();
        IkGuiImplDocking.dockContextShutdown();
        for (Viewport viewport : context.viewports) {
            if (viewport.rendererUserData != null
                    || viewport.platformUserData != null
                    || viewport.platformHandle != null) {
                IkGuiImplDebugTools.reportError(
                        log, "The backend or app forgot to call destroyPlatformWindows()");
                break;
            }
        }
        context.io.setAppAcceptingEvents(false);
        // The context always owns its font atlas, so it's destroyed along with it
        context.io.fonts.destroy();
        context = null;
        IkGuiImplButtons.context = null;
        IkGuiImplCombo.context = null;
        IkGuiImplConfig.context = null;
        IkGuiImplDragDrop.context = null;
        IkGuiImplInputText.context = null;
        IkGuiImplKeys.context = null;
        IkGuiImplMouseCursor.context = null;
        IkGuiImplNav.context = null;
        IkGuiImplLayout.context = null;
        IkGuiImplListClipper.context = null;
        IkGuiImplLogging.context = null;
        IkGuiImplMenus.context = null;
        IkGuiImplColor.context = null;
        IkGuiImplMiscWidgets.context = null;
        IkGuiImplMultiSelect.context = null;
        IkGuiImplSliders.context = null;
        IkGuiImplPopups.context = null;
        IkGuiImplTableHeaders.context = null;
        IkGuiImplTableSettings.context = null;
        IkGuiImplTables.context = null;
        IkGuiImplTabs.context = null;
        IkGuiImplText.context = null;
        IkGuiImplTrees.context = null;
        IkGuiImplTypingSelect.context = null;
        IkGuiImplUtils.context = null;
        IkGuiImplWindows.context = null;
        IkGuiImplDocking.context = null;
        IkGuiImplDockBuilder.context = null;
        IkGuiImplDockSettings.context = null;
        IkGuiImplViewports.context = null;
        IkGuiImplMetrics.context = null;
        IkGuiImplDebugTools.context = null;
        IkGuiInternal.context = null;
    }

    /**
     * Create an explicit dockspace node within the current window, filling the available content
     * region. Dockspaces need to be submitted before any window they can host, so submit them early
     * in your frame. They also need to be kept alive if hidden, otherwise windows docked into them
     * will be undocked; submit non-visible dockspaces with {@link DockNodeFlags#KEEP_ALIVE_ONLY}.
     *
     * @param dockspaceID The ID of the dockspace, which must not be 0.
     * @return The dockspace ID.
     */
    public static int dockSpace(int dockspaceID) {
        return IkGuiImplDocking.dockSpace(dockspaceID, 0, 0, DockNodeFlags.NONE, null);
    }

    /**
     * Create an explicit dockspace node within the current window.
     *
     * @param dockspaceID The ID of the dockspace, which must not be 0.
     * @param width The width, 0 to use the available width or negative to leave space.
     * @param height The height, 0 to use the available height or negative to leave space.
     * @return The dockspace ID.
     * @see #dockSpace(int)
     */
    public static int dockSpace(int dockspaceID, float width, float height) {
        return IkGuiImplDocking.dockSpace(dockspaceID, width, height, DockNodeFlags.NONE, null);
    }

    /**
     * Create an explicit dockspace node within the current window.
     *
     * @param dockspaceID The ID of the dockspace, which must not be 0.
     * @param width The width, 0 to use the available width or negative to leave space.
     * @param height The height, 0 to use the available height or negative to leave space.
     * @param dockNodeFlags Dock node flags.
     * @return The dockspace ID.
     * @see #dockSpace(int)
     * @see DockNodeFlags
     */
    public static int dockSpace(int dockspaceID, float width, float height, int dockNodeFlags) {
        return IkGuiImplDocking.dockSpace(dockspaceID, width, height, dockNodeFlags, null);
    }

    /**
     * Create an explicit dockspace node within the current window.
     *
     * @param dockspaceID The ID of the dockspace, which must not be 0.
     * @param width The width, 0 to use the available width or negative to leave space.
     * @param height The height, 0 to use the available height or negative to leave space.
     * @param dockNodeFlags Dock node flags.
     * @param windowClass The window class of the dockspace, which controls which windows can be
     *     docked into it. May be null.
     * @return The dockspace ID.
     * @see #dockSpace(int)
     * @see DockNodeFlags
     */
    public static int dockSpace(
            int dockspaceID,
            float width,
            float height,
            int dockNodeFlags,
            WindowClass windowClass) {
        return IkGuiImplDocking.dockSpace(dockspaceID, width, height, dockNodeFlags, windowClass);
    }

    /**
     * Create an invisible window covering the main viewport, then submit a dockspace into it. Most
     * applications can simply call this once per frame to allow docking windows into e.g. the edges
     * of the screen.
     *
     * @return The dockspace ID.
     */
    public static int dockSpaceOverViewport() {
        return IkGuiImplDocking.dockSpaceOverViewport(0, null, DockNodeFlags.NONE, null);
    }

    /**
     * Create an invisible window covering a viewport, then submit a dockspace into it.
     *
     * @param dockspaceID The ID of the dockspace, or 0 to use a default ID.
     * @param viewport The viewport, or null for the main viewport.
     * @return The dockspace ID.
     * @see #dockSpaceOverViewport()
     */
    public static int dockSpaceOverViewport(int dockspaceID, Viewport viewport) {
        return IkGuiImplDocking.dockSpaceOverViewport(
                dockspaceID, viewport, DockNodeFlags.NONE, null);
    }

    /**
     * Create an invisible window covering a viewport, then submit a dockspace into it. Use with
     * {@link DockNodeFlags#PASSTHROUGH_CENTRAL_NODE} to keep the central node transparent.
     *
     * @param dockspaceID The ID of the dockspace, or 0 to use a default ID.
     * @param viewport The viewport, or null for the main viewport.
     * @param dockNodeFlags Dock node flags.
     * @return The dockspace ID.
     * @see #dockSpaceOverViewport()
     * @see DockNodeFlags
     */
    public static int dockSpaceOverViewport(int dockspaceID, Viewport viewport, int dockNodeFlags) {
        return IkGuiImplDocking.dockSpaceOverViewport(dockspaceID, viewport, dockNodeFlags, null);
    }

    /**
     * Create an invisible window covering a viewport, then submit a dockspace into it.
     *
     * @param dockspaceID The ID of the dockspace, or 0 to use a default ID.
     * @param viewport The viewport, or null for the main viewport.
     * @param dockNodeFlags Dock node flags.
     * @param windowClass The window class of the dockspace, may be null.
     * @return The dockspace ID.
     * @see #dockSpaceOverViewport()
     * @see DockNodeFlags
     */
    public static int dockSpaceOverViewport(
            int dockspaceID, Viewport viewport, int dockNodeFlags, WindowClass windowClass) {
        return IkGuiImplDocking.dockSpaceOverViewport(
                dockspaceID, viewport, dockNodeFlags, windowClass);
    }

    public static boolean dragFloat(String label, float[] value) {
        return IkGuiImplSliders.dragFloat(
                label,
                value,
                1.0f,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat(String label, @NonNull IkFloat value) {
        return IkGuiImplSliders.dragFloat(
                label,
                value.getData(),
                1.0f,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat(String label, float[] value, float speed) {
        return IkGuiImplSliders.dragFloat(
                label,
                value,
                speed,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat(String label, @NonNull IkFloat value, float speed) {
        return IkGuiImplSliders.dragFloat(
                label,
                value.getData(),
                speed,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat(
            String label, float[] value, float speed, float min, float max) {
        return IkGuiImplSliders.dragFloat(
                label,
                value,
                speed,
                min,
                max,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat(
            String label, @NonNull IkFloat value, float speed, float min, float max) {
        return IkGuiImplSliders.dragFloat(
                label,
                value.getData(),
                speed,
                min,
                max,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat(
            String label, float[] value, float speed, float min, float max, String format) {
        return IkGuiImplSliders.dragFloat(label, value, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragFloat(
            String label,
            @NonNull IkFloat value,
            float speed,
            float min,
            float max,
            String format) {
        return IkGuiImplSliders.dragFloat(
                label, value.getData(), speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragFloat(
            String label,
            float[] value,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.dragFloat(label, value, speed, min, max, format, sliderFlags);
    }

    public static boolean dragFloat(
            String label,
            @NonNull IkFloat value,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.dragFloat(
                label, value.getData(), speed, min, max, format, sliderFlags);
    }

    public static boolean dragFloat2(String label, float[] values) {
        return IkGuiImplSliders.dragFloat2(
                label,
                values,
                1.0f,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat2(String label, float[] values, float speed) {
        return IkGuiImplSliders.dragFloat2(
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
        return IkGuiImplSliders.dragFloat2(
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
        return IkGuiImplSliders.dragFloat2(
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
        return IkGuiImplSliders.dragFloat2(label, values, speed, min, max, format, sliderFlags);
    }

    public static boolean dragFloat3(String label, float[] values) {
        return IkGuiImplSliders.dragFloat3(
                label,
                values,
                1.0f,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat3(String label, float[] values, float speed) {
        return IkGuiImplSliders.dragFloat3(
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
        return IkGuiImplSliders.dragFloat3(
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
        return IkGuiImplSliders.dragFloat3(
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
        return IkGuiImplSliders.dragFloat3(label, values, speed, min, max, format, sliderFlags);
    }

    public static boolean dragFloat4(String label, float[] values) {
        return IkGuiImplSliders.dragFloat4(
                label,
                values,
                1.0f,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragFloat4(String label, float[] values, float speed) {
        return IkGuiImplSliders.dragFloat4(
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
        return IkGuiImplSliders.dragFloat4(
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
        return IkGuiImplSliders.dragFloat4(
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
        return IkGuiImplSliders.dragFloat4(label, values, speed, min, max, format, sliderFlags);
    }

    public static boolean dragFloatRange2(String label, float[] currentMin, float[] currentMax) {
        return IkGuiImplSliders.dragFloatRange2(
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
        return IkGuiImplSliders.dragFloatRange2(
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
        return IkGuiImplSliders.dragFloatRange2(
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
        return IkGuiImplSliders.dragFloatRange2(
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
        return IkGuiImplSliders.dragFloatRange2(
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
        return IkGuiImplSliders.dragFloatRange2(
                label, currentMin, currentMax, speed, min, max, format, formatMax, sliderFlags);
    }

    public static boolean dragInt(String label, int[] value) {
        return IkGuiImplSliders.dragInt(
                label,
                value,
                1.0f,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt(String label, int[] value, float speed) {
        return IkGuiImplSliders.dragInt(
                label,
                value,
                speed,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt(String label, int[] value, float speed, int min, int max) {
        return IkGuiImplSliders.dragInt(
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
        return IkGuiImplSliders.dragInt(label, value, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragInt(
            String label,
            int[] value,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.dragInt(label, value, speed, min, max, format, sliderFlags);
    }

    public static boolean dragInt2(String label, int[] values) {
        return IkGuiImplSliders.dragInt2(
                label,
                values,
                1.0f,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt2(String label, int[] values, float speed) {
        return IkGuiImplSliders.dragInt2(
                label,
                values,
                speed,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt2(String label, int[] values, float speed, int min, int max) {
        return IkGuiImplSliders.dragInt2(
                label,
                values,
                speed,
                min,
                max,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt2(
            String label, int[] values, float speed, int min, int max, String format) {
        return IkGuiImplSliders.dragInt2(label, values, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragInt2(
            String label,
            int[] values,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.dragInt2(label, values, speed, min, max, format, sliderFlags);
    }

    public static boolean dragInt3(String label, int[] values) {
        return IkGuiImplSliders.dragInt3(
                label,
                values,
                1.0f,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt3(String label, int[] values, float speed) {
        return IkGuiImplSliders.dragInt3(
                label,
                values,
                speed,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt3(String label, int[] values, float speed, int min, int max) {
        return IkGuiImplSliders.dragInt3(
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
        return IkGuiImplSliders.dragInt3(label, values, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragInt3(
            String label,
            int[] values,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.dragInt3(label, values, speed, min, max, format, sliderFlags);
    }

    public static boolean dragInt4(String label, int[] values) {
        return IkGuiImplSliders.dragInt4(
                label,
                values,
                1.0f,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt4(String label, int[] values, float speed) {
        return IkGuiImplSliders.dragInt4(
                label,
                values,
                speed,
                0,
                0,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean dragInt4(String label, int[] values, float speed, int min, int max) {
        return IkGuiImplSliders.dragInt4(
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
        return IkGuiImplSliders.dragInt4(label, values, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragInt4(
            String label,
            int[] values,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.dragInt4(label, values, speed, min, max, format, sliderFlags);
    }

    public static boolean dragIntRange2(String label, int[] currentMin, int[] currentMax) {
        return IkGuiImplSliders.dragIntRange2(
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
        return IkGuiImplSliders.dragIntRange2(
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
        return IkGuiImplSliders.dragIntRange2(
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
        return IkGuiImplSliders.dragIntRange2(
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
        return IkGuiImplSliders.dragIntRange2(
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
        return IkGuiImplSliders.dragIntRange2(
                label, currentMin, currentMax, speed, min, max, format, formatMax, sliderFlags);
    }

    public static boolean dragScalar(
            String label, @NonNull SliderDataType dataType, @NonNull Object data) {
        return IkGuiImplSliders.dragScalar(
                label, dataType, data, 1.0f, null, null, null, SliderFlags.NONE);
    }

    public static boolean dragScalar(
            String label, @NonNull SliderDataType dataType, @NonNull Object data, float speed) {
        return IkGuiImplSliders.dragScalar(
                label, dataType, data, speed, null, null, null, SliderFlags.NONE);
    }

    public static boolean dragScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            float speed,
            Number min,
            Number max) {
        return IkGuiImplSliders.dragScalar(
                label, dataType, data, speed, min, max, null, SliderFlags.NONE);
    }

    public static boolean dragScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            float speed,
            Number min,
            Number max,
            String format) {
        return IkGuiImplSliders.dragScalar(
                label, dataType, data, speed, min, max, format, SliderFlags.NONE);
    }

    /**
     * Submit preview contents for a combo, to display more than just a text label. Call this after
     * beginCombo(), and after endCombo() if the combo was open. The preview is designed to only
     * host non-interactive elements.
     *
     * @return True if the preview is visible, in which case call endComboPreview().
     */
    public static boolean beginComboPreview() {
        return IkGuiImplCombo.beginComboPreview();
    }

    /** End the preview contents of a combo, only call this if beginComboPreview() returned true. */
    public static void endComboPreview() {
        IkGuiImplCombo.endComboPreview();
    }

    /**
     * A drag for any data type. The data is an array of the data type or one of the Ik* boxes, and
     * the limits are boxed numbers, e.g. {@code dragScalar("x", SliderDataType.LONG, value, 1.0f,
     * 0L, 100L)}. A null limit uses the limit of the data type. If min and max are both 0, the
     * value is not clamped unless SliderFlags.CLAMP_ZERO_RANGE is set.
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param data The data.
     * @param speed The speed, in value units per pixel.
     * @param min The minimum value, or null for none.
     * @param max The maximum value, or null for none.
     * @param format The display format, null for the default for the data type.
     * @param sliderFlags Slider flags.
     * @return True if the value changed.
     */
    public static boolean dragScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            float speed,
            Number min,
            Number max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.dragScalar(
                label, dataType, data, speed, min, max, format, sliderFlags);
    }

    public static boolean dragScalarN(
            String label, @NonNull SliderDataType dataType, @NonNull Object data, int components) {
        return IkGuiImplSliders.dragScalarN(
                label, dataType, data, components, 1.0f, null, null, null, SliderFlags.NONE);
    }

    public static boolean dragScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            float speed) {
        return IkGuiImplSliders.dragScalarN(
                label, dataType, data, components, speed, null, null, null, SliderFlags.NONE);
    }

    public static boolean dragScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            float speed,
            Number min,
            Number max) {
        return IkGuiImplSliders.dragScalarN(
                label, dataType, data, components, speed, min, max, null, SliderFlags.NONE);
    }

    public static boolean dragScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            float speed,
            Number min,
            Number max,
            String format) {
        return IkGuiImplSliders.dragScalarN(
                label, dataType, data, components, speed, min, max, format, SliderFlags.NONE);
    }

    public static boolean dragScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            float speed,
            Number min,
            Number max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.dragScalarN(
                label, dataType, data, components, speed, min, max, format, sliderFlags);
    }

    public static void dummy(@NonNull Vector2f size) {
        IkGuiImplLayout.dummy(size.x, size.y);
    }

    public static void dummy(float width, float height) {
        IkGuiImplLayout.dummy(width, height);
    }

    public static void end() {
        IkGuiImplWindows.end();
    }

    public static void endChild() {
        IkGuiImplWindows.endChild();
    }

    public static void endCombo() {
        IkGuiImplCombo.endCombo();
    }

    public static void endDisabled() {
        IkGuiImplUtils.endDisabled();
    }

    /** Only call endDragDropSource() if beginDragDropSource() returns true. */
    public static void endDragDropSource() {
        IkGuiImplDragDrop.endDragDropSource();
    }

    /** Only call endDragDropTarget() if beginDragDropTarget() returns true. */
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

    /** End the main menu bar, only call this if beginMainMenuBar() returned true. */
    public static void endMainMenuBar() {
        IkGuiImplMenus.endMainMenuBar();
    }

    /** End a menu, only call this if beginMenu() returned true. */
    public static void endMenu() {
        IkGuiImplMenus.endMenu();
    }

    /** End the menu bar, only call this if beginMenuBar() returned true. */
    public static void endMenuBar() {
        IkGuiImplMenus.endMenuBar();
    }

    /** End a popup, only call this if a beginPopup*() call returned true. */
    public static void endPopup() {
        IkGuiImplPopups.endPopup();
    }

    public static void endTabBar() {
        IkGuiImplTabs.endTabBar();
    }

    public static void endTabItem() {
        IkGuiImplTabs.endTabItem();
    }

    /** Only call endTable() if beginTable() returns true. */
    public static void endTable() {
        IkGuiImplTables.endTable();
    }

    /** End a tooltip, only call this if beginTooltip() or beginItemTooltip() returned true. */
    public static void endTooltip() {
        IkGuiImplPopups.endTooltip();
    }

    /**
     * Find a viewport by ID.
     *
     * @param id The viewport ID.
     * @return The viewport, or null if not found.
     */
    public static Viewport findViewportByID(int id) {
        return IkGuiImplViewports.findViewportByID(id);
    }

    /**
     * Find a viewport by the platform handle that the backend stored in it. This is a helper for
     * backends.
     *
     * @param platformHandle The platform handle, compared by equality.
     * @return The viewport, or null if not found.
     */
    public static Viewport findViewportByPlatformHandle(Object platformHandle) {
        return IkGuiImplViewports.findViewportByPlatformHandle(platformHandle);
    }

    /**
     * Fetch the background draw list for the viewport of the current window. This draw list will be
     * the first rendered one, useful to quickly draw shapes/text behind other windows.
     *
     * @return The background draw list.
     */
    public static DrawList getBackgroundDrawList() {
        return IkGuiImplUtils.getBackgroundDrawList(null);
    }

    /**
     * Fetch the background draw list for a viewport. This draw list will be the first rendered one,
     * useful to quickly draw shapes/text behind other windows.
     *
     * @param viewport The viewport, or null for the viewport of the current window.
     * @return The background draw list.
     */
    public static DrawList getBackgroundDrawList(Viewport viewport) {
        return IkGuiImplUtils.getBackgroundDrawList(viewport);
    }

    public static String getClipboardText() {
        return IkGuiImplUtils.getClipboardText();
    }

    /**
     * Fetch the current style, which can be modified. Use pushStyleColor() and pushStyleVar*() to
     * change it temporarily.
     *
     * @return The style.
     */
    public static Style getStyle() {
        return context.style;
    }

    /**
     * Set the current style to the dark color theme, which is the default.
     *
     * @see #styleColorsDark(Style)
     */
    public static void styleColorsDark() {
        StyleColors.setThemeDark(context.style.color);
    }

    /**
     * Set a style to the dark color theme, which is the default.
     *
     * @param destination The style to modify.
     */
    public static void styleColorsDark(@NonNull Style destination) {
        StyleColors.setThemeDark(destination.color);
    }

    /**
     * Set the current style to the light color theme, which works best with a thicker font than the
     * default and frame borders.
     *
     * @see #styleColorsLight(Style)
     */
    public static void styleColorsLight() {
        StyleColors.setThemeLight(context.style.color);
    }

    /**
     * Set a style to the light color theme.
     *
     * @param destination The style to modify.
     */
    public static void styleColorsLight(@NonNull Style destination) {
        StyleColors.setThemeLight(destination.color);
    }

    /**
     * Set the current style to the classic color theme.
     *
     * @see #styleColorsClassic(Style)
     */
    public static void styleColorsClassic() {
        StyleColors.setThemeClassic(context.style.color);
    }

    /**
     * Set a style to the classic color theme.
     *
     * @param destination The style to modify.
     */
    public static void styleColorsClassic(@NonNull Style destination) {
        StyleColors.setThemeClassic(destination.color);
    }

    /**
     * The name of a style color, which is the name of the enum constant.
     *
     * @param type The type of color.
     * @return The name.
     */
    public static String getStyleColorName(@NonNull ColorType type) {
        return type.name();
    }

    /**
     * Fetch a style color as floats, including any pushed colors but not the global alpha.
     *
     * @param type The type of color.
     * @return The color, as red, green, blue and alpha from 0 to 1.
     */
    public static Vector4f getStyleColorVec4(@NonNull ColorType type) {
        final Vector4f result = new Vector4f();
        IkGuiImplUtils.colorConvertU32ToFloat4(IkGuiImplUtils.getColor(type), result);
        return result;
    }

    /**
     * Fetch a style color as floats, including any pushed colors but not the global alpha.
     *
     * @param type The type of color.
     * @param result Where to store the color, as red, green, blue and alpha from 0 to 1.
     */
    public static void getStyleColorVec4(@NonNull ColorType type, @NonNull Vector4f result) {
        IkGuiImplUtils.colorConvertU32ToFloat4(IkGuiImplUtils.getColor(type), result);
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
     * Apply the global alpha (style.alpha) to a color, ready to draw with.
     *
     * @param color The color, in RGBA format.
     * @return The color with the global alpha applied.
     */
    public static int getColorU32(int color) {
        return IkGuiImplUtils.applyGlobalAlpha(color, false);
    }

    /**
     * Apply an alpha multiplier and the global alpha (style.alpha) to a color, ready to draw with.
     *
     * @param color The color, in RGBA format.
     * @param alphaMultiplier An extra multiplier for the alpha.
     * @return The color with the alpha applied.
     */
    public static int getColorU32(int color, float alphaMultiplier) {
        return IkGuiImplUtils.applyGlobalAlpha(Color.multiplyAlpha(color, alphaMultiplier), false);
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

    /**
     * Fetch the draw data for the main viewport, which is valid after render() and until the next
     * call to newFrame(). The draw data for other viewports is in each viewport, see {@link
     * PlatformIO#viewports}.
     *
     * @return The draw data, or null if it is not currently valid.
     */
    public static DrawData getDrawData() {
        return IkGuiImplViewports.getDrawData();
    }

    public static float getCursorStartPosY() {
        return IkGuiImplUtils.getCursorStartPosY();
    }

    /**
     * Peek directly into the current payload data from anywhere.
     *
     * @param <T> The type of payload data.
     * @return The payload data, or null when drag and drop is finished or inactive.
     */
    public static <T> T getDragDropPayload() {
        return IkGuiImplDragDrop.getDragDropPayload();
    }

    /**
     * Peek directly into the current payload data from anywhere, if its type is the fully qualified
     * name of the class.
     *
     * @param aClass The class of the payload.
     * @param <T> The type of payload data.
     * @return The payload data, or null when drag and drop is finished or inactive, or the payload
     *     is a different type.
     */
    public static <T> T getDragDropPayload(@NonNull Class<T> aClass) {
        return IkGuiImplDragDrop.getDragDropPayload(aClass);
    }

    /**
     * Peek directly into the current payload data from anywhere, if it has the given type.
     *
     * @param dataType The payload type.
     * @param <T> The type of payload data.
     * @return The payload data, or null when drag and drop is finished or inactive, or the payload
     *     is a different type.
     */
    public static <T> T getDragDropPayload(@NonNull String dataType) {
        return IkGuiImplDragDrop.getDragDropPayload(dataType);
    }

    /**
     * Peek directly into the current payload from anywhere, equivalent to ImGui's
     * GetDragDropPayload(). Use {@link Payload#isDataType(String)} to check the payload type, and
     * {@link Payload#isPreview()}/{@link Payload#isDelivery()} after accepting it.
     *
     * @return The payload, or null when drag and drop is finished or inactive.
     */
    public static Payload getDragDropPayloadInfo() {
        return IkGuiImplDragDrop.getDragDropPayloadInfo();
    }

    /**
     * Get the current font size in points, as set by the user or the style. Text is rasterized at
     * this size scaled by the DPI, so on screens with a DPI scale other than 1 this is not the
     * on-screen size. Use {@link #getFontSizeInPixels()} for layout, like sizing widgets relative
     * to the text (Dear ImGui's GetFontSize() returns pixels).
     *
     * @return The font size, in points.
     * @see #getFontSizeInPixels()
     */
    public static int getFontSize() {
        return context.fontSize;
    }

    /**
     * Get the current font size in pixels, which is the height of a line of text on screen. This is
     * what Dear ImGui's GetFontSize() returns, so use it for layout, like sizing widgets relative
     * to the text. It differs from {@link #getFontSize()} by the DPI scale.
     *
     * @return The font size, in pixels.
     * @see #getFontSize()
     */
    public static float getFontSizeInPixels() {
        return IkGuiInternal.getFontSize();
    }

    /**
     * Fetch the foreground draw list for the viewport of the current window. This draw list will be
     * the last rendered one, useful to quickly draw shapes/text over other windows.
     *
     * @return The foreground draw list.
     */
    public static DrawList getForegroundDrawList() {
        return IkGuiImplUtils.getForegroundDrawList(null);
    }

    /**
     * Fetch the foreground draw list for a viewport. This draw list will be the last rendered one,
     * useful to quickly draw shapes/text over other windows.
     *
     * @param viewport The viewport, or null for the viewport of the current window.
     * @return The foreground draw list.
     */
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
    /**
     * Calculate a unique ID by hashing the provided value with the entire ID stack.
     *
     * @param name The name.
     * @return The ID.
     */
    public static int getID(String name) {
        return IkGuiImplUtils.getID(name);
    }

    /**
     * Calculate a unique ID by hashing the provided value with the entire ID stack.
     *
     * @param value The integer value.
     * @return The ID.
     */
    public static int getID(int value) {
        return IkGuiImplUtils.getID(value);
    }

    /**
     * Tell single clicks and double clicks on the last item apart, with the left mouse button.
     *
     * @return 1 for a delayed single click, 2 or more for repeated clicks, otherwise 0.
     * @see #getItemClickedCountWithSingleClickDelay(MouseButton, long)
     */
    public static int getItemClickedCountWithSingleClickDelay() {
        return IkGuiImplUtils.getItemClickedCountWithSingleClickDelay(MouseButton.LEFT, -1);
    }

    /**
     * Tell single clicks and double clicks on the last item apart.
     *
     * @param button The mouse button.
     * @return 1 for a delayed single click, 2 or more for repeated clicks, otherwise 0.
     * @see #getItemClickedCountWithSingleClickDelay(MouseButton, long)
     */
    public static int getItemClickedCountWithSingleClickDelay(@NonNull MouseButton button) {
        return IkGuiImplUtils.getItemClickedCountWithSingleClickDelay(button, -1);
    }

    /**
     * Tell single clicks and double clicks on the last item apart, as a building block for things
     * like "click a selected label again to rename it". Returns 1 for a single click, but only
     * after a delay after the mouse is released, and 2 or more right away for double clicks and
     * further repeated clicks.
     *
     * <p>When this returns 1, you can also check:
     *
     * <ul>
     *   <li>io.mouseClickedPosition for where the mouse was at the time of the click.
     *   <li>isItemHovered() for whether the mouse is still over the item after the delay.
     *   <li>getContext().lastActiveIDWasSelected or lastActiveIDWasSoleSelected for whether the
     *       item was selected at the time of the click.
     * </ul>
     *
     * @param button The mouse button.
     * @param delay The delay after the release, in milliseconds, or negative to use
     *     io.mouseSingleClickDelay. It's at least slightly longer than io.mouseDoubleClickTime.
     * @return 1 for a delayed single click, 2 or more for repeated clicks, otherwise 0.
     */
    public static int getItemClickedCountWithSingleClickDelay(
            @NonNull MouseButton button, long delay) {
        return IkGuiImplUtils.getItemClickedCountWithSingleClickDelay(button, delay);
    }

    /**
     * Get the ID of the last item, which is often the same as the ID that was passed to the item
     * function. Returns 0 for items that don't have an ID, like text().
     *
     * @return The ID of the last item.
     */
    public static int getItemID() {
        return context.lastItemData.id;
    }

    /**
     * Get the generic item flags of the last item.
     *
     * @return The item flags of the last item.
     * @see ItemFlags
     */
    public static int getItemFlags() {
        return context.lastItemData.itemFlags;
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

    /**
     * Uses the provided repeat delay and rate to return a count of how many times the key has been
     * pressed this frame, most often 0 or 1 but might be greater if the rate is small enough that
     * multiple presses happen per frame.
     *
     * @param key The key to check.
     * @param repeatDelay The delay before repeating starts, in milliseconds.
     * @param rate The repeat rate, in milliseconds.
     * @return The number of times the key was pressed this frame.
     */
    public static int getKeyPressedAmount(@NonNull Key key, long repeatDelay, long rate) {
        return IkGuiImplUtils.getKeyPressedAmount(key, repeatDelay, rate);
    }

    /**
     * Fetch the primary/default viewport. This is never null.
     *
     * @return The main viewport.
     */
    public static Viewport getMainViewport() {
        return IkGuiImplViewports.getMainViewport();
    }

    public static int getMouseClickedCount(@NonNull MouseButton button) {
        return context.io.mouseClickedCount[button.index];
    }

    /**
     * Get the desired mouse cursor shape. This is reset to {@link MouseCursor#ARROW} in {@link
     * #newFrame()}, and updated during the frame. Valid before {@link #render()}.
     *
     * @return The desired cursor.
     */
    public static MouseCursor getMouseCursor() {
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

    /**
     * Get the maximum scroll amount on the x-axis, which is roughly the content width minus the
     * window width.
     *
     * @return The maximum horizontal scroll.
     */
    public static float getScrollMaxX() {
        return IkGuiImplUtils.getScrollMaxX();
    }

    /**
     * Get the maximum scroll amount on the y-axis, which is roughly the content height minus the
     * window height.
     *
     * @return The maximum vertical scroll.
     */
    public static float getScrollMaxY() {
        return IkGuiImplUtils.getScrollMaxY();
    }

    public static float getScrollX() {
        return IkGuiImplUtils.getScrollX();
    }

    public static float getScrollY() {
        return IkGuiImplUtils.getScrollY();
    }

    /**
     * Fetch the persistent state storage of the current window, which is used to store things like
     * whether tree nodes are open.
     *
     * @return The storage for the current window.
     */
    public static Storage getStateStorage() {
        return context.windowCurrent.currentStateStorage;
    }

    /**
     * Replace the state storage used by the current window for the rest of the frame, for example
     * to share tree node open states between windows. The window goes back to its own storage next
     * frame.
     *
     * @param storage The storage to use, or null to use the window's own storage.
     */
    public static void setStateStorage(Storage storage) {
        final Window window = context.windowCurrent;
        window.currentStateStorage = storage != null ? storage : window.stateStorage;
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

    /**
     * The height of a line of text, which is approximately the font size in pixels.
     *
     * @return The line height.
     */
    public static float getTextLineHeight() {
        return IkGuiImplLayout.getTextLineHeight();
    }

    /**
     * The distance in pixels between 2 consecutive lines of text, which is the font size plus the
     * item spacing.
     *
     * @return The line height plus spacing.
     */
    public static float getTextLineHeightWithSpacing() {
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

    /**
     * Fetch the viewport associated with the current window.
     *
     * @return The viewport of the current window.
     */
    public static Viewport getWindowViewport() {
        return IkGuiImplViewports.getWindowViewport();
    }

    /**
     * Get the DPI scale of the current window's viewport, where 1 is 96 DPI.
     *
     * @return The DPI scale of the current window.
     */
    public static float getWindowDpiScale() {
        return context.currentDpiScale;
    }

    /**
     * Get the draw list associated with the current window, to append your own drawing primitives.
     *
     * @return The draw list for the current window.
     */
    public static DrawList getWindowDrawList() {
        return IkGuiImplWindows.getWindowDrawList();
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

    /**
     * Display an image, with an optional border according to style.imageBorderSize.
     *
     * @param texture The texture to display.
     * @param width The width of the image.
     * @param height The height of the image.
     */
    public static void image(@NonNull TextureInfo texture, float width, float height) {
        IkGuiImplMiscWidgets.image(texture, width, height, 0.0f, 0.0f, 1.0f, 1.0f);
    }

    /**
     * Display an image, with an optional border according to style.imageBorderSize.
     *
     * @param texture The texture to display.
     * @param size The size of the image.
     */
    public static void image(@NonNull TextureInfo texture, @NonNull Vector2f size) {
        IkGuiImplMiscWidgets.image(texture, size.x, size.y, 0.0f, 0.0f, 1.0f, 1.0f);
    }

    /**
     * Display part of an image, with an optional border according to style.imageBorderSize.
     *
     * @param texture The texture to display.
     * @param width The width of the image.
     * @param height The height of the image.
     * @param u0 The texture u coordinate at the left.
     * @param v0 The texture v coordinate at the top.
     * @param u1 The texture u coordinate at the right.
     * @param v1 The texture v coordinate at the bottom.
     */
    public static void image(
            @NonNull TextureInfo texture,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1) {
        IkGuiImplMiscWidgets.image(texture, width, height, u0, v0, u1, v1);
    }

    /**
     * Display part of an image, with an optional border according to style.imageBorderSize.
     *
     * @param texture The texture to display.
     * @param size The size of the image.
     * @param uv0 The texture coordinates at the top left.
     * @param uv1 The texture coordinates at the bottom right.
     */
    public static void image(
            @NonNull TextureInfo texture,
            @NonNull Vector2f size,
            @NonNull Vector2f uv0,
            @NonNull Vector2f uv1) {
        IkGuiImplMiscWidgets.image(texture, size.x, size.y, uv0.x, uv0.y, uv1.x, uv1.y);
    }

    /**
     * A button with an image, which adds style.framePadding around the image.
     *
     * @param id The string ID of the button.
     * @param texture The texture to display.
     * @param width The width of the image.
     * @param height The height of the image.
     * @return True if the button was pressed.
     */
    public static boolean imageButton(
            @NonNull String id, @NonNull TextureInfo texture, float width, float height) {
        return IkGuiImplMiscWidgets.imageButton(
                id, texture, width, height, 0.0f, 0.0f, 1.0f, 1.0f, Color.CLEAR, Color.WHITE);
    }

    /**
     * A button with an image, which adds style.framePadding around the image.
     *
     * @param id The string ID of the button.
     * @param texture The texture to display.
     * @param size The size of the image.
     * @return True if the button was pressed.
     */
    public static boolean imageButton(
            @NonNull String id, @NonNull TextureInfo texture, @NonNull Vector2f size) {
        return IkGuiImplMiscWidgets.imageButton(
                id, texture, size.x, size.y, 0.0f, 0.0f, 1.0f, 1.0f, Color.CLEAR, Color.WHITE);
    }

    /**
     * A button with part of an image, which adds style.framePadding around the image.
     *
     * @param id The string ID of the button.
     * @param texture The texture to display.
     * @param width The width of the image.
     * @param height The height of the image.
     * @param u0 The texture u coordinate at the left.
     * @param v0 The texture v coordinate at the top.
     * @param u1 The texture u coordinate at the right.
     * @param v1 The texture v coordinate at the bottom.
     * @return True if the button was pressed.
     */
    public static boolean imageButton(
            @NonNull String id,
            @NonNull TextureInfo texture,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1) {
        return IkGuiImplMiscWidgets.imageButton(
                id, texture, width, height, u0, v0, u1, v1, Color.CLEAR, Color.WHITE);
    }

    /**
     * A button with part of an image, which adds style.framePadding around the image.
     *
     * @param id The string ID of the button.
     * @param texture The texture to display.
     * @param size The size of the image.
     * @param uv0 The texture coordinates at the top left.
     * @param uv1 The texture coordinates at the bottom right.
     * @return True if the button was pressed.
     */
    public static boolean imageButton(
            @NonNull String id,
            @NonNull TextureInfo texture,
            @NonNull Vector2f size,
            @NonNull Vector2f uv0,
            @NonNull Vector2f uv1) {
        return IkGuiImplMiscWidgets.imageButton(
                id, texture, size.x, size.y, uv0.x, uv0.y, uv1.x, uv1.y, Color.CLEAR, Color.WHITE);
    }

    /**
     * A button with part of an image, which adds style.framePadding around the image.
     *
     * @param id The string ID of the button.
     * @param texture The texture to display.
     * @param width The width of the image.
     * @param height The height of the image.
     * @param u0 The texture u coordinate at the left.
     * @param v0 The texture v coordinate at the top.
     * @param u1 The texture u coordinate at the right.
     * @param v1 The texture v coordinate at the bottom.
     * @param background An extra background color drawn behind the image.
     * @param tint The color to multiply the image by.
     * @return True if the button was pressed.
     */
    public static boolean imageButton(
            @NonNull String id,
            @NonNull TextureInfo texture,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            int background,
            int tint) {
        return IkGuiImplMiscWidgets.imageButton(
                id, texture, width, height, u0, v0, u1, v1, background, tint);
    }

    /**
     * A button with part of an image, which adds style.framePadding around the image.
     *
     * @param id The string ID of the button.
     * @param texture The texture to display.
     * @param size The size of the image.
     * @param uv0 The texture coordinates at the top left.
     * @param uv1 The texture coordinates at the bottom right.
     * @param background An extra background color drawn behind the image.
     * @param tint The color to multiply the image by.
     * @return True if the button was pressed.
     */
    public static boolean imageButton(
            @NonNull String id,
            @NonNull TextureInfo texture,
            @NonNull Vector2f size,
            @NonNull Vector2f uv0,
            @NonNull Vector2f uv1,
            int background,
            int tint) {
        return IkGuiImplMiscWidgets.imageButton(
                id, texture, size.x, size.y, uv0.x, uv0.y, uv1.x, uv1.y, background, tint);
    }

    /**
     * Display an image with a background color, and an optional border according to
     * style.imageBorderSize.
     *
     * @param texture The texture to display.
     * @param width The width of the image.
     * @param height The height of the image.
     * @param background The background color drawn behind the image.
     */
    public static void imageWithBackground(
            @NonNull TextureInfo texture, float width, float height, int background) {
        IkGuiImplMiscWidgets.imageWithBackground(
                texture, width, height, 0.0f, 0.0f, 1.0f, 1.0f, background, Color.WHITE);
    }

    /**
     * Display part of an image with a background color and tint, and an optional border according
     * to style.imageBorderSize.
     *
     * @param texture The texture to display.
     * @param width The width of the image.
     * @param height The height of the image.
     * @param u0 The texture u coordinate at the left.
     * @param v0 The texture v coordinate at the top.
     * @param u1 The texture u coordinate at the right.
     * @param v1 The texture v coordinate at the bottom.
     * @param background The background color drawn behind the image.
     * @param tint The color to multiply the image by.
     */
    public static void imageWithBackground(
            @NonNull TextureInfo texture,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            int background,
            int tint) {
        IkGuiImplMiscWidgets.imageWithBackground(
                texture, width, height, u0, v0, u1, v1, background, tint);
    }

    /**
     * Display part of an image with a background color and tint, and an optional border according
     * to style.imageBorderSize.
     *
     * @param texture The texture to display.
     * @param size The size of the image.
     * @param uv0 The texture coordinates at the top left.
     * @param uv1 The texture coordinates at the bottom right.
     * @param background The background color drawn behind the image.
     * @param tint The color to multiply the image by.
     */
    public static void imageWithBackground(
            @NonNull TextureInfo texture,
            @NonNull Vector2f size,
            @NonNull Vector2f uv0,
            @NonNull Vector2f uv1,
            int background,
            int tint) {
        IkGuiImplMiscWidgets.imageWithBackground(
                texture, size.x, size.y, uv0.x, uv0.y, uv1.x, uv1.y, background, tint);
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
        return IkGuiImplInputText.inputDouble(
                label,
                value.getData(),
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.DOUBLE_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputDouble(String label, double[] value) {
        return IkGuiImplInputText.inputDouble(
                label,
                value,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.DOUBLE_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputDouble(String label, IkDouble value, double step) {
        return IkGuiImplInputText.inputDouble(
                label,
                value.getData(),
                step,
                0.0f,
                IkGuiImplMiscWidgets.DOUBLE_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputDouble(String label, double[] value, double step) {
        return IkGuiImplInputText.inputDouble(
                label,
                value,
                step,
                0.0f,
                IkGuiImplMiscWidgets.DOUBLE_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputDouble(String label, IkDouble value, double step, double stepFast) {
        return IkGuiImplInputText.inputDouble(
                label,
                value.getData(),
                step,
                stepFast,
                IkGuiImplMiscWidgets.DOUBLE_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputDouble(String label, double[] value, double step, double stepFast) {
        return IkGuiImplInputText.inputDouble(
                label,
                value,
                step,
                stepFast,
                IkGuiImplMiscWidgets.DOUBLE_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputDouble(
            String label, IkDouble value, double step, double stepFast, String format) {
        return IkGuiImplInputText.inputDouble(
                label, value.getData(), step, stepFast, format, InputTextFlags.NONE);
    }

    public static boolean inputDouble(
            String label, double[] value, double step, double stepFast, String format) {
        return IkGuiImplInputText.inputDouble(
                label, value, step, stepFast, format, InputTextFlags.NONE);
    }

    public static boolean inputDouble(
            String label,
            IkDouble value,
            double step,
            double stepFast,
            String format,
            int inputTextFlags) {
        return IkGuiImplInputText.inputDouble(
                label, value.getData(), step, stepFast, format, inputTextFlags);
    }

    public static boolean inputDouble(
            String label,
            double[] value,
            double step,
            double stepFast,
            String format,
            int inputTextFlags) {
        return IkGuiImplInputText.inputDouble(label, value, step, stepFast, format, inputTextFlags);
    }

    public static boolean inputFloat(String label, IkFloat value) {
        return IkGuiImplInputText.inputFloat(
                label,
                value.getData(),
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputFloat(String label, float[] value) {
        return IkGuiImplInputText.inputFloat(
                label,
                value,
                0.0f,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputFloat(String label, IkFloat value, float step) {
        return IkGuiImplInputText.inputFloat(
                label,
                value.getData(),
                step,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputFloat(String label, float[] value, float step) {
        return IkGuiImplInputText.inputFloat(
                label,
                value,
                step,
                0.0f,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputFloat(String label, IkFloat value, float step, float stepFast) {
        return IkGuiImplInputText.inputFloat(
                label,
                value.getData(),
                step,
                stepFast,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputFloat(String label, float[] value, float step, float stepFast) {
        return IkGuiImplInputText.inputFloat(
                label,
                value,
                step,
                stepFast,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                InputTextFlags.NONE);
    }

    public static boolean inputFloat(
            String label, IkFloat value, float step, float stepFast, String format) {
        return IkGuiImplInputText.inputFloat(
                label, value.getData(), step, stepFast, format, InputTextFlags.NONE);
    }

    public static boolean inputFloat(
            String label, float[] value, float step, float stepFast, String format) {
        return IkGuiImplInputText.inputFloat(
                label, value, step, stepFast, format, InputTextFlags.NONE);
    }

    public static boolean inputFloat(
            String label,
            IkFloat value,
            float step,
            float stepFast,
            String format,
            int inputTextFlags) {
        return IkGuiImplInputText.inputFloat(
                label, value.getData(), step, stepFast, format, inputTextFlags);
    }

    public static boolean inputFloat(
            String label,
            float[] value,
            float step,
            float stepFast,
            String format,
            int inputTextFlags) {
        return IkGuiImplInputText.inputFloat(label, value, step, stepFast, format, inputTextFlags);
    }

    public static boolean inputFloat2(String label, float[] values) {
        return IkGuiImplInputText.inputFloat2(
                label, values, IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT, InputTextFlags.NONE);
    }

    public static boolean inputFloat2(String label, float[] values, String format) {
        return IkGuiImplInputText.inputFloat2(label, values, format, InputTextFlags.NONE);
    }

    public static boolean inputFloat2(String label, float[] values, int inputTextFlags) {
        return IkGuiImplInputText.inputFloat2(
                label, values, IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT, inputTextFlags);
    }

    public static boolean inputFloat2(
            String label, float[] values, String format, int inputTextFlags) {
        return IkGuiImplInputText.inputFloat2(label, values, format, inputTextFlags);
    }

    public static boolean inputFloat3(String label, float[] values) {
        return IkGuiImplInputText.inputFloat3(
                label, values, IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT, InputTextFlags.NONE);
    }

    public static boolean inputFloat3(String label, float[] values, String format) {
        return IkGuiImplInputText.inputFloat3(label, values, format, InputTextFlags.NONE);
    }

    public static boolean inputFloat3(String label, float[] values, int inputTextFlags) {
        return IkGuiImplInputText.inputFloat3(
                label, values, IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT, inputTextFlags);
    }

    public static boolean inputFloat3(
            String label, float[] values, String format, int inputTextFlags) {
        return IkGuiImplInputText.inputFloat3(label, values, format, inputTextFlags);
    }

    public static boolean inputFloat4(String label, float[] values) {
        return IkGuiImplInputText.inputFloat4(
                label, values, IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT, InputTextFlags.NONE);
    }

    public static boolean inputFloat4(String label, float[] values, String format) {
        return IkGuiImplInputText.inputFloat4(label, values, format, InputTextFlags.NONE);
    }

    public static boolean inputFloat4(String label, float[] values, int inputTextFlags) {
        return IkGuiImplInputText.inputFloat4(
                label, values, IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT, inputTextFlags);
    }

    public static boolean inputFloat4(
            String label, float[] values, String format, int inputTextFlags) {
        return IkGuiImplInputText.inputFloat4(label, values, format, inputTextFlags);
    }

    public static boolean inputInt(String label, IkInt value) {
        return IkGuiImplInputText.inputInt(label, value.getData(), 1, 100, InputTextFlags.NONE);
    }

    public static boolean inputInt(String label, int[] value) {
        return IkGuiImplInputText.inputInt(label, value, 1, 100, InputTextFlags.NONE);
    }

    public static boolean inputInt(String label, IkInt value, int step) {
        return IkGuiImplInputText.inputInt(label, value.getData(), step, 100, InputTextFlags.NONE);
    }

    public static boolean inputInt(String label, int[] value, int step) {
        return IkGuiImplInputText.inputInt(label, value, step, 100, InputTextFlags.NONE);
    }

    public static boolean inputInt(String label, IkInt value, int step, int stepFast) {
        return IkGuiImplInputText.inputInt(
                label, value.getData(), step, stepFast, InputTextFlags.NONE);
    }

    public static boolean inputInt(String label, int[] value, int step, int stepFast) {
        return IkGuiImplInputText.inputInt(label, value, step, stepFast, InputTextFlags.NONE);
    }

    public static boolean inputInt(
            String label, IkInt value, int step, int stepFast, int inputTextFlags) {
        return IkGuiImplInputText.inputInt(label, value.getData(), step, stepFast, inputTextFlags);
    }

    public static boolean inputInt(
            String label, int[] value, int step, int stepFast, int inputTextFlags) {
        return IkGuiImplInputText.inputInt(label, value, step, stepFast, inputTextFlags);
    }

    public static boolean inputInt2(String label, int[] values) {
        return IkGuiImplInputText.inputInt2(label, values, InputTextFlags.NONE);
    }

    public static boolean inputInt2(String label, int[] values, int inputTextFlags) {
        return IkGuiImplInputText.inputInt2(label, values, inputTextFlags);
    }

    public static boolean inputInt3(String label, int[] values) {
        return IkGuiImplInputText.inputInt3(label, values, InputTextFlags.NONE);
    }

    public static boolean inputInt3(String label, int[] values, int inputTextFlags) {
        return IkGuiImplInputText.inputInt3(label, values, inputTextFlags);
    }

    public static boolean inputInt4(String label, int[] values) {
        return IkGuiImplInputText.inputInt4(label, values, InputTextFlags.NONE);
    }

    public static boolean inputInt4(String label, int[] values, int inputTextFlags) {
        return IkGuiImplInputText.inputInt4(label, values, inputTextFlags);
    }

    public static boolean inputScalar(
            String label, @NonNull SliderDataType dataType, @NonNull Object data) {
        return IkGuiImplInputText.inputScalar(
                label, dataType, data, null, null, null, InputTextFlags.NONE);
    }

    public static boolean inputScalar(
            String label, @NonNull SliderDataType dataType, @NonNull Object data, Number step) {
        return IkGuiImplInputText.inputScalar(
                label, dataType, data, step, null, null, InputTextFlags.NONE);
    }

    public static boolean inputScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number step,
            Number stepFast) {
        return IkGuiImplInputText.inputScalar(
                label, dataType, data, step, stepFast, null, InputTextFlags.NONE);
    }

    public static boolean inputScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number step,
            Number stepFast,
            String format) {
        return IkGuiImplInputText.inputScalar(
                label, dataType, data, step, stepFast, format, InputTextFlags.NONE);
    }

    /**
     * An input for any data type, with optional +/- step buttons. The data is an array of the data
     * type or one of the Ik* boxes, and the steps are boxed numbers.
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param data The data.
     * @param step The step for the +/- buttons, or null for no buttons.
     * @param stepFast The step while holding Ctrl, or null to use the step.
     * @param format The display format, null for the default for the data type.
     * @param inputTextFlags Input text flags.
     * @return True if the value changed.
     */
    public static boolean inputScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number step,
            Number stepFast,
            String format,
            int inputTextFlags) {
        return IkGuiImplInputText.inputScalar(
                label, dataType, data, step, stepFast, format, inputTextFlags);
    }

    public static boolean inputScalarN(
            String label, @NonNull SliderDataType dataType, @NonNull Object data, int components) {
        return IkGuiImplInputText.inputScalarN(
                label, dataType, data, components, null, null, null, InputTextFlags.NONE);
    }

    public static boolean inputScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            Number step) {
        return IkGuiImplInputText.inputScalarN(
                label, dataType, data, components, step, null, null, InputTextFlags.NONE);
    }

    public static boolean inputScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            Number step,
            Number stepFast) {
        return IkGuiImplInputText.inputScalarN(
                label, dataType, data, components, step, stepFast, null, InputTextFlags.NONE);
    }

    public static boolean inputScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            Number step,
            Number stepFast,
            String format) {
        return IkGuiImplInputText.inputScalarN(
                label, dataType, data, components, step, stepFast, format, InputTextFlags.NONE);
    }

    public static boolean inputScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            Number step,
            Number stepFast,
            String format,
            int inputTextFlags) {
        return IkGuiImplInputText.inputScalarN(
                label, dataType, data, components, step, stepFast, format, inputTextFlags);
    }

    public static boolean inputText(String label, IkString text) {
        return IkGuiImplInputText.inputText(label, text, InputTextFlags.NONE, null);
    }

    public static boolean inputText(String label, IkString text, int inputTextFlags) {
        return IkGuiImplInputText.inputText(label, text, inputTextFlags, null);
    }

    public static boolean inputText(
            String label,
            @NonNull IkString text,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return IkGuiImplInputText.inputText(label, text, inputTextFlags, callback);
    }

    public static boolean inputTextMultiline(String label, @NonNull IkString text) {
        return IkGuiImplInputText.inputTextMultiline(
                label, text, 0.0f, 0.0f, InputTextFlags.NONE, null);
    }

    public static boolean inputTextMultiline(
            String label, @NonNull IkString text, float width, float height) {
        return IkGuiImplInputText.inputTextMultiline(
                label, text, width, height, InputTextFlags.NONE, null);
    }

    public static boolean inputTextMultiline(
            String label, @NonNull IkString text, float width, float height, int inputTextFlags) {
        return IkGuiImplInputText.inputTextMultiline(
                label, text, width, height, inputTextFlags, null);
    }

    public static boolean inputTextMultiline(
            String label,
            @NonNull IkString text,
            float width,
            float height,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return IkGuiImplInputText.inputTextMultiline(
                label, text, width, height, inputTextFlags, callback);
    }

    public static boolean inputTextMultiline(
            String label, @NonNull IkString text, int inputTextFlags) {
        return IkGuiImplInputText.inputTextMultiline(label, text, 0.0f, 0.0f, inputTextFlags, null);
    }

    public static boolean inputTextMultiline(
            String label,
            @NonNull IkString text,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return IkGuiImplInputText.inputTextMultiline(
                label, text, 0.0f, 0.0f, inputTextFlags, callback);
    }

    public static boolean inputTextWithHint(String label, String hint, @NonNull IkString text) {
        return IkGuiImplInputText.inputTextWithHint(label, hint, text, InputTextFlags.NONE, null);
    }

    public static boolean inputTextWithHint(
            String label, String hint, @NonNull IkString text, int inputTextFlags) {
        return IkGuiImplInputText.inputTextWithHint(label, hint, text, inputTextFlags, null);
    }

    public static boolean inputTextWithHint(
            String label,
            String hint,
            @NonNull IkString text,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return IkGuiImplInputText.inputTextWithHint(label, hint, text, inputTextFlags, callback);
    }

    public static boolean invisibleButton(String text, @NonNull Vector2f size) {
        return IkGuiImplButtons.invisibleButton(text, size.x, size.y, ButtonFlags.NONE);
    }

    public static boolean invisibleButton(String text, float width, float height) {
        return IkGuiImplButtons.invisibleButton(text, width, height, ButtonFlags.NONE);
    }

    public static boolean invisibleButton(String text, @NonNull Vector2f size, int buttonFlags) {
        return IkGuiImplButtons.invisibleButton(text, size.x, size.y, buttonFlags);
    }

    public static boolean invisibleButton(String text, float width, float height, int buttonFlags) {
        return IkGuiImplButtons.invisibleButton(text, width, height, buttonFlags);
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

    /**
     * Check if the mouse button was released io.mouseSingleClickDelay ago, which is true for a
     * single frame. Prefer getItemClickedCountWithSingleClickDelay().
     *
     * @param button The mouse button.
     * @return True on the frame where the delay passes, if the button wasn't pressed again.
     */
    public static boolean isMouseReleasedWithDelay(@NonNull MouseButton button) {
        return IkGuiImplUtils.isMouseReleasedWithDelay(button, -1);
    }

    /**
     * Check if the mouse button was released a certain time ago, which is true for a single frame.
     * Generally used with a delay of at least io.mouseDoubleClickTime, and checking
     * io.mouseClickedLastCount is 1. Prefer getItemClickedCountWithSingleClickDelay().
     *
     * @param button The mouse button.
     * @param delay The delay after the release, in milliseconds, or negative to use
     *     io.mouseSingleClickDelay.
     * @return True on the frame where the delay passes, if the button wasn't pressed again.
     */
    public static boolean isMouseReleasedWithDelay(@NonNull MouseButton button, long delay) {
        return IkGuiImplUtils.isMouseReleasedWithDelay(button, delay);
    }

    public static boolean isMouseReleased(@NonNull MouseButton button) {
        return IkGuiImplUtils.isMouseReleased(button);
    }

    /**
     * Check if a popup is open at the current popup stack level.
     *
     * @param id The ID of the popup.
     * @return True if the popup is open.
     */
    public static boolean isPopupOpen(int id) {
        return IkGuiImplPopups.isPopupOpen(id, PopupFlags.NONE);
    }

    /**
     * Check if a popup is open at the current popup stack level.
     *
     * @param stringID The string ID of the popup, relative to the current ID stack.
     * @return True if the popup is open.
     */
    public static boolean isPopupOpen(@NonNull String stringID) {
        return IkGuiImplPopups.isPopupOpen(stringID, PopupFlags.NONE);
    }

    /**
     * Check if a popup is open.
     *
     * @param id The ID of the popup, should be 0 when using {@link PopupFlags#ANY_POPUP_ID}.
     * @param popupFlags Supports {@link PopupFlags#ANY_POPUP_ID} and {@link
     *     PopupFlags#ANY_POPUP_LEVEL}.
     * @return True if the popup is open.
     */
    public static boolean isPopupOpen(int id, int popupFlags) {
        return IkGuiImplPopups.isPopupOpen(id, popupFlags);
    }

    /**
     * Check if a popup is open.
     *
     * @param stringID The string ID of the popup, relative to the current ID stack.
     * @param popupFlags Supports {@link PopupFlags#ANY_POPUP_ID}, {@link
     *     PopupFlags#ANY_POPUP_LEVEL} can only be used with the ID version.
     * @return True if the popup is open.
     */
    public static boolean isPopupOpen(@NonNull String stringID, int popupFlags) {
        return IkGuiImplPopups.isPopupOpen(stringID, popupFlags);
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

    public static boolean listBox(
            String label, @NonNull IkInt currentItem, @NonNull String[] items) {
        return IkGuiImplMiscWidgets.listBox(label, currentItem, items, -1);
    }

    public static boolean listBox(
            String label, @NonNull IkInt currentItem, @NonNull String[] items, int heightInItems) {
        return IkGuiImplMiscWidgets.listBox(label, currentItem, items, heightInItems);
    }

    /**
     * Load settings from a .ini file. Call after createContext() and before the first newFrame().
     * newFrame() automatically calls this with io.iniFilename, if settings were not already loaded.
     * A missing file is ignored.
     *
     * @param filename The path to the .ini file.
     */
    public static void loadIniSettingsFromDisk(@NonNull String filename) {
        IkGuiImplConfig.loadIniSettingsFromDisk(filename);
    }

    /**
     * Load settings from .ini data from your own data source. Call after createContext() and before
     * the first newFrame().
     *
     * @param data The .ini data.
     */
    public static void loadIniSettingsFromMemory(@NonNull String data) {
        IkGuiImplConfig.loadIniSettingsFromMemory(data);
    }

    /** Helper to display buttons for logging to the terminal, a file, or the clipboard. */
    public static void logButtons() {
        IkGuiImplLogging.logButtons();
    }

    /** Stop logging, closing the file or setting the clipboard as appropriate. */
    public static void logFinish() {
        IkGuiImplLogging.logFinish();
    }

    /**
     * Pass text data straight to the log, without being displayed.
     *
     * @param text The text to log.
     */
    public static void logText(@NonNull String text) {
        IkGuiImplLogging.logText(text);
    }

    /** Start logging to the OS clipboard, which is set when logging finishes. */
    public static void logToClipboard() {
        IkGuiImplLogging.logToClipboard(-1);
    }

    /**
     * Start logging to the OS clipboard, which is set when logging finishes.
     *
     * @param autoOpenDepth Tree nodes are automatically opened up to this depth while logging,
     *     negative values use the default depth.
     */
    public static void logToClipboard(int autoOpenDepth) {
        IkGuiImplLogging.logToClipboard(autoOpenDepth);
    }

    /** Start logging to the file at io.logFilename, appending to it. */
    public static void logToFile() {
        IkGuiImplLogging.logToFile(-1, null);
    }

    /**
     * Start logging to the file at io.logFilename, appending to it.
     *
     * @param autoOpenDepth Tree nodes are automatically opened up to this depth while logging,
     *     negative values use the default depth.
     */
    public static void logToFile(int autoOpenDepth) {
        IkGuiImplLogging.logToFile(autoOpenDepth, null);
    }

    /**
     * Start logging to a file, appending to it.
     *
     * @param autoOpenDepth Tree nodes are automatically opened up to this depth while logging,
     *     negative values use the default depth.
     * @param filename The file to log to, or null to use io.logFilename.
     */
    public static void logToFile(int autoOpenDepth, String filename) {
        IkGuiImplLogging.logToFile(autoOpenDepth, filename);
    }

    /** Start logging to the terminal (standard output). */
    public static void logToTTY() {
        IkGuiImplLogging.logToTTY(-1);
    }

    /**
     * Start logging to the terminal (standard output).
     *
     * @param autoOpenDepth Tree nodes are automatically opened up to this depth while logging,
     *     negative values use the default depth.
     */
    public static void logToTTY(int autoOpenDepth) {
        IkGuiImplLogging.logToTTY(autoOpenDepth);
    }

    /**
     * A menu item.
     *
     * @param label The label, which is also used for the ID.
     * @return True when activated.
     */
    public static boolean menuItem(@NonNull String label) {
        return IkGuiImplMenus.menuItem(label, null, false, true);
    }

    /**
     * A menu item.
     *
     * @param label The label, which is also used for the ID.
     * @param shortcut Shortcut text to display, may be null. This is only displayed, not processed.
     * @return True when activated.
     */
    public static boolean menuItem(@NonNull String label, String shortcut) {
        return IkGuiImplMenus.menuItem(label, shortcut, false, true);
    }

    /**
     * A menu item.
     *
     * @param label The label, which is also used for the ID.
     * @param shortcut Shortcut text to display, may be null. This is only displayed, not processed.
     * @param selected Whether to display a check mark.
     * @return True when activated.
     */
    public static boolean menuItem(@NonNull String label, String shortcut, boolean selected) {
        return IkGuiImplMenus.menuItem(label, shortcut, selected, true);
    }

    /**
     * A menu item.
     *
     * @param label The label, which is also used for the ID.
     * @param shortcut Shortcut text to display, may be null. This is only displayed, not processed.
     * @param selected Whether to display a check mark.
     * @param enabled Whether the item is enabled.
     * @return True when activated.
     */
    public static boolean menuItem(
            @NonNull String label, String shortcut, boolean selected, boolean enabled) {
        return IkGuiImplMenus.menuItem(label, shortcut, selected, enabled);
    }

    /**
     * A menu item which toggles a value when activated.
     *
     * @param label The label, which is also used for the ID.
     * @param shortcut Shortcut text to display, may be null. This is only displayed, not processed.
     * @param selected Whether to display a check mark, toggled when activated. May be null.
     * @return True when activated.
     */
    public static boolean menuItem(@NonNull String label, String shortcut, IkBoolean selected) {
        return IkGuiImplMenus.menuItem(label, shortcut, selected, true);
    }

    /**
     * A menu item which toggles a value when activated.
     *
     * @param label The label, which is also used for the ID.
     * @param shortcut Shortcut text to display, may be null. This is only displayed, not processed.
     * @param selected Whether to display a check mark, toggled when activated. May be null.
     * @param enabled Whether the item is enabled.
     * @return True when activated.
     */
    public static boolean menuItem(
            @NonNull String label, String shortcut, IkBoolean selected, boolean enabled) {
        return IkGuiImplMenus.menuItem(label, shortcut, selected, enabled);
    }

    public static void newFrame() {
        IkGuiImplUtils.newFrame();
    }

    public static void newLine() {
        IkGuiImplLayout.newLine();
    }

    /**
     * Mark a popup as open, which should not be called every frame. The popup is shown by a later
     * call to beginPopup() with the same ID.
     *
     * @param id The ID of the popup.
     * @return True if the popup was toggled open.
     */
    public static boolean openPopup(int id) {
        return IkGuiImplPopups.openPopupEx(id, PopupFlags.NONE);
    }

    /**
     * Mark a popup as open, which should not be called every frame. The popup is shown by a later
     * call to beginPopup() with the same ID, at the same level of the ID stack.
     *
     * @param stringID The string ID of the popup, relative to the current ID stack.
     * @return True if the popup was toggled open.
     */
    public static boolean openPopup(@NonNull String stringID) {
        return IkGuiImplPopups.openPopup(stringID, PopupFlags.NONE);
    }

    /**
     * Mark a popup as open, which should not be called every frame.
     *
     * @param id The ID of the popup.
     * @param popupFlags Popup flags.
     * @return True if the popup was toggled open.
     * @see PopupFlags
     */
    public static boolean openPopup(int id, int popupFlags) {
        return IkGuiImplPopups.openPopupEx(id, popupFlags);
    }

    /**
     * Mark a popup as open, which should not be called every frame.
     *
     * @param stringID The string ID of the popup, relative to the current ID stack.
     * @param popupFlags Popup flags.
     * @return True if the popup was toggled open.
     * @see PopupFlags
     */
    public static boolean openPopup(@NonNull String stringID, int popupFlags) {
        return IkGuiImplPopups.openPopup(stringID, popupFlags);
    }

    /**
     * Open a popup associated with the last item when it is right-clicked. This triggers on the
     * mouse release event, to be consistent with other popup behavior.
     *
     * @return True if the popup was opened.
     */
    public static boolean openPopupOnItemClick() {
        return IkGuiImplPopups.openPopupOnItemClick(null, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open a popup when the last item is right-clicked.
     *
     * @param stringID The string ID of the popup, or null to use the last item ID.
     * @return True if the popup was opened.
     */
    public static boolean openPopupOnItemClick(String stringID) {
        return IkGuiImplPopups.openPopupOnItemClick(stringID, PopupFlags.MOUSE_BUTTON_DEFAULT);
    }

    /**
     * Open a popup when the last item is clicked.
     *
     * @param stringID The string ID of the popup, or null to use the last item ID.
     * @param popupFlags Popup flags, including which mouse button opens it.
     * @return True if the popup was opened.
     * @see PopupFlags
     */
    public static boolean openPopupOnItemClick(String stringID, int popupFlags) {
        return IkGuiImplPopups.openPopupOnItemClick(stringID, popupFlags);
    }

    public static void plotHistogram(
            String label, @NonNull IntFunction<Float> valuesGetter, int count) {
        IkGuiImplMiscWidgets.plotHistogram(
                label, valuesGetter, count, 0, null, Float.MAX_VALUE, Float.MAX_VALUE, 0.0f, 0.0f);
    }

    public static void plotHistogram(String label, float[] values, int count) {
        IkGuiImplMiscWidgets.plotHistogram(
                label, values, count, 0, null, Float.MAX_VALUE, Float.MAX_VALUE, 0.0f, 0.0f);
    }

    public static void plotHistogram(
            String label, @NonNull IntFunction<Float> valuesGetter, int count, int offset) {
        IkGuiImplMiscWidgets.plotHistogram(
                label,
                valuesGetter,
                count,
                offset,
                null,
                Float.MAX_VALUE,
                Float.MAX_VALUE,
                0.0f,
                0.0f);
    }

    public static void plotHistogram(String label, float[] values, int count, int offset) {
        IkGuiImplMiscWidgets.plotHistogram(
                label, values, count, offset, null, Float.MAX_VALUE, Float.MAX_VALUE, 0.0f, 0.0f);
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
                0.0f,
                0.0f);
    }

    public static void plotHistogram(
            String label, float[] values, int count, int offset, String overlay) {
        IkGuiImplMiscWidgets.plotHistogram(
                label,
                values,
                count,
                offset,
                overlay,
                Float.MAX_VALUE,
                Float.MAX_VALUE,
                0.0f,
                0.0f);
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
                label, valuesGetter, count, offset, overlay, scaleMin, scaleMax, 0.0f, 0.0f);
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
                label, values, count, offset, overlay, scaleMin, scaleMax, 0.0f, 0.0f);
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

    public static void plotLines(
            String label, @NonNull IntFunction<Float> valuesGetter, int count) {
        IkGuiImplMiscWidgets.plotLines(
                label, valuesGetter, count, 0, null, Float.MAX_VALUE, Float.MAX_VALUE, 0.0f, 0.0f);
    }

    public static void plotLines(String label, float[] values, int count) {
        IkGuiImplMiscWidgets.plotLines(
                label, values, count, 0, null, Float.MAX_VALUE, Float.MAX_VALUE, 0.0f, 0.0f);
    }

    public static void plotLines(
            String label, @NonNull IntFunction<Float> valuesGetter, int count, int offset) {
        IkGuiImplMiscWidgets.plotLines(
                label,
                valuesGetter,
                count,
                offset,
                null,
                Float.MAX_VALUE,
                Float.MAX_VALUE,
                0.0f,
                0.0f);
    }

    public static void plotLines(String label, float[] values, int count, int offset) {
        IkGuiImplMiscWidgets.plotLines(
                label, values, count, offset, null, Float.MAX_VALUE, Float.MAX_VALUE, 0.0f, 0.0f);
    }

    public static void plotLines(
            String label,
            @NonNull IntFunction<Float> valuesGetter,
            int count,
            int offset,
            String overlay) {
        IkGuiImplMiscWidgets.plotLines(
                label,
                valuesGetter,
                count,
                offset,
                overlay,
                Float.MAX_VALUE,
                Float.MAX_VALUE,
                0.0f,
                0.0f);
    }

    public static void plotLines(
            String label, float[] values, int count, int offset, String overlay) {
        IkGuiImplMiscWidgets.plotLines(
                label,
                values,
                count,
                offset,
                overlay,
                Float.MAX_VALUE,
                Float.MAX_VALUE,
                0.0f,
                0.0f);
    }

    public static void plotLines(
            String label,
            @NonNull IntFunction<Float> valuesGetter,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax) {
        IkGuiImplMiscWidgets.plotLines(
                label, valuesGetter, count, offset, overlay, scaleMin, scaleMax, 0.0f, 0.0f);
    }

    public static void plotLines(
            String label,
            float[] values,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax) {
        IkGuiImplMiscWidgets.plotLines(
                label, values, count, offset, overlay, scaleMin, scaleMax, 0.0f, 0.0f);
    }

    public static void plotLines(
            String label,
            @NonNull IntFunction<Float> valuesGetter,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax,
            float width,
            float height) {
        IkGuiImplMiscWidgets.plotLines(
                label, valuesGetter, count, offset, overlay, scaleMin, scaleMax, width, height);
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
        IkGuiImplMiscWidgets.plotLines(
                label, values, count, offset, overlay, scaleMin, scaleMax, width, height);
    }

    public static void popClipRect() {
        IkGuiImplLayout.popClipRect();
    }

    public static void popFont() {
        IkGuiImplText.popFont();
    }

    public static void popID() {
        IkGuiImplUtils.popID();
    }

    public static void popItemWidth() {
        IkGuiImplLayout.popItemWidth();
    }

    /** Pop an item flag that was pushed with {@link #pushItemFlag(int, boolean)}. */
    public static void popItemFlag() {
        IkGuiImplUtils.popItemFlag();
    }

    public static void popStyleColor() {
        IkGuiImplUtils.popStyleColor();
    }

    public static void popStyleColor(int count) {
        IkGuiImplUtils.popStyleColor(count);
    }

    public static void popStyleVar() {
        IkGuiImplUtils.popStyleVar();
    }

    public static void popStyleVar(int count) {
        IkGuiImplUtils.popStyleVar(count);
    }

    public static void popTextWrapPos() {
        IkGuiImplLayout.popTextWrapPos();
    }

    /**
     * Renders a progress bar.
     *
     * @param progress The progress, ranging from 0.0 (0%) to 1.0 (100%).
     */
    public static void progressBar(float progress) {
        IkGuiImplMiscWidgets.progressBar(progress, -Float.MIN_VALUE, 0, null);
    }

    public static void progressBar(float progress, float width, float height) {
        IkGuiImplMiscWidgets.progressBar(progress, width, height, null);
    }

    public static void progressBar(float progress, float width, float height, String overlayText) {
        IkGuiImplMiscWidgets.progressBar(progress, width, height, overlayText);
    }

    public static void pushClipRect(
            float minX, float minY, float maxX, float maxY, boolean intersectWithCurrentClipRect) {
        IkGuiImplLayout.pushClipRect(minX, minY, maxX, maxY, intersectWithCurrentClipRect);
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
        IkGuiImplText.pushFont(font, context.fontSize);
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
        IkGuiImplText.pushFont(font, size);
    }

    /**
     * Temporarily change the font size, keeping the current font, by pushing values onto a stack.
     * This must be popped with {@link #popFont()} before the end of the frame. Equivalent to
     * ImGui's PushFont(NULL, size).
     *
     * @param size The size of the font.
     * @see #popFont()
     * @see #pushFont(String, int)
     */
    public static void pushFontSize(int size) {
        IkGuiImplText.pushFontSize(size);
    }

    /**
     * Push an ID onto the ID stack.
     *
     * @param id The ID.
     * @return The new ID (which is based on the provided ID, and parent ID if there is one).
     */
    public static int pushID(int id) {
        return IkGuiImplUtils.pushID(id);
    }

    /**
     * Push an ID onto the ID stack.
     *
     * @param name The name, which might be null.
     * @return The new ID (which is based on the name and parent ID if there is one).
     */
    public static int pushID(String name) {
        return IkGuiImplUtils.pushID(name);
    }

    /**
     * Modify an item flag for all following items, until popped with {@link #popItemFlag()}.
     *
     * @param option The item flag(s) to modify.
     * @param enabled Whether the flags should be enabled or disabled.
     * @see ItemFlags
     */
    public static void pushItemFlag(int option, boolean enabled) {
        IkGuiImplUtils.pushItemFlag(option, enabled);
    }

    public static void pushItemWidth(float width) {
        IkGuiImplLayout.pushItemWidth(width);
    }

    public static void pushStyleColor(@NonNull ColorType type, float r, float g, float b, float a) {
        IkGuiImplUtils.pushStyleColor(type, Color.rgba(r, g, b, a));
    }

    public static void pushStyleColor(@NonNull ColorType type, int rgba) {
        IkGuiImplUtils.pushStyleColor(type, rgba);
    }

    public static void pushStyleColor(@NonNull ColorType type, int r, int g, int b, int a) {
        IkGuiImplUtils.pushStyleColor(type, Color.rgba(r, g, b, a));
    }

    public static void pushStyleVarFloat(@NonNull StyleVariable variable, float value) {
        IkGuiImplUtils.pushStyleVarFloat(variable, value);
    }

    /**
     * Temporarily change the x component of a style variable that has 2 floats, keeping y. Pop it
     * with popStyleVar().
     *
     * @param variable The style variable, like StyleVariable.FRAME_PADDING.
     * @param x The new x value.
     */
    public static void pushStyleVarX(@NonNull StyleVariable variable, float x) {
        IkGuiImplUtils.pushStyleVarX(variable, x);
    }

    /**
     * Temporarily change the y component of a style variable that has 2 floats, keeping x. Pop it
     * with popStyleVar().
     *
     * @param variable The style variable, like StyleVariable.FRAME_PADDING.
     * @param y The new y value.
     */
    public static void pushStyleVarY(@NonNull StyleVariable variable, float y) {
        IkGuiImplUtils.pushStyleVarY(variable, y);
    }

    public static void pushStyleVarFloat2(@NonNull StyleVariable variable, float x, float y) {
        IkGuiImplUtils.pushStyleVarFloat2(variable, x, y);
    }

    public static void pushStyleVarInt(@NonNull StyleVariable variable, int value) {
        IkGuiImplUtils.pushStyleVarInt(variable, value);
    }

    public static void pushStyleVarInt2(@NonNull StyleVariable variable, int x, int y) {
        IkGuiImplUtils.pushStyleVarInt2(variable, x, y);
    }

    public static void pushTextWrapPos() {
        IkGuiImplLayout.pushTextWrapPos(0.0f);
    }

    /**
     * Push word-wrapping position for text commands. If less than 0, no wrapping. If 0, wrap to end
     * of window (or column). If greater than 0, wrap at 'wrapLocalPosX' position in window local
     * space.
     *
     * @param wrapLocalPosX The wrapping position.
     */
    public static void pushTextWrapPos(float wrapLocalPosX) {
        IkGuiImplLayout.pushTextWrapPos(wrapLocalPosX);
    }

    /**
     * A radio button that doesn't store its own state.
     *
     * @param label The label, which is also used for the ID.
     * @param active Whether the radio button is currently selected.
     * @return True if the radio button was clicked.
     */
    public static boolean radioButton(String label, boolean active) {
        return IkGuiImplMiscWidgets.radioButton(label, active);
    }

    public static boolean radioButton(String label, @NonNull IkInt selectionStorage, int value) {
        return IkGuiImplMiscWidgets.radioButton(label, selectionStorage, value);
    }

    public static void render() {
        IkGuiImplUtils.render();
    }

    /**
     * Render and swap buffers for all the secondary viewports (platform windows), using the render
     * and swap callbacks in the platform IO. Call in the main loop after updatePlatformWindows().
     * Skips minimized viewports. This may be reimplemented by the application for custom rendering
     * needs, by iterating the {@link PlatformIO#viewports} list.
     */
    public static void renderPlatformWindowsDefault() {
        IkGuiImplViewports.renderPlatformWindowsDefault(null, null);
    }

    /**
     * Render and swap buffers for all the secondary viewports (platform windows), using the render
     * and swap callbacks in the platform IO. Call in the main loop after updatePlatformWindows().
     * Skips minimized viewports. This may be reimplemented by the application for custom rendering
     * needs, by iterating the {@link PlatformIO#viewports} list.
     *
     * @param platformRenderArgument Passed to the platform render and swap callbacks.
     * @param rendererRenderArgument Passed to the renderer render and swap callbacks.
     */
    public static void renderPlatformWindowsDefault(
            Object platformRenderArgument, Object rendererRenderArgument) {
        IkGuiImplViewports.renderPlatformWindowsDefault(
                platformRenderArgument, rendererRenderArgument);
    }

    public static void resetMouseDragDelta() {
        IkGuiImplUtils.resetMouseDragDelta(MouseButton.LEFT);
    }

    public static void resetMouseDragDelta(@NonNull MouseButton button) {
        IkGuiImplUtils.resetMouseDragDelta(button);
    }

    /** Keep rendering on the same line. */
    public static void sameLine() {
        IkGuiImplLayout.sameLine(0, -1);
    }

    /**
     * Keep rendering on the same line.
     *
     * @param offsetFromStartX If positive, the window-local x position. If zero, placed at the end
     *     of the last widget's rectangle.
     */
    public static void sameLine(int offsetFromStartX) {
        IkGuiImplLayout.sameLine(offsetFromStartX, -1);
    }

    /**
     * Keep rendering on the same line.
     *
     * @param offsetFromStartX If positive, the window-local x position. If zero, placed at the end
     *     of the last widget's rectangle.
     * @param spacingAfterCurrent The horizontal spacing between the current and next widget.
     */
    public static void sameLine(final float offsetFromStartX, float spacingAfterCurrent) {
        IkGuiImplLayout.sameLine(offsetFromStartX, spacingAfterCurrent);
    }

    /**
     * Save settings to a .ini file. This is automatically called (if io.iniFilename is not null) a
     * few seconds after any modification that should be reflected in the .ini file, and by
     * destroyContext().
     *
     * @param filename The path to the .ini file.
     */
    public static void saveIniSettingsToDisk(@NonNull String filename) {
        IkGuiImplConfig.saveIniSettingsToDisk(filename);
    }

    /**
     * Save settings to a string, to save by your own means. Call this when io.wantSaveIniSettings
     * is set, then clear io.wantSaveIniSettings yourself.
     *
     * @return The .ini data.
     */
    public static String saveIniSettingsToMemory() {
        return IkGuiImplConfig.saveIniSettingsToMemory();
    }

    public static boolean selectable(String label) {
        return IkGuiImplMiscWidgets.selectable(label, false, SelectableFlags.NONE, 0.0f, 0.0f);
    }

    /**
     * Start a multi-select scope, without selection size or item count information.
     *
     * @param multiSelectFlags The multi-select flags.
     * @return The IO, with requests to apply to your selection.
     * @see #beginMultiSelect(int, int, int)
     */
    public static MultiSelectIO beginMultiSelect(int multiSelectFlags) {
        return IkGuiImplMultiSelect.beginMultiSelect(multiSelectFlags, -1, -1);
    }

    /**
     * Start a multi-select scope, without an item count.
     *
     * @param multiSelectFlags The multi-select flags.
     * @param selectionSize The size of the selection, or -1 if unknown.
     * @return The IO, with requests to apply to your selection.
     * @see #beginMultiSelect(int, int, int)
     */
    public static MultiSelectIO beginMultiSelect(int multiSelectFlags, int selectionSize) {
        return IkGuiImplMultiSelect.beginMultiSelect(multiSelectFlags, selectionSize, -1);
    }

    /**
     * Start a multi-select scope, which implements the standard selection idioms
     * (Ctrl+Mouse/Keyboard, Shift+Mouse/Keyboard, box-select, etc.) for the selectables, tree nodes
     * and checkboxes submitted inside it. Your code owns the selection data:
     *
     * <ol>
     *   <li>Call beginMultiSelect() and apply the requests in the returned IO to your selection.
     *   <li>If using a clipper, make sure the range source item is always submitted, e.g. with
     *       {@code clipper.includeItemByIndex((int) io.rangeSourceItem)}.
     *   <li>For each item, call {@link #setNextItemSelectionUserData(long)} (most likely with the
     *       item index), then submit the item.
     *   <li>Call {@link #endMultiSelect()} and apply the requests in the returned IO.
     * </ol>
     *
     * Without a clipper, applying the requests from beginMultiSelect() is optional, since each item
     * handles them itself. {@link SelectionBasicStorage} and {@link SelectionExternalStorage} can
     * apply requests for you.
     *
     * @param multiSelectFlags The multi-select flags.
     * @param selectionSize The size of the selection, 0/1 if you can only tell if it's empty, or -1
     *     if unknown. This lets CLEAR_ON_ESCAPE skip claiming the Escape key when the selection is
     *     empty.
     * @param itemsCount The number of items, stored in the IO for applying requests, or -1.
     * @return The IO, with requests to apply to your selection. Don't hold on to it.
     * @see MultiSelectFlags
     */
    public static MultiSelectIO beginMultiSelect(
            int multiSelectFlags, int selectionSize, int itemsCount) {
        return IkGuiImplMultiSelect.beginMultiSelect(multiSelectFlags, selectionSize, itemsCount);
    }

    /**
     * End a multi-select scope.
     *
     * @return The IO, with requests to apply to your selection. Don't hold on to it.
     * @see #beginMultiSelect(int, int, int)
     */
    public static MultiSelectIO endMultiSelect() {
        return IkGuiImplMultiSelect.endMultiSelect();
    }

    /**
     * Set the selection user data for the next item, which is required for each item inside a
     * multi-select scope. This is most likely the item's index in your current view, but may be any
     * value except {@link SelectionUserData#INVALID}.
     *
     * @param selectionUserData The value identifying the item in selection requests.
     */
    public static void setNextItemSelectionUserData(long selectionUserData) {
        IkGuiImplMultiSelect.setNextItemSelectionUserData(selectionUserData);
    }

    /**
     * Check if the last item's selection state was toggled, inside a multi-select scope. Useful if
     * you need the per-item information before reaching endMultiSelect(), e.g. for rendering. Only
     * toggle events are reported, in order to handle clipping correctly.
     *
     * @return True if the last item's selection was toggled.
     */
    public static boolean isItemToggledSelection() {
        return IkGuiImplMultiSelect.isItemToggledSelection();
    }

    public static boolean selectable(String label, boolean selected) {
        return IkGuiImplMiscWidgets.selectable(label, selected, SelectableFlags.NONE, 0.0f, 0.0f);
    }

    public static boolean selectable(String label, boolean selected, int selectableFlags) {
        return IkGuiImplMiscWidgets.selectable(label, selected, selectableFlags, 0.0f, 0.0f);
    }

    public static boolean selectable(
            String label, boolean selected, int selectableFlags, float width, float height) {
        return IkGuiImplMiscWidgets.selectable(label, selected, selectableFlags, width, height);
    }

    public static boolean selectable(String label, @NonNull IkBoolean selected) {
        return IkGuiImplMiscWidgets.selectable(label, selected, SelectableFlags.NONE, 0.0f, 0.0f);
    }

    public static boolean selectable(
            String label, @NonNull IkBoolean selected, int selectableFlags) {
        return IkGuiImplMiscWidgets.selectable(label, selected, selectableFlags, 0.0f, 0.0f);
    }

    public static boolean selectable(
            String label,
            @NonNull IkBoolean selected,
            int selectableFlags,
            float width,
            float height) {
        return IkGuiImplMiscWidgets.selectable(label, selected, selectableFlags, width, height);
    }

    public static void separator() {
        IkGuiImplLayout.separator();
    }

    /**
     * Text with a horizontal line on either side, used as a section title. The appearance is
     * controlled by the separator text border size, alignment, and padding style variables.
     *
     * @param label The label, anything after "##" is hidden.
     */
    public static void separatorText(@NonNull String label) {
        IkGuiImplLayout.separatorText(label);
    }

    public static void setClipboardText(String text) {
        IkGuiImplUtils.setClipboardText(text);
    }

    public static void setCursorPos(@NonNull Vector2f pos) {
        IkGuiImplUtils.setCursorPos(pos.x, pos.y);
    }

    public static void setCursorPos(float x, float y) {
        IkGuiImplUtils.setCursorPos(x, y);
    }

    public static void setCursorPosX(float x) {
        IkGuiImplUtils.setCursorPosX(x);
    }

    public static void setCursorPosY(float y) {
        IkGuiImplUtils.setCursorPosY(y);
    }

    public static void setCursorScreenPos(@NonNull Vector2f pos) {
        IkGuiImplUtils.setCursorScreenPos(pos.x, pos.y);
    }

    public static void setCursorScreenPos(float x, float y) {
        IkGuiImplUtils.setCursorScreenPos(x, y);
    }

    /**
     * Set the payload every frame, using the fully qualified class name of the payload as the type.
     * Call between beginDragDropSource() and endDragDropSource().
     *
     * @param payload The payload, which must not be null. It is held, not copied.
     * @return True when the payload has been accepted by a target.
     */
    public static boolean setDragDropPayload(@NonNull Object payload) {
        return IkGuiImplDragDrop.setDragDropPayload(payload, Condition.NONE);
    }

    /**
     * Set the payload, using the fully qualified class name of the payload as the type. Call
     * between beginDragDropSource() and endDragDropSource().
     *
     * @param payload The payload, which must not be null. It is held, not copied.
     * @param condition {@link Condition#ALWAYS} to set the payload every frame, or {@link
     *     Condition#ONCE} to set it only when the drag starts.
     * @return True when the payload has been accepted by a target.
     */
    public static boolean setDragDropPayload(
            @NonNull Object payload, @NonNull Condition condition) {
        return IkGuiImplDragDrop.setDragDropPayload(payload, condition);
    }

    /**
     * Set the payload every frame. Call between beginDragDropSource() and endDragDropSource().
     *
     * @param dataType The user-defined type. Strings starting with an underscore are reserved for
     *     IkGui internal types.
     * @param payload The payload, which may be null. It is held, not copied.
     * @return True when the payload has been accepted by a target.
     */
    public static boolean setDragDropPayload(@NonNull String dataType, Object payload) {
        return IkGuiImplDragDrop.setDragDropPayload(dataType, payload, Condition.NONE);
    }

    /**
     * Set the payload. Call between beginDragDropSource() and endDragDropSource().
     *
     * @param dataType The user-defined type. Strings starting with an underscore are reserved for
     *     IkGui internal types.
     * @param payload The payload, which may be null. It is held, not copied.
     * @param condition {@link Condition#ALWAYS} to set the payload every frame, or {@link
     *     Condition#ONCE} to set it only when the drag starts.
     * @return True when the payload has been accepted by a target.
     */
    public static boolean setDragDropPayload(
            @NonNull String dataType, Object payload, @NonNull Condition condition) {
        return IkGuiImplDragDrop.setDragDropPayload(dataType, payload, condition);
    }

    /**
     * Set the current font to use for the application across frames.
     *
     * @param fontPath The path to teh font from the resource folder, which must be loaded.
     * @see #pushFont(String)
     * @see #setFontFallbacks(String...)
     */
    public static void setFont(@NonNull String fontPath) {
        IkGuiImplText.setFont(fontPath, context.fontSize);
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
        IkGuiImplText.setFont(fontPath, size);
    }

    /**
     * Set (overwrite) the list of font fallbacks. Fonts that are not yet loaded will be loaded.
     *
     * @param fontList The list of font names to use, in order from first to last to check for
     *     glyphs.
     * @see Context#fontFallbacks
     */
    public static void setFontFallbacks(@NonNull String... fontList) {
        IkGuiImplText.setFontFallbacks(fontList);
    }

    /**
     * Set the font size to use for the application across frames.
     *
     * @param fontSize The size of the font.
     * @see #pushFont(String, int)
     */
    public static void setFontSize(int fontSize) {
        IkGuiImplText.setFontSize(fontSize);
    }

    public static void setItemDefaultFocus() {
        IkGuiImplUtils.setItemDefaultFocus();
    }

    /**
     * Alter the visibility of the keyboard/gamepad navigation cursor. By default it is shown when
     * using an arrow key, and hidden when clicking with the mouse.
     *
     * @param visible Whether the cursor should be visible.
     */
    public static void setNavCursorVisible(boolean visible) {
        IkGuiImplNav.setNavCursorVisible(visible);
    }

    /**
     * Override io.wantCaptureKeyboard next frame. This is equivalent to setting
     * io.wantCaptureKeyboard after the next newFrame() call.
     *
     * @param wantCaptureKeyboard Whether the application should ignore keyboard inputs.
     */
    public static void setNextFrameWantCaptureKeyboard(boolean wantCaptureKeyboard) {
        context.wantCaptureKeyboardNextFrame = wantCaptureKeyboard ? 1 : 0;
    }

    /**
     * Override io.wantCaptureMouse next frame. This is equivalent to setting io.wantCaptureMouse
     * after the next newFrame() call.
     *
     * @param wantCaptureMouse Whether the application should ignore mouse inputs.
     */
    public static void setNextFrameWantCaptureMouse(boolean wantCaptureMouse) {
        context.wantCaptureMouseNextFrame = wantCaptureMouse ? 1 : 0;
    }

    /**
     * Check if a shortcut was pressed, e.g. shortcut(KeyChord.of(KeyModFlags.CTRL, Key.S)). By
     * default it is routed to the focused window, so multiple windows can use the same shortcut
     * without conflicts, and the active item gets priority.
     *
     * @param keyChord The key chord.
     * @return True if the shortcut was pressed.
     * @see KeyChord
     */
    public static boolean shortcut(int keyChord) {
        return IkGuiImplKeys.shortcut(keyChord, InputFlags.NONE, KeyRoutingData.KEY_OWNER_ANY);
    }

    /**
     * Check if a shortcut was pressed.
     *
     * @param keyChord The key chord.
     * @param inputFlags Input flags, for repeat and routing policy.
     * @return True if the shortcut was pressed.
     * @see KeyChord
     * @see InputFlags
     */
    public static boolean shortcut(int keyChord, int inputFlags) {
        return IkGuiImplKeys.shortcut(keyChord, inputFlags, KeyRoutingData.KEY_OWNER_ANY);
    }

    /**
     * Set a shortcut for the next item, which activates it as if it was clicked.
     *
     * @param keyChord The key chord.
     * @see KeyChord
     */
    public static void setNextItemShortcut(int keyChord) {
        IkGuiImplKeys.setNextItemShortcut(keyChord, InputFlags.NONE);
    }

    /**
     * Set a shortcut for the next item, which activates it as if it was clicked.
     *
     * @param keyChord The key chord.
     * @param inputFlags Input flags, for repeat, routing, and InputFlags.TOOLTIP.
     * @see KeyChord
     * @see InputFlags
     */
    public static void setNextItemShortcut(int keyChord, int inputFlags) {
        IkGuiImplKeys.setNextItemShortcut(keyChord, inputFlags);
    }

    /**
     * Set the key owner to the last item ID if it is hovered or active, so other code doesn't read
     * the key. For example, a widget using the mouse wheel can claim it so the window doesn't
     * scroll.
     *
     * @param key The key.
     * @return True if ownership was set.
     */
    public static boolean setItemKeyOwner(@NonNull Key key) {
        return IkGuiImplKeys.setItemKeyOwner(key, InputFlags.NONE);
    }

    /**
     * Check if a key chord (modifiers + key) was pressed, which requires exactly the modifiers of
     * the chord. This doesn't do any routing or ownership checks, see shortcut() for that.
     *
     * @param keyChord The key chord.
     * @return True if the chord was pressed.
     * @see KeyChord
     */
    public static boolean isKeyChordPressed(int keyChord) {
        return IkGuiImplKeys.isKeyChordPressed(
                keyChord, InputFlags.NONE, KeyRoutingData.KEY_OWNER_ANY);
    }

    /**
     * A human readable name for a key, for debugging.
     *
     * @param key The key.
     * @return The name of the key.
     */
    public static String getKeyName(@NonNull Key key) {
        return KeyChord.getKeyName(key);
    }

    public static void setKeyboardFocusHere() {
        IkGuiImplUtils.setKeyboardFocusHere(0);
    }

    public static void setKeyboardFocusHere(int offset) {
        IkGuiImplUtils.setKeyboardFocusHere(offset);
    }

    public static void setMouseCursor(@NonNull MouseCursor cursor) {
        IkGuiImplUtils.setMouseCursor(cursor);
    }

    /**
     * Set the ID used to store the open state of the next tree node, instead of its item ID. This
     * doesn't depend on the ID stack, so the open state can be queried from anywhere.
     *
     * @param storageID The ID to store the open state under.
     */
    public static void setNextItemStorageID(int storageID) {
        IkGuiImplTrees.setNextItemStorageID(storageID);
    }

    public static void setNextItemOpen(boolean isOpen) {
        IkGuiImplUtils.setNextItemOpen(isOpen, Condition.NONE);
    }

    public static void setNextItemOpen(boolean isOpen, @NonNull Condition condition) {
        IkGuiImplUtils.setNextItemOpen(isOpen, condition);
    }

    /**
     * Allow the next item to be overlapped by a subsequent item. Useful with invisible buttons,
     * selectable, tree nodes covering an area where subsequent items may need to be added.
     */
    public static void setNextItemAllowOverlap() {
        context.nextItemData.itemFlags |= ItemFlags.ALLOW_OVERLAP;
    }

    public static void setNextItemWidth(float width) {
        IkGuiImplUtils.setNextItemWidth(width);
    }

    public static void setNextWindowCollapsed(boolean collapsed) {
        IkGuiImplWindows.setNextWindowCollapsed(collapsed, Condition.NONE);
    }

    public static void setNextWindowCollapsed(boolean collapsed, @NonNull Condition condition) {
        IkGuiImplWindows.setNextWindowCollapsed(collapsed, condition);
    }

    /**
     * Set the dock node ID of the next window.
     *
     * @param id The dock node ID.
     */
    public static void setNextWindowDockID(int id) {
        IkGuiImplWindows.setNextWindowDockID(id, Condition.NONE);
    }

    /**
     * Set the dock node ID of the next window.
     *
     * @param id The dock node ID.
     * @param condition The condition.
     */
    public static void setNextWindowDockID(int id, @NonNull Condition condition) {
        IkGuiImplWindows.setNextWindowDockID(id, condition);
    }

    /**
     * Set the window class of the next window, which controls docking compatibility (windows of
     * different classes can't be docked together, unless allowed by the class).
     *
     * @param windowClass The window class, which is copied.
     */
    public static void setNextWindowClass(@NonNull WindowClass windowClass) {
        IkGuiImplWindows.setNextWindowClass(windowClass);
    }

    public static void setNextWindowFocus() {
        IkGuiImplWindows.setNextWindowFocus();
    }

    /**
     * Set the viewport of the next window.
     *
     * @param viewportID The ID of the viewport.
     */
    public static void setNextWindowViewport(int viewportID) {
        IkGuiImplViewports.setNextWindowViewport(viewportID);
    }

    /**
     * Set the background color alpha of the next window. Helper to easily override the alpha
     * component of {@link ColorType#WINDOW_BACKGROUND}, {@link ColorType#CHILD_BACKGROUND}, and
     * {@link ColorType#POPUP_BACKGROUND}.
     *
     * @param alpha The alpha, between 0 and 1.
     */
    public static void setNextWindowBgAlpha(float alpha) {
        IkGuiImplWindows.setNextWindowBgAlpha(alpha);
    }

    /**
     * Set the next window content size (~ scrollable client area, which enforces the range of
     * scrollbars). Not including window decorations (title bar, menu bar, etc.) nor window padding.
     * Set an axis to 0 to leave it automatic.
     *
     * @param width The content width.
     * @param height The content height.
     */
    public static void setNextWindowContentSize(float width, float height) {
        IkGuiImplWindows.setNextWindowContentSize(width, height);
    }

    /**
     * Set the next window position. Call before {@link #begin(String)}.
     *
     * @param x The x position, in screen coordinates.
     * @param y The y position, in screen coordinates.
     */
    public static void setNextWindowPos(float x, float y) {
        IkGuiImplWindows.setNextWindowPos(x, y, Condition.NONE, 0, 0);
    }

    /**
     * Set the next window position. Call before {@link #begin(String)}.
     *
     * @param x The x position, in screen coordinates.
     * @param y The y position, in screen coordinates.
     * @param condition When to apply the position.
     */
    public static void setNextWindowPos(float x, float y, @NonNull Condition condition) {
        IkGuiImplWindows.setNextWindowPos(x, y, condition, 0, 0);
    }

    /**
     * Set the next window position. Call before {@link #begin(String)}. Use a pivot of (0.5, 0.5)
     * to center on the given point, etc.
     *
     * @param x The x position, in screen coordinates.
     * @param y The y position, in screen coordinates.
     * @param condition When to apply the position.
     * @param pivotX The x pivot, from 0 (left) to 1 (right).
     * @param pivotY The y pivot, from 0 (top) to 1 (bottom).
     */
    public static void setNextWindowPos(
            float x, float y, @NonNull Condition condition, float pivotX, float pivotY) {
        IkGuiImplWindows.setNextWindowPos(x, y, condition, pivotX, pivotY);
    }

    /**
     * Set the next window scrolling value. Use a negative value on an axis to not affect that axis.
     *
     * @param x The horizontal scroll.
     * @param y The vertical scroll.
     */
    public static void setNextWindowScroll(float x, float y) {
        IkGuiImplWindows.setNextWindowScroll(x, y);
    }

    /**
     * Set the next window size. Set an axis to 0 to force an auto-fit on this axis. Call before
     * {@link #begin(String)}.
     *
     * @param x The width.
     * @param y The height.
     */
    public static void setNextWindowSize(float x, float y) {
        IkGuiImplWindows.setNextWindowSize(x, y, Condition.NONE);
    }

    /**
     * Set the next window size. Set an axis to 0 to force an auto-fit on this axis. Call before
     * {@link #begin(String)}.
     *
     * @param x The width.
     * @param y The height.
     * @param condition When to apply the size.
     */
    public static void setNextWindowSize(float x, float y, @NonNull Condition condition) {
        IkGuiImplWindows.setNextWindowSize(x, y, condition);
    }

    /**
     * Set the next window size limits. Use 0 or {@link Float#MAX_VALUE} if you don't want limits.
     * Use -1 for both min and max of the same axis to preserve the current size (which itself is a
     * constraint).
     *
     * @param minWidth The minimum width.
     * @param minHeight The minimum height.
     * @param maxWidth The maximum width.
     * @param maxHeight The maximum height.
     */
    public static void setNextWindowSizeConstraints(
            float minWidth, float minHeight, float maxWidth, float maxHeight) {
        IkGuiImplWindows.setNextWindowSizeConstraints(minWidth, minHeight, maxWidth, maxHeight);
    }

    /**
     * Set the size limits of the next window, with a callback for programmatic constraints. Use 0
     * or Float.MAX_VALUE for no limit, and -1 for both the min and max of an axis to keep the
     * current size. The callback is called after the limits are applied, and may change the desired
     * size, e.g. to keep an aspect ratio.
     *
     * @param minWidth The minimum width.
     * @param minHeight The minimum height.
     * @param maxWidth The maximum width.
     * @param maxHeight The maximum height.
     * @param callback The custom constraint, may be null.
     */
    public static void setNextWindowSizeConstraints(
            float minWidth,
            float minHeight,
            float maxWidth,
            float maxHeight,
            Consumer<SizeCallbackData> callback) {
        IkGuiImplWindows.setNextWindowSizeConstraints(
                minWidth, minHeight, maxWidth, maxHeight, callback);
    }

    /**
     * Adjust the scrolling amount to make the given position visible. Generally {@link
     * #getCursorStartPos()} + offset to compute a valid position.
     *
     * @param localX The position in window local coordinates.
     */
    public static void setScrollFromPosX(float localX) {
        IkGuiImplUtils.setScrollFromPosX(localX, 0.5f);
    }

    /**
     * Adjust the scrolling amount to make the given position visible. Generally {@link
     * #getCursorStartPos()} + offset to compute a valid position.
     *
     * @param localX The position in window local coordinates.
     * @param centerXRatio 0 for the left of the window, 0.5 for the center, 1 for the right.
     */
    public static void setScrollFromPosX(float localX, float centerXRatio) {
        IkGuiImplUtils.setScrollFromPosX(localX, centerXRatio);
    }

    /**
     * Adjust the scrolling amount to make the given position visible. Generally {@link
     * #getCursorStartPos()} + offset to compute a valid position.
     *
     * @param localY The position in window local coordinates.
     */
    public static void setScrollFromPosY(float localY) {
        IkGuiImplUtils.setScrollFromPosY(localY, 0.5f);
    }

    /**
     * Adjust the scrolling amount to make the given position visible. Generally {@link
     * #getCursorStartPos()} + offset to compute a valid position.
     *
     * @param localY The position in window local coordinates.
     * @param centerYRatio 0 for the top of the window, 0.5 for the center, 1 for the bottom.
     */
    public static void setScrollFromPosY(float localY, float centerYRatio) {
        IkGuiImplUtils.setScrollFromPosY(localY, centerYRatio);
    }

    public static void setScrollHereX() {
        IkGuiImplUtils.setScrollHereX(0.5f);
    }

    public static void setScrollHereX(float centerXRatio) {
        IkGuiImplUtils.setScrollHereX(centerXRatio);
    }

    public static void setScrollHereY() {
        IkGuiImplUtils.setScrollHereY(0.5f);
    }

    public static void setScrollHereY(float centerYRatio) {
        IkGuiImplUtils.setScrollHereY(centerYRatio);
    }

    public static void setScrollX(float x) {
        IkGuiImplUtils.setScrollX(x);
    }

    public static void setScrollY(float y) {
        IkGuiImplUtils.setScrollY(y);
    }

    public static void setTabItemClosed(String label) {
        IkGuiImplTabs.setTabItemClosed(label);
    }

    /**
     * Set a text-only tooltip if the preceding item was hovered. Overrides any previous call to
     * setTooltip().
     *
     * @param text The text of the tooltip.
     */
    public static void setItemTooltip(@NonNull String text) {
        IkGuiImplPopups.setItemTooltip(text);
    }

    /**
     * Set a text-only tooltip, often used after an isItemHovered() check. Overrides any previous
     * call to setTooltip().
     *
     * @param text The text of the tooltip.
     */
    public static void setTooltip(@NonNull String text) {
        IkGuiImplPopups.setTooltip(text);
    }

    public static void setWindowCollapsed(boolean collapsed) {
        IkGuiImplWindows.setWindowCollapsed(collapsed, Condition.NONE);
    }

    public static void setWindowCollapsed(boolean collapsed, @NonNull Condition condition) {
        IkGuiImplWindows.setWindowCollapsed(collapsed, condition);
    }

    public static void setWindowFocus() {
        IkGuiImplWindows.setWindowFocus();
    }

    public static void setWindowPos(@NonNull Window window, @NonNull Vector2f position) {
        IkGuiImplWindows.setWindowPos(window, position.x, position.y, Condition.NONE);
    }

    public static void setWindowPos(
            @NonNull Window window, @NonNull Vector2f position, @NonNull Condition condition) {
        IkGuiImplWindows.setWindowPos(window, position.x, position.y, condition);
    }

    public static void setWindowPos(@NonNull Window window, float x, float y) {
        IkGuiImplWindows.setWindowPos(window, x, y, Condition.NONE);
    }

    public static void setWindowPos(
            @NonNull Window window, float x, float y, @NonNull Condition condition) {
        IkGuiImplWindows.setWindowPos(window, x, y, condition);
    }

    public static void setWindowPos(@NonNull Vector2f position) {
        IkGuiImplWindows.setWindowPos(
                context.windowCurrent, position.x, position.y, Condition.NONE);
    }

    public static void setWindowPos(@NonNull Vector2f position, @NonNull Condition condition) {
        IkGuiImplWindows.setWindowPos(context.windowCurrent, position.x, position.y, condition);
    }

    public static void setWindowPos(float x, float y) {
        IkGuiImplWindows.setWindowPos(context.windowCurrent, x, y, Condition.NONE);
    }

    public static void setWindowPos(float x, float y, @NonNull Condition condition) {
        IkGuiImplWindows.setWindowPos(context.windowCurrent, x, y, condition);
    }

    /**
     * Set a named window to be collapsed or not.
     *
     * @param name The name of the window.
     * @param collapsed Whether it should be collapsed.
     * @param condition When to apply the change.
     */
    public static void setWindowCollapsed(
            @NonNull String name, boolean collapsed, @NonNull Condition condition) {
        Window window = IkGuiInternal.findWindowByName(name);
        if (window != null) {
            IkGuiImplWindows.setWindowCollapsed(window, collapsed, condition);
        }
    }

    /**
     * Set a named window to be focused. Use null to remove focus.
     *
     * @param name The name of the window.
     */
    public static void setWindowFocus(String name) {
        IkGuiImplWindows.setWindowFocus(name);
    }

    /**
     * Set the position of a named window.
     *
     * @param name The name of the window.
     * @param x The x position, in screen coordinates.
     * @param y The y position, in screen coordinates.
     * @param condition When to apply the change.
     */
    public static void setWindowPos(
            @NonNull String name, float x, float y, @NonNull Condition condition) {
        Window window = IkGuiInternal.findWindowByName(name);
        if (window != null) {
            IkGuiImplWindows.setWindowPos(window, x, y, condition);
        }
    }

    /**
     * Set the size of a named window. Set an axis to 0 to force an auto-fit on that axis.
     *
     * @param name The name of the window.
     * @param x The width.
     * @param y The height.
     * @param condition When to apply the change.
     */
    public static void setWindowSize(
            @NonNull String name, float x, float y, @NonNull Condition condition) {
        Window window = IkGuiInternal.findWindowByName(name);
        if (window != null) {
            IkGuiImplWindows.setWindowSize(window, x, y, condition);
        }
    }

    public static void setWindowSize(float x, float y) {
        IkGuiImplWindows.setWindowSize(context.windowCurrent, x, y, Condition.NONE);
    }

    public static void setWindowSize(float x, float y, @NonNull Condition condition) {
        IkGuiImplWindows.setWindowSize(context.windowCurrent, x, y, condition);
    }

    /**
     * Show the debug log window, which displays events recorded by the library, like changes to
     * focus, popups, and docking.
     */
    public static void showDebugLogWindow() {
        IkGuiImplDebugTools.showDebugLogWindow(null);
    }

    /**
     * Show the debug log window, which displays events recorded by the library, like changes to
     * focus, popups, and docking.
     *
     * @param open The open state of the window, may be null.
     */
    public static void showDebugLogWindow(IkBoolean open) {
        IkGuiImplDebugTools.showDebugLogWindow(open);
    }

    /**
     * Show the style editor, which edits the current style. A copy of the style when the editor was
     * first shown is used as the reference to compare to and revert to.
     */
    public static void showStyleEditor() {
        IkGuiDemo.showStyleEditor(null);
    }

    /**
     * Show the style editor, which edits the current style.
     *
     * @param ref The reference style to compare to, revert to and save to, or null to use an
     *     internal copy.
     */
    public static void showStyleEditor(Style ref) {
        IkGuiDemo.showStyleEditor(ref);
    }

    /**
     * A combo to pick one of the default color themes.
     *
     * @param label The label of the combo.
     * @return True if a theme was picked.
     */
    public static boolean showStyleSelector(String label) {
        return IkGuiDemo.showStyleSelector(label);
    }

    /**
     * A combo to pick the current font from the loaded fonts.
     *
     * @param label The label of the combo.
     */
    public static void showFontSelector(String label) {
        IkGuiImplMetrics.showFontSelector(label);
    }

    /**
     * Show the loaded fonts and the font atlas, for debugging.
     *
     * @param atlas The font atlas.
     */
    public static void showFontAtlas(@NonNull FontAtlas atlas) {
        IkGuiImplMetrics.showFontAtlas(atlas);
    }

    /** The version of Dear ImGui that IkGui is ported from, which is the docking branch. */
    public static final String DEAR_IMGUI_VERSION = "1.93.0 WIP";

    /** The version of Dear ImGui that IkGui is ported from, as a number for comparisons. */
    public static final int DEAR_IMGUI_VERSION_NUM = 19_297;

    /**
     * Show the About window, with credits and build/system information. It has no close button.
     *
     * @see #showAboutWindow(IkBoolean)
     */
    public static void showAboutWindow() {
        IkGuiDemo.showAboutWindow(null);
    }

    /**
     * Show the About window, with credits and build/system information.
     *
     * @param open If not null, a close button is shown that sets this to false.
     */
    public static void showAboutWindow(IkBoolean open) {
        IkGuiDemo.showAboutWindow(open);
    }

    /**
     * Add a block of help text about how to use the GUI as an end user, with the mouse, keyboard
     * and gamepad. This is not a window, so it can be added to any window.
     */
    public static void showUserGuide() {
        IkGuiDemo.showUserGuide();
    }

    public static void showDemoWindow() {
        IkGuiDemo.showDemoWindow(null);
    }

    public static void showDemoWindow(final IkBoolean open) {
        IkGuiDemo.showDemoWindow(open);
    }

    /**
     * Show the ID stack tool window, which shows how the ID of the hovered item was built from the
     * ID stack.
     */
    public static void showIDStackToolWindow() {
        IkGuiImplDebugTools.showIDStackToolWindow(null);
    }

    /**
     * Show the ID stack tool window, which shows how the ID of the hovered item was built from the
     * ID stack.
     *
     * @param open The open state of the window, may be null.
     */
    public static void showIDStackToolWindow(IkBoolean open) {
        IkGuiImplDebugTools.showIDStackToolWindow(open);
    }

    /**
     * Show the metrics/debugger window, which displays the internal state of the library: windows,
     * draw lists, viewports, fonts, tables, docking, settings, inputs, and more.
     */
    public static void showMetricsWindow() {
        IkGuiImplMetrics.showMetricsWindow(null);
    }

    /**
     * Show the metrics/debugger window, which displays the internal state of the library: windows,
     * draw lists, viewports, fonts, tables, docking, settings, inputs, and more.
     *
     * @param open The open state of the window, may be null.
     */
    public static void showMetricsWindow(IkBoolean open) {
        IkGuiImplMetrics.showMetricsWindow(open);
    }

    public static boolean sliderAngle(String label, float[] value) {
        return IkGuiImplSliders.sliderAngle(
                label,
                value,
                -360.0f,
                360.0f,
                IkGuiImplMiscWidgets.SLIDER_ANGLE_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean sliderAngle(
            String label, float[] value, float minDegrees, float maxDegrees) {
        return IkGuiImplSliders.sliderAngle(
                label,
                value,
                minDegrees,
                maxDegrees,
                IkGuiImplMiscWidgets.SLIDER_ANGLE_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean sliderAngle(
            String label, float[] value, float minDegrees, float maxDegrees, String format) {
        return IkGuiImplSliders.sliderAngle(
                label, value, minDegrees, maxDegrees, format, SliderFlags.NONE);
    }

    public static boolean sliderAngle(
            String label,
            float[] value,
            float minDegrees,
            float maxDegrees,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.sliderAngle(
                label, value, minDegrees, maxDegrees, format, sliderFlags);
    }

    public static boolean sliderFloat(String label, float[] value, float min, float max) {
        return IkGuiImplSliders.sliderFloat(
                label,
                value,
                min,
                max,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean sliderFloat(
            String label, float[] value, float min, float max, String format) {
        return IkGuiImplSliders.sliderFloat(label, value, min, max, format, SliderFlags.NONE);
    }

    public static boolean sliderFloat(
            String label, float[] value, float min, float max, String format, int sliderFlags) {
        return IkGuiImplSliders.sliderFloat(label, value, min, max, format, sliderFlags);
    }

    public static boolean sliderFloat2(String label, float[] values, float min, float max) {
        return IkGuiImplSliders.sliderFloat2(
                label,
                values,
                min,
                max,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean sliderFloat2(
            String label, float[] values, float min, float max, String format) {
        return IkGuiImplSliders.sliderFloat2(label, values, min, max, format, SliderFlags.NONE);
    }

    public static boolean sliderFloat2(
            String label, float[] values, float min, float max, String format, int sliderFlags) {
        return IkGuiImplSliders.sliderFloat2(label, values, min, max, format, sliderFlags);
    }

    public static boolean sliderFloat3(String label, float[] values, float min, float max) {
        return IkGuiImplSliders.sliderFloat3(
                label,
                values,
                min,
                max,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean sliderFloat3(
            String label, float[] values, float min, float max, String format) {
        return IkGuiImplSliders.sliderFloat3(label, values, min, max, format, SliderFlags.NONE);
    }

    public static boolean sliderFloat3(
            String label, float[] values, float min, float max, String format, int sliderFlags) {
        return IkGuiImplSliders.sliderFloat3(label, values, min, max, format, sliderFlags);
    }

    public static boolean sliderFloat4(String label, float[] values, float min, float max) {
        return IkGuiImplSliders.sliderFloat4(
                label,
                values,
                min,
                max,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean sliderFloat4(
            String label, float[] values, float min, float max, String format) {
        return IkGuiImplSliders.sliderFloat4(label, values, min, max, format, SliderFlags.NONE);
    }

    public static boolean sliderFloat4(
            String label, float[] values, float min, float max, String format, int sliderFlags) {
        return IkGuiImplSliders.sliderFloat4(label, values, min, max, format, sliderFlags);
    }

    public static boolean sliderInt(String label, int[] value, int min, int max) {
        return IkGuiImplSliders.sliderInt(
                label, value, min, max, IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT, SliderFlags.NONE);
    }

    public static boolean sliderInt(String label, int[] value, int min, int max, String format) {
        return IkGuiImplSliders.sliderInt(label, value, min, max, format, SliderFlags.NONE);
    }

    public static boolean sliderInt(
            String label, int[] value, int min, int max, String format, int sliderFlags) {
        return IkGuiImplSliders.sliderInt(label, value, min, max, format, sliderFlags);
    }

    public static boolean sliderInt2(String label, int[] values, int min, int max) {
        return IkGuiImplSliders.sliderInt2(
                label, values, min, max, IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT, SliderFlags.NONE);
    }

    public static boolean sliderInt2(String label, int[] values, int min, int max, String format) {
        return IkGuiImplSliders.sliderInt2(label, values, min, max, format, SliderFlags.NONE);
    }

    public static boolean sliderInt2(
            String label, int[] values, int min, int max, String format, int sliderFlags) {
        return IkGuiImplSliders.sliderInt2(label, values, min, max, format, sliderFlags);
    }

    public static boolean sliderInt3(String label, int[] values, int min, int max) {
        return IkGuiImplSliders.sliderInt3(
                label, values, min, max, IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT, SliderFlags.NONE);
    }

    public static boolean sliderInt3(String label, int[] values, int min, int max, String format) {
        return IkGuiImplSliders.sliderInt3(label, values, min, max, format, SliderFlags.NONE);
    }

    public static boolean sliderInt3(
            String label, int[] values, int min, int max, String format, int sliderFlags) {
        return IkGuiImplSliders.sliderInt3(label, values, min, max, format, sliderFlags);
    }

    public static boolean sliderInt4(String label, int[] values, int min, int max) {
        return IkGuiImplSliders.sliderInt3(
                label, values, min, max, IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT, SliderFlags.NONE);
    }

    public static boolean sliderInt4(String label, int[] values, int min, int max, String format) {
        return IkGuiImplSliders.sliderInt3(label, values, min, max, format, SliderFlags.NONE);
    }

    public static boolean sliderInt4(
            String label, int[] values, int min, int max, String format, int sliderFlags) {
        return IkGuiImplSliders.sliderInt3(label, values, min, max, format, sliderFlags);
    }

    public static boolean sliderScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number min,
            Number max) {
        return IkGuiImplSliders.sliderScalar(
                label, dataType, data, min, max, null, SliderFlags.NONE);
    }

    public static boolean sliderScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number min,
            Number max,
            String format) {
        return IkGuiImplSliders.sliderScalar(
                label, dataType, data, min, max, format, SliderFlags.NONE);
    }

    /**
     * A slider for any data type. The data is an array of the data type or one of the Ik* boxes,
     * and the limits are boxed numbers, which are both required, e.g. {@code sliderScalar("x",
     * SliderDataType.LONG, value, 0L, 100L)}. For 64-bit and floating point types the limits must
     * be within half the range of the type, use a drag for larger ranges.
     *
     * @param label The label, which is also used for the ID.
     * @param dataType The data type.
     * @param data The data.
     * @param min The minimum value.
     * @param max The maximum value.
     * @param format The display format, null for the default for the data type.
     * @param sliderFlags Slider flags.
     * @return True if the value changed.
     */
    public static boolean sliderScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number min,
            Number max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.sliderScalar(label, dataType, data, min, max, format, sliderFlags);
    }

    public static boolean sliderScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            Number min,
            Number max) {
        return IkGuiImplSliders.sliderScalarN(
                label, dataType, data, components, min, max, null, SliderFlags.NONE);
    }

    public static boolean sliderScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            Number min,
            Number max,
            String format) {
        return IkGuiImplSliders.sliderScalarN(
                label, dataType, data, components, min, max, format, SliderFlags.NONE);
    }

    public static boolean sliderScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            Number min,
            Number max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.sliderScalarN(
                label, dataType, data, components, min, max, format, sliderFlags);
    }

    public static boolean smallButton(String text) {
        return IkGuiImplButtons.smallButton(text);
    }

    /** Adds vertical space, equal to the current style's y spacing. */
    public static void spacing() {
        IkGuiImplLayout.spacing();
    }

    public static boolean tabItemButton(String label) {
        return IkGuiImplTabs.tabItemButton(label, TabItemFlags.NONE);
    }

    public static boolean tabItemButton(String label, int tabItemFlags) {
        return IkGuiImplTabs.tabItemButton(label, tabItemFlags);
    }

    /**
     * Submit a row with angled headers for every column with the TableColumnFlags.ANGLED_HEADER
     * flag. Must be the first row.
     */
    public static void tableAngledHeadersRow() {
        IkGuiImplTableHeaders.tableAngledHeadersRow();
    }

    /**
     * The number of columns in the current table.
     *
     * @return The number of columns passed to beginTable().
     */
    public static int tableGetColumnCount() {
        return IkGuiImplTables.tableGetColumnCount();
    }

    /**
     * The flags of the current column, which can be used to query the enabled/visible/sorted/
     * hovered status flags.
     *
     * @return The column flags.
     * @see TableColumnFlags
     */
    public static int tableGetColumnFlags() {
        return IkGuiImplTables.tableGetColumnFlags(-1);
    }

    /**
     * The flags of a column, which can be used to query the enabled/visible/sorted/hovered status
     * flags.
     *
     * @param index The column index, or -1 for the current column.
     * @return The column flags.
     * @see TableColumnFlags
     */
    public static int tableGetColumnFlags(int index) {
        return IkGuiImplTables.tableGetColumnFlags(index);
    }

    /**
     * The current column index.
     *
     * @return The current column index.
     */
    public static int tableGetColumnIndex() {
        return IkGuiImplTables.tableGetColumnIndex();
    }

    /**
     * The name of the current column.
     *
     * @return The name, or "" if the column didn't have a name declared by tableSetupColumn().
     */
    public static String tableGetColumnName() {
        return IkGuiImplTables.tableGetColumnName(-1);
    }

    /**
     * The name of a column.
     *
     * @param index The column index, or -1 for the current column.
     * @return The name, or "" if the column didn't have a name declared by tableSetupColumn().
     */
    public static String tableGetColumnName(int index) {
        return IkGuiImplTables.tableGetColumnName(index);
    }

    /**
     * The hovered column. You can also use tableGetColumnFlags() and TableColumnFlags.IS_HOVERED.
     *
     * @return The hovered column, -1 when the table is not hovered, or the column count if the
     *     unused space to the right of the visible columns is hovered.
     */
    public static int tableGetHoveredColumn() {
        return IkGuiImplTables.tableGetHoveredColumn();
    }

    /**
     * The current row index, header rows are accounted for.
     *
     * @return The current row index.
     */
    public static int tableGetRowIndex() {
        return IkGuiImplTables.tableGetRowIndex();
    }

    /**
     * Fetch the latest sort specs for the current table. When specsDirty is true you should sort
     * your data, then clear specsDirty. Don't hold on to this over multiple frames or past a
     * subsequent call to beginTable().
     *
     * @return The sort specs, or null if the table is not sortable.
     */
    public static TableSortSpecs tableGetSortSpecs() {
        return IkGuiImplTables.tableGetSortSpecs();
    }

    /**
     * Submit one header cell manually (rarely used).
     *
     * @param label The label, which is also used for the ID.
     */
    public static void tableHeader(String label) {
        IkGuiImplTableHeaders.tableHeader(label);
    }

    /**
     * Submit a row with header cells, based on the data provided to tableSetupColumn(). This also
     * submits the context menu.
     */
    public static void tableHeadersRow() {
        IkGuiImplTableHeaders.tableHeadersRow();
    }

    /**
     * Append into the next column, or the first column of the next row if currently in the last
     * column.
     *
     * @return True when the column is visible.
     */
    public static boolean tableNextColumn() {
        return IkGuiImplTables.tableNextColumn();
    }

    /** Append into the first cell of a new row. */
    public static void tableNextRow() {
        IkGuiImplTables.tableNextRow(TableRowFlags.NONE, 0.0f);
    }

    /**
     * Append into the first cell of a new row.
     *
     * @param tableRowFlags The row flags.
     * @see TableRowFlags
     */
    public static void tableNextRow(int tableRowFlags) {
        IkGuiImplTables.tableNextRow(tableRowFlags, 0.0f);
    }

    /**
     * Append into the first cell of a new row.
     *
     * @param tableRowFlags The row flags.
     * @param minHeight The minimum row height, including the top and bottom cell padding.
     * @see TableRowFlags
     */
    public static void tableNextRow(int tableRowFlags, float minHeight) {
        IkGuiImplTables.tableNextRow(tableRowFlags, minHeight);
    }

    /**
     * Change the color of the current cell or row.
     *
     * @param target What to change the color of.
     * @param color The color.
     */
    public static void tableSetBackgroundColor(@NonNull TableBackgroundTarget target, int color) {
        IkGuiImplTables.tableSetBackgroundColor(target, color, -1);
    }

    /**
     * Change the color of a cell or row.
     *
     * @param target What to change the color of.
     * @param color The color.
     * @param columnIndex The column index for cell backgrounds, or -1 for the current column.
     */
    public static void tableSetBackgroundColor(
            @NonNull TableBackgroundTarget target, int color, int columnIndex) {
        IkGuiImplTables.tableSetBackgroundColor(target, color, columnIndex);
    }

    /**
     * Append into the specified column.
     *
     * @param index The column index.
     * @return True when the column is visible.
     */
    public static boolean tableSetColumnIndex(int index) {
        return IkGuiImplTables.tableSetColumnIndex(index);
    }

    /**
     * Change the user accessible enabled/disabled state of a column. Set to false to hide the
     * column. Requires TableFlags.HIDEABLE. The user can use the context menu to change this
     * themselves.
     *
     * @param index The column index, or -1 for the current column.
     * @param enabled Whether the column should be enabled.
     */
    public static void tableSetColumnEnabled(int index, boolean enabled) {
        IkGuiImplTables.tableSetColumnEnabled(index, enabled);
    }

    /**
     * Set up a column. Call before the first row.
     *
     * @param label The column label, may be null.
     */
    public static void tableSetupColumn(String label) {
        IkGuiImplTables.tableSetupColumn(label, TableColumnFlags.NONE, 0.0f, 0);
    }

    /**
     * Set up a column. Call before the first row.
     *
     * @param label The column label, may be null.
     * @param tableColumnFlags The column flags.
     * @see TableColumnFlags
     */
    public static void tableSetupColumn(String label, int tableColumnFlags) {
        IkGuiImplTables.tableSetupColumn(label, tableColumnFlags, 0.0f, 0);
    }

    /**
     * Set up a column. Call before the first row.
     *
     * @param label The column label, may be null.
     * @param tableColumnFlags The column flags.
     * @param widthOrWeight The initial width (for fixed columns) or weight (for stretch columns),
     *     ignored if 0 or less.
     * @see TableColumnFlags
     */
    public static void tableSetupColumn(String label, int tableColumnFlags, float widthOrWeight) {
        IkGuiImplTables.tableSetupColumn(label, tableColumnFlags, widthOrWeight, 0);
    }

    /**
     * Set up a column. Call before the first row.
     *
     * @param label The column label, may be null.
     * @param tableColumnFlags The column flags.
     * @param widthOrWeight The initial width (for fixed columns) or weight (for stretch columns),
     *     ignored if 0 or less.
     * @param userID User data, which is returned in the sort specs.
     * @see TableColumnFlags
     */
    public static void tableSetupColumn(
            String label, int tableColumnFlags, float widthOrWeight, int userID) {
        IkGuiImplTables.tableSetupColumn(label, tableColumnFlags, widthOrWeight, userID);
    }

    /**
     * Lock columns/rows so they stay visible when scrolled.
     *
     * @param columns The number of columns to freeze.
     * @param rows The number of rows to freeze.
     */
    public static void tableSetupScrollFreeze(int columns, int rows) {
        IkGuiImplTables.tableSetupScrollFreeze(columns, rows);
    }

    public static void text(@NonNull String text) {
        IkGuiImplText.text(text);
    }

    /**
     * Hyperlink text, which acts like a button.
     *
     * @param label The label, which is also used for the ID.
     * @return True if the link was clicked.
     */
    public static boolean textLink(@NonNull String label) {
        return IkGuiImplText.textLink(label);
    }

    /**
     * Hyperlink text, which opens the label as a URL or file when clicked.
     *
     * @param url The URL, which is also the label and ID.
     * @return True if the link was clicked.
     * @see PlatformIO#openInShellFunction
     */
    public static boolean textLinkOpenURL(@NonNull String url) {
        return IkGuiImplText.textLinkOpenURL(url, null);
    }

    /**
     * Hyperlink text, which opens a URL or file when clicked.
     *
     * @param label The label, which is also used for the ID.
     * @param url The URL to open, or null to use the label.
     * @return True if the link was clicked.
     * @see PlatformIO#openInShellFunction
     */
    public static boolean textLinkOpenURL(@NonNull String label, String url) {
        return IkGuiImplText.textLinkOpenURL(label, url);
    }

    public static void textColored(float r, float g, float b, float a, @NonNull String text) {
        IkGuiImplText.textColored(r, g, b, a, text);
    }

    public static void textColored(int r, int g, int b, int a, @NonNull String text) {
        IkGuiImplText.textColored(r, g, b, a, text);
    }

    public static void textColored(int color, @NonNull String text) {
        IkGuiImplText.textColored(color, text);
    }

    public static void textDisabled(@NonNull String text) {
        IkGuiImplText.textDisabled(text);
    }

    public static void textUnformatted(@NonNull String text) {
        IkGuiImplText.textUnformatted(text);
    }

    public static void textWrapped(@NonNull String text) {
        IkGuiImplText.textWrapped(text);
    }

    public static boolean treeNode(int id, String format) {
        return IkGuiImplTrees.treeNode(id, format);
    }

    public static boolean treeNode(String label) {
        return IkGuiImplTrees.treeNode(label);
    }

    /**
     * A tree node with a string ID and separate display text.
     *
     * @param stringID The string used for the ID.
     * @param text The text to display.
     * @return True if the node is open, in which case treePop() needs to be called.
     */
    public static boolean treeNode(String stringID, String text) {
        return IkGuiImplTrees.treeNodeEx(stringID, TreeNodeFlags.NONE, text);
    }

    public static boolean treeNodeEx(int id, int treeNodeFlags, String format) {
        return IkGuiImplTrees.treeNodeEx(id, treeNodeFlags, format);
    }

    public static boolean treeNodeEx(String label) {
        return IkGuiImplTrees.treeNodeEx(label, TreeNodeFlags.NONE);
    }

    public static boolean treeNodeEx(String label, int treeNodeFlags) {
        return IkGuiImplTrees.treeNodeEx(label, treeNodeFlags);
    }

    /**
     * A tree node with a string ID, separate display text, and flags.
     *
     * @param stringID The string used for the ID.
     * @param treeNodeFlags Flags for the tree node.
     * @param text The text to display.
     * @return True if the node is open, in which case treePop() needs to be called (unless {@link
     *     TreeNodeFlags#NO_TREE_PUSH_ON_OPEN} is set).
     */
    public static boolean treeNodeEx(String stringID, int treeNodeFlags, String text) {
        return IkGuiImplTrees.treeNodeEx(stringID, treeNodeFlags, text);
    }

    public static void treePop() {
        IkGuiImplTrees.treePop();
    }

    public static void treePush() {
        IkGuiImplTrees.treePush();
    }

    public static void treePush(int id) {
        IkGuiImplTrees.treePush(id);
    }

    public static void treePush(String stringID) {
        IkGuiImplTrees.treePush(stringID);
    }

    /**
     * Create, update, and destroy platform windows to match each active viewport. Call in the main
     * loop after render(), when multiple viewports are enabled. This handles the creation and
     * updates of all OS windows using the callbacks in the platform IO.
     */
    public static void updatePlatformWindows() {
        IkGuiImplViewports.updatePlatformWindows();
    }

    public static void unindent() {
        IkGuiImplLayout.unindent(0.0f);
    }

    public static void unindent(float width) {
        IkGuiImplLayout.unindent(width);
    }

    public static void value(String prefix, boolean value) {
        IkGuiImplText.value(prefix, value);
    }

    public static void value(String prefix, float value) {
        IkGuiImplText.value(prefix, value);
    }

    public static void value(String prefix, float value, String format) {
        IkGuiImplText.value(prefix, value, format);
    }

    public static void value(String prefix, int value) {
        IkGuiImplText.value(prefix, value);
    }

    public static void value(String prefix, long value) {
        IkGuiImplText.value(prefix, value);
    }

    public static boolean vSliderFloat(
            String label, float width, float height, @NonNull IkFloat value, float min, float max) {
        return IkGuiImplSliders.vSliderFloat(
                label,
                width,
                height,
                value.getData(),
                min,
                max,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean vSliderFloat(
            String label, float width, float height, float[] value, float min, float max) {
        return IkGuiImplSliders.vSliderFloat(
                label,
                width,
                height,
                value,
                min,
                max,
                IkGuiImplMiscWidgets.FLOAT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean vSliderFloat(
            String label,
            float width,
            float height,
            @NonNull IkFloat value,
            float min,
            float max,
            String format) {
        return IkGuiImplSliders.vSliderFloat(
                label, width, height, value.getData(), min, max, format, SliderFlags.NONE);
    }

    public static boolean vSliderFloat(
            String label,
            float width,
            float height,
            float[] value,
            float min,
            float max,
            String format) {
        return IkGuiImplSliders.vSliderFloat(
                label, width, height, value, min, max, format, SliderFlags.NONE);
    }

    public static boolean vSliderFloat(
            String label,
            float width,
            float height,
            @NonNull IkFloat value,
            float min,
            float max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.vSliderFloat(
                label, width, height, value.getData(), min, max, format, sliderFlags);
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
        return IkGuiImplSliders.vSliderFloat(
                label, width, height, value, min, max, format, sliderFlags);
    }

    public static boolean vSliderInt(
            String label, float width, float height, @NonNull IkInt value, int min, int max) {
        return IkGuiImplSliders.vSliderInt(
                label,
                width,
                height,
                value.getData(),
                min,
                max,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean vSliderInt(
            String label, float width, float height, int[] value, int min, int max) {
        return IkGuiImplSliders.vSliderInt(
                label,
                width,
                height,
                value,
                min,
                max,
                IkGuiImplMiscWidgets.INT_DEFAULT_FORMAT,
                SliderFlags.NONE);
    }

    public static boolean vSliderInt(
            String label,
            float width,
            float height,
            @NonNull IkInt value,
            int min,
            int max,
            String format) {
        return IkGuiImplSliders.vSliderInt(
                label, width, height, value.getData(), min, max, format, SliderFlags.NONE);
    }

    public static boolean vSliderInt(
            String label, float width, float height, int[] value, int min, int max, String format) {
        return IkGuiImplSliders.vSliderInt(
                label, width, height, value, min, max, format, SliderFlags.NONE);
    }

    public static boolean vSliderInt(
            String label,
            float width,
            float height,
            @NonNull IkInt value,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.vSliderInt(
                label, width, height, value.getData(), min, max, format, sliderFlags);
    }

    public static boolean vSliderInt(
            String label,
            float width,
            float height,
            int[] value,
            int min,
            int max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.vSliderInt(
                label, width, height, value, min, max, format, sliderFlags);
    }

    public static boolean vSliderScalar(
            String label,
            @NonNull Vector2f size,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number min,
            Number max) {
        return IkGuiImplSliders.vSliderScalar(
                label, size.x, size.y, dataType, data, min, max, null, SliderFlags.NONE);
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number min,
            Number max) {
        return IkGuiImplSliders.vSliderScalar(
                label, width, height, dataType, data, min, max, null, SliderFlags.NONE);
    }

    public static boolean vSliderScalar(
            String label,
            @NonNull Vector2f size,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number min,
            Number max,
            String format) {
        return IkGuiImplSliders.vSliderScalar(
                label, size.x, size.y, dataType, data, min, max, format, SliderFlags.NONE);
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number min,
            Number max,
            String format) {
        return IkGuiImplSliders.vSliderScalar(
                label, width, height, dataType, data, min, max, format, SliderFlags.NONE);
    }

    public static boolean vSliderScalar(
            String label,
            @NonNull Vector2f size,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number min,
            Number max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.vSliderScalar(
                label, size.x, size.y, dataType, data, min, max, format, sliderFlags);
    }

    public static boolean vSliderScalar(
            String label,
            float width,
            float height,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            Number min,
            Number max,
            String format,
            int sliderFlags) {
        return IkGuiImplSliders.vSliderScalar(
                label, width, height, dataType, data, min, max, format, sliderFlags);
    }

    /** Private constructor so this is not instantiated. */
    private IkGui() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
