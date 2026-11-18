package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.ColorCode;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Range;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Decal;
import com.ikalagaming.characters.definition.DecalUse;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.definition.Fx;
import com.ikalagaming.characters.definition.FxUse;
import com.ikalagaming.characters.definition.Palette;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.definition.Slider;
import com.ikalagaming.characters.design.DesignCodec;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.view.PartSlots;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.flags.ColorEditFlags;
import com.ikalagaming.graphics.gui.flags.HoveredFlags;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The character editor's look panel tabs that shape and color the look: the Scales tab with the
 * sliders, the Colors tab with the palette channels, and the Effects tab with the decal and the
 * particle effects worn. The editor keeps one for as long as it exists and still draws the tab bar
 * around them; the design shown is passed to each method as an {@link EditorSession} rather than
 * kept, so it can't be worked on after the session closes. What is kept here, the Colors tab's
 * channel and picker, stays between designs and across closing and opening the editor again, as it
 * did on the editor. Changes to the look go through the editor's change and undo paths, passed in.
 * Render thread only, like the editor's UI.
 */
final class LookTabs {
    /** No swatch hovered. */
    static final int NO_SWATCH = -1;

    /** The face table's column headings, for sliders with width, height and depth values. */
    private static final String[] AXES = {"Width", "Height", "Depth"};

    /** The gap between swatches, in pixels, small so a row of them fits the panel. */
    private static final float SWATCH_GAP = 2;

    /** The smallest effect scale offered, as effects can't be scaled to nothing. */
    private static final float MIN_FX_SCALE = 1 / DesignCodec.FACTOR_STEPS;

    /** The channels a newly chosen decal's colors take, until the user picks others. */
    private static final Map<String, String> DECAL_CHANNELS =
            Map.of(Decal.FIRST, Palette.ACCENT, Decal.SECOND, Palette.PRIMARY);

    /** The loaded definitions. */
    private final Supplier<CharacterRegistry> registry;

    /** Keeps the shown character in step with the look. */
    private final ShownSync sync;

    /** Changes the editor's look, then shows the change. */
    private final Consumer<Consumer<CharacterLook>> changeLook;

    /** Remembers the look before a drag for undo, once, when the drag starts changing it. */
    private final Runnable startEdit;

    /** The palette channel the Colors tab shows, or null before one is picked. */
    private String colorChannel;

    /** The color picker's color, sRGB. */
    private final float[] picker = {0.5f, 0.5f, 0.5f};

    /**
     * Set up the tabs; nothing is drawn until a session is passed in.
     *
     * @param registry The loaded definitions, read each time they are needed.
     * @param sync Keeps the shown character in step with the look.
     * @param changeLook Changes the editor's look, then shows the change.
     * @param startEdit Remembers the look before a drag for undo, once per drag.
     */
    LookTabs(
            Supplier<CharacterRegistry> registry,
            ShownSync sync,
            Consumer<Consumer<CharacterLook>> changeLook,
            Runnable startEdit) {
        this.registry = registry;
        this.sync = sync;
        this.changeLook = changeLook;
        this.startEdit = startEdit;
    }

    /**
     * The palette channel the Colors tab shows, which a hovered swatch previews on.
     *
     * @return The channel, or null before one is picked.
     */
    String colorChannel() {
        return colorChannel;
    }

    /**
     * Draw the sliders, grouped as they say.
     *
     * @param session The design shown.
     */
    void drawScales(EditorSession session) {
        String group = null;
        boolean changed = false;
        for (Slider slider : session.look.sliders()) {
            String sliderGroup = groupOf(slider);
            if (!sliderGroup.equals(group)) {
                group = sliderGroup;
                IkGui.separatorText(group);
                if (hasVectors(session, group)) {
                    drawAxisHeadings();
                }
            }
            Range range = session.look.range(slider, CharacterEditor.SLIDER_CONTEXT);
            float[] value = session.look.values().get(slider.id());
            String label = Objects.requireNonNullElse(slider.label(), slider.id().toString());
            // A full helmet locks the face's sliders, as there is no face to shape
            Part locking = session.look.lockedBy(slider);
            IkGui.beginDisabled(locking != null);
            if (slider.vector()) {
                changed |= IkGui.sliderFloat3(label, value, range.min(), range.max());
            } else {
                changed |= IkGui.sliderFloat(label, value, range.min(), range.max());
            }
            IkGui.endDisabled();
            if (locking != null && IkGui.isItemHovered(HoveredFlags.ALLOW_WHEN_DISABLED)) {
                IkGui.setTooltip("Locked by " + PreparedDesign.label(locking.id()));
            }
        }
        if (changed) {
            startEdit.run();
            session.look.touch();
            sync.applySliders(session);
        }
    }

