package com.ikalagaming.characters;

import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.demo.CharacterPreview;
import com.ikalagaming.characters.demo.SkinnedDemo;
import com.ikalagaming.characters.editor.CharacterEditor;
import com.ikalagaming.characters.registry.BuiltInContent;
import com.ikalagaming.characters.registry.CharacterLoader;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.registry.ContentSource;
import com.ikalagaming.characters.registry.Schemas;
import com.ikalagaming.event.Listener;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.PluginManager;
import com.ikalagaming.plugins.config.ConfigManager;
import com.ikalagaming.plugins.config.PluginConfig;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Character and animal design: body plans, parts, appearance and animation.
 *
 * @author Ches Burks
 */
@Slf4j
public class CharacterPlugin extends Plugin {
    /**
     * The name of the plugin in Java for convenience, for other classes to refer to it. Must match
     * the name in plugin.yml, which {@link #getName()} returns by default.
     */
    public static final String PLUGIN_NAME = "Ikala-Characters";

    /** The console command for loading and checking character content. */
    public static final String CHARACTER_COMMAND = "character";

    /** The config entry listing content folders, as {@code namespace=folder}. */
    static final String CONTENT_FOLDERS = "content-folders";

    /** The config entry naming where the character editor saves designs, as namespace=folder. */
    static final String EDITOR_SAVE_TO = "editor.save-to";

    /** The config entry naming the rig reference file, for moving characters onto a rig. */
    static final String EDITOR_REFERENCE = "editor.reference";

    /** The reference file used when the config doesn't name one. */
    private static final String DEFAULT_REFERENCE = "test_content/characters/mannequin/male.glb";

    /** Two glTF file paths, which may hold spaces, and an optional clip name after them. */
    private static final Pattern TWO_FILES =
            Pattern.compile(
                    "(.+?\\.gl(?:b|tf))\\s+(.+?\\.gl(?:b|tf))(?:\\s+(\\S+))?",
                    Pattern.CASE_INSENSITIVE);

    /** The most problems logged by a load, so a broken folder doesn't flood the console. */
    private static final int MAX_LOGGED_PROBLEMS = 50;

    /** The skinned tube demo, which {@code character demo} turns on and off. */
    private final SkinnedDemo demo = new SkinnedDemo(PLUGIN_NAME);

    /** Shows imported characters, for {@code character show} and {@code character hide}. */
    private final CharacterPreview preview = new CharacterPreview(PLUGIN_NAME, this::getRegistry);

    /** The character editor, while enabled. */
    private CharacterEditor editor;

    /** The definitions currently loaded, replaced whole on each reload. */
    private volatile CharacterRegistry registry = CharacterRegistry.EMPTY;

    /**
     * The definitions currently loaded. Registries are immutable, so hold on to the one returned
     * for the length of a task rather than calling this repeatedly.
     *
     * @return The registry.
     */
    public CharacterRegistry getRegistry() {
        return registry;
    }

    @Override
    public Set<Listener> getListeners() {
        return Set.of();
    }

    @Override
    public boolean onEnable() {
        reload();
        var graphics = GraphicsManager.forPlugin(getName());
        editor = new CharacterEditor(graphics, getName(), this::getRegistry, this::editorSettings);
        editor.install();
        // Listed in editors' tool menus, like the asset editor's main menu
        graphics.ui().registerTool(CharacterEditor.TOOL_ID, CharacterEditor.LABEL, editor::open);
        PluginManager.getInstance()
                .registerCommand(CHARACTER_COMMAND, this::onCharacterCommand, getName());
        return true;
    }

    @Override
    public boolean onDisable() {
        // Unloading also removes the plugin's commands, but they shouldn't run while disabled
        PluginManager.getInstance().unregisterCommand(CHARACTER_COMMAND);
        demo.stop();
        preview.hide();
        if (editor != null) {
            final CharacterEditor closing = editor;
            var graphics = GraphicsManager.forPlugin(getName());
            if (!graphics.isClosed()) {
                graphics.ui().post(closing::close);
            }
            editor = null;
        }
        registry = CharacterRegistry.EMPTY;
        return true;
    }

    /** Load the content folders listed in the config, replacing the current registry. */
    public void reload() {
        PluginConfig config = ConfigManager.loadConfig(getName());
        List<ContentSource> sources = new ArrayList<>();
        if (config.isList(CONTENT_FOLDERS)) {
            for (String entry : config.getStringList(CONTENT_FOLDERS)) {
                ContentSource source = parseSource(entry);
                if (source == null) {
                    log.warn(
                            "Skipping the content folder '{}', which should be namespace=folder",
                            entry);
                } else {
                    sources.add(source);
                }
            }
        }
        CharacterLoader.Result result = loadWithBuiltIn(sources);
        logProblems(result.problems());
        registry = result.registry();
        log.info(
                "Loaded {} character definitions from the plugin and {} folders, content hash {}",
                registry.size(),
                sources.size(),
                registry.getContentHash().substring(0, 12));
    }

    /**
     * What the character editor offers, from the config.
     *
     * @return The settings.
     */
    private CharacterEditor.EditorSettings editorSettings() {
        PluginConfig config = ConfigManager.loadConfig(getName());
        String reference = config.getOrDefault(EDITOR_REFERENCE, DEFAULT_REFERENCE);
        ContentSource saveTo = parseSource(config.getOrDefault(EDITOR_SAVE_TO, ""));
        return new CharacterEditor.EditorSettings(
                Path.of(reference),
                saveTo == null ? null : saveTo.namespace(),
                saveTo == null ? null : saveTo.root(),
                this::reload);
    }

