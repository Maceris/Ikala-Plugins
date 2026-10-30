package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonText;
import com.ikalagaming.characters.data.Vec3;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.view.CharacterFiles;
import com.ikalagaming.characters.view.PartAssembler;
import com.ikalagaming.characters.view.ShownCharacter;
import com.ikalagaming.graphics.DebugDraw;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.scene.debug.DebugLine;
import com.ikalagaming.graphics.scene.debug.DebugSphere;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The character editor's Align tab: lines an accessory up on its socket by hand. Its placement
 * (offset along the model's axes, turn and scale about its socket on the rig's reference body,
 * see {@link Part.Placement}) previews live as an extra transform on the socket bone, measures show where the head shows through and how far the accessory sits above
 * the brow, and Save writes the placement into the part's definition, for the design's variant or
 * every variant.
 *
 * @author Ches Burks
 */
@Slf4j
final class AccessoryAligner {
    /** How far a drag moves the offset per pixel, in millimeters. */
    private static final float OFFSET_SPEED = 0.5f;

    /** How far a drag turns per pixel, in degrees. */
    private static final float TURN_SPEED = 0.25f;

    /** How far a drag scales per pixel. */
    private static final float SCALE_SPEED = 0.002f;

    /** How far above the brow bone an accessory's bottom snaps to, in meters. */
    static final float BROW_GAP = 0.035f;

    /** How far above the head an accessory rests, in meters. */
    static final float REST_GAP = 0.004f;

    /** How far away the view buttons put the camera, in meters. */
    private static final double VIEW_DISTANCE = 0.9;

    /** How big the markers for points of the head that show are, in meters. */
    private static final double MARKER_RADIUS = 0.004;

    /** The most markers drawn, so a hat far off doesn't draw thousands. */
    private static final int MAX_MARKERS = 400;

    /** Markers for points of the head that show: orange. */
    private static final int SHOWING_COLOR = Color.rgba(255, 140, 0, 255);

    /** The brow line: cyan. */
    private static final int BROW_COLOR = Color.rgba(0, 220, 255, 255);

    /** The bone the accessory's bottom snaps above. */
    static final String BROW_BONE = "brow.L";

    /** The slot whose parts are the head. */
    static final String HEAD_SLOT = "head";

    /** Millimeters per meter. */
    private static final float MILLIMETERS = 1000;

    /**
     * An accessory being worn.
     *
     * @param socket The socket it is on.
     * @param part Its definition, as loaded now.
     * @param meshes Its meshes among the character's parts.
     */
    record Worn(@NonNull String socket, @NonNull Part part, @NonNull List<Integer> meshes) {}

    /**
     * What the tab works on this frame.
     *
     * @param character The shown character's parts, at rest in model space.
     * @param shown The shown character.
     * @param accessories The accessories worn.
     * @param head The meshes of the worn head.
     * @param variant The design's variant.
     * @param origin Where the character stands; it faces +Z, unturned.
     */
    record Stage(
            @NonNull ImportedCharacter character,
            @NonNull ShownCharacter shown,
            @NonNull List<Worn> accessories,
            @NonNull List<Integer> head,
            @NonNull String variant,
            @NonNull Vector3dc origin) {}

    /** Points the editor's camera. */
    interface Views {
        /**
         * Look at a point.
         *
         * @param point The point, in world space.
         * @param pitch The pitch, positive looking down.
         * @param yaw The yaw, 0 looking along -z.
         * @param distance How far away to be.
         */
        void look(Vector3dc point, double pitch, double yaw, double distance);
    }

    /** Draws the markers. */
    private final DebugDraw debug;

    /** The loaded definitions. */
    private final Supplier<CharacterRegistry> registry;

    /** Loads the definitions again, after a save. */
    private final Runnable reload;

    /** Points the camera. */
    private final Views views;

    /** The socket being lined up, or null before one is picked. */
    private String socket;

    /** The accessory being lined up, or null. */
    private Id partId;

    /** The shown character the placement was read for. */
    private ShownCharacter readFor;

    /** The placement built into the shown mesh, which the preview moves on from. */
    private Part.Placement built = Part.Placement.NONE;

