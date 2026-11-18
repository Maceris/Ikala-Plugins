package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.ColorCode;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Decal;
import com.ikalagaming.characters.definition.DecalUse;
import com.ikalagaming.characters.definition.FxUse;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.Pose;
import com.ikalagaming.characters.pose.PoseSolver;
import com.ikalagaming.characters.pose.Proportions;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.pose.SpringChains;
import com.ikalagaming.characters.pose.WingMotion;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.view.AccessoryRests;
import com.ikalagaming.characters.view.CharacterDecals;
import com.ikalagaming.characters.view.CharacterFx;
import com.ikalagaming.characters.view.FxEffects;
import com.ikalagaming.characters.view.PartForms;
import com.ikalagaming.characters.view.PartSlots;
import com.ikalagaming.characters.view.ShownCharacter;
import com.ikalagaming.graphics.SkinnedPart;
import com.ikalagaming.graphics.Skins;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Keeps the character editor's shown character in step with the look being edited: the parts it
 * wears and how they are pushed out and rested, its proportions and height, the chains swinging on
 * their springs and the wings, its colors and decal, and its effects. Each change is put on the
 * character only when what it is made from changes. The editor keeps one for as long as it exists;
 * the design shown is passed to each method as an {@link EditorSession} rather than kept, so it
 * can't be worked on after the session closes. What is kept here, like the swinging chains and the
 * height, stays between designs and across closing and opening the editor again, as it did on the
 * editor. Render thread only, like the editor's UI.
 */
final class ShownSync {
    /** Logs as the editor did, before this was split out of it. */
    private static final Logger log = LoggerFactory.getLogger(CharacterEditor.class);

    /** Floats per vertex in a part's vertices. */
    private static final int VERTEX_FLOATS = 14;

    /** Bone weights per vertex. */
    private static final int INFLUENCES = 4;

    /** The most a color's channel holds, see {@link Color}. */
    private static final float COLOR_MAX = 255;

    /** The loaded definitions. */
    private final Supplier<CharacterRegistry> registry;

    /** The shown character's animation, which applies the effects' states. */
    private final CharacterMotion motion;

    /** Whether worn chains, like a cape or tail, swing on their springs. */
    private boolean springsOn = true;

    /** The chains swinging now, and the character they swing on, to set them up only on change. */
    private Set<String> swinging = Set.of();

    /** The character the chains swing on. */
    private ShownCharacter swingingOn;

    /** What worn wings do, kept as parts change, or null to follow the motion. */
    private WingMotion.State wingState;

    /** The worn wings' poses, or null when no wings are worn. */
    private WingMotion wingMotion;

    /** The worn chains' springs, or null when none swing. */
    private SpringChains springChains;

    /** Which bones are on worn extension chains, by index. */
    private boolean[] onChain = new boolean[0];

    /** The wings worn, for the Align tab. */
    private List<WingPoser.Worn> wornWings = List.of();

    /** How tall the character is with the sliders as they are, in meters. */
    private float characterHeight;

    /**
     * Set up keeping a shown character in step; nothing is shown until a session is passed in.
     *
     * @param registry The loaded definitions, read each time they are needed.
     * @param motion The shown character's animation.
     */
    ShownSync(Supplier<CharacterRegistry> registry, CharacterMotion motion) {
        this.registry = registry;
        this.motion = motion;
    }

    /**
     * Whether worn chains, like a cape or tail, swing on their springs.
     *
     * @return True if they swing.
     */
    boolean springsOn() {
        return springsOn;
    }

    /**
     * Turn the worn chains' springs on or off. Takes effect the next time the sliders are applied.
     *
     * @param on Whether they swing.
     */
    void setSpringsOn(boolean on) {
        springsOn = on;
    }

    /**
     * What worn wings do, kept as parts change.
     *
     * @return The state, or null to follow the motion.
     */
    WingMotion.State wingState() {
        return wingState;
    }

