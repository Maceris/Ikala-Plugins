package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.ColorCode;
import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.data.Range;
import com.ikalagaming.characters.definition.AnimStyle;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.definition.Fx;
import com.ikalagaming.characters.definition.FxUse;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.definition.Slider;
import com.ikalagaming.characters.demo.CharacterPreview;
import com.ikalagaming.characters.design.DesignCodec;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.Pose;
import com.ikalagaming.characters.pose.PoseSolver;
import com.ikalagaming.characters.pose.Proportions;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.view.CharacterFx;
import com.ikalagaming.characters.view.FxEffects;
import com.ikalagaming.characters.view.PartAssembler;
import com.ikalagaming.characters.view.PartCategories;
import com.ikalagaming.characters.view.PartRegions;
import com.ikalagaming.characters.view.PartSlots;
import com.ikalagaming.characters.view.ShownCharacter;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Skins;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.ColorEditFlags;
import com.ikalagaming.graphics.gui.flags.SelectableFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.scene.Camera;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.ui.Immediate;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.ui.spec.Observable;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.SpecLoader;
import com.ikalagaming.random.SeededRandom;

import com.google.gson.GsonBuilder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The character editor: a character standing on a {@link CharacterStage}, an orbit camera, a part
 * panel choosing what fills each slot, a slider panel shaping the body, a clip bar playing its
 * animations, and undo, random and clear buttons. The panels are UI specs bundled with the plugin,
 * so any program with the plugin can open the editor, like the asset editor's tool menu or the
 * game's character creation.
 *
 * <p>The part panel groups the slots into the body's regions, one open at a time, and the camera
 * glides to frame the open one. Each slot's dropdown puts the hovered choice on the character while
 * it is open, as a preview; clicking keeps it.
 *
 * <p>The editor works from the loaded designs: each is built out of its parts' meshes by {@link
 * PartAssembler}, its plan's slots and layers become the panel's slots, and saving writes the
 * current look as a design into the content folder the plugin's config names. Slots are sorted into
 * regions by height with {@link PartCategories}. Render thread only, apart from loading characters.
 *
 * @author Ches Burks
 */
@Slf4j
public final class CharacterEditor {
    /** The tool's ID, in editors' tool menus. */
    public static final String TOOL_ID = "characters/editor";

    /** The tool's label, in editors' tool menus. */
    public static final String LABEL = "Character Editor";

    /** The part panel's spec, in the plugin's jar. */
    static final String PARTS_SPEC = "/ui/character-editor/parts.yml";

    /** The slider panel's spec, in the plugin's jar. */
    static final String SLIDERS_SPEC = "/ui/character-editor/sliders.yml";

    /** What the style dropdown calls having no style. */
    static final String PLAN_STYLE = "the plan's own";

    /** The clip bar's spec, in the plugin's jar. */
    static final String CLIPS_SPEC = "/ui/character-editor/clips.yml";

    /** The undo, random and clear buttons' spec, in the plugin's jar. */
    static final String ACTIONS_SPEC = "/ui/character-editor/actions.yml";

    /** The node type that draws the sliders, for the slider panel's spec. */
    public static final String SLIDERS_NODE = "characters.sliders";

    /** The node type that draws the slot categories and dropdowns, for the part panel's spec. */
    public static final String PARTS_NODE = "characters.parts";

    /** The handler that shows the character before in the list. */
    static final String PREVIOUS_CHARACTER = "previous-character";

    /** The handler that shows the character after in the list. */
    static final String NEXT_CHARACTER = "next-character";

    /** The handler that closes the editor. */
    static final String CLOSE = "close";

    /** The handler that undoes the last change to the look. */
    static final String UNDO = "undo";

    /** The handler that picks random parts and sliders. */
    static final String RANDOMIZE = "randomize";

    /** The handler that takes off everything that can be taken off. */
    static final String CLEAR = "clear";

    /** The handler that puts every slider back to 0. */
    static final String RESET_SLIDERS = "reset-sliders";

    /** The handler that plays the clip before. */
    static final String PREVIOUS_CLIP = "previous-clip";

    /** The handler that plays the clip after. */
    static final String NEXT_CLIP = "next-clip";

    /** The handler that pauses or plays the clip. */
    static final String TOGGLE_PLAY = "toggle-play";

    /** The value naming the character shown. */
    static final String CHARACTER_NAME = "character.name";

    /** The value saying what the sliders shape, or why there are none. */
    static final String SLIDERS_TITLE = "sliders.title";

    /** The value naming the clip playing. */
    static final String CLIP_NAME = "clip.name";

    /** The value saying which clip of how many is playing. */
    static final String CLIP_POSITION = "clip.position";

    /** The value for the play button's text. */
    static final String PLAY_TEXT = "clip.play";

    /** The window that reads the mouse for the camera; it draws nothing. */
    static final String INPUT_WINDOW = "Character editor input";

    /** The context sliders are limited to, as character creation for players. */
    private static final String SLIDER_CONTEXT = Slider.CONTEXT_PLAYER;

    /** What an empty slot's dropdown says. */
    private static final String EMPTY_LABEL = "none";

    /** How far the mouse must move with a button down to drag, in pixels. */
    private static final float DRAG_SLOP = 2;

    /** How high on the character the camera looks, in meters. */
    private static final double LOOK_HEIGHT = 1.0;

    /** How far away the camera starts, in meters. */
    private static final double LOOK_DISTANCE = 4.2;

    /** How far down the camera starts looking, in radians. */
    private static final double LOOK_PITCH = 0.12;

    /** How far the camera starts turned to the character's side, in radians. */
    private static final double LOOK_YAW = -0.35;

    /** The smallest radius framed, so a small region like the head isn't filled edge to edge. */
    private static final double MIN_FRAME_RADIUS = 0.35;

    /** Where the part panel's dropdowns start, after the slot names, in pixels. */
    private static final int NAME_COLUMN = 64;

    /** The room IkGui leaves between items on a row, in pixels, give or take. */
    private static final float ROW_SPACING = 12;

    /** How tall a variant's card is, in rows of text. */
    private static final float CARD_ROWS = 1.6f;

    /** Floats per vertex in a part's vertices. */
    private static final int VERTEX_FLOATS = 14;

    /** Bone weights per vertex. */
    private static final int INFLUENCES = 4;

    /** How many variant cards go on a row. */
    private static final int CARDS_PER_ROW = 3;

    /** How far to the side of the character the ruler stands, in meters. */
    private static final double RULER_OFFSET = 0.8;

    /** How tall the ruler is, in meters. */
    private static final float RULER_HEIGHT = 2.5f;

    /** The distance between the ruler's small marks, in meters. */
    private static final float RULER_STEP = 0.25f;

    /** How long the ruler's marks are, in pixels; every other one is twice as long. */
    private static final float RULER_MARK = 6;

    /** The ruler's color. */
    private static final int RULER_COLOR =
            com.ikalagaming.graphics.gui.util.Color.rgba(1.0f, 1.0f, 1.0f, 0.8f);

    /** The color of the line at the character's height. */
    private static final int HEIGHT_COLOR =
            com.ikalagaming.graphics.gui.util.Color.rgba(1.0f, 0.75f, 0.2f, 0.95f);

    /** Meters per inch, for showing heights in feet and inches too. */
    private static final double METERS_PER_INCH = 0.0254;

    /** The face table's column headings, for sliders with width, height and depth values. */
    private static final String[] AXES = {"Width", "Height", "Depth"};

    /** The gap between swatches, in pixels, small so a row of them fits the panel. */
    private static final float SWATCH_GAP = 2;

    /** No swatch hovered. */
    private static final int NO_SWATCH = -1;