    /**
     * Draw the Effects tab: each effect the design wears, with what it is, where it goes, its color
     * and its tuning, and a button to add another, up to {@link Design#MAX_FX}.
     *
     * @param session The design shown.
     */
    void drawEffects(EditorSession session) {
        drawDecal(session);
        IkGui.separatorText("Effects");
        List<Fx> all = registry.get().all(Fx.class);
        if (all.isEmpty()) {
            IkGui.textDisabled("No effects loaded");
            return;
        }
        List<FxUse> uses = session.look.fx();
        for (int i = 0; i < uses.size(); ++i) {
            IkGui.pushID(i);
            IkGui.separatorText("Effect " + (i + 1));
            drawEffect(session, i, uses.get(i), all);
            IkGui.popID();
        }
        IkGui.spacing();
        if (uses.size() < Design.MAX_FX && IkGui.button("Add effect")) {
            String socket =
                    session.plan.sockets().isEmpty() ? null : session.plan.sockets().get(0).id();
            String bone =
                    socket == null
                            ? session.prepared.character().rig().bones().get(0).name()
                            : null;
            changeLook.accept(
                    changed ->
                            changed.addFx(
                                    new FxUse(
                                            all.get(0).id(),
                                            bone,
                                            socket,
                                            null,
                                            null,
                                            1,
                                            1,
                                            1,
                                            FxUse.ALWAYS,
                                            null,
                                            false)));
        }
        if (uses.size() >= Design.MAX_FX) {
            IkGui.textDisabled("A design wears up to " + Design.MAX_FX + " effects");
        }
    }

    /**
     * Draw the decal rows: which art, on which of the plan's places, and the palette channel each
     * of its colors takes.
     *
     * @param session The design shown.
     */
    private void drawDecal(EditorSession session) {
        IkGui.separatorText("Decal");
        List<Decal> all = registry.get().all(Decal.class);
        if (all.isEmpty() || session.plan.decals().isEmpty()) {
            IkGui.textDisabled("No decals for this body");
            return;
        }
        DecalUse use = session.look.decal();
        if (IkGui.beginCombo(
                "Decal##decal", use == null ? "none" : PreparedDesign.label(use.decal()))) {
            if (IkGui.selectable("none##no-decal", use == null)) {
                changeLook.accept(changed -> changed.setDecal(null));
            }
            for (Decal decal : all) {
                boolean chosen = use != null && decal.id().equals(use.decal());
                if (IkGui.selectable(
                        PreparedDesign.label(decal.id()) + "##" + decal.id(), chosen)) {
                    String place = use == null ? session.plan.decals().get(0).id() : use.place();
                    Map<String, String> channels = use == null ? DECAL_CHANNELS : use.channels();
                    changeLook.accept(
                            changed -> changed.setDecal(new DecalUse(decal.id(), place, channels)));
                }
            }
            IkGui.endCombo();
        }
        if (use == null) {
            return;
        }
        if (session.plan.decals().size() > 1 && IkGui.beginCombo("On##decal-place", use.place())) {
            for (BodyPlan.DecalPlace place : session.plan.decals()) {
                if (IkGui.selectable(
                        place.id() + "##" + place.id(), place.id().equals(use.place()))) {
                    changeLook.accept(
                            changed ->
                                    changed.setDecal(
                                            new DecalUse(use.decal(), place.id(), use.channels())));
                }
            }
            IkGui.endCombo();
        }
        String[] labels = {"Fill##decal-r", "Edge##decal-g"};
        List<String> masks = List.of(Decal.FIRST, Decal.SECOND);
        for (int c = 0; c < masks.size(); ++c) {
            String mask = masks.get(c);
            String channel = use.channels().get(mask);
            if (IkGui.beginCombo(labels[c], channel == null ? "its own" : channel)) {
                if (IkGui.selectable("its own##own-" + mask, channel == null)) {
                    changeLook.accept(changed -> changed.setDecal(recolored(use, mask, null)));
                }
                for (String other : session.plan.allChannels()) {
                    if (IkGui.selectable(
                            other + "##" + mask + "-" + other, other.equals(channel))) {
                        changeLook.accept(changed -> changed.setDecal(recolored(use, mask, other)));
                    }
                }
                IkGui.endCombo();
            }
        }
    }