    /** The placement's offset, in millimeters. */
    private final float[] offset = new float[3];

    /** The placement's turn, in degrees. */
    private final float[] turn = new float[3];

    /** The placement's scale. */
    private final float[] scale = {1};

    /** Whether Save writes the placement for every variant rather than the design's. */
    private boolean everyVariant;

    /** The socket bone, or -1. */
    private int bone = -1;

    /** The socket bone's rest transform in model space, on the shown skeleton. */
    private final Matrix4f socketRest = new Matrix4f();

    /** Where the socket is on the rig's reference body, which placements turn about. */
    private final Vector3f pivot = new Vector3f();

    /** Finds the rig's reference body. */
    private final Supplier<Path> reference;

    /** The reference body's file, as last read. */
    private Path referenceFile;

    /** The reference body's skeleton, or null if it couldn't be read. */
    private Skeleton referenceSkeleton;

    /** The last measures, or null to measure again. */
    private AccessoryFit.Seat seat;

    /** The accessory's lowest point less the brow's height, as last measured. */
    private float browGap = Float.NaN;

    /** What the last save or snap did, or null. */
    private String status;

    /** Whether the placement changed since it was read or saved. */
    private boolean unsaved;

    /**
     * Set up the tab.
     *
     * @param debug Draws the markers.
     * @param registry The loaded definitions.
     * @param reload Loads the definitions again.
     * @param views Points the camera.
     * @param reference Finds the rig's reference body, which placements turn about.
     */
    AccessoryAligner(
            @NonNull DebugDraw debug,
            @NonNull Supplier<CharacterRegistry> registry,
            @NonNull Runnable reload,
            @NonNull Views views,
            @NonNull Supplier<Path> reference) {
        this.debug = debug;
        this.registry = registry;
        this.reload = reload;
        this.views = views;
        this.reference = reference;
    }

    /**
     * The rig's reference body's skeleton, read again when the file changes.
     *
     * @param fallback The skeleton to use if the reference can't be read.
     * @return The skeleton.
     */
    private Skeleton referenceSkeleton(Skeleton fallback) {
        Path file = reference.get();
        if (!file.equals(referenceFile)) {
            referenceFile = file;
            try {
                referenceSkeleton = CharacterFiles.read(file, registry.get()).skeleton();
            } catch (IOException | IllegalArgumentException e) {
                log.warn("Couldn't read the rig reference {}: {}", file, e.getMessage());
                referenceSkeleton = null;
            }
        }
        return referenceSkeleton == null ? fallback : referenceSkeleton;
    }

