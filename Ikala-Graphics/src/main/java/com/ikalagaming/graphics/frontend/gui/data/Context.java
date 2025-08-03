package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.GuiInputSource;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.event.GuiInputEvent;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Map;

public class Context {
    // TODO(ches) context
    private boolean initialized;
    private IkIO io;
    private PlatformIO platformIO;
    private Style style;

    private ArrayList<FontAtlas> fontAtlases;
    private Font font;
    private FontBaked fontBaked;
    private float fontSize;
    private float fontSizeBase;
    private float fontDensity;
    private float dpiScale;
    private DrawListSharedData drawListSharedData;
    private double time;
    private int frameCount;
    private int frameCountEnded;
    private int frameCountRendered;
    private boolean insideFrame;
    private ArrayList<GuiInputEvent> inputEventQueue;

    /**
     * Past input events that get processed when we start a new frame, mostly for mouse/pen trails.
     */
    private ArrayList<GuiInputEvent> inputEventTrail;

    /** Windows, sorted in display order, back to front. */
    private ArrayList<Window> windowsDisplayOrder;

    private ArrayList<Window> windowFocusOrder;
    private Map<Integer, Window> windowByID;
    private int windowActiveCount;
    private Window windowCurrent;
    private Window windowHovered;
    private Window windowHoveredUnderMovingWindow;
    private Window windowMoving;
    private Window windowWheeling;
    private Vector2f windowWheelingRefMousePos;
    private int windowWheelingStartFrame;
    private int windowWheelingScrolledFrame;
    private float windowWheelingReleaseTimer;
    private Vector2f windowWheelingWheelRemainder;
    private Vector2f windowWheelingAxisAverage;

    private int hoveredID;
    private int hoveredIDPreviousFrame;
    private int hoveredIDPreviousFrameItemCount;
    private float hoveredIDTimer;
    private float hoveredIDInactiveTimer;
    private boolean hoveredIDAllowOverlap;
    private boolean hoveredIDDisabled;
    private int activeID;
    private int activeIDSeenThisFrame;
    private float activeIDTimer;
    private boolean activeIDActivatedThisFrame;
    private boolean activeIDAllowOverlap;
    private boolean activeIDRetainOnFocusLoss;
    private boolean activeIDHasBeenPressedBefore;
    private boolean activeIDHasBeenEditedBefore;
    private boolean activeIDHasBeenEditedThisFrame;
    private boolean activeIDFromShortcut;
    private MouseButton activeIDMouseButton;
    private Vector2f activeIDClickOffset;
    private Window activeIDWindow;
    private GuiInputSource activeIDSource;
    private int activeIDPreviousFrame;
    private DeactivatedItemData deactivatedItemData;
    private int lastActiveID;
    private float lastActiveIDTimer;

    private double lastKeyModsChangeTime;
    private double lastKeyModsChangeFromNoneTime;
    private double lastKeyboardKeyPressTime;
}
