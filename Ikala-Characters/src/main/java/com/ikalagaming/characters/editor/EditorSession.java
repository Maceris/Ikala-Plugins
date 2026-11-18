package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.DecalUse;
import com.ikalagaming.characters.definition.FxUse;
import com.ikalagaming.characters.design.Equipment;
import com.ikalagaming.characters.view.AccessoryRests;
import com.ikalagaming.characters.view.CharacterFx;
import com.ikalagaming.characters.view.PartCategories;
import com.ikalagaming.characters.view.PartForms;
import com.ikalagaming.characters.view.PartRegions;
import com.ikalagaming.characters.view.ShownCharacter;

import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the character editor keeps about the design it shows: the built design, the character on the
 * stage, the look being edited, and what was last put on the character, to change it only when that
 * changes. The editor makes one when the first design finishes loading after it opens, sets it up
 * again as each later design loads, and drops it when it closes, so the editor holds none while
 * nothing has been shown. While it exists, the design, plan, regions, categories, look and
 * character are all set. Render thread only, like the editor's UI.
 */
final class EditorSession {
    /** The design shown, built and sorted. */
    PreparedDesign prepared;

    /** The body plan the character's rig belongs to. */
    BodyPlan plan;

    /** What the body under the worn parts hides, for the shown character. */
    PartRegions regions;

    /** Its slots sorted into the body's regions. */
    PartCategories categories;

    /** What the character wears and its slider values. */
    CharacterLook look;

    /** The character shown. */
    ShownCharacter shown;

    /** The parts last shown, to change them only when they change. */
    List<Integer> lastWorn = List.of();

    /**
     * How far each shown part pushing out over bulky body parts is pushed over each slot, by part
     * index, to move one again only when that changes; see {@link PartForms}.
     */
    final Map<Integer, Map<String, Float>> pushed = new HashMap<>();

    /**
     * How far each shown accessory is moved out to rest on what is worn under it, by part index, to
     * move one again only when that changes; see {@link AccessoryRests}.
     */
    final Map<Integer, Vector3f> rested = new HashMap<>();

    /** The decal last shown, see {@link ShownSync}'s showDecal. */
    DecalUse shownDecal;

    /** Whether the shown character has the look's decal, {@link #shownDecal}. */
    boolean decalSynced;

    /** The look before the slider being dragged, taken while no slider is dragged. */
    CharacterLook.Snapshot beforeEdit;

    /** The look's version when {@link #beforeEdit} was taken. */
    int beforeEditVersion = -1;

    /** Whether a slider drag has been remembered for undo. */
    boolean editing;

    /** What the shown character has equipped. */
    Equipment equipment = Equipment.NONE;

    /** The effects the equipped items carry. */
    List<FxUse> itemFx = List.of();

    /** The look's version when the parts were last colored, or -1 to color them again. */
    int shownColorVersion = -1;

    /**
     * The colors the worn parts give palette channels, as of {@link #shownColorVersion}, see {@link
     * ShownSync}'s findPartColors.
     */
    Map<String, Integer> partColors = Map.of();

    /** The channel last previewed, or null. */
    String shownPreviewChannel;

    /** The swatch last previewed. */
    int shownPreviewCode;

    /** What the shown effects were built from, to tell when to build them again. */
    List<Object> shownFx = List.of();

    /** The effects on the shown character, from the parts and the design. */
    List<FxUse> shownUses = List.of();

    /** The shown character's effects, or null. */
    CharacterFx shownEffects;

    /** Which category is open, or -1 for none. */
    int openCategory = -1;

    /** Which clip of the character's is playing, or -1 for none. */
    int clipIndex = -1;

    /**
     * Let go of the shown character, as the editor closes or is abandoned: stops its timer, and
     * skips releasing graphics once they are closed. The session isn't used afterward.
     */
    void close() {
        shown.close();
        shown = null;
        pushed.clear();
        rested.clear();
    }
}
