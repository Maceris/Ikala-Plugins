package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.automation.Steps;
import com.ikalagaming.graphics.ui.automation.UiAutomationException;
import com.ikalagaming.graphics.ui.automation.UiTestDriver;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Automation runs that reach past retained nodes: mouse input at points in the window, for code
 * that reads IkGui's input itself, and IkGui items drawn in immediate mode, found by label.
 */
class UiAutomationInputTest {
    private Context context;
    private UiManager manager;
    private GraphicsContext owner;
    private UiTestDriver driver;

    /** Clicks seen by the probe, as "button x,y" with where the mouse was. */
    private final List<String> clicks = new ArrayList<>();

    /** Whether IkGui wanted the mouse for itself during any click the probe saw. */
    private boolean capturedClick;

    /** How far the mouse moved while the left button was dragging. */
    private final Vector2f dragged = new Vector2f();

    /** How long the left drag was when the button was released. */
    private float releasedDrag = -1;

    /** How far the mouse wheel turned. */
    private float wheel;

    /** How often Apply was pressed. */
    private int applied;

    /** How often Ping was pressed. */
    private int pinged;

    /** Which of the two Go buttons were pressed. */
    private final List<String> goes = new ArrayList<>();

    private final IkBoolean showBounds = new IkBoolean(false);
    private final float[] speed = {1};
    private final IkString name = new IkString(64);
    private int mode;
    private static final String[] MODES = {"Coarse", "Fine", "Exact"};

    /** The tab whose contents were drawn last. */
    private String openTab;

    private static final String[] TABS = {"Body", "Costume", "Colors"};

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        manager = new UiManager();
        owner = new GraphicsContext("Test-Plugin", null);
        driver = new UiTestDriver(manager, owner);
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    private void show(String id, Immediate area) {
        Surface surface =
                new Surface(id)
                        .anchors(Anchors.topLeft())
                        .content(
                                new Column("root")
                                        .width(Sizing.fixed(300))
                                        .height(Sizing.fixed(300))
                                        .add(area));
        manager.add(owner, surface);
        driver.frames(2);
    }

    /** Show a small surface whose immediate area watches the mouse, like a 3D view would. */
    private void showProbe() {
        show(
                "probe",
                new Immediate(
                        "watch",
                        frame -> {
                            IkIO io = IkGui.getIO();
                            for (MouseButton button :
                                    List.of(MouseButton.LEFT, MouseButton.RIGHT)) {
                                if (IkGui.isMouseClicked(button)) {
                                    clicks.add(
                                            button
                                                    + " "
                                                    + Math.round(IkGui.getMousePos().x)
                                                    + ","
                                                    + Math.round(IkGui.getMousePos().y));
                                    capturedClick |= io.wantCaptureMouse;
                                }
                            }
                            if (IkGui.isMouseDragging(MouseButton.LEFT, 0)) {
                                dragged.add(io.mouseDelta);
                            }
                            if (IkGui.isMouseReleased(MouseButton.LEFT)) {
                                releasedDrag =
                                        IkGui.getMouseDragDelta(MouseButton.LEFT, 0).length();
                            }
                            wheel += io.mouseWheel;
                        }));
    }

    /** Show a surface of immediate mode tools, like a material editor. */
    private void showTools() {
        show(
                "tools",
                new Immediate(
                        "controls",
                        frame -> {
                            if (IkGui.button("Apply")) {
                                ++applied;
                            }
                            IkGui.checkbox("Show bounds", showBounds);
                            if (IkGui.beginCombo("Mode", MODES[mode])) {
                                for (int i = 0; i < MODES.length; ++i) {
                                    if (IkGui.selectable(MODES[i], i == mode)) {
                                        mode = i;
                                    }
                                }
                                IkGui.endCombo();
                            }
                            IkGui.sliderFloat("Speed", speed, 0, 10);
                            IkGui.inputText("Name##field", name);
                            if (IkGui.button("Go##first")) {
                                goes.add("first");
                            }
                            if (IkGui.button("Go##second")) {
                                goes.add("second");
                            }
                            IkGui.setNextWindowPos(700, 100, Condition.ALWAYS);
                            if (IkGui.begin("Plain tools##plain")) {
                                if (IkGui.button("Ping")) {
                                    ++pinged;
                                }
                            }
                            IkGui.end();
                        }));
    }

    /** Show a surface with a tab bar, whose open tab is written to {@link #openTab}. */
    private void showTabs() {
        show(
                "tabs",
                new Immediate(
                        "bar",
                        frame -> {
                            if (IkGui.beginTabBar("##sections")) {
                                for (String tab : TABS) {
                                    if (IkGui.beginTabItem(tab)) {
                                        openTab = tab;
                                        IkGui.endTabItem();
                                    }
                                }
                                IkGui.endTabBar();
                            }
                        }));
    }