    /**
     * The worn wings' poses.
     *
     * @return The poses, or null when no wings are worn.
     */
    WingMotion wingMotion() {
        return wingMotion;
    }

    /**
     * The worn chains' springs.
     *
     * @return The springs, or null when none swing.
     */
    SpringChains springChains() {
        return springChains;
    }

    /**
     * Which bones are on worn extension chains, by index. The array is the one kept here, not a
     * copy.
     *
     * @return The flags.
     */
    boolean[] onChain() {
        return onChain;
    }

    /**
     * The wings worn, for the Align tab.
     *
     * @return The wings.
     */
    List<WingPoser.Worn> wornWings() {
        return wornWings;
    }

    /**
     * How tall the character is with the sliders as they were last applied successfully.
     *
     * @return The height, in meters.
     */
    float characterHeight() {
        return characterHeight;
    }

    /**
     * Show what the look wears, with one slot previewing another choice, if that changes what is
     * shown.
     *
     * @param session The design shown, or null while none is.
     * @param previewSlot The slot to preview, or {@link CharacterLook#NO_PREVIEW}.
     * @param previewChoice What it would hold.
     */
    void showWorn(EditorSession session, int previewSlot, int previewChoice) {
        if (session == null) {
            return;
        }
        List<Integer> worn = session.look.worn(previewSlot, previewChoice);
        pushForms(session, worn, session.look.slotStandoffs(previewSlot, previewChoice));
        if (!worn.equals(session.lastWorn)) {
            session.lastWorn = worn;
            session.shown.showParts(worn);
            session.shown.setHiddenRegions(session.regions.hidden(worn));
        }
    }

    /**
     * Push the shown layers that have form targets out over the slots of bulky body parts, and move
     * the shown accessories out to rest on what is worn under them, each only when how far it moves
     * changes.
     *
     * @param session The design shown.
     * @param worn The shown parts.
     * @param slotStandoffs How far the body part in each slot stands out, in meters.
     */
    void pushForms(EditorSession session, List<Integer> worn, Map<String, Float> slotStandoffs) {
        Map<String, Float> amounts = PartForms.shares(session.plan, slotStandoffs);
        ImportedCharacter character = session.prepared.character();
        for (int index : worn) {
            ImportedCharacter.Part part = character.parts().get(index);
            if (part.formTargets().isEmpty()) {
                continue;
            }
            Map<String, Float> own = new HashMap<>();
            amounts.forEach(
                    (slot, amount) -> {
                        if (part.formTargets().containsKey(slot)) {
                            own.put(slot, amount);
                        }
                    });
            if (!own.equals(session.pushed.getOrDefault(index, Map.of()))) {
                session.pushed.put(index, own);
                session.shown.replaceVertices(index, PartForms.displace(part, own));
            }
        }
        restAccessories(session, worn);
    }

    /**
     * Move the shown accessories out to rest on what is worn under them, each only when how far it
     * moves changes.
     *
     * @param session The design shown.
     * @param worn The shown parts.
     */
    private void restAccessories(EditorSession session, List<Integer> worn) {
        List<Part> definitions = session.prepared.partDefinitions();
        Set<Part> wornParts = new LinkedHashSet<>();
        for (int index : worn) {
            Part definition = definitions.get(index);
            if (definition != null) {
                wornParts.add(definition);
            }
        }
        ImportedCharacter character = session.prepared.character();
        for (int index : worn) {
            Part definition = definitions.get(index);
            ImportedCharacter.Part part = character.parts().get(index);
            if (definition == null
                    || definition.kind() != Part.PartKind.ACCESSORY
                    || part.joints().length == 0) {
                continue;
            }
            // The socket the mesh is on, whichever of the sockets it fits that is
            BodyPlan.Socket socket =
                    session.plan.socketOnBone(character.skeleton().name(part.joints()[0]));
            Vector3f push =
                    AccessoryRests.push(
                            session.plan, socket, wornParts, session.prepared.design().variant());
            Vector3f last = session.rested.get(index);
            if (last == null ? push.lengthSquared() > 0 : !push.equals(last)) {
                session.rested.put(index, push);
                session.shown.replaceVertices(index, AccessoryRests.moved(part, push));
            }
        }
    }

