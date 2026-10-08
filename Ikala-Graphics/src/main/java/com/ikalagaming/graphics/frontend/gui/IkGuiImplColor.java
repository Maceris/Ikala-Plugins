package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.ColorButtonPosition;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.SliderDataType;
import com.ikalagaming.graphics.frontend.gui.flags.ButtonFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ColorEditFlags;
import com.ikalagaming.graphics.frontend.gui.flags.DragDropFlags;
import com.ikalagaming.graphics.frontend.gui.flags.DrawFlags;
import com.ikalagaming.graphics.frontend.gui.flags.HoveredFlags;
import com.ikalagaming.graphics.frontend.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemStatusFlags;
import com.ikalagaming.graphics.frontend.gui.flags.NavRenderCursorFlags;
import com.ikalagaming.graphics.frontend.gui.flags.PopupFlags;
import com.ikalagaming.graphics.frontend.gui.flags.SelectableFlags;
import com.ikalagaming.graphics.frontend.gui.flags.SliderFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Color;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.MathUtil;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.Locale;

/** Color edits, color pickers, color buttons, and their helpers. */
@Slf4j
class IkGuiImplColor {

    /** Drag and drop payload type for colors without alpha, as a float[3]. */
    static final String PAYLOAD_TYPE_COLOR_3F = "_COL3F";

    /** Drag and drop payload type for colors with alpha, as a float[4]. */
    static final String PAYLOAD_TYPE_COLOR_4F = "_COL4F";

    private static final String[] COMPONENT_IDS = {"##X", "##Y", "##Z", "##W"};

    /** Short display, then long display for RGBA, then long display for HSVA. */
    private static final String[][] FORMAT_TABLE_INT = {
        {"%3d", "%3d", "%3d", "%3d"},
        {"R:%3d", "G:%3d", "B:%3d", "A:%3d"},
        {"H:%3d", "S:%3d", "V:%3d", "A:%3d"}
    };

    /** Short display, then long display for RGBA, then long display for HSVA. */
    private static final String[][] FORMAT_TABLE_FLOAT = {
        {"%.3f", "%.3f", "%.3f", "%.3f"},
        {"R:%.3f", "G:%.3f", "B:%.3f", "A:%.3f"},
        {"H:%.3f", "S:%.3f", "V:%.3f", "A:%.3f"}
    };

    static Context context;

    /**
     * Edit an RGB color, with each component in the range 0-1.
     *
     * @param label The label, which is also used for the ID.
     * @param color The color, which must have at least 3 elements.
     * @param colorEditFlags Flags for the edit.
     * @return True if the value was changed.
     * @see ColorEditFlags
     */
    public static boolean colorEdit3(String label, float[] color, int colorEditFlags) {
        return colorEdit4(label, color, colorEditFlags | ColorEditFlags.NO_ALPHA);
    }

    /**
     * Edit an RGBA color, with each component in the range 0-1. Left-click on the color square to
     * open the color picker, right-click to open the options menu.
     *
     * @param label The label, which is also used for the ID.
     * @param color The color, which must have at least 4 elements, or 3 if the NO_ALPHA flag is
     *     set.
     * @param colorEditFlags Flags for the edit.
     * @return True if the value was changed.
     * @see ColorEditFlags
     */
    public static boolean colorEdit4(@NonNull String label, float[] color, int colorEditFlags) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (!validateColor(color, colorEditFlags)) {
            return false;
        }

        int flags = colorEditFlags;
        final StyleVariables style = context.style.variable;
        final float squareSize = IkGuiImplLayout.getFrameHeight();
        final String displayedLabel = Hash.getDisplayedText(label);
        float widthFull = IkGuiImplUtils.calculateItemWidth();
        context.nextItemData.clearFlags();

        IkGuiImplLayout.beginGroup();
        IkGuiImplUtils.pushID(label);
        final boolean setCurrentColorEditID = context.colorEditCurrentID == 0;
        if (setCurrentColorEditID) {
            context.colorEditCurrentID = window.idStack.peek();
        }

        // If we're not showing any slider there's no point in doing any HSV conversions
        final int flagsUntouched = flags;
        if ((flags & ColorEditFlags.NO_INPUTS) != 0) {
            flags =
                    (flags & ~ColorEditFlags.DISPLAY_MASK)
                            | ColorEditFlags.DISPLAY_RGB
                            | ColorEditFlags.NO_OPTIONS;
        }

        // Context menu: display and modify options (before defaults are applied)
        if ((flags & ColorEditFlags.NO_OPTIONS) == 0) {
            colorEditOptionsPopup(color, flags);
        }

        // Read stored options
        flags = applyStoredOptions(flags);

        final boolean alpha = (flags & ColorEditFlags.NO_ALPHA) == 0;
        final boolean hdr = (flags & ColorEditFlags.HDR) != 0;
        final int components = alpha ? 4 : 3;
        final float widthButton =
                (flags & ColorEditFlags.NO_SMALL_PREVIEW) != 0
                        ? 0.0f
                        : squareSize + style.itemInnerSpacing.x;
        final float widthInputs = Math.max(widthFull - widthButton, 1.0f);
        widthFull = widthInputs + widthButton;

        // Convert to the formats we need
        final float[] f = {color[0], color[1], color[2], alpha ? color[3] : 1.0f};
        if ((flags & ColorEditFlags.INPUT_HSV) != 0 && (flags & ColorEditFlags.DISPLAY_RGB) != 0) {
            hsvToRgbInPlace(f);
        } else if ((flags & ColorEditFlags.INPUT_RGB) != 0
                && (flags & ColorEditFlags.DISPLAY_HSV) != 0) {
            // Hue is lost when converting from grayscale rgb (saturation=0). Restore it.
            rgbToHsvInPlace(f);
            colorEditRestoreHS(color, f);
        }
        final int[] i = {
            floatToInt8Unbound(f[0]),
            floatToInt8Unbound(f[1]),
            floatToInt8Unbound(f[2]),
            floatToInt8Unbound(f[3])
        };

        boolean valueChanged = false;
        boolean valueChangedAsFloat = false;

        final float posX = window.cursorPosition.x;
        final float posY = window.cursorPosition.y;
        final float inputsOffsetX =
                style.colorButtonPosition == ColorButtonPosition.LEFT ? widthButton : 0.0f;
        window.cursorPosition.x = posX + inputsOffsetX;

        if ((flags & (ColorEditFlags.DISPLAY_RGB | ColorEditFlags.DISPLAY_HSV)) != 0
                && (flags & ColorEditFlags.NO_INPUTS) == 0) {
            // RGB/HSV 0..255 Sliders
            final float widthItems = widthInputs - style.itemInnerSpacing.x * (components - 1);
            final float widthPerComponent = IkGuiInternal.truncate(widthItems / components);
            final boolean drawColorMarker =
                    (flags & (ColorEditFlags.DISPLAY_HSV | ColorEditFlags.NO_COLOR_MARKERS)) == 0;

            final Vector2f prefixSize = new Vector2f();
            IkGuiImplUtils.calcTextSize(
                    prefixSize,
                    (flags & ColorEditFlags.FLOAT) != 0 ? "M:0.000" : "M:000",
                    false,
                    -1.0f);
            final boolean hidePrefix = drawColorMarker || widthPerComponent <= prefixSize.x;
            final int formatIndex;
            if (hidePrefix) {
                formatIndex = 0;
            } else {
                formatIndex = (flags & ColorEditFlags.DISPLAY_HSV) != 0 ? 2 : 1;
            }
            final int dragFlags = drawColorMarker ? SliderFlags.COLOR_MARKERS : SliderFlags.NONE;

            float previousSplit = 0.0f;
            for (int n = 0; n < components; ++n) {
                if (n > 0) {
                    IkGuiImplLayout.sameLine(0, style.itemInnerSpacing.x);
                }
                final float nextSplit = IkGuiInternal.truncate(widthItems * (n + 1) / components);
                IkGuiImplUtils.setNextItemWidth(Math.max(nextSplit - previousSplit, 1.0f));
                previousSplit = nextSplit;
                if (drawColorMarker) {
                    IkGuiImplSliders.setNextItemColorMarker(
                            IkGuiImplSliders.DEFAULT_RGBA_COLOR_MARKERS[n]);
                }

                // FIXME(from ImGui): When the HDR flag is passed HS values snap in weird ways when
                // SV values go below 0.
                if ((flags & ColorEditFlags.FLOAT) != 0) {
                    valueChanged |=
                            IkGuiImplSliders.dragScalarIndex(
                                    COMPONENT_IDS[n],
                                    SliderDataType.FLOAT,
                                    f,
                                    n,
                                    1.0f / 255.0f,
                                    0.0f,
                                    hdr ? 0.0f : 1.0f,
                                    FORMAT_TABLE_FLOAT[formatIndex][n],
                                    dragFlags);
                    valueChangedAsFloat |= valueChanged;
                } else {
                    valueChanged |=
                            IkGuiImplSliders.dragScalarIndex(
                                    COMPONENT_IDS[n],
                                    SliderDataType.INT,
                                    i,
                                    n,
                                    1.0f,
                                    0,
                                    hdr ? 0 : 255,
                                    FORMAT_TABLE_INT[formatIndex][n],
                                    dragFlags);
                }
                if ((flags & ColorEditFlags.NO_OPTIONS) == 0) {
                    IkGuiImplPopups.openPopupOnItemClick("context", PopupFlags.MOUSE_BUTTON_RIGHT);
                }
            }
        } else if ((flags & ColorEditFlags.DISPLAY_HEX) != 0
                && (flags & ColorEditFlags.NO_INPUTS) == 0) {
            // RGB Hexadecimal Input
            final String hex;
            if (alpha) {
                hex =
                        String.format(
                                Locale.ROOT,
                                "#%02X%02X%02X%02X",
                                MathUtil.clamp(i[0], 0, 255),
                                MathUtil.clamp(i[1], 0, 255),
                                MathUtil.clamp(i[2], 0, 255),
                                MathUtil.clamp(i[3], 0, 255));
            } else {
                hex =
                        String.format(
                                Locale.ROOT,
                                "#%02X%02X%02X",
                                MathUtil.clamp(i[0], 0, 255),
                                MathUtil.clamp(i[1], 0, 255),
                                MathUtil.clamp(i[2], 0, 255));
            }
            final IkString buffer = new IkString(hex, 64);
            IkGuiImplUtils.setNextItemWidth(widthInputs);
            if (IkGuiImplInputText.inputText(
                    "##Text", buffer, InputTextFlags.CHARS_UPPERCASE, null)) {
                valueChanged = true;
                parseHexColor(buffer.get(), alpha, i);
            }
            if ((flags & ColorEditFlags.NO_OPTIONS) == 0) {
                IkGuiImplPopups.openPopupOnItemClick("context", PopupFlags.MOUSE_BUTTON_RIGHT);
            }
        }