    /**
     * Draw the tab and preview the placement.
     *
     * @param stage What it works on.
     */
    void draw(@NonNull Stage stage) {
        if (stage.accessories().isEmpty()) {
            IkGui.textDisabled("Put an accessory on a socket to line it up");
            return;
        }
        Worn worn = pickSocket(stage);
        if (readFor != stage.shown() || !worn.part().id().equals(partId)) {
            read(stage, worn);
        }
        IkGui.text(worn.part().id().toString());
        IkGui.textDisabled(
                "Body type "
                        + stage.variant()
                        + (worn.part().placement().containsKey(stage.variant())
                                ? ", its own placement"
                                : ""));

        boolean changed = false;
        IkGui.separatorText("Placement: x across, y up, z forward");
        changed |= IkGui.dragFloat3("Offset (mm)", offset, OFFSET_SPEED, -500, 500, "%.1f");
        changed |= IkGui.dragFloat3("Turn (deg)", turn, TURN_SPEED, -180, 180, "%.1f");
        changed |= IkGui.dragFloat("Scale", scale, SCALE_SPEED, 0.2f, 5, "%.3f");
        if (IkGui.button("Bottom to brow")) {
            changed |= snapToBrow(stage, worn);
        }
        IkGui.sameLine();
        if (IkGui.button("Rest on head")) {
            changed |= restOnHead(stage, worn);
        }
        IkGui.sameLine();
        if (IkGui.button("Center")) {
            changed |= center(stage, worn);
        }
        if (changed) {
            unsaved = true;
            status = null;
            preview(stage);
        }

        IkGui.separatorText("Fit");
        if (seat == null) {
            measure(stage, worn);
        }
        IkGui.text(
                Float.isNaN(browGap)
                        ? "No brow bone"
                        : String.format(
                                Locale.ROOT, "Bottom %.1f mm above the brow", browGap * MILLIMETERS));
        IkGui.text(
                Float.isNaN(seat.gap())
                        ? "Nothing of it is over the head"
                        : String.format(
                                Locale.ROOT,
                                "Could drop %.1f mm before resting on the head",
                                seat.gap() * MILLIMETERS));
        if (seat.showingCount() == 0) {
            IkGui.text("No head shows through");
        } else {
            IkGui.textColored(
                    SHOWING_COLOR, seat.showingCount() + " points of the head show (orange)");
        }
        drawMarkers(stage);

        IkGui.separatorText("View");
        Vector3d middle = middle(stage, worn);
        if (IkGui.button("Front")) {
            views.look(middle, 0, 0, VIEW_DISTANCE);
        }
        IkGui.sameLine();
        if (IkGui.button("Side")) {
            views.look(middle, 0, Math.PI / 2, VIEW_DISTANCE);
        }
        IkGui.sameLine();
        if (IkGui.button("Back")) {
            views.look(middle, 0, Math.PI, VIEW_DISTANCE);
        }
        IkGui.sameLine();
        if (IkGui.button("Top")) {
            views.look(middle, OrbitCamera.MAX_PITCH, 0, VIEW_DISTANCE);
        }

        IkGui.separatorText("Save");
        if (IkGui.checkbox("For every body type", everyVariant)) {
            everyVariant = !everyVariant;
        }
        if (IkGui.button(unsaved ? "Save*" : "Save")) {
            save(worn, stage.variant());
        }
        IkGui.sameLine();
        if (IkGui.button("Undo changes")) {
            setFields(built);
            unsaved = false;
            preview(stage);
        }
        IkGui.sameLine();
        if (IkGui.button("Clear")) {
            setFields(Part.Placement.NONE);
            unsaved = true;
            preview(stage);
        }
        if (status != null) {
            IkGui.textWrapped(status);
        }
    }

    /**
     * Stop previewing, as the tab closes or the character goes.
     *
     * @param shown The shown character, or null.
     */
    void close(ShownCharacter shown) {
        if (shown != null && bone >= 0) {
            shown.setAdjustment(bone, null);
        }
        readFor = null;
        partId = null;
    }

    /**
     * Draw the socket picker and find the accessory on the picked socket.
     *
     * @param stage What the tab works on.
     * @return The accessory.
     */
    private Worn pickSocket(Stage stage) {
        Worn picked =
                stage.accessories().stream()
                        .filter(worn -> worn.socket().equals(socket))
                        .findFirst()
                        .orElse(stage.accessories().get(0));
        if (IkGui.beginCombo("Socket", picked.socket())) {
            for (Worn worn : stage.accessories()) {
                if (IkGui.selectable(worn.socket(), worn == picked)) {
                    picked = worn;
                }
            }
            IkGui.endCombo();
        }
        if (!picked.socket().equals(socket) && readFor != null && bone >= 0) {
            // Moving to another socket drops the unsaved preview on this one
            readFor.setAdjustment(bone, null);
            readFor = null;
        }
        socket = picked.socket();
        return picked;
    }

    /**
     * Start on an accessory: read the placement its shown mesh was built with.
     *
     * @param stage What the tab works on.
     * @param worn The accessory.
     */
    private void read(Stage stage, Worn worn) {
        if (readFor != null && bone >= 0 && readFor == stage.shown()) {
            readFor.setAdjustment(bone, null);
        }
        readFor = stage.shown();
        partId = worn.part().id();
        built = worn.part().placementFor(stage.variant());
        setFields(built);
        unsaved = false;
        status = null;
        ImportedCharacter.Part mesh = stage.character().parts().get(worn.meshes().get(0));
        bone = mesh.joints().length == 0 ? -1 : mesh.joints()[0];
        Skeleton skeleton = stage.character().skeleton();
        if (bone >= 0) {
            skeleton.inverseBind(bone, socketRest).invert();
            PartAssembler.restPosition(referenceSkeleton(skeleton), bone, pivot);
        }
        seat = null;
    }

