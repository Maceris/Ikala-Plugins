package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.AnimStyle;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.definition.Item;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.design.Equipment;
import com.ikalagaming.characters.design.LookStack;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.WingMotion;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.view.HeldItems;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.ui.spec.Observable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The character editor's clips and motion, and its Equipment tab: the clip bar's clip, position and
 * play button, the shown character's animation built for its styles, the Motion tab, and the held
 * items equipped. It wraps the editor's {@link CharacterMotion}. The editor keeps one for as long
 * as it exists; the design shown is passed to each method as an {@link EditorSession} rather than
 * kept, so it can't be worked on after the session closes. What is kept here, like whether the clip
 * plays and the bones are drawn, stays between designs and across closing and opening the editor
 * again, as it did on the editor. Render thread only, like the editor's UI; only the animation's
 * clips are read on other threads, by {@link CharacterMotion}.
 */
final class MotionControls {
    /** What grip socket types start with, like grip.1h. */
    static final String GRIP_TYPE = "grip";

    /** What the equipment dropdowns call an empty slot. */
    static final String EMPTY_SLOT = "(empty)";

    /** What the look-as dropdown calls an item's own look. */
    static final String OWN_LOOK = "its own";

    /** The state held items are in when drawn, by the test items' naming. */
    static final String DRAWN = "drawn";

    /** The state held items are in when sheathed. */
    static final String SHEATHED = "sheathed";

    /** What the style dropdown calls having no style. */
    static final String PLAN_STYLE = "the plan's own";

    /** The loaded definitions. */
    private final Supplier<CharacterRegistry> registry;

    /** The shown character's animation and the Motion tab. */
    private final CharacterMotion motion;

    /** Keeps the shown character in step with the look. */
    private final ShownSync sync;

    /** Changes the editor's look, then shows the change. */
    private final Consumer<Consumer<CharacterLook>> changeLook;

    /** Where the animation's clips are read, a pool the plugin owns. */
    private final ExecutorService builds;

    /** Runs a task on the render thread. */
    private final Consumer<Runnable> post;

    /** Whether the character stands at rest, read each time it is needed. */
    private final BooleanSupplier standsStill;

    /** Whether the Body tab is showing, read each time it is needed. */
    private final BooleanSupplier bodyTab;

    /** The rig reference file, read each time the animation is built. */
    private final Supplier<Path> reference;

    /** The clip's name. */
    private final Observable<String> clipName = Observable.of("");

    /** Which clip of how many. */
    private final Observable<String> clipPosition = Observable.of("");

    /** The play button's text. */
    private final Observable<String> playText = Observable.of("Pause");

    /** Whether the clip's time moves on. */
    private boolean playing = true;

    /** The styles the animation was last built for. */
    private List<Id> motionStyles = List.of();

    /** Reads held items' meshes. */
    private final HeldItems heldItems = new HeldItems();

    /** Whether the bones are drawn over the character. */
    private boolean showBones;

    /** Whether the chains' bones are named, while the bones are drawn. */
    private boolean boneNames;

    /**
     * Set up the clips, motion and equipment; nothing plays until a session is passed in.
     *
     * @param registry The loaded definitions, read each time they are needed.
     * @param motion The shown character's animation.
     * @param sync Keeps the shown character in step with the look.
     * @param changeLook Changes the editor's look, then shows the change.
     * @param builds Where the animation's clips are read, a pool the plugin owns.
     * @param post Runs a task on the render thread.
     * @param standsStill Whether the character stands at rest, as on the Body tab.
     * @param bodyTab Whether the Body tab is showing.
     * @param reference The rig reference file, read while the editor is open.
     */
    MotionControls(
            Supplier<CharacterRegistry> registry,
            CharacterMotion motion,
            ShownSync sync,
            Consumer<Consumer<CharacterLook>> changeLook,
            ExecutorService builds,
            Consumer<Runnable> post,
            BooleanSupplier standsStill,
            BooleanSupplier bodyTab,
            Supplier<Path> reference) {
        this.registry = registry;
        this.motion = motion;
        this.sync = sync;
        this.changeLook = changeLook;
        this.builds = builds;
        this.post = post;
        this.standsStill = standsStill;
        this.bodyTab = bodyTab;
        this.reference = reference;
    }

    /**
     * The clip bar's clip name.
     *
     * @return The clip's name.
     */
    Observable<String> clipName() {
        return clipName;
    }

    /**
     * The clip bar's position, which clip of how many.
     *
     * @return Which clip of how many.
     */
    Observable<String> clipPosition() {
        return clipPosition;
    }