    /**
     * A decal with one of its colors taking another palette channel.
     *
     * @param use The decal.
     * @param mask The mask channel, {@link Decal#FIRST} or {@link Decal#SECOND}.
     * @param channel The palette channel, or null for the decal's default color.
     * @return The decal.
     */
    private static DecalUse recolored(DecalUse use, String mask, String channel) {
        Map<String, String> channels = new LinkedHashMap<>(use.channels());
        if (channel == null) {
            channels.remove(mask);
        } else {
            channels.put(mask, channel);
        }
        return new DecalUse(use.decal(), use.place(), channels);
    }

    /**
     * For scripts: wear a decal, or none. Call on the render thread.
     *
     * @param session The design shown, or null if none is.
     * @param decalId The decal's ID, or null or empty for none.
     * @param place The plan's decal place, or null for its first.
     * @param first The palette channel the decal's first color takes, or null for its default.
     * @param second The channel its second color takes, or null.
     * @return False if nothing is shown or the plan has no decal places.
     */
    boolean wearDecal(
            EditorSession session, String decalId, String place, String first, String second) {
        if (session == null || session.plan.decals().isEmpty()) {
            return false;
        }
        if (decalId == null || decalId.isBlank()) {
            changeLook.accept(changed -> changed.setDecal(null));
            return true;
        }
        Map<String, String> channels = new LinkedHashMap<>();
        if (first != null && !first.isBlank()) {
            channels.put(Decal.FIRST, first);
        }
        if (second != null && !second.isBlank()) {
            channels.put(Decal.SECOND, second);
        }
        String where = place == null || place.isBlank() ? session.plan.decals().get(0).id() : place;
        changeLook.accept(
                changed -> changed.setDecal(new DecalUse(Id.parse(decalId), where, channels)));
        return true;
    }

    /**
     * Draw one effect's rows.
     *
     * @param session The design shown.
     * @param index Its position.
     * @param use The effect.
     * @param all The loaded effects.
     */
    private void drawEffect(EditorSession session, int index, FxUse use, List<Fx> all) {
        if (IkGui.beginCombo("Effect##fx", PreparedDesign.label(use.fx()))) {
            for (Fx fx : all) {
                if (IkGui.selectable(
                        PreparedDesign.label(fx.id()) + "##" + fx.id(), fx.id().equals(use.fx()))) {
                    changeLook.accept(changed -> changed.setFx(index, withFx(use, fx.id())));
                }
            }
            IkGui.endCombo();
        }
        String place = use.socket() != null ? use.socket() : use.bone();
        if (IkGui.beginCombo("On##place", place)) {
            for (BodyPlan.Socket socket : session.plan.sockets()) {
                if (IkGui.selectable(
                        socket.id() + "##socket-" + socket.id(),
                        socket.id().equals(use.socket()))) {
                    changeLook.accept(
                            changed -> changed.setFx(index, onPlace(use, null, socket.id())));
                }
            }
            IkGui.separator();
            for (Rig.Bone bone : session.prepared.character().rig().bones()) {
                if (bone.kind() != Rig.BoneKind.DEFORM) {
                    continue;
                }
                if (IkGui.selectable(
                        bone.name() + "##bone-" + bone.name(), bone.name().equals(use.bone()))) {
                    changeLook.accept(
                            changed -> changed.setFx(index, onPlace(use, bone.name(), null)));
                }
            }
            IkGui.endCombo();
        }
        String color = use.color() == null ? "its own" : use.color();
        if (IkGui.beginCombo("Color##color", color)) {
            if (IkGui.selectable("its own##own", use.color() == null)) {
                changeLook.accept(changed -> changed.setFx(index, colored(use, null)));
            }
            for (String channel : session.plan.allChannels()) {
                if (IkGui.selectable(channel + "##" + channel, channel.equals(use.color()))) {
                    changeLook.accept(changed -> changed.setFx(index, colored(use, channel)));
                }
            }
            IkGui.endCombo();
        }
        if (IkGui.beginCombo("When##when", use.when())) {
            List<String> whens = new ArrayList<>(List.of(FxUse.ALWAYS, FxUse.MOVING));
            whens.addAll(CharacterMotion.toggles(session.shownUses));
            for (String when : whens) {
                if (IkGui.selectable(when + "##when-" + when, when.equals(use.when()))) {
                    changeLook.accept(changed -> changed.setFx(index, use.withWhen(when)));
                }
            }
            IkGui.endCombo();
        }
        float[] scale = {use.scale()};
        float[] rate = {use.rate()};
        float[] intensity = {use.intensity()};
        boolean changed =
                IkGui.sliderFloat("Scale##scale", scale, MIN_FX_SCALE, FxUse.FACTORS.max());
        changed |= IkGui.sliderFloat("Rate##rate", rate, 0, FxUse.FACTORS.max());
        changed |= IkGui.sliderFloat("Glow##intensity", intensity, 0, FxUse.FACTORS.max());
        if (changed) {
            startEdit.run();
            session.look.dragFx(
                    index,
                    new FxUse(
                            use.fx(),
                            use.bone(),
                            use.socket(),
                            use.offset(),
                            use.color(),
                            Math.max(MIN_FX_SCALE, DesignCodec.snapFactor(scale[0])),
                            DesignCodec.snapFactor(rate[0]),
                            DesignCodec.snapFactor(intensity[0]),
                            use.when(),
                            use.to(),
                            use.turn()));
        }
        if (IkGui.button("Remove")) {
            changeLook.accept(changed2 -> changed2.setFx(index, null));
        }
    }

