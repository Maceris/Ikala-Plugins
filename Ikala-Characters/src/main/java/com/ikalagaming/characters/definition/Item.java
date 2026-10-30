package com.ikalagaming.characters.definition;

import com.ikalagaming.characters.data.Doc;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.Optional;
import com.ikalagaming.characters.data.Vec3;

import lombok.NonNull;

import java.util.List;
import java.util.Map;

/**
 * How an equippable item looks on characters: for each body plan, a layer part it is worn as, like
 * a helmet, or a mesh held in a socket, like a sword, with where it attaches and the states it can
 * be in, like drawn in the hand or sheathed on the back. Gameplay items live in their own plugins
 * and name one of these for their look, so stats and looks stay apart and transmog is choosing
 * another look for the same slot.
 *
 * @param id This look's ID.
 * @param slots The equipment slots it goes in.
 * @param category What kind of thing it is, like {@code sword.2h}, for editors and gameplay.
 * @param looks How it looks on each plan, by plan ID.
 * @param dyeChannels The palette channels players can dye it in.
 * @param tags Tags editors filter by.
 * @author Ches Burks
 */
@Doc(
        "How an equippable item looks: a layer part it is worn as, or a mesh held in a socket, for"
                + " each body plan. Gameplay items name one of these for their look.")
public record Item(
        @Doc("This look's ID, like \"ikala:item/iron_helm\".") Id id,
        @Doc("The equipment slots it goes in, like [\"mainHand\"].") List<String> slots,
        @Doc("What kind of thing it is, like \"sword.2h\".") @Optional String category,
        @Doc("How it looks on each plan, by plan ID.") Map<String, Look> looks,
        @Doc("The palette channels it can be dyed in, like [\"primary\", \"accent\"].") @Optional
                List<String> dyeChannels,
        @Doc("Tags that editors filter by, like \"weapon\".") @Optional List<String> tags)
        implements Definition {

    /** The field for the slots. */
    public static final String FIELD_SLOTS = "slots";

    /** The field for the looks. */
    public static final String FIELD_LOOKS = "looks";

    /** The field for the dye channels. */
    public static final String FIELD_DYE_CHANNELS = "dyeChannels";

    /**
     * How an item looks on one plan.
     *
     * @param part The layer part it is worn as, or null for a held item.
     * @param mesh The mesh it is held as, as file#mesh, or null for a worn item.
     * @param states Where a held item goes in each state, by state; the first is where it starts.
     * @param attach How a held mesh sits in each socket or socket type.
     * @param hidesLayers Layers it hides, like a helmet hiding hair.
     * @param hidesRegions Mask regions of the body it hides.
     * @param style The way the character moves with it, or null.
     * @param fx Effects it carries.
     */
    @Doc("How an item looks on one plan: worn as a layer part, or held as a mesh in a socket.")
    public record Look(
            @Doc("The layer part it is worn as, like a helmet.") @Optional Id part,
            @Doc(
                            "The mesh it is held as, like \"weapons/sword.glb#sword\", relative to"
                                    + " its namespace's content folder, with its grip at the"
                                    + " origin.")
                    @Optional
                    String mesh,
            @Doc(
                            "Where a held item goes in each state, like {\"drawn\": \"grip.R\","
                                    + " \"sheathed\": \"back\"}, as socket names or types. The"
                                    + " first is where it starts.")
                    @Optional
                    Map<String, String> states,
            @Doc("How a held mesh sits in each socket or socket type, like {\"back\": {...}}.")
                    @Optional
                    Map<String, Attach> attach,
            @Doc("Layers it hides, like a helmet hiding hair.") @Optional List<String> hidesLayers,
            @Doc("Mask regions of the body it hides.") @Optional List<String> hidesRegions,
            @Doc(
                            "The way the character moves with it, like"
                                    + " \"ikala:style/weapon/two_handed_melee\".")
                    @Optional
                    Id style,
            @Doc(
                            "Effects it carries, like a glowing blade. On a held item, an effect"
                                    + " with no bone or socket sits on the item, with offsets in"
                                    + " its mesh's space.")
                    @Optional
                    List<FxUse> fx) {
        /** The field for the part. */
        public static final String FIELD_PART = "part";

        /** The field for the mesh. */
        public static final String FIELD_MESH = "mesh";

        /** The field for the states. */
        public static final String FIELD_STATES = "states";

        /** The field for the attach offsets. */
        public static final String FIELD_ATTACH = "attach";

        /** The field for the hidden layers. */
        public static final String FIELD_HIDES_LAYERS = "hidesLayers";

        /** The field for the hidden regions. */
        public static final String FIELD_HIDES_REGIONS = "hidesRegions";

        /** The field for the style. */
        public static final String FIELD_STYLE = "style";

        /** The field for the effects. */
        public static final String FIELD_FX = "fx";

        /**
         * Whether it is held in a socket rather than worn as a layer.
         *
         * @return True for a held mesh.
         */
        public boolean held() {
            return mesh != null;
        }

        /**
         * The state it starts in.
         *
         * @return The first state, or null if it has none.
         */
        public String firstState() {
            return states.isEmpty() ? null : states.keySet().iterator().next();
        }

        /**
         * How it sits in a socket: the socket's own attach, else its type's, else none.
         *
         * @param socket The socket.
         * @return The attach, or null for the mesh as it is.
         */
        public Attach attachFor(@NonNull BodyPlan.Socket socket) {
            Attach own = attach.get(socket.id());
            return own != null || socket.type() == null ? own : attach.get(socket.type());
        }
    }

    /**
     * How a held mesh sits in a socket.
     *
     * @param offset Its offset from the socket, in meters, in the socket's space.
     * @param rotate Its rotation from the socket, in degrees about x, y and z.
     */
    @Doc("How a held mesh sits in a socket.")
    public record Attach(
            @Doc("Its offset from the socket, in meters.") @Optional Vec3 offset,
            @Doc("Its rotation from the socket, in degrees about x, y and z.") @Optional
                    Vec3 rotate) {}

    /**
     * How it looks on a plan.
     *
     * @param plan The plan.
     * @return The look, or null if it has none for that plan.
     */
    public Look lookOn(@NonNull Id plan) {
        return looks.get(plan.toString());
    }

    @Override
    public void check(Checks checks) {
        if (slots.isEmpty()) {
            checks.error(JsonPath.at(FIELD_SLOTS), "An item goes in at least one slot");
        }
        if (looks.isEmpty()) {
            checks.error(JsonPath.at(FIELD_LOOKS), "An item needs a look for some plan");
        }
        String looksPath = JsonPath.at(FIELD_LOOKS);
        looks.forEach(
                (planId, look) -> {
                    String path = JsonPath.field(looksPath, planId);
                    BodyPlan body;
                    try {
                        body = checks.require(BodyPlan.class, Id.parse(planId), path);
                    } catch (IllegalArgumentException e) {
                        checks.error(path, "Looks are by plan ID, not '" + planId + "'");
                        return;
                    }
                    if (body != null) {
                        checkLook(checks, body, look, path);
                    }
                });
    }

    /**
     * Check one plan's look.
     *
     * @param checks Where problems go.
     * @param body The plan.
     * @param look The look.
     * @param path Where it is.
     */
    private void checkLook(Checks checks, BodyPlan body, Look look, String path) {
        for (int i = 0; i < slots.size(); ++i) {
            if (body.equipmentSlot(slots.get(i)) == null) {
                checks.error(
                        JsonPath.at(FIELD_SLOTS, i),
                        body.id() + " has no equipment slot '" + slots.get(i) + "'");
            }
        }
        if ((look.part() == null) == (look.mesh() == null)) {
            checks.error(path, "A look is worn as a part or held as a mesh");
        }
        if (look.part() != null) {
            checkWorn(checks, body, look, path);
        }
        if (look.held()) {
            checkHeld(checks, body, look, path);
        }
        for (int i = 0; i < look.hidesLayers().size(); ++i) {
            if (body.layer(look.hidesLayers().get(i)) == null) {
                checks.error(
                        JsonPath.index(JsonPath.field(path, Look.FIELD_HIDES_LAYERS), i),
                        "The plan has no layer '" + look.hidesLayers().get(i) + "'");
            }
        }
        for (int i = 0; i < look.hidesRegions().size(); ++i) {
            if (!body.regions().contains(look.hidesRegions().get(i))) {
                checks.error(
                        JsonPath.index(JsonPath.field(path, Look.FIELD_HIDES_REGIONS), i),
                        "The plan has no region '" + look.hidesRegions().get(i) + "'");
            }
        }
        if (look.style() != null) {
            String stylePath = JsonPath.field(path, Look.FIELD_STYLE);
            AnimStyle style = checks.require(AnimStyle.class, look.style(), stylePath);
            if (style != null && !style.plan().equals(body.id())) {
                checks.error(stylePath, look.style() + " is for " + style.plan());
            }
        }
        for (int i = 0; i < dyeChannels.size(); ++i) {
            if (!body.hasChannel(dyeChannels.get(i))) {
                checks.error(
                        JsonPath.at(FIELD_DYE_CHANNELS, i),
                        body.id() + " has no palette channel '" + dyeChannels.get(i) + "'");
            }
        }
        Rig rig =
                checks.all(Rig.class).stream()
                        .filter(r -> r.id().equals(body.rig()))
                        .findFirst()
                        .orElse(null);
        String fxPath = JsonPath.field(path, Look.FIELD_FX);
        for (int i = 0; i < look.fx().size(); ++i) {
            look.fx()
                    .get(i)
                    .check(
                            body,
                            rig,
                            (effect, at) -> checks.require(Fx.class, effect, at),
                            checks,
                            JsonPath.index(fxPath, i),
                            look.held());
        }
    }

    /**
     * Check a worn look: its part is a layer part for the plan, in a slot's layer.
     *
     * @param checks Where problems go.
     * @param body The plan.
     * @param look The look.
     * @param path Where it is.
     */
    private void checkWorn(Checks checks, BodyPlan body, Look look, String path) {
        String partPath = JsonPath.field(path, Look.FIELD_PART);
        Part part = checks.require(Part.class, look.part(), partPath);
        if (part == null) {
            return;
        }
        if (part.kind() != Part.PartKind.LAYER || !part.plan().equals(body.id())) {
            checks.error(partPath, look.part() + " isn't a layer part for " + body.id());
            return;
        }
        boolean fits =
                slots.stream()
                        .map(body::equipmentSlot)
                        .anyMatch(
                                slot ->
                                        slot != null
                                                && slot.layer() != null
                                                && part.fills().contains(slot.layer()));
        if (!fits) {
            checks.error(partPath, look.part() + " fills none of its slots' layers");
        }
        if (!look.states().isEmpty() || !look.attach().isEmpty()) {
            checks.error(path, "Only held looks have states and attach offsets");
        }
    }

    /**
     * Check a held look: its mesh names a file and mesh, and its states and attach offsets name
     * sockets its slots allow.
     *
     * @param checks Where problems go.
     * @param body The plan.
     * @param look The look.
     * @param path Where it is.
     */
    private void checkHeld(Checks checks, BodyPlan body, Look look, String path) {
        int split = look.mesh().lastIndexOf(AnimationSet.CLIP_SEPARATOR);
        if (split <= 0 || split == look.mesh().length() - 1) {
            checks.error(
                    JsonPath.field(path, Look.FIELD_MESH),
                    "A mesh is its file, # and its name, like sword.glb#sword");
        }
        if (look.states().isEmpty()) {
            checks.error(
                    JsonPath.field(path, Look.FIELD_STATES), "A held item needs a state to be in");
        }
        String statesPath = JsonPath.field(path, Look.FIELD_STATES);
        look.states()
                .forEach(
                        (state, socket) -> {
                            if (!allowed(body, socket)) {
                                checks.error(
                                        JsonPath.field(statesPath, state),
                                        "None of its slots holds items in '" + socket + "'");
                            }
                        });
        String attachPath = JsonPath.field(path, Look.FIELD_ATTACH);
        for (String socket : look.attach().keySet()) {
            if (body.socketsFor(socket).isEmpty()) {
                checks.error(
                        JsonPath.field(attachPath, socket),
                        "The plan has no socket or socket type '" + socket + "'");
            }
        }
    }

    /**
     * Whether one of this item's slots holds items in a socket.
     *
     * @param body The plan.
     * @param socket A socket's name or type.
     * @return True if some slot lists it, or a socket it picks.
     */
    private boolean allowed(BodyPlan body, String socket) {
        List<BodyPlan.Socket> picked = body.socketsFor(socket);
        for (String slotName : slots) {
            BodyPlan.EquipmentSlot slot = body.equipmentSlot(slotName);
            if (slot == null) {
                continue;
            }
            for (String listed : slot.sockets()) {
                if (body.socketsFor(listed).stream().anyMatch(picked::contains)) {
                    return true;
                }
            }
        }
        return false;
    }
}
