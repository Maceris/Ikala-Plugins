package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.TextureInfo;
import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.flags.ButtonFlags;
import com.ikalagaming.graphics.gui.flags.ChildFlags;
import com.ikalagaming.graphics.gui.flags.DrawFlags;
import com.ikalagaming.graphics.gui.flags.ItemFlags;
import com.ikalagaming.graphics.gui.flags.ItemStatusFlags;
import com.ikalagaming.graphics.gui.flags.KeyModFlags;
import com.ikalagaming.graphics.gui.flags.MultiSelectFlags;
import com.ikalagaming.graphics.gui.flags.NavRenderCursorFlags;
import com.ikalagaming.graphics.gui.flags.SelectableFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.MathUtil;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.Locale;
import java.util.function.IntFunction;

@Slf4j
class IkGuiImplMiscWidgets {

    public static final String FLOAT_DEFAULT_FORMAT = "%.3f";
    public static final String INT_DEFAULT_FORMAT = "%d";
    public static final String DOUBLE_DEFAULT_FORMAT = "%.6f";
    public static final String SLIDER_ANGLE_DEFAULT_FORMAT = "%.0f deg";

    static Context context;

    /**
     * Begin a list box, which is a framed child window with a label, generally filled with
     * selectables. If you don't need a label you can use beginChild() with ChildFlags.FRAME_STYLE
     * directly.
     *
     * @param label The label, which is also used for the ID.
     * @param width The width, 0 for the default item width, negative to align to the right edge.
     * @param height The height, 0 for the default height (about 7 items), negative to align to the
     *     bottom edge.
     * @return True if the list box is visible, and endListBox() needs to be called.
     */
    public static boolean beginListBox(@NonNull String label, float width, float height) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }

        final StyleVariables style = context.style.variable;
        final int id = window.getID(label);
        final String displayedLabel = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayedLabel, false, -1.0f);

        // The size defaults to hold about 7.25 items. The fractional number of items helps show
        // that we can scroll down/up without looking at the scrollbar.
        final Vector2f size = new Vector2f(width, height);
        IkGuiInternal.calcItemSize(
                size,
                IkGuiImplUtils.calculateItemWidth(),
                IkGuiImplLayout.getTextLineHeightWithSpacing() * 7.25f
                        + style.framePadding.y * 2.0f);
        IkGuiInternal.truncate(size);
        final float frameWidth = size.x;
        final float frameHeight = Math.max(size.y, labelSize.y);
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat frameBB = new RectFloat(x, y, x + frameWidth, y + frameHeight);
        final RectFloat bb =
                new RectFloat(
                        x,
                        y,
                        frameBB.getRight()
                                + (labelSize.x > 0.0f
                                        ? style.itemInnerSpacing.x + labelSize.x
                                        : 0.0f),
                        frameBB.getBottom());
        context.nextItemData.clearFlags();

        if (!IkGuiImplUtils.isRectVisible(
                bb.getLeft(), bb.getTop(), bb.getRight(), bb.getBottom())) {
            IkGuiInternal.itemSize(bb.getWidth(), bb.getHeight(), style.framePadding.y);
            IkGuiInternal.itemAdd(bb, 0, frameBB, ItemFlags.NONE);
            // We behave like begin() and need to consume those values
            context.nextWindowData.clearFlags();
            return false;
        }

        IkGuiImplLayout.beginGroup();
        if (labelSize.x > 0.0f) {
            final float labelX = frameBB.getRight() + style.itemInnerSpacing.x;
            final float labelY = frameBB.getTop() + style.framePadding.y;
            IkGuiInternal.renderText(labelX, labelY, displayedLabel, false);
            window.cursorMaxPosition.set(
                    Math.max(window.cursorMaxPosition.x, labelX + labelSize.x),
                    Math.max(window.cursorMaxPosition.y, labelY + labelSize.y));
            IkGuiImplText.alignTextToFramePadding();
        }

        IkGuiImplWindows.beginChild(
                null,
                id,
                frameBB.getWidth(),
                frameBB.getHeight(),
                ChildFlags.FRAME_STYLE,
                WindowFlags.NONE);
        return true;
    }

    /**
     * A checkbox that doesn't store its own state.
     *
     * @param label The label, which is also used for the ID.
     * @param checked Whether the checkbox is currently checked.
     * @return True if the checkbox was clicked, so the caller should toggle their state.
     */
    public static boolean checkbox(@NonNull String label, boolean checked) {
        return checkbox(label, new IkBoolean(checked));
    }

    /**
     * A checkbox.
     *
     * @param label The label, which is also used for the ID.
     * @param active The state of the checkbox, which is toggled when clicked.
     * @return True if the checkbox was clicked.
     */
    public static boolean checkbox(@NonNull String label, @NonNull IkBoolean active) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }

        final StyleVariables style = context.style.variable;
        final int id = window.getID(label);
        final String displayedLabel = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayedLabel, false, -1.0f);

        final float squareSize = IkGuiImplLayout.getFrameHeight();
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat totalBB =
                new RectFloat(
                        x,
                        y,
                        x
                                + squareSize
                                + (labelSize.x > 0.0f
                                        ? style.itemInnerSpacing.x + labelSize.x
                                        : 0.0f),
                        y + labelSize.y + style.framePadding.y * 2.0f);
        IkGuiInternal.itemSize(totalBB, style.framePadding.y);
        final boolean isVisible = IkGuiInternal.itemAdd(totalBB, id);
        final boolean isMultiSelect =
                (context.lastItemData.itemFlags & ItemFlags.INTERNAL_IS_MULTI_SELECT) != 0;
        // Extra layer of "no logic clip" for box-select support
        if (!isVisible && !IkGuiImplMultiSelect.isUnclippedByBoxSelect(totalBB)) {
            IkGuiInternal.testEngineItemInfo(
                    id,
                    label,
                    context.lastItemData.statusFlags
                            | ItemStatusFlags.CHECKABLE
                            | (active.get() ? ItemStatusFlags.CHECKED : 0));
            return false;
        }

        // Range-selection/multi-selection support (header)
        final IkBoolean checked = new IkBoolean(active.get());
        if (isMultiSelect) {
            IkGuiImplMultiSelect.multiSelectItemHeader(id, checked, null);
        }

        final IkBoolean hovered = new IkBoolean();
        final IkBoolean held = new IkBoolean();
        boolean pressed =
                IkGuiInternal.buttonBehavior(totalBB, id, hovered, held, ButtonFlags.NONE);

        // Range-selection/multi-selection support (footer)
        if (isMultiSelect) {
            final IkBoolean pressedState = new IkBoolean(pressed);
            IkGuiImplMultiSelect.multiSelectItemFooter(
                    id, checked, pressedState, MultiSelectFlags.INTERNAL_CHECKBOX_MODE);
            pressed = pressedState.get();
        } else if (pressed) {
            checked.set(!checked.get());
        }

        if (active.get() != checked.get()) {
            active.set(checked.get());
            // The return value
            pressed = true;
            IkGuiInternal.markItemEdited(id);
        }
        if (!isVisible) {
            IkGuiInternal.testEngineItemInfo(
                    id,
                    label,
                    context.lastItemData.statusFlags
                            | ItemStatusFlags.CHECKABLE
                            | (active.get() ? ItemStatusFlags.CHECKED : 0));
            return pressed;
        }

        IkGuiImplNav.renderNavCursor(totalBB, id, NavRenderCursorFlags.NONE, -1.0f);
        // Render
        final boolean mixedValue = (context.lastItemData.itemFlags & ItemFlags.MIXED_VALUE) != 0;
        final RectFloat checkBB = new RectFloat(x, y, x + squareSize, y + squareSize);
        IkGuiInternal.renderFrame(
                checkBB.getLeft(),
                checkBB.getTop(),
                checkBB.getRight(),
                checkBB.getBottom(),
                IkGuiImplUtils.getColorWithGlobalAlpha(
                        (held.get() && hovered.get())
                                ? ColorType.FRAME_BACKGROUND_ACTIVE
                                : hovered.get()
                                        ? ColorType.FRAME_BACKGROUND_HOVERED
                                        : (mixedValue || active.get())
                                                ? ColorType.CHECKBOX_SELECTED_BACKGROUND
                                                : ColorType.FRAME_BACKGROUND),
                true,
                style.frameRounding);
        final int checkColor = IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.CHECK_MARK);
        if (mixedValue) {
            // Undocumented tristate/mixed/indeterminate checkbox
            final float pad = Math.max(1.0f, IkGuiInternal.truncate(squareSize / 3.6f));
            window.drawList.addRectFilled(
                    checkBB.getLeft() + pad,
                    checkBB.getTop() + pad,
                    checkBB.getRight() - pad,
                    checkBB.getBottom() - pad,
                    checkColor,
                    style.frameRounding);
        } else if (active.get()) {
            final float pad = Math.max(1.0f, IkGuiInternal.truncate(squareSize / 6.0f));
            IkGuiInternal.renderCheckMark(
                    window.drawList,
                    checkBB.getLeft() + pad,
                    checkBB.getTop() + pad,
                    checkColor,
                    squareSize - pad * 2.0f);
        }

        final float labelY = checkBB.getTop() + style.framePadding.y;
        if (context.logEnabled) {
            IkGuiImplLogging.logRenderedText(
                    labelY, mixedValue ? "[~]" : active.get() ? "[x]" : "[ ]");
        }
        if (labelSize.x > 0.0f) {
            IkGuiInternal.renderText(
                    checkBB.getRight() + style.itemInnerSpacing.x, labelY, displayedLabel, false);
        }
        IkGuiInternal.testEngineItemInfo(
                id,
                label,
                context.lastItemData.statusFlags
                        | ItemStatusFlags.CHECKABLE
                        | (active.get() ? ItemStatusFlags.CHECKED : 0));
        return pressed;
    }

    /**
     * A checkbox that toggles some bits in a set of flags. If only some of the bits are set, the
     * checkbox shows a mixed state.
     *
     * @param label The label, which is also used for the ID.
     * @param flags The flags to modify.
     * @param flagsValue The bits that this checkbox controls.
     * @return True if the checkbox was clicked.
     */
    public static boolean checkboxFlags(
            @NonNull String label, @NonNull IkInt flags, int flagsValue) {
        final boolean allOn = (flags.get() & flagsValue) == flagsValue;
        final boolean anyOn = (flags.get() & flagsValue) != 0;
        final IkBoolean checked = new IkBoolean(allOn);
        final boolean pressed;
        if (!allOn && anyOn) {
            IkGuiImplUtils.pushItemFlag(ItemFlags.MIXED_VALUE, true);
            pressed = checkbox(label, checked);
            IkGuiImplUtils.popItemFlag();
        } else {
            pressed = checkbox(label, checked);
        }
        if (pressed) {
            if (checked.get()) {
                flags.set(flags.get() | flagsValue);
            } else {
                flags.set(flags.get() & ~flagsValue);
            }
        }
        return pressed;
    }

    /** End a list box, only call this if beginListBox() returned true. */
    public static void endListBox() {
        final Window window = context.windowCurrent;
        if (window == null || (window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) == 0) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Mismatched beginListBox()/endListBox() calls. Did you test the return value of beginListBox()?");
            return;
        }
        IkGuiImplWindows.endChild();
        // This is only required to be able to do isItem*() queries on the whole list box,
        // including the label
        IkGuiImplLayout.endGroup();
    }

    /**
     * Display an image, with optional border according to style.imageBorderSize.
     *
     * @param texture The texture.
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
        imageWithBackground(texture, width, height, u0, v0, u1, v1, Color.CLEAR, Color.WHITE);
    }

    /**
     * An image button, which uses the frame padding around the image and button colors for the
     * background.
     *
     * @param stringID The ID of the button.
     * @param texture The texture.
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
            @NonNull String stringID,
            @NonNull TextureInfo texture,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            int background,
            int tint) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        return imageButtonEx(
                window.getID(stringID),
                texture,
                width,
                height,
                u0,
                v0,
                u1,
                v1,
                background,
                tint,
                ButtonFlags.NONE);
    }

    /**
     * An image button with an explicit ID and button flags.
     *
     * @param id The ID of the button.
     * @param texture The texture.
     * @param width The width of the image.
     * @param height The height of the image.
     * @param u0 The texture u coordinate at the left.
     * @param v0 The texture v coordinate at the top.
     * @param u1 The texture u coordinate at the right.
     * @param v1 The texture v coordinate at the bottom.
     * @param background An extra background color drawn behind the image.
     * @param tint The color to multiply the image by.
     * @param buttonFlags Flags for the button behavior.
     * @return True if the button was pressed.
     */
    static boolean imageButtonEx(
            int id,
            @NonNull TextureInfo texture,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            int background,
            int tint,
            int buttonFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }

        final StyleVariables style = context.style.variable;
        final Vector2f padding = style.framePadding;
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat bb =
                new RectFloat(x, y, x + width + padding.x * 2, y + height + padding.y * 2);
        IkGuiInternal.itemSize(bb, -1.0f);
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
        final float minX = bb.getLeft() + padding.x;
        final float minY = bb.getTop() + padding.y;
        final float maxX = bb.getRight() - padding.x;
        final float maxY = bb.getBottom() - padding.y;
        if ((background & 0xFF) != 0) {
            window.drawList.addRectFilled(
                    minX, minY, maxX, maxY, IkGuiImplUtils.applyGlobalAlpha(background, false));
        }
        final float imageRounding =
                Math.max(style.frameRounding - Math.max(padding.x, padding.y), style.imageRounding);
        window.drawList.addImageRounded(
                texture,
                minX,
                minY,
                maxX,
                maxY,
                u0,
                v0,
                u1,
                v1,
                IkGuiImplUtils.applyGlobalAlpha(tint, false),
                imageRounding);

        return pressed;
    }

    /**
     * Display an image with a background color, and an optional border according to
     * style.imageBorderSize.
     *
     * @param texture The texture.
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
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return;
        }

        final StyleVariables style = context.style.variable;
        final float padding = style.imageBorderSize;
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat bb = new RectFloat(x, y, x + width + padding * 2, y + height + padding * 2);
        IkGuiInternal.itemSize(bb, -1.0f);
        if (!IkGuiInternal.itemAdd(bb, 0)) {
            return;
        }

        // Render
        final float rounding = style.imageRounding;
        final float minX = bb.getLeft() + padding;
        final float minY = bb.getTop() + padding;
        final float maxX = bb.getRight() - padding;
        final float maxY = bb.getBottom() - padding;
        if ((background & 0xFF) != 0) {
            window.drawList.addRectFilled(
                    minX,
                    minY,
                    maxX,
                    maxY,
                    IkGuiImplUtils.applyGlobalAlpha(background, false),
                    rounding);
        }
        window.drawList.addImageRounded(
                texture,
                minX,
                minY,
                maxX,
                maxY,
                u0,
                v0,
                u1,
                v1,
                IkGuiImplUtils.applyGlobalAlpha(tint, false),
                rounding);
        if (padding > 0.0f) {
            window.drawList.addRect(
                    bb.getLeft(),
                    bb.getTop(),
                    bb.getRight(),
                    bb.getBottom(),
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.BORDER),
                    rounding,
                    DrawFlags.ROUND_CORNERS_ALL,
                    padding);
        }
    }

    /**
     * Display text with a label, aligned the same way as value widgets (the text where the frame
     * would be, then the label to the right).
     *
     * @param label The label.
     * @param text The value text.
     */
    public static void labelText(String label, @NonNull String text) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return;
        }

        final StyleVariables style = context.style.variable;
        final float width = IkGuiImplUtils.calculateItemWidth();

        final Vector2f valueSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(valueSize, text, false, -1.0f);
        final String displayedLabel = label == null ? "" : Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayedLabel, false, -1.0f);

        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat valueBB =
                new RectFloat(x, y, x + width, y + valueSize.y + style.framePadding.y * 2);
        final RectFloat totalBB =
                new RectFloat(
                        x,
                        y,
                        x
                                + width
                                + (labelSize.x > 0.0f
                                        ? style.itemInnerSpacing.x + labelSize.x
                                        : 0.0f),
                        y + Math.max(valueSize.y, labelSize.y) + style.framePadding.y * 2);
        IkGuiInternal.itemSize(totalBB, style.framePadding.y);
        if (!IkGuiInternal.itemAdd(totalBB, 0)) {
            return;
        }

        // Render
        IkGuiInternal.renderTextClipped(
                valueBB.getLeft() + style.framePadding.x,
                valueBB.getTop() + style.framePadding.y,
                valueBB.getRight(),
                valueBB.getBottom(),
                text,
                valueSize,
                0.0f,
                0.0f,
                null);
        if (labelSize.x > 0.0f) {
            IkGuiInternal.renderText(
                    valueBB.getRight() + style.itemInnerSpacing.x,
                    valueBB.getTop() + style.framePadding.y,
                    displayedLabel,
                    false);
        }
    }

    /**
     * A simple list box for selecting an item from a list. This is a helper around
     * beginListBox()/endListBox(), consider using those directly to submit custom data or store the
     * selection differently.
     *
     * @param label The label, which is also used for the ID.
     * @param currentItem The index of the selected item, updated when the selection changes.
     * @param items The items to choose from.
     * @param heightInItems The height of the list box in items, -1 for the default (up to 7).
     * @return True if the selection changed.
     */
    public static boolean listBox(
            @NonNull String label,
            @NonNull IkInt currentItem,
            @NonNull String[] items,
            int heightInItems) {
        // Calculate the size from the height in items
        if (heightInItems < 0) {
            heightInItems = Math.min(items.length, 7);
        }
        final float heightInItemsFloat = heightInItems + 0.25f;
        final float height =
                IkGuiInternal.truncate(
                        IkGuiImplLayout.getTextLineHeightWithSpacing() * heightInItemsFloat
                                + context.style.variable.framePadding.y * 2.0f);

        if (!beginListBox(label, 0.0f, height)) {
            return false;
        }

        // We know exactly our line height here so we pass it as a minor optimization
        boolean valueChanged = false;
        final ListClipper clipper = new ListClipper();
        clipper.begin(items.length, IkGuiImplLayout.getTextLineHeightWithSpacing());
        if (currentItem.get() >= 0 && currentItem.get() < items.length) {
            clipper.includeItemByIndex(currentItem.get());
        }
        while (clipper.step()) {
            for (int i = clipper.displayStart; i < clipper.displayEnd; ++i) {
                final String itemText = items[i] != null ? items[i] : "*Unknown item*";
                IkGuiImplUtils.pushID(i);
                final boolean itemSelected = i == currentItem.get();
                if (selectable(itemText, itemSelected, SelectableFlags.NONE, 0, 0)) {
                    currentItem.set(i);
                    valueChanged = true;
                }
                if (itemSelected) {
                    IkGuiImplNav.setItemDefaultFocus();
                }
                IkGuiImplUtils.popID();
            }
        }
        endListBox();

        if (valueChanged) {
            IkGuiInternal.markItemEdited(context.lastItemData.id);
        }
        return valueChanged;
    }

    /**
     * Plot values as a histogram, using a function to fetch them.
     *
     * @param label The label, which is also used for the ID.
     * @param valuesGetter Fetches the value at an index.
     * @param count The number of values.
     * @param offset The index of the first value to display, wrapping around.
     * @param overlay Text to display over the top of the plot, may be null.
     * @param scaleMin The value at the bottom of the plot, Float.MAX_VALUE to use the minimum.
     * @param scaleMax The value at the top of the plot, Float.MAX_VALUE to use the maximum.
     * @param width The width, 0 for the default item width, negative to align to the right edge.
     * @param height The height, 0 for the default height.
     */
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
        plotEx(
                PlotType.HISTOGRAM,
                label,
                valuesGetter,
                count,
                offset,
                overlay,
                scaleMin,
                scaleMax,
                width,
                height);
    }

    /**
     * Plot values as a histogram.
     *
     * @param label The label, which is also used for the ID.
     * @param values The values.
     * @param count The number of values to use.
     * @param offset The index of the first value to display, wrapping around.
     * @param overlay Text to display over the top of the plot, may be null.
     * @param scaleMin The value at the bottom of the plot, Float.MAX_VALUE to use the minimum.
     * @param scaleMax The value at the top of the plot, Float.MAX_VALUE to use the maximum.
     * @param width The width, 0 for the default item width, negative to align to the right edge.
     * @param height The height, 0 for the default height.
     */
    public static void plotHistogram(
            String label,
            @NonNull float[] values,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax,
            float width,
            float height) {
        if (count > values.length) {
            IkGuiImplDebugTools.reportError(
                    log, "plotHistogram count {} exceeds the array size {}", count, values.length);
            return;
        }
        plotEx(
                PlotType.HISTOGRAM,
                label,
                i -> values[i],
                count,
                offset,
                overlay,
                scaleMin,
                scaleMax,
                width,
                height);
    }

    /**
     * Plot values as a line graph, using a function to fetch them.
     *
     * @param label The label, which is also used for the ID.
     * @param valuesGetter Fetches the value at an index.
     * @param count The number of values.
     * @param offset The index of the first value to display, wrapping around.
     * @param overlay Text to display over the top of the plot, may be null.
     * @param scaleMin The value at the bottom of the plot, Float.MAX_VALUE to use the minimum.
     * @param scaleMax The value at the top of the plot, Float.MAX_VALUE to use the maximum.
     * @param width The width, 0 for the default item width, negative to align to the right edge.
     * @param height The height, 0 for the default height.
     */
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
        plotEx(
                PlotType.LINES,
                label,
                valuesGetter,
                count,
                offset,
                overlay,
                scaleMin,
                scaleMax,
                width,
                height);
    }

    /**
     * Plot values as a line graph.
     *
     * @param label The label, which is also used for the ID.
     * @param values The values.
     * @param count The number of values to use.
     * @param offset The index of the first value to display, wrapping around.
     * @param overlay Text to display over the top of the plot, may be null.
     * @param scaleMin The value at the bottom of the plot, Float.MAX_VALUE to use the minimum.
     * @param scaleMax The value at the top of the plot, Float.MAX_VALUE to use the maximum.
     * @param width The width, 0 for the default item width, negative to align to the right edge.
     * @param height The height, 0 for the default height.
     */
    public static void plotLines(
            String label,
            @NonNull float[] values,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax,
            float width,
            float height) {
        if (count > values.length) {
            IkGuiImplDebugTools.reportError(
                    log, "plotLines count {} exceeds the array size {}", count, values.length);
            return;
        }
        plotEx(
                PlotType.LINES,
                label,
                i -> values[i],
                count,
                offset,
                overlay,
                scaleMin,
                scaleMax,
                width,
                height);
    }

    /** The types of plot that plotEx() can draw. */
    enum PlotType {
        LINES,
        HISTOGRAM
    }

    /**
     * Implementation of the plot widgets.
     *
     * @param plotType The type of plot.
     * @param label The label, which is also used for the ID.
     * @param valuesGetter Fetches the value at an index.
     * @param count The number of values.
     * @param offset The index of the first value to display, wrapping around.
     * @param overlay Text to display over the top of the plot, may be null.
     * @param scaleMin The value at the bottom of the plot, Float.MAX_VALUE to use the minimum.
     * @param scaleMax The value at the top of the plot, Float.MAX_VALUE to use the maximum.
     * @param width The width, 0 for the default item width, negative to align to the right edge.
     * @param height The height, 0 for the default height.
     * @return The index of the hovered value, or -1 if none are hovered.
     */
    static int plotEx(
            @NonNull PlotType plotType,
            @NonNull String label,
            @NonNull IntFunction<Float> valuesGetter,
            int count,
            int offset,
            String overlay,
            float scaleMin,
            float scaleMax,
            float width,
            float height) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return -1;
        }

        final StyleVariables style = context.style.variable;
        final int id = window.getID(label);

        final String displayedLabel = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayedLabel, false, -1.0f);
        final Vector2f frameSize =
                IkGuiInternal.calcItemSize(
                        new Vector2f(width, height),
                        IkGuiImplUtils.calculateItemWidth(),
                        labelSize.y + style.framePadding.y * 2.0f);

        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat frameBB = new RectFloat(x, y, x + frameSize.x, y + frameSize.y);
        final RectFloat innerBB =
                new RectFloat(
                        frameBB.getLeft() + style.framePadding.x,
                        frameBB.getTop() + style.framePadding.y,
                        frameBB.getRight() - style.framePadding.x,
                        frameBB.getBottom() - style.framePadding.y);
        final RectFloat totalBB =
                new RectFloat(
                        frameBB.getLeft(),
                        frameBB.getTop(),
                        frameBB.getRight()
                                + (labelSize.x > 0.0f
                                        ? style.itemInnerSpacing.x + labelSize.x
                                        : 0.0f),
                        frameBB.getBottom());
        IkGuiInternal.itemSize(totalBB, style.framePadding.y);
        if (!IkGuiInternal.itemAdd(totalBB, id, frameBB, ItemFlags.NO_NAV)) {
            return -1;
        }
        final IkBoolean hovered = new IkBoolean(false);
        IkGuiInternal.buttonBehavior(frameBB, id, hovered, null, ButtonFlags.NONE);

        // Determine the scale from the values if not specified
        float min = scaleMin;
        float max = scaleMax;
        if (min == Float.MAX_VALUE || max == Float.MAX_VALUE) {
            float valueMin = Float.MAX_VALUE;
            float valueMax = -Float.MAX_VALUE;
            for (int i = 0; i < count; ++i) {
                final float value = valuesGetter.apply(i);
                if (Float.isNaN(value)) {
                    continue;
                }
                valueMin = Math.min(valueMin, value);
                valueMax = Math.max(valueMax, value);
            }
            if (min == Float.MAX_VALUE) {
                min = valueMin;
            }
            if (max == Float.MAX_VALUE) {
                max = valueMax;
            }
        }

        IkGuiInternal.renderFrame(
                frameBB.getLeft(),
                frameBB.getTop(),
                frameBB.getRight(),
                frameBB.getBottom(),
                IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.FRAME_BACKGROUND),
                true,
                style.frameRounding);

        final boolean lines = plotType == PlotType.LINES;
        final int countMin = lines ? 2 : 1;
        int indexHovered = -1;
        if (count >= countMin) {
            final int resolutionWidth = Math.min((int) frameSize.x, count) + (lines ? -1 : 0);
            final int itemCount = count + (lines ? -1 : 0);

            // Tooltip on hover
            final Vector2f mouse = context.io.mousePosition;
            if (hovered.get() && innerBB.contains(mouse.x, mouse.y)) {
                final float t =
                        MathUtil.clamp(
                                (mouse.x - innerBB.getLeft()) / innerBB.getWidth(), 0.0f, 0.999f);
                final int valueIndex = (int) (t * itemCount);

                final float v0 = valuesGetter.apply((valueIndex + offset) % count);
                final float v1 = valuesGetter.apply((valueIndex + 1 + offset) % count);
                if (lines) {
                    IkGuiImplPopups.setTooltip(
                            String.format(
                                    Locale.ROOT,
                                    "%d: %8.4g\n%d: %8.4g",
                                    valueIndex,
                                    v0,
                                    valueIndex + 1,
                                    v1));
                } else {
                    IkGuiImplPopups.setTooltip(
                            String.format(Locale.ROOT, "%d: %8.4g", valueIndex, v0));
                }
                indexHovered = valueIndex;
            }

            final float tStep = 1.0f / resolutionWidth;
            final float inverseScale = min == max ? 0.0f : 1.0f / (max - min);

            final float firstValue = valuesGetter.apply(offset % count);
            float t0 = 0.0f;
            // Point in the normalized space of our target rectangle
            float tp0X = t0;
            float tp0Y = 1.0f - saturate((firstValue - min) * inverseScale);
            // Where the zero line stands
            final float histogramZeroLineT;
            if (min * max < 0.0f) {
                histogramZeroLineT = 1 + min * inverseScale;
            } else {
                histogramZeroLineT = min < 0.0f ? 0.0f : 1.0f;
            }

            final int colorBase =
                    IkGuiImplUtils.getColorWithGlobalAlpha(
                            lines ? ColorType.PLOT_LINES : ColorType.PLOT_HISTOGRAM);
            final int colorHovered =
                    IkGuiImplUtils.getColorWithGlobalAlpha(
                            lines
                                    ? ColorType.PLOT_LINES_HOVERED
                                    : ColorType.PLOT_HISTOGRAM_HOVERED);

            final DrawList drawList = window.drawList;
            final float innerWidth = innerBB.getWidth();
            final float innerHeight = innerBB.getHeight();
            for (int n = 0; n < resolutionWidth; ++n) {
                final float t1 = t0 + tStep;
                final int v1Index = (int) (t0 * itemCount + 0.5f);
                final float v1 = valuesGetter.apply((v1Index + offset + 1) % count);
                final float tp1X = t1;
                final float tp1Y = 1.0f - saturate((v1 - min) * inverseScale);

                final float pos0X = innerBB.getLeft() + innerWidth * tp0X;
                final float pos0Y = innerBB.getTop() + innerHeight * tp0Y;
                float pos1X = innerBB.getLeft() + innerWidth * tp1X;
                final float pos1Y =
                        innerBB.getTop() + innerHeight * (lines ? tp1Y : histogramZeroLineT);
                final int color = indexHovered == v1Index ? colorHovered : colorBase;
                if (lines) {
                    drawList.addLine(pos0X, pos0Y, pos1X, pos1Y, color);
                } else {
                    if (pos1X >= pos0X + 2.0f) {
                        pos1X -= 1.0f;
                    }
                    // The zero line may be above or below the value
                    drawList.addRectFilled(
                            pos0X, Math.min(pos0Y, pos1Y), pos1X, Math.max(pos0Y, pos1Y), color);
                }

                t0 = t1;
                tp0X = tp1X;
                tp0Y = tp1Y;
            }
        }

        // Text overlay
        if (overlay != null) {
            IkGuiInternal.renderTextClipped(
                    frameBB.getLeft(),
                    frameBB.getTop() + style.framePadding.y,
                    frameBB.getRight(),
                    frameBB.getBottom(),
                    overlay,
                    null,
                    0.5f,
                    0.0f,
                    null);
        }

        if (labelSize.x > 0.0f) {
            IkGuiInternal.renderText(
                    frameBB.getRight() + style.itemInnerSpacing.x,
                    innerBB.getTop(),
                    displayedLabel,
                    false);
        }

        IkGuiInternal.testEngineItemInfo(id, label, context.lastItemData.statusFlags);
        return indexHovered;
    }

    /**
     * A progress bar.
     *
     * @param fraction The progress, from 0.0 (0%) to 1.0 (100%). Negative values display an
     *     indeterminate progress bar animation, which must be animated along with time, so e.g.
     *     passing -1.0f * time works.
     * @param width The width, 0 for the default item width, negative to align to the right edge.
     * @param height The height, 0 for the default height.
     * @param overlayText Text to display, null to display the percentage (or nothing for
     *     indeterminate bars).
     */
    public static void progressBar(float fraction, float width, float height, String overlayText) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return;
        }

        final StyleVariables style = context.style.variable;
        final Vector2f size =
                IkGuiInternal.calcItemSize(
                        new Vector2f(width, height),
                        IkGuiImplUtils.calculateItemWidth(),
                        IkGuiInternal.getFontSize() + style.framePadding.y * 2.0f);
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat bb = new RectFloat(x, y, x + size.x, y + size.y);
        IkGuiInternal.itemSize(size.x, size.y, style.framePadding.y);
        if (!IkGuiInternal.itemAdd(bb, 0)) {
            return;
        }

        final boolean isIndeterminate = fraction < 0.0f;
        float progress = fraction;
        if (!isIndeterminate) {
            progress = saturate(progress);
        }

        // Out of courtesy we accept a NaN fraction without crashing
        float fill0 = 0.0f;
        float fill1 = Float.isNaN(progress) ? 0.0f : progress;

        if (isIndeterminate) {
            final float fillWidth = 0.2f;
            fill0 = ((-progress) % 1.0f) * (1.0f + fillWidth) - fillWidth;
            fill1 = saturate(fill0 + fillWidth);
            fill0 = saturate(fill0);
        }

        // Render
        IkGuiInternal.renderFrame(
                bb.getLeft(),
                bb.getTop(),
                bb.getRight(),
                bb.getBottom(),
                IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.FRAME_BACKGROUND),
                true,
                style.frameRounding);
        bb.expand(-style.frameBorderSize, -style.frameBorderSize);
        final float fillX0 = bb.getLeft() + (bb.getRight() - bb.getLeft()) * fill0;
        final float fillX1 = bb.getLeft() + (bb.getRight() - bb.getLeft()) * fill1;
        if (fillX0 < fillX1) {
            // Draw the whole rounded bar clipped to the filled range, so the fill follows the
            // rounded edges of the frame.
            final DrawList drawList = window.drawList;
            drawList.pushClipRect(fillX0, bb.getTop(), fillX1, bb.getBottom(), true);
            drawList.addRectFilled(
                    bb.getLeft(),
                    bb.getTop(),
                    bb.getRight(),
                    bb.getBottom(),
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.PLOT_HISTOGRAM),
                    style.frameRounding,
                    DrawFlags.ROUND_CORNERS_ALL);
            drawList.popClipRect();
        }

        // Default to displaying the fraction as a percentage, but the user can override it.
        // Don't display text for indeterminate bars by default.
        if (!isIndeterminate || overlayText != null) {
            final String overlay =
                    overlayText != null
                            ? overlayText
                            : String.format(Locale.ROOT, "%.0f%%", progress * 100 + 0.01f);

            final Vector2f overlaySize = new Vector2f();
            IkGuiImplUtils.calcTextSize(overlaySize, overlay, false, -1.0f);
            if (overlaySize.x > 0.0f) {
                final float textX =
                        isIndeterminate
                                ? (bb.getLeft() + bb.getRight() - overlaySize.x) * 0.5f
                                : fillX1 + style.itemSpacing.x;
                IkGuiInternal.renderTextClipped(
                        MathUtil.clamp(
                                textX,
                                bb.getLeft(),
                                Math.max(
                                        bb.getLeft(),
                                        bb.getRight() - overlaySize.x - style.itemInnerSpacing.x)),
                        bb.getTop(),
                        bb.getRight(),
                        bb.getBottom(),
                        overlay,
                        overlaySize,
                        0.0f,
                        0.5f,
                        bb);
            }
        }
    }

    private static float saturate(float value) {
        return MathUtil.clamp(value, 0.0f, 1.0f);
    }

    /**
     * A radio button, which doesn't store its own state.
     *
     * @param label The label, which is also used for the ID.
     * @param active Whether the radio button is currently selected.
     * @return True if the radio button was clicked.
     */
    public static boolean radioButton(@NonNull String label, boolean active) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }

        final StyleVariables style = context.style.variable;
        final int id = window.getID(label);
        final String displayedLabel = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayedLabel, false, -1.0f);

        final float squareSize = IkGuiImplLayout.getFrameHeight();
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat totalBB =
                new RectFloat(
                        x,
                        y,
                        x
                                + squareSize
                                + (labelSize.x > 0.0f
                                        ? style.itemInnerSpacing.x + labelSize.x
                                        : 0.0f),
                        y + labelSize.y + style.framePadding.y * 2.0f);
        IkGuiInternal.itemSize(totalBB, style.framePadding.y);
        if (!IkGuiInternal.itemAdd(totalBB, id)) {
            return false;
        }

        final float centerX = Math.round(x + squareSize * 0.5f);
        final float centerY = Math.round(y + squareSize * 0.5f);
        final float radius = (squareSize - 1.0f) * 0.5f;

        final IkBoolean hovered = new IkBoolean();
        final IkBoolean held = new IkBoolean();
        final boolean pressed =
                IkGuiInternal.buttonBehavior(totalBB, id, hovered, held, ButtonFlags.NONE);
        if (pressed) {
            IkGuiInternal.markItemEdited(id);
        }

        IkGuiImplNav.renderNavCursor(totalBB, id, NavRenderCursorFlags.NONE, -1.0f);
        // Render
        window.drawList.addCircleFilled(
                centerX,
                centerY,
                radius,
                IkGuiImplUtils.getColorWithGlobalAlpha(
                        (held.get() && hovered.get())
                                ? ColorType.FRAME_BACKGROUND_ACTIVE
                                : hovered.get()
                                        ? ColorType.FRAME_BACKGROUND_HOVERED
                                        : ColorType.FRAME_BACKGROUND));
        // A mixed value shows neither on nor off
        if (active && (context.lastItemData.itemFlags & ItemFlags.MIXED_VALUE) == 0) {
            final float pad = Math.max(1.0f, IkGuiInternal.truncate(squareSize / 6.0f));
            window.drawList.addCircleFilled(
                    centerX,
                    centerY,
                    radius - pad,
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.CHECK_MARK));
        }
        if (style.frameBorderSize > 0.0f) {
            window.drawList.addCircle(
                    centerX,
                    centerY,
                    radius,
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.BORDER),
                    style.frameBorderSize);
        }

        if (context.logEnabled) {
            IkGuiImplLogging.logRenderedText(y + style.framePadding.y, active ? "(x)" : "( )");
        }
        if (labelSize.x > 0.0f) {
            IkGuiInternal.renderText(
                    x + squareSize + style.itemInnerSpacing.x,
                    y + style.framePadding.y,
                    displayedLabel,
                    false);
        }
        IkGuiInternal.testEngineItemInfo(id, label, context.lastItemData.statusFlags);
        return pressed;
    }

    /**
     * A radio button that sets an int to a specific value when clicked.
     *
     * @param label The label, which is also used for the ID.
     * @param selectionStorage The currently selected value.
     * @param value The value this radio button represents.
     * @return True if the radio button was clicked.
     */
    public static boolean radioButton(
            @NonNull String label, @NonNull IkInt selectionStorage, int value) {
        final boolean pressed = radioButton(label, selectionStorage.get() == value);
        if (pressed) {
            selectionStorage.set(value);
        }
        return pressed;
    }

    /**
     * A selectable item, which is highlighted when selected. Selectables are tightly packed with no
     * gap between them, and by default span the available width.
     *
     * @param label The label, which is also used for the ID.
     * @param selected Whether the item is drawn as selected.
     * @param selectableFlags Flags for the selectable.
     * @param width The width, 0 to use the remaining width.
     * @param height The height, 0 to use the label height.
     * @return True if the item was clicked.
     * @see SelectableFlags
     */
    public static boolean selectable(
            @NonNull String label,
            boolean selected,
            int selectableFlags,
            float width,
            float height) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }

        final StyleVariables style = context.style.variable;

        // Submit the label or explicit size to itemSize(), whereas itemAdd() will submit a
        // larger/spanning rectangle
        final int id = window.getID(label);
        final String displayedLabel = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayedLabel, false, -1.0f);
        float sizeX = width != 0.0f ? width : labelSize.x;
        final float sizeY = height != 0.0f ? height : labelSize.y;
        final float posX = window.cursorPosition.x;
        final float posY = window.cursorPosition.y + window.baseOffsetCurrentLine;
        IkGuiInternal.itemSize(sizeX, sizeY, 0.0f);

        // Fill horizontal space. We don't support negative sizes because the item spacing
        // extension would make explicitly right-aligned sizes not visibly match other widgets.
        final boolean spanAllColumns = (selectableFlags & SelectableFlags.SPAN_ALL_COLUMNS) != 0;
        final float minX = spanAllColumns ? window.rectParentWork.getLeft() : posX;
        final float maxX =
                spanAllColumns ? window.rectParentWork.getRight() : window.rectWork.getRight();
        if (width == 0.0f
                || (selectableFlags & SelectableFlags.INTERNAL_SPAN_AVAILABLE_WIDTH) != 0) {
            sizeX = Math.max(labelSize.x, maxX - minX);
        }

        // Selectables are meant to be tightly packed together with no click-gap, so we extend
        // their box to cover the spacing between selectables
        final RectFloat bb = new RectFloat(minX, posY, minX + sizeX, posY + sizeY);
        if ((selectableFlags & SelectableFlags.INTERNAL_NO_PAD_WITH_HALF_SPACING) == 0) {
            final float spacingX = spanAllColumns ? 0.0f : style.itemSpacing.x;
            final float spacingY = style.itemSpacing.y;
            final float spacingLeft = IkGuiInternal.truncate(spacingX * 0.50f);
            final float spacingUp = IkGuiInternal.truncate(spacingY * 0.50f);
            bb.set(
                    bb.getLeft() - spacingLeft,
                    bb.getTop() - spacingUp,
                    bb.getRight() + (spacingX - spacingLeft),
                    bb.getBottom() + (spacingY - spacingUp));
        }

        final boolean disabledItem = (selectableFlags & SelectableFlags.DISABLED) != 0;
        final int extraItemFlags = disabledItem ? ItemFlags.DISABLED : ItemFlags.NONE;
        final boolean isVisible;
        if (spanAllColumns) {
            // Modify the clip rect for itemAdd(), faster than pushing a full clip rect
            final RectFloat clip = window.rectCurrentClip;
            final float backupClipMinX = clip.getLeft();
            final float backupClipMaxX = clip.getRight();
            clip.set(
                    window.rectParentWork.getLeft(),
                    clip.getTop(),
                    window.rectParentWork.getRight(),
                    clip.getBottom());
            isVisible = IkGuiInternal.itemAdd(bb, id, null, extraItemFlags);
            clip.set(backupClipMinX, clip.getTop(), backupClipMaxX, clip.getBottom());
        } else {
            isVisible = IkGuiInternal.itemAdd(bb, id, null, extraItemFlags);
        }
        final boolean isMultiSelect =
                (context.lastItemData.itemFlags & ItemFlags.INTERNAL_IS_MULTI_SELECT) != 0;
        // Extra layer of "no logic clip" for box-select support
        if (!isVisible && !IkGuiImplMultiSelect.isUnclippedByBoxSelect(bb)) {
            return false;
        }

        final boolean disabledGlobal = (context.currentItemFlags & ItemFlags.DISABLED) != 0;
        if (disabledItem && !disabledGlobal) {
            IkGuiImplUtils.beginDisabled(true);
        }

        if (spanAllColumns && context.currentTable != null) {
            IkGuiImplTables.tablePushBackgroundChannel();
            context.lastItemData.statusFlags |= ItemStatusFlags.HAS_CLIP_RECT;
            context.lastItemData.clipRect.set(window.rectCurrentClip);
        }

        // We use NO_HOLDING_ACTIVE_ID on menus so users can click and hold on a menu then drag to
        // browse child entries
        int buttonFlags = ButtonFlags.NONE;
        if ((selectableFlags & SelectableFlags.INTERNAL_NO_HOLDING_ACTIVE_ID) != 0) {
            buttonFlags |= ButtonFlags.INTERNAL_NO_HOLDING_ACTIVE_ID;
        }
        if ((selectableFlags & SelectableFlags.INTERNAL_SELECT_ON_CLICK) != 0) {
            buttonFlags |= ButtonFlags.INTERNAL_PRESSED_ON_CLICK;
        }
        if ((selectableFlags & SelectableFlags.INTERNAL_SELECT_ON_RELEASE) != 0) {
            buttonFlags |= ButtonFlags.INTERNAL_PRESSED_ON_RELEASE;
        }
        if ((selectableFlags & SelectableFlags.INTERNAL_NO_SET_KEY_OWNER) != 0) {
            buttonFlags |= ButtonFlags.INTERNAL_NO_SET_KEY_OWNER;
        }
        if ((selectableFlags & SelectableFlags.ALLOW_DOUBLE_CLICK) != 0) {
            buttonFlags |=
                    ButtonFlags.INTERNAL_PRESSED_ON_CLICK_RELEASE
                            | ButtonFlags.INTERNAL_PRESSED_ON_DOUBLE_CLICK;
        }
        if ((selectableFlags & SelectableFlags.ALLOW_OVERLAP) != 0
                || (context.lastItemData.itemFlags & ItemFlags.ALLOW_OVERLAP) != 0) {
            buttonFlags |= ButtonFlags.ALLOW_OVERLAP;
        }

        // Multi-selection support (header)
        final boolean wasSelected = selected;
        final IkBoolean selectedState = new IkBoolean(selected);
        if (isMultiSelect) {
            // Handle multi-select and alter the button flags for it
            final IkInt multiSelectButtonFlags = new IkInt(buttonFlags);
            IkGuiImplMultiSelect.multiSelectItemHeader(id, selectedState, multiSelectButtonFlags);
            buttonFlags = multiSelectButtonFlags.get();
            selected = selectedState.get();
        }

        final IkBoolean hovered = new IkBoolean();
        final IkBoolean held = new IkBoolean();
        boolean pressed = IkGuiInternal.buttonBehavior(bb, id, hovered, held, buttonFlags);
        boolean autoSelected = false;

        // Multi-selection support (footer)
        if (isMultiSelect) {
            final IkBoolean pressedState = new IkBoolean(pressed);
            IkGuiImplMultiSelect.multiSelectItemFooter(
                    id, selectedState, pressedState, MultiSelectFlags.NONE);
            selected = selectedState.get();
            pressed = pressedState.get();
        } else if ((selectableFlags & SelectableFlags.SELECT_ON_NAV) != 0
                && context.navJustMovedToID != 0
                && context.navJustMovedToFocusScopeID == context.currentFocusScopeID
                && !context.navJustMovedToIsInit
                && context.navJustMovedToID == id
                && (context.navJustMovedToKeyMods & KeyModFlags.CTRL) == 0) {
            // Auto-select when moved into with navigation
            selected = pressed = autoSelected = true;
        }

        // Update the nav ID when clicking or when hovering, so navigation can be resumed with
        // the keyboard/gamepad
        if ((pressed
                        || (hovered.get()
                                && (selectableFlags & SelectableFlags.INTERNAL_SET_NAV_ID_ON_HOVER)
                                        != 0))
                && !context.navHighlightItemUnderNav
                && context.navFocusedWindow == window
                && context.navLayer == window.navLayerCurrent) {
            IkGuiImplNav.setNavID(
                    id,
                    window.navLayerCurrent,
                    context.currentFocusScopeID,
                    IkGuiImplNav.windowRectAbsToRel(window, bb, new RectFloat(0, 0, 0, 0)));
            if (context.io.configNavCursorVisibleAuto) {
                context.navCursorVisible = false;
            }
        }
        if (pressed) {
            IkGuiInternal.markItemEdited(id);
        }

        if (selected != wasSelected) {
            context.lastItemData.statusFlags |= ItemStatusFlags.TOGGLED_SELECTION;
        }
        if (context.activeID == id && context.activeIDIsJustActivated) {
            // Remember the selection at the time of the click, for delayed clicks
            context.activeIDWasSelected = wasSelected;
            context.activeIDWasSoleSelected =
                    wasSelected
                            && (!isMultiSelect
                                    || context.currentMultiSelect.isSoleOrUnknownSelectionSize);
        }

        // Render
        final boolean highlighted =
                hovered.get() || (selectableFlags & SelectableFlags.HIGHLIGHT) != 0;
        if (isVisible && (highlighted || selected)) {
            final ColorType colorType;
            if (held.get() && highlighted) {
                colorType = ColorType.HEADER_ACTIVE;
            } else if (highlighted) {
                colorType = ColorType.HEADER_HOVERED;
            } else {
                colorType = ColorType.HEADER;
            }
            IkGuiInternal.renderFrame(
                    bb.getLeft(),
                    bb.getTop(),
                    bb.getRight(),
                    bb.getBottom(),
                    IkGuiImplUtils.getColorWithGlobalAlpha(colorType),
                    false,
                    style.selectableRounding);
        }
        if (isVisible && context.navID == id) {
            // Always show the nav rectangle in multi-select scopes
            IkGuiImplNav.renderNavCursor(
                    bb,
                    id,
                    NavRenderCursorFlags.COMPACT
                            | (isMultiSelect
                                    ? NavRenderCursorFlags.ALWAYS_DRAW
                                    : NavRenderCursorFlags.NONE),
                    style.selectableRounding);
        }

        if (spanAllColumns && context.currentTable != null) {
            IkGuiImplTables.tablePopBackgroundChannel();
        }

        // Text stays at the submission position. Alignment/clipping extents ignore
        // SPAN_ALL_COLUMNS.
        if (isVisible) {
            IkGuiInternal.renderTextClipped(
                    posX,
                    posY,
                    Math.min(posX + sizeX, window.rectWork.getRight()),
                    posY + sizeY,
                    displayedLabel,
                    labelSize,
                    style.selectableTextAlign.x,
                    style.selectableTextAlign.y,
                    bb);
        }

        // Automatically close popups
        if (pressed
                && !autoSelected
                && (window.flags & WindowFlags.INTERNAL_POPUP) != 0
                && (selectableFlags & SelectableFlags.NO_AUTO_CLOSE_POPUPS) == 0
                && (context.lastItemData.itemFlags & ItemFlags.AUTO_CLOSE_POPUPS) != 0) {
            IkGuiImplPopups.closeCurrentPopup();
        }

        if (disabledItem && !disabledGlobal) {
            IkGuiImplUtils.endDisabled();
        }

        // This always returns the pressed state, not the selected state
        IkGuiInternal.testEngineItemInfo(id, label, context.lastItemData.statusFlags);
        return pressed;
    }

    /**
     * A selectable item, which toggles the selected value when clicked.
     *
     * @param label The label, which is also used for the ID.
     * @param selected Whether the item is selected, toggled when clicked.
     * @param selectableFlags Flags for the selectable.
     * @param width The width, 0 to use the remaining width.
     * @param height The height, 0 to use the label height.
     * @return True if the item was clicked.
     * @see SelectableFlags
     */
    public static boolean selectable(
            @NonNull String label,
            @NonNull IkBoolean selected,
            int selectableFlags,
            float width,
            float height) {
        if (selectable(label, selected.get(), selectableFlags, width, height)) {
            selected.set(!selected.get());
            return true;
        }
        return false;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplMiscWidgets() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
