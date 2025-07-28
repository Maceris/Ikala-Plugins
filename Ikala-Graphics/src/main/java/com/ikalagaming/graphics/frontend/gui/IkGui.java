package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.callback.GuiInputTextCallback;
import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.*;

import lombok.NonNull;
import org.joml.Vector2f;
import org.joml.Vector4f;

/** Immediate mode GUI library based on ImGui. */
public class IkGui {
    private static Context context;
    private static IkIO io;
    private static PlatformIO platformIO;
    private static DrawList
            windowDrawList; // TODO(ches) is this just the current window? Can we store an ID
    // instead?
    private static DrawList backgroundDrawList;
    private static DrawList foregroundDrawList;
    private static Storage storage;
    private static Viewport windowViewport;
    private static DrawData drawData;
    private static Style style;
    private static Font font;

    public static void init() {
        context = new Context();
        io = new IkIO();
        platformIO = new PlatformIO();
        windowDrawList = new DrawList();
        backgroundDrawList = new DrawList();
        foregroundDrawList = new DrawList();
        storage = new Storage();
        windowViewport = new Viewport();
        drawData = new DrawData();
        style = new Style();
        font = new Font();
    }

    public static Context createContext() {
        return new Context();
    }

    public static void destroyContext() {
        // TODO(ches) destroy current context
    }

    public static void destroyContext(@NonNull Context context) {
        // TODO(ches) destroy context
    }

    public static Context getCurrentContext() {
        return context;
    }

    public static void setCurrentContext(@NonNull Context context) {
        IkGui.context = context;
    }

    public static IkIO getIO() {
        return io;
    }

    public static Style getStyle() {
        return style;
    }

    public static Viewport getWindowViewport() {
        // TODO(ches) complete this
        return windowViewport;
    }

    public static void begin(@NonNull String title) {
        // TODO(ches) complete this
    }

    public static void begin(@NonNull String title, @NonNull IkBoolean open) {
        // TODO(ches) complete this
    }

    public static void begin(@NonNull String title, int windowFlags) {
        // TODO(ches) complete this
    }