    /** How many characters the save name holds. */
    private static final int SAVE_NAME_CAPACITY = 64;

    /** The smallest effect scale offered, as effects can't be scaled to nothing. */
    private static final float MIN_FX_SCALE = 1 / DesignCodec.FACTOR_STEPS;

    /** Room left beside the save name for its button, in pixels. */
    private static final float SAVE_BUTTON_WIDTH = 60;

    /** Milliseconds per second, for IkGui's frame time. */
    private static final double MILLISECONDS = 1000;

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** The plugin's name, which owns what is shown. */
    private final String plugin;

    /** The loaded definitions. */
    private final Supplier<CharacterRegistry> registry;

    /** The characters to choose from, and the rig reference for moving them, from the config. */
    private final Supplier<EditorSettings> settings;

    /** The stage. */
    private final CharacterStage stage;

    /** Turns the camera. */
    private final OrbitCamera orbit = new OrbitCamera();

    /** What the body under the worn parts hides, for the shown character. */
    private PartRegions regions;

    /**
     * A design built from its plan's parts and sorted into slots, ready to show, which is slow
     * enough to do off the render thread. Every part of the plan with a mesh for the design's
     * variant is loaded, so choosing parts doesn't load anything.
     *
     * @param design The design.
     * @param plan Its plan.
     * @param character Every part's meshes, merged, with base parts split along the regions'
     *     borders.
     * @param slots The plan's slots and layers that have parts, each offering the parts that fill
     *     it first.
     * @param regions What the layers cover.
     * @param optionIds For each slot, the part each of its options is.
     * @param fills For each slot, the places each of its options fills.
     * @param channels For each of the character's parts, the palette channel each tint mask channel
     *     takes, as a position in the plan's channels.
     * @param channelNames The palette channels the parts use, in the plan's order.
     */
    private record Prepared(
            Design design,
            BodyPlan plan,
            ImportedCharacter character,
            PartSlots slots,
            PartRegions regions,
            List<List<Id>> optionIds,
            List<List<Set<String>>> fills,
            List<int[]> channels,
            List<String> channelNames) {
        /**
         * Build a design's parts and sort them.
         *
         * @param design The design.
         * @param registry The loaded definitions.
         * @param reference The rig's reference file.
         * @param files Files read before, added to.
         * @return The prepared design.
         * @throws IllegalArgumentException If its plan or parts can't be loaded.
         */
        static Prepared build(
                Design design,
                CharacterRegistry registry,
                Path reference,
                Map<Path, ImportedCharacter> files) {
            BodyPlan plan = registry.get(BodyPlan.class, design.plan());
            if (plan == null) {
                throw new IllegalArgumentException("No loaded plan " + design.plan());
            }
            Map<String, Id> worn = design.worn(plan, id -> registry.get(Part.class, id));
            Set<Id> wornIds = new HashSet<>(worn.values());
            // The worn parts first, so their files' clips are the ones kept
            List<Part> candidates =
                    registry.all(Part.class).stream()
                            .filter(
                                    part ->
                                            part.plan().equals(plan.id())
                                                    && part.kind() != Part.PartKind.ACCESSORY
                                                    && !part.fills().isEmpty()
                                                    && PartAssembler.meshFor(part, design.variant())
                                                            != null)
                            .sorted(Comparator.comparing(part -> !wornIds.contains(part.id())))
                            .toList();
            PartAssembler.Assembled assembled =
                    PartAssembler.assemble(
                            candidates, plan, design.variant(), registry, reference, files);
            if (assembled == null) {
                throw new IllegalArgumentException("None of " + plan.id() + "'s parts loaded");
            }

            List<String> places = new ArrayList<>();
            plan.slots().forEach(slot -> places.add(slot.id()));
            plan.layers().forEach(layer -> places.add(layer.id()));
            List<PartSlots.Slot> slots = new ArrayList<>();
            List<List<Id>> optionIds = new ArrayList<>();
            List<List<Set<String>>> fills = new ArrayList<>();
            for (String place : places) {
                boolean layer = plan.layer(place) != null;
                List<List<Integer>> options = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                List<Id> ids = new ArrayList<>();
                List<Set<String>> optionFills = new ArrayList<>();
                for (Part part :
                        candidates.stream().sorted(Comparator.comparing(Part::id)).toList()) {
                    boolean kind =
                            part.kind() == (layer ? Part.PartKind.LAYER : Part.PartKind.BODY);
                    List<Integer> meshes = assembled.partsOf(part.id());
                    if (!kind || !part.fills().get(0).equals(place) || meshes.isEmpty()) {
                        continue;
                    }
                    options.add(meshes);
                    labels.add(label(part.id()));
                    ids.add(part.id());
                    optionFills.add(Set.copyOf(part.fills()));
                }
                if (options.isEmpty()) {
                    continue;
                }
                int initial = ids.indexOf(worn.get(place));
                // Layers can be empty; body slots only while another part covers them
                slots.add(new PartSlots.Slot(place, options, labels, layer, initial));
                optionIds.add(ids);
                fills.add(optionFills);
            }

            ImportedCharacter character = assembled.character();
            boolean[] base = new boolean[character.parts().size()];
            boolean[] wearable = new boolean[character.parts().size()];
            for (int p = 0; p < base.length; ++p) {
                Part part = registry.get(Part.class, assembled.partIds().get(p));
                base[p] = part.kind() == Part.PartKind.BODY;
                wearable[p] = part.kind() == Part.PartKind.LAYER;
            }
            PartRegions regions = PartRegions.of(character, base, wearable);

            List<String> all = plan.allChannels();
            Set<Integer> used = new TreeSet<>();
            for (int[] mapped : assembled.channels()) {
                for (int channel : mapped) {
                    if (channel >= 0) {
                        used.add(channel);
                    }
                }
            }
            return new Prepared(
                    design,
                    plan,
                    regions.character(),
                    new PartSlots(slots),
                    regions,
                    optionIds,
                    fills,
                    assembled.channels(),
                    used.stream().map(all::get).toList());
        }

        /**
         * What an editor calls a part: the last step of its ID's path, with spaces.
         *
         * @param id The part's ID.
         * @return The label.
         */
        private static String label(Id id) {
            String path = id.path();
            return path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        }
    }

    /** The character's name. */
    private final Observable<String> characterName = Observable.of("");

    /** The slider panel's title. */
    private final Observable<String> slidersTitle = Observable.of("");

    /** The clip's name. */
    private final Observable<String> clipName = Observable.of("");

    /** Which clip of how many. */
    private final Observable<String> clipPosition = Observable.of("");

    /** The play button's text. */
    private final Observable<String> playText = Observable.of("Pause");

    /** The open panels. */
    private final List<SpecInstance> panels = new ArrayList<>();

    /** The way back to whatever opened the editor, or null while closed. */
    private Runnable back;

    /** Counts characters asked for, so a slow load that was replaced is dropped. */
    private int loads;

    /** The settings while open. */
    private EditorSettings current;

    /** Which of the loaded designs is shown. */
    private int designIndex;

    /** The design shown, built and sorted, or null while loading. */
    private Prepared prepared;

    /** Files read while the editor is open, on their plan's rig, for switching designs fast. */
    private final Map<Path, ImportedCharacter> readFiles = new ConcurrentHashMap<>();

    /** The name to save the design as. */
    private final IkString saveName = new IkString(SAVE_NAME_CAPACITY);

    /** What the last save did, shown under the name. */
    private String saveStatus = "";

    /** The particle effects characters wear, loaded while the editor is open. */
    private FxEffects fxEffects;

    /** What the shown effects were built from, to tell when to build them again. */
    private List<Object> shownFx = List.of();

