package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Vec3;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.pose.WingMotion;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.view.ShownCharacter;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.gizmo.GizmoView;
import com.ikalagaming.graphics.gizmo.TransformGizmo;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.Viewport;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Align tab's wing mode: poses a worn wing by hand, for a wing that folds or sits differently
 * from the plan's. The handles turn a bone of the folded or gliding wing, or move the wing's root,
 * and the fields hold the angles; the wing shows the change live. Save writes the poses into the
 * part's definition as its own {@code wing}, for it and its other side, which mirrors them.
 *
 * @author Ches Burks
 */
@Slf4j
final class WingPoser {
    /** How far a drag moves the offset per pixel, in millimeters. */
    private static final float OFFSET_SPEED = 0.5f;

    /** How far a drag turns per pixel, in degrees. */
    private static final float TURN_SPEED = 0.25f;

    /** What Ctrl snaps moves to, in meters. */
    private static final float SNAP_MOVE = 0.005f;

    /** What Ctrl snaps turns to, in degrees. */
    private static final float SNAP_TURN = 5;

    /** Millimeters per meter. */
    private static final float MILLIMETERS = 1000;

    /** The poses the tool edits: those with angles for each bone. */
    static final List<WingMotion.State> POSED =
            List.of(WingMotion.State.FOLD, WingMotion.State.GLIDE);

    /**
     * A wing being worn.
     *
     * @param chain Its chain.
     * @param part Its definition, as loaded now.
     * @param bones Its chain's bones, from the root out.
     * @param mirrored Whether it is a right wing, which mirrors the poses.
     * @param planPoses The plan's poses for its chain.
     */
    record Worn(
            @NonNull String chain,
            @NonNull Part part,
            @NonNull List<String> bones,
            boolean mirrored,
            BodyPlan.@NonNull WingPoses planPoses) {
        /**
         * The poses it starts with: its own, or else the plan's.
         *
         * @return The poses.
         */
        BodyPlan.WingPoses poses() {
            return part.wing() == null ? planPoses : part.wing();
        }
    }

    /**
     * What the tab works on this frame.
     *
     * @param shown The shown character.
     * @param wings The wings worn.
     * @param motion Moves the worn wings.
     */
    record Stage(
            @NonNull ShownCharacter shown, @NonNull List<Worn> wings, @NonNull WingMotion motion) {}

    /** The loaded definitions. */
    private final Supplier<CharacterRegistry> registry;

    /** Loads the definitions again, after a save. */
    private final Runnable reload;

    /** Has the wings take a pose. */
    private final Consumer<WingMotion.State> showPose;

    /** The wing's chain being posed, or null before one is picked. */
    private String chain;

    /** The wing's part being posed, or null. */
    private Id partId;

    /** The shown character the poses were read for. */
    private ShownCharacter readFor;

    /** The poses as read or saved, which the fields start from and keep the flaps of. */
    private BodyPlan.WingPoses read;

    /** The pose being edited. */
    private WingMotion.State editing = WingMotion.State.FOLD;

    /** The bone the handles turn, as its place in the chain. */
    private int bone;

    /** The root's offset, in millimeters. */
    private final float[] offset = new float[3];

    /** Each bone's angles in each posed state, in degrees, by {@link #POSED}'s order. */
    private final List<List<float[]>> angles = new ArrayList<>();

    /** Whether Save and the preview change the other side's wing too. */
    private boolean bothSides = true;

    /** What the last save did, or null. */
    private String status;

    /** Whether the poses changed since they were read or saved. */
    private boolean unsaved;

    /** The handles drawn on the wing for moving and turning it with the mouse. */
    private final TransformGizmo gizmo = new TransformGizmo();

    /** The offset when the handles' drag started. */
    private final float[] dragOffset = new float[3];

    /** The bone's angles when the handles' drag started. */
    private final float[] dragAngles = new float[3];