    /**
     * Pose the character with the sliders' values.
     *
     * @param session The design shown, or null while none is.
     * @return False if the sliders don't fit the skeleton.
     */
    boolean applySliders(EditorSession session) {
        if (session == null) {
            return false;
        }
        try {
            Set<Part> worn = new LinkedHashSet<>();
            for (int index : session.look.worn(CharacterLook.NO_PREVIEW, 0)) {
                Part definition = session.prepared.partDefinitions().get(index);
                if (definition != null) {
                    worn.add(definition);
                }
            }
            // Parts on extension chains make their chains as long as their meshes
            Proportions proportions =
                    Proportions.fromSliders(
                                    session.shown.getSkeleton(),
                                    session.plan,
                                    session.look.sliders(),
                                    session.look.values(),
                                    1)
                            .withChains(session.plan, Proportions.chainLengths(session.plan, worn));
            session.shown.setProportions(proportions);
            characterHeight = heightWith(session, proportions);
            swingChains(session, worn);
            return true;
        } catch (IllegalArgumentException e) {
            log.warn(
                    "The sliders don't fit {}: {}",
                    session.shown.getCharacter().name(),
                    e.getMessage());
            return false;
        }
    }

    /**
     * Swing the chains the worn parts use on their springs, setting them up again only when that
     * changes.
     *
     * @param session The design shown.
     * @param worn The parts worn.
     */
    private void swingChains(EditorSession session, Set<Part> worn) {
        // As loaded now, so the Align tab sees a wing's saved poses
        List<WingPoser.Worn> wingsWorn = new ArrayList<>();
        for (Part part : worn) {
            BodyPlan.Extension extension =
                    part.extension() == null ? null : session.plan.extension(part.extension());
            if (extension != null && extension.wing() != null) {
                wingsWorn.add(
                        new WingPoser.Worn(
                                extension.id(),
                                part,
                                extension.bones(),
                                extension.rightSide(),
                                extension.wing()));
            }
        }
        wornWings = List.copyOf(wingsWorn);
        Set<String> chains = new TreeSet<>();
        worn.stream().map(Part::extension).filter(Objects::nonNull).forEach(chains::add);
        Set<String> keyed = new TreeSet<>(chains);
        // Turning the springs off or on sets them up again, and so does another wing part, whose
        // poses may be its own
        keyed.add(springsOn ? "+springs" : "-springs");
        worn.stream()
                .filter(part -> part.extension() != null)
                .forEach(part -> keyed.add(part.id().toString()));
        if (keyed.equals(swinging) && session.shown == swingingOn) {
            return;
        }
        swinging = keyed;
        swingingOn = session.shown;
        SpringChains springs =
                new SpringChains(
                        session.shown.getSkeleton(), session.plan, springsOn ? chains : Set.of());
        springChains = springs.isEmpty() ? null : springs;
        session.shown.setSprings(springChains);
        onChain = new boolean[session.shown.getSkeleton().size()];
        for (String chain : chains) {
            BodyPlan.Extension extension = session.plan.extension(chain);
            if (extension != null) {
                for (String name : extension.bones()) {
                    int bone = session.shown.getSkeleton().index(name);
                    if (bone >= 0) {
                        onChain[bone] = true;
                    }
                }
            }
        }
        // A worn wing part can fold or sit its own way
        Map<String, BodyPlan.WingPoses> own = new HashMap<>();
        worn.stream()
                .filter(part -> part.extension() != null && part.wing() != null)
                .forEach(part -> own.put(part.extension(), part.wing()));
        WingMotion wings =
                new WingMotion(
                        session.shown.getSkeleton(),
                        session.plan,
                        chains,
                        wingState == null ? WingMotion.State.FOLD : wingState,
                        own);
        if (wingState == null) {
            wings.setAutomatic();
        }
        wingMotion = wings.isEmpty() ? null : wings;
        session.shown.setWings(wingMotion);
    }