    /** The effects on the shown character, from the parts and the design. */
    private List<FxUse> shownUses = List.of();

    /** The shown character's effects, or null. */
    private CharacterFx shownEffects;

    /** The shown character's animation and the Motion tab. */
    private final CharacterMotion motion = new CharacterMotion();

    /** The style the animation was last built for. */
    private Id motionStyle;

    /** The character shown, or null while loading or if it failed. */
    private ShownCharacter shown;

    /** What the character wears and its slider values, or null while nothing is shown. */
    private CharacterLook look;

    /** Its slots sorted into the body's regions. */
    private PartCategories categories;

    /** The palette channel the Colors tab shows, or null before one is picked. */
    private String colorChannel;

    /** The color picker's color, sRGB. */
    private final float[] picker = {0.5f, 0.5f, 0.5f};

    /** The look's version when the parts were last colored, or -1 to color them again. */
    private int shownColorVersion = -1;

    /** The channel last previewed, or null. */
    private String shownPreviewChannel;

    /** The swatch last previewed. */
    private int shownPreviewCode;

    /** Whether the Body tab is showing, rather than the Costume tab. */
    private boolean bodyTab = true;

    /** How tall the character is with the sliders as they are, in meters. */
    private float characterHeight;

    /** Which category is open, or -1 for none. */
    private int openCategory = -1;

    /** The parts last shown, to change them only when they change. */
    private List<Integer> lastWorn = List.of();

    /** The body plan the character's rig belongs to, or null. */
    private BodyPlan plan;

    /** The look before the slider being dragged, taken while no slider is dragged. */
    private CharacterLook.Snapshot beforeEdit;

    /** The look's version when {@link #beforeEdit} was taken. */
    private int beforeEditVersion = -1;

    /** Whether a slider drag has been remembered for undo. */
    private boolean editing;

    /** Which clip of the character's is playing, or -1 for none. */
    private int clipIndex = -1;

    /** Whether the clip's time moves on. */
    private boolean playing = true;

    /** Whether the left button went down over the stage rather than a panel. */
    private boolean pressedOnStage;

    /** Whether the middle button went down over the stage rather than a panel. */
    private boolean middlePressedOnStage;

    /**
     * What the editor reads from the config.
     *
     * @param reference The rig reference file, for moving characters from other rigs onto it.
     * @param saveNamespace The namespace saved designs go in, or null if saving is off.
     * @param saveFolder The content folder saved designs go in, as design/name.json, or null.
     * @param reload Loads the definitions again, so saved designs show up.
     */
    public record EditorSettings(
            @NonNull Path reference,
            String saveNamespace,
            Path saveFolder,
            @NonNull Runnable reload) {}

    /**
     * Set up the editor; nothing is shown until it opens.
     *
     * @param graphics The plugin's graphics context.
     * @param plugin The plugin's name.
     * @param registry The loaded definitions, read each time a character is shown.
     * @param settings What to show, read each time the editor opens.
     */
    public CharacterEditor(
            @NonNull GraphicsContext graphics,
            @NonNull String plugin,
            @NonNull Supplier<CharacterRegistry> registry,
            @NonNull Supplier<EditorSettings> settings) {
        this.graphics = graphics;
        this.plugin = plugin;
        this.registry = registry;
        this.settings = settings;
        stage = new CharacterStage(graphics);
    }

    /** Register the panels' node types, once, when the plugin is enabled. */
    public void install() {
        graphics.ui()
                .registerNodeType(
                        SLIDERS_NODE, (id, properties) -> new Immediate(id, this::drawSliders));
        graphics.ui()
                .registerNodeType(
                        PARTS_NODE, (id, properties) -> new Immediate(id, this::drawParts));
    }

    /**
     * Whether the editor is open.
     *
     * @return True if open.
     */
    public boolean isOpen() {
        return back != null;
    }

    /**
     * Open the editor on the first character in the config. Nothing happens if it is open.
     *
     * @param goBack What to run when it closes, like showing the menu that opened it again.
     */
    public void open(@NonNull Runnable goBack) {
        if (back != null) {
            return;
        }
        back = goBack;
        current = settings.get();
        fxEffects = new FxEffects(plugin);
        // Effect files follow their edits while the editor is open
        graphics.particles().setHotReload(true);
        stage.enter();
        Scene scene = GraphicsManager.getScene();
        orbit.look(
                scene.getCamera(),
                new Vector3d(CharacterStage.origin()).add(0, LOOK_HEIGHT, 0),
                LOOK_PITCH,
                LOOK_YAW,
                LOOK_DISTANCE);
        GraphicsManager.getWindowManager().addWindow(graphics, INPUT_WINDOW, new InputWindow());
        GraphicsManager.getWindowManager().show(INPUT_WINDOW);
        SpecBindings bindings =
                new SpecBindings()
                        .handler(PREVIOUS_CHARACTER, () -> showDesign(designIndex - 1))
                        .handler(NEXT_CHARACTER, () -> showDesign(designIndex + 1))
                        .handler(CLOSE, this::close)
                        .handler(UNDO, () -> changeLook(CharacterLook::undo))
                        .handler(
                                RANDOMIZE,
                                () ->
                                        changeLook(
                                                changed ->
                                                        changed.randomize(
                                                                new SeededRandom(System.nanoTime()),
                                                                SLIDER_CONTEXT)))
                        .handler(CLEAR, () -> changeLook(CharacterLook::clear))
                        .handler(RESET_SLIDERS, () -> changeLook(CharacterLook::resetSliders))
                        .handler(PREVIOUS_CLIP, () -> stepClip(-1))
                        .handler(NEXT_CLIP, () -> stepClip(1))
                        .handler(TOGGLE_PLAY, this::togglePlay)
                        .value(CHARACTER_NAME, characterName)
                        .value(SLIDERS_TITLE, slidersTitle)
                        .value(CLIP_NAME, clipName)
                        .value(CLIP_POSITION, clipPosition)
                        .value(PLAY_TEXT, playText);
        for (String spec : List.of(PARTS_SPEC, SLIDERS_SPEC, CLIPS_SPEC, ACTIONS_SPEC)) {
            panels.add(
                    graphics.ui()
                            .open(SpecLoader.loadResource(CharacterEditor.class, spec), bindings));
        }
        showDesign(0);
    }

    /**
     * The designs to choose from: every loaded design.
     *
     * @return The designs, in ID order.
     */
    private List<Design> designs() {
        return registry.get().all(Design.class);
    }

    /**
     * Show one of the loaded designs, wrapping around the ends of the list.
     *
     * @param index The design's position in the list.
     */
    private void showDesign(int index) {
        List<Design> designs = designs();
        if (designs.isEmpty()) {
            characterName.set("No designs loaded; add a content folder with some");
            return;
        }
        designIndex = Math.floorMod(index, designs.size());
        showDesign(designs.get(designIndex));
    }

    /**
     * Build a design on another thread and show it; the character shown stays until it is ready.
     *
     * @param design The design.
     */
    private void showDesign(Design design) {
        final Path reference = current.reference();
        final CharacterRegistry definitions = registry.get();
        final int load = ++loads;
        characterName.set("Loading " + design.id() + "...");
        CompletableFuture.supplyAsync(
                        () -> Prepared.build(design, definitions, reference, readFiles))
                .whenComplete(
                        (built, failure) ->
                                graphics.ui().post(() -> loaded(load, design, built, failure)));
    }

