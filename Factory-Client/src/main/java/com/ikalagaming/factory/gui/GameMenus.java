package com.ikalagaming.factory.gui;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.registry.DefinitionLoader;
import com.ikalagaming.factory.save.NewGame;
import com.ikalagaming.factory.save.SaveSummary;
import com.ikalagaming.factory.save.Saves;
import com.ikalagaming.factory.saves.SaveUtil;
import com.ikalagaming.factory.world.gen.data.Kind;
import com.ikalagaming.factory.world.gen.data.WorldgenData;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.input.ControlsScreen;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.TextInput;
import com.ikalagaming.graphics.ui.spec.Observable;
import com.ikalagaming.graphics.ui.spec.ObservableList;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecEvent;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.SpecLoader;
import com.ikalagaming.launcher.events.Shutdown;
import com.ikalagaming.plugins.PluginInfo;
import com.ikalagaming.plugins.PluginManager;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * The game's menus: the main menu, the Single Player screen listing the saves, and the New Game
 * screen that makes one. They are retained UI specs in the plugin's {@code ui/} resources, and only
 * one shows at a time.
 */
@Slf4j
public class GameMenus {

    /** The main menu's surface. */
    public static final String MAIN_MENU = "factory/main-menu";

    /** The Single Player screen's surface. */
    public static final String SINGLE_PLAYER = "factory/single-player";

    /** The New Game screen's surface. */
    public static final String NEW_GAME = "factory/new-game";

    /** The pause menu's surface, over the game. */
    public static final String PAUSE = "factory/pause";

    /** The Options screen's surface. */
    public static final String OPTIONS = "factory/options";

    /** Every surface here, of which at most one shows. */
    private static final List<String> SURFACES =
            List.of(MAIN_MENU, SINGLE_PLAYER, NEW_GAME, PAUSE, OPTIONS);

    /** The specs, in the plugin's resources. */
    private static final String MAIN_MENU_SPEC = "/ui/main-menu.yml";

    private static final String SINGLE_PLAYER_SPEC = "/ui/single-player.yml";
    private static final String NEW_GAME_SPEC = "/ui/new-game.yml";
    private static final String PAUSE_SPEC = "/ui/pause.yml";
    private static final String OPTIONS_SPEC = "/ui/options.yml";

    /** What the pause menu's buttons do in the game. */
    public interface GameActions {
        /** Close the pause menu and carry on playing. */
        void resume();

        /** Leave the game and go back to the main menu. */
        void exitToMainMenu();
    }

    /** Node paths in the New Game spec, from its root. */
    private static final String NAME_INPUT = "form/name";

    private static final String SEED_INPUT = "form/seed";

    /** The prefix of the strings that name world types, followed by the ID with : and / as _. */
    static final String WORLD_TYPE_KEY_PREFIX = "WORLD_TYPE_";