    /**
     * Change what worn wings do, keeping it for wings put on later.
     *
     * @param state What they do, or null to follow the character's motion.
     */
    void setWingState(WingMotion.State state) {
        wingState = state;
        if (wingMotion == null) {
            return;
        }
        if (state == null) {
            wingMotion.setAutomatic();
        } else {
            wingMotion.setState(state);
        }
    }

    /**
     * How tall the character stands at rest with some proportions: the top of its body parts,
     * skinned as the renderer skins them, leaving out what can be taken off, like helmets.
     *
     * @param session The design shown.
     * @param proportions The proportions.
     * @return The height, in meters.
     */
    private float heightWith(EditorSession session, Proportions proportions) {
        Skeleton skeleton = session.shown.getSkeleton();
        PoseSolver solver = new PoseSolver(skeleton);
        solver.solve(new Pose(skeleton), proportions);
        float[] palette = new float[skeleton.size() * Skeleton.MATRIX_FLOATS];
        solver.writePalette(proportions, palette, 0);
        Matrix4f[] matrices = new Matrix4f[skeleton.size()];
        for (int b = 0; b < matrices.length; ++b) {
            matrices[b] = new Matrix4f().set(palette, b * Skeleton.MATRIX_FLOATS);
        }
        List<ImportedCharacter.Part> parts = session.shown.getCharacter().parts();
        Vector3f rest = new Vector3f();
        Vector3f moved = new Vector3f();
        Vector3f sum = new Vector3f();
        float top = 0;
        for (int s = 0; s < session.look.slots().slots().size(); ++s) {
            PartSlots.Slot slot = session.look.slots().slots().get(s);
            int choice = session.look.choice(s);
            if (slot.optional() || choice == CharacterLook.EMPTY) {
                continue;
            }
            for (int index : slot.options().get(choice)) {
                ImportedCharacter.Part part = parts.get(index);
                float[] v = part.vertices();
                for (int vertex = 0; vertex < v.length / VERTEX_FLOATS; ++vertex) {
                    rest.set(
                            v[vertex * VERTEX_FLOATS],
                            v[vertex * VERTEX_FLOATS + 1],
                            v[vertex * VERTEX_FLOATS + 2]);
                    sum.zero();
                    for (int i = 0; i < INFLUENCES; ++i) {
                        float weight = part.weights()[vertex * INFLUENCES + i];
                        if (weight > 0) {
                            matrices[part.joints()[vertex * INFLUENCES + i]].transformPosition(
                                    rest, moved);
                            sum.fma(weight, moved);
                        }
                    }
                    top = Math.max(top, sum.y);
                }
            }
        }
        return top;
    }

    /**
     * Put the effects of the worn parts and the design on the character, if they or the colors
     * changed since they were last put on.
     *
     * @param session The design shown, or null while none is.
     * @param fxEffects The particle effects characters wear, or null while the editor is closed.
     */
    void showFx(EditorSession session, FxEffects fxEffects) {
        if (session == null || fxEffects == null) {
            return;
        }
        List<Part> worn = wornParts(session);
        List<FxUse> uses = new ArrayList<>();
        for (Part part : worn) {
            uses.addAll(part.fx());
        }
        uses.addAll(session.look.fx());
        uses.addAll(session.itemFx);
        Map<String, Color> colors = new HashMap<>();
        for (String channel : session.plan.allChannels()) {
            Integer code = channelColor(session, channel);
            if (code != null) {
                colors.put(channel, ColorCode.toColor(code));
            }
        }
        List<Object> key = List.of(uses, colors);
        if (key.equals(session.shownFx)) {
            return;
        }
        session.shownFx = key;
        session.shownUses = uses;
        session.shownEffects =
                uses.isEmpty()
                        ? null
                        : new CharacterFx(
                                uses,
                                worn,
                                session.plan,
                                session.shown.getSkeleton(),
                                colors,
                                registry.get(),
                                fxEffects);
        motion.applyStates(session.shownEffects, uses);
        session.shown.setFx(session.shownEffects);
    }