    /** Whether the handles were being dragged last frame, to tell when a drag starts. */
    private boolean wasDragging;

    /** The IkGui frame the handles were last updated in, to tell whether the tab is open. */
    private int gizmoFrame = -1;

    /** The bones as last posed. */
    private float[] posed = new float[0];

    /**
     * Set up the mode.
     *
     * @param registry The loaded definitions.
     * @param reload Loads the definitions again.
     * @param showPose Has the wings take a pose.
     */
    WingPoser(
            @NonNull Supplier<CharacterRegistry> registry,
            @NonNull Runnable reload,
            @NonNull Consumer<WingMotion.State> showPose) {
        this.registry = registry;
        this.reload = reload;
        this.showPose = showPose;
        gizmo.setSnap(SNAP_MOVE, SNAP_TURN, 1);
        gizmo.setMode(TransformGizmo.Mode.ROTATE);
    }

    /**
     * Whether the handles want the mouse this frame, so the editor's camera should leave it alone.
     *
     * @return True while the mouse is on a handle or dragging one, with the mode open.
     */
    boolean wantsMouse() {
        return gizmoFrame >= IkGui.getFrameCount() - 1 && gizmo.wantsMouse();
    }

    /**
     * Draw the mode and preview the poses.
     *
     * @param stage What it works on.
     */
    void draw(@NonNull Stage stage) {
        if (stage.wings().isEmpty()) {
            IkGui.textDisabled("Wear a wing to pose it");
            return;
        }
        Worn worn = pickWing(stage);
        if (readFor != stage.shown() || !worn.part().id().equals(partId)) {
            read(stage, worn);
        }
        IkGui.text(worn.part().id().toString());
        IkGui.textDisabled(
                worn.part().wing() == null ? "Moves with the plan's poses" : "Has its own poses");

        IkGui.textUnformatted("Pose");
        for (WingMotion.State state : POSED) {
            IkGui.sameLine();
            if (IkGui.radioButton(state.key() + "##wing-pose-" + state.key(), editing == state)) {
                editing = state;
                showPose.accept(state);
            }
        }

        boolean changed = false;
        IkGui.separatorText("Root: x out, y up, z forward");
        changed |= IkGui.dragFloat3("Offset (mm)", offset, OFFSET_SPEED, -300, 300, "%.1f");

        IkGui.separatorText("Turns about each joint (deg)");
        List<float[]> turns = angles.get(POSED.indexOf(editing));
        for (int i = 0; i < turns.size(); ++i) {
            if (IkGui.radioButton("##wing-bone-" + i, bone == i)) {
                bone = i;
            }
            IkGui.sameLine();
            changed |=
                    IkGui.dragFloat3(
                            worn.bones().get(i) + "##wing-turn-" + i,
                            turns.get(i),
                            TURN_SPEED,
                            -180,
                            180,
                            "%.1f");
        }
        changed |= drawHandles(stage, worn);
        if (changed) {
            unsaved = true;
            status = null;
            preview(stage, worn);
        }

        IkGui.separatorText("Save");
        if (IkGui.checkbox("Both sides", bothSides)) {
            bothSides = !bothSides;
            preview(stage, worn);
        }
        if (IkGui.button(unsaved ? "Save*" : "Save")) {
            save(stage, worn);
        }
        IkGui.sameLine();
        if (IkGui.button("Undo changes")) {
            setFields(read, worn.bones().size());
            unsaved = false;
            preview(stage, worn);
        }
        IkGui.sameLine();
        if (IkGui.button("Plan's poses")) {
            setFields(worn.planPoses(), worn.bones().size());
            unsaved = true;
            preview(stage, worn);
        }
        if (status != null) {
            IkGui.textWrapped(status);
        }
    }