        Window pickerActiveWindow = null;
        if ((flags & ColorEditFlags.NO_SMALL_PREVIEW) == 0) {
            final float buttonOffsetX =
                    ((flags & ColorEditFlags.NO_INPUTS) != 0
                                    || style.colorButtonPosition == ColorButtonPosition.LEFT)
                            ? 0.0f
                            : widthInputs + style.itemInnerSpacing.x;
            window.cursorPosition.set(posX + buttonOffsetX, posY);

            final float[] previewColor = {color[0], color[1], color[2], alpha ? color[3] : 1.0f};
            if (colorButton("##ColorButton", previewColor, flags, 0, 0)
                    && (flags & ColorEditFlags.NO_PICKER) == 0) {
                // Store the current color and open a picker
                System.arraycopy(previewColor, 0, context.colorPickerReference, 0, 4);
                IkGuiImplPopups.openPopup("picker", PopupFlags.NONE);
                final RectFloat buttonRect = context.lastItemData.rect;
                IkGuiImplWindows.setNextWindowPos(
                        buttonRect.getLeft(),
                        buttonRect.getBottom() + style.itemSpacing.y,
                        Condition.ALWAYS,
                        0.0f,
                        0.0f);
            }
            if ((flags & ColorEditFlags.NO_OPTIONS) == 0) {
                IkGuiImplPopups.openPopupOnItemClick("context", PopupFlags.MOUSE_BUTTON_RIGHT);
            }

            if (IkGuiImplPopups.beginPopup("picker", WindowFlags.NONE)) {
                if (context.windowCurrent.beginCount == 1) {
                    pickerActiveWindow = context.windowCurrent;
                    if (!displayedLabel.isEmpty()) {
                        IkGuiImplText.textEx(displayedLabel);
                        IkGuiImplLayout.spacing();
                    }
                    final int pickerFlagsToForward =
                            ColorEditFlags.DATA_TYPE_MASK
                                    | ColorEditFlags.PICKER_MASK
                                    | ColorEditFlags.INPUT_MASK
                                    | ColorEditFlags.HDR
                                    | ColorEditFlags.NO_ALPHA
                                    | ColorEditFlags.ALPHA_BAR;
                    final int pickerFlags =
                            (flagsUntouched & pickerFlagsToForward)
                                    | ColorEditFlags.DISPLAY_MASK
                                    | ColorEditFlags.NO_LABEL
                                    | ColorEditFlags.ALPHA_PREVIEW_HALF;
                    IkGuiImplUtils.setNextItemWidth(squareSize * 12.0f);
                    IkGuiImplUtils.pushItemFlag(ItemFlags.MIXED_VALUE, false);
                    valueChanged |=
                            colorPicker4(
                                    "##picker", color, pickerFlags, context.colorPickerReference);
                    IkGuiImplUtils.popItemFlag();
                }
                IkGuiImplPopups.endPopup();
            }
        }

        if (!displayedLabel.isEmpty() && (flags & ColorEditFlags.NO_LABEL) == 0) {
            // Position not necessarily next to the last submitted button (e.g. if
            // style.colorButtonPosition is LEFT), but we need to use sameLine() to set up the
            // baseline correctly.
            IkGuiImplLayout.sameLine(0.0f, style.itemInnerSpacing.x);
            window.cursorPosition.x =
                    posX
                            + ((flags & ColorEditFlags.NO_INPUTS) != 0
                                    ? widthButton
                                    : widthFull + style.itemInnerSpacing.x);
            IkGuiImplText.textEx(displayedLabel);
        }

        // Convert back
        if (valueChanged && pickerActiveWindow == null) {
            if (!valueChangedAsFloat) {
                for (int n = 0; n < 4; ++n) {
                    f[n] = i[n] / 255.0f;
                }
            }
            if ((flags & ColorEditFlags.DISPLAY_HSV) != 0
                    && (flags & ColorEditFlags.INPUT_RGB) != 0) {
                context.colorEditSavedHue = f[0];
                context.colorEditSavedSaturation = f[1];
                hsvToRgbInPlace(f);
                context.colorEditSavedID = context.colorEditCurrentID;
                context.colorEditSavedColor = float4ToRGBA(f[0], f[1], f[2], 0);
            }
            if ((flags & ColorEditFlags.DISPLAY_RGB) != 0
                    && (flags & ColorEditFlags.INPUT_HSV) != 0) {
                rgbToHsvInPlace(f);
            }

            color[0] = f[0];
            color[1] = f[1];
            color[2] = f[2];
            if (alpha) {
                color[3] = f[3];
            }
        }

        if (setCurrentColorEditID) {
            context.colorEditCurrentID = 0;
        }
        IkGuiImplUtils.popID();
        IkGuiImplLayout.endGroup();

        // Drag and Drop Target. The flag test is merely an optional micro-optimization,
        // beginDragDropTarget() does the same test.
        if ((context.lastItemData.statusFlags & ItemStatusFlags.HOVERED_RECT) != 0
                && (context.lastItemData.itemFlags & ItemFlags.READ_ONLY) == 0
                && (flags & ColorEditFlags.NO_DRAG_DROP) == 0
                && IkGuiImplDragDrop.beginDragDropTarget()) {
            boolean acceptedDragDrop = false;
            final float[] payload3 =
                    IkGuiImplDragDrop.acceptDragDropPayload(
                            PAYLOAD_TYPE_COLOR_3F, DragDropFlags.NONE);
            if (payload3 != null) {
                // Preserve alpha if any
                System.arraycopy(payload3, 0, color, 0, 3);
                valueChanged = true;
                acceptedDragDrop = true;
            }
            final float[] payload4 =
                    IkGuiImplDragDrop.acceptDragDropPayload(
                            PAYLOAD_TYPE_COLOR_4F, DragDropFlags.NONE);
            if (payload4 != null) {
                System.arraycopy(payload4, 0, color, 0, components);
                valueChanged = true;
                acceptedDragDrop = true;
            }

            // Drag and drop payloads are always RGB
            if (acceptedDragDrop && (flags & ColorEditFlags.INPUT_HSV) != 0) {
                rgbToHsvInPlace(color);
            }
            IkGuiImplDragDrop.endDragDropTarget();
        }

        // When the picker is being actively used, use its active id so isItemActive() will
        // function on colorEdit4().
        if (pickerActiveWindow != null
                && context.activeID != 0
                && context.activeIDWindow == pickerActiveWindow) {
            context.lastItemData.id = context.activeID;
        }

        // In case of ID collision, the second endGroup() won't catch context.activeID
        if (valueChanged && context.lastItemData.id != 0) {
            IkGuiInternal.markItemEdited(context.lastItemData.id);
        }