    /**
     * What the Align tab works on: the worn accessories and head.
     *
     * @param session The design shown.
     * @return The stage.
     */
    AccessoryAligner.Stage alignStage(EditorSession session) {
        List<AccessoryAligner.Worn> accessories = new ArrayList<>();
        List<Integer> head = new ArrayList<>();
        List<PartSlots.Slot> slots = session.look.slots().slots();
        for (int s = 0; s < slots.size(); ++s) {
            int choice = session.look.choice(s);
            if (choice == CharacterLook.EMPTY) {
                continue;
            }
            PartSlots.Slot slot = slots.get(s);
            List<Integer> meshes = slot.options().get(choice);
            if (AccessoryAligner.HEAD_SLOT.equals(slot.id())) {
                head.addAll(meshes);
            }
            Part part =
                    registry.get().get(Part.class, session.prepared.optionIds().get(s).get(choice));
            if (part != null
                    && part.kind() == Part.PartKind.ACCESSORY
                    && session.plan.socket(slot.id()) != null
                    && !meshes.isEmpty()) {
                accessories.add(new AccessoryAligner.Worn(slot.id(), part, meshes));
            }
        }
        return new AccessoryAligner.Stage(
                session.prepared.character(),
                session.shown,
                accessories,
                head,
                session.plan.variants().get(Math.max(0, session.look.variant())).id(),
                CharacterStage.origin());
    }

    /**
     * The parts the look wears.
     *
     * @param session The design shown.
     * @return The worn parts' definitions.
     */
    private List<Part> wornParts(EditorSession session) {
        List<Part> worn = new ArrayList<>();
        for (int s = 0; s < session.look.slots().slots().size(); ++s) {
            int choice = session.look.choice(s);
            if (choice == CharacterLook.EMPTY) {
                continue;
            }
            Part part =
                    registry.get().get(Part.class, session.prepared.optionIds().get(s).get(choice));
            if (part != null) {
                worn.add(part);
            }
        }
        return worn;
    }

    /**
     * Find the colors worn parts give palette channels the design may leave uncolored: for each
     * channel, the first default of a material slot whose tint mask channel takes it.
     *
     * @param session The design shown.
     * @return The color codes, by palette channel.
     */
    private Map<String, Integer> findPartColors(EditorSession session) {
        Map<String, Integer> found = new HashMap<>();
        for (Part part : wornParts(session)) {
            for (Part.MaterialSlot slot : part.materialSlots()) {
                slot.channels()
                        .forEach(
                                (mask, channel) -> {
                                    Color color = slot.defaults().get(mask);
                                    if (color != null) {
                                        found.putIfAbsent(channel, ColorCode.encode(color));
                                    }
                                });
            }
        }
        return found;
    }

    /**
     * The color a palette channel shows: the design's, or else the worn parts' own.
     *
     * @param session The design shown.
     * @param channel The palette channel.
     * @return The color code, or null if neither colors it.
     */
    Integer channelColor(EditorSession session, String channel) {
        Integer code = session.look.color(channel);
        return code != null ? code : session.partColors.get(channel);
    }

