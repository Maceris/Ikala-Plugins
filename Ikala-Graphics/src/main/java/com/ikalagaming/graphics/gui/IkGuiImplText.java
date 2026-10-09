package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.FontBackup;
import com.ikalagaming.graphics.gui.data.FontMetrics;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.PlatformIO;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.enums.MouseCursor;
import com.ikalagaming.graphics.gui.flags.ButtonFlags;
import com.ikalagaming.graphics.gui.flags.NavRenderCursorFlags;
import com.ikalagaming.graphics.gui.flags.PopupFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.MathUtil;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector4f;

@Slf4j
class IkGuiImplText {
    static Context context;

    /**
     * Vertically align upcoming text baseline to the frame padding, so that text will align
     * properly to regularly framed items (call if you have text on a line before a framed item).
     */
    public static void alignTextToFramePadding() {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return;
        }
        final float framePaddingY = context.style.variable.framePadding.y;
        window.lineSizeCurrent.y =
                Math.max(window.lineSizeCurrent.y, IkGuiInternal.getFontSize() + framePaddingY * 2);
        window.baseOffsetCurrentLine = Math.max(window.baseOffsetCurrentLine, framePaddingY);
    }

    /**
     * Draw a small circle and keep the cursor on the same line. Advances the cursor x position by
     * getTreeNodeToLabelSpacing(), the same distance that treeNode() uses.
     */
    public static void bullet() {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return;
        }

        final float fontSize = IkGuiInternal.getFontSize();
        final float framePaddingX = context.style.variable.framePadding.x;
        final float lineHeight =
                Math.max(
                        Math.min(
                                window.lineSizeCurrent.y,
                                fontSize + context.style.variable.framePadding.y * 2),
                        fontSize);
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y;
        final RectFloat bb = new RectFloat(x, y, x + fontSize, y + lineHeight);
        IkGuiInternal.itemSize(bb.getWidth(), bb.getHeight(), -1.0f);
        if (!IkGuiInternal.itemAdd(bb, 0)) {
            IkGuiImplLayout.sameLine(0, framePaddingX * 2);
            return;
        }

        // Render and stay on the same line
        IkGuiInternal.renderBullet(
                window.drawList,
                bb.getLeft() + framePaddingX + fontSize * 0.5f,
                bb.getTop() + lineHeight * 0.5f,
                IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT));
        IkGuiImplLayout.sameLine(0, framePaddingX * 2.0f);
    }

    /**
     * Shortcut for bullet() + text().
     *
     * @param text The text to display.
     */
    public static void bulletText(@NonNull String text) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return;
        }

        final float fontSize = IkGuiInternal.getFontSize();
        final float framePaddingX = context.style.variable.framePadding.x;
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, text, false, -1.0f);
        // Empty text doesn't add padding
        final float totalWidth =
                fontSize + (labelSize.x > 0.0f ? (labelSize.x + framePaddingX * 2) : 0.0f);
        final float totalHeight = labelSize.y;
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y + window.baseOffsetCurrentLine;
        IkGuiInternal.itemSize(totalWidth, totalHeight, 0.0f);
        final RectFloat bb = new RectFloat(x, y, x + totalWidth, y + totalHeight);
        if (!IkGuiInternal.itemAdd(bb, 0)) {
            return;
        }

        // Render
        IkGuiInternal.renderBullet(
                window.drawList,
                bb.getLeft() + framePaddingX + fontSize * 0.5f,
                bb.getTop() + fontSize * 0.5f,
                IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT));
        IkGuiInternal.renderText(
                bb.getLeft() + fontSize + framePaddingX * 2, bb.getTop(), text, false);
    }

    public static void popFont() {
        if (context.fontStack.isEmpty()) {
            IkGuiImplDebugTools.reportError(log, "Trying to pop a font when none are pushed");
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

    public static void pushFont(@NonNull String font, int size) {
        String oldFont = context.font == null ? null : context.font.name;
        context.fontStack.push(new FontBackup(oldFont, context.fontSize));
        context.font = context.io.fonts.getFont(font);
        context.fontSize = size;
    }

    public static void pushFontSize(int size) {
        final String oldFont = context.font == null ? null : context.font.name;
        context.fontStack.push(new FontBackup(oldFont, context.fontSize));
        context.fontSize = size;
    }

    public static void setFont(@NonNull String fontPath, int size) {
        if (!context.io.fonts.isFontLoaded(fontPath)) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "Font {} is not loaded, cannot use it as the current font until loaded.",
                    fontPath);
            return;
        }
        context.font = context.io.fonts.getFont(fontPath);
        context.fontSize = size;
    }

    public static void setFontFallbacks(@NonNull String... fontList) {
        context.fontFallbacks.clear();
        for (String fontName : fontList) {
            if (!context.io.fonts.isFontLoaded(fontName) && !context.io.fonts.loadFont(fontName)) {
                continue;
            }
            context.fontFallbacks.add(context.io.fonts.getFont(fontName));
        }
    }

    public static void setFontSize(int fontSize) {
        context.fontSize = fontSize;
    }

    public static void text(@NonNull String text) {
        textEx(text);
    }

    public static void textColored(float r, float g, float b, float a, @NonNull String text) {
        textColored(Color.rgba(r, g, b, a), text);
    }

    public static void textColored(int r, int g, int b, int a, @NonNull String text) {
        textColored(Color.rgba(r, g, b, a), text);
    }

    public static void textColored(int color, @NonNull String text) {
        IkGuiImplUtils.pushStyleColor(ColorType.TEXT, color);
        textEx(text);
        IkGuiImplUtils.popStyleColor();
    }

    public static void textDisabled(@NonNull String text) {
        textColored(IkGuiImplUtils.getColor(ColorType.TEXT_DISABLED), text);
    }

    /**
     * Render raw text without any formatting. This is the same as text() in Java, as we don't use
     * format strings.
     *
     * @param text The text to display.
     */
    public static void textUnformatted(@NonNull String text) {
        textEx(text);
    }

    /**
     * Text that wraps at the end of the window (or column) by default. If a text wrap position has
     * been pushed, that is used instead.
     *
     * @param text The text to display.
     */
    public static void textWrapped(@NonNull String text) {
        final Window window = IkGuiInternal.getCurrentWindow();
        // Keep the existing wrap position if one is set
        final boolean needBackup = window.currentTextWrapPosition < 0.0f;
        if (needBackup) {
            IkGuiImplLayout.pushTextWrapPos(0.0f);
        }
        textEx(text);
        if (needBackup) {
            IkGuiImplLayout.popTextWrapPos();
        }
    }

    /**
     * Internal implementation of text rendering, which handles layout, clipping and wrapping.
     *
     * @param text The text to display.
     */
    static void textEx(@NonNull String text) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return;
        }

        final float textPosX = window.cursorPosition.x;
        final float textPosY = window.cursorPosition.y + window.baseOffsetCurrentLine;
        final float wrapPosX = window.currentTextWrapPosition;
        final boolean wrapEnabled = wrapPosX >= 0.0f;
        final float wrapWidth =
                wrapEnabled ? IkGuiInternal.calcWrapWidthForPos(textPosX, wrapPosX) : 0.0f;

        final Vector2f textSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(textSize, text, false, wrapWidth);
        final RectFloat bb =
                new RectFloat(textPosX, textPosY, textPosX + textSize.x, textPosY + textSize.y);
        IkGuiInternal.itemSize(textSize.x, textSize.y, 0.0f);
        if (!IkGuiInternal.itemAdd(bb, 0)) {
            return;
        }

        // Render (we don't hide text after ## in this end-user function)
        IkGuiInternal.renderTextWrapped(bb.getLeft(), bb.getTop(), text, wrapWidth);
    }

    /**
     * Hyperlink text, which acts like a button. The hovered and held colors are derived from the
     * text link style color.
     *
     * @param label The label, which is also used for the ID.
     * @return True if the link was clicked.
     */
    public static boolean textLink(@NonNull String label) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return false;
        }

        final int id = window.getID(label);
        final float x = window.cursorPosition.x;
        final float y = window.cursorPosition.y + window.baseOffsetCurrentLine;
        final Vector2f size = new Vector2f();
        IkGuiImplUtils.calcTextSize(size, label, true, -1.0f);
        final RectFloat bb = new RectFloat(x, y, x + size.x, y + size.y);
        IkGuiInternal.itemSize(size.x, size.y, 0.0f);
        if (!IkGuiInternal.itemAdd(bb, id)) {
            return false;
        }

        final IkBoolean hovered = new IkBoolean(false);
        final IkBoolean held = new IkBoolean(false);
        final boolean pressed =
                IkGuiInternal.buttonBehavior(bb, id, hovered, held, ButtonFlags.NONE);
        IkGuiImplNav.renderNavCursor(bb, id, NavRenderCursorFlags.NONE, -1.0f);

        if (hovered.get()) {
            IkGuiImplUtils.setMouseCursor(MouseCursor.HAND);
        }

        // Brighten and shift the hue when hovered or held, and use a darker underline
        final Vector4f linkColor = new Vector4f();
        IkGuiImplUtils.colorConvertU32ToFloat4(
                IkGuiImplUtils.getColor(ColorType.TEXT_LINK), linkColor);
        final float[] hsv = new float[3];
        Color.rgbTohsv(new float[] {linkColor.x, linkColor.y, linkColor.z}, hsv);
        if (held.get() || hovered.get()) {
            hsv[2] = MathUtil.clamp(hsv[2] + (held.get() ? 0.4f : 0.3f), 0.0f, 1.0f);
            hsv[0] = (hsv[0] + 0.02f) % 1.0f;
        }
        final float[] rgb = new float[3];
        Color.hsvToColor(hsv, rgb);
        final int textColor = Color.rgba(rgb[0], rgb[1], rgb[2], linkColor.w);
        hsv[2] = MathUtil.clamp(hsv[2] - 0.2f, 0.0f, 1.0f);
        Color.hsvToColor(hsv, rgb);
        final int lineColor = Color.rgba(rgb[0], rgb[1], rgb[2], linkColor.w);

        final FontMetrics metrics = IkGuiInternal.getFontMetrics();
        final float descent = metrics != null ? metrics.descent() : 0.0f;
        final float lineY = bb.getBottom() + (float) Math.floor(descent * 0.2f);
        window.drawList.addLineH(
                bb.getLeft(),
                bb.getRight(),
                lineY,
                IkGuiImplUtils.applyGlobalAlpha(lineColor, false),
                1.0f);

        IkGuiImplUtils.pushStyleColor(ColorType.TEXT, textColor);
        IkGuiInternal.renderText(bb.getLeft(), bb.getTop(), label, true);
        IkGuiImplUtils.popStyleColor();

        IkGuiInternal.testEngineItemInfo(id, label, context.lastItemData.statusFlags);
        return pressed;
    }

    /**
     * Hyperlink text, which opens a URL or file with the platform when clicked. Shows the URL as a
     * tooltip, and has a context menu to copy it.
     *
     * @param label The label, which is also used for the ID.
     * @param url The URL to open, or null to use the label.
     * @return True if the link was clicked.
     * @see PlatformIO#openInShellFunction
     */
    public static boolean textLinkOpenURL(@NonNull String label, String url) {
        final String target = url != null ? url : label;
        final boolean pressed = textLink(label);
        if (pressed && context.platformIO.openInShellFunction != null) {
            context.platformIO.openInShellFunction.test(target);
        }
        // It is more reassuring to always display the URL, even when it's the same as the label
        IkGuiImplPopups.setItemTooltip(String.format("Open '%s'", target));
        if (IkGuiImplPopups.beginPopupContextItem((String) null, PopupFlags.MOUSE_BUTTON_DEFAULT)) {
            if (IkGuiImplMenus.menuItem("Copy Link###CopyLink", null, false, true)) {
                IkGuiImplUtils.setClipboardText(target);
            }
            IkGuiImplPopups.endPopup();
        }
        return pressed;
    }

    public static void value(String prefix, boolean value) {
        text(String.format("%s: %s", prefix, value ? "true" : "false"));
    }

    public static void value(String prefix, float value) {
        text(String.format("%s: %.3f", prefix, value));
    }

    public static void value(String prefix, float value, String format) {
        if (format == null) {
            value(prefix, value);
            return;
        }
        text(prefix + ": " + String.format(format, value));
    }

    public static void value(String prefix, int value) {
        text(String.format("%s: %d", prefix, value));
    }

    public static void value(String prefix, long value) {
        text(String.format("%s: %d", prefix, value));
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplText() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
