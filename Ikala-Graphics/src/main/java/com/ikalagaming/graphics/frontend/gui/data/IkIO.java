package com.ikalagaming.graphics.frontend.gui.data;

import org.joml.Vector2f;

public class IkIO {
    // TODO(ches) IO state

    public int getConfigFlags() {
        // TODO(ches) complete this
        return 0;
    }

    public void setConfigFlags(int value) {
        // TODO(ches) complete this
    }

    public void addConfigFlags(int flags) {
        this.setConfigFlags(this.getConfigFlags() | flags);
    }

    public void removeConfigFlags(int flags) {
        this.setConfigFlags(this.getConfigFlags() & ~flags);
    }

    public boolean hasConfigFlags(int flags) {
        return (this.getConfigFlags() & flags) != 0;
    }

    public int getBackendFlags() {
        // TODO(ches) complete this
        return 0;
    }

    public void setBackendFlags(int value) {
        // TODO(ches) complete this
    }

    public void addBackendFlags(int flags) {
        this.setBackendFlags(this.getBackendFlags() | flags);
    }

    public void removeBackendFlags(int flags) {
        this.setBackendFlags(this.getBackendFlags() & ~flags);
    }

    public boolean hasBackendFlags(int flags) {
        return (this.getBackendFlags() & flags) != 0;
    }

    public Vector2f getDisplaySize() {
        final Vector2f value = new Vector2f();
        getDisplaySize(value);
        return value;
    }

    public void getDisplaySize(Vector2f output) {
        // TODO(ches) complete this
    }

    public float getDisplaySizeX() {
        // TODO(ches) complete this
        return 0;
    }

    public float getDisplaySizeY() {
        // TODO(ches) complete this
        return 0;
    }

    public void setDisplaySize(Vector2f value) {
        // TODO(ches) complete this
    }

    public void setDisplaySize(float valueX, float valueY) {
        // TODO(ches) complete this
    }

    public float getDeltaTime() {
        // TODO(ches) complete this
        return 0;
    }

    public void setDeltaTime(float value) {
        // TODO(ches) complete this
    }

    public float getIniSavingRate() {
        // TODO(ches) complete this
        return 0;
    }

    public void setIniSavingRate(float value) {
        // TODO(ches) complete this
    }

    public String getIniFilename() {
        // TODO(ches) complete this
        return "";
    }

    public void setIniFilename(String value) {
        // TODO(ches) complete this
    }

    public String getLogFilename() {
        // TODO(ches) complete this
        return "";
    }

    public void setLogFilename(String value) {
        // TODO(ches) complete this
    }

    public FontAtlas getFonts() {
        // TODO(ches) complete this
        return null;
    }

    public void setFonts(FontAtlas value) {
        // TODO(ches) complete this
    }

    public float getFontGlobalScale() {
        // TODO(ches) complete this
        return 0;
    }

    public void setFontGlobalScale(float value) {
        // TODO(ches) complete this
    }

    public boolean getFontAllowUserScaling() {
        // TODO(ches) complete this
        return false;
    }

    public void setFontAllowUserScaling(boolean value) {
        // TODO(ches) complete this
    }

    public Font getFontDefault() {
        // TODO(ches) complete this
        return null;
    }

    public void setFontDefault(Font value) {
        // TODO(ches) complete this
    }

    public Vector2f getDisplayFramebufferScale() {
        Vector2f dst = new Vector2f();

        // TODO(ches) complete this
        return dst;
    }

    public float getDisplayFramebufferScaleX() {
        // TODO(ches) complete this
        return 0;
    }

    public float getDisplayFramebufferScaleY() {
        // TODO(ches) complete this
        return 0;
    }

    public void getDisplayFramebufferScale(Vector2f dst) {
        // TODO(ches) complete this
    }

    public void setDisplayFramebufferScale(Vector2f value) {
        // TODO(ches) complete this
    }

    public void setDisplayFramebufferScale(float valueX, float valueY) {
        // TODO(ches) complete this
    }