    /**
     * An effect with another effect definition.
     *
     * @param use The effect.
     * @param fx The new definition.
     * @return The changed effect.
     */
    private static FxUse withFx(FxUse use, Id fx) {
        return new FxUse(
                fx,
                use.bone(),
                use.socket(),
                use.offset(),
                use.color(),
                use.scale(),
                use.rate(),
                use.intensity(),
                use.when(),
                use.to(),
                use.turn());
    }

    /**
     * An effect on another bone or socket, without its offset.
     *
     * @param use The effect.
     * @param bone The bone, or null.
     * @param socket The socket, or null.
     * @return The changed effect.
     */
    private static FxUse onPlace(FxUse use, String bone, String socket) {
        return new FxUse(
                use.fx(),
                bone,
                socket,
                null,
                use.color(),
                use.scale(),
                use.rate(),
                use.intensity(),
                use.when(),
                use.to(),
                use.turn());
    }

    /**
     * An effect with another color.
     *
     * @param use The effect.
     * @param channel The palette channel, or null for the effect's own colors.
     * @return The changed effect.
     */
    private static FxUse colored(FxUse use, String channel) {
        return new FxUse(
                use.fx(),
                use.bone(),
                use.socket(),
                use.offset(),
                channel,
                use.scale(),
                use.rate(),
                use.intensity(),
                use.when(),
                use.to(),
                use.turn());
    }