    /**
     * Draw the wing picker and find the wing picked.
     *
     * @param stage What the mode works on.
     * @return The wing.
     */
    private Worn pickWing(Stage stage) {
        Worn picked =
                stage.wings().stream()
                        .filter(worn -> worn.chain().equals(chain))
                        .findFirst()
                        .orElse(stage.wings().get(0));
        if (IkGui.beginCombo("Wing", picked.chain())) {
            for (Worn worn : stage.wings()) {
                if (IkGui.selectable(worn.chain(), worn == picked)) {
                    picked = worn;
                }
            }
            IkGui.endCombo();
        }
        chain = picked.chain();
        return picked;
    }

    /**
     * Start on a wing: read its poses and show the pose being edited.
     *
     * @param stage What the mode works on.
     * @param worn The wing.
     */
    private void read(Stage stage, Worn worn) {
        readFor = stage.shown();
        partId = worn.part().id();
        read = worn.poses();
        setFields(read, worn.bones().size());
        bone = Math.min(bone, worn.bones().size() - 1);
        unsaved = false;
        status = null;
        // Shown as read, in case an earlier unsaved change is still on the wing
        preview(stage, worn);
        showPose.accept(editing);
    }

    /**
     * Show poses in the fields.
     *
     * @param poses The poses.
     * @param count How many bones the wing has.
     */
    private void setFields(BodyPlan.WingPoses poses, int count) {
        Vec3 moved = poses.offset() == null ? Vec3.ZERO : poses.offset();
        offset[0] = moved.x() * MILLIMETERS;
        offset[1] = moved.y() * MILLIMETERS;
        offset[2] = moved.z() * MILLIMETERS;
        angles.clear();
        for (WingMotion.State state : POSED) {
            List<Vec3> from = state == WingMotion.State.FOLD ? poses.fold() : poses.glide();
            List<float[]> turns = new ArrayList<>();
            for (int i = 0; i < Math.max(count, from.size()); ++i) {
                Vec3 turn = i < from.size() && from.get(i) != null ? from.get(i) : Vec3.ZERO;
                turns.add(new float[] {turn.x(), turn.y(), turn.z()});
            }
            angles.add(turns);
        }
    }

    /**
     * The poses the fields hold.
     *
     * @return The poses, keeping the flaps and sway as read, with angles rounded to hundredths of a
     *     degree and the offset to a tenth of a millimeter.
     */
    BodyPlan.WingPoses poses() {
        Vec3 moved =
                new Vec3(
                        round(offset[0] / MILLIMETERS, 4),
                        round(offset[1] / MILLIMETERS, 4),
                        round(offset[2] / MILLIMETERS, 4));
        return new BodyPlan.WingPoses(
                turns(angles.get(0)),
                turns(angles.get(1)),
                read.flap(),
                read.flapRate(),
                read.flapLag(),
                read.sway(),
                read.swayRate(),
                Vec3.ZERO.equals(moved) ? null : moved);
    }

    /**
     * Each bone's angles as written, leaving off bones past the last one that turns.
     *
     * @param fields The fields.
     * @return The angles.
     */
    private static List<Vec3> turns(List<float[]> fields) {
        List<Vec3> turns = new ArrayList<>();
        for (float[] field : fields) {
            turns.add(new Vec3(round(field[0], 2), round(field[1], 2), round(field[2], 2)));
        }
        while (!turns.isEmpty() && Vec3.ZERO.equals(turns.get(turns.size() - 1))) {
            turns.remove(turns.size() - 1);
        }
        return turns;
    }

    /**
     * Round to some decimal places.
     *
     * @param value The value.
     * @param places How many places.
     * @return The rounded value.
     */
    private static float round(float value, int places) {
        double factor = Math.pow(10, places);
        return (float) (Math.round(value * factor) / factor);
    }

    /**
     * Show the fields' poses on the wing, and its other side's.
     *
     * @param stage What the mode works on.
     * @param worn The wing.
     */
    private void preview(Stage stage, Worn worn) {
        BodyPlan.WingPoses poses = poses();
        stage.motion().setPoses(worn.chain(), poses);
        for (Worn other : stage.wings()) {
            if (other != worn && isPair(worn, other)) {
                stage.motion().setPoses(other.chain(), bothSides ? poses : other.poses());
            }
        }
    }