    /**
     * Show the fields' values.
     *
     * @param placement The placement.
     */
    private void setFields(Part.Placement placement) {
        Vec3 moved = placement.offset() == null ? Vec3.ZERO : placement.offset();
        offset[0] = moved.x() * MILLIMETERS;
        offset[1] = moved.y() * MILLIMETERS;
        offset[2] = moved.z() * MILLIMETERS;
        Vec3 turned = placement.rotate() == null ? Vec3.ZERO : placement.rotate();
        turn[0] = turned.x();
        turn[1] = turned.y();
        turn[2] = turned.z();
        scale[0] = placement.scale();
        seat = null;
    }

    /**
     * The placement the fields hold.
     *
     * @return The placement, rounded to a tenth of a millimeter and a hundredth of a degree.
     */
    Part.Placement placement() {
        Vec3 moved =
                new Vec3(
                        round(offset[0] / MILLIMETERS, 4),
                        round(offset[1] / MILLIMETERS, 4),
                        round(offset[2] / MILLIMETERS, 4));
        Vec3 turned = new Vec3(round(turn[0], 2), round(turn[1], 2), round(turn[2], 2));
        return new Part.Placement(
                moved.equals(Vec3.ZERO) ? null : moved,
                turned.equals(Vec3.ZERO) ? null : turned,
                Math.max(0.01f, round(scale[0], 4)));
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
     * How the placement moves the shown mesh, which was built with {@link #built}, in model space.
     *
     * @param out Where to put it.
     * @return out.
     */
    private Matrix4f moveFromBuilt(Matrix4f out) {
        return placement()
                .matrix(pivot, out)
                .mul(built.matrix(pivot, new Matrix4f()).invert());
    }

    /**
     * Show the fields' placement on the character.
     *
     * @param stage What the tab works on.
     */
    private void preview(Stage stage) {
        if (bone < 0) {
            return;
        }
        // The shown skeleton takes extra transforms in the socket bone's space at rest
        Matrix4f change =
                new Matrix4f(socketRest)
                        .invert()
                        .mul(moveFromBuilt(new Matrix4f()))
                        .mul(socketRest);
        stage.shown().setAdjustment(bone, change);
        seat = null;
    }

    /**
     * Measure how the accessory sits, as placed now.
     *
     * @param stage What the tab works on.
     * @param worn The accessory.
     */
    private void measure(Stage stage, Worn worn) {
        AccessoryFit.Mesh accessory = accessoryMesh(stage, worn);
        AccessoryFit.Mesh head = headMesh(stage);
        seat =
                head == null
                        ? new AccessoryFit.Seat(new float[0], Float.NaN)
                        : AccessoryFit.seat(head, accessory);
        float brow = browHeight(stage);
        browGap = Float.isNaN(brow) ? Float.NaN : accessory.bottom() - brow;
    }

    /**
     * The accessory's triangles as placed now.
     *
     * @param stage What the tab works on.
     * @param worn The accessory.
     * @return The triangles.
     */
    private AccessoryFit.Mesh accessoryMesh(Stage stage, Worn worn) {
        return AccessoryFit.mesh(
                worn.meshes().stream().map(stage.character().parts()::get).toList(),
                moveFromBuilt(new Matrix4f()));
    }

    /**
     * The worn head's triangles.
     *
     * @param stage What the tab works on.
     * @return The triangles, or null if no head is worn.
     */
    private static AccessoryFit.Mesh headMesh(Stage stage) {
        if (stage.head().isEmpty()) {
            return null;
        }
        return AccessoryFit.mesh(
                stage.head().stream().map(stage.character().parts()::get).toList(), null);
    }

    /**
     * The brow bone's height at rest on the rig's reference body, which accessories are fitted
     * to. A skeleton moved onto the rig from another can have its brow elsewhere, as the brow
     * turns with a head bone that points another way.
     *
     * @param stage What the tab works on.
     * @return The height, or NaN if the rig has no brow bone.
     */
    private float browHeight(Stage stage) {
        Skeleton skeleton = referenceSkeleton(stage.character().skeleton());
        int brow = skeleton.index(BROW_BONE);
        if (brow < 0) {
            return Float.NaN;
        }
        return PartAssembler.restPosition(skeleton, brow, new Vector3f()).y;
    }

    /**
     * Move the placement by a distance in model space.
     *
     * @param model The distance, in meters.
     */
    private void moveBy(Vector3f model) {
        offset[0] += model.x * MILLIMETERS;
        offset[1] += model.y * MILLIMETERS;
        offset[2] += model.z * MILLIMETERS;
    }

    /**
     * Raise or lower the accessory so its bottom is {@link #BROW_GAP} above the brow.
     *
     * @param stage What the tab works on.
     * @param worn The accessory.
     * @return Whether it moved.
     */
    private boolean snapToBrow(Stage stage, Worn worn) {
        float brow = browHeight(stage);
        if (Float.isNaN(brow) || bone < 0) {
            status = "The rig has no " + BROW_BONE + " bone";
            return false;
        }
        float bottom = accessoryMesh(stage, worn).bottom();
        moveBy(new Vector3f(0, brow + BROW_GAP - bottom, 0));
        status = String.format(Locale.ROOT, "Bottom moved to %.0f mm above the brow",
                BROW_GAP * MILLIMETERS);
        return true;
    }

    /**
     * Drop or raise the accessory until it rests {@link #REST_GAP} above the head.
     *
     * @param stage What the tab works on.
     * @param worn The accessory.
     * @return Whether it moved.
     */
    private boolean restOnHead(Stage stage, Worn worn) {
        AccessoryFit.Mesh head = headMesh(stage);
        if (head == null || bone < 0) {
            status = "No head is worn";
            return false;
        }
        AccessoryFit.Seat now = AccessoryFit.seat(head, accessoryMesh(stage, worn));
        if (Float.isNaN(now.gap())) {
            status = "Nothing of it is over the head";
            return false;
        }
        moveBy(new Vector3f(0, REST_GAP - now.gap(), 0));
        status = "Dropped onto the head";
        return true;
    }

    /**
     * Center the accessory side to side over the head. Front to back stays, since a cap's visor
     * or a hat's feather would pull a box's middle off the head.
     *
     * @param stage What the tab works on.
     * @param worn The accessory.
     * @return Whether it moved.
     */
    private boolean center(Stage stage, Worn worn) {
        AccessoryFit.Mesh head = headMesh(stage);
        if (head == null || bone < 0) {
            status = "No head is worn";
            return false;
        }
        AccessoryFit.Mesh accessory = accessoryMesh(stage, worn);
        float headMiddle = AccessoryFit.middleAbove(head, accessory.bottom());
        float middle = AccessoryFit.middleAbove(accessory, accessory.bottom());
        if (Float.isNaN(headMiddle) || Float.isNaN(middle)) {
            status = "The head doesn't reach the accessory";
            return false;
        }
        moveBy(new Vector3f(headMiddle - middle, 0, 0));
        status = "Centered side to side";
        return true;
    }

    /**
     * The middle of the accessory as placed now, in world space.
     *
     * @param stage What the tab works on.
     * @param worn The accessory.
     * @return The point.
     */
    private Vector3d middle(Stage stage, Worn worn) {
        AccessoryFit.Mesh accessory = accessoryMesh(stage, worn);
        Vector3f middle = new Vector3f(accessory.min()).add(accessory.max()).mul(0.5f);
        return new Vector3d(stage.origin()).add(middle.x, middle.y, middle.z);
    }

    /**
     * Mark the points of the head that show, and the brow line.
     *
     * @param stage What the tab works on.
     */
    private void drawMarkers(Stage stage) {
        Vector3dc origin = stage.origin();
        float[] showing = seat.showing();
        int step = Math.max(1, showing.length / 3 / MAX_MARKERS);
        for (int i = 0; i < showing.length; i += 3 * step) {
            debug.draw(
                    new DebugSphere(
                            new Vector3d(origin).add(showing[i], showing[i + 1], showing[i + 2]),
                            MARKER_RADIUS,
                            SHOWING_COLOR,
                            true));
        }
        float brow = browHeight(stage);
        if (!Float.isNaN(brow)) {
            double y = origin.y() + brow;
            debug.draw(
                    new DebugLine(
                            new Vector3d(origin.x() - 0.15, y, origin.z() + 0.15),
                            new Vector3d(origin.x() + 0.15, y, origin.z() + 0.15),
                            BROW_COLOR,
                            true));
        }
    }

    /**
     * Write the placement into the part's definition file, then load the definitions again.
     *
     * @param worn The accessory.
     * @param variant The design's variant.
     */
    private void save(Worn worn, String variant) {
        Id id = worn.part().id();
        Path file;
        try {
            file = findDefinition(id);
        } catch (IOException e) {
            status = "Not saved: " + e.getMessage();
            return;
        }
        if (file == null) {
            status = "Not saved: no definition file of " + id + " is on disk";
            return;
        }
        String key = everyVariant ? Part.ANY_VARIANT : variant;
        try {
            writePlacement(file, id, key, placement());
        } catch (IOException | IllegalArgumentException e) {
            status = "Not saved: " + e.getMessage();
            return;
        }
        unsaved = false;
        status = "Saved the placement for " + (everyVariant ? "every body type" : variant)
                + " to " + file.getFileName();
        log.info("Saved the placement of {} for {} to {}", id, key, file);
        reload.run();
    }

    /**
     * Find the file that defines a part, in its namespace's content folder.
     *
     * @param id The part's ID.
     * @return The file, or null if none on disk does.
     * @throws IOException If the folder can't be searched.
     */
    private Path findDefinition(Id id) throws IOException {
        Path root = registry.get().asset(id.namespace(), "");
        if (root == null || !Files.isDirectory(root)) {
            return null;
        }
        String quoted = "\"" + id + "\"";
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (text.contains(quoted) && definitionIn(JsonText.parse(text, e -> {}), id)
                        != null) {
                    return file;
                }
            }
        }
        return null;
    }