        return valueChanged;
    }

    /**
     * A color picker for an RGB color, with each component in the range 0-1.
     *
     * @param label The label, which is also used for the ID.
     * @param color The color, which must have at least 3 elements.
     * @param colorEditFlags Flags for the picker.
     * @return True if the value was changed.
     * @see ColorEditFlags
     */
    public static boolean colorPicker3(String label, float[] color, int colorEditFlags) {
        if (color == null || color.length < 3) {
            IkGuiImplDebugTools.reportError(
                    log, "colorPicker3 requires a color array with at least 3 elements");
            return false;
        }
        final float[] color4 = {color[0], color[1], color[2], 1.0f};
        if (!colorPicker4(label, color4, colorEditFlags | ColorEditFlags.NO_ALPHA, null)) {
            return false;
        }
        color[0] = color4[0];
        color[1] = color4[1];
        color[2] = color4[2];
        return true;
    }

    /**
     * A color picker for an RGBA color, with each component in the range 0-1.
     *
     * @param label The label, which is also used for the ID.
     * @param color The color, which must have at least 4 elements, or 3 if the NO_ALPHA flag is
     *     set.
     * @param colorEditFlags Flags for the picker.
     * @param referenceColor The original color to display next to the current one, which can be
     *     clicked to revert to it. May be null to not display it.
     * @return True if the value was changed.
     * @see ColorEditFlags
     */
    public static boolean colorPicker4(
            @NonNull String label, float[] color, int colorEditFlags, float[] referenceColor) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (!validateColor(color, colorEditFlags)) {
            return false;
        }
        if (referenceColor != null && !validateColor(referenceColor, colorEditFlags)) {
            return false;
        }

        int flags = colorEditFlags;
        final DrawList drawList = window.drawList;
        final StyleVariables style = context.style.variable;
        final IkIO io = context.io;

        final float width = IkGuiImplUtils.calculateItemWidth();
        final boolean isReadOnly =
                ((context.nextItemData.itemFlags | context.currentItemFlags) & ItemFlags.READ_ONLY)
                        != 0;
        context.nextItemData.clearFlags();

        final float backupAlpha = style.alpha;
        if (context.disabledStackSize > 0) {
            // Cancel out the effect of beginDisabled() for color swatches
            style.alpha = context.disabledAlphaBackup;
        }

        IkGuiImplUtils.pushID(label);
        final boolean setCurrentColorEditID = context.colorEditCurrentID == 0;
        if (setCurrentColorEditID) {
            context.colorEditCurrentID = window.idStack.peek();
        }
        IkGuiImplLayout.beginGroup();

        if ((flags & ColorEditFlags.NO_SIDE_PREVIEW) == 0) {
            flags |= ColorEditFlags.NO_SMALL_PREVIEW;
        }

        // Context menu: display and store options
        if ((flags & ColorEditFlags.NO_OPTIONS) == 0) {
            colorPickerOptionsPopup(color, flags);
        }

        // Read stored options
        final int config = io.configColorEditFlags;
        if ((flags & ColorEditFlags.PICKER_MASK) == 0) {
            flags |=
                    ((config & ColorEditFlags.PICKER_MASK) != 0
                                    ? config
                                    : ColorEditFlags.DEFAULT_OPTIONS)
                            & ColorEditFlags.PICKER_MASK;
        }
        if ((flags & ColorEditFlags.INPUT_MASK) == 0) {
            flags |=
                    ((config & ColorEditFlags.INPUT_MASK) != 0
                                    ? config
                                    : ColorEditFlags.DEFAULT_OPTIONS)
                            & ColorEditFlags.INPUT_MASK;
        }
        flags = ensureSingleOption(flags, ColorEditFlags.PICKER_MASK, "picker");
        flags = ensureSingleOption(flags, ColorEditFlags.INPUT_MASK, "input");
        if ((flags & ColorEditFlags.NO_OPTIONS) == 0) {
            flags |= (config & ColorEditFlags.ALPHA_BAR);
        }

        // Setup
        final int components = (flags & ColorEditFlags.NO_ALPHA) != 0 ? 3 : 4;
        final boolean alphaBar =
                (flags & ColorEditFlags.ALPHA_BAR) != 0 && (flags & ColorEditFlags.NO_ALPHA) == 0;
        final float pickerPosX = window.cursorPosition.x;
        final float pickerPosY = window.cursorPosition.y;
        final float squareSize = IkGuiImplLayout.getFrameHeight();
        // Arbitrary smallish width of Hue/Alpha picking bars
        final float barsWidth = squareSize;
        // Saturation/Value picking box
        final float svPickerSize =
                Math.max(
                        barsWidth,
                        width - (alphaBar ? 2 : 1) * (barsWidth + style.itemInnerSpacing.x));
        final float bar0PosX = pickerPosX + svPickerSize + style.itemInnerSpacing.x;
        final float bar1PosX = bar0PosX + barsWidth + style.itemInnerSpacing.x;
        final float barsTrianglesHalfSize = IkGuiInternal.truncate(barsWidth * 0.20f);

        final float[] backupInitialColor = new float[4];
        System.arraycopy(color, 0, backupInitialColor, 0, components);

        final float wheelThickness = svPickerSize * 0.08f;
        final float wheelRadiusOuter = svPickerSize * 0.50f;
        final float wheelRadiusInner = wheelRadiusOuter - wheelThickness;
        final float wheelCenterX = pickerPosX + (svPickerSize + barsWidth) * 0.5f;
        final float wheelCenterY = pickerPosY + svPickerSize * 0.5f;

        // Note: the triangle is displayed rotated with triangle A pointing to Hue, but most
        // coordinates stay unrotated for logic.
        final boolean triangleRotate = (flags & ColorEditFlags.PICKER_NO_ROTATE) == 0;
        final float triangleR = wheelRadiusInner - (int) (svPickerSize * 0.027f);
        // Hue point
        final Vector2f triangleA =
                triangleRotate
                        ? new Vector2f(triangleR, 0.0f)
                        : new Vector2f(triangleR * 0.866f, triangleR * 0.5f);
        // Black point
        final Vector2f triangleB =
                triangleRotate
                        ? new Vector2f(triangleR * -0.5f, triangleR * -0.866f)
                        : new Vector2f(0.0f, -triangleR);
        // White point
        final Vector2f triangleC =
                triangleRotate
                        ? new Vector2f(triangleR * -0.5f, triangleR * 0.866f)
                        : new Vector2f(triangleR * -0.866f, triangleR * 0.5f);

        final float[] hsv = {color[0], color[1], color[2]};
        final float[] rgb = {color[0], color[1], color[2]};
        if ((flags & ColorEditFlags.INPUT_RGB) != 0) {
            // Hue is lost when converting from grayscale rgb (saturation=0). Restore it.
            Color.rgbTohsv(rgb, hsv);
            colorEditRestoreHS(color, hsv);
        } else if ((flags & ColorEditFlags.INPUT_HSV) != 0) {
            Color.hsvToColor(hsv, rgb);
        }

        boolean valueChanged = false;
        boolean valueChangedHue = false;
        boolean valueChangedSV = false;

        IkGuiImplUtils.pushItemFlag(ItemFlags.NO_NAV, true);
        if ((flags & ColorEditFlags.PICKER_HUE_WHEEL) != 0) {
            // Hue wheel + SV triangle logic
            IkGuiImplButtons.invisibleButton(
                    "hsv",
                    svPickerSize + style.itemInnerSpacing.x + barsWidth,
                    svPickerSize,
                    ButtonFlags.NONE);
            if (IkGuiImplUtils.isItemActive() && !isReadOnly) {
                final Vector2f initialOffset =
                        new Vector2f(io.mouseClickedPosition[0]).sub(wheelCenterX, wheelCenterY);
                final Vector2f currentOffset =
                        new Vector2f(io.mousePosition).sub(wheelCenterX, wheelCenterY);
                final float initialDistanceSquared = initialOffset.lengthSquared();
                if (initialDistanceSquared >= (wheelRadiusInner - 1) * (wheelRadiusInner - 1)
                        && initialDistanceSquared
                                <= (wheelRadiusOuter + 1) * (wheelRadiusOuter + 1)) {
                    // Interacting with the Hue wheel
                    hsv[0] = (float) (Math.atan2(currentOffset.y, currentOffset.x) / Math.PI * 0.5);
                    if (hsv[0] < 0.0f) {
                        hsv[0] += 1.0f;
                    }
                    valueChanged = true;
                    valueChangedHue = true;
                }
                final float cosHueAngle =
                        triangleRotate ? (float) Math.cos(-hsv[0] * 2.0 * Math.PI) : 1.0f;
                final float sinHueAngle =
                        triangleRotate ? (float) Math.sin(-hsv[0] * 2.0 * Math.PI) : 0.0f;
                if (triangleContainsPoint(
                        triangleA,
                        triangleB,
                        triangleC,
                        rotate(initialOffset, cosHueAngle, sinHueAngle))) {
                    // Interacting with the SV triangle
                    Vector2f currentOffsetUnrotated =
                            rotate(currentOffset, cosHueAngle, sinHueAngle);
                    if (!triangleContainsPoint(
                            triangleA, triangleB, triangleC, currentOffsetUnrotated)) {
                        currentOffsetUnrotated =
                                triangleClosestPoint(
                                        triangleA, triangleB, triangleC, currentOffsetUnrotated);
                    }
                    final float[] uvw = new float[3];
                    triangleBarycentricCoords(
                            triangleA, triangleB, triangleC, currentOffsetUnrotated, uvw);
                    hsv[2] = MathUtil.clamp(1.0f - uvw[1], 0.0001f, 1.0f);
                    hsv[1] = MathUtil.clamp(uvw[0] / hsv[2], 0.0001f, 1.0f);
                    valueChanged = true;
                    valueChangedSV = true;
                }
            }
            if ((flags & ColorEditFlags.NO_OPTIONS) == 0) {
                IkGuiImplPopups.openPopupOnItemClick("context", PopupFlags.MOUSE_BUTTON_RIGHT);
            }
        } else if ((flags & ColorEditFlags.PICKER_HUE_BAR) != 0) {
            // SV rectangle logic
            IkGuiImplButtons.invisibleButton("sv", svPickerSize, svPickerSize, ButtonFlags.NONE);
            if (IkGuiImplUtils.isItemActive() && !isReadOnly) {
                hsv[1] = saturate((io.mousePosition.x - pickerPosX) / (svPickerSize - 1));
                hsv[2] = 1.0f - saturate((io.mousePosition.y - pickerPosY) / (svPickerSize - 1));
                // Greatly reduces hue jitter and reset to 0 when hue == 255 and color is rapidly
                // modified using the SV square.
                colorEditRestoreH(color, hsv);
                valueChanged = true;
                valueChangedSV = true;
            }
            if ((flags & ColorEditFlags.NO_OPTIONS) == 0) {
                IkGuiImplPopups.openPopupOnItemClick("context", PopupFlags.MOUSE_BUTTON_RIGHT);
            }

            // Hue bar logic
            IkGuiImplUtils.setCursorScreenPos(bar0PosX, pickerPosY);
            IkGuiImplButtons.invisibleButton("hue", barsWidth, svPickerSize, ButtonFlags.NONE);
            if (IkGuiImplUtils.isItemActive() && !isReadOnly) {
                hsv[0] = saturate((io.mousePosition.y - pickerPosY) / (svPickerSize - 1));
                valueChanged = true;
                valueChangedHue = true;
            }
        }

        // Alpha bar logic
        if (alphaBar) {
            IkGuiImplUtils.setCursorScreenPos(bar1PosX, pickerPosY);
            IkGuiImplButtons.invisibleButton("alpha", barsWidth, svPickerSize, ButtonFlags.NONE);
            if (IkGuiImplUtils.isItemActive()) {
                color[3] = 1.0f - saturate((io.mousePosition.y - pickerPosY) / (svPickerSize - 1));
                valueChanged = true;
            }
        }
        IkGuiImplUtils.popItemFlag(); // ItemFlags.NO_NAV

        if ((flags & ColorEditFlags.NO_SIDE_PREVIEW) == 0) {
            IkGuiImplLayout.sameLine(0, style.itemInnerSpacing.x);
            IkGuiImplLayout.beginGroup();
        }

        if ((flags & ColorEditFlags.NO_LABEL) == 0) {
            final String displayedLabel = Hash.getDisplayedText(label);
            if (!displayedLabel.isEmpty()) {
                if ((flags & ColorEditFlags.NO_SIDE_PREVIEW) != 0) {
                    IkGuiImplLayout.sameLine(0, style.itemInnerSpacing.x);
                }
                IkGuiImplText.textEx(displayedLabel);
            }
        }

        if ((flags & ColorEditFlags.NO_SIDE_PREVIEW) == 0) {
            IkGuiImplUtils.pushItemFlag(ItemFlags.NO_NAV_DEFAULT_FOCUS, true);
            IkGuiImplUtils.pushItemFlag(ItemFlags.MIXED_VALUE, false);
            final boolean noAlpha = (flags & ColorEditFlags.NO_ALPHA) != 0;
            final float[] currentColor = {color[0], color[1], color[2], noAlpha ? 1.0f : color[3]};
            if ((flags & ColorEditFlags.NO_LABEL) != 0) {
                IkGuiImplText.text("Current");
            }

            final int subFlagsToForward =
                    ColorEditFlags.INPUT_MASK
                            | ColorEditFlags.HDR
                            | ColorEditFlags.ALPHA_MASK
                            | ColorEditFlags.NO_TOOLTIP;
            colorButton(
                    "##current",
                    currentColor,
                    flags & subFlagsToForward,
                    squareSize * 3,
                    squareSize * 2);
            if (referenceColor != null) {
                IkGuiImplText.text("Original");
                final float[] reference = {
                    referenceColor[0],
                    referenceColor[1],
                    referenceColor[2],
                    noAlpha ? 1.0f : referenceColor[3]
                };
                if (colorButton(
                        "##original",
                        reference,
                        flags & subFlagsToForward,
                        squareSize * 3,
                        squareSize * 2)) {
                    System.arraycopy(referenceColor, 0, color, 0, components);
                    valueChanged = true;
                }
            }
            IkGuiImplUtils.popItemFlag();
            IkGuiImplUtils.popItemFlag();
            IkGuiImplLayout.endGroup();
        }

        // Convert back color to RGB
        if (valueChangedHue || valueChangedSV) {
            if ((flags & ColorEditFlags.INPUT_RGB) != 0) {
                Color.hsvToColor(hsv, color);
                context.colorEditSavedHue = hsv[0];
                context.colorEditSavedSaturation = hsv[1];
                context.colorEditSavedID = context.colorEditCurrentID;
                context.colorEditSavedColor = float4ToRGBA(color[0], color[1], color[2], 0);
            } else if ((flags & ColorEditFlags.INPUT_HSV) != 0) {
                color[0] = hsv[0];
                color[1] = hsv[1];
                color[2] = hsv[2];
            }
        }

        // R,G,B and H,S,V slider color editor
        boolean valueChangedFixHueWrap = false;
        if ((flags & ColorEditFlags.NO_INPUTS) == 0) {
            IkGuiImplLayout.pushItemWidth(
                    (alphaBar ? bar1PosX : bar0PosX) + barsWidth - pickerPosX);
            final int subFlagsToForward =
                    ColorEditFlags.DATA_TYPE_MASK
                            | ColorEditFlags.INPUT_MASK
                            | ColorEditFlags.HDR
                            | ColorEditFlags.ALPHA_MASK
                            | ColorEditFlags.NO_OPTIONS
                            | ColorEditFlags.NO_TOOLTIP
                            | ColorEditFlags.NO_SMALL_PREVIEW;
            final int subFlags = (flags & subFlagsToForward) | ColorEditFlags.NO_PICKER;
            final boolean noDisplayFlags = (flags & ColorEditFlags.DISPLAY_MASK) == 0;
            if (((flags & ColorEditFlags.DISPLAY_RGB) != 0 || noDisplayFlags)
                    && colorEdit4("##rgb", color, subFlags | ColorEditFlags.DISPLAY_RGB)) {
                // FIXME(from ImGui): Hackily differentiating using the DragInt (activeID != 0 &&
                // !activeIDAllowOverlap) vs. using the InputText or DropTarget. For the latter
                // we don't want to run the hue-wrap canceling code.
                valueChangedFixHueWrap = context.activeID != 0 && !context.activeIDAllowOverlap;
                valueChanged = true;
            }
            if ((flags & ColorEditFlags.DISPLAY_HSV) != 0 || noDisplayFlags) {
                valueChanged |= colorEdit4("##hsv", color, subFlags | ColorEditFlags.DISPLAY_HSV);
            }
            if ((flags & ColorEditFlags.DISPLAY_HEX) != 0 || noDisplayFlags) {
                valueChanged |= colorEdit4("##hex", color, subFlags | ColorEditFlags.DISPLAY_HEX);
            }
            IkGuiImplLayout.popItemWidth();
        }

        // Try to cancel hue wrap (after colorEdit4 call), if any
        if (valueChangedFixHueWrap && (flags & ColorEditFlags.INPUT_RGB) != 0) {
            final float[] newHSV = new float[3];
            Color.rgbTohsv(color, newHSV);
            if (newHSV[0] <= 0 && hsv[0] > 0) {
                if (newHSV[2] <= 0 && hsv[2] != newHSV[2]) {
                    Color.hsvToColor(
                            new float[] {
                                hsv[0], hsv[1], newHSV[2] <= 0 ? hsv[2] * 0.5f : newHSV[2]
                            },
                            color);
                } else if (newHSV[1] <= 0) {
                    Color.hsvToColor(
                            new float[] {
                                hsv[0], newHSV[1] <= 0 ? hsv[1] * 0.5f : newHSV[1], newHSV[2]
                            },
                            color);
                }
            }
        }

        if (valueChanged) {
            if ((flags & ColorEditFlags.INPUT_RGB) != 0) {
                rgb[0] = color[0];
                rgb[1] = color[1];
                rgb[2] = color[2];
                Color.rgbTohsv(rgb, hsv);
                // Fix the local hue as the display below will use it immediately
                colorEditRestoreHS(color, hsv);
            } else if ((flags & ColorEditFlags.INPUT_HSV) != 0) {
                hsv[0] = color[0];
                hsv[1] = color[1];
                hsv[2] = color[2];
                Color.hsvToColor(hsv, rgb);
            }
        }

        final int styleAlpha8 = floatToInt8Saturated(style.alpha);
        final int colorBlack = Color.rgba(0, 0, 0, styleAlpha8);
        final int colorWhite = Color.rgba(255, 255, 255, styleAlpha8);
        final int colorMidGrey = Color.rgba(128, 128, 128, styleAlpha8);
        final int[] colorHues = {
            Color.rgba(255, 0, 0, styleAlpha8),
            Color.rgba(255, 255, 0, styleAlpha8),
            Color.rgba(0, 255, 0, styleAlpha8),
            Color.rgba(0, 255, 255, styleAlpha8),
            Color.rgba(0, 0, 255, styleAlpha8),
            Color.rgba(255, 0, 255, styleAlpha8),
            Color.rgba(255, 0, 0, styleAlpha8)
        };

        final float[] hueRGB = new float[3];
        Color.hsvToColor(new float[] {hsv[0], 1, 1}, hueRGB);
        final int hueColor = float4ToRGBA(hueRGB[0], hueRGB[1], hueRGB[2], style.alpha);
        // Important: this is still including the main rendering/style alpha!
        final int userColorStrippedOfAlpha = float4ToRGBA(rgb[0], rgb[1], rgb[2], style.alpha);

        float svCursorX;
        float svCursorY;

        if ((flags & ColorEditFlags.PICKER_HUE_WHEEL) != 0) {
            // Render the Hue Wheel, as 6 arcs with a gradient between each pair of hues
            final float wheelRadiusMiddle = (wheelRadiusInner + wheelRadiusOuter) * 0.5f;
            for (int n = 0; n < 6; ++n) {
                final float a0 = n / 6.0f * 2.0f * (float) Math.PI;
                final float a1 = (n + 1.0f) / 6.0f * 2.0f * (float) Math.PI;
                drawList.addArc(
                        wheelCenterX,
                        wheelCenterY,
                        wheelRadiusMiddle,
                        a0,
                        a1,
                        colorHues[n],
                        colorHues[n + 1],
                        wheelThickness);
            }

            // Render Cursor + preview on Hue Wheel
            float cosHueAngle = (float) Math.cos(hsv[0] * 2.0 * Math.PI);
            float sinHueAngle = (float) Math.sin(hsv[0] * 2.0 * Math.PI);
            final float hueCursorX = wheelCenterX + cosHueAngle * wheelRadiusMiddle;
            final float hueCursorY = wheelCenterY + sinHueAngle * wheelRadiusMiddle;
            final float hueCursorRadius =
                    valueChangedHue ? wheelThickness * 0.65f : wheelThickness * 0.55f;
            drawList.addCircleFilled(hueCursorX, hueCursorY, hueCursorRadius, hueColor);
            drawList.addCircle(hueCursorX, hueCursorY, hueCursorRadius + 1, colorMidGrey);
            drawList.addCircle(hueCursorX, hueCursorY, hueCursorRadius, colorWhite);
            if (!triangleRotate) {
                cosHueAngle = 1.0f;
                sinHueAngle = 0.0f;
            }

            // Render the SV triangle (rotated according to hue)
            final Vector2f tra =
                    rotate(triangleA, cosHueAngle, sinHueAngle).add(wheelCenterX, wheelCenterY);
            final Vector2f trb =
                    rotate(triangleB, cosHueAngle, sinHueAngle).add(wheelCenterX, wheelCenterY);
            final Vector2f trc =
                    rotate(triangleC, cosHueAngle, sinHueAngle).add(wheelCenterX, wheelCenterY);
            drawList.addTriangleFilledMultiColor(
                    tra.x, tra.y, trb.x, trb.y, trc.x, trc.y, hueColor, colorBlack, colorWhite);
            drawList.addTriangle(tra.x, tra.y, trb.x, trb.y, trc.x, trc.y, colorMidGrey, 1.5f);
            // Lerp from white to the hue by saturation, then towards black by (1 - value)
            final Vector2f svCursor = new Vector2f(trc).lerp(tra, saturate(hsv[1]));
            svCursor.lerp(trb, saturate(1 - hsv[2]));
            svCursorX = svCursor.x;
            svCursorY = svCursor.y;
        } else {
            // Render the SV Square
            final float svMaxX = pickerPosX + svPickerSize;
            final float svMaxY = pickerPosY + svPickerSize;
            drawList.addRectFilledMultiColor(
                    pickerPosX,
                    pickerPosY,
                    svMaxX,
                    svMaxY,
                    colorWhite,
                    hueColor,
                    hueColor,
                    colorWhite);
            drawList.addRectFilledMultiColor(
                    pickerPosX,
                    pickerPosY,
                    svMaxX,
                    svMaxY,
                    Color.CLEAR,
                    Color.CLEAR,
                    colorBlack,
                    colorBlack);
            IkGuiInternal.renderFrameBorder(pickerPosX, pickerPosY, svMaxX, svMaxY, 0.0f);
            // Sneakily prevent the circle from sticking out too much
            svCursorX =
                    clamp(
                            Math.round(pickerPosX + saturate(hsv[1]) * svPickerSize),
                            pickerPosX + 2,
                            pickerPosX + svPickerSize - 2);
            svCursorY =
                    clamp(
                            Math.round(pickerPosY + saturate(1 - hsv[2]) * svPickerSize),
                            pickerPosY + 2,
                            pickerPosY + svPickerSize - 2);

            // Render the Hue Bar
            if ((flags & ColorEditFlags.PICKER_HUE_BAR) != 0) {
                for (int n = 0; n < 6; ++n) {
                    drawList.addRectFilledMultiColor(
                            bar0PosX,
                            pickerPosY + n * (svPickerSize / 6),
                            bar0PosX + barsWidth,
                            pickerPosY + (n + 1) * (svPickerSize / 6),
                            colorHues[n],
                            colorHues[n],
                            colorHues[n + 1],
                            colorHues[n + 1]);
                }
                final float bar0LineY = Math.round(pickerPosY + hsv[0] * svPickerSize);
                IkGuiInternal.renderFrameBorder(
                        bar0PosX, pickerPosY, bar0PosX + barsWidth, svMaxY, 0.0f);
                renderArrowsForVerticalBar(
                        drawList,
                        bar0PosX - 1,
                        bar0LineY,
                        barsTrianglesHalfSize + 1,
                        barsTrianglesHalfSize,
                        barsWidth + 2.0f,
                        style.alpha);
            }
        }

        // Render the cursor/preview circle (clamp S/V within 0..1 range because floating point
        // colors may lead HSV values to be out of range)
        final float svCursorRadius =
                valueChangedSV ? wheelThickness * 0.55f : wheelThickness * 0.40f;
        drawList.addCircleFilled(svCursorX, svCursorY, svCursorRadius, userColorStrippedOfAlpha);
        drawList.addCircle(svCursorX, svCursorY, svCursorRadius + 1, colorMidGrey);
        drawList.addCircle(svCursorX, svCursorY, svCursorRadius, colorWhite);

        // Render the alpha bar
        if (alphaBar) {
            final float alpha = saturate(color[3]);
            final float bar1MaxX = bar1PosX + barsWidth;
            final float bar1MaxY = pickerPosY + svPickerSize;
            renderColorRectWithAlphaCheckerboard(
                    drawList,
                    bar1PosX,
                    pickerPosY,
                    bar1MaxX,
                    bar1MaxY,
                    Color.CLEAR,
                    style.alpha,
                    barsWidth / 2.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    DrawFlags.ROUND_CORNERS_ALL);
            final int transparent = userColorStrippedOfAlpha & 0xFFFFFF00;
            drawList.addRectFilledMultiColor(
                    bar1PosX,
                    pickerPosY,
                    bar1MaxX,
                    bar1MaxY,
                    userColorStrippedOfAlpha,
                    userColorStrippedOfAlpha,
                    transparent,
                    transparent);
            final float bar1LineY = Math.round(pickerPosY + (1.0f - alpha) * svPickerSize);
            IkGuiInternal.renderFrameBorder(bar1PosX, pickerPosY, bar1MaxX, bar1MaxY, 0.0f);
            renderArrowsForVerticalBar(
                    drawList,
                    bar1PosX - 1,
                    bar1LineY,
                    barsTrianglesHalfSize + 1,
                    barsTrianglesHalfSize,
                    barsWidth + 2.0f,
                    style.alpha);
        }

        IkGuiImplLayout.endGroup();

        if (valueChanged && arraysEqual(backupInitialColor, color, components)) {
            valueChanged = false;
        }
        // In case of ID collision, the second endGroup() won't catch context.activeID
        if (valueChanged && context.lastItemData.id != 0) {
            IkGuiInternal.markItemEdited(context.lastItemData.id);
        }

        if (setCurrentColorEditID) {
            context.colorEditCurrentID = 0;
        }
        IkGuiImplUtils.popID();

        if (context.disabledStackSize > 0) {
            style.alpha = backupAlpha;
        }

        return valueChanged;
    }

    /**
     * Display a color square/button. Hover for details, returns true when pressed. The label is
     * only displayed in the tooltip, not next to the button.
     *
     * @param descriptionID The description used in the tooltip, which is also used for the ID.
     * @param color The color, as RGBA (or HSVA if the INPUT_HSV flag is set). If only 3 elements
     *     are provided, alpha is treated as 1.
     * @param colorEditFlags Flags for the button.
     * @param width The width, 0 to use the default size.
     * @param height The height, 0 to use the default size.
     * @return True if the button was clicked.
     * @see ColorEditFlags
     */
    public static boolean colorButton(
            @NonNull String descriptionID,
            float[] color,
            int colorEditFlags,
            float width,
            float height) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }
        if (color == null || color.length < 3) {
            IkGuiImplDebugTools.reportError(
                    log, "colorButton requires a color array with at least 3 elements");
            return false;
        }

        int flags = colorEditFlags;
        final StyleVariables style = context.style.variable;
        final int id = window.getID(descriptionID);
        final float defaultSize = IkGuiImplLayout.getFrameHeight();
        final float sizeX = width == 0.0f ? defaultSize : width;
        final float sizeY = height == 0.0f ? defaultSize : height;
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat bb = new RectFloat(x, y, x + sizeX, y + sizeY);
        IkGuiInternal.itemSize(bb, sizeY >= defaultSize ? style.framePadding.y : 0.0f);
        if (!IkGuiInternal.itemAdd(bb, id)) {
            return false;
        }

        final IkBoolean hovered = new IkBoolean(false);
        final boolean pressed =
                IkGuiInternal.buttonBehavior(bb, id, hovered, null, ButtonFlags.NONE);

        if ((flags & (ColorEditFlags.NO_ALPHA | ColorEditFlags.ALPHA_OPAQUE)) != 0) {
            flags &= ~(ColorEditFlags.ALPHA_NO_BACKGROUND | ColorEditFlags.ALPHA_PREVIEW_HALF);
        }

        final float[] colorSource = {
            color[0], color[1], color[2], color.length >= 4 ? color[3] : 1.0f
        };
        final float[] colorRGB = colorSource.clone();
        if ((flags & ColorEditFlags.INPUT_HSV) != 0) {
            hsvToRgbInPlace(colorRGB);
        }

        final int colorRGBWithoutAlpha = float4ToRGBA(colorRGB[0], colorRGB[1], colorRGB[2], 1.0f);
        final int colorRGBWithAlpha =
                float4ToRGBA(colorRGB[0], colorRGB[1], colorRGB[2], colorRGB[3]);
        final float gridStep = Math.min(sizeX, sizeY) / 2.99f;
        final float rounding = Math.min(style.frameRounding, gridStep * 0.5f);
        final boolean isMixed = (context.lastItemData.itemFlags & ItemFlags.MIXED_VALUE) != 0;

        final float backupAlpha = style.alpha;
        if (context.disabledStackSize > 0) {
            // Cancel out the effect of beginDisabled() for color swatches
            style.alpha = context.disabledAlphaBackup;
        }
        final DrawList drawList = window.drawList;
        if (isMixed) {
            drawList.addRectFilled(
                    bb.getLeft(),
                    bb.getTop(),
                    bb.getRight(),
                    bb.getBottom(),
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.FRAME_BACKGROUND),
                    rounding,
                    DrawFlags.ROUND_CORNERS_ALL);
            if (context.mixedValueLabel != null) {
                IkGuiImplUtils.pushStyleColor(
                        ColorType.TEXT, IkGuiImplUtils.getColor(ColorType.TEXT_DISABLED));
                IkGuiInternal.renderTextClipped(
                        bb.getLeft(),
                        bb.getTop() + style.framePadding.y,
                        bb.getRight(),
                        bb.getBottom(),
                        context.mixedValueLabel,
                        null,
                        0.5f,
                        0.0f,
                        null);
                IkGuiImplUtils.popStyleColor();
            }
        } else if ((flags & ColorEditFlags.ALPHA_PREVIEW_HALF) != 0 && colorRGB[3] < 1.0f) {
            final float midX = Math.round((bb.getLeft() + bb.getRight()) * 0.5f);
            if ((flags & ColorEditFlags.ALPHA_NO_BACKGROUND) == 0) {
                renderColorRectWithAlphaCheckerboard(
                        drawList,
                        bb.getLeft() + gridStep,
                        bb.getTop(),
                        bb.getRight(),
                        bb.getBottom(),
                        colorRGBWithAlpha,
                        style.alpha,
                        gridStep,
                        -gridStep,
                        0.0f,
                        rounding,
                        DrawFlags.ROUND_CORNERS_RIGHT);
            } else {
                drawList.addRectFilled(
                        bb.getLeft() + gridStep,
                        bb.getTop(),
                        bb.getRight(),
                        bb.getBottom(),
                        IkGuiImplUtils.applyGlobalAlpha(colorRGBWithAlpha, false),
                        rounding,
                        DrawFlags.ROUND_CORNERS_RIGHT);
            }
            drawList.addRectFilled(
                    bb.getLeft(),
                    bb.getTop(),
                    midX,
                    bb.getBottom(),
                    IkGuiImplUtils.applyGlobalAlpha(colorRGBWithoutAlpha, false),
                    rounding,
                    DrawFlags.ROUND_CORNERS_LEFT);
        } else {
            // Because applyGlobalAlpha() multiplies by the global style alpha and we don't want to
            // display a checkerboard if the source code had no alpha
            final boolean opaque = (flags & ColorEditFlags.ALPHA_OPAQUE) != 0;
            final int colorToDraw = opaque ? colorRGBWithoutAlpha : colorRGBWithAlpha;
            final float sourceAlpha = opaque ? 1.0f : colorRGB[3];
            if (sourceAlpha < 1.0f && (flags & ColorEditFlags.ALPHA_NO_BACKGROUND) == 0) {
                renderColorRectWithAlphaCheckerboard(
                        drawList,
                        bb.getLeft(),
                        bb.getTop(),
                        bb.getRight(),
                        bb.getBottom(),
                        colorToDraw,
                        style.alpha,
                        gridStep,
                        0.0f,
                        0.0f,
                        rounding,
                        DrawFlags.ROUND_CORNERS_ALL);
            } else {
                drawList.addRectFilled(
                        bb.getLeft(),
                        bb.getTop(),
                        bb.getRight(),
                        bb.getBottom(),
                        IkGuiImplUtils.applyGlobalAlpha(colorToDraw, false),
                        rounding,
                        DrawFlags.ROUND_CORNERS_ALL);
            }
        }
        if (context.disabledStackSize > 0) {
            style.alpha = backupAlpha;
        }
        IkGuiImplNav.renderNavCursor(bb, id, NavRenderCursorFlags.NONE, -1.0f);
        if ((flags & ColorEditFlags.NO_BORDER) == 0) {
            if (style.frameBorderSize > 0.0f) {
                IkGuiInternal.renderFrameBorder(
                        bb.getLeft(), bb.getTop(), bb.getRight(), bb.getBottom(), rounding);
            } else {
                // Color buttons are often in need of some sort of border
                drawList.addRect(
                        bb.getLeft(),
                        bb.getTop(),
                        bb.getRight(),
                        bb.getBottom(),
                        IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.FRAME_BACKGROUND),
                        rounding,
                        DrawFlags.ROUND_CORNERS_ALL,
                        1.0f);
            }
        }

        // Drag and Drop Source. The activeID test is merely an optional micro-optimization,
        // beginDragDropSource() does the same test.
        if (context.activeID == id
                && (flags & ColorEditFlags.NO_DRAG_DROP) == 0
                && IkGuiImplDragDrop.beginDragDropSource(DragDropFlags.NONE)) {
            if ((flags & ColorEditFlags.NO_ALPHA) != 0) {
                IkGuiImplDragDrop.setDragDropPayload(
                        PAYLOAD_TYPE_COLOR_3F,
                        new float[] {colorRGB[0], colorRGB[1], colorRGB[2]},
                        Condition.ONCE);
            } else {
                IkGuiImplDragDrop.setDragDropPayload(
                        PAYLOAD_TYPE_COLOR_4F, colorRGB.clone(), Condition.ONCE);
            }
            IkGuiImplUtils.pushItemFlag(ItemFlags.MIXED_VALUE, false);
            colorButton(descriptionID, colorSource, flags, 0, 0);
            IkGuiImplUtils.popItemFlag();
            IkGuiImplLayout.sameLine(0, -1);
            IkGuiImplText.textEx("Color");
            IkGuiImplDragDrop.endDragDropSource();
        }

        // Tooltip
        if ((flags & ColorEditFlags.NO_TOOLTIP) == 0
                && hovered.get()
                && IkGuiImplUtils.isItemHovered(HoveredFlags.FOR_TOOLTIP)) {
            colorTooltip(
                    descriptionID,
                    colorSource,
                    flags & (ColorEditFlags.INPUT_MASK | ColorEditFlags.ALPHA_MASK));
        }

        return pressed;
    }

    /**
     * Display a tooltip describing a color.
     *
     * @param text The text to display above the color, may be null.
     * @param color The color, with 3 or 4 elements.
     * @param colorEditFlags Flags, only the input and alpha options are used.
     */
    static void colorTooltip(String text, @NonNull float[] color, int colorEditFlags) {
        if (!IkGuiImplPopups.beginTooltipEx(true, WindowFlags.NONE)) {
            return;
        }
        final String displayed = text == null ? "" : Hash.getDisplayedText(text);
        if (!displayed.isEmpty()) {
            IkGuiImplText.textEx(displayed);
            IkGuiImplLayout.separator();
        }

        final boolean noAlpha = (colorEditFlags & ColorEditFlags.NO_ALPHA) != 0 || color.length < 4;
        final float size =
                IkGuiInternal.getFontSize() * 3 + context.style.variable.framePadding.y * 2;
        final float[] previewColor = {color[0], color[1], color[2], noAlpha ? 1.0f : color[3]};
        final int cr = floatToInt8Saturated(color[0]);
        final int cg = floatToInt8Saturated(color[1]);
        final int cb = floatToInt8Saturated(color[2]);
        final int ca = noAlpha ? 255 : floatToInt8Saturated(color[3]);
        final int flagsToForward = ColorEditFlags.INPUT_MASK | ColorEditFlags.ALPHA_MASK;
        IkGuiImplUtils.pushItemFlag(ItemFlags.MIXED_VALUE, false);
        colorButton(
                "##preview",
                previewColor,
                (colorEditFlags & flagsToForward) | ColorEditFlags.NO_TOOLTIP,
                size,
                size);
        IkGuiImplUtils.popItemFlag();
        IkGuiImplLayout.sameLine(0, -1);
        if ((colorEditFlags & ColorEditFlags.INPUT_RGB) != 0
                || (colorEditFlags & ColorEditFlags.INPUT_MASK) == 0) {
            if (noAlpha) {
                IkGuiImplText.text(
                        String.format(
                                Locale.ROOT,
                                "#%02X%02X%02X\nR: %d, G: %d, B: %d\n(%.3f, %.3f, %.3f)",
                                cr,
                                cg,
                                cb,
                                cr,
                                cg,
                                cb,
                                color[0],
                                color[1],
                                color[2]));
            } else {
                IkGuiImplText.text(
                        String.format(
                                Locale.ROOT,
                                "#%02X%02X%02X%02X\nR:%d, G:%d, B:%d, A:%d\n"
                                        + "(%.3f, %.3f, %.3f, %.3f)",
                                cr,
                                cg,
                                cb,
                                ca,
                                cr,
                                cg,
                                cb,
                                ca,
                                color[0],
                                color[1],
                                color[2],
                                color[3]));
            }
        } else if ((colorEditFlags & ColorEditFlags.INPUT_HSV) != 0) {
            if (noAlpha) {
                IkGuiImplText.text(
                        String.format(
                                Locale.ROOT,
                                "H: %.3f, S: %.3f, V: %.3f",
                                color[0],
                                color[1],
                                color[2]));
            } else {
                IkGuiImplText.text(
                        String.format(
                                Locale.ROOT,
                                "H: %.3f, S: %.3f, V: %.3f, A: %.3f",
                                color[0],
                                color[1],
                                color[2],
                                color[3]));
            }
        }
        IkGuiImplPopups.endTooltip();
    }

    /**
     * The right-click options menu for a color edit, to pick the display and data type and copy the
     * value.
     *
     * @param color The color being edited.
     * @param colorEditFlags The flags for the color edit.
     */
    static void colorEditOptionsPopup(@NonNull float[] color, int colorEditFlags) {
        final boolean allowOptionInputs = (colorEditFlags & ColorEditFlags.DISPLAY_MASK) == 0;
        final boolean allowOptionDataType = (colorEditFlags & ColorEditFlags.DATA_TYPE_MASK) == 0;
        if ((!allowOptionInputs && !allowOptionDataType)
                || !IkGuiImplPopups.beginPopup("context", WindowFlags.NONE)) {
            return;
        }

        IkGuiImplUtils.pushItemFlag(ItemFlags.INTERNAL_NO_MARK_EDITED, true);
        int options = context.io.configColorEditFlags;
        if (allowOptionInputs) {
            options =
                    optionRadioButton(
                            "RGB",
                            options,
                            ColorEditFlags.DISPLAY_MASK,
                            ColorEditFlags.DISPLAY_RGB);
            options =
                    optionRadioButton(
                            "HSV",
                            options,
                            ColorEditFlags.DISPLAY_MASK,
                            ColorEditFlags.DISPLAY_HSV);
            options =
                    optionRadioButton(
                            "Hex",
                            options,
                            ColorEditFlags.DISPLAY_MASK,
                            ColorEditFlags.DISPLAY_HEX);
        }
        if (allowOptionDataType) {
            if (allowOptionInputs) {
                IkGuiImplLayout.separator();
            }
            options =
                    optionRadioButton(
                            "0..255", options, ColorEditFlags.DATA_TYPE_MASK, ColorEditFlags.UINT8);
            options =
                    optionRadioButton(
                            "0.00..1.00",
                            options,
                            ColorEditFlags.DATA_TYPE_MASK,
                            ColorEditFlags.FLOAT);
        }

        if (allowOptionInputs || allowOptionDataType) {
            IkGuiImplLayout.separator();
        }
        if (IkGuiImplButtons.button("Copy as..", -1, 0)) {
            IkGuiImplPopups.openPopup("Copy", PopupFlags.NONE);
        }
        if (IkGuiImplPopups.beginPopup("Copy", WindowFlags.NONE)) {
            final boolean noAlpha =
                    (colorEditFlags & ColorEditFlags.NO_ALPHA) != 0 || color.length < 4;
            final int cr = floatToInt8Saturated(color[0]);
            final int cg = floatToInt8Saturated(color[1]);
            final int cb = floatToInt8Saturated(color[2]);
            final int ca = noAlpha ? 255 : floatToInt8Saturated(color[3]);
            copySelectable(
                    String.format(
                            Locale.ROOT,
                            "(%.3ff, %.3ff, %.3ff, %.3ff)",
                            color[0],
                            color[1],
                            color[2],
                            noAlpha ? 1.0f : color[3]));
            copySelectable(String.format(Locale.ROOT, "(%d,%d,%d,%d)", cr, cg, cb, ca));
            copySelectable(String.format(Locale.ROOT, "#%02X%02X%02X", cr, cg, cb));
            if (!noAlpha) {
                copySelectable(String.format(Locale.ROOT, "#%02X%02X%02X%02X", cr, cg, cb, ca));
            }
            IkGuiImplPopups.endPopup();
        }

        context.io.configColorEditFlags = options;
        IkGuiImplUtils.popItemFlag();
        IkGuiImplPopups.endPopup();
    }

    /**
     * The right-click options menu for a color picker, to pick the picker type and alpha bar.
     *
     * @param referenceColor The color to show in the small preview pickers.
     * @param colorEditFlags The flags for the color picker.
     */
    static void colorPickerOptionsPopup(@NonNull float[] referenceColor, int colorEditFlags) {
        final boolean allowOptionPicker = (colorEditFlags & ColorEditFlags.PICKER_MASK) == 0;
        final boolean allowOptionAlphaBar =
                (colorEditFlags & ColorEditFlags.NO_ALPHA) == 0
                        && (colorEditFlags & ColorEditFlags.ALPHA_BAR) == 0;
        if ((!allowOptionPicker && !allowOptionAlphaBar)
                || !IkGuiImplPopups.beginPopup("context", WindowFlags.NONE)) {
            return;
        }

        IkGuiImplUtils.pushItemFlag(ItemFlags.INTERNAL_NO_MARK_EDITED, true);
        if (allowOptionPicker) {
            // FIXME(from ImGui): Picker size copied from main picker function
            final float pickerSizeX = IkGuiInternal.getFontSize() * 8;
            final float pickerSizeY =
                    Math.max(
                            IkGuiInternal.getFontSize() * 8
                                    - (IkGuiImplLayout.getFrameHeight()
                                            + context.style.variable.itemInnerSpacing.x),
                            1.0f);
            IkGuiImplLayout.pushItemWidth(pickerSizeX);
            for (int pickerType = 0; pickerType < 2; ++pickerType) {
                // Draw a small/thumbnail version of each picker type (over an invisible button
                // for selection)
                if (pickerType > 0) {
                    IkGuiImplLayout.separator();
                }
                IkGuiImplUtils.pushID(pickerType);
                int pickerFlags =
                        ColorEditFlags.NO_INPUTS
                                | ColorEditFlags.NO_OPTIONS
                                | ColorEditFlags.NO_LABEL
                                | ColorEditFlags.NO_SIDE_PREVIEW
                                | (colorEditFlags & ColorEditFlags.NO_ALPHA);
                pickerFlags |=
                        pickerType == 0
                                ? ColorEditFlags.PICKER_HUE_BAR
                                : ColorEditFlags.PICKER_HUE_WHEEL;
                final Vector2f backupPosition = IkGuiImplUtils.getCursorScreenPos();
                // By default, selectable() closes the popup
                if (IkGuiImplMiscWidgets.selectable(
                        "##selectable", false, SelectableFlags.NONE, pickerSizeX, pickerSizeY)) {
                    context.io.configColorEditFlags =
                            (context.io.configColorEditFlags & ~ColorEditFlags.PICKER_MASK)
                                    | (pickerFlags & ColorEditFlags.PICKER_MASK);
                }
                IkGuiImplUtils.setCursorScreenPos(backupPosition.x, backupPosition.y);
                final float[] previewingReference = new float[4];
                System.arraycopy(
                        referenceColor,
                        0,
                        previewingReference,
                        0,
                        (pickerFlags & ColorEditFlags.NO_ALPHA) != 0 ? 3 : 4);
                colorPicker4("##previewing_picker", previewingReference, pickerFlags, null);
                IkGuiImplUtils.popID();
            }
            IkGuiImplLayout.popItemWidth();
        }
        if (allowOptionAlphaBar) {
            if (allowOptionPicker) {
                IkGuiImplLayout.separator();
            }
            final IkInt options = new IkInt(context.io.configColorEditFlags);
            if (IkGuiImplMiscWidgets.checkboxFlags(
                    "Alpha Bar", options, ColorEditFlags.ALPHA_BAR)) {
                context.io.configColorEditFlags = options.get();
            }
        }
        IkGuiImplUtils.popItemFlag();
        IkGuiImplPopups.endPopup();
    }

    /**
     * Render a rectangle of a color, with a checkerboard behind it if the color has some
     * transparency.
     *
     * @param drawList The draw list to render to.
     * @param minX The left edge.
     * @param minY The top edge.
     * @param maxX The right edge.
     * @param maxY The bottom edge.
     * @param color The color, in RGBA format.
     * @param alpha The global alpha, in the range 0-1.
     * @param gridStep The size of each checkerboard cell.
     * @param gridOffsetX The x offset of the checkerboard grid.
     * @param gridOffsetY The y offset of the checkerboard grid.
     * @param rounding The corner rounding.
     * @param drawFlags Draw flags for which corners to round.
     */
    static void renderColorRectWithAlphaCheckerboard(
            @NonNull DrawList drawList,
            float minX,
            float minY,
            float maxX,
            float maxY,
            int color,
            float alpha,
            float gridStep,
            float gridOffsetX,
            float gridOffsetY,
            float rounding,
            int drawFlags) {
        int flags = drawFlags;
        if ((flags & DrawFlags.ROUND_CORNERS_MASK) == 0) {
            flags = DrawFlags.ROUND_CORNERS_ALL;
        }
        if ((color & 0xFF) == 0xFF) {
            drawList.addRectFilled(minX, minY, maxX, maxY, color, rounding, flags);
            return;
        }

        final int alphaMask = (int) (MathUtil.clamp(alpha, 0.0f, 1.0f) * 255.0f);
        final int background1 =
                (alphaBlendColors(Color.rgba(128, 128, 128, 255), color) & 0xFFFFFF00) | alphaMask;
        final int background2 =
                (alphaBlendColors(Color.rgba(204, 204, 204, 255), color) & 0xFFFFFF00) | alphaMask;
        // Dual layer is faster but incorrect if blending
        final boolean dualLayer = alpha == 1.0f;
        if (dualLayer) {
            drawList.addRectFilled(minX, minY, maxX, maxY, background1, rounding, flags);
        }

        int yi = 0;
        for (float y = minY + gridOffsetY; y < maxY; y += gridStep, ++yi) {
            final float y1 = clamp((int) y, minY, maxY);
            final float y2 = Math.min((int) (y + gridStep), maxY);
            if (y2 <= y1) {
                continue;
            }

            final float xStartOffset = dualLayer ? (((yi ^ 1) & 1) * gridStep) : 0.0f;
            final float xStep = dualLayer ? gridStep * 2.0f : gridStep;
            int xi = 0;
            for (float x = minX + gridOffsetX + xStartOffset; x < maxX; x += xStep, ++xi) {
                final float x1 = clamp((int) x, minX, maxX);
                final float x2 = Math.min((int) (x + gridStep), maxX);
                if (x2 <= x1) {
                    continue;
                }
                int cellFlags = DrawFlags.ROUND_CORNERS_NONE;
                if (y1 <= minY) {
                    if (x1 <= minX) {
                        cellFlags |= DrawFlags.ROUND_CORNERS_TOP_LEFT;
                    }
                    if (x2 >= maxX) {
                        cellFlags |= DrawFlags.ROUND_CORNERS_TOP_RIGHT;
                    }
                }
                if (y2 >= maxY) {
                    if (x1 <= minX) {
                        cellFlags |= DrawFlags.ROUND_CORNERS_BOTTOM_LEFT;
                    }
                    if (x2 >= maxX) {
                        cellFlags |= DrawFlags.ROUND_CORNERS_BOTTOM_RIGHT;
                    }
                }

                // Combine flags
                if (flags == DrawFlags.ROUND_CORNERS_NONE
                        || cellFlags == DrawFlags.ROUND_CORNERS_NONE) {
                    cellFlags = DrawFlags.ROUND_CORNERS_NONE;
                } else {
                    cellFlags &= flags;
                }
                final int cellColor;
                if (dualLayer) {
                    cellColor = background2;
                } else {
                    cellColor = ((yi + xi) & 1) != 0 ? background2 : background1;
                }
                drawList.addRectFilled(x1, y1, x2, y2, cellColor, rounding, cellFlags);
            }
        }
    }

    /**
     * Render arrows pointing at a position on a vertical bar, from both sides.
     *
     * @param drawList The draw list to render to.
     * @param posX The left of the bar.
     * @param posY The y position to point at.
     * @param halfSizeX Half of the width of each arrow.
     * @param halfSizeY Half of the height of each arrow.
     * @param barWidth The width of the bar.
     * @param alpha The global alpha, in the range 0-1.
     */
    private static void renderArrowsForVerticalBar(
            @NonNull DrawList drawList,
            float posX,
            float posY,
            float halfSizeX,
            float halfSizeY,
            float barWidth,
            float alpha) {
        final int alpha8 = floatToInt8Saturated(alpha);
        final int black = Color.rgba(0, 0, 0, alpha8);
        final int white = Color.rgba(255, 255, 255, alpha8);
        renderArrowPointingRight(
                drawList, posX + halfSizeX + 1, posY, halfSizeX + 2, halfSizeY + 1, black);
        renderArrowPointingRight(drawList, posX + halfSizeX, posY, halfSizeX, halfSizeY, white);
        renderArrowPointingLeft(
                drawList,
                posX + barWidth - halfSizeX - 1,
                posY,
                halfSizeX + 2,
                halfSizeY + 1,
                black);
        renderArrowPointingLeft(
                drawList, posX + barWidth - halfSizeX, posY, halfSizeX, halfSizeY, white);
    }

    /** Render a triangle pointing right, with the tip at (posX, posY). */
    private static void renderArrowPointingRight(
            DrawList drawList, float posX, float posY, float halfSizeX, float halfSizeY, int col) {
        drawList.addTriangleFilled(
                posX - halfSizeX,
                posY + halfSizeY,
                posX - halfSizeX,
                posY - halfSizeY,
                posX,
                posY,
                col);
    }

    /** Render a triangle pointing left, with the tip at (posX, posY). */
    private static void renderArrowPointingLeft(
            DrawList drawList, float posX, float posY, float halfSizeX, float halfSizeY, int col) {
        drawList.addTriangleFilled(
                posX + halfSizeX,
                posY - halfSizeY,
                posX + halfSizeX,
                posY + halfSizeY,
                posX,
                posY,
                col);
    }

    /**
     * Restore the saved hue if we are editing the same color as the one it was saved for.
     *
     * @param color The color being edited, as RGB.
     * @param hsv The hue, saturation, and value, where the hue may be modified.
     */
    private static void colorEditRestoreH(float[] color, float[] hsv) {
        if (context.colorEditSavedID != context.colorEditCurrentID
                || context.colorEditSavedColor != float4ToRGBA(color[0], color[1], color[2], 0)) {
            return;
        }
        hsv[0] = context.colorEditSavedHue;
    }

    /**
     * The color edit supports RGB and HSV inputs. In case of RGB input the resulting color may have
     * an undefined hue and/or saturation. Since the widget displays both RGB and HSV values, we
     * must preserve the hue and saturation to prevent these values resetting.
     *
     * @param color The color being edited, as RGB.
     * @param hsv The hue, saturation, and value, where the hue and saturation may be modified.
     */
    private static void colorEditRestoreHS(float[] color, float[] hsv) {
        if (context.colorEditSavedID != context.colorEditCurrentID
                || context.colorEditSavedColor != float4ToRGBA(color[0], color[1], color[2], 0)) {
            return;
        }

        // When S == 0, H is undefined. When H == 1 it wraps around to 0.
        if (hsv[1] == 0.0f || (hsv[0] == 0.0f && context.colorEditSavedHue == 1)) {
            hsv[0] = context.colorEditSavedHue;
        }

        // When V == 0, S is undefined
        if (hsv[2] == 0.0f) {
            hsv[1] = context.colorEditSavedSaturation;
        }
    }

    /**
     * Fill in any missing display, data type, picker, or input options from the stored options in
     * IkIO.configColorEditFlags.
     *
     * @param colorEditFlags The flags passed in to the widget.
     * @return The flags with all options set.
     */
    private static int applyStoredOptions(int colorEditFlags) {
        int flags = colorEditFlags;
        final int config = context.io.configColorEditFlags;
        final int[] masks = {
            ColorEditFlags.DISPLAY_MASK,
            ColorEditFlags.DATA_TYPE_MASK,
            ColorEditFlags.PICKER_MASK,
            ColorEditFlags.INPUT_MASK
        };
        int allMasks = 0;
        for (int mask : masks) {
            if ((flags & mask) == 0) {
                flags |= config & mask;
            }
            allMasks |= mask;
        }
        flags |= config & ~allMasks;
        flags = ensureSingleOption(flags, ColorEditFlags.DISPLAY_MASK, "display");
        return ensureSingleOption(flags, ColorEditFlags.INPUT_MASK, "input");
    }

    /**
     * Make sure exactly one option within a group is selected, logging an error and picking one if
     * not.
     *
     * @param flags The flags.
     * @param mask The mask for the group of options.
     * @param name The name of the option group, for logging.
     * @return The corrected flags.
     */
    private static int ensureSingleOption(int flags, int mask, String name) {
        final int selected = flags & mask;
        if (Integer.bitCount(selected) == 1) {
            return flags;
        }
        IkGuiImplDebugTools.reportError(
                log, "Color edit flags must have exactly one {} option selected", name);
        final int fallback =
                selected != 0
                        ? Integer.lowestOneBit(selected)
                        : (ColorEditFlags.DEFAULT_OPTIONS & mask);
        return (flags & ~mask) | fallback;
    }

    /**
     * A radio button for one option of a group of mutually exclusive flags.
     *
     * @param label The label.
     * @param options The current options.
     * @param mask The mask for the group of options.
     * @param option The option this button represents.
     * @return The options, updated if the button was clicked.
     */
    private static int optionRadioButton(String label, int options, int mask, int option) {
        if (IkGuiImplMiscWidgets.radioButton(label, (options & option) != 0)) {
            return (options & ~mask) | option;
        }
        return options;
    }

    /**
     * A selectable that copies its text to the clipboard when clicked.
     *
     * @param text The text.
     */
    private static void copySelectable(String text) {
        if (IkGuiImplMiscWidgets.selectable(text, false, SelectableFlags.NONE, 0, 0)) {
            IkGuiImplUtils.setClipboardText(text);
        }
    }

    /**
     * Parse a hexadecimal color like "#FF8000" or "#FF8000FF", similar to sscanf with "%02X" per
     * component. Components that are not provided are left as 0, except alpha which defaults to
     * 255.
     *
     * @param text The text to parse.
     * @param alpha Whether to parse an alpha component.
     * @param result Where to store the components, in the range 0-255.
     */
    static void parseHexColor(@NonNull String text, boolean alpha, @NonNull int[] result) {
        int position = 0;
        while (position < text.length()
                && (text.charAt(position) == '#'
                        || Character.isWhitespace(text.charAt(position)))) {
            ++position;
        }
        result[0] = 0;
        result[1] = 0;
        result[2] = 0;
        // Alpha defaults to 255 as it may be omitted (e.g. inputting #FFFFFF)
        result[3] = 0xFF;
        final int components = alpha ? 4 : 3;
        for (int component = 0; component < components; ++component) {
            int value = 0;
            int digits = 0;
            while (digits < 2 && position < text.length()) {
                final int digit = Character.digit(text.charAt(position), 16);
                if (digit < 0) {
                    break;
                }
                value = value * 16 + digit;
                ++digits;
                ++position;
            }
            if (digits == 0) {
                return;
            }
            result[component] = value;
        }
    }

    /**
     * Check that a color array is large enough for the given flags.
     *
     * @param color The color array.
     * @param colorEditFlags The flags, to check for NO_ALPHA.
     * @return True if the array is valid, false (and logs an error) otherwise.
     */
    private static boolean validateColor(float[] color, int colorEditFlags) {
        final int required = (colorEditFlags & ColorEditFlags.NO_ALPHA) != 0 ? 3 : 4;
        if (color == null || color.length < required) {
            IkGuiImplDebugTools.reportError(
                    log, "Color arrays must have at least {} elements", required);
            return false;
        }
        return true;
    }

    /** Compare the first count elements of two arrays. */
    private static boolean arraysEqual(float[] a, float[] b, int count) {
        for (int n = 0; n < count; ++n) {
            if (Float.compare(a[n], b[n]) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Convert a float color to RGBA format, rounding each component.
     *
     * @return The color in RGBA format.
     */
    static int float4ToRGBA(float r, float g, float b, float a) {
        return Color.rgba(
                floatToInt8Saturated(r),
                floatToInt8Saturated(g),
                floatToInt8Saturated(b),
                floatToInt8Saturated(a));
    }

    /**
     * Convert a float in the range 0-1 to an int in the range 0-255, rounding and clamping.
     *
     * @param value The value.
     * @return The value in the range 0-255.
     */
    static int floatToInt8Saturated(float value) {
        return (int) (saturate(value) * 255.0f + 0.5f);
    }

    /**
     * Convert a float in the range 0-1 to an int in the range 0-255, rounding but not clamping.
     *
     * @param value The value.
     * @return The value, scaled to 0-255 for the range 0-1.
     */
    private static int floatToInt8Unbound(float value) {
        return (int) (value * 255.0f + (value >= 0 ? 0.5f : -0.5f));
    }

    /**
     * Blend the second color over the first using the alpha of the second color.
     *
     * @param colorA The first color, in RGBA format.
     * @param colorB The second color, in RGBA format.
     * @return The opaque blended color.
     */
    private static int alphaBlendColors(int colorA, int colorB) {
        final float t = (colorB & 0xFF) / 255.0f;
        final int r = lerp((colorA >>> 24) & 0xFF, (colorB >>> 24) & 0xFF, t);
        final int g = lerp((colorA >>> 16) & 0xFF, (colorB >>> 16) & 0xFF, t);
        final int b = lerp((colorA >>> 8) & 0xFF, (colorB >>> 8) & 0xFF, t);
        return Color.rgba(r, g, b, 0xFF);
    }

    private static int lerp(int a, int b, float t) {
        return (int) (a + (b - a) * t);
    }

    private static float saturate(float value) {
        return MathUtil.clamp(value, 0.0f, 1.0f);
    }

    /**
     * Clamp a value to a range like MathUtil.clamp(), but without throwing if min is larger than
     * max.
     */
    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    /** Convert the first 3 elements of an array from HSV to RGB, in place. */
    private static void hsvToRgbInPlace(float[] values) {
        Color.hsvToColor(new float[] {values[0], values[1], values[2]}, values);
    }

    /** Convert the first 3 elements of an array from RGB to HSV, in place. */
    private static void rgbToHsvInPlace(float[] values) {
        Color.rgbTohsv(new float[] {values[0], values[1], values[2]}, values);
    }

    /**
     * Rotate a vector given the cosine and sine of the angle.
     *
     * @return A new rotated vector.
     */
    private static Vector2f rotate(Vector2f v, float cos, float sin) {
        return new Vector2f(v.x * cos - v.y * sin, v.x * sin + v.y * cos);
    }

    /** Check if a point is inside a triangle. */
    private static boolean triangleContainsPoint(Vector2f a, Vector2f b, Vector2f c, Vector2f p) {
        final boolean b1 = ((p.x - b.x) * (a.y - b.y) - (p.y - b.y) * (a.x - b.x)) < 0.0f;
        final boolean b2 = ((p.x - c.x) * (b.y - c.y) - (p.y - c.y) * (b.x - c.x)) < 0.0f;
        final boolean b3 = ((p.x - a.x) * (c.y - a.y) - (p.y - a.y) * (c.x - a.x)) < 0.0f;
        return (b1 == b2) && (b2 == b3);
    }

    /**
     * Calculate the barycentric coordinates of a point within a triangle.
     *
     * @param out Where to store (u, v, w), the weights for a, b, and c respectively.
     */
    private static void triangleBarycentricCoords(
            Vector2f a, Vector2f b, Vector2f c, Vector2f p, float[] out) {
        final float v0x = b.x - a.x;
        final float v0y = b.y - a.y;
        final float v1x = c.x - a.x;
        final float v1y = c.y - a.y;
        final float v2x = p.x - a.x;
        final float v2y = p.y - a.y;
        final float denominator = v0x * v1y - v1x * v0y;
        out[1] = (v2x * v1y - v1x * v2y) / denominator;
        out[2] = (v0x * v2y - v2x * v0y) / denominator;
        out[0] = 1.0f - out[1] - out[2];
    }

    /** Find the closest point on a triangle's edges to the given point. */
    private static Vector2f triangleClosestPoint(Vector2f a, Vector2f b, Vector2f c, Vector2f p) {
        final Vector2f projectionAB = lineClosestPoint(a, b, p);
        final Vector2f projectionBC = lineClosestPoint(b, c, p);
        final Vector2f projectionCA = lineClosestPoint(c, a, p);
        final float distanceAB = p.distanceSquared(projectionAB);
        final float distanceBC = p.distanceSquared(projectionBC);
        final float distanceCA = p.distanceSquared(projectionCA);
        final float min = Math.min(distanceAB, Math.min(distanceBC, distanceCA));
        if (min == distanceAB) {
            return projectionAB;
        }
        if (min == distanceBC) {
            return projectionBC;
        }
        return projectionCA;
    }

    /** Find the closest point on the line segment from a to b, to the point p. */
    private static Vector2f lineClosestPoint(Vector2f a, Vector2f b, Vector2f p) {
        final float apX = p.x - a.x;
        final float apY = p.y - a.y;
        final float abX = b.x - a.x;
        final float abY = b.y - a.y;
        final float dot = apX * abX + apY * abY;
        if (dot < 0.0f) {
            return new Vector2f(a);
        }
        final float abLengthSquared = abX * abX + abY * abY;
        if (dot > abLengthSquared) {
            return new Vector2f(b);
        }
        return new Vector2f(a.x + abX * dot / abLengthSquared, a.y + abY * dot / abLengthSquared);
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplColor() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