    /**
     * Color the parts by their channels, with one channel previewing a swatch, if that changes what
     * is shown.
     *
     * @param session The design shown, or null while none is.
     * @param previewChannel The channel to preview, or null.
     * @param previewCode The swatch's color code.
     */
    void showColors(EditorSession session, String previewChannel, int previewCode) {
        if (session == null
                || session.look.version() == session.shownColorVersion
                        && Objects.equals(previewChannel, session.shownPreviewChannel)
                        && previewCode == session.shownPreviewCode) {
            return;
        }
        if (session.shownColorVersion != session.look.version()) {
            session.partColors = findPartColors(session);
        }
        session.shownColorVersion = session.look.version();
        session.shownPreviewChannel = previewChannel;
        session.shownPreviewCode = previewCode;
        // One tint per standard channel; channels the design leaves uncolored are unset
        List<String> channels = session.plan.allChannels();
        float[] tints = new float[3 * Math.min(channels.size(), Skins.TINT_SLOTS)];
        Arrays.fill(tints, 1);
        float[] rgb = new float[3];
        for (int c = 0; c < tints.length / 3; ++c) {
            String channel = channels.get(c);
            Integer code =
                    channel.equals(previewChannel)
                            ? Integer.valueOf(previewCode)
                            : session.look.color(channel);
            if (code != null) {
                ColorCode.decode(code, rgb);
                System.arraycopy(rgb, 0, tints, 3 * c, 3);
            } else {
                // Each part shows its own default for a channel the design leaves uncolored
                tints[3 * c] = Skins.UNSET;
            }
        }
        session.shown.setTints(tints);
        showDecal(session);
    }

    /**
     * Give the shown character the look's decal, if it changed: its art, its box on the plan's
     * place, the tint slots its colors take and its defaults, and the parts that show it.
     *
     * @param session The design shown.
     */
    private void showDecal(EditorSession session) {
        DecalUse use = session.look.decal();
        if (session.decalSynced && Objects.equals(use, session.shownDecal)) {
            return;
        }
        session.decalSynced = true;
        session.shownDecal = use;
        Decal art = use == null ? null : registry.get().get(Decal.class, use.decal());
        BodyPlan.DecalPlace place = use == null ? null : session.plan.decalPlace(use.place());
        byte[] bytes = null;
        if (art != null) {
            try {
                bytes = registry.get().readAsset(art.id().namespace(), art.image());
            } catch (IOException e) {
                log.warn("Couldn't read the decal {}: {}", art.image(), e.getMessage());
            }
        }
        if (bytes == null || place == null) {
            if (use != null) {
                log.debug("No decal art for {} on {}", use.decal(), use.place());
            }
            session.shown.setDecal(null, null, -1, -1, SkinnedPart.WHITE, SkinnedPart.WHITE);
            return;
        }
        List<String> all = session.plan.allChannels();
        int[] slots = new int[2];
        int[] fallbacks = new int[2];
        List<String> masks = List.of(Decal.FIRST, Decal.SECOND);
        for (int c = 0; c < masks.size(); ++c) {
            String channel = use.channels().get(masks.get(c));
            int slot = channel == null ? -1 : all.indexOf(channel);
            slots[c] = slot < Skins.TINT_SLOTS ? slot : -1;
            Color color = art.defaults().get(masks.get(c));
            fallbacks[c] =
                    color == null
                            ? SkinnedPart.WHITE
                            : SkinnedPart.pack(
                                    color.red() / COLOR_MAX,
                                    color.green() / COLOR_MAX,
                                    color.blue() / COLOR_MAX);
        }
        session.shown.setDecal(
                new ImportedCharacter.ImageData(art.image(), "image/png", bytes),
                CharacterDecals.restToBox(session.shown.getSkeleton(), place),
                slots[0],
                slots[1],
                fallbacks[0],
                fallbacks[1]);
        boolean[] showing =
                CharacterDecals.partsShowing(
                        session.plan, place, session.prepared.partDefinitions());
        session.shown.setDecalParts(showing);
        if (log.isDebugEnabled()) {
            int count = 0;
            for (boolean shows : showing) {
                count += shows ? 1 : 0;
            }
            log.debug("Showing the decal {} on {} of {} parts", art.id(), count, showing.length);
        }
    }
}