    /**
     * Whether two worn wings are each other's other side.
     *
     * @param one One wing.
     * @param other The other.
     * @return True if one names the other as its pair, or the other names it.
     */
    private static boolean isPair(Worn one, Worn other) {
        return other.part().id().equals(one.part().pair())
                || one.part().id().equals(other.part().pair());
    }

    /**
     * Draw the handles' settings, update the handles on the wing, and apply their drag.
     *
     * @param stage What the mode works on.
     * @param worn The wing.
     * @return Whether the fields changed.
     */
    private boolean drawHandles(Stage stage, Worn worn) {
        IkGui.separatorText("Handles");
        TransformGizmo.Mode mode = gizmo.getMode();
        if (IkGui.radioButton("Move root##wing-move", mode == TransformGizmo.Mode.MOVE)) {
            gizmo.setMode(TransformGizmo.Mode.MOVE);
        }
        IkGui.sameLine();
        if (IkGui.radioButton("Turn bone##wing-turn", mode != TransformGizmo.Mode.MOVE)) {
            gizmo.setMode(TransformGizmo.Mode.ROTATE);
        }
        IkGui.sameLine();
        boolean local = gizmo.getSpace() == TransformGizmo.Space.LOCAL;
        if (IkGui.checkbox("Its own axes##wing-local", local)) {
            gizmo.setSpace(local ? TransformGizmo.Space.WORLD : TransformGizmo.Space.LOCAL);
        }
        if (gizmo.getMode() == TransformGizmo.Mode.SCALE) {
            // Wings don't scale here; their part's chain length does
            gizmo.setMode(TransformGizmo.Mode.ROTATE);
        }
        Vector3d at = posedHead(stage, worn);
        if (at == null) {
            return false;
        }
        gizmoFrame = IkGui.getFrameCount();
        gizmo.handleKeys();
        Viewport viewport = IkGui.getMainViewport();
        GizmoView view =
                GizmoView.of(
                        GraphicsManager.getScene(),
                        viewport.position.x,
                        viewport.position.y,
                        viewport.size.x,
                        viewport.size.y);
        float[] turn = turnFields();
        Quaternionf current =
                WingMotion.turn(List.of(vec(turn)), 0, worn.mirrored(), new Quaternionf());
        TransformGizmo.Drag drag =
                gizmo.update(
                        view, at, current.get(new Matrix3f()), TransformGizmo.Input.fromIkGui());
        gizmo.draw(IkGui.getBackgroundDrawList());
        boolean started = gizmo.isDragging() && !wasDragging;
        wasDragging = gizmo.isDragging();
        if (drag == null) {
            return false;
        }
        if (started) {
            System.arraycopy(offset, 0, dragOffset, 0, 3);
            System.arraycopy(turn, 0, dragAngles, 0, 3);
        }
        float[] before = {offset[0], offset[1], offset[2], turn[0], turn[1], turn[2]};
        System.arraycopy(dragOffset, 0, offset, 0, 3);
        System.arraycopy(dragAngles, 0, turn, 0, 3);
        if (!drag.canceled()) {
            if (drag.mode() == TransformGizmo.Mode.MOVE) {
                // Characters stand unturned on the stage; a right wing's offset is mirrored
                float sign = worn.mirrored() ? -1 : 1;
                offset[0] += sign * drag.move().x() * MILLIMETERS;
                offset[1] += drag.move().y() * MILLIMETERS;
                offset[2] += drag.move().z() * MILLIMETERS;
            } else if (drag.mode() == TransformGizmo.Mode.ROTATE) {
                Quaternionf by =
                        new Quaternionf()
                                .rotationAxis(
                                        drag.angle(),
                                        drag.axis().x(),
                                        drag.axis().y(),
                                        drag.axis().z());
                Vec3 turned = turned(vec(dragAngles), worn.mirrored(), by);
                turn[0] = turned.x();
                turn[1] = turned.y();
                turn[2] = turned.z();
            }
        }
        float[] after = {offset[0], offset[1], offset[2], turn[0], turn[1], turn[2]};
        return !Arrays.equals(before, after);
    }