    /**
     * Draw the Colors tab: the palette channels the parts take, the chosen channel's swatches,
     * which preview on the character while hovered, and a picker that snaps to the colors a design
     * can hold.
     *
     * @param session The design shown.
     * @return The color code of the swatch hovered, or {@link #NO_SWATCH}.
     */
    int drawColors(EditorSession session) {
        List<String> channels = session.look.channels();
        if (channels.isEmpty()) {
            IkGui.textDisabled("Nothing to color");
            return NO_SWATCH;
        }
        if (colorChannel == null || !channels.contains(colorChannel)) {
            colorChannel = channels.get(0);
        }
        float swatch = IkGui.getFrameHeight();
        for (String channel : channels) {
            Integer code = session.look.color(channel);
            IkGui.colorButton(
                    "##channel-" + channel,
                    rgba(sync.channelColor(session, channel)),
                    ColorEditFlags.NO_TOOLTIP,
                    swatch,
                    swatch);
            IkGui.sameLine();
            String name = channel.substring(0, 1).toUpperCase(Locale.ROOT) + channel.substring(1);
            String text = code == null ? name + "  (the parts' own colors)" : name;
            if (IkGui.selectable(text + "##" + channel, channel.equals(colorChannel))) {
                colorChannel = channel;
            }
        }
        IkGui.separatorText(colorChannel);

        int hovered = NO_SWATCH;
        List<Integer> swatches = Swatches.forChannel(colorChannel);
        // A fixed size: sizing them to the panel's width made a scrollbar come and go each frame
        float size = swatch;
        for (int i = 0; i < swatches.size(); ++i) {
            if (i % Swatches.COLUMNS != 0) {
                IkGui.sameLine(0, SWATCH_GAP);
            }
            int code = swatches.get(i);
            if (IkGui.colorButton(
                    "##swatch" + i, rgba(code), ColorEditFlags.NO_TOOLTIP, size, size)) {
                changeLook.accept(changed -> changed.setColor(colorChannel, code));
            }
            if (IkGui.isItemHovered()) {
                hovered = code;
            }
        }

        // The picker snaps to the colors a design can hold
        Integer current = session.look.color(colorChannel);
        Integer shownColor = sync.channelColor(session, colorChannel);
        if (shownColor != null) {
            ColorCode.decode(shownColor, picker);
        }
        IkGui.spacing();
        if (IkGui.colorPicker3(
                "##picker",
                picker,
                ColorEditFlags.NO_SIDE_PREVIEW
                        | ColorEditFlags.NO_INPUTS
                        | ColorEditFlags.NO_ALPHA)) {
            startEdit.run();
            session.look.dragColor(colorChannel, ColorCode.encode(picker[0], picker[1], picker[2]));
        }
        if (current != null) {
            IkGui.textDisabled(
                    String.format(
                            Locale.ROOT, "%s, code %04x", ColorCode.toColor(current), current));
        }
        if (IkGui.button("Use the parts' own colors")) {
            changeLook.accept(changed -> changed.setColor(colorChannel, null));
        }
        if (channelUntinted(session, colorChannel)) {
            IkGui.textWrapped(
                    "These parts are painted textures without tint masks, so they keep their own"
                            + " colors.");
        }
        return hovered;
    }

    /**
     * Whether no part whose own channel this is takes tints, so choosing its color shows nothing.
     *
     * @param session The design shown.
     * @param channel The palette channel.
     * @return True if none does.
     */
    private static boolean channelUntinted(EditorSession session, String channel) {
        boolean any = false;
        for (PartSlots.Slot slot : session.look.slots().slots()) {
            if (!PartSlots.channelOf(slot).equals(channel)) {
                continue;
            }
            for (List<Integer> option : slot.options()) {
                for (int part : option) {
                    if (session.shown.isTinted(part)) {
                        return false;
                    }
                    any = true;
                }
            }
        }
        return any;
    }

    /**
     * A color code as IkGui's RGBA, or a mid gray for the parts' own colors.
     *
     * @param code The code, or null.
     * @return The color.
     */
    private static float[] rgba(Integer code) {
        float[] color = {0.5f, 0.5f, 0.5f, 1};
        if (code != null) {
            ColorCode.decode(code, color);
        }
        return color;
    }

    /**
     * Whether any slider in a group has width, height and depth values.
     *
     * @param session The design shown.
     * @param group The group.
     * @return True if one does.
     */
    private static boolean hasVectors(EditorSession session, String group) {
        return session.look.sliders().stream()
                .anyMatch(slider -> slider.vector() && groupOf(slider).equals(group));
    }

    /** Write Width, Height and Depth over the three columns of the sliders below. */
    private static void drawAxisHeadings() {
        float start = IkGui.getCursorPosX();
        float spacing = IkGui.getStyle().variable.itemInnerSpacing.x;
        float column = (IkGui.calcItemWidth() - 2 * spacing) / AXES.length;
        for (int axis = 0; axis < AXES.length; ++axis) {
            float textWidth = IkGui.calcTextSize(AXES[axis]).x;
            float x = start + axis * (column + spacing) + (column - textWidth) / 2;
            if (axis > 0) {
                IkGui.sameLine();
            }
            IkGui.setCursorPosX(x);
            IkGui.textDisabled(AXES[axis]);
        }
    }

    /**
     * The editor group a slider is shown in, which the editor also sorts a plan's sliders by.
     *
     * @param slider The slider.
     * @return Its group, or "other".
     */
    static String groupOf(Slider slider) {
        return Objects.requireNonNullElse(slider.group(), "other");
    }
}
