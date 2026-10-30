package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.Default;
import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;
import com.ikalagaming.characters.data.Vec3;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One swappable mesh: a body part that fills slots, a clothing layer, or an accessory on a socket.
 *
 * @param id This part's ID.
 * @param plan The body plan it is for.
 * @param kind What sort of part it is.
 * @param fills The slots or layers it fills.
 * @param fits The sockets an accessory fits.
 * @param rigid Whether an accessory moves rigidly with its socket.
 * @param extension The extension chain a skinned accessory is weighted to.
 * @param mirror Whether the converter makes the other side by mirroring.
 * @param meshes Mesh assets by variant.
 * @param materialSlots The mesh's materials.
 * @param tags Tags variants and editors filter by.
 * @param conflicts Parts that can't be used with this one.
 * @param requires Parts that must be used with this one.
 * @param hidesRegions Mask regions of the body it hides.
 * @param hidesLayers Layers it hides.
 * @param sockets Sockets it moves.
 * @param fx Effects it carries, gone when it is swapped.
 * @author Ches Burks
 */
@Doc(
        "One swappable mesh: a body part that fills slots, a clothing or armor layer, or an"
                + " accessory on a socket.")
public record Part(
        @Doc("This part's ID, like \"ikala:part/biped/forearm_robot\".") Id id,
        @Doc("The body plan it is for.") Id plan,
        @Doc("What sort of part it is.") PartKind kind,
        @Doc(
                        "The body slots a body part fills, or the layers a layer part fills."
                                + " Accessories leave it out.")
                @Optional
                List<String> fills,
        @Doc("For accessories: the socket names or socket types it fits.") @Optional
                List<String> fits,
        @Doc("For accessories: whether it moves rigidly with its socket, like a hat.")
                @Default("true")
                boolean rigid,
        @Doc("For skinned accessories: the extension chain it is weighted to, like \"cape\".")
                @Optional
                String extension,
        @Doc(
                        "Whether it is authored for one side, and the converter makes the other"
                                + " by mirroring and swapping .L and .R.")
                @Default("false")
                boolean mirror,
        @Doc("Mesh assets by variant name, with \"*\" for any other variant.") @Optional
                Map<String, String> meshes,
        @Doc("The mesh's materials, and which palette channels tint them.") @Optional
                List<MaterialSlot> materialSlots,
        @Doc("Tags that variants and editors filter by, like \"robot\".") @Optional
                List<String> tags,
        @Doc("Parts that can't be used along with this one.") @Optional List<Id> conflicts,
        @Doc("Parts that must be used along with this one.") @Optional List<Id> requires,
        @Doc("Mask regions of the body this hides, so they don't poke through.") @Optional
                List<String> hidesRegions,
        @Doc("Layers this hides, like a helmet hiding hair.") @Optional List<String> hidesLayers,
        @Doc(
                        "Sockets this moves, for stumps and missing limbs, so held items still"
                                + " have somewhere to go.")
                @Optional
                Map<String, SocketMove> sockets,
        @Doc("Effects it carries, like sparks on a robot arm, gone when it is swapped.") @Optional
                List<FxUse> fx,
        @Doc(
                        "For accessories: where it sits on its socket, by variant name with \"*\""
                                + " for any other variant, moved from where its mesh puts it.")
                @Optional
                Map<String, Placement> placement)
        implements Definition {

    /** The field for the plan. */
    public static final String FIELD_PLAN = "plan";

    /** The field for the slots or layers it fills. */
    public static final String FIELD_FILLS = "fills";

    /** The field for the sockets an accessory fits. */
    public static final String FIELD_FITS = "fits";

    /** The field for the extension chain. */
    public static final String FIELD_EXTENSION = "extension";

    /** The field for mirroring. */
    public static final String FIELD_MIRROR = "mirror";

    /** The field for the meshes. */
    public static final String FIELD_MESHES = "meshes";

    /** The field for the material slots. */
    public static final String FIELD_MATERIAL_SLOTS = "materialSlots";

    /** The field for conflicting parts. */
    public static final String FIELD_CONFLICTS = "conflicts";

    /** The field for the placements. */
    public static final String FIELD_PLACEMENT = "placement";

    /** The field for required parts. */
    public static final String FIELD_REQUIRES = "requires";

    /** The field for hidden regions. */
    public static final String FIELD_HIDES_REGIONS = "hidesRegions";

    /** The field for hidden layers. */
    public static final String FIELD_HIDES_LAYERS = "hidesLayers";

    /** The field for moved sockets. */
    public static final String FIELD_SOCKETS = "sockets";

    /** The field for the effects. */
    public static final String FIELD_FX = "fx";

    /** The tint mask channels a material slot maps to palette channels. */
    public static final Set<String> MASK_CHANNELS = Set.of("r", "g", "b", "a");

    /** The mesh key used for any variant without its own mesh. */
    public static final String ANY_VARIANT = "*";

    /** What sort of part this is. */
    public enum PartKind {
        /** Fills body slots and matches their seam rings. */
        BODY,
        /** Overlaps body slots and hides what it covers. */
        LAYER,
        /** Attaches to a socket. */
        ACCESSORY
    }

    /**
     * One of the mesh's materials.
     *
     * @param name The material's name in the mesh.
     * @param template The material template it uses.
     * @param channels Which palette channel each tint mask channel takes.
     * @param defaults The color each tint mask channel shows when the design leaves its palette
     *     channel uncolored, as a generated part's own colors.
     */
    @Doc("One of the mesh's materials.")
    public record MaterialSlot(
            @Doc("The material's name in the mesh.") String name,
            @Doc("The material template it uses.") Id template,
            @Doc(
                            "Which palette channel each tint mask channel (r, g, b, a) takes,"
                                    + " like {\"r\": \"primary\"}.")
                    @Optional
                    Map<String, String> channels,
            @Doc(
                            "The color each tint mask channel shows when the design leaves its"
                                    + " palette channel uncolored, like {\"r\": \"#6b4a2f\"}.")
                    @Optional
                    Map<String, Color> defaults) {
        /** The field for the template. */
        public static final String FIELD_TEMPLATE = "template";

        /** The field for the channel mapping. */
        public static final String FIELD_CHANNELS = "channels";

        /** The field for the default colors. */
        public static final String FIELD_DEFAULTS = "defaults";
    }

    /**
     * Where a socket moves to.
     *
     * @param bone The bone it moves to.
     * @param offset Its offset from the bone, in meters.
     * @param rotate Its rotation from the bone, in degrees.
     */
    @Doc("Where a socket moves to.")
    public record SocketMove(
            @Doc("The bone it moves to.") String bone,
            @Doc("Its offset from the bone, in meters.") @Optional Vec3 offset,
            @Doc("Its rotation from the bone, in degrees.") @Optional Vec3 rotate) {
        /** The field for the bone. */
        public static final String FIELD_BONE = "bone";
    }

    /**
     * How an accessory is moved from where its mesh puts it, at rest in model space: scaled and
     * turned about where its socket bone is on the rig's reference body, then moved. The axes are
     * the model's (x across, y up, z forward), not the bone's, so the placement means the same on
     * every skeleton, including ones moved onto the rig from another whose bones point other ways.
     *
     * @param offset How far it moves, in meters.
     * @param rotate How it turns about the x, y and z axes, in degrees, in that order.
     * @param scale How much it grows.
     */
    @Doc("Where an accessory sits on its socket, moved from where its mesh puts it.")
    public record Placement(
            @Doc("How far it moves, in meters: x across, y up, z forward.") @Optional Vec3 offset,
            @Doc(
                            "How it turns about the x, y and z axes through its socket, in degrees,"
                                    + " in that order.")
                    @Optional
                    Vec3 rotate,
            @Doc("How much it grows about its socket.") @Default("1") float scale) {
        /** Leaves the accessory where its mesh puts it. */
        public static final Placement NONE = new Placement(null, null, 1);

        /** The field for the scale. */
        public static final String FIELD_SCALE = "scale";

        /**
         * The placement as a transform in model space.
         *
         * @param socket Where the socket bone is at rest on the rig's reference body.
         * @param out Where to put it.
         * @return out.
         */
        public Matrix4f matrix(@NonNull Vector3fc socket, @NonNull Matrix4f out) {
            out.translation(socket);
            if (offset != null) {
                out.translate(offset.x(), offset.y(), offset.z());
            }
            if (rotate != null) {
                out.rotateXYZ(
                        (float) Math.toRadians(rotate.x()),
                        (float) Math.toRadians(rotate.y()),
                        (float) Math.toRadians(rotate.z()));
            }
            return out.scale(scale).translate(-socket.x(), -socket.y(), -socket.z());
        }
    }

    /**
     * Where this accessory sits on a variant.
     *
     * @param variant The variant.
     * @return Its placement for the variant, else for any variant, else {@link Placement#NONE}.
     */
    public Placement placementFor(@NonNull String variant) {
        Placement found = placement.get(variant);
        if (found == null) {
            found = placement.get(ANY_VARIANT);
        }
        return found == null ? Placement.NONE : found;
    }

    @Override
    public void check(Checks checks) {
        String planPath = JsonPath.at(FIELD_PLAN);
        BodyPlan body = checks.require(BodyPlan.class, plan, planPath);
        Rig rig = body == null ? null : checks.require(Rig.class, body.rig(), planPath);

        checkKind(checks);
        if (conflicts.contains(id)) {
            checks.error(JsonPath.at(FIELD_CONFLICTS), "A part can't conflict with itself");
        }
        checkMaterials(checks, body);
        if (body == null) {
            return;
        }

        for (int i = 0; i < fills.size(); ++i) {
            String name = fills.get(i);
            String path = JsonPath.at(FIELD_FILLS, i);
            if (kind == PartKind.BODY) {
                BodyPlan.Slot slot = body.slot(name);
                if (slot == null) {
                    checks.error(path, "The plan has no body slot '" + name + "'");
                } else if (mirror && slot.mirrorOf() == null) {
                    checks.error(path, "The slot '" + name + "' has no mirror to make");
                }
            } else if (kind == PartKind.LAYER && body.layer(name) == null) {
                checks.error(path, "The plan has no layer '" + name + "'");
            }
        }
        if (mirror && kind != PartKind.BODY) {
            checks.error(JsonPath.at(FIELD_MIRROR), "Only body parts are mirrored");
        }
        for (int i = 0; i < fits.size(); ++i) {
            String fit = fits.get(i);
            boolean found =
                    body.sockets().stream()
                            .anyMatch(s -> s.id().equals(fit) || fit.equals(s.type()));
            if (!found) {
                checks.error(
                        JsonPath.at(FIELD_FITS, i),
                        "The plan has no socket or socket type '" + fit + "'");
            }
        }
        if (extension != null && body.extension(extension) == null) {
            checks.error(
                    JsonPath.at(FIELD_EXTENSION), "The plan has no extension '" + extension + "'");
        }
        for (String variant : meshes.keySet()) {
            if (!ANY_VARIANT.equals(variant) && body.variant(variant) == null) {
                checks.error(
                        JsonPath.field(JsonPath.at(FIELD_MESHES), variant),
                        "The plan has no variant '" + variant + "'");
            }
        }
        for (int i = 0; i < hidesRegions.size(); ++i) {
            if (!body.regions().contains(hidesRegions.get(i))) {
                checks.error(
                        JsonPath.at(FIELD_HIDES_REGIONS, i),
                        "The plan has no region '" + hidesRegions.get(i) + "'");
            }
        }
        for (int i = 0; i < hidesLayers.size(); ++i) {
            if (body.layer(hidesLayers.get(i)) == null) {
                checks.error(
                        JsonPath.at(FIELD_HIDES_LAYERS, i),
                        "The plan has no layer '" + hidesLayers.get(i) + "'");
            }
        }
        sockets.forEach(
                (socket, move) -> {
                    String path = JsonPath.field(JsonPath.at(FIELD_SOCKETS), socket);
                    if (body.socket(socket) == null) {
                        checks.error(path, "The plan has no socket '" + socket + "'");
                    }
                    if (rig != null && rig.bone(move.bone()) == null) {
                        checks.error(
                                JsonPath.field(path, SocketMove.FIELD_BONE),
                                "The rig has no bone named '" + move.bone() + "'");
                    }
                });
        checkRelated(checks, conflicts, FIELD_CONFLICTS);
        checkRelated(checks, requires, FIELD_REQUIRES);
        for (int i = 0; i < fx.size(); ++i) {
            fx.get(i)
                    .check(
                            body,
                            rig,
                            (effect, path) -> checks.require(Fx.class, effect, path),
                            checks,
                            JsonPath.at(FIELD_FX, i));
        }
    }

    /**
     * Check that the fields used fit the kind of part.
     *
     * @param checks Where problems go.
     */
    private void checkKind(Checks checks) {
        switch (kind) {
            case BODY, LAYER -> {
                if (fills.isEmpty()) {
                    checks.error(
                            JsonPath.at(FIELD_FILLS),
                            "A " + DataFormat.enumName(kind) + " part must fill something");
                }
                if (!fits.isEmpty() || extension != null || !placement.isEmpty()) {
                    checks.error(
                            JsonPath.ROOT,
                            "Only accessories use "
                                    + FIELD_FITS
                                    + ", "
                                    + FIELD_EXTENSION
                                    + " and "
                                    + FIELD_PLACEMENT);
                }
            }
            case ACCESSORY -> {
                if (!fills.isEmpty()) {
                    checks.error(
                            JsonPath.at(FIELD_FILLS),
                            "Accessories go on sockets, so they don't fill anything");
                }
                if (fits.isEmpty()) {
                    checks.error(
                            JsonPath.at(FIELD_FITS),
                            "An accessory needs at least one socket it fits");
                }
                placement.forEach(
                        (variant, place) -> {
                            if (!(place.scale() > 0)) {
                                checks.error(
                                        JsonPath.field(
                                                JsonPath.field(
                                                        JsonPath.at(FIELD_PLACEMENT), variant),
                                                Placement.FIELD_SCALE),
                                        "A placement's scale is above 0");
                            }
                        });
            }
        }
    }

    /**
     * Check the material slots' templates and channels.
     *
     * @param checks Where problems go.
     * @param body The plan, or null if it is missing.
     */
    private void checkMaterials(Checks checks, BodyPlan body) {
        for (int i = 0; i < materialSlots.size(); ++i) {
            MaterialSlot slot = materialSlots.get(i);
            String path = JsonPath.at(FIELD_MATERIAL_SLOTS, i);
            checks.require(
                    MaterialTemplate.class,
                    slot.template(),
                    JsonPath.field(path, MaterialSlot.FIELD_TEMPLATE));
            String channelsPath = JsonPath.field(path, MaterialSlot.FIELD_CHANNELS);
            slot.channels()
                    .forEach(
                            (mask, channel) -> {
                                String channelPath = JsonPath.field(channelsPath, mask);
                                if (!MASK_CHANNELS.contains(mask)) {
                                    checks.error(
                                            channelPath,
                                            "Tint mask channels are r, g, b and a, not '"
                                                    + mask
                                                    + "'");
                                }
                                if (body != null && !body.hasChannel(channel)) {
                                    checks.error(
                                            channelPath,
                                            "The plan has no palette channel '" + channel + "'");
                                }
                            });
            String defaultsPath = JsonPath.field(path, MaterialSlot.FIELD_DEFAULTS);
            for (String mask : slot.defaults().keySet()) {
                if (!slot.channels().containsKey(mask)) {
                    checks.error(
                            JsonPath.field(defaultsPath, mask),
                            "The tint mask channel '" + mask + "' takes no palette channel");
                }
            }
        }
    }

    /**
     * Check that related parts exist and are for the same plan.
     *
     * @param checks Where problems go.
     * @param parts The related parts.
     * @param field The field they are listed in.
     */
    private void checkRelated(Checks checks, List<Id> parts, String field) {
        for (int i = 0; i < parts.size(); ++i) {
            String path = JsonPath.at(field, i);
            Part other = checks.require(Part.class, parts.get(i), path);
            if (other != null && !other.plan().equals(plan)) {
                checks.error(
                        path,
                        "'" + other.id() + "' is for the plan " + other.plan() + ", not " + plan);
            }
        }
    }
}
