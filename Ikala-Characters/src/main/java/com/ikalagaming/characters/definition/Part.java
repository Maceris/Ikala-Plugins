package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.Default;
import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;
import com.ikalagaming.characters.data.Scale;
import com.ikalagaming.characters.data.Vec3;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * @param extension The extension chain it is weighted to: the place of an accessory without
 *     sockets, or a chain a layer or socket accessory also moves with.
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
 * @param placement Where an accessory sits on its socket, by variant.
 * @param seams How a body part meets its neighbors at its outer seams.
 * @param pair The part for the other side, put on with this one.
 * @param locksSliders Sliders it locks while worn, by ID or editor group.
 * @param hidesSockets Sockets it covers, by name or type.
 * @param form For body parts, the plan's thinnest clothing form its surface fits in.
 * @param standoff How far it stands out over each body slot, as a share of the slot's radius, with
 *     "*" for any other slot: a body part from the bare body, a layer from the body part under it.
 *     Slots left out take a body part's form's share or a layer's layer's standoff.
 * @param covers For layers, the body slots it really covers, or empty for all its layers cover.
 * @param bare For body parts, whether it is worn bare: clothing covering its slots can't go over
 *     it.
 * @param chainLength For parts on an extension chain, how much longer its bones are than the rig's:
 *     one scale for every bone, or one per bone from the root out; empty for the rig's lengths.
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
        @Doc(
                        "The extension chain it is weighted to, like \"tail\": an accessory"
                                + " without sockets goes on the chain, and a layer, like a cape,"
                                + " or an accessory on a socket also moves with it.")
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
                Map<String, Placement> placement,
        @Doc(
                        "For body parts: how it meets its neighbors at its outer seams, by seam"
                                + " name. A seam left out welds.")
                @Optional
                Map<String, SeamMode> seams,
        @Doc(
                        "The part for the other side, like the right arm of a left arm, which"
                                + " editors put on along with this one unless sides are chosen"
                                + " alone.")
                @Optional
                Id pair,
        @Doc(
                        "Sliders this locks while worn, by slider ID or editor group, like"
                                + " \"face\" for a full helmet.")
                @Optional
                List<String> locksSliders,
        @Doc(
                        "Sockets this covers, by name or type, like \"face\" for a full helmet:"
                                + " accessories and effects there aren't shown.")
                @Optional
                List<String> hidesSockets,
        @Doc(
                        "For body parts: the plan's thinnest clothing form its surface fits in, like"
                                + " \"bulky\" for a robot leg, so layers over it show their mesh for"
                                + " that form. Left out, it is the plan's first, the bare body's.")
                @Optional
                String form,
        @Doc(
                        "How far it stands out over each body slot, as a share of the slot's"
                                + " radius, with \"*\" for any other slot: a body part from the"
                                + " bare body, which clothing over it is pushed out by, a layer"
                                + " from the body part under it. Accessories resting on the slot"
                                + " sit on both. Slots left out take a body part's form's share or"
                                + " a layer's layer's standoff.")
                @Optional
                Map<String, Float> standoff,
        @Doc(
                        "For layers: the body slots it really covers, like a t-shirt leaving the"
                                + " arms bare. Left out, all its layers cover.")
                @Optional
                List<String> covers,
        @Doc(
                        "For body parts: whether it is worn bare, like an arm covered in spikes:"
                                + " it can't be worn with layers covering its slots.")
                @Default("false")
                boolean bare,
        @Doc(
                        "For parts on an extension chain: how much longer its bones are than the"
                                + " rig's, one number for every bone or one per bone from the root"
                                + " out, so a long tail's mesh fits its own chain. Its mesh is made"
                                + " on the longer chain.")
                @Optional
                List<Float> chainLength)
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

    /** The field for the seam modes. */
    public static final String FIELD_SEAMS = "seams";

    /** The field for the other side's part. */
    public static final String FIELD_PAIR = "pair";

    /** The field for locked sliders. */
    public static final String FIELD_LOCKS_SLIDERS = "locksSliders";

    /** The field for hidden sockets. */
    public static final String FIELD_HIDES_SOCKETS = "hidesSockets";

    /** The field for the clothing form. */
    public static final String FIELD_FORM = "form";

    /** The field for the standoff. */
    public static final String FIELD_STANDOFF = "standoff";

    /** The field for the covered slots. */
    public static final String FIELD_COVERS = "covers";

    /** The field for being worn bare. */
    public static final String FIELD_BARE = "bare";

    /** The field for the chain length. */
    public static final String FIELD_CHAIN_LENGTH = "chainLength";

    /** The standoff key for any slot not given its own. */
    public static final String ANY_SLOT = "*";

    /** Joins a variant and a clothing form in a mesh key, like "female@bulky". */
    public static final String FORM_SEPARATOR = "@";

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

    /** How a body part meets its neighbor at a seam. */
    public enum SeamMode {
        /** Its edge is the seam ring, vertex for vertex, so it joins its neighbor without a gap. */
        WELD,
        /**
         * It ends in a shape of its own, like a skeleton arm's bone, and a welded neighbor closes
         * its ring with a cap.
         */
        OPEN
    }

    /**
     * The seams at the edges of the slots a body part fills that no other slot it fills shares:
     * where it meets its neighbors. A whole arm's are the shoulder only.
     *
     * @param body The plan.
     * @return The seams, in the order the slots list them.
     */
    public Set<String> outerSeams(@NonNull BodyPlan body) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (kind == PartKind.BODY) {
            for (String name : fills) {
                BodyPlan.Slot slot = body.slot(name);
                if (slot != null) {
                    slot.seams().forEach(seam -> counts.merge(seam, 1, Integer::sum));
                }
            }
        }
        Set<String> outer = new LinkedHashSet<>();
        counts.forEach(
                (seam, count) -> {
                    if (count == 1) {
                        outer.add(seam);
                    }
                });
        return outer;
    }

    /**
     * How this part meets its neighbor at a seam.
     *
     * @param seam The seam.
     * @return Its mode there, welding if it names none.
     */
    public SeamMode seamMode(@NonNull String seam) {
        return seams.getOrDefault(seam, SeamMode.WELD);
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
     * @param scale How much it grows along its turned x, y and z axes, so a hat can be made
     *     narrower without getting shorter.
     */
    @Doc("Where an accessory sits on its socket, moved from where its mesh puts it.")
    public record Placement(
            @Doc("How far it moves, in meters: x across, y up, z forward.") @Optional Vec3 offset,
            @Doc(
                            "How it turns about the x, y and z axes through its socket, in degrees,"
                                    + " in that order.")
                    @Optional
                    Vec3 rotate,
            @Doc(
                            "How much it grows about its socket: one number to grow evenly, or"
                                    + " [x, y, z] along its own turned axes.")
                    @Default("1")
                    Scale scale) {
        /** Leaves the accessory where its mesh puts it. */
        public static final Placement NONE = new Placement(null, null, Scale.ONE);

        /**
         * A placement that grows evenly.
         *
         * @param offset How far it moves, in meters.
         * @param rotate How it turns about the x, y and z axes, in degrees, in that order.
         * @param scale How much it grows.
         */
        public Placement(Vec3 offset, Vec3 rotate, float scale) {
            this(offset, rotate, Scale.of(scale));
        }

        /** The field for the scale. */
        public static final String FIELD_SCALE = "scale";

        /**
         * This placement moved further.
         *
         * @param by How much further, in meters, in model space.
         * @return The moved placement.
         */
        public Placement movedBy(@NonNull Vector3fc by) {
            Vec3 moved =
                    offset == null
                            ? new Vec3(by.x(), by.y(), by.z())
                            : new Vec3(
                                    offset.x() + by.x(), offset.y() + by.y(), offset.z() + by.z());
            return new Placement(moved, rotate, scale);
        }

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
            return out.scale(scale.x(), scale.y(), scale.z())
                    .translate(-socket.x(), -socket.y(), -socket.z());
        }
    }

    /**
     * Whether this is an accessory that goes on an extension chain rather than a socket: one with a
     * chain and no sockets. One with both goes on its socket and moves with the chain too.
     *
     * @return True for a tail, wing and the like.
     */
    public boolean onChain() {
        return kind == PartKind.ACCESSORY && extension != null && fits.isEmpty();
    }

    /**
     * Where this part is listed: the slots or layers it fills, its chain for an accessory on one,
     * or the sockets an accessory fits by name.
     *
     * @return The places, the one it is listed under first.
     */
    public List<String> places() {
        if (onChain()) {
            return List.of(extension);
        }
        return kind == PartKind.ACCESSORY ? fits : fills;
    }

    /**
     * How much longer each of its chain's bones is than the rig's.
     *
     * @param body The plan.
     * @return One scale per bone of its chain from the root out, or null if it has no chain, the
     *     plan lacks it, or its bones are the rig's length.
     */
    public float[] chainLengths(@NonNull BodyPlan body) {
        BodyPlan.Extension chain = extension == null ? null : body.extension(extension);
        if (chain == null || chainLength.isEmpty()) {
            return null;
        }
        float[] lengths = new float[chain.bones().size()];
        boolean longer = false;
        for (int i = 0; i < lengths.length; ++i) {
            Float scale = chainLength.get(chainLength.size() == 1 ? 0 : i % chainLength.size());
            lengths[i] = scale == null ? 1 : scale;
            longer |= lengths[i] != 1;
        }
        return longer ? lengths : null;
    }

    /**
     * How far this part stands out over a body slot, as it gives it.
     *
     * @param slot The body slot.
     * @return The distance in meters, or null if it gives none for the slot.
     */
    public Float standoffOver(@NonNull String slot) {
        Float found = standoff.get(slot);
        return found != null ? found : standoff.get(ANY_SLOT);
    }

    /**
     * The body slots this layer covers.
     *
     * @param plan The body plan.
     * @return Its own covered slots if it gives them, else those of the layers it fills; empty for
     *     parts that aren't layers.
     */
    public Set<String> coveredSlots(@NonNull BodyPlan plan) {
        if (kind != PartKind.LAYER) {
            return Set.of();
        }
        if (!covers.isEmpty()) {
            return new LinkedHashSet<>(covers);
        }
        Set<String> slots = new LinkedHashSet<>();
        for (String fill : fills) {
            BodyPlan.Layer layer = plan.layer(fill);
            if (layer != null) {
                slots.addAll(layer.covers());
            }
        }
        return slots;
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
        if (!seams.isEmpty() && kind != PartKind.BODY) {
            checks.error(JsonPath.at(FIELD_SEAMS), "Only body parts meet others at seams");
        } else {
            Set<String> outer = outerSeams(body);
            for (String seam : seams.keySet()) {
                if (!outer.contains(seam)) {
                    checks.error(
                            JsonPath.field(JsonPath.at(FIELD_SEAMS), seam),
                            "'"
                                    + seam
                                    + "' isn't a seam where this part meets a neighbor; its"
                                    + " outer seams are "
                                    + outer);
                }
            }
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
        BodyPlan.Extension chain = extension == null ? null : body.extension(extension);
        if (extension != null && chain == null) {
            checks.error(
                    JsonPath.at(FIELD_EXTENSION), "The plan has no extension '" + extension + "'");
        }
        checkChainLength(checks, chain);
        for (String key : meshes.keySet()) {
            int at = key.indexOf(FORM_SEPARATOR);
            String variant = at < 0 ? key : key.substring(0, at);
            String path = JsonPath.field(JsonPath.at(FIELD_MESHES), key);
            if (!ANY_VARIANT.equals(variant) && body.variant(variant) == null) {
                checks.error(path, "The plan has no variant '" + variant + "'");
            }
            if (at >= 0 && body.formIndex(key.substring(at + 1)) < 0) {
                checks.error(path, "The plan has no form '" + key.substring(at + 1) + "'");
            }
        }
        if (form != null) {
            if (kind != PartKind.BODY) {
                checks.error(JsonPath.at(FIELD_FORM), "Only body parts have a clothing form");
            } else if (body.formIndex(form) < 0) {
                checks.error(JsonPath.at(FIELD_FORM), "The plan has no form '" + form + "'");
            }
        }
        if (!standoff.isEmpty() && kind == PartKind.ACCESSORY) {
            checks.error(JsonPath.at(FIELD_STANDOFF), "Only body parts and layers have a standoff");
        }
        if (!covers.isEmpty() && kind != PartKind.LAYER) {
            checks.error(JsonPath.at(FIELD_COVERS), "Only layers cover slots of their own");
        }
        for (int i = 0; i < covers.size(); ++i) {
            if (body.slot(covers.get(i)) == null) {
                checks.error(
                        JsonPath.at(FIELD_COVERS, i),
                        "The plan has no body slot '" + covers.get(i) + "'");
            }
        }
        if (bare && kind != PartKind.BODY) {
            checks.error(JsonPath.at(FIELD_BARE), "Only body parts are worn bare");
        }
        standoff.forEach(
                (slot, meters) -> {
                    String path = JsonPath.at(FIELD_STANDOFF);
                    if (!ANY_SLOT.equals(slot) && body.slot(slot) == null) {
                        checks.error(path, "The plan has no body slot '" + slot + "'");
                    } else if (meters == null || meters < 0) {
                        checks.error(path, "A standoff can't be below 0");
                    }
                });
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
        checkPair(checks, body);
        List<Slider> allSliders = checks.all(Slider.class);
        for (int i = 0; i < locksSliders.size(); ++i) {
            String name = locksSliders.get(i);
            boolean known =
                    allSliders.stream()
                            .anyMatch(
                                    s -> name.equals(s.group()) || s.id().toString().equals(name));
            if (!known) {
                checks.error(
                        JsonPath.at(FIELD_LOCKS_SLIDERS, i),
                        "There is no slider or slider group '" + name + "'");
            }
        }
        for (int i = 0; i < hidesSockets.size(); ++i) {
            String hidden = hidesSockets.get(i);
            if (body.sockets().stream()
                    .noneMatch(s -> s.id().equals(hidden) || hidden.equals(s.type()))) {
                checks.error(
                        JsonPath.at(FIELD_HIDES_SOCKETS, i),
                        "The plan has no socket or socket type '" + hidden + "'");
            }
        }
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
                if (!fits.isEmpty() || !placement.isEmpty()) {
                    checks.error(
                            JsonPath.ROOT,
                            "Only accessories use " + FIELD_FITS + " and " + FIELD_PLACEMENT);
                }
                if (kind == PartKind.BODY && extension != null) {
                    checks.error(
                            JsonPath.at(FIELD_EXTENSION),
                            "Only accessories and layers move with an extension chain");
                }
            }
            case ACCESSORY -> {
                if (!fills.isEmpty()) {
                    checks.error(
                            JsonPath.at(FIELD_FILLS),
                            "Accessories go on sockets or chains, so they don't fill anything");
                }
                if (fits.isEmpty() && extension == null) {
                    checks.error(
                            JsonPath.at(FIELD_FITS),
                            "An accessory needs a socket it fits or an extension chain");
                }
                if (onChain() && !placement.isEmpty()) {
                    checks.error(
                            JsonPath.at(FIELD_PLACEMENT),
                            "An accessory on a chain is placed by its chain, not a placement");
                }
                placement.forEach(
                        (variant, place) -> {
                            if (!place.scale().isPositive()) {
                                checks.error(
                                        JsonPath.field(
                                                JsonPath.field(
                                                        JsonPath.at(FIELD_PLACEMENT), variant),
                                                Placement.FIELD_SCALE),
                                        "A placement's scale is above 0 along every axis");
                            }
                        });
            }
        }
    }

    /**
     * Check the chain length: only for parts on a chain, one scale or one per bone, each above 0.
     *
     * @param checks Where problems go.
     * @param chain The part's chain, or null if it has none or the plan lacks it.
     */
    private void checkChainLength(Checks checks, BodyPlan.Extension chain) {
        if (chainLength.isEmpty()) {
            return;
        }
        String path = JsonPath.at(FIELD_CHAIN_LENGTH);
        if (extension == null) {
            checks.error(path, "Only parts on an extension chain set its length");
            return;
        }
        if (chain != null
                && chainLength.size() != 1
                && chainLength.size() != chain.bones().size()) {
            checks.error(
                    path,
                    "Give one scale, or one for each of the chain's "
                            + chain.bones().size()
                            + " bones");
        }
        for (int i = 0; i < chainLength.size(); ++i) {
            Float scale = chainLength.get(i);
            if (scale == null || scale <= 0) {
                checks.error(JsonPath.at(FIELD_CHAIN_LENGTH, i), "A chain length is above 0");
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
     * Check that the other side's part exists, is the same kind for the same plan, names this one
     * back if it names any, and goes in the mirrored places.
     *
     * @param checks Where problems go.
     * @param body The plan.
     */
    private void checkPair(Checks checks, BodyPlan body) {
        if (pair == null) {
            return;
        }
        String path = JsonPath.at(FIELD_PAIR);
        if (pair.equals(id)) {
            checks.error(path, "A part can't be its own pair");
            return;
        }
        Part other = checks.require(Part.class, pair, path);
        if (other == null) {
            return;
        }
        if (other.kind() != kind || !other.plan().equals(plan)) {
            checks.error(path, "'" + pair + "' isn't the same kind of part for the same plan");
            return;
        }
        if (other.pair() != null && !other.pair().equals(id)) {
            checks.error(path, "'" + pair + "' pairs with " + other.pair() + " instead");
        }
        List<String> places = places();
        List<String> otherPlaces = other.places();
        Set<String> expected = new LinkedHashSet<>();
        places.forEach(place -> expected.add(mirrored(body, place)));
        if (!new LinkedHashSet<>(otherPlaces).equals(expected)) {
            checks.error(
                    path,
                    "'"
                            + pair
                            + "' should go in the other side's places "
                            + expected
                            + ", not "
                            + otherPlaces);
        }
    }

    /**
     * The place on the other side: a slot's mirror, else the name with .L and .R swapped, else the
     * place itself, for places in the middle.
     *
     * @param body The plan.
     * @param place A slot, layer or socket name or type.
     * @return The mirrored place.
     */
    public static String mirrored(@NonNull BodyPlan body, @NonNull String place) {
        BodyPlan.Slot slot = body.slot(place);
        if (slot != null && slot.mirrorOf() != null) {
            return slot.mirrorOf();
        }
        if (place.endsWith(".L")) {
            return place.substring(0, place.length() - 2) + ".R";
        }
        if (place.endsWith(".R")) {
            return place.substring(0, place.length() - 2) + ".L";
        }
        return place;
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
