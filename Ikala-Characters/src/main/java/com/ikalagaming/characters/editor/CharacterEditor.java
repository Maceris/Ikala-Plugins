package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.ColorCode;
import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.data.Range;
import com.ikalagaming.characters.definition.AnimStyle;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Decal;
import com.ikalagaming.characters.definition.DecalUse;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.definition.Fx;
import com.ikalagaming.characters.definition.FxUse;
import com.ikalagaming.characters.definition.Item;
import com.ikalagaming.characters.definition.Palette;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.definition.Slider;
import com.ikalagaming.characters.demo.CharacterPreview;
import com.ikalagaming.characters.design.DesignCodec;
import com.ikalagaming.characters.design.Equipment;
import com.ikalagaming.characters.design.LookStack;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.Pose;
import com.ikalagaming.characters.pose.PoseSolver;
import com.ikalagaming.characters.pose.Proportions;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.pose.SpringChains;
import com.ikalagaming.characters.pose.WingMotion;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.registry.ContentSource;
import com.ikalagaming.characters.view.AccessoryRests;
import com.ikalagaming.characters.view.CharacterDecals;
import com.ikalagaming.characters.view.CharacterFx;
import com.ikalagaming.characters.view.FxEffects;
import com.ikalagaming.characters.view.HeldItems;
import com.ikalagaming.characters.view.PartAssembler;
import com.ikalagaming.characters.view.PartCategories;
import com.ikalagaming.characters.view.PartForms;
import com.ikalagaming.characters.view.PartSlots;
import com.ikalagaming.characters.view.ShownCharacter;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.SkinnedPart;
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
import com.ikalagaming.graphics.gui.flags.HoveredFlags;
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
import com.ikalagaming.plugins.PluginThreads;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
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

    /** The Costume tab's checkbox for putting pairs, like both claws, on together. */
    private static final String BOTH_SIDES_LABEL = "Both sides##both-sides";

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

    /** What the build threads are named after the plugin's name. */
    private static final String BUILDS_NAME = "character-builds";

    /** How many designs and clip sets build at once. */
    private static final int BUILD_THREADS = 2;

    /** How long {@link #stopWork()} waits for builds in flight, in milliseconds. */
    private static final long STOP_WAIT_MS = 1000;

    /**
     * Builds designs and reads clip files off the render thread. Owned by the plugin, so unloading
     * stops it even if {@link #stopWork()} isn't called.
     */
    private final ExecutorService builds;

    /** The loaded definitions. */
    private final Supplier<CharacterRegistry> registry;

    /** The characters to choose from, and the rig reference for moving them, from the config. */
    private final Supplier<EditorSettings> settings;

    /** The stage. */
    private final CharacterStage stage;

    /** Turns the camera. */
    private final OrbitCamera orbit = new OrbitCamera();

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

    /** The last load that finished, shown or failed, so scripts know when to look. */
    private int finishedLoad;

    /** Whether a script stood the character at rest, as the Body tab does. */
    private boolean scriptStill;

    /** The settings while open. */
    private EditorSettings current;

    /** The Align tab, which lines accessories up on their sockets. */
    private final AccessoryAligner aligner;

    /** The Content tab, which picks the content folders. */
    private final ContentPanel content;

    /** Whether the Content tab is showing. */
    private boolean contentTab;

    /** Which of the loaded designs is shown. */
    private int designIndex;

    /**
     * The design shown and what was last put on its character, or null until a design first loads
     * after the editor opens, and again once it closes.
     */
    private EditorSession session;

    /**
     * Files read while the editor is open, on their plan's rig, for switching designs fast. Holds
     * null for a file that couldn't be read, so it isn't tried again; a concurrent map can't hold
     * null, so this is a synchronized one. Builds run on other threads.
     */
    private final Map<Path, ImportedCharacter> readFiles =
            Collections.synchronizedMap(new HashMap<>());

    /** The name to save the design as. */
    private final IkString saveName = new IkString(SAVE_NAME_CAPACITY);

    /** What the last save did, shown under the name. */
    private String saveStatus = "";

    /** The particle effects characters wear, loaded while the editor is open. */
    private FxEffects fxEffects;

    /** The shown character's animation and the Motion tab. */
    private final CharacterMotion motion = new CharacterMotion();

    /** The Motion tab's wing choice that follows the motion, as scripts name it too. */
    static final String WINGS_AUTO = "auto";

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

    /** Draws the bones over the character. */
    private final BoneView boneView;

    /** Whether the bones are drawn over the character. */
    private boolean showBones;

    /** Whether the chains' bones are named, while the bones are drawn. */
    private boolean boneNames;

    /** The Align tab's wing mode. */
    private final WingPoser wingPoser;

    /** Whether the Align tab poses wings rather than lining up accessories. */
    private boolean posingWings;

    /** The wings worn, for the Align tab. */
    private List<WingPoser.Worn> wornWings = List.of();

    /** The styles the animation was last built for. */
    private List<Id> motionStyles = List.of();

    /** Reads held items' meshes. */
    private final HeldItems heldItems = new HeldItems();

    /** The palette channel the Colors tab shows, or null before one is picked. */
    private String colorChannel;

    /** The color picker's color, sRGB. */
    private final float[] picker = {0.5f, 0.5f, 0.5f};

    /** Whether the Body tab is showing, rather than the Costume tab. */
    private boolean bodyTab = true;

    /** How tall the character is with the sliders as they are, in meters. */
    private float characterHeight;

    /** The channel whose swatch the Colors tab has hovered, or null, see {@link #tickFrame}. */
    private String previewChannel;

    /** The hovered swatch's color code, or {@link #NO_SWATCH}. */
    private int previewCode = NO_SWATCH;

    /** Runs {@link #tickFrame} while the editor is open. */
    private final Runnable frameTick = this::tickFrame;

    /** The most a color's channel holds, see {@link Color}. */
    private static final float COLOR_MAX = 255;

    /** The channels a newly chosen decal's colors take, until the user picks others. */
    private static final Map<String, String> DECAL_CHANNELS =
            Map.of(Decal.FIRST, Palette.ACCENT, Decal.SECOND, Palette.PRIMARY);

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
     * @param folders The content folders loaded, in order.
     * @param setFolders Changes the content folders and where designs save, then loads them again.
     */
    public record EditorSettings(
            @NonNull Path reference,
            String saveNamespace,
            Path saveFolder,
            @NonNull Runnable reload,
            @NonNull List<ContentSource> folders,
            ContentPanel.@NonNull FolderSetter setFolders,
            @NonNull Supplier<List<Problem>> problems) {}

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
        builds = PluginThreads.fixedPool(plugin, BUILDS_NAME, BUILD_THREADS);
        this.registry = registry;
        this.settings = settings;
        stage = new CharacterStage(graphics);
        content = new ContentPanel(graphics.ui(), registry);
        boneView = new BoneView(graphics.debug());
        aligner =
                new AccessoryAligner(
                        graphics.debug(),
                        registry,
                        () -> current.reload().run(),
                        (point, pitch, yaw, distance) ->
                                orbit.look(
                                        GraphicsManager.getScene().getCamera(),
                                        point,
                                        pitch,
                                        yaw,
                                        distance),
                        () -> current.reference());
        wingPoser = new WingPoser(registry, () -> current.reload().run(), this::setWingState);
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
        graphics.addFrameTask(frameTick);
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
            characterName.set("No designs loaded; add a content folder on the Content tab");
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
        try {
            CompletableFuture.supplyAsync(
                            () -> PreparedDesign.build(design, definitions, reference, readFiles),
                            builds)
                    .whenComplete(
                            (built, failure) ->
                                    graphics.ui().post(() -> loaded(load, design, built, failure)));
        } catch (RejectedExecutionException e) {
            // The plugin is being disabled, and the editor is about to close
            characterName.set("Closing...");
        }
    }

    /**
     * Put a design built on another thread on the stage, unless another was asked for since.
     *
     * @param load Which load this was.
     * @param design The design.
     * @param built The built design, or null if it failed.
     * @param failure Why it failed, or null.
     */
    private void loaded(int load, Design design, PreparedDesign built, Throwable failure) {
        if (back == null || load != loads) {
            return;
        }
        finishedLoad = load;
        if (built == null) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            log.error(
                    "Couldn't show {} in the character editor: {}",
                    design.id(),
                    cause.getMessage(),
                    cause);
            characterName.set("Couldn't build " + design.id());
            return;
        }
        if (session == null) {
            session = new EditorSession();
        } else {
            session.shown.close();
        }
        session.prepared = built;
        session.plan = built.plan();
        ImportedCharacter character = built.character();
        PartSlots slots = built.slots();
        session.regions = built.regions();
        Set<String> extras = new HashSet<>();
        // Tails and wings; a chain named like a layer, like the cape, stays with the layers
        session.plan.extensions().stream()
                .map(BodyPlan.Extension::id)
                .filter(chain -> session.plan.layer(chain) == null)
                .forEach(extras::add);
        session.categories = PartCategories.of(character, slots, extras);
        List<Slider> found = findSliders(session.plan);
        session.look =
                new CharacterLook(
                        slots,
                        found,
                        session.plan.variants(),
                        built.channelNames(),
                        built.fills(),
                        built.optionParts(),
                        session.plan,
                        built.formMeshes());
        session.look.start(
                session.plan.variants().indexOf(session.plan.variant(design.variant())),
                DesignSnapshot.sliderValues(design),
                DesignSnapshot.colorCodes(design),
                design.fx(),
                design.style(),
                design.decal());
        session.decalSynced = false;
        session.beforeEditVersion = -1;
        session.editing = false;
        session.lastWorn = session.look.worn(CharacterLook.NO_PREVIEW, 0);
        // Facing the camera's side of the floor; characters face +Z
        session.shown =
                new ShownCharacter(
                        plugin,
                        character,
                        session.regions.regions(),
                        built.channels(),
                        built.fallbacks(),
                        new Vector3d(CharacterStage.origin()),
                        new Quaternionf(),
                        session.lastWorn);
        session.shown.setHiddenRegions(session.regions.hidden(session.lastWorn));
        session.pushed.clear();
        session.rested.clear();
        pushForms(session.lastWorn, session.look.slotStandoffs(CharacterLook.NO_PREVIEW, 0));
        session.equipment = Equipment.NONE;
        session.itemFx = List.of();
        motion.setCombat(false);
        session.shownColorVersion = -1;
        session.shownFx = List.of();
        characterName.set(design.id().toString());
        String path = design.id().path();
        saveName.set(path.substring(path.lastIndexOf('/') + 1));
        if (!applySliders()) {
            session.look =
                    new CharacterLook(
                            slots,
                            List.of(),
                            List.of(),
                            built.channelNames(),
                            built.fills(),
                            built.optionParts(),
                            session.plan,
                            built.formMeshes());
            slidersTitle.set("No sliders: the skeleton lacks their bones");
        }
        session.openCategory =
                Math.min(session.openCategory, session.categories.categories().size() - 1);
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
        motionStyles = styles();
        motion.setStanding(standsStill());
        motion.build(
                registry.get(),
                session.plan,
                variant,
                motionStyles,
                current.reference(),
                session.shown,
                builds,
                task -> graphics.ui().post(task));
    }

    /**
     * Make the character another variant: the same parts and colors on the variant's meshes, with
     * the sliders where the variant starts them. The parts are loaded again, so there is nothing to
     * undo afterward.
     *
     * @param index The variant's position in the plan.
     */
    private void switchVariant(int index) {
        if (session == null || index == session.look.variant()) {
            return;
        }
        showDesign(
                DesignSnapshot.of(
                        session.look,
                        session.plan,
                        session.prepared,
                        session.prepared.design().id(),
                        session.plan.variants().get(index).id(),
                        false));
    }

    /**
     * Save the design under the name typed, into the save folder, check it as a player's, and load
     * the definitions again so it is among the designs.
     */
    private void save() {
        if (current.saveFolder() == null || current.saveNamespace() == null) {
            saveStatus = "Saving is off; pick a folder to save to on the Content tab";
            return;
        }
        String name = saveName.get().trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        if (name.isEmpty()) {
            saveStatus = "Type a name to save as";
            return;
        }
        Id id = Id.parse(current.saveNamespace() + ":design/" + name);
        Design design =
                DesignSnapshot.of(
                        session.look,
                        session.plan,
                        session.prepared,
                        id,
                        session.plan.variants().get(Math.max(0, session.look.variant())).id(),
                        true);
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
        if (session == null) {
            return;
        }
        change.accept(session.look);
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
        if (session == null) {
            return;
        }
        List<Integer> worn = session.look.worn(previewSlot, previewChoice);
        pushForms(worn, session.look.slotStandoffs(previewSlot, previewChoice));
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
     * @param worn The shown parts.
     * @param slotStandoffs How far the body part in each slot stands out, in meters.
     */
    private void pushForms(List<Integer> worn, Map<String, Float> slotStandoffs) {
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
        restAccessories(worn);
    }

    /**
     * Move the shown accessories out to rest on what is worn under them, each only when how far it
     * moves changes.
     *
     * @param worn The shown parts.
     */
    private void restAccessories(List<Integer> worn) {
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
     * Draw the character panel: a Body tab choosing the body's variant, with its height and a ruler
     * beside it, and a Costume tab with the parts.
     *
     * @param frame The UI frame.
     */
    private void drawParts(@NonNull UiFrame frame) {
        boolean ready = session != null;
        boolean body = bodyTab;
        boolean onContent = false;
        if (IkGui.beginTabBar("##character-tabs")) {
            if (IkGui.beginTabItem("Body")) {
                body = true;
                if (ready) {
                    drawBody();
                } else {
                    drawWaiting();
                }
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Costume")) {
                body = false;
                if (ready) {
                    drawCostume();
                } else {
                    drawWaiting();
                }
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Content")) {
                onContent = true;
                if (content.draw(current)) {
                    frame.fire(this::contentChanged);
                }
                IkGui.endTabItem();
            }
            IkGui.endTabBar();
        }
        contentTab = onContent;
        if (!ready) {
            return;
        }
        if (body != bodyTab) {
            bodyTab = body;
            frameOpenCategory();
            // The Body tab stands the character at rest, so the ruler reads its height true
            playClip(session.clipIndex);
        }
        if (bodyTab || contentTab) {
            showWorn(CharacterLook.NO_PREVIEW, 0);
        }
        if (bodyTab && !contentTab) {
            drawRuler();
        }
    }

    /** Draw what the Body and Costume tabs show while there is no character: why not. */
    private void drawWaiting() {
        IkGui.textWrapped(
                designs().isEmpty()
                        ? "No designs are loaded; add a content folder with some on the Content tab"
                        : "Loading...");
    }

    /** Read the settings again after the Content tab changed the folders, and show a design. */
    private void contentChanged() {
        current = settings.get();
        showDesign(designIndex);
    }

    /** Draw the Body tab: a card for each variant of the body plan, and the character's height. */
    private void drawBody() {
        List<BodyPlan.Variant> variants = session.look.variants();
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
                    session.look.variant() == v,
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
     * Draw the Costume tab: whether pairs go on together, then the slot categories, one open at a
     * time, with a dropdown and arrows for each slot that has a choice. The dropdown previews the
     * hovered choice on the character. Slots with nothing available for this body, and categories
     * of only such slots, aren't shown.
     */
    private void drawCostume() {
        int previewSlot = CharacterLook.NO_PREVIEW;
        int previewChoice = 0;
        if (IkGui.checkbox(BOTH_SIDES_LABEL, session.look.bothSides())) {
            session.look.setBothSides(!session.look.bothSides());
        }
        List<PartCategories.Category> list = session.categories.categories();
        for (int c = 0; c < list.size(); ++c) {
            PartCategories.Category category = list.get(c);
            if (category.slots().stream().noneMatch(this::isShown)) {
                continue;
            }
            IkGui.setNextItemOpen(session.openCategory == c, Condition.ALWAYS);
            boolean open = IkGui.collapsingHeader(category.label() + "##" + category.id());
            if (open != (session.openCategory == c)) {
                session.openCategory = open ? c : -1;
                frameOpenCategory();
            }
            if (!open) {
                continue;
            }
            for (int slot : category.slots()) {
                if (!isShown(slot)) {
                    continue;
                }
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
     * Whether the Costume tab shows a slot: it holds something, or offers something for this body.
     *
     * @param slot The slot's position.
     * @return True if it is shown.
     */
    private boolean isShown(int slot) {
        return session.look.choice(slot) != CharacterLook.EMPTY
                || !session.look.available(slot).isEmpty();
    }

    /**
     * Draw one slot: its name, then what fills it, with arrows around a dropdown for slots with a
     * choice. A slot covered by another's part, or hidden by a worn part, says so instead.
     *
     * @param slot The slot's position.
     * @return The choice hovered in its open dropdown, or {@code Integer.MIN_VALUE} for none.
     */
    private int drawSlot(int slot) {
        PartSlots.Slot info = session.look.slots().slots().get(slot);
        int choice = session.look.choice(slot);
        String current = choice == CharacterLook.EMPTY ? EMPTY_LABEL : info.labels().get(choice);
        // The slot's name in a column of its own, so the rows line up
        IkGui.alignTextToFramePadding();
        IkGui.textUnformatted(info.id());
        IkGui.sameLine(NAME_COLUMN);
        int cover = session.look.coveredBy(slot);
        if (cover >= 0) {
            PartSlots.Slot covering = session.look.slots().slots().get(cover);
            IkGui.textDisabled("part of " + covering.labels().get(session.look.choice(cover)));
            return Integer.MIN_VALUE;
        }
        Part hiding = session.look.hiddenBy(slot);
        if (hiding != null) {
            IkGui.textDisabled(
                    (choice == CharacterLook.EMPTY ? "" : current + ", ")
                            + "hidden by "
                            + PreparedDesign.label(hiding.id()));
            return Integer.MIN_VALUE;
        }
        if (session.look.isFixed(slot)) {
            IkGui.textUnformatted(current);
            return Integer.MIN_VALUE;
        }
        int hovered = Integer.MIN_VALUE;
        if (IkGui.button("<")) {
            session.look.step(slot, -1);
        }
        IkGui.sameLine();
        float arrow = IkGui.getFrameHeight();
        IkGui.setNextItemWidth(
                Math.max(arrow, IkGui.getContentRegionAvailableX() - arrow - ROW_SPACING));
        // Named after the slot, so tests and scripts can find each dropdown
        if (IkGui.beginCombo("##" + info.id(), current)) {
            List<Integer> shown = new ArrayList<>();
            if (info.optional()) {
                shown.add(CharacterLook.EMPTY);
            }
            shown.addAll(session.look.available(slot));
            for (int option : shown) {
                String text =
                        option == CharacterLook.EMPTY ? EMPTY_LABEL : info.labels().get(option);
                boolean selected = option == choice;
                if (IkGui.selectable(text + "##" + option, selected)) {
                    session.look.choose(slot, option);
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
            session.look.step(slot, 1);
        }
        return hovered;
    }

    /** Glide the camera to frame the open category, or the whole character if none is open. */
    private void frameOpenCategory() {
        if (session == null) {
            return;
        }
        Vector3f min;
        Vector3f max;
        if (!bodyTab && session.openCategory >= 0) {
            PartCategories.Category category =
                    session.categories.categories().get(session.openCategory);
            min = category.min();
            max = category.max();
        } else {
            min = session.categories.min();
            max = session.categories.max();
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
            characterHeight = heightWith(proportions);
            swingChains(worn);
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
     * @param worn The parts worn.
     */
    private void swingChains(Set<Part> worn) {
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
     * @param proportions The proportions.
     * @return The height, in meters.
     */
    private float heightWith(Proportions proportions) {
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
        if (session == null) {
            return;
        }
        if (!session.editing && session.beforeEditVersion != session.look.version()) {
            // Only taken when the look changed, so idle frames don't copy it
            session.beforeEdit = session.look.snapshot();
            session.beforeEditVersion = session.look.version();
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
            if (IkGui.beginTabItem("Equipment")) {
                drawEquipment();
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Motion")) {
                drawMotion();
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Align")) {
                drawAlign();
                IkGui.endTabItem();
            }
            IkGui.endTabBar();
        }
        if (session.editing && !IkGui.isAnyItemActive()) {
            session.editing = false;
        }
        // The next frame's tick shows it, see tickFrame
        previewChannel = hovered == NO_SWATCH ? null : colorChannel;
        previewCode = hovered;
    }

    /**
     * Keep the shown character in step with the look, every frame whether or not the GUI is drawn,
     * so scripts and captures work with the GUI off: its effects, its motion, and its colors with
     * any swatch the Colors tab has hovered.
     */
    private void tickFrame() {
        if (session == null) {
            return;
        }
        showFx();
        tickMotion();
        if (showBones) {
            boneView.draw(session.shown, onChain, springChains, boneNames);
        }
        showColors(previewChannel, previewCode);
        // The Colors tab sets it again each frame it is drawn
        previewChannel = null;
        previewCode = NO_SWATCH;
    }

    /** Remember the look before a drag for undo, once, when the drag starts changing it. */
    private void startEdit() {
        if (!session.editing) {
            session.look.push(session.beforeEdit);
            session.editing = true;
        }
    }

    /** Draw the sliders, grouped as they say. */
    private void drawScales() {
        String group = null;
        boolean changed = false;
        for (Slider slider : session.look.sliders()) {
            String sliderGroup = groupOf(slider);
            if (!sliderGroup.equals(group)) {
                group = sliderGroup;
                IkGui.separatorText(group);
                if (hasVectors(group)) {
                    drawAxisHeadings();
                }
            }
            Range range = session.look.range(slider, SLIDER_CONTEXT);
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
            startEdit();
            session.look.touch();
            applySliders();
        }
    }

    /**
     * Draw the Effects tab: each effect the design wears, with what it is, where it goes, its color
     * and its tuning, and a button to add another, up to {@link Design#MAX_FX}.
     */
    private void drawEffects() {
        drawDecal();
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
            drawEffect(i, uses.get(i), all);
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
     */
    private void drawDecal() {
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
                changeLook(changed -> changed.setDecal(null));
            }
            for (Decal decal : all) {
                boolean chosen = use != null && decal.id().equals(use.decal());
                if (IkGui.selectable(
                        PreparedDesign.label(decal.id()) + "##" + decal.id(), chosen)) {
                    String place = use == null ? session.plan.decals().get(0).id() : use.place();
                    Map<String, String> channels = use == null ? DECAL_CHANNELS : use.channels();
                    changeLook(
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
                    changeLook(
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
                    changeLook(changed -> changed.setDecal(recolored(use, mask, null)));
                }
                for (String other : session.plan.allChannels()) {
                    if (IkGui.selectable(
                            other + "##" + mask + "-" + other, other.equals(channel))) {
                        changeLook(changed -> changed.setDecal(recolored(use, mask, other)));
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
     * @param decalId The decal's ID, or null or empty for none.
     * @param place The plan's decal place, or null for its first.
     * @param first The palette channel the decal's first color takes, or null for its default.
     * @param second The channel its second color takes, or null.
     * @return False if nothing is shown or the plan has no decal places.
     */
    boolean wearDecal(String decalId, String place, String first, String second) {
        if (session == null || session.plan.decals().isEmpty()) {
            return false;
        }
        if (decalId == null || decalId.isBlank()) {
            changeLook(changed -> changed.setDecal(null));
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
        changeLook(changed -> changed.setDecal(new DecalUse(Id.parse(decalId), where, channels)));
        return true;
    }

    /**
     * Draw one effect's rows.
     *
     * @param index Its position.
     * @param use The effect.
     * @param all The loaded effects.
     */
    private void drawEffect(int index, FxUse use, List<Fx> all) {
        if (IkGui.beginCombo("Effect##fx", PreparedDesign.label(use.fx()))) {
            for (Fx fx : all) {
                if (IkGui.selectable(
                        PreparedDesign.label(fx.id()) + "##" + fx.id(), fx.id().equals(use.fx()))) {
                    changeLook(changed -> changed.setFx(index, withFx(use, fx.id())));
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
                    changeLook(changed -> changed.setFx(index, onPlace(use, null, socket.id())));
                }
            }
            IkGui.separator();
            for (Rig.Bone bone : session.prepared.character().rig().bones()) {
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
            for (String channel : session.plan.allChannels()) {
                if (IkGui.selectable(channel + "##" + channel, channel.equals(use.color()))) {
                    changeLook(changed -> changed.setFx(index, colored(use, channel)));
                }
            }
            IkGui.endCombo();
        }
        if (IkGui.beginCombo("When##when", use.when())) {
            List<String> whens = new ArrayList<>(List.of(FxUse.ALWAYS, FxUse.MOVING));
            whens.addAll(CharacterMotion.toggles(session.shownUses));
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
     * Put the effects of the worn parts and the design on the character, if they or the colors
     * changed since they were last put on.
     */
    private void showFx() {
        if (session == null || fxEffects == null) {
            return;
        }
        List<Part> worn = wornParts();
        List<FxUse> uses = new ArrayList<>();
        for (Part part : worn) {
            uses.addAll(part.fx());
        }
        uses.addAll(session.look.fx());
        uses.addAll(session.itemFx);
        Map<String, Color> colors = new HashMap<>();
        for (String channel : session.plan.allChannels()) {
            Integer code = channelColor(channel);
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
     * @return The stage.
     */
    private AccessoryAligner.Stage alignStage() {
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
     * @return The worn parts' definitions.
     */
    private List<Part> wornParts() {
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
     * @return The color codes, by palette channel.
     */
    private Map<String, Integer> findPartColors() {
        Map<String, Integer> found = new HashMap<>();
        for (Part part : wornParts()) {
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
     * @param channel The palette channel.
     * @return The color code, or null if neither colors it.
     */
    private Integer channelColor(String channel) {
        Integer code = session.look.color(channel);
        return code != null ? code : session.partColors.get(channel);
    }

    /**
     * Draw the Colors tab: the palette channels the parts take, the chosen channel's swatches,
     * which preview on the character while hovered, and a picker that snaps to the colors a design
     * can hold.
     *
     * @return The color code of the swatch hovered, or {@link #NO_SWATCH}.
     */
    private int drawColors() {
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
                    rgba(channelColor(channel)),
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
                changeLook(changed -> changed.setColor(colorChannel, code));
            }
            if (IkGui.isItemHovered()) {
                hovered = code;
            }
        }

        // The picker snaps to the colors a design can hold
        Integer current = session.look.color(colorChannel);
        Integer shownColor = channelColor(colorChannel);
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
            startEdit();
            session.look.dragColor(colorChannel, ColorCode.encode(picker[0], picker[1], picker[2]));
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
     * Color the parts by their channels, with one channel previewing a swatch, if that changes what
     * is shown.
     *
     * @param previewChannel The channel to preview, or null.
     * @param previewCode The swatch's color code.
     */
    private void showColors(String previewChannel, int previewCode) {
        if (session == null
                || session.look.version() == session.shownColorVersion
                        && Objects.equals(previewChannel, session.shownPreviewChannel)
                        && previewCode == session.shownPreviewCode) {
            return;
        }
        if (session.shownColorVersion != session.look.version()) {
            session.partColors = findPartColors();
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
        showDecal();
    }

    /**
     * Give the shown character the look's decal, if it changed: its art, its box on the plan's
     * place, the tint slots its colors take and its defaults, and the parts that show it.
     */
    private void showDecal() {
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

    /**
     * Whether any slider in a group has width, height and depth values.
     *
     * @param group The group.
     * @return True if one does.
     */
    private boolean hasVectors(String group) {
        return session.look.sliders().stream()
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
        if (session == null) {
            return;
        }
        motion.setStanding(standsStill());
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
        session.shown.play(standsStill() ? null : clip);
        session.shown.setPlaying(playing);
        clipName.set(bodyTab ? clip.name() + " (plays on the Costume tab)" : clip.name());
        clipPosition.set((session.clipIndex + 1) + " / " + clips.size());
    }

    /**
     * Step through the actions, or the file's clips if the plan has no actions.
     *
     * @param step How far, like -1 for the one before.
     */
    private void stepClip(int step) {
        if (motion.active()) {
            motion.cycle(step);
        } else if (session != null) {
            // Nothing plays while nothing is shown
            playClip(session.clipIndex + step);
        }
    }

    /** Draw the Align tab: lining up accessories, or posing wings when wings are worn. */
    private void drawAlign() {
        if (!wornWings.isEmpty() && wingMotion != null) {
            if (IkGui.radioButton("Accessories##align-accessories", !posingWings)) {
                posingWings = false;
            }
            IkGui.sameLine();
            if (IkGui.radioButton("Wings##align-wings", posingWings)) {
                posingWings = true;
            }
        } else {
            posingWings = false;
        }
        if (posingWings) {
            wingPoser.draw(new WingPoser.Stage(session.shown, wornWings, wingMotion));
        } else {
            aligner.draw(alignStage());
        }
    }

    /** Draw the Motion tab: the style, then the movement, actions and effect states. */
    private void drawMotion() {
        List<AnimStyle> styles =
                registry.get().all(AnimStyle.class).stream()
                        .filter(style -> style.plan().equals(session.plan.id()))
                        .toList();
        Id chosen = session.look.style();
        String preview = chosen == null ? PLAN_STYLE : PreparedDesign.label(chosen);
        if (IkGui.beginCombo("Style##style", preview)) {
            if (IkGui.selectable(PLAN_STYLE + "##own-style", chosen == null)) {
                changeLook(changed -> changed.setStyle(null));
            }
            for (AnimStyle style : styles) {
                if (IkGui.selectable(
                        PreparedDesign.label(style.id()) + "##" + style.id(),
                        style.id().equals(chosen))) {
                    changeLook(changed -> changed.setStyle(style.id()));
                }
            }
            IkGui.endCombo();
        }
        if (IkGui.checkbox("Swing chains##springs", springsOn)) {
            springsOn = !springsOn;
            applySliders();
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
        if (wingMotion != null) {
            IkGui.textUnformatted("Wings");
            IkGui.sameLine();
            if (IkGui.radioButton(WINGS_AUTO + "##wings-auto", wingState == null)) {
                setWingState(null);
            }
            for (WingMotion.State state : WingMotion.State.values()) {
                IkGui.sameLine();
                if (IkGui.radioButton(state.key() + "##wings-" + state.key(), state == wingState)) {
                    setWingState(state);
                }
            }
        }
        motion.draw(session.shownEffects, session.shownUses);
    }

    /**
     * The styles the character moves in: its equipped items', in the plan's slot order, then the
     * look's.
     *
     * @return The styles, the first winning.
     */
    private List<Id> styles() {
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
     */
    private void drawEquipment() {
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
            drawEquipped(slot.id(), session.equipment.slots().get(slot.id()), fits, definitions);
            IkGui.popID();
        }
        if (!any) {
            IkGui.textDisabled("No held items fit this plan");
            return;
        }
        IkGui.spacing();
        if (IkGui.button("Draw all")) {
            setAllStates(DRAWN);
        }
        IkGui.sameLine();
        if (IkGui.button("Sheathe all")) {
            setAllStates(SHEATHED);
        }
    }

    /**
     * Draw one slot's rows.
     *
     * @param slot The slot.
     * @param equipped What is in it, or null.
     * @param fits The held items that fit it.
     * @param definitions The loaded definitions.
     */
    private void drawEquipped(
            String slot,
            Equipment.Equipped equipped,
            List<Item> fits,
            CharacterRegistry definitions) {
        String preview = equipped == null ? EMPTY_SLOT : PreparedDesign.label(equipped.item());
        if (IkGui.beginCombo("Item##item", preview)) {
            if (IkGui.selectable(EMPTY_SLOT + "##empty", equipped == null)) {
                equip(slot, null);
            }
            for (Item item : fits) {
                if (IkGui.selectable(
                        PreparedDesign.label(item.id()) + "##" + item.id(),
                        equipped != null && item.id().equals(equipped.item()))) {
                    equip(slot, new Equipment.Equipped(item.id(), null, null, false, Map.of()));
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
                        equip(slot, equipped.withState(option));
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
     * @param slot The slot.
     * @param equipped The item, or null to empty the slot.
     */
    private void equip(String slot, Equipment.Equipped equipped) {
        Map<String, Equipment.Equipped> slots = new LinkedHashMap<>(session.equipment.slots());
        if (equipped == null) {
            slots.remove(slot);
        } else {
            slots.put(slot, equipped);
        }
        session.equipment = new Equipment(slots);
        applyEquipment();
    }

    /**
     * Put every held item that has a state in it.
     *
     * @param state The state, like drawn.
     */
    private void setAllStates(String state) {
        Map<String, Equipment.Equipped> slots = new LinkedHashMap<>();
        session.equipment
                .slots()
                .forEach((slot, equipped) -> slots.put(slot, equipped.withState(state)));
        session.equipment = new Equipment(slots);
        applyEquipment();
    }

    /**
     * Show the equipment on the character: its held meshes on their sockets and its items' effects.
     * The styles follow on the next frame, as the animation is rebuilt for them.
     */
    private void applyEquipment() {
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

    /** Rebuild the animation when the style changed, repeat actions, and fill the clip bar. */
    private void tickMotion() {
        if (session == null) {
            return;
        }
        if (!motionStyles.equals(styles())) {
            buildMotion(session.look.variants().get(session.look.variant()).id());
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
        if (session != null) {
            session.shown.setPlaying(playing);
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
            // A press on the Align tab's handles drags them, not the camera
            pressedOnStage =
                    !io.wantCaptureMouse && !aligner.wantsMouse() && !wingPoser.wantsMouse();
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
        graphics.removeFrameTask(frameTick);
        if (session != null) {
            session.close();
            session = null;
        }
        motion.detach();
        readFiles.clear();
        graphics.particles().setHotReload(false);
        fxEffects.close();
        fxEffects = null;
        stage.leave();
        goBack.run();
    }

    /**
     * Let go of the editor after the plugin's graphics closed, as when the plugin is disabled after
     * them: stop the shown character's timer and forget what was shown, without touching the UI or
     * anything else on the GPU, which went with the graphics. Doesn't go back to what opened it.
     * Call on the thread disabling the plugin, since the render thread is gone.
     *
     * <p>Safe off the render thread: the plugin's graphics only read as closed while it is being
     * disabled once graphics shut down, which closes every context on the render thread as its last
     * tick, so no UI code of the editor runs any more; an unloaded plugin's context closes only
     * after its disabling. What this does touch from other threads is locked or volatile: the shown
     * character's timer is shut down and waited for before its effects close, and the effects and
     * emitters take their own locks.
     */
    public void abandon() {
        if (back == null) {
            return;
        }
        back = null;
        ++loads;
        panels.clear();
        if (session != null) {
            // Stops its timer, and skips releasing graphics once they are closed
            session.close();
            session = null;
        }
        motion.detach();
        readFiles.clear();
        if (fxEffects != null) {
            // Skips unloading effects once graphics are closed
            fxEffects.close();
            fxEffects = null;
        }
    }

    /**
     * Stop building designs and reading clips, when the plugin is disabled. Builds in flight are
     * interrupted and waited for briefly; ones not started are dropped. Doesn't need graphics, and
     * can be called from any thread. The editor can't show designs afterward.
     */
    public void stopWork() {
        builds.shutdownNow();
        try {
            if (!builds.awaitTermination(STOP_WAIT_MS, TimeUnit.MILLISECONDS)) {
                log.warn("Character editor builds did not stop in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Whether the character stands at rest: on the Body tab, so the ruler reads its height true, or
     * when a script asked.
     *
     * @return True if it stands at rest.
     */
    private boolean standsStill() {
        return bodyTab || scriptStill;
    }

    /**
     * For scripts: show a design by its ID. Call on the render thread.
     *
     * @param id The design's ID, like "characters:design/fighter".
     * @return False if the editor is closed or no design has that ID.
     */
    boolean showDesignNamed(@NonNull String id) {
        if (back == null) {
            return false;
        }
        List<Design> designs = designs();
        for (int i = 0; i < designs.size(); ++i) {
            if (designs.get(i).id().toString().equals(id)) {
                designIndex = i;
                showDesign(designs.get(i));
                return true;
            }
        }
        return false;
    }

    /**
     * For scripts: whether the design asked for last is shown, or failed to load, so what is on the
     * stage won't change by itself. Call on the render thread.
     *
     * @return True once it is.
     */
    boolean isSettled() {
        return back != null && loads > 0 && finishedLoad == loads;
    }

    /** For scripts: take off everything that can be taken off. Call on the render thread. */
    void bare() {
        changeLook(CharacterLook::clear);
    }

    /**
     * For scripts: wear a part, filling the slot that offers it. Call on the render thread.
     *
     * @param partId The part's ID.
     * @return False if nothing is shown or no slot offers the part.
     */
    boolean wear(@NonNull String partId) {
        if (session == null) {
            return false;
        }
        int[] found = PreparedDesign.findOption(session.prepared.optionIds(), partId);
        if (found == null) {
            return false;
        }
        changeLook(changed -> changed.choose(found[0], found[1]));
        return true;
    }

    /**
     * For scripts: draw debug shapes, like the Align tab's markers, or not. Call on the render
     * thread.
     *
     * @param show Whether to draw them.
     */
    void showMarkers(boolean show) {
        stage.showDebug(show);
    }

    /**
     * For scripts: draw the bones over the character, or not. Call on the render thread.
     *
     * @param show Whether to draw them.
     * @param names Whether to name the chains' bones.
     */
    void showBones(boolean show, boolean names) {
        showBones = show;
        boneNames = names;
    }

    /**
     * For scripts: where bones are posed now. Call on the render thread.
     *
     * @param prefix What the bones' names start with.
     * @return Each bone's name, head in model space and y axis, joined by "; ", or an empty string.
     */
    String posedHeads(String prefix) {
        if (session == null) {
            return "";
        }
        Skeleton skeleton = session.shown.getSkeleton();
        float[] posed = new float[skeleton.size() * ShownCharacter.BONE_FLOATS];
        if (!session.shown.posedBones(posed)) {
            return "";
        }
        List<String> heads = new ArrayList<>();
        for (int b = 0; b < skeleton.size(); ++b) {
            if (skeleton.name(b).startsWith(prefix)) {
                int at = b * ShownCharacter.BONE_FLOATS;
                heads.add(
                        String.format(
                                Locale.ROOT,
                                "%s %.3f %.3f %.3f along %.2f %.2f %.2f",
                                skeleton.name(b),
                                posed[at],
                                posed[at + 1],
                                posed[at + 2],
                                posed[at + 3],
                                posed[at + 4],
                                posed[at + 5]));
            }
        }
        return String.join("; ", heads);
    }

    /**
     * For scripts: the parts worn, as the game would show them. Call on the render thread.
     *
     * @return The part IDs, in slot order, or none if nothing is shown.
     */
    List<String> wornIds() {
        List<String> ids = new ArrayList<>();
        if (session == null) {
            return ids;
        }
        for (int slot = 0; slot < session.look.slots().slots().size(); ++slot) {
            int choice = session.look.choice(slot);
            if (choice != CharacterLook.EMPTY && session.look.hiddenBy(slot) == null) {
                String form = session.look.formOf(slot);
                ids.add(
                        session.prepared.optionIds().get(slot).get(choice)
                                + (form == null ? "" : Part.FORM_SEPARATOR + form));
            }
        }
        return ids;
    }

    /**
     * For scripts: put pairs on together, or choose sides alone. Call on the render thread.
     *
     * @param both Whether pairs go on together.
     */
    void setBothSides(boolean both) {
        if (session != null) {
            session.look.setBothSides(both);
        }
    }

    /**
     * For scripts: stand the character at rest, or let it move again. Call on the render thread.
     * Letting it move also leaves the Body tab's rest stance, which headless scripts can't click
     * away from; with the GUI drawn, the tab shown decides again on the next frame.
     *
     * @param still Whether it stands at rest.
     */
    void setStill(boolean still) {
        scriptStill = still;
        if (!still) {
            bodyTab = false;
        }
        if (session != null) {
            playClip(session.clipIndex);
        }
    }

    /**
     * For scripts: move at a speed, as the Motion tab's slider does. Call on the render thread.
     *
     * @param metersPerSecond The speed, 0 to stand.
     */
    void setSpeed(float metersPerSecond) {
        motion.setSpeed(metersPerSecond);
    }

    /**
     * For scripts: look at what the character wears or at one part, from far enough away to see it
     * whole, without gliding. Call on the render thread.
     *
     * @param partId The part to frame, or null for the whole character.
     * @param pitch The pitch, positive looking down.
     * @param yaw The yaw, as {@link OrbitCamera#look} takes it.
     * @return False if nothing is shown or no slot offers the part.
     */
    boolean frame(String partId, double pitch, double yaw) {
        if (session == null) {
            return false;
        }
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        if (partId == null) {
            // What is worn, not every part offered, which reaches far wider
            PartCategories.bounds(
                    session.prepared.character(),
                    session.look.worn(CharacterLook.NO_PREVIEW, 0),
                    min,
                    max);
        } else {
            int[] found = PreparedDesign.findOption(session.prepared.optionIds(), partId);
            if (found == null) {
                return false;
            }
            PartCategories.bounds(
                    session.prepared.character(),
                    session.prepared.slots().slots().get(found[0]).options().get(found[1]),
                    min,
                    max);
        }
        Vector3d center =
                new Vector3d((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, (min.z + max.z) / 2.0)
                        .add(CharacterStage.origin());
        double radius = Math.max(MIN_FRAME_RADIUS, new Vector3f(max).sub(min).length() / 2);
        orbit.look(
                GraphicsManager.getScene().getCamera(),
                center,
                pitch,
                yaw,
                OrbitCamera.frameDistance(radius));
        return true;
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
