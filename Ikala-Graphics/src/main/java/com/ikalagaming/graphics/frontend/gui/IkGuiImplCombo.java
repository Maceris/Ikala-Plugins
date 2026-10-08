package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.*;
import com.ikalagaming.graphics.frontend.gui.flags.*;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.function.IntFunction;

/** Combo boxes. */
@Slf4j
class IkGuiImplCombo {

    static Context context;

    /**
     * Calculate the maximum height of a popup that shows a certain number of items.
     *
     * @param itemCount The number of items, 0 or less for no limit.
     * @return The height in pixels.
     */
    static float calcMaxPopupHeightFromItemCount(int itemCount) {
        if (itemCount <= 0) {
            return Float.MAX_VALUE;
        }
        final StyleVariables style = context.style.variable;
        return (IkGuiInternal.getFontSize() + style.itemSpacing.y) * itemCount
                - style.itemSpacing.y
                + (style.windowPadding.y * 2);
    }

    /**
     * Begin a combo box. The popup is only visible (and this returns true) when it is open.
     *
     * @param label The label, which is also used for the ID.
     * @param previewValue The text to display in the combo box, may be null.
     * @param comboFlags Combo flags.
     * @return True if the combo popup is open, and endCombo() needs to be called.
     * @see ComboFlags
     */
    public static boolean beginCombo(@NonNull String label, String previewValue, int comboFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();

        final int backupNextWindowDataFlags = context.nextWindowData.fieldFlags;
        // We behave like begin() and need to consume those values
        context.nextWindowData.clearFlags();
        if (window.skipItems) {
            return false;
        }

        final StyleVariables style = context.style.variable;
        final int id = window.getID(label);
        if ((comboFlags & (ComboFlags.NO_ARROW_BUTTON | ComboFlags.NO_PREVIEW))
                == (ComboFlags.NO_ARROW_BUTTON | ComboFlags.NO_PREVIEW)) {
            IkGuiImplDebugTools.reportError(
                    log, "Can't use both NO_ARROW_BUTTON and NO_PREVIEW on combo {}", label);
            return false;
        }
        if ((comboFlags & ComboFlags.WIDTH_FIT_PREVIEW) != 0
                && (comboFlags & ComboFlags.NO_PREVIEW) != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Can't use both WIDTH_FIT_PREVIEW and NO_PREVIEW on combo {}", label);
            return false;
        }

        final float arrowSize =
                (comboFlags & ComboFlags.NO_ARROW_BUTTON) != 0
                        ? 0.0f
                        : IkGuiImplLayout.getFrameHeight();
        final String displayedLabel = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayedLabel, false, -1.0f);
        float previewWidth = 0.0f;
        if ((comboFlags & ComboFlags.WIDTH_FIT_PREVIEW) != 0 && previewValue != null) {
            final Vector2f previewSize = new Vector2f();
            IkGuiImplUtils.calcTextSize(previewSize, previewValue, false, -1.0f);
            previewWidth = previewSize.x;
        }
        final float width;
        if ((comboFlags & ComboFlags.NO_PREVIEW) != 0) {
            width = arrowSize;
        } else if ((comboFlags & ComboFlags.WIDTH_FIT_PREVIEW) != 0) {
            width = arrowSize + previewWidth + style.framePadding.x * 2.0f;
        } else {
            width = IkGuiImplUtils.calculateItemWidth();
        }
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat bb =
                new RectFloat(x, y, x + width, y + labelSize.y + style.framePadding.y * 2.0f);
        final RectFloat totalBB =
                new RectFloat(
                        bb.getLeft(),
                        bb.getTop(),
                        bb.getRight()
                                + (labelSize.x > 0.0f
                                        ? style.itemInnerSpacing.x + labelSize.x
                                        : 0.0f),
                        bb.getBottom());
        IkGuiInternal.itemSize(totalBB, style.framePadding.y);
        if (!IkGuiInternal.itemAdd(totalBB, id, bb, ItemFlags.NONE)) {
            return false;
        }