    /**
     * The play button's text.
     *
     * @return The text.
     */
    Observable<String> playText() {
        return playText;
    }

    /**
     * Whether the bones are drawn over the character.
     *
     * @return True if they are.
     */
    boolean showBones() {
        return showBones;
    }

    /**
     * Whether the chains' bones are named, while the bones are drawn.
     *
     * @return True if they are.
     */
    boolean boneNames() {
        return boneNames;
    }

    /**
     * Draw the bones over the character, or not.
     *
     * @param show Whether to draw them.
     * @param names Whether to name the chains' bones.
     */
    void setBones(boolean show, boolean names) {
        showBones = show;
        boneNames = names;
    }

    /**
     * Play one of the character's clips, wrapping around the ends of the list.
     *
     * @param session The design shown, or null while nothing is.
     * @param index The clip's position in the character's list.
     */
    void playClip(EditorSession session, int index) {
        if (session == null) {
            return;
        }
        motion.setStanding(standsStill.getAsBoolean());
        if (motion.active()) {
            return;
        }
        List<ImportedCharacter.ClipData> clips = session.shown.getCharacter().clips();
        if (clips.isEmpty()) {
            session.clipIndex = -1;
            session.shown.play(null);
            clipName.set("No clips");
            clipPosition.set("");
            return;
        }
        session.clipIndex = Math.floorMod(index, clips.size());
        ImportedCharacter.ClipData clip = clips.get(session.clipIndex);
        // The Body tab stands the character at rest, so the ruler reads its height true
        session.shown.play(standsStill.getAsBoolean() ? null : clip);
        session.shown.setPlaying(playing);
        clipName.set(
                bodyTab.getAsBoolean() ? clip.name() + " (plays on the Costume tab)" : clip.name());
        clipPosition.set((session.clipIndex + 1) + " / " + clips.size());
    }

    /**
     * Step through the actions, or the file's clips if the plan has no actions.
     *
     * @param session The design shown, or null while nothing is.
     * @param step How far, like -1 for the one before.
     */
    void stepClip(EditorSession session, int step) {
        if (motion.active()) {
            motion.cycle(step);
        } else if (session != null) {
            // Nothing plays while nothing is shown
            playClip(session, session.clipIndex + step);
        }
    }

    /**
     * Build the shown character's animation for its variant and the look's style.
     *
     * @param session The design shown.
     * @param variant The variant.
     */
    void buildMotion(EditorSession session, String variant) {
        motionStyles = styles(session);
        motion.setStanding(standsStill.getAsBoolean());
        motion.build(
                registry.get(),
                session.plan,
                variant,
                motionStyles,
                reference.get(),
                session.shown,
                builds,
                post);
    }

    /**
     * Draw the Motion tab: the style, then the movement, actions and effect states.
     *
     * @param session The design shown.
     */
    void drawMotion(EditorSession session) {
        List<AnimStyle> styles =
                registry.get().all(AnimStyle.class).stream()
                        .filter(style -> style.plan().equals(session.plan.id()))
                        .toList();
        Id chosen = session.look.style();
        String preview = chosen == null ? PLAN_STYLE : PreparedDesign.label(chosen);
        if (IkGui.beginCombo("Style##style", preview)) {
            if (IkGui.selectable(PLAN_STYLE + "##own-style", chosen == null)) {
                changeLook.accept(changed -> changed.setStyle(null));
            }
            for (AnimStyle style : styles) {
                if (IkGui.selectable(
                        PreparedDesign.label(style.id()) + "##" + style.id(),
                        style.id().equals(chosen))) {
                    changeLook.accept(changed -> changed.setStyle(style.id()));
                }
            }
            IkGui.endCombo();
        }
        if (IkGui.checkbox("Swing chains##springs", sync.springsOn())) {
            sync.setSpringsOn(!sync.springsOn());
            sync.applySliders(session);
        }
        IkGui.sameLine();
        if (IkGui.checkbox("Bones##bones", showBones)) {
            showBones = !showBones;
        }
        if (showBones) {
            IkGui.sameLine();
            if (IkGui.checkbox("Names##bone-names", boneNames)) {
                boneNames = !boneNames;
            }
            IkGui.textDisabled("Orange: chains; cyan: capsules; magenta: one-way capsules");
        }
        if (sync.wingMotion() != null) {
            IkGui.textUnformatted("Wings");
            IkGui.sameLine();
            if (IkGui.radioButton(
                    CharacterEditor.WINGS_AUTO + "##wings-auto", sync.wingState() == null)) {
                sync.setWingState(null);
            }
            for (WingMotion.State state : WingMotion.State.values()) {
                IkGui.sameLine();
                if (IkGui.radioButton(
                        state.key() + "##wings-" + state.key(), state == sync.wingState())) {
                    sync.setWingState(state);
                }
            }
        }
        motion.draw(session.shownEffects, session.shownUses);
    }