    public boolean getConfigDockingNoSplit() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigDockingNoSplit(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigDockingWithShift() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigDockingWithShift(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigDockingAlwaysTabBar() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigDockingAlwaysTabBar(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigDockingTransparentPayload() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigDockingTransparentPayload(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigViewportsNoAutoMerge() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigViewportsNoAutoMerge(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigViewportsNoTaskBarIcon() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigViewportsNoTaskBarIcon(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigViewportsNoDecoration() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigViewportsNoDecoration(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigViewportsNoDefaultParent() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigViewportsNoDefaultParent(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getMouseDrawCursor() {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseDrawCursor(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigMacOSXBehaviors() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigMacOSXBehaviors(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigInputTrickleEventQueue() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigInputTrickleEventQueue(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigInputTextCursorBlink() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigInputTextCursorBlink(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigInputTextEnterKeepActive() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigInputTextEnterKeepActive(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigDragClickToInputText() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigDragClickToInputText(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigWindowsResizeFromEdges() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigWindowsResizeFromEdges(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigWindowsMoveFromTitleBarOnly() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigWindowsMoveFromTitleBarOnly(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigMemoryCompactTimer() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigMemoryCompactTimer(boolean value) {
        // TODO(ches) complete this
    }

    public float getMouseDoubleClickTime() {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseDoubleClickTime(float value) {
        // TODO(ches) complete this
    }

    public float getMouseDoubleClickMaxDist() {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseDoubleClickMaxDist(float value) {
        // TODO(ches) complete this
    }

    public float getMouseDragThreshold() {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseDragThreshold(float value) {
        // TODO(ches) complete this
    }

    public float getKeyRepeatDelay() {
        // TODO(ches) complete this
        return 0;
    }

    public void setKeyRepeatDelay(float value) {
        // TODO(ches) complete this
    }

    public float getKeyRepeatRate() {
        // TODO(ches) complete this
        return 0;
    }

    public void setKeyRepeatRate(float value) {
        // TODO(ches) complete this
    }

    public boolean getConfigDebugIsDebuggerPresent() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigDebugIsDebuggerPresent(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigDebugBeginReturnValueOnce() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigDebugBeginReturnValueOnce(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigDebugBeginReturnValueLoop() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigDebugBeginReturnValueLoop(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigDebugIgnoreFocusLoss() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigDebugIgnoreFocusLoss(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getConfigDebugIniSettings() {
        // TODO(ches) complete this
        return false;
    }

    public void setConfigDebugIniSettings(boolean value) {
        // TODO(ches) complete this
    }

    public String getBackendPlatformName() {
        // TODO(ches) complete this
        return "";
    }

    public void setBackendPlatformName(String value) {
        // TODO(ches) complete this
    }

    public String getBackendRendererName() {
        // TODO(ches) complete this
        return "";
    }

    public void setBackendRendererName(String value) {
        // TODO(ches) complete this
    }

    public short getPlatformLocaleDecimalPoint() {
        // TODO(ches) complete this
        return 0;
    }

    public void setPlatformLocaleDecimalPoint(short value) {
        // TODO(ches) complete this
    }

    public void addKeyEvent(int key, boolean down) {
        // TODO(ches) complete this
    }

    public void addKeyAnalogEvent(int key, boolean down, float v) {
        // TODO(ches) complete this
    }

    public void addMousePosEvent(float x, float y) {
        // TODO(ches) complete this
    }

    public void addMouseButtonEvent(int button, boolean down) {
        // TODO(ches) complete this
    }

    public void addMouseWheelEvent(float whX, float whY) {
        // TODO(ches) complete this
    }

    public void addMouseSourceEvent(int source) {
        // TODO(ches) complete this
    }

    public void addMouseViewportEvent(int id) {
        // TODO(ches) complete this
    }

    public void addFocusEvent(boolean focused) {
        // TODO(ches) complete this
    }

    public void addInputCharacter(int c) {
        // TODO(ches) complete this
    }

    public void addInputCharacterUTF16(short c) {
        // TODO(ches) complete this
    }

    public void addInputCharactersUTF8(String str) {
        // TODO(ches) complete this
    }

    public void setAppAcceptingEvents(boolean acceptingEvents) {
        // TODO(ches) complete this
    }

    public void clearEventsQueue() {
        // TODO(ches) complete this
    }

    public void clearInputKeys() {
        // TODO(ches) complete this
    }

    public void clearInputMouse() {
        // TODO(ches) complete this
    }

    public boolean getWantCaptureMouse() {
        // TODO(ches) complete this
        return false;
    }

    public void setWantCaptureMouse(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getWantCaptureKeyboard() {
        // TODO(ches) complete this
        return false;
    }

    public void setWantCaptureKeyboard(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getWantTextInput() {
        // TODO(ches) complete this
        return false;
    }

    public void setWantTextInput(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getWantSetMousePos() {
        // TODO(ches) complete this
        return false;
    }

    public void setWantSetMousePos(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getWantSaveIniSettings() {
        // TODO(ches) complete this
        return false;
    }

    public void setWantSaveIniSettings(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getNavActive() {
        // TODO(ches) complete this
        return false;
    }

    public void setNavActive(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getNavVisible() {
        // TODO(ches) complete this
        return false;
    }

    public void setNavVisible(boolean value) {
        // TODO(ches) complete this
    }

    public float getFramerate() {
        // TODO(ches) complete this
        return 0;
    }

    public void setFramerate(float value) {
        // TODO(ches) complete this
    }

    public int getMetricsRenderVertices() {
        // TODO(ches) complete this
        return 0;
    }

    public void setMetricsRenderVertices(int value) {
        // TODO(ches) complete this
    }

    public int getMetricsRenderIndices() {
        // TODO(ches) complete this
        return 0;
    }

    public void setMetricsRenderIndices(int value) {
        // TODO(ches) complete this
    }

    public int getMetricsRenderWindows() {
        // TODO(ches) complete this
        return 0;
    }

    public void setMetricsRenderWindows(int value) {
        // TODO(ches) complete this
    }

    public int getMetricsActiveWindows() {
        // TODO(ches) complete this
        return 0;
    }

    public void setMetricsActiveWindows(int value) {
        // TODO(ches) complete this
    }

    public Vector2f getMouseDelta() {
        Vector2f dst = new Vector2f();
        // TODO(ches) complete this

        return dst;
    }

    public float getMouseDeltaX() {
        // TODO(ches) complete this
        return 0;
    }

    public float getMouseDeltaY() {
        // TODO(ches) complete this
        return 0;
    }

    public void getMouseDelta(Vector2f dst) {
        // TODO(ches) complete this
    }

    public void setMouseDelta(Vector2f value) {
        // TODO(ches) complete this
    }

    public void setMouseDelta(float valueX, float valueY) {
        // TODO(ches) complete this
    }

    public Context getCtx() {
        // TODO(ches) complete this
        return null;
    }

    public void setCtx(Context value) {
        // TODO(ches) complete this
    }

    public Vector2f getMousePos() {
        Vector2f dst = new Vector2f();
        // TODO(ches) complete this

        return dst;
    }

    public float getMousePosX() {
        // TODO(ches) complete this
        return 0;
    }

    public float getMousePosY() {
        // TODO(ches) complete this
        return 0;
    }

    public void getMousePos(Vector2f dst) {
        // TODO(ches) complete this
    }

    public void setMousePos(Vector2f value) {
        // TODO(ches) complete this
    }

    public void setMousePos(float valueX, float valueY) {
        // TODO(ches) complete this
    }

    public boolean[] getMouseDown() {
        // TODO(ches) complete this
        return null;
    }

    public boolean getMouseDown(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseDown(boolean[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDown(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public float getMouseWheel() {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseWheel(float value) {
        // TODO(ches) complete this
    }

    public float getMouseWheelH() {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseWheelH(float value) {
        // TODO(ches) complete this
    }

    public int getMouseHoveredViewport() {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseHoveredViewport(int value) {
        // TODO(ches) complete this
    }

    public boolean getKeyCtrl() {
        // TODO(ches) complete this
        return false;
    }

    public void setKeyCtrl(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getKeyShift() {
        // TODO(ches) complete this
        return false;
    }

    public void setKeyShift(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getKeyAlt() {
        // TODO(ches) complete this
        return false;
    }

    public void setKeyAlt(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getKeySuper() {
        // TODO(ches) complete this
        return false;
    }

    public void setKeySuper(boolean value) {
        // TODO(ches) complete this
    }

    public int getKeyMods() {
        // TODO(ches) complete this
        return 0;
    }

    public void setKeyMods(int value) {
        // TODO(ches) complete this
    }

    public Object[] getKeysData() {
        // TODO(ches) complete this, add some kind of KeyData struct
        return null;
    }

    public void setKeysData(Object[] value) {
        // TODO(ches) complete this
    }

    public boolean getWantCaptureMouseUnlessPopupClose() {
        // TODO(ches) complete this
        return false;
    }

    public void setWantCaptureMouseUnlessPopupClose(boolean value) {
        // TODO(ches) complete this
    }

    public Vector2f getMousePosPrev() {
        Vector2f dst = new Vector2f();
        // TODO(ches) complete this

        return dst;
    }

    public float getMousePosPrevX() {
        // TODO(ches) complete this
        return 0;
    }

    public float getMousePosPrevY() {
        // TODO(ches) complete this
        return 0;
    }

    public void getMousePosPrev(Vector2f dst) {
        // TODO(ches) complete this
    }

    public void setMousePosPrev(Vector2f value) {
        // TODO(ches) complete this
    }

    public void setMousePosPrev(float valueX, float valueY) {
        // TODO(ches) complete this
    }

    public Vector2f[] getMouseClickedPos() {
        // TODO(ches) complete this
        return null;
    }

    public void setMouseClickedPos(Vector2f[] value) {
        // TODO(ches) complete this
    }

    public double[] getMouseClickedTime() {
        // TODO(ches) complete this
        return null;
    }

    public double getMouseClickedTime(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseClickedTime(double[] value) {
        // TODO(ches) complete this
    }

    public void setMouseClickedTime(int idx, double value) {
        // TODO(ches) complete this
    }

    public boolean[] getMouseClicked() {
        // TODO(ches) complete this
        return null;
    }

    public boolean getMouseClicked(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseClicked(boolean[] value) {
        // TODO(ches) complete this
    }

    public void setMouseClicked(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public boolean[] getMouseDoubleClicked() {
        // TODO(ches) complete this
        return null;
    }

    public boolean getMouseDoubleClicked(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseDoubleClicked(boolean[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDoubleClicked(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public int[] getMouseClickedCount() {
        // TODO(ches) complete this
        return null;
    }

    public int getMouseClickedCount(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseClickedCount(int[] value) {
        // TODO(ches) complete this
    }

    public void setMouseClickedCount(int idx, int value) {
        // TODO(ches) complete this
    }

    public int[] getMouseClickedLastCount() {
        // TODO(ches) complete this
        return null;
    }

    public int getMouseClickedLastCount(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseClickedLastCount(int[] value) {
        // TODO(ches) complete this
    }

    public void setMouseClickedLastCount(int idx, int value) {
        // TODO(ches) complete this
    }

    public boolean[] getMouseReleased() {
        // TODO(ches) complete this
        return null;
    }

    public boolean getMouseReleased(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseReleased(boolean[] value) {
        // TODO(ches) complete this
    }

    public void setMouseReleased(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public boolean[] getMouseDownOwned() {
        // TODO(ches) complete this
        return null;
    }

    public boolean getMouseDownOwned(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseDownOwned(boolean[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDownOwned(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public boolean[] getMouseDownOwnedUnlessPopupClose() {
        // TODO(ches) complete this
        return null;
    }

    public boolean getMouseDownOwnedUnlessPopupClose(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseDownOwnedUnlessPopupClose(boolean[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDownOwnedUnlessPopupClose(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public boolean getMouseWheelRequestAxisSwap() {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseWheelRequestAxisSwap(boolean value) {
        // TODO(ches) complete this
    }

    public boolean getMouseCtrlLeftAsRightClick() {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseCtrlLeftAsRightClick(boolean value) {
        // TODO(ches) complete this
    }

    public float[] getMouseDownDuration() {
        // TODO(ches) complete this
        return null;
    }

    public float getMouseDownDuration(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseDownDuration(float[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDownDuration(int idx, float value) {
        // TODO(ches) complete this
    }

    public float[] getMouseDownDurationPrev() {
        // TODO(ches) complete this
        return null;
    }

    public float getMouseDownDurationPrev(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseDownDurationPrev(float[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDownDurationPrev(int idx, float value) {
        // TODO(ches) complete this
    }

    public Vector2f[] getMouseDragMaxDistanceAbs() {
        // TODO(ches) complete this
        return null;
    }

    public void setMouseDragMaxDistanceAbs(Vector2f[] value) {
        // TODO(ches) complete this
    }

    public float[] getMouseDragMaxDistanceSqr() {
        // TODO(ches) complete this
        return null;
    }

    public float getMouseDragMaxDistanceSqr(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseDragMaxDistanceSqr(float[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDragMaxDistanceSqr(int idx, float value) {
        // TODO(ches) complete this
    }

    public float getPenPressure() {
        // TODO(ches) complete this
        return 0;
    }

    public void setPenPressure(float value) {
        // TODO(ches) complete this
    }

    public boolean getAppFocusLost() {
        // TODO(ches) complete this
        return false;
    }

    public boolean getAppAcceptingEvents() {
        // TODO(ches) complete this
        return false;
    }

    public short getBackendUsingLegacyKeyArrays() {
        // TODO(ches) complete this
        return 0;
    }

    public void setBackendUsingLegacyKeyArrays(short value) {
        // TODO(ches) complete this
    }

    public boolean getBackendUsingLegacyNavInputArray() {
        // TODO(ches) complete this
        return false;
    }

    public void setBackendUsingLegacyNavInputArray(boolean value) {
        // TODO(ches) complete this
    }

    public short getInputQueueSurrogate() {
        // TODO(ches) complete this
        return 0;
    }

    public void setInputQueueSurrogate(short value) {
        // TODO(ches) complete this
    }
}
