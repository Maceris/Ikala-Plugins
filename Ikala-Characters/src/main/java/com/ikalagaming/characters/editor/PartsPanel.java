package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.view.PartCategories;
import com.ikalagaming.characters.view.PartSlots;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.SelectableFlags;
import com.ikalagaming.graphics.scene.Camera;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.ui.UiFrame;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The character editor's parts panel: a Body tab choosing the body's variant, with its height, a
 * ruler beside the character and the Save button, a Costume tab with the slot categories and their
 * dropdowns, and the Content tab, which {@link ContentPanel} draws. The editor keeps one for as
 * long as it exists; the design shown is passed to each method as an {@link EditorSession} rather
 * than kept, so it can't be worked on after the session closes. What is kept here is which tab
 * shows, which stays between designs and across closing and opening the editor again, as it did on
 * the editor. Saving, switching variants, the designs and the settings stay the editor's, passed
 * in. Render thread only, like the editor's UI.
 */
final class PartsPanel {
    /** What an empty slot's dropdown says. */
    private static final String EMPTY_LABEL = "none";

    /** The Costume tab's checkbox for putting pairs, like both claws, on together. */
    private static final String BOTH_SIDES_LABEL = "Both sides##both-sides";

    /** Where the part panel's dropdowns start, after the slot names, in pixels. */
    private static final int NAME_COLUMN = 64;

    /** The room IkGui leaves between items on a row, in pixels, give or take. */
    private static final float ROW_SPACING = 12;

    /** How tall a variant's card is, in rows of text. */
    private static final float CARD_ROWS = 1.6f;

    /** How many variant cards go on a row. */
    private static final int CARDS_PER_ROW = 3;

    /** How far to the side of the character the ruler stands, in meters. */
    private static final double RULER_OFFSET = 0.8;

    /** How tall the ruler is, in meters. */
    private static final float RULER_HEIGHT = 2.5f;

    /** The distance between the ruler's small marks, in meters. */
    private static final float RULER_STEP = 0.25f;

    /** How long the ruler's marks are, in pixels; every other one is twice as long. */
    private static final float RULER_MARK = 6;

    /** The ruler's color. */
    private static final int RULER_COLOR =
            com.ikalagaming.graphics.gui.util.Color.rgba(1.0f, 1.0f, 1.0f, 0.8f);

    /** The color of the line at the character's height. */
    private static final int HEIGHT_COLOR =
            com.ikalagaming.graphics.gui.util.Color.rgba(1.0f, 0.75f, 0.2f, 0.95f);

    /** Meters per inch, for showing heights in feet and inches too. */
    private static final double METERS_PER_INCH = 0.0254;

    /** Room left beside the save name for its button, in pixels. */
    private static final float SAVE_BUTTON_WIDTH = 60;

    /** Keeps the shown character in step with the look. */
    private final ShownSync sync;

    /** The clip bar, which plays the clip again when the Body tab's rest stance changes. */
    private final MotionControls controls;

    /** Turns the camera, to frame the open category. */
    private final OrbitCamera orbit;

    /** The Content tab, which picks the content folders. */
    private final ContentPanel content;

    /** The editor's settings while open. */
    private final Supplier<CharacterEditor.EditorSettings> settings;

    /** Reads the settings again after the Content tab changed the folders, and shows a design. */
    private final Runnable contentChanged;

    /** The designs to choose from, in ID order. */
    private final Supplier<List<Design>> designs;

    /** Makes the character another variant, by the variant's position in the plan. */
    private final IntConsumer switchVariant;

    /** The name to save the design as, which the editor sets when a design loads. */
    private final IkString saveName;

    /** Saves the design under the name typed. */
    private final Runnable save;

    /** What the last save did, shown under the name. */
    private final Supplier<String> saveStatus;

    /** Whether the Content tab is showing. */
    private boolean contentTab;

    /** Whether the Body tab is showing, rather than the Costume tab. */
    private boolean bodyTab = true;

    /**
     * Set up the panel; nothing is drawn until the editor's parts node draws it.
     *
     * @param sync Keeps the shown character in step with the look.
     * @param controls The clip bar, which plays the clip again when the tab changes.
     * @param orbit Turns the camera, to frame the open category.
     * @param content The Content tab.
     * @param settings The editor's settings while open.
     * @param contentChanged Reads the settings again after the folders changed, and shows a design.
     * @param designs The designs to choose from.
     * @param switchVariant Makes the character another variant, by its position in the plan.
     * @param saveName The name to save the design as.
     * @param save Saves the design under the name typed.
     * @param saveStatus What the last save did.
     */
    PartsPanel(
            ShownSync sync,
            MotionControls controls,
            OrbitCamera orbit,
            ContentPanel content,
            Supplier<CharacterEditor.EditorSettings> settings,
            Runnable contentChanged,
            Supplier<List<Design>> designs,
            IntConsumer switchVariant,
            IkString saveName,
            Runnable save,
            Supplier<String> saveStatus) {
        this.sync = sync;
        this.controls = controls;
        this.orbit = orbit;
        this.content = content;
        this.settings = settings;
        this.contentChanged = contentChanged;
        this.designs = designs;
        this.switchVariant = switchVariant;
        this.saveName = saveName;
        this.save = save;
        this.saveStatus = saveStatus;
    }