    /**
     * The styles the character moves in: its equipped items', in the plan's slot order, then the
     * look's.
     *
     * @param session The design shown.
     * @return The styles, the first winning.
     */
    private List<Id> styles(EditorSession session) {
        List<Id> styles = new ArrayList<>();
        CharacterRegistry definitions = registry.get();
        for (BodyPlan.EquipmentSlot slot : session.plan.equipment()) {
            Equipment.Equipped equipped = session.equipment.slots().get(slot.id());
            Item item = equipped == null ? null : definitions.get(Item.class, equipped.item());
            Item.Look itemLook = item == null ? null : item.lookOn(session.plan.id());
            if (itemLook != null && itemLook.style() != null) {
                styles.add(itemLook.style());
            }
        }
        if (session.look.style() != null) {
            styles.add(session.look.style());
        }
        return styles;
    }

    /**
     * Draw the Equipment tab: for each of the plan's slots that something held fits, the item in
     * it, its state, a look shown in its place, and whether it is hidden. Worn items, which the
     * look stack puts in layers, aren't offered here yet.
     *
     * @param session The design shown.
     */
    void drawEquipment(EditorSession session) {
        CharacterRegistry definitions = registry.get();
        List<Item> all = definitions.all(Item.class);
        boolean any = false;
        for (BodyPlan.EquipmentSlot slot : session.plan.equipment()) {
            List<Item> fits =
                    all.stream()
                            .filter(
                                    item ->
                                            item.slots().contains(slot.id())
                                                    && item.lookOn(session.plan.id()) != null
                                                    && item.lookOn(session.plan.id()).held())
                            .toList();
            if (fits.isEmpty()) {
                continue;
            }
            any = true;
            IkGui.pushID(slot.id());
            IkGui.separatorText(slot.id());
            drawEquipped(
                    session,
                    slot.id(),
                    session.equipment.slots().get(slot.id()),
                    fits,
                    definitions);
            IkGui.popID();
        }
        if (!any) {
            IkGui.textDisabled("No held items fit this plan");
            return;
        }
        IkGui.spacing();
        if (IkGui.button("Draw all")) {
            setAllStates(session, DRAWN);
        }
        IkGui.sameLine();
        if (IkGui.button("Sheathe all")) {
            setAllStates(session, SHEATHED);
        }
    }

    /**
     * Draw one slot's rows.
     *
     * @param session The design shown.
     * @param slot The slot.
     * @param equipped What is in it, or null.
     * @param fits The held items that fit it.
     * @param definitions The loaded definitions.
     */
    private void drawEquipped(
            EditorSession session,
            String slot,
            Equipment.Equipped equipped,
            List<Item> fits,
            CharacterRegistry definitions) {
        String preview = equipped == null ? EMPTY_SLOT : PreparedDesign.label(equipped.item());
        if (IkGui.beginCombo("Item##item", preview)) {
            if (IkGui.selectable(EMPTY_SLOT + "##empty", equipped == null)) {
                equip(session, slot, null);
            }
            for (Item item : fits) {
                if (IkGui.selectable(
                        PreparedDesign.label(item.id()) + "##" + item.id(),
                        equipped != null && item.id().equals(equipped.item()))) {
                    equip(
                            session,
                            slot,
                            new Equipment.Equipped(item.id(), null, null, false, Map.of()));
                }
            }
            IkGui.endCombo();
        }
        if (equipped == null) {
            return;
        }
        Item shownItem = definitions.get(Item.class, equipped.shown());
        Item.Look shownLook = shownItem == null ? null : shownItem.lookOn(session.plan.id());
        if (shownLook != null) {
            String state = equipped.state() == null ? shownLook.firstState() : equipped.state();
            if (IkGui.beginCombo("State##state", state)) {
                for (String option : shownLook.states().keySet()) {
                    if (IkGui.selectable(option + "##" + option, option.equals(state))) {
                        equip(session, slot, equipped.withState(option));
                    }
                }
                IkGui.endCombo();
            }
        }
        String lookAs =
                equipped.lookAs() == null ? OWN_LOOK : PreparedDesign.label(equipped.lookAs());
        if (IkGui.beginCombo("Look as##look-as", lookAs)) {
            if (IkGui.selectable(OWN_LOOK + "##own", equipped.lookAs() == null)) {
                equip(
                        session,
                        slot,
                        new Equipment.Equipped(
                                equipped.item(),
                                equipped.state(),
                                null,
                                equipped.hidden(),
                                equipped.dye()));
            }
            for (Item item : fits) {
                if (item.id().equals(equipped.item())) {
                    continue;
                }
                if (IkGui.selectable(
                        PreparedDesign.label(item.id()) + "##look-" + item.id(),
                        item.id().equals(equipped.lookAs()))) {
                    equip(
                            session,
                            slot,
                            new Equipment.Equipped(
                                    equipped.item(),
                                    equipped.state(),
                                    item.id(),
                                    equipped.hidden(),
                                    equipped.dye()));
                }
            }
            IkGui.endCombo();
        }
        if (IkGui.checkbox("Hidden##hidden", equipped.hidden())) {
            equip(
                    session,
                    slot,
                    new Equipment.Equipped(
                            equipped.item(),
                            equipped.state(),
                            equipped.lookAs(),
                            !equipped.hidden(),
                            equipped.dye()));
        }
    }