    /**
     * Find a part's definition among a file's.
     *
     * @param json The file's JSON, one definition or an array of them.
     * @param id The part's ID.
     * @return The definition, or null if it isn't there.
     */
    static JsonObject definitionIn(JsonElement json, Id id) {
        List<JsonElement> found = new ArrayList<>();
        if (json instanceof JsonArray array) {
            array.forEach(found::add);
        } else if (json != null) {
            found.add(json);
        }
        for (JsonElement element : found) {
            if (element instanceof JsonObject object
                    && object.has("id")
                    && id.toString().equals(object.get("id").getAsString())) {
                return object;
            }
        }
        return null;
    }

    /**
     * Write a placement into a definition file. Comment lines at the top of the file stay; a file
     * with comments further down is left alone, since writing it again would drop them.
     *
     * @param file The file.
     * @param id The part's ID.
     * @param variant The variant to place it for, or {@link Part#ANY_VARIANT}.
     * @param placement The placement.
     * @throws IOException If the file can't be read or written.
     * @throws IllegalArgumentException If the file has comments past its top or lacks the part.
     */
    static void writePlacement(Path file, Id id, String variant, Part.Placement placement)
            throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        StringBuilder header = new StringBuilder();
        String[] lines = text.split("\\R", -1);
        int body = 0;
        while (body < lines.length && lines[body].trim().startsWith("//")) {
            header.append(lines[body]).append('\n');
            ++body;
        }
        String rest = String.join("\n", List.of(lines).subList(body, lines.length));
        if (rest.contains("//") || rest.contains("/*")) {
            throw new IllegalArgumentException(
                    file.getFileName() + " has comments inside; add the placement by hand");
        }
        JsonElement json = JsonText.parse(rest, e -> {});
        JsonObject definition = definitionIn(json, id);
        if (definition == null) {
            throw new IllegalArgumentException(file.getFileName() + " doesn't define " + id);
        }
        JsonObject placements =
                definition.has(Part.FIELD_PLACEMENT)
                        ? definition.getAsJsonObject(Part.FIELD_PLACEMENT)
                        : new JsonObject();
        placements.add(variant, DataFormat.write(placement));
        definition.add(Part.FIELD_PLACEMENT, placements);
        Files.writeString(
                file,
                header + new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n",
                StandardCharsets.UTF_8);
    }
}