    /**
     * A bone's angles after a turn more, in model space, about its head.
     *
     * @param angles The bone's angles, for the left side, in degrees.
     * @param mirrored Whether the wing is a right one.
     * @param by The turn more.
     * @return The angles, for the left side.
     */
    static Vec3 turned(@NonNull Vec3 angles, boolean mirrored, @NonNull Quaternionfc by) {
        // Turns in model space about the bone's head, after the bones nearer the root turned it
        // there, so a turn more goes before the bone's own
        Quaternionf turn = WingMotion.turn(List.of(angles), 0, mirrored, new Quaternionf());
        return WingMotion.angles(new Quaternionf(by).mul(turn), mirrored);
    }

    /**
     * The fields of the bone the handles turn, in the pose being edited.
     *
     * @return The fields, changed in place.
     */
    private float[] turnFields() {
        return angles.get(POSED.indexOf(editing)).get(bone);
    }

    /**
     * Angles as a vector.
     *
     * @param degrees The angles.
     * @return The vector.
     */
    private static Vec3 vec(float[] degrees) {
        return new Vec3(degrees[0], degrees[1], degrees[2]);
    }

    /**
     * Where the handles go: the root's head when moving, else the turned bone's, in world space.
     *
     * @param stage What the mode works on.
     * @param worn The wing.
     * @return The point, or null before the character is posed.
     */
    private Vector3d posedHead(Stage stage, Worn worn) {
        ShownCharacter shown = stage.shown();
        int size = shown.getSkeleton().size() * ShownCharacter.BONE_FLOATS;
        if (posed.length != size) {
            posed = new float[size];
        }
        if (!shown.posedBones(posed)) {
            return null;
        }
        String name = worn.bones().get(gizmo.getMode() == TransformGizmo.Mode.MOVE ? 0 : bone);
        int index = shown.getSkeleton().index(name);
        if (index < 0) {
            return null;
        }
        int at = index * ShownCharacter.BONE_FLOATS;
        Vector3f head =
                shown.facing().transform(new Vector3f(posed[at], posed[at + 1], posed[at + 2]));
        return new Vector3d(shown.position()).add(head.x, head.y, head.z);
    }

    /**
     * Write the poses into the part's definition file, and its other side's, then load the
     * definitions again.
     *
     * @param stage What the mode works on.
     * @param worn The wing.
     */
    private void save(Stage stage, Worn worn) {
        BodyPlan.WingPoses poses = poses();
        List<Id> parts = new ArrayList<>();
        parts.add(worn.part().id());
        if (bothSides && worn.part().pair() != null) {
            parts.add(worn.part().pair());
        }
        List<String> saved = new ArrayList<>();
        for (Id id : parts) {
            try {
                Path file = AccessoryAligner.findDefinition(registry.get(), id);
                if (file == null) {
                    status = "Not saved: no definition file of " + id + " is on disk";
                    return;
                }
                AccessoryAligner.writeDefinition(
                        file,
                        id,
                        definition -> definition.add(Part.FIELD_WING, DataFormat.write(poses)));
                saved.add(file.getFileName().toString());
                log.info("Saved the wing poses of {} to {}", id, file);
            } catch (IOException | IllegalArgumentException e) {
                status = "Not saved: " + e.getMessage();
                return;
            }
        }
        read = poses;
        unsaved = false;
        status = "Saved the poses to " + String.join(", ", saved);
        // The wing keeps the poses shown, which are now its part's
        reload.run();
    }
}