    /**
     * Put an item in a slot and show the change.
     *
     * @param session The design shown.
     * @param slot The slot.
     * @param equipped The item, or null to empty the slot.
     */
    private void equip(EditorSession session, String slot, Equipment.Equipped equipped) {
        Map<String, Equipment.Equipped> slots = new LinkedHashMap<>(session.equipment.slots());
        if (equipped == null) {
            slots.remove(slot);
        } else {
            slots.put(slot, equipped);
        }
        session.equipment = new Equipment(slots);
        applyEquipment(session);
    }

    /**
     * Put every held item that has a state in it.
     *
     * @param session The design shown.
     * @param state The state, like drawn.
     */
    private void setAllStates(EditorSession session, String state) {
        Map<String, Equipment.Equipped> slots = new LinkedHashMap<>();
        session.equipment
                .slots()
                .forEach((slot, equipped) -> slots.put(slot, equipped.withState(state)));
        session.equipment = new Equipment(slots);
        applyEquipment(session);
    }

    /**
     * Show the equipment on the character: its held meshes on their sockets and its items' effects.
     * The styles follow on the next frame, as the animation is rebuilt for them.
     *
     * @param session The design shown, or null while nothing is.
     */
    private void applyEquipment(EditorSession session) {
        if (session == null) {
            return;
        }
        CharacterRegistry definitions = registry.get();
        Design design =
                DesignSnapshot.of(
                        session.look,
                        session.plan,
                        session.prepared,
                        session.prepared.design().id(),
                        session.look.variants().get(session.look.variant()).id(),
                        true);
        LookStack.Shown stack = LookStack.resolve(design, session.equipment, definitions);
        List<Part> worn = new ArrayList<>();
        for (Id partId : stack.worn().values()) {
            Part part = definitions.get(Part.class, partId);
            if (part != null) {
                worn.add(part);
            }
        }
        session.shown.setHeld(
                heldItems.meshes(
                        stack.held(),
                        worn,
                        session.plan,
                        session.shown.getSkeleton(),
                        definitions));
        // A weapon in a hand readies the character to fight
        boolean drawn =
                stack.held().stream()
                        .anyMatch(
                                held ->
                                        held.socket().type() != null
                                                && held.socket().type().startsWith(GRIP_TYPE));
        motion.setCombat(drawn);
        motion.applyStates(session.shownEffects, session.shownUses);
        session.itemFx = List.copyOf(stack.fx().subList(design.fx().size(), stack.fx().size()));
    }

    /**
     * Rebuild the animation when the style changed, repeat actions, and fill the clip bar.
     *
     * @param session The design shown, or null while nothing is.
     */
    void tickMotion(EditorSession session) {
        if (session == null) {
            return;
        }
        if (!motionStyles.equals(styles(session))) {
            buildMotion(session, session.look.variants().get(session.look.variant()).id());
        }
        motion.tick();
        if (motion.active()) {
            String text = motion.barText();
            if (!text.equals(clipName.get())) {
                clipName.set(text);
            }
            String position = motion.positionText();
            if (!position.equals(clipPosition.get())) {
                clipPosition.set(position);
            }
        }
    }

    /**
     * Pause or play the clip.
     *
     * @param session The design shown, or null while nothing is.
     */
    void togglePlay(EditorSession session) {
        playing = !playing;
        playText.set(playing ? "Pause" : "Play");
        if (session != null) {
            session.shown.setPlaying(playing);
        }
    }
}
