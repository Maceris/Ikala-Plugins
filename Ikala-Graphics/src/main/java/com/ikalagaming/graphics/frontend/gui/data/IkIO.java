package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.flags.BackendFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ConfigFlags;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector2f;

@Getter
@Setter
public class IkIO {
    /**
     * @see BackendFlags
     */
    private int backendFlags;

    @NonNull private String backendPlatformName;
    @NonNull private String backendRendererName;
    private short backendUsingLegacyKeyArrays;
    private boolean backendUsingLegacyNavInputArray;

    /**
     * @see ConfigFlags
     */
    private int configFlags;

    private boolean configDebugBeginReturnValueLoop;
    private boolean configDebugBeginReturnValueOnce;
    private boolean configDebugIgnoreFocusLoss;
    private boolean configDebugIniSettings;
    private boolean configDebugIsDebuggerPresent;
    private boolean configDockingAlwaysTabBar;
    private boolean configDockingNoSplit;
    private boolean configDockingTransparentPayload;
    private boolean configDockingWithShift;
    private boolean configDragClickToInputText;
    private boolean configInputTextCursorBlink;
    private boolean configInputTextEnterKeepActive;
    private boolean configInputTrickleEventQueue;
    private boolean configMacOSXBehaviors;
    private boolean configMemoryCompactTimer;
    private boolean configViewportsNoAutoMerge;
    private boolean configViewportsNoDecoration;
    private boolean configViewportsNoDefaultParent;
    private boolean configViewportsNoTaskBarIcon;
    private boolean configWindowsMoveFromTitleBarOnly;
    private boolean configWindowsResizeFromEdges;
    @NonNull private Context context;
    private float deltaTime;
    private final Vector2f displayFramebufferScale;
    private final Vector2f displaySize;
    @NonNull private FontAtlas fonts;
    private boolean fontAllowUserScaling;
    @NonNull private Font fontDefault;
    private float fontGlobalScale;
    private float framerate;
    @NonNull private String iniFilename;
    private float iniSavingRate;
    private short inputQueueSurrogate;
    private boolean keyAlt;
    private boolean keyCtrl;
    private int keyMods;
    private float keyRepeatDelay;
    private float keyRepeatRate;
    private boolean keyShift;
    private boolean keySuper;
    @NonNull private String logFilename;
    private int metricsActiveWindows;
    private int metricsRenderIndices;
    private int metricsRenderVertices;
    private int metricsRenderWindows;
    private boolean mouseCtrlLeftAsRightClick;
    private float mouseDoubleClickMaxDist;
    private float mouseDoubleClickTime;
    private float mouseDragThreshold;
    private boolean mouseDrawCursor;
    private int mouseHoveredViewport;
    private float mouseWheel;
    private float mouseWheelH;
    private boolean mouseWheelRequestAxisSwap;
    private boolean navActive;
    private boolean navVisible;
    private float penPressure;
    private short platformLocaleDecimalPoint;
    private boolean wantCaptureKeyboard;
    private boolean wantCaptureMouse;
    private boolean wantCaptureMouseUnlessPopupClose;
    private boolean wantSaveIniSettings;
    private boolean wantSetMousePos;
    private boolean wantTextInput;

    public IkIO() {
        displaySize = new Vector2f(0, 0);
        configFlags = ConfigFlags.NONE;
        displayFramebufferScale = new Vector2f(0, 0);
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
        return new Vector2f(displaySize);
    }

    public void getDisplaySize(@NonNull Vector2f output) {
        output.set(displaySize);
    }

    public float getDisplaySizeX() {
        return displaySize.x;
    }

    public float getDisplaySizeY() {
        return displaySize.y;
    }

    public void setDisplaySize(@NonNull Vector2f value) {
        displaySize.set(value);
    }

    public void setDisplaySize(float valueX, float valueY) {
        displaySize.set(valueX, valueY);
    }

    public Vector2f getDisplayFramebufferScale() {
        return new Vector2f(displayFramebufferScale);
    }

    public float getDisplayFramebufferScaleX() {
        return displayFramebufferScale.x;
    }

    public float getDisplayFramebufferScaleY() {
        return displayFramebufferScale.y;
    }

    public void getDisplayFramebufferScale(@NonNull Vector2f dst) {
        dst.set(displayFramebufferScale);
    }

    public void setDisplayFramebufferScale(@NonNull Vector2f value) {
        displayFramebufferScale.set(value);
    }

    public void setDisplayFramebufferScale(float valueX, float valueY) {
        displayFramebufferScale.set(valueX, valueY);
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

    public Object[] getKeysData() {
        // TODO(ches) complete this, add some kind of KeyData struct
        return null;
    }

    public void setKeysData(Object[] value) {
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

    public boolean getAppFocusLost() {
        // TODO(ches) complete this
        return false;
    }

    public boolean getAppAcceptingEvents() {
        // TODO(ches) complete this
        return false;
    }
}