    /**
     * Whether the Body tab is showing, rather than the Costume tab.
     *
     * @return True on the Body tab.
     */
    boolean bodyTab() {
        return bodyTab;
    }

    /**
     * Leave the Body tab's rest stance, as scripts do; with the GUI drawn, the tab shown decides
     * again on the next frame.
     */
    void leaveBodyTab() {
        bodyTab = false;
    }

    /**
     * Draw the character panel: a Body tab choosing the body's variant, with its height and a ruler
     * beside it, and a Costume tab with the parts.
     *
     * @param frame The UI frame.
     * @param session The design shown, or null until one loads.
     */
    void draw(@NonNull UiFrame frame, EditorSession session) {
        boolean ready = session != null;
        boolean body = bodyTab;
        boolean onContent = false;
        if (IkGui.beginTabBar("##character-tabs")) {
            if (IkGui.beginTabItem("Body")) {
                body = true;
                if (ready) {
                    drawBody(session);
                } else {
                    drawWaiting();
                }
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Costume")) {
                body = false;
                if (ready) {
                    drawCostume(session);
                } else {
                    drawWaiting();
                }
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Content")) {
                onContent = true;
                if (content.draw(settings.get())) {
                    frame.fire(contentChanged);
                }
                IkGui.endTabItem();
            }
            IkGui.endTabBar();
        }
        contentTab = onContent;
        if (!ready) {
            return;
        }
        if (body != bodyTab) {
            bodyTab = body;
            frameOpenCategory(session);
            // The Body tab stands the character at rest, so the ruler reads its height true
            controls.playClip(session, session.clipIndex);
        }
        if (bodyTab || contentTab) {
            sync.showWorn(session, CharacterLook.NO_PREVIEW, 0);
        }
        if (bodyTab && !contentTab) {
            drawRuler();
        }
    }

    /** Draw what the Body and Costume tabs show while there is no character: why not. */
    private void drawWaiting() {
        IkGui.textWrapped(
                designs.get().isEmpty()
                        ? "No designs are loaded; add a content folder with some on the Content tab"
                        : "Loading...");
    }

    /**
     * Draw the Body tab: a card for each variant of the body plan, and the character's height.
     *
     * @param session The design shown.
     */
    private void drawBody(EditorSession session) {
        List<BodyPlan.Variant> variants = session.look.variants();
        if (variants.isEmpty()) {
            IkGui.textDisabled("The rig has no body plan with variants");
        }
        float spacing = IkGui.getStyle().variable.itemSpacing.x;
        float cardWidth =
                (IkGui.getContentRegionAvailableX() - (CARDS_PER_ROW - 1) * spacing)
                        / CARDS_PER_ROW;
        for (int v = 0; v < variants.size(); ++v) {
            if (v % CARDS_PER_ROW != 0) {
                IkGui.sameLine();
            }
            String name = variants.get(v).id();
            String label = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
            if (IkGui.selectable(
                    label + "##variant" + v,
                    session.look.variant() == v,
                    SelectableFlags.NONE,
                    cardWidth,
                    IkGui.getFrameHeight() * CARD_ROWS)) {
                switchVariant.accept(v);
            }
        }
        IkGui.spacing();
        double inches = sync.characterHeight() / METERS_PER_INCH;
        long feet = (long) Math.floor(inches / 12);
        long rest = Math.round(inches - feet * 12);
        if (rest == 12) {
            ++feet;
            rest = 0;
        }
        IkGui.textUnformatted(
                String.format(
                        Locale.ROOT,
                        "Height: %.2f m (%d' %d\")",
                        sync.characterHeight(),
                        feet,
                        rest));

        IkGui.separatorText("Save");
        IkGui.setNextItemWidth(IkGui.getContentRegionAvailableX() - SAVE_BUTTON_WIDTH);
        IkGui.inputText("##save-name", saveName);
        IkGui.sameLine();
        if (IkGui.button("Save")) {
            save.run();
        }
        String status = saveStatus.get();
        if (!status.isEmpty()) {
            IkGui.textWrapped(status);
        }
    }