        // Open on click
        final IkBoolean hovered = new IkBoolean();
        final IkBoolean held = new IkBoolean();
        final boolean pressed =
                IkGuiInternal.buttonBehavior(bb, id, hovered, held, ButtonFlags.NONE);
        final int popupID = Hash.getID("##ComboPopup", id);
        boolean popupOpen = IkGuiImplPopups.isPopupOpen(popupID, PopupFlags.NONE);
        if (pressed && !popupOpen) {
            IkGuiImplPopups.openPopupEx(popupID, PopupFlags.NONE);
            popupOpen = true;
        }

        // Render shape
        final int frameColor =
                IkGuiImplUtils.getColorWithGlobalAlpha(
                        hovered.get()
                                ? ColorType.FRAME_BACKGROUND_HOVERED
                                : ColorType.FRAME_BACKGROUND);
        final float valueX2 = Math.max(bb.getLeft(), bb.getRight() - arrowSize);
        final DrawList drawList = window.drawList;
        IkGuiImplNav.renderNavCursor(bb, id, NavRenderCursorFlags.NONE, -1.0f);
        if ((comboFlags & ComboFlags.NO_PREVIEW) == 0) {
            drawList.addRectFilled(
                    bb.getLeft(),
                    bb.getTop(),
                    valueX2,
                    bb.getBottom(),
                    frameColor,
                    style.frameRounding,
                    (comboFlags & ComboFlags.NO_ARROW_BUTTON) != 0
                            ? DrawFlags.ROUND_CORNERS_ALL
                            : DrawFlags.ROUND_CORNERS_LEFT);
        }
        if ((comboFlags & ComboFlags.NO_ARROW_BUTTON) == 0) {
            final int backgroundColor =
                    IkGuiImplUtils.getColorWithGlobalAlpha(
                            (popupOpen || hovered.get())
                                    ? ColorType.BUTTON_HOVERED
                                    : ColorType.BUTTON);
            final int textColor = IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT);
            drawList.addRectFilled(
                    valueX2,
                    bb.getTop(),
                    bb.getRight(),
                    bb.getBottom(),
                    backgroundColor,
                    style.frameRounding,
                    width <= arrowSize
                            ? DrawFlags.ROUND_CORNERS_ALL
                            : DrawFlags.ROUND_CORNERS_RIGHT);
            if (valueX2 + arrowSize - style.framePadding.x <= bb.getRight()) {
                IkGuiInternal.renderArrow(
                        drawList,
                        valueX2 + style.framePadding.y,
                        bb.getTop() + style.framePadding.y,
                        textColor,
                        Direction.DOWN,
                        1.0f);
            }
        }
        IkGuiInternal.renderFrameBorder(
                bb.getLeft(), bb.getTop(), bb.getRight(), bb.getBottom(), style.frameRounding);

        // Store the geometry for beginComboPreview(), only necessary when visible
        if ((comboFlags & ComboFlags.NO_PREVIEW) != 0) {
            context.comboPreviewData.previewRect.set(0, 0, 0, 0);
        } else {
            context.comboPreviewData.previewRect.set(
                    bb.getLeft(), bb.getTop(), valueX2, bb.getBottom());
        }

        // Render preview and label
        if (previewValue != null && (comboFlags & ComboFlags.NO_PREVIEW) == 0) {
            if (context.logEnabled) {
                IkGuiImplLogging.logSetNextTextDecoration("{", "}");
            }
            IkGuiInternal.renderTextClipped(
                    bb.getLeft() + style.framePadding.x,
                    bb.getTop() + style.framePadding.y,
                    valueX2,
                    bb.getBottom(),
                    previewValue,
                    null,
                    0.0f,
                    0.0f,
                    null);
        }
        if (labelSize.x > 0) {
            IkGuiInternal.renderText(
                    bb.getRight() + style.itemInnerSpacing.x,
                    bb.getTop() + style.framePadding.y,
                    displayedLabel,
                    false);
        }

        if (!popupOpen) {
            return false;
        }

        context.nextWindowData.fieldFlags = backupNextWindowDataFlags;
        return beginComboPopup(popupID, bb, comboFlags);
    }

    /**
     * Begin the popup part of a combo box.
     *
     * @param popupID The ID of the popup.
     * @param bb The bounding box of the combo box.
     * @param comboFlags Combo flags.
     * @return True if the popup is open, and endCombo() needs to be called.
     */
    static boolean beginComboPopup(int popupID, @NonNull RectFloat bb, int comboFlags) {
        if (!IkGuiImplPopups.isPopupOpen(popupID, PopupFlags.NONE)) {
            context.nextWindowData.clearFlags();
            return false;
        }

        // Set the popup size
        final NextWindowData nextWindowData = context.nextWindowData;
        final float width = bb.getWidth();
        if ((nextWindowData.fieldFlags & NextWindowFlags.HAS_SIZE_CONSTRAINT) != 0) {
            final RectFloat constraint = nextWindowData.sizeConstraintRect;
            constraint.set(
                    Math.max(constraint.getLeft(), width),
                    constraint.getTop(),
                    constraint.getRight(),
                    constraint.getBottom());
        } else {
            if ((comboFlags & ComboFlags.HEIGHT_MASK) == 0) {
                comboFlags |= ComboFlags.HEIGHT_REGULAR;
            }
            int popupMaxHeightInItems = -1;
            if ((comboFlags & ComboFlags.HEIGHT_REGULAR) != 0) {
                popupMaxHeightInItems = 8;
            } else if ((comboFlags & ComboFlags.HEIGHT_SMALL) != 0) {
                popupMaxHeightInItems = 4;
            } else if ((comboFlags & ComboFlags.HEIGHT_LARGE) != 0) {
                popupMaxHeightInItems = 20;
            }
            float constraintMinX = 0.0f;
            float constraintMaxY = Float.MAX_VALUE;
            final boolean hasSize = (nextWindowData.fieldFlags & NextWindowFlags.HAS_SIZE) != 0;
            // Don't apply constraints if the user specified a size
            if (!hasSize || nextWindowData.sizeValue.x <= 0.0f) {
                constraintMinX = width;
            }
            if (!hasSize || nextWindowData.sizeValue.y <= 0.0f) {
                constraintMaxY = calcMaxPopupHeightFromItemCount(popupMaxHeightInItems);
            }
            IkGuiImplWindows.setNextWindowSizeConstraints(
                    constraintMinX, 0.0f, Float.MAX_VALUE, constraintMaxY);
        }

        // This is essentially a specialized version of beginPopupEx(), recycling windows by depth
        final String name = String.format("##Combo_%02d", context.beginComboDepth);

        // Set the position given a custom constraint (peek into the expected window size so we can
        // position it)
        final Window popupWindow = IkGuiInternal.findWindowByName(name);
        if (popupWindow != null && popupWindow.wasActive) {
            // Always override the last direction to not leave a chance for a past value to affect
            // us. Left = "below, toward left", down = "below, toward right (default)".
            final Vector2f sizeExpected =
                    IkGuiInternal.calcWindowNextAutoFitSize(popupWindow, new Vector2f());
            popupWindow.autoPosLastDirection =
                    (comboFlags & ComboFlags.POPUP_ALIGN_LEFT) != 0
                            ? Direction.LEFT
                            : Direction.DOWN;
            final RectFloat outer =
                    IkGuiImplPopups.getPopupAllowedExtentRect(popupWindow, new RectFloat());
            final Vector2f position =
                    IkGuiImplPopups.findBestWindowPosForPopupEx(
                            bb.getLeft(),
                            bb.getBottom(),
                            sizeExpected,
                            popupWindow,
                            outer,
                            bb,
                            IkGuiImplPopups.PopupPositionPolicy.COMBO_BOX,
                            new Vector2f());
            IkGuiImplWindows.setNextWindowPos(position.x, position.y, Condition.ALWAYS, 0.0f, 0.0f);
        }

        final int windowFlags =
                WindowFlags.ALWAYS_AUTO_RESIZE
                        | WindowFlags.INTERNAL_POPUP
                        | WindowFlags.NO_TITLE_BAR
                        | WindowFlags.NO_RESIZE
                        | WindowFlags.NO_SAVED_SETTINGS
                        | WindowFlags.NO_MOVE;
        // Horizontally align ourselves with the framed text
        final StyleVariables style = context.style.variable;
        IkGuiImplUtils.pushStyleVarFloat2(
                StyleVariable.WINDOW_PADDING, style.framePadding.x, style.windowPadding.y);
        final boolean result = IkGuiImplWindows.begin(name, null, windowFlags);
        IkGuiImplUtils.popStyleVar();
        if (!result) {
            IkGuiImplPopups.endPopup();
            IkGuiImplDebugTools.reportError(
                    log, "Combo popup {} was open but could not begin", name);
            return false;
        }
        context.beginComboDepth++;
        return true;
    }

    /**
     * Submit preview contents for a combo, to display more than just a text label. Call this after
     * beginCombo(), and after endCombo() if the combo was open. The preview is designed to only
     * host non-interactive elements, and is not compatible with {@link
     * ComboFlags#WIDTH_FIT_PREVIEW}.
     *
     * @return True if the preview is visible, in which case call endComboPreview().
     */
    public static boolean beginComboPreview() {
        final Window window = context.windowCurrent;
        if (window.skipItems || (context.lastItemData.statusFlags & ItemStatusFlags.VISIBLE) == 0) {
            return false;
        }
        final ComboPreviewData previewData = context.comboPreviewData;
        // A narrower test, which also handles NO_PREVIEW
        if (!window.rectCurrentClip.overlaps(previewData.previewRect)
                || previewData.previewRect.getWidth() <= 0.0f) {
            return false;
        }
        // Calling this after endCombo() only works if combos aren't nested
        if (context.lastItemData.rect.getLeft() != previewData.previewRect.getLeft()
                || context.lastItemData.rect.getTop() != previewData.previewRect.getTop()) {
            IkGuiImplDebugTools.reportError(
                    log, "Call beginComboPreview() after beginCombo(), not after endCombo()!");
            return false;
        }
        if (previewData.withinPreview) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Cannot recurse beginComboPreview(): call endComboPreview() before opening"
                            + " another combo.");
            return false;
        }

        previewData.backupCursorPos.set(window.cursorPosition);
        previewData.backupCursorMaxPos.set(window.cursorMaxPosition);
        previewData.backupCursorPosPreviousLine.set(window.cursorPreviousLinePosition);
        previewData.backupPreviousLineTextBaseOffset = window.baseOffsetPreviousLine;
        previewData.backupLayout = window.layoutType;
        previewData.withinPreview = true;
        previewData.backupWorkRectMaxX = window.rectWork.getRight();
        previewData.backupContentRectMaxX = window.rectContent.getRight();
        final float maxX =
                previewData.previewRect.getRight() - context.style.variable.framePadding.x;
        window.rectWork.setRight(maxX);
        window.rectContent.setRight(maxX);
        window.cursorPosition.set(
                previewData.previewRect.getLeft() + context.style.variable.framePadding.x,
                previewData.previewRect.getTop() + context.style.variable.framePadding.y);
        window.cursorMaxPosition.set(window.cursorPosition);
        window.layoutType = LayoutType.HORIZONTAL;
        window.sameLine = false;
        IkGuiImplLayout.pushClipRect(
                previewData.previewRect.getLeft(),
                previewData.previewRect.getTop(),
                previewData.previewRect.getRight(),
                previewData.previewRect.getBottom(),
                true);
        return true;
    }

    /** End the preview contents of a combo, only call this if beginComboPreview() returned true. */
    public static void endComboPreview() {
        final Window window = context.windowCurrent;
        final ComboPreviewData previewData = context.comboPreviewData;

        // Upstream widens the clip rect of the last draw command here so it can merge with the
        // previous one. Our draw list keeps a clip rect per shape, so there is nothing to merge.
        IkGuiImplLayout.popClipRect();
        window.cursorPosition.set(previewData.backupCursorPos);
        window.cursorMaxPosition.max(previewData.backupCursorMaxPos);
        window.cursorPreviousLinePosition.set(previewData.backupCursorPosPreviousLine);
        window.baseOffsetPreviousLine = previewData.backupPreviousLineTextBaseOffset;
        window.rectWork.setRight(previewData.backupWorkRectMaxX);
        window.rectContent.setRight(previewData.backupContentRectMaxX);
        window.layoutType = previewData.backupLayout;
        window.sameLine = false;
        previewData.withinPreview = false;
    }

    /** End a combo box, only call this if beginCombo() returned true. */
    public static void endCombo() {
        context.beginComboDepth--;
        final String name = String.format("##Combo_%02d", context.beginComboDepth);
        if (context.windowCurrent == null || !name.equals(context.windowCurrent.name)) {
            IkGuiImplDebugTools.reportError(log, "Calling endCombo() in the wrong window!");
            context.beginComboDepth++;
            return;
        }
        IkGuiImplPopups.endPopup();
    }

    /**
     * A simple combo box for selecting an item from a list.
     *
     * @param label The label, which is also used for the ID.
     * @param currentItem The index of the selected item, updated when the selection changes.
     * @param items The items to choose from.
     * @param popupMaxHeightInItems The maximum height of the popup in items, -1 for the default.
     * @return True if the selection changed.
     */
    public static boolean combo(
            @NonNull String label,
            @NonNull IkInt currentItem,
            @NonNull String[] items,
            int popupMaxHeightInItems) {
        return combo(label, currentItem, i -> items[i], items.length, popupMaxHeightInItems);
    }

    /**
     * A simple combo box for selecting an item, where the item names are fetched as needed.
     *
     * @param label The label, which is also used for the ID.
     * @param currentItem The index of the selected item, updated when the selection changes.
     * @param getter Fetches the name of the item at an index, which may return null.
     * @param itemsCount The number of items to choose from.
     * @param popupMaxHeightInItems The maximum height of the popup in items, -1 for the default.
     * @return True if the selection changed.
     */
    public static boolean combo(
            @NonNull String label,
            @NonNull IkInt currentItem,
            @NonNull IntFunction<String> getter,
            int itemsCount,
            int popupMaxHeightInItems) {
        // Get the preview string
        String previewValue = null;
        if (((context.nextItemData.itemFlags | context.currentItemFlags) & ItemFlags.MIXED_VALUE)
                != 0) {
            previewValue = "";
        } else if (currentItem.get() >= 0 && currentItem.get() < itemsCount) {
            previewValue = getter.apply(currentItem.get());
        }

        if (popupMaxHeightInItems != -1
                && (context.nextWindowData.fieldFlags & NextWindowFlags.HAS_SIZE_CONSTRAINT) == 0) {
            IkGuiImplWindows.setNextWindowSizeConstraints(
                    0, 0, Float.MAX_VALUE, calcMaxPopupHeightFromItemCount(popupMaxHeightInItems));
        }

        if (!beginCombo(label, previewValue, ComboFlags.NONE)) {
            return false;
        }

        // Display the items, only submitting the visible ones
        boolean valueChanged = false;
        final ListClipper clipper = new ListClipper();
        clipper.begin(itemsCount);
        // Always include the selected item so the popup can focus it when opening
        if (currentItem.get() >= 0 && currentItem.get() < itemsCount) {
            clipper.includeItemByIndex(currentItem.get());
        }
        while (clipper.step()) {
            for (int i = clipper.displayStart; i < clipper.displayEnd; ++i) {
                final String name = getter.apply(i);
                final String itemText = name != null ? name : "*Unknown item*";
                IkGuiImplUtils.pushID(i);
                final boolean itemSelected = i == currentItem.get();
                if (IkGuiImplMiscWidgets.selectable(
                                itemText, itemSelected, SelectableFlags.NONE, 0, 0)
                        && currentItem.get() != i) {
                    valueChanged = true;
                    currentItem.set(i);
                }
                if (itemSelected) {
                    IkGuiImplNav.setItemDefaultFocus();
                }
                IkGuiImplUtils.popID();
            }
        }

        endCombo();
        if (valueChanged) {
            IkGuiInternal.markItemEdited(context.lastItemData.id);
        }
        return valueChanged;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplCombo() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