    private String failure(Consumer<Steps> build) {
        return assertThrows(UiAutomationException.class, () -> driver.run(build)).getMessage();
    }

    @Test
    void mouseClicksReachCodeReadingInputOutsideTheUi() {
        showProbe();
        driver.run(
                steps ->
                        steps.mouseClick(900, 500)
                                .mouseClick(910, 520, MouseButton.RIGHT)
                                .mouseDoubleClick(920, 540));
        assertEquals(
                List.of("LEFT 900,500", "RIGHT 910,520", "LEFT 920,540", "LEFT 920,540"), clicks);
        assertFalse(capturedClick, "IkGui shouldn't want a click outside its windows");
        assertTrue(releasedDrag >= 0 && releasedDrag < 1, "a click isn't a drag: " + releasedDrag);
    }

    @Test
    void mouseDragsMoveTheWholeWayWithTheButtonHeld() {
        showProbe();
        driver.run(steps -> steps.mouseDrag(800, 400, 900, 450));
        assertEquals(List.of("LEFT 800,400"), clicks);
        assertEquals(100, dragged.x, 0.01f);
        assertEquals(50, dragged.y, 0.01f);
        assertEquals(Math.hypot(100, 50), releasedDrag, 0.01f);
        assertFalse(IkGui.isMouseDown(MouseButton.LEFT));
    }

    @Test
    void mouseWheelTurnsAtThePoint() {
        showProbe();
        driver.run(steps -> steps.mouseWheel(900, 500, 2).mouseWheel(900, 500, -0.5f));
        assertEquals(1.5f, wheel, 0.001f);
        assertEquals(900, IkGui.getMousePos().x, 0.01f);
        assertEquals(500, IkGui.getMousePos().y, 0.01f);
    }

    @Test
    void immediateButtonsAndCheckBoxesAreClickedByLabel() {
        showTools();
        driver.run(
                steps ->
                        steps.clickItem("tools//controls", "Apply")
                                .assertItemChecked("tools", "Show bounds", false)
                                .clickItem("tools", "Show bounds")
                                .assertItemChecked("tools", "Show bounds", true));
        assertEquals(1, applied);
        assertTrue(showBounds.get());
    }

    @Test
    void tabsAreClickedByLabel() {
        showTabs();
        assertEquals("Body", openTab);
        driver.run(
                steps ->
                        steps.waitForItem("tabs", "Costume")
                                .clickItem("tabs", "Colors")
                                .clickItem("tabs//bar", "Costume"));
        driver.frames(1);
        assertEquals("Costume", openTab);
    }

    @Test
    void comboListsAreFoundInTheirPopup() {
        showTools();
        driver.run(steps -> steps.clickItem("tools", "Mode").clickItem("tools", "Fine"));
        assertEquals(1, mode);
    }

    @Test
    void slidersAndFieldsTakeTypedText() {
        showTools();
        driver.run(
                steps ->
                        steps.typeItem("tools", "Speed", "2.5", true)
                                .typeItem("tools", "##field", "Ada", true));
        assertEquals(2.5f, speed[0], 0.0001f);
        assertEquals("Ada", name.get());
    }

    @Test
    void labelsMatchTheirIdPartAndPlainWindowsByName() {
        showTools();
        driver.run(
                steps ->
                        steps.clickItem("tools", "##second")
                                .clickItem("tools", "Go##first")
                                .clickItem("window=Plain tools", "Ping")
                                .hoverItem("window=Plain tools##plain", "Ping"));
        assertEquals(List.of("second", "first"), goes);
        assertEquals(1, pinged);
    }

    @Test
    void itemMissesExplainWhatIsThere() {
        showTools();
        String ambiguous = failure(steps -> steps.clickItem("tools", "Go"));
        assertTrue(ambiguous.contains("'tools, Go' is ambiguous"), ambiguous);

        String missing = failure(steps -> steps.timeout(0.2).clickItem("tools", "Aplpy"));
        assertTrue(missing.startsWith("clickItem(tools, Aplpy): timed out"), missing);
        assertTrue(missing.contains("labels: Apply, "), missing);

        // The plain window is outside the surface's rectangle
        String outside = failure(steps -> steps.timeout(0.2).clickItem("tools", "Ping"));
        assertTrue(outside.contains("is labelled 'Ping'"), outside);

        List<Boolean> found = new ArrayList<>();
        driver.run(
                steps ->
                        steps.itemExists("*", "Ping", found::add)
                                .itemExists("tools", "Nothing", found::add));
        assertEquals(List.of(true, false), found);
        assertEquals(0, applied);
    }
}
