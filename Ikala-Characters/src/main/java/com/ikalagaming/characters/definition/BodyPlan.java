package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Default;
import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;
import com.ikalagaming.characters.data.Range;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A rig plus everything the editor and animation need: variants, body slots, clothing layers,
 * sockets, extension chains, sliders and IK setup. Plans are the unit of reuse, so any part or clip
 * made for a plan works on every character of that plan.
 *
 * @param id This plan's ID.
 * @param rig The rig it is built on.
 * @param variants Its variants, like male and female.
 * @param slots Its body slots.
 * @param layers Its clothing and armor layers.
 * @param sockets Its attach points.
 * @param equipment Its equipment slots, which items go in.
 * @param extensions Its optional bone chains.
 * @param sliders The sliders it uses, by ID pattern.
 * @param channels Palette channels beyond the standard ones.
 * @param regions Mask region names.
 * @param boneGroups Named sets of bones, for animation masks.
 * @param ik IK setup.
 * @param collision How its collision capsule is placed.
 * @author Ches Burks
 */
@Doc(
        "A rig plus the variants, body slots, clothing layers, sockets, extension chains, sliders"
                + " and IK setup built on it. Any part or clip made for a plan works on every"
                + " character of that plan.")
public record BodyPlan(
        @Doc("This plan's ID, like \"ikala:biped\".") Id id,
        @Doc("The rig it is built on.") Id rig,
        @Doc("Its variants, like male and female, which share the rig's bones.")
                List<Variant> variants,
        @Doc("Body slots, which body parts fill, joined at seam rings.") @Optional List<Slot> slots,
        @Doc("Clothing and armor layers, which overlap body slots and hide what they cover.")
                @Optional
                List<Layer> layers,
        @Doc("Attach points for accessories and held items.") @Optional List<Socket> sockets,
        @Doc(
                        "Equipment slots, which gameplay items go in, each drawn in a layer or held"
                                + " in a socket. Earlier slots' item styles come first.")
                @Optional
                List<EquipmentSlot> equipment,
        @Doc("Optional bone chains, like a tail, wings or a cape.") @Optional
                List<Extension> extensions,
        @Doc(
                        "The sliders it uses, as IDs or patterns ending in *, like"
                                + " \"ikala:slider/biped/*\".")
                @Optional
                List<String> sliders,
        @Doc(
                        "Palette channels beyond the standard ones (skin, eyes, hair, primary,"
                                + " secondary, tertiary, accent), like \"fur\".")
                @Optional
                List<String> channels,
        @Doc(
                        "Mask region names that body meshes tag their triangles with, so layers"
                                + " can hide them. At most 64.")
                @Optional
                List<String> regions,
        @Doc("Named sets of bones, for masking animation layers, like \"upperBody\".") @Optional
                Map<String, List<String>> boneGroups,
        @Doc("IK setup.") @Optional Ik ik,
        @Doc("How the collision capsule is placed.") @Optional Collision collision)
        implements Definition {

    /** The field for the rig. */
    public static final String FIELD_RIG = "rig";

    /** The field for the variants. */
    public static final String FIELD_VARIANTS = "variants";

    /** The field for the body slots. */
    public static final String FIELD_SLOTS = "slots";

    /** The field for the layers. */
    public static final String FIELD_LAYERS = "layers";

    /** The field for the sockets. */
    public static final String FIELD_SOCKETS = "sockets";

    /** The field for the extension chains. */
    public static final String FIELD_EXTENSIONS = "extensions";

    /** The field for the equipment slots. */
    public static final String FIELD_EQUIPMENT = "equipment";

    /** The field for the slider patterns. */
    public static final String FIELD_SLIDERS = "sliders";

    /** The field for the extra palette channels. */
    public static final String FIELD_CHANNELS = "channels";

    /** The field for the mask regions. */
    public static final String FIELD_REGIONS = "regions";

    /** The field for the bone groups. */
    public static final String FIELD_BONE_GROUPS = "boneGroups";

    /** The field for the IK setup. */
    public static final String FIELD_IK = "ik";

    /** The field for the collision capsule. */
    public static final String FIELD_COLLISION = "collision";

    /** The most mask regions a plan can have, one per bit of a 64 bit mask. */
    public static final int MAX_REGIONS = 64;

    /**
     * A variant, which shares the plan's bones with its own proportions and limits.
     *
     * @param id The variant's name.
     * @param tags Tags of the parts it allows, or empty to allow any.
     * @param sliders Slider defaults and ranges that differ from the slider's own.
     * @param parts The part each body slot or layer starts with, by slot or layer name.
     */
    @Doc("A variant, sharing the plan's bones with its own proportions and limits.")
    public record Variant(
            @Doc("The variant's name, like \"female\".") String id,
            @Doc("Parts must have one of these tags to be used on it. Empty allows any part.")
                    @Optional
                    List<String> tags,
            @Doc("Slider settings for this variant, by slider ID.") @Optional
                    Map<String, SliderSetting> sliders,
            @Doc(
                            "The part each body slot or layer starts with, by slot or layer name,"
                                    + " which designs that leave a slot out fall back to. Slots"
                                    + " left out here start empty.")
                    @Optional
                    Map<String, Id> parts) {
        /** The field for the slider settings. */
        public static final String FIELD_SLIDERS = "sliders";

        /** The field for the default parts. */
        public static final String FIELD_PARTS = "parts";

        /**
         * Whether a part may be used on this variant, by its tags.
         *
         * @param part The part.
         * @return True if the variant allows any part, or the part has one of its tags.
         */
        public boolean allows(@NonNull Part part) {
            return tags.isEmpty() || part.tags().stream().anyMatch(tags::contains);
        }

        /**
         * The values a slider may take on this variant: its own range for a context, narrowed by
         * the variant's setting for it.
         *
         * @param slider The slider.
         * @param context The context, like "player".
         * @return The range.
         */
        public Range range(@NonNull Slider slider, @NonNull String context) {
            Range own = slider.range(context);
            SliderSetting setting = sliders.get(slider.id().toString());
            if (setting == null || setting.range() == null) {
                return own;
            }
            return new Range(
                    Math.max(own.min(), setting.range().min()),
                    Math.min(own.max(), setting.range().max()));
        }
    }

    /**
     * A variant's setting for one slider.
     *
     * @param initial The value it starts at.
     * @param range The values allowed, within the slider's own.
     */
    @Doc("A variant's setting for one slider.")
    public record SliderSetting(
            @Doc("The value it starts at.") @Default("0") float initial,
            @Doc("The values allowed, as [min, max], within the slider's own.") @Optional
                    Range range) {}

    /**
     * A body slot, which one body part fills.
     *
     * @param id The slot's name.
     * @param bones The bones a part in this slot may weight.
     * @param seams The seam rings at its edges.
     * @param mirrorOf The slot on the other side.
     * @param optional Whether it may be left empty, like a tail.
     */
    @Doc("A body slot, which one body part fills.")
    public record Slot(
            @Doc("The slot's name, like \"lowerArm.L\".") String id,
            @Doc("The bones a part in this slot may weight.") List<String> bones,
            @Doc("The seam rings at its edges, shared with neighboring slots.") @Optional
                    List<String> seams,
            @Doc("The matching slot on the other side, for mirrored parts.") @Optional
                    String mirrorOf,
            @Doc("Whether the slot may be left empty, like a tail.") @Default("false")
                    boolean optional) {
        /** The field for the bones. */
        public static final String FIELD_BONES = "bones";

        /** The field for the mirrored slot. */
        public static final String FIELD_MIRROR_OF = "mirrorOf";
    }

    /**
     * A clothing or armor layer.
     *
     * @param id The layer's name.
     * @param covers The body slots it covers.
     * @param order Where it stacks; higher goes over lower.
     */
    @Doc("A clothing or armor layer.")
    public record Layer(
            @Doc("The layer's name, like \"gloves\".") String id,
            @Doc("The body slots it covers.") @Optional List<String> covers,
            @Doc("Where it stacks; higher goes over lower, like shirt 10 < jacket 20.")
                    @Default("0")
                    int order) {
        /** The field for the covered slots. */
        public static final String FIELD_COVERS = "covers";
    }

    /**
     * An attach point.
     *
     * @param id The socket's name.
     * @param bone The bone it is on.
     * @param max How many items it holds.
     * @param type Its type tag, which items fit by.
     */
    @Doc("An attach point.")
    public record Socket(
            @Doc("The socket's name, like \"back\".") String id,
            @Doc("The bone it is on, like \"socket.back\".") String bone,
            @Doc("How many items it holds at once.") @Default("1") int max,
            @Doc("Its type tag, which items fit by, like \"grip.1h\" or \"back.large\".") @Optional
                    String type) {
        /** The field for the bone. */
        public static final String FIELD_BONE = "bone";

        /** The field for the item count. */
        public static final String FIELD_MAX = "max";
    }

    /**
     * A gameplay equipment slot: where an item in it is drawn on this plan.
     *
     * @param id The slot's name.
     * @param layer The layer items in it are drawn in, or null for held items.
     * @param sockets The sockets or socket types items in it can be held in.
     */
    @Doc("An equipment slot, like \"head\" or \"mainHand\", and where its items are drawn.")
    public record EquipmentSlot(
            @Doc("The slot's name, like \"mainHand\".") String id,
            @Doc("The layer items in it are drawn in, like \"helmet\".") @Optional String layer,
            @Doc(
                            "The sockets, or socket types, items in it can be held in, like"
                                    + " \"grip.R\" or \"back.large\".")
                    @Optional
                    List<String> sockets) {
        /** The field for the layer. */
        public static final String FIELD_LAYER = "layer";

        /** The field for the sockets. */
        public static final String FIELD_SOCKETS = "sockets";
    }

    /**
     * An optional chain of bones.
     *
     * @param id The chain's name.
     * @param root The bone it hangs from.
     * @param bones Its bones, from the root outward.
     */
    @Doc("An optional bone chain, skipped when no part uses it.")
    public record Extension(
            @Doc("The chain's name, like \"cape\".") String id,
            @Doc("The bone it hangs from.") String root,
            @Doc("Its bones, from the root outward, each the next one's parent.")
                    List<String> bones) {
        /** The field for the bones. */
        public static final String FIELD_BONES = "bones";
    }

    /**
     * IK setup.
     *
     * @param feet The leg chains that foot IK plants.
     * @param lookAt The bones that turn to look, with weights.
     */
    @Doc("IK setup.")
    public record Ik(
            @Doc("The leg chains that foot IK plants.") @Optional List<Leg> feet,
            @Doc("The bones that turn to look at a target, and how much each turns.") @Optional
                    List<LookAt> lookAt) {
        /** The field for the legs. */
        public static final String FIELD_FEET = "feet";

        /** The field for the look-at bones. */
        public static final String FIELD_LOOK_AT = "lookAt";
    }

    /**
     * A leg for foot IK.
     *
     * @param chain The bones from hip to foot.
     */
    @Doc("A leg for foot IK.")
    public record Leg(
            @Doc("The bones from the hip to the foot, each the next one's parent.")
                    List<String> chain) {
        /** The field for the chain. */
        public static final String FIELD_CHAIN = "chain";
    }

    /**
     * One bone's share of looking at a target.
     *
     * @param bone The bone.
     * @param weight How much of the turn it takes.
     */
    @Doc("One bone's share of looking at a target.")
    public record LookAt(
            @Doc("The bone.") String bone,
            @Doc("How much of the turn it takes, 0 to 1.") float weight) {
        /** The field for the bone. */
        public static final String FIELD_BONE = "bone";

        /** The field for the weight. */
        public static final String FIELD_WEIGHT = "weight";
    }

    /**
     * How the collision capsule is placed.
     *
     * @param capsuleFromBones The two bones the capsule spans.
     * @param radiusScale The radius as a share of the capsule's height.
     */
    @Doc("How the collision capsule is placed.")
    public record Collision(
            @Doc("The two bones the capsule spans, bottom first.") List<String> capsuleFromBones,
            @Doc("The radius as a share of the capsule's height.") @Default("0.35")
                    float radiusScale) {
        /** The field for the capsule's bones. */
        public static final String FIELD_CAPSULE_FROM_BONES = "capsuleFromBones";

        /** The field for the radius. */
        public static final String FIELD_RADIUS_SCALE = "radiusScale";
    }

    /**
     * Every palette channel the plan has: the standard ones, then its own, in the order tint colors
     * and design bytes number them.
     *
     * @return The channels.
     */
    public List<String> allChannels() {
        List<String> all = new ArrayList<>(Palette.STANDARD_CHANNELS);
        channels.stream().filter(c -> !all.contains(c)).forEach(all::add);
        return all;
    }

    /**
     * Find a variant.
     *
     * @param name The variant's name.
     * @return The variant, or null if there is none by that name.
     */
    public Variant variant(@NonNull String name) {
        return variants.stream().filter(v -> v.id().equals(name)).findFirst().orElse(null);
    }

    /**
     * Find a body slot.
     *
     * @param name The slot's name.
     * @return The slot, or null if there is none by that name.
     */
    public Slot slot(@NonNull String name) {
        return slots.stream().filter(s -> s.id().equals(name)).findFirst().orElse(null);
    }

    /**
     * Find a layer.
     *
     * @param name The layer's name.
     * @return The layer, or null if there is none by that name.
     */
    public Layer layer(@NonNull String name) {
        return layers.stream().filter(l -> l.id().equals(name)).findFirst().orElse(null);
    }

    /**
     * Find a socket.
     *
     * @param name The socket's name.
     * @return The socket, or null if there is none by that name.
     */
    public Socket socket(@NonNull String name) {
        return sockets.stream().filter(s -> s.id().equals(name)).findFirst().orElse(null);
    }

    /**
     * Find an equipment slot.
     *
     * @param name The slot's name.
     * @return The slot, or null if there is none by that name.
     */
    public EquipmentSlot equipmentSlot(@NonNull String name) {
        return equipment.stream().filter(e -> e.id().equals(name)).findFirst().orElse(null);
    }

    /**
     * Find the sockets a name picks: the socket by that name, or every socket of that type.
     *
     * @param nameOrType A socket's name or a socket type.
     * @return The sockets, maybe none.
     */
    public List<Socket> socketsFor(@NonNull String nameOrType) {
        Socket named = socket(nameOrType);
        if (named != null) {
            return List.of(named);
        }
        return sockets.stream().filter(s -> nameOrType.equals(s.type())).toList();
    }

    /**
     * Find an extension chain.
     *
     * @param name The chain's name.
     * @return The chain, or null if there is none by that name.
     */
    public Extension extension(@NonNull String name) {
        return extensions.stream().filter(e -> e.id().equals(name)).findFirst().orElse(null);
    }

    /**
     * Whether a palette channel can be used on this plan's parts.
     *
     * @param channel The channel's name.
     * @return Whether it is a standard channel or one of this plan's.
     */
    public boolean hasChannel(@NonNull String channel) {
        return Palette.STANDARD_CHANNELS.contains(channel) || channels.contains(channel);
    }

    /**
     * The sliders this plan uses.
     *
     * @param candidates The sliders to pick from, like all of a registry's.
     * @return The sliders whose IDs match this plan's patterns.
     */
    public List<Slider> matchingSliders(@NonNull List<Slider> candidates) {
        List<Slider> matched = new ArrayList<>();
        for (Slider slider : candidates) {
            if (sliders.stream().anyMatch(pattern -> slider.id().matches(pattern))) {
                matched.add(slider);
            }
        }
        return matched;
    }

    @Override
    public void check(Checks checks) {
        Rig rigDefinition = checks.require(Rig.class, rig, JsonPath.at(FIELD_RIG));
        // Every name must still be unique without the rig, but bones can only be checked with it
        BoneCheck bones =
                rigDefinition == null
                        ? (name, path) -> {}
                        : (name, path) -> {
                            if (rigDefinition.bone(name) == null) {
                                checks.error(path, "The rig has no bone named '" + name + "'");
                            }
                        };

        checkVariants(checks);
        Set<String> slotNames = checkSlots(checks, bones);
        checkLayers(checks, slotNames);
        checkSockets(checks, bones);
        checkEquipment(checks);
        checkExtensions(checks, rigDefinition, bones);
        checkNames(checks);

        String groupsPath = JsonPath.at(FIELD_BONE_GROUPS);
        boneGroups.forEach(
                (group, members) -> {
                    String groupPath = JsonPath.field(groupsPath, group);
                    for (int i = 0; i < members.size(); ++i) {
                        bones.check(members.get(i), JsonPath.index(groupPath, i));
                    }
                });

        if (ik != null) {
            checkIk(checks, rigDefinition, bones);
        }
        if (collision != null) {
            checkCollision(checks, bones);
        }
        checkSliders(checks, rigDefinition);
    }

    /**
     * Check the variants and their slider settings.
     *
     * @param checks Where problems go.
     */
    private void checkVariants(Checks checks) {
        if (variants.isEmpty()) {
            checks.error(JsonPath.at(FIELD_VARIANTS), "A plan needs at least one variant");
        }
        Set<String> names = new HashSet<>();
        Set<String> sliderIds = new HashSet<>();
        matchingSliders(checks.all(Slider.class))
                .forEach(slider -> sliderIds.add(slider.id().toString()));
        for (int i = 0; i < variants.size(); ++i) {
            Variant variant = variants.get(i);
            String path = JsonPath.at(FIELD_VARIANTS, i);
            unique(checks, names, variant.id(), JsonPath.field(path, FIELD_ID), "variant");
            String settingsPath = JsonPath.field(path, Variant.FIELD_SLIDERS);
            variant.sliders()
                    .forEach(
                            (slider, setting) -> {
                                String settingPath = JsonPath.field(settingsPath, slider);
                                if (!sliderIds.contains(slider)) {
                                    checks.error(
                                            settingPath,
                                            "'" + slider + "' isn't one of this plan's sliders");
                                }
                                if (setting.range() != null
                                        && !setting.range().contains(setting.initial())) {
                                    checks.error(
                                            settingPath,
                                            "The initial value is outside the variant's range");
                                }
                            });
            checkDefaultParts(checks, variant, JsonPath.field(path, Variant.FIELD_PARTS));
        }
    }

    /**
     * Check a variant's default parts: each fills the slot or layer it is listed under, is for this
     * plan, and is allowed on the variant. Problems with them are warnings, so a broken part leaves
     * its slot empty rather than taking the whole plan, and every part on it, down too.
     *
     * @param checks Where problems go.
     * @param variant The variant.
     * @param partsPath Where its default parts are.
     */
    private void checkDefaultParts(Checks checks, Variant variant, String partsPath) {
        Map<Id, Part> usable = new HashMap<>();
        checks.all(Part.class).forEach(part -> usable.put(part.id(), part));
        variant.parts()
                .forEach(
                        (place, partId) -> {
                            String path = JsonPath.field(partsPath, place);
                            if (slot(place) == null && layer(place) == null) {
                                checks.error(
                                        path, "The plan has no body slot or layer '" + place + "'");
                                return;
                            }
                            Part part = usable.get(partId);
                            if (part == null) {
                                checks.warning(
                                        path,
                                        partId
                                                + " isn't loaded or has errors, so '"
                                                + place
                                                + "' starts empty");
                            } else if (!part.plan().equals(id)) {
                                checks.warning(path, partId + " is for " + part.plan());
                            } else if (!part.fills().contains(place)) {
                                checks.warning(path, partId + " doesn't fill '" + place + "'");
                            } else if (!variant.allows(part)) {
                                checks.warning(
                                        path,
                                        partId
                                                + " has none of the variant's tags "
                                                + variant.tags());
                            }
                        });
    }

    /**
     * Check the body slots.
     *
     * @param checks Where problems go.
     * @param bones Checks that bones exist.
     * @return The slots' names.
     */
    private Set<String> checkSlots(Checks checks, BoneCheck bones) {
        Set<String> names = new HashSet<>();
        Map<String, String> boneOwners = new HashMap<>();
        for (int i = 0; i < slots.size(); ++i) {
            Slot slot = slots.get(i);
            String path = JsonPath.at(FIELD_SLOTS, i);
            String bonesPath = JsonPath.field(path, Slot.FIELD_BONES);
            unique(checks, names, slot.id(), JsonPath.field(path, FIELD_ID), "slot");
            if (slot.bones().isEmpty()) {
                checks.error(bonesPath, "A slot needs at least one bone");
            }
            for (int b = 0; b < slot.bones().size(); ++b) {
                String bone = slot.bones().get(b);
                String bonePath = JsonPath.index(bonesPath, b);
                bones.check(bone, bonePath);
                String owner = boneOwners.putIfAbsent(bone, slot.id());
                if (owner != null) {
                    checks.warning(
                            bonePath,
                            "The bone '" + bone + "' is also in the slot '" + owner + "'");
                }
            }
            if (slot.mirrorOf() != null) {
                String mirrorPath = JsonPath.field(path, Slot.FIELD_MIRROR_OF);
                Slot other = slot(slot.mirrorOf());
                if (other == null) {
                    checks.error(mirrorPath, "There is no slot '" + slot.mirrorOf() + "'");
                } else if (other.mirrorOf() != null && !slot.id().equals(other.mirrorOf())) {
                    checks.error(
                            mirrorPath,
                            "'"
                                    + other.id()
                                    + "' mirrors '"
                                    + other.mirrorOf()
                                    + "', not this slot");
                }
            }
        }
        return names;
    }

    /**
     * Check the layers.
     *
     * @param checks Where problems go.
     * @param slotNames The body slots' names, which layer names mustn't reuse.
     */
    private void checkLayers(Checks checks, Set<String> slotNames) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < layers.size(); ++i) {
            Layer layer = layers.get(i);
            String path = JsonPath.at(FIELD_LAYERS, i);
            String idPath = JsonPath.field(path, FIELD_ID);
            unique(checks, names, layer.id(), idPath, "layer");
            if (slotNames.contains(layer.id())) {
                checks.error(idPath, "'" + layer.id() + "' is already a slot's name");
            }
            String coversPath = JsonPath.field(path, Layer.FIELD_COVERS);
            for (int c = 0; c < layer.covers().size(); ++c) {
                if (!slotNames.contains(layer.covers().get(c))) {
                    checks.error(
                            JsonPath.index(coversPath, c),
                            "There is no slot '" + layer.covers().get(c) + "'");
                }
            }
        }
    }

    /**
     * Check the sockets.
     *
     * @param checks Where problems go.
     * @param bones Checks that bones exist.
     */
    private void checkSockets(Checks checks, BoneCheck bones) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < sockets.size(); ++i) {
            Socket socket = sockets.get(i);
            String path = JsonPath.at(FIELD_SOCKETS, i);
            unique(checks, names, socket.id(), JsonPath.field(path, FIELD_ID), "socket");
            bones.check(socket.bone(), JsonPath.field(path, Socket.FIELD_BONE));
            if (socket.max() < 1) {
                checks.error(
                        JsonPath.field(path, Socket.FIELD_MAX),
                        "A socket must hold at least 1 item");
            }
        }
    }

    /**
     * Check the equipment slots: unique names, and layers and sockets the plan has.
     *
     * @param checks Where problems go.
     */
    private void checkEquipment(Checks checks) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < equipment.size(); ++i) {
            EquipmentSlot slot = equipment.get(i);
            String path = JsonPath.at(FIELD_EQUIPMENT, i);
            unique(checks, names, slot.id(), JsonPath.field(path, FIELD_ID), "equipment slot");
            if (slot.layer() != null && layer(slot.layer()) == null) {
                checks.error(
                        JsonPath.field(path, EquipmentSlot.FIELD_LAYER),
                        "The plan has no layer '" + slot.layer() + "'");
            }
            for (int s = 0; s < slot.sockets().size(); ++s) {
                String socket = slot.sockets().get(s);
                if (socketsFor(socket).isEmpty()) {
                    checks.error(
                            JsonPath.index(JsonPath.field(path, EquipmentSlot.FIELD_SOCKETS), s),
                            "The plan has no socket or socket type '" + socket + "'");
                }
            }
            if (slot.layer() == null && slot.sockets().isEmpty()) {
                checks.error(path, "An equipment slot needs a layer or sockets");
            }
        }
    }

    /**
     * Check the extension chains.
     *
     * @param checks Where problems go.
     * @param rigDefinition The rig, or null if it is missing.
     * @param bones Checks that bones exist.
     */
    private void checkExtensions(Checks checks, Rig rigDefinition, BoneCheck bones) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < extensions.size(); ++i) {
            Extension extension = extensions.get(i);
            String path = JsonPath.at(FIELD_EXTENSIONS, i);
            unique(checks, names, extension.id(), JsonPath.field(path, FIELD_ID), "extension");
            if (extension.bones().isEmpty()) {
                checks.error(
                        JsonPath.field(path, Extension.FIELD_BONES),
                        "An extension needs at least one bone");
            }
            List<String> chain = new ArrayList<>();
            chain.add(extension.root());
            chain.addAll(extension.bones());
            checkChain(checks, rigDefinition, chain, path, bones);
        }
    }

    /**
     * Check the channel and region names.
     *
     * @param checks Where problems go.
     */
    private void checkNames(Checks checks) {
        Set<String> channelNames = new HashSet<>();
        for (int i = 0; i < channels.size(); ++i) {
            String channel = channels.get(i);
            String path = JsonPath.at(FIELD_CHANNELS, i);
            if (Palette.STANDARD_CHANNELS.contains(channel)) {
                checks.warning(
                        path, "'" + channel + "' is a standard channel, so it needn't be listed");
            }
            unique(checks, channelNames, channel, path, "channel");
        }

        Set<String> regionNames = new HashSet<>();
        for (int i = 0; i < regions.size(); ++i) {
            unique(checks, regionNames, regions.get(i), JsonPath.at(FIELD_REGIONS, i), "region");
        }
        if (regions.size() > MAX_REGIONS) {
            checks.error(
                    JsonPath.at(FIELD_REGIONS),
                    "A plan can have at most " + MAX_REGIONS + " regions, not " + regions.size());
        }
    }

    /**
     * Check the IK setup.
     *
     * @param checks Where problems go.
     * @param rigDefinition The rig, or null if it is missing.
     * @param bones Checks that bones exist.
     */
    private void checkIk(Checks checks, Rig rigDefinition, BoneCheck bones) {
        String ikPath = JsonPath.at(FIELD_IK);
        String feetPath = JsonPath.field(ikPath, Ik.FIELD_FEET);
        for (int i = 0; i < ik.feet().size(); ++i) {
            List<String> chain = ik.feet().get(i).chain();
            String path = JsonPath.index(feetPath, i);
            if (chain.size() < 2) {
                checks.error(
                        JsonPath.field(path, Leg.FIELD_CHAIN), "A leg needs at least two bones");
            }
            checkChain(checks, rigDefinition, chain, path, bones);
        }
        String lookPath = JsonPath.field(ikPath, Ik.FIELD_LOOK_AT);
        for (int i = 0; i < ik.lookAt().size(); ++i) {
            LookAt look = ik.lookAt().get(i);
            String path = JsonPath.index(lookPath, i);
            bones.check(look.bone(), JsonPath.field(path, LookAt.FIELD_BONE));
            if (look.weight() < 0 || look.weight() > 1) {
                checks.error(JsonPath.field(path, LookAt.FIELD_WEIGHT), "Weights go from 0 to 1");
            }
        }
    }

    /**
     * Check the collision capsule.
     *
     * @param checks Where problems go.
     * @param bones Checks that bones exist.
     */
    private void checkCollision(Checks checks, BoneCheck bones) {
        String path = JsonPath.at(FIELD_COLLISION);
        String endsPath = JsonPath.field(path, Collision.FIELD_CAPSULE_FROM_BONES);
        List<String> ends = collision.capsuleFromBones();
        if (ends.size() != 2) {
            checks.error(endsPath, "The capsule spans exactly two bones, not " + ends.size());
        }
        for (int i = 0; i < ends.size(); ++i) {
            bones.check(ends.get(i), JsonPath.index(endsPath, i));
        }
        if (collision.radiusScale() <= 0) {
            checks.error(
                    JsonPath.field(path, Collision.FIELD_RADIUS_SCALE),
                    "The radius must be above 0");
        }
    }

    /**
     * Check the sliders this plan uses against its rig.
     *
     * @param checks Where problems go.
     * @param rigDefinition The rig, or null if it is missing.
     */
    private void checkSliders(Checks checks, Rig rigDefinition) {
        List<Slider> matched = matchingSliders(checks.all(Slider.class));
        for (int i = 0; i < sliders.size(); ++i) {
            String pattern = sliders.get(i);
            if (matched.stream().noneMatch(slider -> slider.id().matches(pattern))) {
                checks.warning(
                        JsonPath.at(FIELD_SLIDERS, i), "No slider matches '" + pattern + "'");
            }
        }
        if (rigDefinition == null) {
            return;
        }
        for (Slider slider : matched) {
            int pattern = 0;
            while (!slider.id().matches(sliders.get(pattern))) {
                ++pattern;
            }
            String where = JsonPath.at(FIELD_SLIDERS, pattern);
            String which = "The slider " + slider.id();
            for (Slider.Op op : slider.ops()) {
                for (String name : op.bones()) {
                    Rig.Bone bone = rigDefinition.bone(name);
                    if (bone == null) {
                        checks.error(where, which + " uses '" + name + "', which the rig lacks");
                    } else if (bone.kind() == Rig.BoneKind.SOCKET) {
                        checks.error(where, which + " scales the socket bone '" + name + "'");
                    } else if (op.type() == Slider.OpType.LENGTH && bone.parent() == null) {
                        checks.error(
                                where, which + " changes the length of the root '" + name + "'");
                    }
                }
            }
        }
    }

    /**
     * Check that bones form a chain, each the next one's parent.
     *
     * @param checks Where problems go.
     * @param rigDefinition The rig, or null if it is missing.
     * @param chain The bones, from the top down.
     * @param path Where the chain is.
     * @param bones Checks that bones exist.
     */
    private static void checkChain(
            Checks checks, Rig rigDefinition, List<String> chain, String path, BoneCheck bones) {
        for (String bone : chain) {
            bones.check(bone, path);
        }
        if (rigDefinition == null) {
            return;
        }
        for (int i = 1; i < chain.size(); ++i) {
            String parent = chain.get(i - 1);
            String child = chain.get(i);
            if (rigDefinition.bone(parent) != null
                    && rigDefinition.bone(child) != null
                    && !rigDefinition.isParent(parent, child)) {
                checks.error(path, "'" + parent + "' is not the parent of '" + child + "'");
            }
        }
    }

    /**
     * Report a name that was already used.
     *
     * @param checks Where problems go.
     * @param seen The names so far, which this one is added to.
     * @param name The name.
     * @param path Where it is.
     * @param what What it names, for the message.
     */
    private static void unique(
            Checks checks, Set<String> seen, String name, String path, String what) {
        if (!seen.add(name)) {
            checks.error(path, "There are two of the " + what + " '" + name + "'");
        }
    }

    /** Checks that a bone exists in the plan's rig. */
    @FunctionalInterface
    private interface BoneCheck {
        /**
         * Report the bone if the rig doesn't have it.
         *
         * @param name The bone's name.
         * @param path Where it is used.
         */
        void check(String name, String path);
    }
}