    /**
     * Draw the Costume tab: whether pairs go on together, then the slot categories, one open at a
     * time, with a dropdown and arrows for each slot that has a choice. The dropdown previews the
     * hovered choice on the character. Slots with nothing available for this body, and categories
     * of only such slots, aren't shown.
     *
     * @param session The design shown.
     */
    private void drawCostume(EditorSession session) {
        int previewSlot = CharacterLook.NO_PREVIEW;
        int previewChoice = 0;
        if (IkGui.checkbox(BOTH_SIDES_LABEL, session.look.bothSides())) {
            session.look.setBothSides(!session.look.bothSides());
        }
        List<PartCategories.Category> list = session.categories.categories();
        for (int c = 0; c < list.size(); ++c) {
            PartCategories.Category category = list.get(c);
            if (category.slots().stream().noneMatch(slot -> isShown(session, slot))) {
                continue;
            }
            IkGui.setNextItemOpen(session.openCategory == c, Condition.ALWAYS);
            boolean open = IkGui.collapsingHeader(category.label() + "##" + category.id());
            if (open != (session.openCategory == c)) {
                session.openCategory = open ? c : -1;
                frameOpenCategory(session);
            }
            if (!open) {
                continue;
            }
            for (int slot : category.slots()) {
                if (!isShown(session, slot)) {
                    continue;
                }
                IkGui.pushID(slot);
                int hovered = drawSlot(session, slot);
                if (hovered != Integer.MIN_VALUE) {
                    previewSlot = slot;
                    previewChoice = hovered;
                }
                IkGui.popID();
            }
        }
        sync.showWorn(session, previewSlot, previewChoice);
    }

    /**
     * Whether the Costume tab shows a slot: it holds something, or offers something for this body.
     *
     * @param session The design shown.
     * @param slot The slot's position.
     * @return True if it is shown.
     */
    private static boolean isShown(EditorSession session, int slot) {
        return session.look.choice(slot) != CharacterLook.EMPTY
                || !session.look.available(slot).isEmpty();
    }

    /**
     * Draw one slot: its name, then what fills it, with arrows around a dropdown for slots with a
     * choice. A slot covered by another's part, or hidden by a worn part, says so instead.
     *
     * @param session The design shown.
     * @param slot The slot's position.
     * @return The choice hovered in its open dropdown, or {@code Integer.MIN_VALUE} for none.
     */
    private static int drawSlot(EditorSession session, int slot) {
        PartSlots.Slot info = session.look.slots().slots().get(slot);
        int choice = session.look.choice(slot);
        String current = choice == CharacterLook.EMPTY ? EMPTY_LABEL : info.labels().get(choice);
        // The slot's name in a column of its own, so the rows line up
        IkGui.alignTextToFramePadding();
        IkGui.textUnformatted(info.id());
        IkGui.sameLine(NAME_COLUMN);
        int cover = session.look.coveredBy(slot);
        if (cover >= 0) {
            PartSlots.Slot covering = session.look.slots().slots().get(cover);
            IkGui.textDisabled("part of " + covering.labels().get(session.look.choice(cover)));
            return Integer.MIN_VALUE;
        }
        Part hiding = session.look.hiddenBy(slot);
        if (hiding != null) {
            IkGui.textDisabled(
                    (choice == CharacterLook.EMPTY ? "" : current + ", ")
                            + "hidden by "
                            + PreparedDesign.label(hiding.id()));
            return Integer.MIN_VALUE;
        }
        if (session.look.isFixed(slot)) {
            IkGui.textUnformatted(current);
            return Integer.MIN_VALUE;
        }
        int hovered = Integer.MIN_VALUE;
        if (IkGui.button("<")) {
            session.look.step(slot, -1);
        }
        IkGui.sameLine();
        float arrow = IkGui.getFrameHeight();
        IkGui.setNextItemWidth(
                Math.max(arrow, IkGui.getContentRegionAvailableX() - arrow - ROW_SPACING));
        // Named after the slot, so tests and scripts can find each dropdown
        if (IkGui.beginCombo("##" + info.id(), current)) {
            List<Integer> shown = new ArrayList<>();
            if (info.optional()) {
                shown.add(CharacterLook.EMPTY);
            }
            shown.addAll(session.look.available(slot));
            for (int option : shown) {
                String text =
                        option == CharacterLook.EMPTY ? EMPTY_LABEL : info.labels().get(option);
                boolean selected = option == choice;
                if (IkGui.selectable(text + "##" + option, selected)) {
                    session.look.choose(slot, option);
                }
                if (selected) {
                    IkGui.setItemDefaultFocus();
                }
                if (IkGui.isItemHovered()) {
                    hovered = option;
                }
            }
            IkGui.endCombo();
        }
        IkGui.sameLine();
        if (IkGui.button(">")) {
            session.look.step(slot, 1);
        }
        return hovered;
    }

