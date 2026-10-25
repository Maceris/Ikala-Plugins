package com.ikalagaming.characters;

import com.ikalagaming.event.Listener;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.PluginManager;
import com.ikalagaming.plugins.config.ConfigManager;
import com.ikalagaming.plugins.config.PluginConfig;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

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

    /** A console command, to show how plugins register them. */
    public static final String CHARACTER_COMMAND = "character";

    /*
     * The plugin manager calls this once each time the plugin is enabled, and keeps the listeners it
     * registered, so it unregisters the same ones on disable. No need to cache them here.
     */
    @Override
    public Set<Listener> getListeners() {
        return Set.of(new CharacterListener());
    }

    @Override
    public boolean onEnable() {
        /*
         * Config.yml from the jar is copied to the config folder if it is missing. The same object
         * is returned each time it's loaded, and it is safe to use from any thread.
         */
        PluginConfig config = ConfigManager.loadConfig(getName());

        log.info("Nested string: {}", config.getString("standard-settings.other-string"));

        if (config.getBoolean("standard-settings.printMessage")) {
            log.info("{}", config.getString("log-string"));
        }

        /*
         * The typed getters convert between compatible types, so this also works for "2" or 2.0.
         * Missing or wrong values log a warning and return 0 instead of throwing.
         */
        log.info("Repeats: {}", config.getInt("standard-settings.repeats"));

        config.set("test-path.new-key", "Updated value!");
        ConfigManager.saveConfigToDisk(getName());

        PluginManager.getInstance()
                .registerCommand(CHARACTER_COMMAND, this::onCharacterCommand, getName());

        log.info("Hello world! The characters plugin is enabled.");
        return true;
    }

    @Override
    public boolean onDisable() {
        // Unloading also removes the plugin's commands, but they shouldn't run while disabled
        PluginManager.getInstance().unregisterCommand(CHARACTER_COMMAND);
        return true;
    }

    @Override
    public void onUpgrade(@NonNull String lastVersion) {
        log.info("Upgraded from version {}, convert old data here.", lastVersion);
    }

    /**
     * Handle the character command.
     *
     * @param args The arguments that were typed after the command.
     */
    private void onCharacterCommand(List<String> args) {
        log.info("Character command run with arguments {}", args);
    }
}
