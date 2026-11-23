package com.ikalagaming.factory;

import com.ikalagaming.event.Listener;
import com.ikalagaming.factory.server.Server;
import com.ikalagaming.factory.server.ServerListener;
import com.ikalagaming.launcher.AppPaths;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.permissions.PermissionDefault;
import com.ikalagaming.permissions.PermissionPlugin;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.PluginManager;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.Map;

/**
 * Handles the server-side part of Lotomation.
 *
 * @author Ches Burks
 */
@Slf4j
public class FactoryServerPlugin extends Plugin {
    /** The name of the plugin in Java for convenience, should match the name in plugin.yml. */
    public static final String PLUGIN_NAME = "Factory-Server";

    /**
     * The resource bundle for the plugin.
     *
     * @return The bundle.
     * @param resourceBundle The new bundle to use.
     */
    @Getter @Setter private static ResourceBundle resourceBundle;

    /** The server that we will use, which starts out not running. */
    @Getter private static final Server server = new Server();

    private Set<Listener> listeners;

    @Override
    public Set<Listener> getListeners() {
        if (null == this.listeners) {
            this.listeners = Collections.synchronizedSet(new HashSet<>());
            this.listeners.add(new ServerListener(server));
        }
        return this.listeners;
    }

    @Override
    public String getName() {
        return FactoryServerPlugin.PLUGIN_NAME;
    }

    /**
     * Host the instance's save, if the game was started with one ({@code bin/runServer}), as a
     * dedicated server that opens its port.
     *
     * @return True.
     */
    /** The resource declaring this plugin's permission nodes. */
    private static final String PERMISSIONS_RESOURCE = "/permissions.yml";

    /** The commands the console can run, besides the plugin manager's own. */
    private static final List<String> CONSOLE_COMMANDS =
            List.of("gamemode", "op", "deop", "accept", "say");

    @Override
    public boolean onEnable() {
        declarePermissions();
        for (String command : CONSOLE_COMMANDS) {
            PluginManager.getInstance()
                    .registerCommand(
                            command,
                            args -> server.consoleCommand(command + " " + String.join(" ", args)),
                            PLUGIN_NAME);
        }
        AppPaths.instance()
                .ifPresent(
                        instance -> {
                            log.info("Hosting {} as a dedicated server", instance);
                            server.open(instance);
                        });
        return true;
    }

    /** Declare the permission nodes in {@value #PERMISSIONS_RESOURCE}. */
    private void declarePermissions() {
        try (InputStream in = FactoryServerPlugin.class.getResourceAsStream(PERMISSIONS_RESOURCE)) {
            if (in == null) {
                log.warn("{} is missing; commands will need operator", PERMISSIONS_RESOURCE);
                return;
            }
            if (new Yaml().load(in) instanceof Map<?, ?> nodes) {
                PermissionPlugin.getRegistry()
                        .registerAll(PLUGIN_NAME, nodes, PermissionDefault.OP);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read {}", PERMISSIONS_RESOURCE, e);
        }
    }

    @Override
    public boolean onDisable() {
        for (String command : CONSOLE_COMMANDS) {
            PluginManager.getInstance().unregisterCommand(command);
        }
        PermissionPlugin.getRegistry().removeAllOwnedBy(PLUGIN_NAME);
        server.stop();
        return true;
    }

    @Override
    public boolean onLoad() {
        try {
            setResourceBundle(
                    ResourceBundle.getBundle(
                            "com.ikalagaming.factory.strings", Localization.getLocale()));
        } catch (MissingResourceException missingResource) {
            // don't localize this since it would fail anyway
            log.warn("Locale not found for Factory-Server in onLoad()");
        }
        return true;
    }

    @Override
    public boolean onUnload() {
        setResourceBundle(null);
        return true;
    }
}
