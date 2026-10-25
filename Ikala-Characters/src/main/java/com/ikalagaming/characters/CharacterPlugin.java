package com.ikalagaming.characters;

import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.demo.SkinnedDemo;
import com.ikalagaming.characters.registry.BuiltInContent;
import com.ikalagaming.characters.registry.CharacterLoader;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.registry.ContentSource;
import com.ikalagaming.characters.registry.Schemas;
import com.ikalagaming.event.Listener;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.PluginManager;
import com.ikalagaming.plugins.config.ConfigManager;
import com.ikalagaming.plugins.config.PluginConfig;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    /** The most problems logged by a load, so a broken folder doesn't flood the console. */
    private static final int MAX_LOGGED_PROBLEMS = 50;

    /** The skinned tube demo, which {@code character demo} turns on and off. */
    private final SkinnedDemo demo = new SkinnedDemo(PLUGIN_NAME);

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
        PluginManager.getInstance()
                .registerCommand(CHARACTER_COMMAND, this::onCharacterCommand, getName());
        return true;
    }

    @Override
    public boolean onDisable() {
        // Unloading also removes the plugin's commands, but they shouldn't run while disabled
        PluginManager.getInstance().unregisterCommand(CHARACTER_COMMAND);
        demo.stop();
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
                            "Usage: character reload | demo | validate namespace=folder | schemas"
                                    + " <output folder>");
        }
    }
}