    public static void begin(@NonNull String title, @NonNull IkBoolean open, int windowFlags) {
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

    public static void endChild() {
        // TODO(ches) complete this
    }

    public static void newFrame() {
        // TODO(ches) complete this
    }

    public static void endFrame() {
        // TODO(ches) complete this
    }

    public static void render() {
        // TODO(ches) complete this
    }

    public static void end() {
        // TODO(ches) complete this
    }

    public static boolean isWindowAppearing() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isWindowCollapsed() {
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

    public static Vector2f getWindowPos() {
        Vector2f pos = new Vector2f();
        getWindowPos(pos);
        return pos;
    }

    public static void getWindowPos(@NonNull Vector2f pos) {
        // TODO(ches) complete this
    }

    public static float getWindowPosX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getWindowPosY() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getWindowSize() {
        Vector2f size = new Vector2f();
        getWindowSize(size);
        return size;
    }

    public static void getWindowSize(@NonNull Vector2f size) {
        // TODO(ches) complete this
    }

    public static float getWindowwidth() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getWindowheight() {
        // TODO(ches) complete this
        return 0;
    }

    public static void setNextWindowPos(float x, float y) {
        // TODO(ches) complete this
    }

    public static void setNextWindowPos(float x, float y, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static void setNextWindowPos(
            float x, float y, @NonNull Condition condition, int pivotX, int pivotY) {
        // TODO(ches) complete this
    }

    public static void setNextWindowSize(float x, float y) {
        // TODO(ches) complete this
    }

    public static void setNextWindowSize(float x, float y, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static void setNextWindowCollapsed(boolean collapsed) {
        // TODO(ches) complete this
    }

    public static void setNextWindowCollapsed(boolean collapsed, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static void setNextWindowFocus() {
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

    public static void setWindowCollapsed(boolean collapsed) {
        // TODO(ches) complete this
    }

    public static void setWindowCollapsed(boolean collapsed, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static void setWindowFocus() {
        // TODO(ches) complete this
    }

    public static Vector2f getContentRegionAvailable() {
        Vector2f region = new Vector2f();
        getContentRegionAvailable(region);
        return region;
    }

    public static void getContentRegionAvailable(@NonNull Vector2f region) {
        // TODO(ches) complete this
    }

    public static float getContentRegionAvailableX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getContentRegionAvailableY() {
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

    public static float getScrollX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getScrollY() {
        // TODO(ches) complete this
        return 0;
    }

    public static void setScrollX(float x) {
        // TODO(ches) complete this
    }

    public static void setScrollY(float x) {
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

    public static void pushFont(@NonNull Font font) {
        // TODO(ches) complete this
    }

    public static void popFont() {
        // TODO(ches) complete this
    }

    public static void pushStyleColor(@NonNull ColorType type, float r, float g, float b, float a) {
        // TODO(ches) complete this
    }

    public static void pushStyleColor(@NonNull ColorType type, int r, int g, int b, int a) {
        // TODO(ches) complete this
    }

    public static void pushStyleColor(@NonNull ColorType type, int rgba) {
        // TODO(ches) complete this
    }

    public static void popStyleColor() {
        // TODO(ches) complete this
    }

    public static void popStyleColor(int count) {
        // TODO(ches) complete this
    }

    public static void pushStyleVar(@NonNull StyleVar var, float value) {
        if (var.getDimensions() != 1) {
            // TODO(ches) error
        }
        // TODO(ches) complete this
    }

    public static void pushStyleVar(@NonNull StyleVar var, float x, float y) {
        if (var.getDimensions() != 2) {
            // TODO(ches) error
        }
        // TODO(ches) complete this
    }

    public static void popStyleVar() {
        // TODO(ches) complete this
    }

    public static void popStyleVar(int count) {
        // TODO(ches) complete this
    }

    public static void pushAllowKeyboardFocus(boolean allow) {
        // TODO(ches) complete this
    }

    public static void popAllowKeyboardFocus() {
        // TODO(ches) complete this
    }

    public static void pushButtonRepeat(boolean repeat) {
        // TODO(ches) complete this
    }

    public static void popButtonRepeat() {
        // TODO(ches) complete this
    }

    public static void pushItemWidth(float width) {
        // TODO(ches) complete this
    }

    public static void popItemWidth() {
        // TODO(ches) complete this
    }

    public static void setNextItemWidth(float width) {
        // TODO(ches) complete this
    }

    public static float calculateItemWidth() {
        // TODO(ches) complete this
        return 0;
    }

    public static void pushTextWrapPos() {
        // TODO(ches) complete this
    }

    public static void pushTextWrapPos(float x) {
        // TODO(ches) complete this
    }

    public static void popTextWrapPos() {
        // TODO(ches) complete this
    }

    public static Font getFont() {
        // TODO(ches) complete this
        return font;
    }

    public static int getFontSize() {
        // TODO(ches) complete this
        return 0;
    }

    public static int getColor(@NonNull ColorType type) {
        // TODO(ches) complete this
        return 0;
    }

    public static int getColor(@NonNull ColorType type, float alphaMultiplier) {
        // TODO(ches) complete this
        return 0;
    }

    public static int getColor(float r, float g, float b, float a) {
        // TODO(ches) complete this
        return 0;
    }

    /**
     * Returns a color, represented as an int, with the current style's alpha value applied to it.
     *
     * @param color The original color.
     * @return The scaled color.
     */
    public static int getColor(int color) {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector4f getStyleColor(@NonNull StyleVar styleVar) {
        Vector4f value = new Vector4f();
        getStyleColor(styleVar, value);
        return value;
    }

    public static void getStyleColor(@NonNull StyleVar styleVar, @NonNull Vector4f value) {
        // TODO(ches) complete this
    }

    public static void separator() {
        // TODO(ches) complete this
    }

    public static void sameLine() {
        // TODO(ches) complete this
    }

    public static void sameLine(float offsetFromStartX) {
        // TODO(ches) complete this
    }

    public static void sameLine(float offsetFromStartX, float spacingAfterCurrent) {
        // TODO(ches) complete this
    }

    public static void newLine() {
        // TODO(ches) complete this
    }

    /** Adds vertical space, equal to the current style's y spacing. */
    public static void spacing() {
        // TODO(ches) complete this
    }

    public static void dummy(float width, float height) {
        // TODO(ches) complete this
    }

    public static void indent() {
        // TODO(ches) complete this
    }

    public static void indent(float width) {
        // TODO(ches) complete this
    }

    public static void unindent() {
        // TODO(ches) complete this
    }

    public static void unindent(float width) {
        // TODO(ches) complete this
    }

    public static void beginGroup() {
        // TODO(ches) complete this
    }

    public static void endGroup() {
        // TODO(ches) complete this
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

    public static void setCursorPos(float x, float y) {
        // TODO(ches) complete this
    }

    public static void setCursorPosX(float x) {
        // TODO(ches) complete this
    }

    public static void setCursorPosY(float y) {
        // TODO(ches) complete this
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

    public static void setCursorScreenPos(float x, float y) {
        // TODO(ches) complete this
    }

    public static void alignTextToFramePadding() {
        // TODO(ches) complete this
    }

    public static float getTextLineHeight() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getTextLineHeightWithSpacing() {
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

    public static void pushID(String name) {
        // TODO(ches) complete this
    }

    public static void pushID(long id) {
        // TODO(ches) complete this
    }

    public static void pushID(int id) {
        // TODO(ches) complete this
    }

    public static void popID() {
        // TODO(ches) complete this
    }

    public static int getID(String name) {
        // TODO(ches) complete this
        return 0;
    }

    public static int getID(long id) {
        // TODO(ches) complete this
        return 0;
    }

    public static void textUnformatted(String text) {
        // TODO(ches) complete this
    }

    public static void text(String text) {
        // TODO(ches) complete this
    }

    public static void textColored(float r, float g, float b, float a, String text) {
        // TODO(ches) complete this
    }

    public static void textColored(int r, int g, int b, int a, String text) {
        // TODO(ches) complete this
    }

    public static void textColored(int color, String text) {
        // TODO(ches) complete this
    }

    public static void textDisabled(String text) {
        // TODO(ches) complete this
    }

    public static void textWrapped(String text) {
        // TODO(ches) complete this
    }

    public static void labelText(String label, String text) {
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

    public static boolean smallButton(String text) {
        // TODO(ches) complete this
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

    public static boolean arrowButton(String text, @NonNull Direction direction) {
        // TODO(ches) complete this
        return false;
    }

    public static void image(int textureID, float width, float height) {
        // TODO(ches) complete this
    }

    public static void image(int textureID, Vector2f size) {
        image(textureID, size.x, size.y);
    }

    public static void image(int textureID, float width, float height, float u, float value) {
        // TODO(ches) complete this
    }

    public static void image(int textureID, Vector2f size, float u, float value) {
        image(textureID, size.x, size.y, u, value);
    }

    public static void image(int textureID, float width, float height, Vector2f uv0) {
        image(textureID, width, height, uv0.x, uv0.y);
    }

    public static void image(int textureID, Vector2f size, Vector2f uv0) {
        image(textureID, size.x, size.y, uv0.x, uv0.y);
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

    public static boolean radioButton(String label, boolean initialState) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean radioButton(String label, IkInt selectionStorage, int value) {
        return false;
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

    public static void bullet() {
        // TODO(ches) complete this
    }

    public static boolean beginCombo(String label, String previewValue) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginCombo(String label, String previewValue, int comboFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static void endCombo() {
        // TODO(ches) complete this
    }

    public static boolean combo(String label, IkInt currentItem, String[] items) {
        return false;
    }

    public static boolean combo(
            String label, IkInt currentItem, String[] items, int popumaxHeightInItems) {
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

    public static boolean inputTextMultiline(String label, IkString text, int inputTextFlags) {
        return false;
    }

    public static boolean inputTextMultiline(
            String label, IkString text, int inputTextFlags, GuiInputTextCallback callback) {
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

    public static boolean inputFloat2(String label, int[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat2(String label, int[] value, int inputTextFlags) {
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

    public static boolean inputFloat4(String label, int[] value) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat4(String label, int[] value, int inputTextFlags) {
        // TODO(ches) complete this
        return false;
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

    public static void setColorEditOptions(int colorEditFlags) {
        // TODO(ches) complete this
    }

    public static boolean treeNode(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean treeNode(String name, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean treeNode(long id, String format) {
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

    public static boolean treeNodeEx(long id, int treeNodeFlags, String format) {
        // TODO(ches) complete this
        return false;
    }

    public static void treePush() {
        // TODO(ches) complete this
    }

    public static void treePush(String name) {
        // TODO(ches) complete this
    }

    public static void treePush(long id) {
        // TODO(ches) complete this
    }

    public static void treePop() {
        // TODO(ches) complete this
    }

    public static float getTreeNodeToLabelSpacing() {
        // TODO(ches) complete this
        return 0;
    }

    public static boolean collapsingHeader(String var0) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean collapsingHeader(String label, int treeNodeFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean collapsingHeader(String label, IkBoolean visible) {
        return false;
    }

    public static boolean collapsingHeader(String label, IkBoolean visible, int treeNodeFlags) {
        return false;
    }

    public static void setNextItemOpen(boolean isOpen) {
        // TODO(ches) complete this
    }

    public static void setNextItemOpen(boolean isOpen, @NonNull Condition condition) {
        // TODO(ches) complete this
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

    public static boolean beginListBox(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginListBox(String label, float width, float height) {
        // TODO(ches) complete this
        return false;
    }

    public static void endListBox() {
        // TODO(ches) complete this
    }

    public static void listBox(String label, IkInt currentItem, String[] items) {
        // TODO(ches) complete this
    }

    public static void listBox(String label, IkInt currentItem, String[] items, int heightInItems) {
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

    public static void value(String prefix, boolean value) {
        // TODO(ches) complete this
    }

    public static void value(String prefix, int value) {
        // TODO(ches) complete this
    }

    public static void value(String prefix, long value) {
        // TODO(ches) complete this
    }

    public static void value(String prefix, float value) {
        // TODO(ches) complete this
    }

    public static void value(String prefix, float value, String format) {
        // TODO(ches) complete this
    }

    public static boolean beginMenuBar() {
        // TODO(ches) complete this
        return false;
    }

    public static void endMenuBar() {
        // TODO(ches) complete this
    }

    public static boolean beginMainMenuBar() {
        // TODO(ches) complete this
        return false;
    }

    public static void endMainMenuBar() {
        // TODO(ches) complete this
    }

    public static boolean beginMenu(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginMenu(String label, boolean enabled) {
        // TODO(ches) complete this
        return false;
    }

    public static void endMenu() {
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

    public static void beginTooltip() {
        // TODO(ches) complete this
    }

    public static void endTooltip() {
        // TODO(ches) complete this
    }

    public static void setTooltip(String text) {
        // TODO(ches) complete this
    }

    public static boolean beginPopup(String name) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopup(String name, int windowFlags) {
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

    public static boolean beginPopupModal(String name, int windowFlags) {
        return false;
    }

    public static boolean beginPopupModal(String name, IkBoolean open, int windowFlags) {
        return false;
    }

    public static void endPopup() {
        // TODO(ches) complete this
    }

    public static void openPopup(String name) {
        // TODO(ches) complete this
    }

    public static void openPopup(String name, int popupFlags) {
        // TODO(ches) complete this
    }

    public static void openPopupOnItemClick() {
        // TODO(ches) complete this
    }

    public static void openPopupOnItemClick(String name) {
        // TODO(ches) complete this
    }

    public static void openPopupOnItemClick(int id) {
        // TODO(ches) complete this
    }

    public static void openPopupOnItemClick(String name, int popupFlags) {
        // TODO(ches) complete this
    }

    public static void closeCurrentPopup() {
        // TODO(ches) complete this
    }

    public static boolean beginPopupContextItem() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextItem(String name) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextItem(int id) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextItem(String name, int popupFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextWindow() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextWindow(String name) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextWindow(int id) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextWindow(String name, int popupFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextVoid() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextVoid(String name) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextVoid(int id) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginPopupContextVoid(String name, int popupFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isPopuopen(String name) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isPopuopen(String name, int popupFlags) {
        // TODO(ches) complete this
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

    public static void endTable() {
        // TODO(ches) complete this
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

    public static boolean tableNextColumn() {
        // TODO(ches) complete this
        return false;
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

    public static void tableHeadersRow() {
        // TODO(ches) complete this
    }

    public static void tableHeader(String label) {
        // TODO(ches) complete this
    }

    public static int tableGetColumnCount() {
        // TODO(ches) complete this
        return 0;
    }

    public static int tableGetColumnIndex() {
        // TODO(ches) complete this
        return 0;
    }

    public static int tableGetRowIndex() {
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

    public static int tableGetColumnFlags() {
        // TODO(ches) complete this
        return 0;
    }

    public static int tableGetColumnFlags(int index) {
        // TODO(ches) complete this
        return 0;
    }

    public static void tableSetBgColor(@NonNull TableBackgroundTarget target, int color) {
        // TODO(ches) complete this
    }

    public static void tableSetBgColor(
            @NonNull TableBackgroundTarget target, int color, int columnIndex) {
        // TODO(ches) complete this
    }

    public static boolean beginTabBar(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginTabBar(String label, int tabBarFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static void endTabBar() {
        // TODO(ches) complete this
    }

    public static boolean beginTabItem(String label) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginTabItem(String label, IkBoolean open) {
        return false;
    }

    public static boolean beginTabItem(String label, int tabItemFlags) {
        return false;
    }

    public static boolean beginTabItem(String label, IkBoolean open, int tabItemFlags) {
        return false;
    }

    public static void endTabItem() {
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

    public static void setTabItemClosed(String label) {
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

    public static void setNextWindowDockID(int id) {
        // TODO(ches) complete this
    }

    public static void setNextWindowDockID(int id, @NonNull Condition condition) {
        // TODO(ches) complete this
    }

    public static int getWindowDockID() {
        // TODO(ches) complete this
        return 0;
    }

    public static boolean isWindowDocked() {
        // TODO(ches) complete this
        return false;
    }

    public static void logToTTY() {
        // TODO(ches) complete this
    }

    public static void logToTTY(int maxDepth) {
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

    public static void logToClipboard() {
        // TODO(ches) complete this
    }

    public static void logToClipboard(int maxDepth) {
        // TODO(ches) complete this
    }

    public static void logFinish() {
        // TODO(ches) complete this
    }

    public static void logButtons() {
        // TODO(ches) complete this
    }

    public static void logText(String text) {
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

    public static boolean setDragDropPayload(String dataType, Object payload) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean setDragDropPayload(
            String dataType, Object payload, @NonNull Condition condition) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean setDragDropPayload(Object payload) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean setDragDropPayload(Object payload, @NonNull Condition condition) {
        // TODO(ches) complete this
        return false;
    }

    public static void endDragDropSource() {
        // TODO(ches) complete this
    }

    public static boolean beginDragDropTarget() {
        // TODO(ches) complete this
        return false;
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

    public static <T> T acceptDragDropPayload(Class<T> aClass) {
        // TODO(ches) complete this
        return null;
    }

    public static <T> T acceptDragDropPayload(Class<T> aClass, int dragDropFlags) {
        // TODO(ches) complete this
        return null;
    }

    public static void endDragDropTarget() {
        // TODO(ches) complete this
    }

    public static <T> T getDragDropPayload() {
        // TODO(ches) complete this
        return null;
    }

    public static <T> T getDragDropPayload(String dataType) {
        // TODO(ches) complete this
        return null;
    }

    public static <T> T getDragDropPayload(Class<T> aClass) {
        // TODO(ches) complete this
        return null;
    }

    public static void beginDisabled() {
        beginDisabled(true);
        // TODO(ches) complete this
    }

    public static void beginDisabled(boolean disabled) {
        // TODO(ches) complete this
    }

    public static void endDisabled() {
        // TODO(ches) complete this
    }

    public static void pushClipRect(
            float minX, float minY, float maxX, float maxY, boolean intersectWithCurrentClipRect) {
        // TODO(ches) complete this
    }

    public static void popClipRect() {
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

    public static boolean isItemHovered() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemHovered(int hoveredFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemActive() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemFocused() {
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

    public static boolean isItemVisible() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemEdited() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isItemActivated() {
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

    public static boolean isItemToggledOpen() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isAnyItemHovered() {
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

    public static Vector2f getItemRectSize() {
        Vector2f value = new Vector2f();
        getItemRectSize(value);
        return value;
    }

    public static void getItemRectSize(Vector2f value) {
        // TODO(ches) complete this
    }

    public static float getItemRectwidth() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getItemRectheight() {
        // TODO(ches) complete this
        return 0;
    }

    public static void setItemAllowOverlap() {
        // TODO(ches) complete this
    }

    public static Viewport getMainViewport() {
        return null;
    }

    public static boolean isRectVisible(float width, float height) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isRectVisible(float minX, float minY, float maxX, float maxY) {
        // TODO(ches) complete this
        return false;
    }

    public static double getTime() {
        // TODO(ches) complete this
        return 0;
    }

    public static int getFrameCount() {
        // TODO(ches) complete this
        return 0;
    }

    public static DrawList getBackgroundDrawList() {
        return null;
    }

    public static DrawList getForegroundDrawList() {
        return null;
    }

    public static DrawList getBackgroundDrawList(Viewport viewport) {
        return null;
    }

    public static DrawList getForegroundDrawList(Viewport viewport) {
        return null;
    }

    public static void setStateStorage(GuiStorage storage) {}

    public static GuiStorage getStateStorage() {
        return null;
    }

    public static boolean beginChildFrame(int id, float width, float height) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginChildFrame(int id, float width, float height, int windowFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static void endChildFrame() {
        // TODO(ches) complete this
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

    public static void calcTextSize(Vector2f result, String text, float wrapWidth) {
        // TODO(ches) complete this
    }

    public static void calcTextSize(
            Vector2f result, String text, boolean hideTextAfterDoubleHash, float wrapWidth) {
        // TODO(ches) complete this
    }

    public final Vector4f colorConvertU32ToFloat4(int in) {
        Vector4f value = new Vector4f();
        colorConvertU32ToFloat4(in, value);
        return value;
    }

    public static void colorConvertU32ToFloat4(int in, Vector4f out) {
        // TODO(ches) complete this
    }

    public static int colorConvertFloat4ToU32(float r, float g, float b, float a) {
        // TODO(ches) complete this
        return 0;
    }

    public static void colorConvertRGBtoHSV(float[] in, float[] out) {
        // TODO(ches) complete this
    }

    public static void colorConvertHSVtoRGB(float[] in, float[] out) {
        // TODO(ches) complete this
    }

    public static int getKeyIndex(@NonNull Key key) {
        // TODO(ches) complete this
        return 0;
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

    public static boolean getKeyPressedAmount(int userKeyIndex, float repeatDelay, float rate) {
        // TODO(ches) complete this
        return false;
    }

    public static void captureKeyboardFromApp() {
        // TODO(ches) complete this
    }

    public static void captureKeyboardFromApp(boolean capture) {
        // TODO(ches) complete this
    }

    public static boolean isMouseDown(@NonNull MouseButton button) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean isAnyMouseDown() {
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

    public static int getMouseClickedCount(@NonNull MouseButton button) {
        // TODO(ches) complete this
        return 0;
    }

    public static boolean isMouseReleased(@NonNull MouseButton button) {
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

    public static Vector2f getMousePos() {
        Vector2f value = new Vector2f();
        getMousePos(value);
        return value;
    }

    public static void getMousePos(Vector2f output) {
        // TODO(ches) complete this
    }

    public static float getMousePosX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getMousePosY() {
        // TODO(ches) complete this
        return 0;
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

    public static Vector2f getMouseDragDelta() {
        Vector2f value = new Vector2f();
        getMouseDragDelta(value);
        return value;
    }

    public static void getMouseDragDelta(Vector2f output) {
        // TODO(ches) complete this
    }

    public static float getMouseDragDeltaX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getMouseDragDeltaY() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getMouseDragDelta(@NonNull MouseButton button) {
        Vector2f value = new Vector2f();
        getMouseDragDelta(value, button);
        return value;
    }

    public static void getMouseDragDelta(Vector2f output, @NonNull MouseButton button) {
        // TODO(ches) complete this
    }

    public static float getMouseDragDeltaX(@NonNull MouseButton button) {
        // TODO(ches) complete this
        return 0;
    }

    public static float getMouseDragDeltaY(@NonNull MouseButton button) {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getMouseDragDelta(@NonNull MouseButton button, float lockThreshold) {
        Vector2f value = new Vector2f();
        getMouseDragDelta(value, button, lockThreshold);
        return value;
    }

    public static void getMouseDragDelta(
            Vector2f output, @NonNull MouseButton button, float lockThreshold) {
        // TODO(ches) complete this
    }

    public static float getMouseDragDeltaX(@NonNull MouseButton button, float lockThreshold) {
        // TODO(ches) complete this
        return 0;
    }

    public static float getMouseDragDeltaY(@NonNull MouseButton button, float lockThreshold) {
        // TODO(ches) complete this
        return 0;
    }

    public static void resetMouseDragDelta() {
        // TODO(ches) complete this
    }

    public static void resetMouseDragDelta(@NonNull MouseButton button) {
        // TODO(ches) complete this
    }

    public static int getMouseCursor() {
        // TODO(ches) complete this
        return 0;
    }

    public static void setMouseCursor(@NonNull MouseCursor cursor) {
        // TODO(ches) complete this
    }

    public static void captureMouseFromApp() {
        // TODO(ches) complete this
    }

    public static void captureMouseFromApp(boolean wantToCaptureNextFrame) {
        // TODO(ches) complete this
    }

    public static String getClipboardText() {
        // TODO(ches) complete this
        return null;
    }

    public static void setClipboardText(String text) {
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

    public static PlatformIO getPlatformIO() {
        // TODO(ches) complete this
        return null;
    }

    public static void updatePlatformWindows() {
        // TODO(ches) complete this
    }

    public static void renderPlatformWindowsDefault() {
        // TODO(ches) complete this
    }

    public static void destroyPlatformWindows() {
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
}