    /** Open the character editor, or close it if it is open, on the render thread. */
    private void toggleEditor() {
        final CharacterEditor shownEditor = editor;
        if (shownEditor == null) {
            return;
        }
        GraphicsManager.forPlugin(getName())
                .ui()
                .post(
                        () -> {
                            if (shownEditor.isOpen()) {
                                shownEditor.close();
                            } else {
                                // Opened from the console, there is no menu to go back to
                                shownEditor.open(() -> {});
                            }
                        });
    }

    /**
     * Load the built-in definitions and other sources, or just the others if the built-in ones
     * can't be read.
     *
     * @param sources The other sources.
     * @return The registry and problems.
     */
    private static CharacterLoader.Result loadWithBuiltIn(List<ContentSource> sources) {
        try {
            return BuiltInContent.loadWith(sources);
        } catch (IOException e) {
            log.error("Couldn't read the built-in character definitions", e);
            return CharacterLoader.load(sources);
        }
    }

    /**
     * Parse a content folder entry.
     *
     * @param entry The entry, as {@code namespace=folder}.
     * @return The source, or null if the entry is malformed.
     */
    static ContentSource parseSource(String entry) {
        int equals = entry.indexOf('=');
        if (equals <= 0 || equals == entry.length() - 1) {
            return null;
        }
        String namespace = entry.substring(0, equals).trim();
        String folder = entry.substring(equals + 1).trim();
        return new ContentSource(namespace, Path.of(folder), folder);
    }

    /**
     * Log problems found while loading, up to a limit.
     *
     * @param problems The problems.
     */
    private static void logProblems(List<Problem> problems) {
        int logged = 0;
        for (Problem problem : problems) {
            if (logged++ == MAX_LOGGED_PROBLEMS) {
                log.warn("... and {} more problems", problems.size() - MAX_LOGGED_PROBLEMS);
                break;
            }
            if (problem.severity() == Problem.Severity.ERROR) {
                log.error("{}", problem);
            } else {
                log.warn("{}", problem);
            }
        }
    }

    /**
     * Show a character from a glTF file. The console splits on spaces, so the words are put back
     * together into the file's path, except a last word that isn't a glTF file, which names the
     * clip to play.
     *
     * @param words The words after {@code show}.
     */
    private void show(List<String> words) {
        if (words.isEmpty()) {
            log.info("Usage: character show <file.glb> [clip]");
            return;
        }
        String last = words.get(words.size() - 1).toLowerCase(Locale.ROOT);
        boolean clipGiven = words.size() > 1 && !last.endsWith(".glb") && !last.endsWith(".gltf");
        String clip = clipGiven ? words.get(words.size() - 1) : null;
        String file = String.join(" ", clipGiven ? words.subList(0, words.size() - 1) : words);
        try {
            preview.show(Path.of(file), clip);
        } catch (IOException | IllegalArgumentException e) {
            log.error("Couldn't show {}: {}", file, e.getMessage());
        }
    }

    /**
     * Show a character moved onto a rig, from its glTF file and a reference file of the rig. The
     * console splits on spaces, so the words are put back together and split after each glTF file
     * name; a word after the second names the clip to play.
     *
     * @param words The words after {@code retarget}.
     */
    private void retarget(List<String> words) {
        Matcher matcher = TWO_FILES.matcher(String.join(" ", words));
        if (!matcher.matches()) {
            log.info("Usage: character retarget <file.glb> <reference.glb> [clip]");
            return;
        }
        try {
            preview.showRetargeted(
                    Path.of(matcher.group(1)), Path.of(matcher.group(2)), matcher.group(3));
        } catch (IOException | IllegalArgumentException e) {
            log.error("Couldn't retarget {}: {}", matcher.group(1), e.getMessage());
        }
    }

    /**
     * Handle the character command.
     *
     * @param args The arguments that were typed after the command.
     */
    private void onCharacterCommand(List<String> args) {
        String action = args.isEmpty() ? "" : args.get(0);
        // The console splits on spaces, so put back the ones in folder names
        String rest = args.size() < 2 ? "" : String.join(" ", args.subList(1, args.size()));
        switch (action) {
            case "reload" -> reload();
            case "show" -> show(args.subList(1, args.size()));
            case "retarget" -> retarget(args.subList(1, args.size()));
            case "hide" -> preview.hide();
            case "editor" -> toggleEditor();
            case "demo" -> {
                if (demo.isRunning()) {
                    demo.stop();
                } else {
                    demo.start();
                }
            }
            case "validate" -> {
                if (parseSource(rest) == null) {
                    log.info("Usage: character validate namespace=folder");
                    return;
                }
                // Content builds on the built-in plans, so check it along with them
                CharacterLoader.Result result = loadWithBuiltIn(List.of(parseSource(rest)));
                logProblems(result.problems());
                log.info(
                        "{} usable definitions, {} problems",
                        result.registry().size(),
                        result.problems().size());
            }
            case "schemas" -> {
                if (rest.isEmpty()) {
                    log.info("Usage: character schemas <output folder>");
                    return;
                }
                try {
                    Schemas.write(Path.of(rest));
                    log.info("Wrote the JSON Schemas to {}", rest);
                } catch (IOException e) {
                    log.error("Couldn't write the schemas to {}", rest, e);
                }
            }
            default ->
                    log.info(
                            "Usage: character reload | demo | editor | show <file> [clip] | retarget <file>"
                                    + " <reference file> [clip] | hide | validate namespace=folder |"
                                    + " schemas <output folder>");
        }
    }
}