    /** Shows dates in the save list. */
    private static final DateTimeFormatter PLAYED_FORMAT =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);

    /**
     * A save as the Single Player screen lists it.
     *
     * @param folder The save's folder, which finds it again.
     * @param name Its name.
     * @param details A line about it: when it was played, its world type and version.
     */
    public record SaveRow(@NonNull String folder, @NonNull String name, @NonNull String details) {}

    /** The plugin's graphics. */
    private final GraphicsContext graphics;

    /** Starts the game with a save, once the menus are hidden. */
    private final Consumer<SaveSummary> startGame;

    /** Gives the time saves are made and played. */
    private final Clock clock;

    /** The saves the Single Player screen lists. */
    private final ObservableList<SaveRow> saves = new ObservableList<>();

    /** Whether there are no saves to list. */
    private final Observable<Boolean> noSaves = Observable.of(true);

    /** The world types to choose from, by ID. */
    private final List<String> worldTypes = new ArrayList<>();

    /** Which world type is chosen. */
    private int worldTypeIndex;

    /** The world types' names, as the dropdown lists them. */
    private final Observable<List<String>> worldTypeNames = Observable.of(List.of());

    /** Which world type the dropdown shows as chosen, or -1 for none. */
    private final Observable<Integer> selectedWorldType = Observable.of(-1);

    /** Why the last Create failed, or blank. */
    private final Observable<String> error = Observable.of("");

    /** Whether there is an error to show. */
    private final Observable<Boolean> hasError = Observable.of(false);

    /** A note on the main menu, like why a server turned the player away. */
    private final Observable<String> mainMenuNote = Observable.of("");

    /** Whether there is a note on the main menu. */
    private final Observable<Boolean> hasMainMenuNote = Observable.of(false);

    /** The open New Game screen, to read its fields. */
    private SpecInstance newGame;

    /** The surface showing, or null for none. */
    private String showing;

    /** Where the Options screen goes back to. */
    private String optionsReturn = MAIN_MENU;

    /** What the pause menu's buttons do; nothing until the game sets it. */
    private GameActions gameActions =
            new GameActions() {
                @Override
                public void resume() {
                    hideAll();
                }

                @Override
                public void exitToMainMenu() {
                    showMainMenu();
                }
            };

    /** The Controls screen, once opened. */
    private ControlsScreen controls;

    /**
     * Menus for the plugin.
     *
     * @param graphics The plugin's graphics.
     * @param startGame Starts the game with a save, once the menus are hidden.
     */
    public GameMenus(@NonNull GraphicsContext graphics, @NonNull Consumer<SaveSummary> startGame) {
        this(graphics, startGame, Clock.systemUTC());
    }

    /**
     * Menus for the plugin.
     *
     * @param graphics The plugin's graphics.
     * @param startGame Starts the game with a save, once the menus are hidden.
     * @param clock Gives the time saves are made and played.
     */
    GameMenus(
            @NonNull GraphicsContext graphics,
            @NonNull Consumer<SaveSummary> startGame,
            @NonNull Clock clock) {
        this.graphics = graphics;
        this.startGame = startGame;
        this.clock = clock;
    }

    /** Build the menus and show the main menu. */
    public void open() {
        UI ui = graphics.ui();
        ResourceBundle bundle = FactoryClientPlugin.getResourceBundle();
        ui.open(
                SpecLoader.loadResource(GameMenus.class, MAIN_MENU_SPEC),
                new SpecBindings()
                        .bundle(bundle)
                        .value("note", mainMenuNote)
                        .value("hasNote", hasMainMenuNote)
                        .handler("open-single-player", this::showSinglePlayer)
                        // Not built yet: FACT-13 and FACT-14
                        .handler("open-multiplayer", () -> {})
                        .handler("open-options", () -> showOptions(MAIN_MENU))
                        .handler("quit-game", () -> new Shutdown().fire()));
        ui.open(
                SpecLoader.loadResource(GameMenus.class, PAUSE_SPEC),
                new SpecBindings()
                        .bundle(bundle)
                        .handler("resume", () -> gameActions.resume())
                        .handler("open-options", () -> showOptions(PAUSE))
                        .handler("exit-to-menu", () -> gameActions.exitToMainMenu()));
        ui.open(
                SpecLoader.loadResource(GameMenus.class, OPTIONS_SPEC),
                new SpecBindings()
                        .bundle(bundle)
                        .handler("open-controls", this::showControls)
                        .handler("options-back", () -> show(optionsReturn)));
        ui.open(
                SpecLoader.loadResource(GameMenus.class, SINGLE_PLAYER_SPEC),
                new SpecBindings()
                        .bundle(bundle)
                        .handler("play-save", this::play)
                        .handler("back", () -> show(MAIN_MENU))
                        .handler("open-new-game", this::showNewGame)
                        .list("saves", saves)
                        .value("noSaves", noSaves));
        newGame =
                ui.open(
                        SpecLoader.loadResource(GameMenus.class, NEW_GAME_SPEC),
                        new SpecBindings()
                                .bundle(bundle)
                                .handler("choose-world-type", this::chooseWorldType)
                                .handler("cancel", this::showSinglePlayer)
                                .handler("create", this::create)
                                .value("worldTypes", worldTypeNames)
                                .value("worldType.selected", selectedWorldType)
                                .value("newGame.error", error)
                                .value("newGame.hasError", hasError));
        show(MAIN_MENU);
    }

    /** Hide every menu, as when the game starts. */
    public void hideAll() {
        show(null);
    }

    /** Show the main menu, as after leaving a game. */
    public void showMainMenu() {
        showMainMenu("");
    }

    /**
     * Show the main menu with a note, like why a server turned the player away.
     *
     * @param note The note, or blank for none.
     */
    public void showMainMenu(@NonNull String note) {
        mainMenuNote.set(note);
        hasMainMenuNote.set(!note.isBlank());
        show(MAIN_MENU);
    }

    /** Show the pause menu over the game. */
    public void showPause() {
        show(PAUSE);
    }

    /**
     * Set what the pause menu's buttons do.
     *
     * @param actions The game's actions.
     */
    public void setGameActions(@NonNull GameActions actions) {
        gameActions = actions;
    }

    /**
     * Whether any menu, or the Controls screen, is showing, so the game should ignore the keys.
     *
     * @return True if one is showing.
     */
    public boolean isShowing() {
        return showing != null || (controls != null && controls.isOpen());
    }

    /**
     * Whether the pause menu, or a screen opened from it, is showing.
     *
     * @return True if the game is in its menu.
     */
    public boolean isPauseShowing() {
        return PAUSE.equals(showing)
                || (PAUSE.equals(optionsReturn)
                        && (OPTIONS.equals(showing) || (controls != null && controls.isOpen())));
    }

    /**
     * Go back a step, as the pause key does in a menu: Controls to Options, Options to where it
     * came from, the pause menu to the game.
     */
    public void back() {
        if (controls != null && controls.isOpen()) {
            controls.close();
            show(OPTIONS);
        } else if (OPTIONS.equals(showing)) {
            show(optionsReturn);
        } else if (PAUSE.equals(showing)) {
            gameActions.resume();
        } else if (SINGLE_PLAYER.equals(showing)) {
            show(MAIN_MENU);
        } else if (NEW_GAME.equals(showing)) {
            show(SINGLE_PLAYER);
        }
    }

    /**
     * Show the Options screen.
     *
     * @param returnTo The surface its Back button goes to.
     */
    private void showOptions(@NonNull String returnTo) {
        optionsReturn = returnTo;
        show(OPTIONS);
    }

    /** Show the Controls screen, which goes back to Options. */
    private void showControls() {
        if (controls == null) {
            controls = new ControlsScreen(graphics, () -> show(OPTIONS));
        }
        show(null);
        controls.open();
    }

    /**
     * Show one menu and hide the others.
     *
     * @param surface The menu's surface, or null to hide them all.
     */
    private void show(String surface) {
        UI ui = graphics.ui();
        for (String other : SURFACES) {
            ui.setVisible(other, other.equals(surface));
        }
        showing = surface;
    }

    /** Read the saves again and show the Single Player screen. */
    void showSinglePlayer() {
        mainMenuNote.set("");
        hasMainMenuNote.set(false);
        refreshSaves();
        show(SINGLE_PLAYER);
    }

    /** Read the saves folder into the list. */
    void refreshSaves() {
        List<SaveRow> rows =
                Saves.list(SaveUtil.savesFolder()).stream().map(GameMenus::rowOf).toList();
        saves.set(rows);
        noSaves.set(rows.isEmpty());
    }

    /**
     * How a save appears in the list.
     *
     * @param save The save.
     * @return Its row.
     */
    static SaveRow rowOf(@NonNull SaveSummary save) {
        ResourceBundle bundle = FactoryClientPlugin.getResourceBundle();
        String played =
                PLAYED_FORMAT
                        .withLocale(Locale.getDefault())
                        .format(
                                Instant.ofEpochMilli(save.lastPlayed())
                                        .atZone(ZoneId.systemDefault()));
        String details =
                SafeResourceLoader.getStringFormatted(
                        "MENU_SINGLE_PLAYER_SAVE_DETAILS",
                        bundle,
                        played,
                        worldTypeName(save.worldType()),
                        save.gameVersion());
        return new SaveRow(save.folder(), save.name(), details);
    }

    /**
     * Play the save whose row's Play button was pressed.
     *
     * @param event The click, whose item is the row.
     */
    private void play(@NonNull SpecEvent event) {
        if (!(event.item() instanceof SaveRow row)) {
            return;
        }
        var folder = SaveUtil.savesFolder().resolve(row.folder());
        Saves.markPlayed(folder, clock);
        Saves.read(folder)
                .ifPresentOrElse(
                        save -> {
                            hideAll();
                            startGame.accept(save);
                        },
                        // Deleted or broken since the list was read
                        this::refreshSaves);
    }

    /** Reset the New Game screen's fields and show it. */
    void showNewGame() {
        loadWorldTypes();
        ResourceBundle bundle = FactoryClientPlugin.getResourceBundle();
        textInput(NAME_INPUT)
                .text(SafeResourceLoader.getString("MENU_NEW_GAME_DEFAULT_NAME", bundle));
        textInput(SEED_INPUT).text("");
        setError("");
        show(NEW_GAME);
    }

    /** Find the world types the data defines, keeping the one chosen if it is still there. */
    private void loadWorldTypes() {
        String chosen = worldTypes.isEmpty() ? null : worldTypes.get(worldTypeIndex);
        worldTypes.clear();
        WorldgenData.load(List.of(DefinitionLoader.dataFolder()))
                .all(Kind.WORLD_TYPE)
                .forEach(file -> worldTypes.add(file.id()));
        int found = chosen == null ? -1 : worldTypes.indexOf(chosen);
        worldTypeIndex = Math.max(found, 0);
        worldTypeNames.set(worldTypes.stream().map(GameMenus::worldTypeName).toList());
        selectedWorldType.set(worldTypes.isEmpty() ? -1 : worldTypeIndex);
    }

    /**
     * Choose the world type the player picked in the dropdown.
     *
     * @param event The choice, whose value is the index.
     */
    private void chooseWorldType(@NonNull SpecEvent event) {
        try {
            int index = Integer.parseInt(event.value());
            if (index >= 0 && index < worldTypes.size()) {
                worldTypeIndex = index;
                selectedWorldType.set(index);
            }
        } catch (NumberFormatException e) {
            log.warn("The world type dropdown sent {}, not an index", event.value());
        }
    }

    /**
     * The name to show for a world type: its string if the bundle has one, otherwise its ID.
     *
     * @param worldType The world type's ID, such as {@code lotomation:overworld}.
     * @return Its name.
     */
    static String worldTypeName(@NonNull String worldType) {
        String key = WORLD_TYPE_KEY_PREFIX + worldType.replace(':', '_').replace('/', '_');
        try {
            ResourceBundle bundle = FactoryClientPlugin.getResourceBundle();
            if (bundle != null && bundle.containsKey(key)) {
                return bundle.getString(key);
            }
        } catch (MissingResourceException e) {
            // Fall back to the ID
        }
        return worldType;
    }

    /** Make the save from the New Game screen and start playing it. */
    private void create() {
        ResourceBundle bundle = FactoryClientPlugin.getResourceBundle();
        if (worldTypes.isEmpty()) {
            setError(SafeResourceLoader.getString("MENU_NEW_GAME_NO_WORLD_TYPES", bundle));
            return;
        }
        String name = textInput(NAME_INPUT).getText().strip();
        if (name.isEmpty()) {
            name = SafeResourceLoader.getString("MENU_NEW_GAME_DEFAULT_NAME", bundle);
        }
        NewGame game =
                new NewGame(name, worldTypes.get(worldTypeIndex), textInput(SEED_INPUT).getText());
        final SaveSummary save;
        try {
            save =
                    Saves.create(
                            SaveUtil.savesFolder(), game, gameVersion(), new SecureRandom(), clock);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not create the save {}", name, e);
            setError(
                    SafeResourceLoader.getStringFormatted(
                            "MENU_NEW_GAME_FAILED", bundle, e.getLocalizedMessage()));
            return;
        }
        log.info("Created save {} in {}", save.name(), save.folder());
        hideAll();
        startGame.accept(save);
    }

    /**
     * The game's version, which saves record: Factory-Core's.
     *
     * @return The version, or 0.0.0 if Factory-Core isn't loaded.
     */
    private static String gameVersion() {
        return PluginManager.getInstance()
                .getInfo(FactoryPlugin.PLUGIN_NAME)
                .map(PluginInfo::getVersion)
                .orElse("0.0.0");
    }

    /**
     * Show an error on the New Game screen, or clear it.
     *
     * @param message The error, or blank for none.
     */
    private void setError(@NonNull String message) {
        error.set(message);
        hasError.set(!message.isBlank());
    }

    /**
     * Find a text field on the New Game screen.
     *
     * @param path Its path from the screen's root.
     * @return The field.
     * @throws IllegalStateException If the spec doesn't have it.
     */
    private TextInput textInput(@NonNull String path) {
        Node<?> node = newGame.find(path);
        if (node instanceof TextInput input) {
            return input;
        }
        throw new IllegalStateException("The New Game screen has no text field " + path);
    }
}