    /**
     * Put a design built on another thread on the stage, unless another was asked for since.
     *
     * @param load Which load this was.
     * @param design The design.
     * @param built The built design, or null if it failed.
     * @param failure Why it failed, or null.
     */
    private void loaded(int load, Design design, Prepared built, Throwable failure) {
        if (back == null || load != loads) {
            return;
        }
        if (built == null) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            log.error(
                    "Couldn't show {} in the character editor: {}",
                    design.id(),
                    cause.getMessage());
            characterName.set("Couldn't build " + design.id());
            return;
        }
        if (shown != null) {
            shown.close();
        }
        prepared = built;
        plan = built.plan();
        ImportedCharacter character = built.character();
        PartSlots slots = built.slots();
        regions = built.regions();
        categories = PartCategories.of(character, slots);
        List<Slider> found = findSliders(plan);
        look =
                new CharacterLook(
                        slots, found, plan.variants(), built.channelNames(), built.fills());
        look.start(
                plan.variants().indexOf(plan.variant(design.variant())),
                sliderValues(design),
                colorCodes(design),
                design.fx(),
                design.style());
        beforeEditVersion = -1;
        editing = false;
        lastWorn = look.worn(CharacterLook.NO_PREVIEW, 0);
        // Facing the camera's side of the floor; characters face +Z
        shown =
                new ShownCharacter(
                        plugin,
                        character,
                        regions.regions(),
                        built.channels(),
                        new Vector3d(CharacterStage.origin()),
                        new Quaternionf(),
                        lastWorn);
        shown.setHiddenRegions(regions.hidden(lastWorn));
        shownColorVersion = -1;
        shownFx = List.of();
        characterName.set(design.id().toString());
        String path = design.id().path();
        saveName.set(path.substring(path.lastIndexOf('/') + 1));
        if (!applySliders()) {
            look =
                    new CharacterLook(
                            slots, List.of(), List.of(), built.channelNames(), built.fills());
            slidersTitle.set("No sliders: the skeleton lacks their bones");
        }
        openCategory = Math.min(openCategory, categories.categories().size() - 1);
        frameOpenCategory();
        ImportedCharacter.ClipData initial = CharacterPreview.pickClip(character, null);
        playClip(initial == null ? -1 : character.clips().indexOf(initial));
        buildMotion(design.variant());
    }

    /**
     * Build the shown character's animation for its variant and the look's style.
     *
     * @param variant The variant.
     */
    private void buildMotion(String variant) {
        motionStyle = look.style();
        motion.setStanding(bodyTab);
        motion.build(
                registry.get(),
                plan,
                variant,
                motionStyle,
                current.reference(),
                shown,
                task -> graphics.ui().post(task));
    }

    /**
     * A design's slider values as the look keeps them.
     *
     * @param design The design.
     * @return The values, by slider ID.
     */
    private static Map<Id, float[]> sliderValues(Design design) {
        Map<Id, float[]> values = new HashMap<>();
        design.sliders()
                .forEach(
                        (id, list) -> {
                            float[] value = new float[list.size()];
                            for (int i = 0; i < value.length; ++i) {
                                value[i] = list.get(i);
                            }
                            values.put(Id.parse(id), value);
                        });
        return values;
    }

    /**
     * A design's colors as the look keeps them.
     *
     * @param design The design.
     * @return The color codes, by palette channel.
     */
    private static Map<String, Integer> colorCodes(Design design) {
        Map<String, Integer> codes = new HashMap<>();
        design.colors().forEach((channel, color) -> codes.put(channel, ColorCode.encode(color)));
        return codes;
    }

    /**
     * The design the editor shows, as it is now.
     *
     * @param id The ID to give it.
     * @param variant The variant to make it.
     * @param withSliders Whether to keep the slider values, or leave them for the variant to start.
     * @return The design.
     */
    private Design currentDesign(Id id, String variant, boolean withSliders) {
        Map<String, Id> parts = new LinkedHashMap<>();
        Map<String, Id> layers = new LinkedHashMap<>();
        for (int s = 0; s < look.slots().slots().size(); ++s) {
            int choice = look.choice(s);
            if (choice == CharacterLook.EMPTY) {
                continue;
            }
            String place = look.slots().slots().get(s).id();
            Id part = prepared.optionIds().get(s).get(choice);
            (plan.layer(place) != null ? layers : parts).put(place, part);
        }
        Map<String, List<Float>> sliders = new LinkedHashMap<>();
        if (withSliders) {
            BodyPlan.Variant shape = plan.variant(variant);
            look.values()
                    .forEach(
                            (sliderId, value) -> {
                                // Sliders where the variant starts them are left out
                                BodyPlan.SliderSetting setting =
                                        shape == null
                                                ? null
                                                : shape.sliders().get(sliderId.toString());
                                float start = setting == null ? 0 : setting.initial();
                                List<Float> snapped = new ArrayList<>();
                                boolean moved = false;
                                for (float v : value) {
                                    float step = DesignCodec.snap(v);
                                    snapped.add(step);
                                    moved |= step != DesignCodec.snap(start);
                                }
                                if (moved) {
                                    sliders.put(sliderId.toString(), snapped);
                                }
                            });
        }
        Map<String, Color> colors = new LinkedHashMap<>();
        for (String channel : look.channels()) {
            Integer code = look.color(channel);
            if (code != null) {
                colors.put(channel, ColorCode.toColor(code));
            }
        }
        return new Design(
                id,
                plan.id(),
                variant,
                look.style(),
                sliders,
                parts,
                layers,
                null,
                colors,
                List.copyOf(look.fx()));
    }

    /**
     * Make the character another variant: the same parts and colors on the variant's meshes, with
     * the sliders where the variant starts them. The parts are loaded again, so there is nothing to
     * undo afterward.
     *
     * @param index The variant's position in the plan.
     */
    private void switchVariant(int index) {
        if (prepared == null || index == look.variant()) {
            return;
        }
        showDesign(currentDesign(prepared.design().id(), plan.variants().get(index).id(), false));
    }

    /**
     * Save the design under the name typed, into the save folder, check it as a player's, and load
     * the definitions again so it is among the designs.
     */
    private void save() {
        if (current.saveFolder() == null || current.saveNamespace() == null) {
            saveStatus = "Saving is off; set editor.save-to in the config";
            return;
        }
        String name = saveName.get().trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        if (name.isEmpty()) {
            saveStatus = "Type a name to save as";
            return;
        }
        Id id = Id.parse(current.saveNamespace() + ":design/" + name);
        Design design =
                currentDesign(id, plan.variants().get(Math.max(0, look.variant())).id(), true);
        List<Problem> problems = design.validate(registry.get(), SLIDER_CONTEXT);
        if (!problems.isEmpty()) {
            saveStatus = "Not saved: " + problems.get(0).message();
            return;
        }
        Path file = current.saveFolder().resolve("design").resolve(name + ".json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(
                    file,
                    new GsonBuilder().setPrettyPrinting().create().toJson(DataFormat.write(design))
                            + "\n");
        } catch (IOException e) {
            saveStatus = "Not saved: " + e.getMessage();
            return;
        }
        int bytes = DesignCodec.encode(design, registry.get()).length;
        current.reload().run();
        designIndex = Math.max(0, designs().stream().map(Design::id).toList().indexOf(id));
        characterName.set(id.toString());
        saveStatus = "Saved " + id + "; " + bytes + " bytes to send";
        log.info("Saved the design {} to {}, {} bytes encoded", id, file, bytes);
    }

    /**
     * Change the look, then show the change.
     *
     * @param change What to do to the look.
     */
    private void changeLook(Consumer<CharacterLook> change) {
        if (look == null) {
            return;
        }
        change.accept(look);
        applySliders();
        showWorn(CharacterLook.NO_PREVIEW, 0);
    }

    /**
     * Show what the look wears, with one slot previewing another choice, if that changes what is
     * shown.
     *
     * @param previewSlot The slot to preview, or {@link CharacterLook#NO_PREVIEW}.
     * @param previewChoice What it would hold.
     */
    private void showWorn(int previewSlot, int previewChoice) {
        if (look == null || shown == null) {
            return;
        }
        List<Integer> worn = look.worn(previewSlot, previewChoice);
        if (!worn.equals(lastWorn)) {
            lastWorn = worn;
            shown.showParts(worn);
            shown.setHiddenRegions(regions.hidden(worn));
        }
    }

    /**
     * Draw the character panel: a Body tab choosing the body's variant, with its height and a ruler
     * beside it, and a Costume tab with the parts.
     *
     * @param frame The UI frame.
     */
    private void drawParts(@NonNull UiFrame frame) {
        if (look == null || categories == null) {
            IkGui.textDisabled("Loading...");
            return;
        }
        boolean body = bodyTab;
        if (IkGui.beginTabBar("##character-tabs")) {
            if (IkGui.beginTabItem("Body")) {
                body = true;
                drawBody();
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Costume")) {
                body = false;
                drawCostume();
                IkGui.endTabItem();
            }
            IkGui.endTabBar();
        }
        if (body != bodyTab) {
            bodyTab = body;
            frameOpenCategory();
            // The Body tab stands the character at rest, so the ruler reads its height true
            playClip(clipIndex);
        }
        if (bodyTab) {
            showWorn(CharacterLook.NO_PREVIEW, 0);
            drawRuler();
        }
    }

    /** Draw the Body tab: a card for each variant of the body plan, and the character's height. */
    private void drawBody() {
        List<BodyPlan.Variant> variants = look.variants();
        if (variants.isEmpty()) {
            IkGui.textDisabled("The rig has no body plan with variants");
        }
        float spacing = IkGui.getStyle().variable.itemSpacing.x;
        float cardWidth =
                (IkGui.getContentRegionAvailableX() - (CARDS_PER_ROW - 1) * spacing)
                        / CARDS_PER_ROW;
        for (int v = 0; v < variants.size(); ++v) {
            if (v % CARDS_PER_ROW != 0) {
                IkGui.sameLine();
            }
            String name = variants.get(v).id();
            String label = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
            if (IkGui.selectable(
                    label + "##variant" + v,
                    look.variant() == v,
                    SelectableFlags.NONE,
                    cardWidth,
                    IkGui.getFrameHeight() * CARD_ROWS)) {
                switchVariant(v);
            }
        }
        IkGui.spacing();
        double inches = characterHeight / METERS_PER_INCH;
        long feet = (long) Math.floor(inches / 12);
        long rest = Math.round(inches - feet * 12);
        if (rest == 12) {
            ++feet;
            rest = 0;
        }
        IkGui.textUnformatted(
                String.format(
                        Locale.ROOT, "Height: %.2f m (%d' %d\")", characterHeight, feet, rest));

        IkGui.separatorText("Save");
        IkGui.setNextItemWidth(IkGui.getContentRegionAvailableX() - SAVE_BUTTON_WIDTH);
        IkGui.inputText("##save-name", saveName);
        IkGui.sameLine();
        if (IkGui.button("Save")) {
            save();
        }
        if (!saveStatus.isEmpty()) {
            IkGui.textWrapped(saveStatus);
        }
    }

    /**
     * Draw the Costume tab: the slot categories, one open at a time, with a dropdown and arrows for
     * each slot that has a choice. The dropdown previews the hovered choice on the character.
     */
    private void drawCostume() {
        int previewSlot = CharacterLook.NO_PREVIEW;
        int previewChoice = 0;
        List<PartCategories.Category> list = categories.categories();
        for (int c = 0; c < list.size(); ++c) {
            PartCategories.Category category = list.get(c);
            IkGui.setNextItemOpen(openCategory == c, Condition.ALWAYS);
            boolean open = IkGui.collapsingHeader(category.label() + "##" + category.id());
            if (open != (openCategory == c)) {
                openCategory = open ? c : -1;
                frameOpenCategory();
            }
            if (!open) {
                continue;
            }
            for (int slot : category.slots()) {
                IkGui.pushID(slot);
                int hovered = drawSlot(slot);
                if (hovered != Integer.MIN_VALUE) {
                    previewSlot = slot;
                    previewChoice = hovered;
                }
                IkGui.popID();
            }
        }
        showWorn(previewSlot, previewChoice);
    }

    /**
     * Draw one slot: its name, then what fills it, with arrows around a dropdown for slots with a
     * choice.
     *
     * @param slot The slot's position.
     * @return The choice hovered in its open dropdown, or {@code Integer.MIN_VALUE} for none.
     */
    private int drawSlot(int slot) {
        PartSlots.Slot info = look.slots().slots().get(slot);
        int choice = look.choice(slot);
        String current = choice == CharacterLook.EMPTY ? EMPTY_LABEL : info.labels().get(choice);
        // The slot's name in a column of its own, so the rows line up
        IkGui.alignTextToFramePadding();
        IkGui.textUnformatted(info.id());
        IkGui.sameLine(NAME_COLUMN);
        int cover = look.coveredBy(slot);
        if (cover >= 0) {
            PartSlots.Slot covering = look.slots().slots().get(cover);
            IkGui.textDisabled("part of " + covering.labels().get(look.choice(cover)));
            return Integer.MIN_VALUE;
        }
        if (look.isFixed(slot)) {
            IkGui.textUnformatted(current);
            return Integer.MIN_VALUE;
        }
        int hovered = Integer.MIN_VALUE;
        if (IkGui.button("<")) {
            look.step(slot, -1);
        }
        IkGui.sameLine();
        float arrow = IkGui.getFrameHeight();
        IkGui.setNextItemWidth(
                Math.max(arrow, IkGui.getContentRegionAvailableX() - arrow - ROW_SPACING));
        // Named after the slot, so tests and scripts can find each dropdown
        if (IkGui.beginCombo("##" + info.id(), current)) {
            int first = info.optional() ? CharacterLook.EMPTY : 0;
            for (int option = first; option < info.options().size(); ++option) {
                String text =
                        option == CharacterLook.EMPTY ? EMPTY_LABEL : info.labels().get(option);
                boolean selected = option == choice;
                if (IkGui.selectable(text + "##" + option, selected)) {
                    look.choose(slot, option);
                }
                if (selected) {
                    IkGui.setItemDefaultFocus();
                }
                if (IkGui.isItemHovered()) {
                    hovered = option;
                }
            }
            IkGui.endCombo();
        }
        IkGui.sameLine();
        if (IkGui.button(">")) {
            look.step(slot, 1);
        }
        return hovered;
    }

    /** Glide the camera to frame the open category, or the whole character if none is open. */
    private void frameOpenCategory() {
        if (categories == null) {
            return;
        }
        Vector3f min;
        Vector3f max;
        if (!bodyTab && openCategory >= 0) {
            PartCategories.Category category = categories.categories().get(openCategory);
            min = category.min();
            max = category.max();
        } else {
            min = categories.min();
            max = categories.max();
        }
        Vector3d center =
                new Vector3d((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, (min.z + max.z) / 2.0)
                        .add(CharacterStage.origin());
        double radius = Math.max(MIN_FRAME_RADIUS, new Vector3f(max).sub(min).length() / 2);
        orbit.glideTo(center, radius);
    }

    /**
     * Find a plan's sliders.
     *
     * @param body The plan.
     * @return The sliders, grouped as the panel shows them.
     */
    private List<Slider> findSliders(BodyPlan body) {
        if (body.sliders() == null) {
            slidersTitle.set("No sliders: " + body.id() + " has none");
            return List.of();
        }
        List<Slider> found =
                new ArrayList<>(body.matchingSliders(registry.get().all(Slider.class)));
        // Grouped for the panel, in the order the groups first come
        List<String> groups = found.stream().map(CharacterEditor::groupOf).distinct().toList();
        found.sort(Comparator.comparingInt(slider -> groups.indexOf(groupOf(slider))));
        slidersTitle.set(body.id() + ", " + found.size() + " sliders");
        return found;
    }

    /**
     * Pose the character with the sliders' values.
     *
     * @return False if the sliders don't fit the skeleton.
     */
    private boolean applySliders() {
        if (shown == null || plan == null || look == null) {
            return look != null && look.sliders().isEmpty();
        }
        try {
            Proportions proportions =
                    Proportions.fromSliders(
                            shown.getSkeleton(), plan, look.sliders(), look.values(), 1);
            shown.setProportions(proportions);
            characterHeight = heightWith(proportions);
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("The sliders don't fit {}: {}", shown.getCharacter().name(), e.getMessage());
            return false;
        }
    }

    /**
     * How tall the character stands at rest with some proportions: the top of its body parts,
     * skinned as the renderer skins them, leaving out what can be taken off, like helmets.
     *
     * @param proportions The proportions.
     * @return The height, in meters.
     */
    private float heightWith(Proportions proportions) {
        Skeleton skeleton = shown.getSkeleton();
        PoseSolver solver = new PoseSolver(skeleton);
        solver.solve(new Pose(skeleton), proportions);
        float[] palette = new float[skeleton.size() * Skeleton.MATRIX_FLOATS];
        solver.writePalette(proportions, palette, 0);
        Matrix4f[] matrices = new Matrix4f[skeleton.size()];
        for (int b = 0; b < matrices.length; ++b) {
            matrices[b] = new Matrix4f().set(palette, b * Skeleton.MATRIX_FLOATS);
        }
        List<ImportedCharacter.Part> parts = shown.getCharacter().parts();
        Vector3f rest = new Vector3f();
        Vector3f moved = new Vector3f();
        Vector3f sum = new Vector3f();
        float top = 0;
        for (int s = 0; s < look.slots().slots().size(); ++s) {
            PartSlots.Slot slot = look.slots().slots().get(s);
            int choice = look.choice(s);
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

    /** Draw a ruler in meters beside the character, with a line at its height, over the stage. */
    private void drawRuler() {
        Scene scene = GraphicsManager.getScene();
        Camera camera = scene.getCamera();
        // The view matrix only turns; positions are taken from the camera's
        Matrix4f viewProjection =
                new Matrix4f(scene.getProjection().getProjectionMatrix())
                        .mul(camera.getViewMatrix());
        Vector3d base = new Vector3d(CharacterStage.origin());
        // Beside the character, on the side the camera sees
        Vector3f right = camera.getInvViewMatrix().transformDirection(new Vector3f(1, 0, 0));
        Vector3d foot = new Vector3d(base).add(-right.x * RULER_OFFSET, 0, -right.z * RULER_OFFSET);
        float[] bottom = toScreen(viewProjection, camera, foot, 0);
        float[] top = toScreen(viewProjection, camera, foot, RULER_HEIGHT);
        if (bottom == null || top == null) {
            return;
        }
        DrawList draw = IkGui.getBackgroundDrawList();
        int fontSize = IkGui.getFontSize();
        draw.addLine(bottom[0], bottom[1], top[0], top[1], RULER_COLOR, 2);
        int marks = Math.round(RULER_HEIGHT / RULER_STEP);
        for (int m = 0; m <= marks; ++m) {
            float height = m * RULER_STEP;
            float[] at = toScreen(viewProjection, camera, foot, height);
            if (at == null) {
                continue;
            }
            boolean major = m % 2 == 0;
            float length = major ? 2 * RULER_MARK : RULER_MARK;
            draw.addLine(at[0] - length, at[1], at[0], at[1], RULER_COLOR, 2);
            if (major) {
                String label = String.format(Locale.ROOT, "%.1f m", height);
                float width = IkGui.calcTextSize(label).x;
                draw.addText(
                        fontSize,
                        at[0] - length - width - 4,
                        at[1] - fontSize / 2f,
                        RULER_COLOR,
                        label,
                        0);
            }
        }
        float[] mark = toScreen(viewProjection, camera, foot, characterHeight);
        float[] head = toScreen(viewProjection, camera, base, characterHeight);
        if (mark != null && head != null) {
            draw.addLine(mark[0], mark[1], head[0], head[1], HEIGHT_COLOR, 2);
        }
    }

    /**
     * Where a point shows on the screen.
     *
     * @param viewProjection The camera's projection times its turn.
     * @param camera The camera.
     * @param ground The point on the ground below it, in world space.
     * @param height How far above the ground it is.
     * @return The x and y in pixels, or null if it is behind the camera.
     */
    private static float[] toScreen(
            Matrix4f viewProjection, Camera camera, Vector3d ground, float height) {
        Vector4f clip =
                viewProjection.transform(
                        new Vector4f(
                                (float) (ground.x - camera.getPosition().x),
                                (float) (ground.y + height - camera.getPosition().y),
                                (float) (ground.z - camera.getPosition().z),
                                1));
        if (clip.w <= 1e-4f) {
            return null;
        }
        float x = clip.x / clip.w;
        float y = clip.y / clip.w;
        // The projection's y may point either way; higher points must come out higher on screen
        Vector4f up =
                viewProjection.transform(
                        new Vector4f(
                                camera.getInvViewMatrix().transformDirection(new Vector3f(0, 1, 0)),
                                0));
        float sign = up.y >= 0 ? -1 : 1;
        // IkGui draws in desktop coordinates, so start from where the window's inside is
        Viewport viewport = IkGui.getMainViewport();
        return new float[] {
            viewport.position.x + (x + 1) / 2 * viewport.size.x,
            viewport.position.y + (sign * y + 1) / 2 * viewport.size.y
        };
    }

    /**
     * Draw the look panel: a Scales tab with the sliders and a Colors tab with the palette
     * channels. A drag of a slider or the color picker is one change to undo.
     *
     * @param frame The UI frame.
     */
    private void drawSliders(@NonNull UiFrame frame) {
        if (look == null) {
            return;
        }
        if (!editing && beforeEditVersion != look.version()) {
            // Only taken when the look changed, so idle frames don't copy it
            beforeEdit = look.snapshot();
            beforeEditVersion = look.version();
        }
        int hovered = NO_SWATCH;
        if (IkGui.beginTabBar("##look-tabs")) {
            if (IkGui.beginTabItem("Scales")) {
                drawScales();
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Colors")) {
                hovered = drawColors();
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Effects")) {
                drawEffects();
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Motion")) {
                drawMotion();
                IkGui.endTabItem();
            }
            IkGui.endTabBar();
        }
        showFx();
        tickMotion();
        if (editing && !IkGui.isAnyItemActive()) {
            editing = false;
        }
        showColors(hovered == NO_SWATCH ? null : colorChannel, hovered);
    }

    /** Remember the look before a drag for undo, once, when the drag starts changing it. */
    private void startEdit() {
        if (!editing) {
            look.push(beforeEdit);
            editing = true;
        }
    }

    /** Draw the sliders, grouped as they say. */
    private void drawScales() {
        String group = null;
        boolean changed = false;
        for (Slider slider : look.sliders()) {
            String sliderGroup = groupOf(slider);
            if (!sliderGroup.equals(group)) {
                group = sliderGroup;
                IkGui.separatorText(group);
                if (hasVectors(group)) {
                    drawAxisHeadings();
                }
            }
            Range range = look.range(slider, SLIDER_CONTEXT);
            float[] value = look.values().get(slider.id());
            String label = Objects.requireNonNullElse(slider.label(), slider.id().toString());
            if (slider.vector()) {
                changed |= IkGui.sliderFloat3(label, value, range.min(), range.max());
            } else {
                changed |= IkGui.sliderFloat(label, value, range.min(), range.max());
            }
        }
        if (changed) {
            startEdit();
            look.touch();
            applySliders();
        }
    }

    /**
     * Draw the Effects tab: each effect the design wears, with what it is, where it goes, its color
     * and its tuning, and a button to add another, up to {@link Design#MAX_FX}.
     */
    private void drawEffects() {
        List<Fx> all = registry.get().all(Fx.class);
        if (all.isEmpty()) {
            IkGui.textDisabled("No effects loaded");
            return;
        }
        List<FxUse> uses = look.fx();
        for (int i = 0; i < uses.size(); ++i) {
            IkGui.pushID(i);
            IkGui.separatorText("Effect " + (i + 1));
            drawEffect(i, uses.get(i), all);
            IkGui.popID();
        }
        IkGui.spacing();
        if (uses.size() < Design.MAX_FX && IkGui.button("Add effect")) {
            String socket = plan.sockets().isEmpty() ? null : plan.sockets().get(0).id();
            String bone = socket == null ? prepared.character().rig().bones().get(0).name() : null;
            changeLook(
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
                                            FxUse.ALWAYS)));
        }
        if (uses.size() >= Design.MAX_FX) {
            IkGui.textDisabled("A design wears up to " + Design.MAX_FX + " effects");
        }
    }

    /**
     * Draw one effect's rows.
     *
     * @param index Its position.
     * @param use The effect.
     * @param all The loaded effects.
     */
    private void drawEffect(int index, FxUse use, List<Fx> all) {
        if (IkGui.beginCombo("Effect##fx", Prepared.label(use.fx()))) {
            for (Fx fx : all) {
                if (IkGui.selectable(
                        Prepared.label(fx.id()) + "##" + fx.id(), fx.id().equals(use.fx()))) {
                    changeLook(changed -> changed.setFx(index, withFx(use, fx.id())));
                }
            }
            IkGui.endCombo();
        }
        String place = use.socket() != null ? use.socket() : use.bone();
        if (IkGui.beginCombo("On##place", place)) {
            for (BodyPlan.Socket socket : plan.sockets()) {
                if (IkGui.selectable(
                        socket.id() + "##socket-" + socket.id(),
                        socket.id().equals(use.socket()))) {
                    changeLook(changed -> changed.setFx(index, onPlace(use, null, socket.id())));
                }
            }
            IkGui.separator();
            for (Rig.Bone bone : prepared.character().rig().bones()) {
                if (bone.kind() != Rig.BoneKind.DEFORM) {
                    continue;
                }
                if (IkGui.selectable(
                        bone.name() + "##bone-" + bone.name(), bone.name().equals(use.bone()))) {
                    changeLook(changed -> changed.setFx(index, onPlace(use, bone.name(), null)));
                }
            }
            IkGui.endCombo();
        }
        String color = use.color() == null ? "its own" : use.color();
        if (IkGui.beginCombo("Color##color", color)) {
            if (IkGui.selectable("its own##own", use.color() == null)) {
                changeLook(changed -> changed.setFx(index, colored(use, null)));
            }
            for (String channel : plan.allChannels()) {
                if (IkGui.selectable(channel + "##" + channel, channel.equals(use.color()))) {
                    changeLook(changed -> changed.setFx(index, colored(use, channel)));
                }
            }
            IkGui.endCombo();
        }
        if (IkGui.beginCombo("When##when", use.when())) {
            List<String> whens = new ArrayList<>(List.of(FxUse.ALWAYS, FxUse.MOVING));
            whens.addAll(CharacterMotion.toggles(shownUses));
            for (String when : whens) {
                if (IkGui.selectable(when + "##when-" + when, when.equals(use.when()))) {
                    changeLook(changed -> changed.setFx(index, use.withWhen(when)));
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
            startEdit();
            look.dragFx(
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
                            use.when()));
        }
        if (IkGui.button("Remove")) {
            changeLook(changed2 -> changed2.setFx(index, null));
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
                use.when());
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
                use.when());
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
                use.when());
    }

    /**
     * Put the effects of the worn parts and the design on the character, if they or the colors
     * changed since they were last put on.
     */
    private void showFx() {
        if (shown == null || look == null || fxEffects == null) {
            return;
        }
        List<Part> worn = new ArrayList<>();
        List<FxUse> uses = new ArrayList<>();
        for (int s = 0; s < look.slots().slots().size(); ++s) {
            int choice = look.choice(s);
            if (choice == CharacterLook.EMPTY) {
                continue;
            }
            Part part = registry.get().get(Part.class, prepared.optionIds().get(s).get(choice));
            if (part != null) {
                worn.add(part);
                uses.addAll(part.fx());
            }
        }
        uses.addAll(look.fx());
        Map<String, Color> colors = new HashMap<>();
        for (String channel : plan.allChannels()) {
            Integer code = look.color(channel);
            if (code != null) {
                colors.put(channel, ColorCode.toColor(code));
            }
        }
        List<Object> key = List.of(uses, colors);
        if (key.equals(shownFx)) {
            return;
        }
        shownFx = key;
        shownUses = uses;
        shownEffects =
                uses.isEmpty()
                        ? null
                        : new CharacterFx(
                                uses,
                                worn,
                                plan,
                                shown.getSkeleton(),
                                colors,
                                registry.get(),
                                fxEffects);
        motion.applyStates(shownEffects, uses);
        shown.setFx(shownEffects);
    }

    /**
     * Draw the Colors tab: the palette channels the parts take, the chosen channel's swatches,
     * which preview on the character while hovered, and a picker that snaps to the colors a design
     * can hold.
     *
     * @return The color code of the swatch hovered, or {@link #NO_SWATCH}.
     */
    private int drawColors() {
        List<String> channels = look.channels();
        if (channels.isEmpty()) {
            IkGui.textDisabled("Nothing to color");
            return NO_SWATCH;
        }
        if (colorChannel == null || !channels.contains(colorChannel)) {
            colorChannel = channels.get(0);
        }
        float swatch = IkGui.getFrameHeight();
        for (String channel : channels) {
            Integer code = look.color(channel);
            IkGui.colorButton(
                    "##channel-" + channel, rgba(code), ColorEditFlags.NO_TOOLTIP, swatch, swatch);
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
                changeLook(changed -> changed.setColor(colorChannel, code));
            }
            if (IkGui.isItemHovered()) {
                hovered = code;
            }
        }

        // The picker snaps to the colors a design can hold
        Integer current = look.color(colorChannel);
        if (current != null) {
            ColorCode.decode(current, picker);
        }
        IkGui.spacing();
        if (IkGui.colorPicker3(
                "##picker",
                picker,
                ColorEditFlags.NO_SIDE_PREVIEW
                        | ColorEditFlags.NO_INPUTS
                        | ColorEditFlags.NO_ALPHA)) {
            startEdit();
            look.dragColor(colorChannel, ColorCode.encode(picker[0], picker[1], picker[2]));
        }
        if (current != null) {
            IkGui.textDisabled(
                    String.format(
                            Locale.ROOT, "%s, code %04x", ColorCode.toColor(current), current));
        }
        if (IkGui.button("Use the parts' own colors")) {
            changeLook(changed -> changed.setColor(colorChannel, null));
        }
        if (channelUntinted(colorChannel)) {
            IkGui.textWrapped(
                    "These parts are painted textures without tint masks, so they keep their own"
                            + " colors.");
        }
        return hovered;
    }

    /**
     * Whether no part whose own channel this is takes tints, so choosing its color shows nothing.
     *
     * @param channel The palette channel.
     * @return True if none does.
     */
    private boolean channelUntinted(String channel) {
        boolean any = false;
        for (PartSlots.Slot slot : look.slots().slots()) {
            if (!PartSlots.channelOf(slot).equals(channel)) {
                continue;
            }
            for (List<Integer> option : slot.options()) {
                for (int part : option) {
                    if (shown.isTinted(part)) {
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
     * Color the parts by their channels, with one channel previewing a swatch, if that changes what
     * is shown.
     *
     * @param previewChannel The channel to preview, or null.
     * @param previewCode The swatch's color code.
     */
    private void showColors(String previewChannel, int previewCode) {
        if (shown == null
                || look.version() == shownColorVersion
                        && Objects.equals(previewChannel, shownPreviewChannel)
                        && previewCode == shownPreviewCode) {
            return;
        }
        shownColorVersion = look.version();
        shownPreviewChannel = previewChannel;
        shownPreviewCode = previewCode;
        // One tint per standard channel; channels without a color stay white, changing nothing
        List<String> channels = plan.allChannels();
        float[] tints = new float[3 * Math.min(channels.size(), Skins.TINT_SLOTS)];
        Arrays.fill(tints, 1);
        float[] rgb = new float[3];
        for (int c = 0; c < tints.length / 3; ++c) {
            String channel = channels.get(c);
            Integer code =
                    channel.equals(previewChannel)
                            ? Integer.valueOf(previewCode)
                            : look.color(channel);
            if (code != null) {
                ColorCode.decode(code, rgb);
                System.arraycopy(rgb, 0, tints, 3 * c, 3);
            }
        }
        shown.setTints(tints);
    }

    /**
     * Whether any slider in a group has width, height and depth values.
     *
     * @param group The group.
     * @return True if one does.
     */
    private boolean hasVectors(String group) {
        return look.sliders().stream()
                .anyMatch(slider -> slider.vector() && groupOf(slider).equals(group));
    }

    /** Write Width, Height and Depth over the three columns of the sliders below. */
    private void drawAxisHeadings() {
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
     * The editor group a slider is shown in.
     *
     * @param slider The slider.
     * @return Its group, or "other".
     */
    private static String groupOf(Slider slider) {
        return Objects.requireNonNullElse(slider.group(), "other");
    }

    /**
     * Play one of the character's clips, wrapping around the ends of the list.
     *
     * @param index The clip's position in the character's list.
     */
    private void playClip(int index) {
        if (shown == null) {
            return;
        }
        motion.setStanding(bodyTab);
        if (motion.active()) {
            return;
        }
        List<ImportedCharacter.ClipData> clips = shown.getCharacter().clips();
        if (clips.isEmpty()) {
            clipIndex = -1;
            shown.play(null);
            clipName.set("No clips");
            clipPosition.set("");
            return;
        }
        clipIndex = Math.floorMod(index, clips.size());
        ImportedCharacter.ClipData clip = clips.get(clipIndex);
        // The Body tab stands the character at rest, so the ruler reads its height true
        shown.play(bodyTab ? null : clip);
        shown.setPlaying(playing);
        clipName.set(bodyTab ? clip.name() + " (plays on the Costume tab)" : clip.name());
        clipPosition.set((clipIndex + 1) + " / " + clips.size());
    }

    /**
     * Step through the actions, or the file's clips if the plan has no actions.
     *
     * @param step How far, like -1 for the one before.
     */
    private void stepClip(int step) {
        if (motion.active()) {
            motion.cycle(step);
        } else {
            playClip(clipIndex + step);
        }
    }

    /** Draw the Motion tab: the style, then the movement, actions and effect states. */
    private void drawMotion() {
        List<AnimStyle> styles =
                registry.get().all(AnimStyle.class).stream()
                        .filter(style -> style.plan().equals(plan.id()))
                        .toList();
        Id chosen = look.style();
        String preview = chosen == null ? PLAN_STYLE : Prepared.label(chosen);
        if (IkGui.beginCombo("Style##style", preview)) {
            if (IkGui.selectable(PLAN_STYLE + "##own-style", chosen == null)) {
                changeLook(changed -> changed.setStyle(null));
            }
            for (AnimStyle style : styles) {
                if (IkGui.selectable(
                        Prepared.label(style.id()) + "##" + style.id(),
                        style.id().equals(chosen))) {
                    changeLook(changed -> changed.setStyle(style.id()));
                }
            }
            IkGui.endCombo();
        }
        motion.draw(shownEffects, shownUses);
    }

    /** Rebuild the animation when the style changed, repeat actions, and fill the clip bar. */
    private void tickMotion() {
        if (shown == null) {
            return;
        }
        if (!Objects.equals(motionStyle, look.style())) {
            buildMotion(look.variants().get(look.variant()).id());
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

    /** Pause or play the clip. */
    private void togglePlay() {
        playing = !playing;
        playText.set(playing ? "Pause" : "Play");
        if (shown != null) {
            shown.setPlaying(playing);
        }
    }

    /**
     * Turn the camera with the mouse where no panel is: left drag turns around the character,
     * middle drag pans, and the wheel zooms. The right button still flies, as everywhere. Moves the
     * camera on through any glide to frame a region.
     */
    private void handleMouse() {
        final IkIO io = IkGui.getIO();
        final Camera camera = GraphicsManager.getScene().getCamera();
        orbit.follow(camera);
        if (IkGui.isMouseClicked(MouseButton.LEFT)) {
            pressedOnStage = !io.wantCaptureMouse;
        }
        if (IkGui.isMouseClicked(MouseButton.MIDDLE)) {
            middlePressedOnStage = !io.wantCaptureMouse;
        }
        if (pressedOnStage && IkGui.isMouseDragging(MouseButton.LEFT, DRAG_SLOP)) {
            orbit.turn(camera, io.mouseDelta.x, io.mouseDelta.y);
        } else if (middlePressedOnStage && IkGui.isMouseDragging(MouseButton.MIDDLE, DRAG_SLOP)) {
            orbit.pan(camera, io.mouseDelta.x, io.mouseDelta.y, io.displaySize.y);
        }
        if (!io.wantCaptureMouse && io.mouseWheel != 0) {
            orbit.zoom(camera, io.mouseWheel);
        }
        orbit.glide(camera, io.deltaTime / MILLISECONDS);
    }

    /** Close the editor, putting back what it changed, and go back to what opened it. */
    public void close() {
        if (back == null) {
            return;
        }
        final Runnable goBack = back;
        back = null;
        ++loads;
        panels.forEach(SpecInstance::close);
        panels.clear();
        GraphicsManager.getWindowManager().removeWindow(INPUT_WINDOW);
        if (shown != null) {
            shown.close();
            shown = null;
        }
        motion.detach();
        shownEffects = null;
        shownUses = List.of();
        look = null;
        prepared = null;
        readFiles.clear();
        shownFx = List.of();
        graphics.particles().setHotReload(false);
        fxEffects.close();
        fxEffects = null;
        regions = null;
        categories = null;
        openCategory = -1;
        lastWorn = List.of();
        stage.leave();
        goBack.run();
    }

    /** Reads the mouse each frame for the orbit camera. Draws no window. */
    private final class InputWindow extends GuiWindow {
        /** Set up the window. */
        InputWindow() {
            super(INPUT_WINDOW, WindowFlags.NO_SAVED_SETTINGS);
        }

        @Override
        public void draw(final int width, final int height) {
            handleMouse();
        }

        @Override
        public boolean handleGuiInput(@NonNull Scene scene) {
            return false;
        }
    }
}
