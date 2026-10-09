package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.StyleVariables;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.enums.Direction;
import com.ikalagaming.graphics.gui.flags.ButtonFlags;
import com.ikalagaming.graphics.gui.flags.ItemFlags;
import com.ikalagaming.graphics.gui.flags.NavRenderCursorFlags;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import org.joml.Vector2f;

class IkGuiImplButtons {
    static Context context;

    /**
     * A square button with an arrow shape.
     *
     * @param id The string ID of the button.
     * @param direction The direction the arrow points.
     * @return True if the button was pressed.
     */
    public static boolean arrowButton(@NonNull String id, @NonNull Direction direction) {
        final float size = IkGuiImplLayout.getFrameHeight();
        return arrowButtonEx(id, direction, size, size, ButtonFlags.NONE);
    }

    /**
     * A button with an arrow shape.
     *
     * @param stringID The string ID of the button.
     * @param direction The direction the arrow points.
     * @param width The width of the button.
     * @param height The height of the button.
     * @param buttonFlags Flags for the button.
     * @return True if the button was pressed.
     */
    public static boolean arrowButtonEx(
            @NonNull String stringID,
            @NonNull Direction direction,
            float width,
            float height,
            int buttonFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }

        final int id = window.getID(stringID);
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat bb = new RectFloat(x, y, x + width, y + height);
        final float defaultSize = IkGuiImplLayout.getFrameHeight();
        IkGuiInternal.itemSize(
                width,
                height,
                height >= defaultSize ? context.style.variable.framePadding.y : -1.0f);
        if (!IkGuiInternal.itemAdd(bb, id)) {
            return false;
        }

        final IkBoolean hovered = new IkBoolean();
        final IkBoolean held = new IkBoolean();
        final boolean pressed = IkGuiInternal.buttonBehavior(bb, id, hovered, held, buttonFlags);