    /**
     * Glide the camera to frame the open category, or the whole character if none is open.
     *
     * @param session The design shown, or null, which frames nothing.
     */
    void frameOpenCategory(EditorSession session) {
        if (session == null) {
            return;
        }
        Vector3f min;
        Vector3f max;
        if (!bodyTab && session.openCategory >= 0) {
            PartCategories.Category category =
                    session.categories.categories().get(session.openCategory);
            min = category.min();
            max = category.max();
        } else {
            min = session.categories.min();
            max = session.categories.max();
        }
        Vector3d center =
                new Vector3d((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, (min.z + max.z) / 2.0)
                        .add(CharacterStage.origin());
        double radius =
                Math.max(CharacterEditor.MIN_FRAME_RADIUS, new Vector3f(max).sub(min).length() / 2);
        orbit.glideTo(center, radius);
    }

    /** Draw a ruler in meters beside the character, with a line at its height, over the stage. */
    private void drawRuler() {
        Scene scene = GraphicsManager.getScene();
        Camera camera = scene.getCamera();
        // The view matrix only turns; positions are taken from the camera's
        Matrix4f viewProjection =
                new Matrix4f(scene.getProjection().getProjectionMatrix())
                        .mul(camera.getViewMatrix());
        Vector3d base = new Vector3d(CharacterStage.origin());
        // Beside the character, on the side the camera sees
        Vector3f right = camera.getInvViewMatrix().transformDirection(new Vector3f(1, 0, 0));
        Vector3d foot = new Vector3d(base).add(-right.x * RULER_OFFSET, 0, -right.z * RULER_OFFSET);
        float[] bottom = toScreen(viewProjection, camera, foot, 0);
        float[] top = toScreen(viewProjection, camera, foot, RULER_HEIGHT);
        if (bottom == null || top == null) {
            return;
        }
        DrawList draw = IkGui.getBackgroundDrawList();
        int fontSize = IkGui.getFontSize();
        draw.addLine(bottom[0], bottom[1], top[0], top[1], RULER_COLOR, 2);
        int marks = Math.round(RULER_HEIGHT / RULER_STEP);
        for (int m = 0; m <= marks; ++m) {
            float height = m * RULER_STEP;
            float[] at = toScreen(viewProjection, camera, foot, height);
            if (at == null) {
                continue;
            }
            boolean major = m % 2 == 0;
            float length = major ? 2 * RULER_MARK : RULER_MARK;
            draw.addLine(at[0] - length, at[1], at[0], at[1], RULER_COLOR, 2);
            if (major) {
                String label = String.format(Locale.ROOT, "%.1f m", height);
                float width = IkGui.calcTextSize(label).x;
                draw.addText(
                        fontSize,
                        at[0] - length - width - 4,
                        at[1] - fontSize / 2f,
                        RULER_COLOR,
                        label,
                        0);
            }
        }
        float[] mark = toScreen(viewProjection, camera, foot, sync.characterHeight());
        float[] head = toScreen(viewProjection, camera, base, sync.characterHeight());
        if (mark != null && head != null) {
            draw.addLine(mark[0], mark[1], head[0], head[1], HEIGHT_COLOR, 2);
        }
    }

    /**
     * Where a point shows on the screen.
     *
     * @param viewProjection The camera's projection times its turn.
     * @param camera The camera.
     * @param ground The point on the ground below it, in world space.
     * @param height How far above the ground it is.
     * @return The x and y in pixels, or null if it is behind the camera.
     */
    private static float[] toScreen(
            Matrix4f viewProjection, Camera camera, Vector3d ground, float height) {
        Vector4f clip =
                viewProjection.transform(
                        new Vector4f(
                                (float) (ground.x - camera.getPosition().x),
                                (float) (ground.y + height - camera.getPosition().y),
                                (float) (ground.z - camera.getPosition().z),
                                1));
        if (clip.w <= 1e-4f) {
            return null;
        }
        float x = clip.x / clip.w;
        float y = clip.y / clip.w;
        // The projection's y may point either way; higher points must come out higher on screen
        Vector4f up =
                viewProjection.transform(
                        new Vector4f(
                                camera.getInvViewMatrix().transformDirection(new Vector3f(0, 1, 0)),
                                0));
        float sign = up.y >= 0 ? -1 : 1;
        // IkGui draws in desktop coordinates, so start from where the window's inside is
        Viewport viewport = IkGui.getMainViewport();
        return new float[] {
            viewport.position.x + (x + 1) / 2 * viewport.size.x,
            viewport.position.y + (sign * y + 1) / 2 * viewport.size.y
        };
    }
}
