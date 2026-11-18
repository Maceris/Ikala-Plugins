package com.ikalagaming.characters.editor;

import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Design;
import com.ikalagaming.characters.definition.Part;
import com.ikalagaming.characters.definition.Slider;
import com.ikalagaming.characters.demo.CharacterPreview;
import com.ikalagaming.characters.design.DesignCodec;
import com.ikalagaming.characters.design.Equipment;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.pose.WingMotion;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.registry.ContentSource;
import com.ikalagaming.characters.view.FxEffects;
import com.ikalagaming.characters.view.PartAssembler;
import com.ikalagaming.characters.view.PartCategories;
import com.ikalagaming.characters.view.PartSlots;
import com.ikalagaming.characters.view.ShownCharacter;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.component.GuiWindow;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.MouseButton;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    static final String SLIDER_CONTEXT = Slider.CONTEXT_PLAYER;

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

    /** How many characters the save name holds. */
    private static final int SAVE_NAME_CAPACITY = 64;

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

    /** Keeps the shown character in step with the look. */
    private final ShownSync sync;

    /** The clip bar, the Motion tab and the Equipment tab. */
    private final MotionControls controls;

    /** The Scales, Colors and Effects tabs. */
    private final LookTabs lookTabs;

    /** The Motion tab's wing choice that follows the motion, as scripts name it too. */
    static final String WINGS_AUTO = "auto";

    /** Draws the bones over the character. */
    private final BoneView boneView;

    /** The Align tab's wing mode. */
    private final WingPoser wingPoser;

    /** Whether the Align tab poses wings rather than lining up accessories. */
    private boolean posingWings;

    /** Whether the Body tab is showing, rather than the Costume tab. */
    private boolean bodyTab = true;

    /** The channel whose swatch the Colors tab has hovered, or null, see {@link #tickFrame}. */
    private String previewChannel;

    /** The hovered swatch's color code, or {@link LookTabs#NO_SWATCH}. */
    private int previewCode = LookTabs.NO_SWATCH;

    /** Runs {@link #tickFrame} while the editor is open. */
    private final Runnable frameTick = this::tickFrame;

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
        sync = new ShownSync(registry, motion);
        controls =
                new MotionControls(
                        registry,
                        motion,
                        sync,
                        this::changeLook,
                        builds,
                        task -> graphics.ui().post(task),
                        this::standsStill,
                        () -> bodyTab,
                        () -> current.reference());
        lookTabs = new LookTabs(registry, sync, this::changeLook, this::startEdit);
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
                        .handler(PREVIOUS_CLIP, () -> controls.stepClip(session, -1))
                        .handler(NEXT_CLIP, () -> controls.stepClip(session, 1))
                        .handler(TOGGLE_PLAY, () -> controls.togglePlay(session))
                        .value(CHARACTER_NAME, characterName)
                        .value(SLIDERS_TITLE, slidersTitle)
                        .value(CLIP_NAME, controls.clipName())
                        .value(CLIP_POSITION, controls.clipPosition())
                        .value(PLAY_TEXT, controls.playText());
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
        sync.pushForms(
                session, session.lastWorn, session.look.slotStandoffs(CharacterLook.NO_PREVIEW, 0));
        session.equipment = Equipment.NONE;
        session.itemFx = List.of();
        motion.setCombat(false);
        session.shownColorVersion = -1;
        session.shownFx = List.of();
        characterName.set(design.id().toString());
        String path = design.id().path();
        saveName.set(path.substring(path.lastIndexOf('/') + 1));
        if (!sync.applySliders(session)) {
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
        controls.playClip(session, initial == null ? -1 : character.clips().indexOf(initial));
        controls.buildMotion(session, design.variant());
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
        sync.applySliders(session);
        sync.showWorn(session, CharacterLook.NO_PREVIEW, 0);
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
            controls.playClip(session, session.clipIndex);
        }
        if (bodyTab || contentTab) {
            sync.showWorn(session, CharacterLook.NO_PREVIEW, 0);
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
        double inches = sync.characterHeight() / METERS_PER_INCH;
        long feet = (long) Math.floor(inches / 12);
        long rest = Math.round(inches - feet * 12);
        if (rest == 12) {
            ++feet;
            rest = 0;
        }
        IkGui.textUnformatted(
                String.format(
                        Locale.ROOT,
                        "Height: %.2f m (%d' %d\")",
                        sync.characterHeight(),
                        feet,
                        rest));

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
        sync.showWorn(session, previewSlot, previewChoice);
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
        List<String> groups = found.stream().map(LookTabs::groupOf).distinct().toList();
        found.sort(Comparator.comparingInt(slider -> groups.indexOf(LookTabs.groupOf(slider))));
        slidersTitle.set(body.id() + ", " + found.size() + " sliders");
        return found;
    }

    /**
     * Change what worn wings do, keeping it for wings put on later.
     *
     * @param state What they do, or null to follow the character's motion.
     */
    void setWingState(WingMotion.State state) {
        sync.setWingState(state);
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
        float[] mark = toScreen(viewProjection, camera, foot, sync.characterHeight());
        float[] head = toScreen(viewProjection, camera, base, sync.characterHeight());
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
        int hovered = LookTabs.NO_SWATCH;
        if (IkGui.beginTabBar("##look-tabs")) {
            if (IkGui.beginTabItem("Scales")) {
                lookTabs.drawScales(session);
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Colors")) {
                hovered = lookTabs.drawColors(session);
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Effects")) {
                lookTabs.drawEffects(session);
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Equipment")) {
                controls.drawEquipment(session);
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Motion")) {
                controls.drawMotion(session);
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
        previewChannel = hovered == LookTabs.NO_SWATCH ? null : lookTabs.colorChannel();
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
        sync.showFx(session, fxEffects);
        controls.tickMotion(session);
        if (controls.showBones()) {
            boneView.draw(session.shown, sync.onChain(), sync.springChains(), controls.boneNames());
        }
        sync.showColors(session, previewChannel, previewCode);
        // The Colors tab sets it again each frame it is drawn
        previewChannel = null;
        previewCode = LookTabs.NO_SWATCH;
    }

    /** Remember the look before a drag for undo, once, when the drag starts changing it. */
    private void startEdit() {
        if (!session.editing) {
            session.look.push(session.beforeEdit);
            session.editing = true;
        }
    }

    /** Draw the Align tab: lining up accessories, or posing wings when wings are worn. */
    private void drawAlign() {
        if (!sync.wornWings().isEmpty() && sync.wingMotion() != null) {
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
            wingPoser.draw(new WingPoser.Stage(session.shown, sync.wornWings(), sync.wingMotion()));
        } else {
            aligner.draw(sync.alignStage(session));
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
     * For scripts: wear a decal, or none. Call on the render thread.
     *
     * @param decalId The decal's ID, or null or empty for none.
     * @param place The plan's decal place, or null for its first.
     * @param first The palette channel the decal's first color takes, or null for its default.
     * @param second The channel its second color takes, or null.
     * @return False if nothing is shown or the plan has no decal places.
     */
    boolean wearDecal(String decalId, String place, String first, String second) {
        return lookTabs.wearDecal(session, decalId, place, first, second);
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
        controls.setBones(show, names);
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
            controls.playClip(session, session.clipIndex);
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