        IkGuiImplNav.renderNavCursor(bb, id, NavRenderCursorFlags.NONE, -1.0f);
        // Render
        final int backgroundColor =
                IkGuiImplUtils.getColorWithGlobalAlpha(
                        (held.get() && hovered.get())
                                ? ColorType.BUTTON_ACTIVE
                                : hovered.get() ? ColorType.BUTTON_HOVERED : ColorType.BUTTON);
        final int textColor = IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT);
        IkGuiInternal.renderFrame(
                bb.getLeft(),
                bb.getTop(),
                bb.getRight(),
                bb.getBottom(),
                backgroundColor,
                true,
                context.style.variable.frameRounding);
        final float fontSize = IkGuiInternal.getFontSize();
        IkGuiInternal.renderArrow(
                window.drawList,
                bb.getLeft() + Math.max(0.0f, (width - fontSize) * 0.5f),
                bb.getTop() + Math.max(0.0f, (height - fontSize) * 0.5f),
                textColor,
                direction,
                1.0f);

        IkGuiInternal.testEngineItemInfo(id, stringID, context.lastItemData.statusFlags);
        return pressed;
    }

    /**
     * A regular button with a label.
     *
     * @param label The label, which is also used for the ID.
     * @param width The width, 0 to fit the label, negative to align to the right edge.
     * @param height The height, 0 to fit the label, negative to align to the bottom edge.
     * @return True if the button was pressed.
     */
    public static boolean button(@NonNull String label, float width, float height) {
        return buttonEx(label, width, height, ButtonFlags.NONE);
    }

    /**
     * A button with a label and custom behavior flags.
     *
     * @param label The label, which is also used for the ID.
     * @param width The width, 0 to fit the label, negative to align to the right edge.
     * @param height The height, 0 to fit the label, negative to align to the bottom edge.
     * @param buttonFlags Flags for the button behavior.
     * @return True if the button was pressed.
     * @see ButtonFlags
     */
    public static boolean buttonEx(
            @NonNull String label, float width, float height, int buttonFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }

        final StyleVariables style = context.style.variable;
        final int id = window.getID(label);
        final String displayedLabel = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayedLabel, false, -1.0f);

        float x = window.cursorPosition.x;
        float y = window.cursorPosition.y;
        // Try to vertically align buttons that are smaller/have no padding so that the text
        // baseline matches
        if ((buttonFlags & ButtonFlags.INTERNAL_ALIGN_TEXT_BASELINE) != 0
                && style.framePadding.y < window.baseOffsetCurrentLine) {
            y += window.baseOffsetCurrentLine - style.framePadding.y;
        }
        final Vector2f size = new Vector2f(width, height);
        IkGuiInternal.calcItemSize(
                size,
                labelSize.x + style.framePadding.x * 2.0f,
                labelSize.y + style.framePadding.y * 2.0f);

        final RectFloat bb = new RectFloat(x, y, x + size.x, y + size.y);
        IkGuiInternal.itemSize(size.x, size.y, style.framePadding.y);
        if (!IkGuiInternal.itemAdd(bb, id)) {
            return false;
        }

        final IkBoolean hovered = new IkBoolean();
        final IkBoolean held = new IkBoolean();
        final boolean pressed = IkGuiInternal.buttonBehavior(bb, id, hovered, held, buttonFlags);

        IkGuiImplNav.renderNavCursor(bb, id, NavRenderCursorFlags.NONE, -1.0f);
        // Render
        final int color =
                IkGuiImplUtils.getColorWithGlobalAlpha(
                        (held.get() && hovered.get())
                                ? ColorType.BUTTON_ACTIVE
                                : hovered.get() ? ColorType.BUTTON_HOVERED : ColorType.BUTTON);
        IkGuiInternal.renderFrame(
                bb.getLeft(),
                bb.getTop(),
                bb.getRight(),
                bb.getBottom(),
                color,
                true,
                style.frameRounding);
        if (context.logEnabled) {
            IkGuiImplLogging.logSetNextTextDecoration("[", "]");
        }
        IkGuiInternal.renderTextClipped(
                bb.getLeft() + style.framePadding.x,
                bb.getTop() + style.framePadding.y,
                bb.getRight() - style.framePadding.x,
                bb.getBottom() - style.framePadding.y,
                displayedLabel,
                labelSize,
                style.buttonTextAlign.x,
                style.buttonTextAlign.y,
                bb);

        IkGuiInternal.testEngineItemInfo(id, label, context.lastItemData.statusFlags);
        return pressed;
    }

    /**
     * A flexible button behavior without the visuals, frequently useful to build custom behaviors
     * using the public API (along with isItemActive, isItemHovered, etc.).
     *
     * @param stringID The string ID of the button.
     * @param width The width, must be non-zero.
     * @param height The height, must be non-zero.
     * @param buttonFlags Flags for the button.
     * @return True if the button was pressed.
     * @see ButtonFlags
     */
    public static boolean invisibleButton(
            @NonNull String stringID, float width, float height, int buttonFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }

        // Ensure zero-size fits to contents
        final Vector2f size =
                new Vector2f(
                        width != 0.0f ? width : -Float.MIN_VALUE,
                        height != 0.0f ? height : -Float.MIN_VALUE);
        IkGuiInternal.calcItemSize(size, 0.0f, 0.0f);

        final int id = window.getID(stringID);
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat bb = new RectFloat(x, y, x + size.x, y + size.y);
        IkGuiInternal.itemSize(size.x, size.y);
        if (!IkGuiInternal.itemAdd(
                bb,
                id,
                null,
                (buttonFlags & ButtonFlags.ENABLE_NAV) != 0 ? ItemFlags.NONE : ItemFlags.NO_NAV)) {
            return false;
        }

        final boolean pressed = IkGuiInternal.buttonBehavior(bb, id, null, null, buttonFlags);
        IkGuiImplNav.renderNavCursor(bb, id, NavRenderCursorFlags.NONE, -1.0f);
        IkGuiInternal.testEngineItemInfo(id, stringID, context.lastItemData.statusFlags);
        return pressed;
    }

    /**
     * A button with no frame padding, to easily embed within text.
     *
     * @param label The label, which is also used for the ID.
     * @return True if the button was pressed.
     */
    public static boolean smallButton(@NonNull String label) {
        final StyleVariables style = context.style.variable;
        final float backupPaddingY = style.framePadding.y;
        style.framePadding.y = 0.0f;
        final boolean pressed = buttonEx(label, 0, 0, ButtonFlags.INTERNAL_ALIGN_TEXT_BASELINE);
        style.framePadding.y = backupPaddingY;
        return pressed;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplButtons() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
