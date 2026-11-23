package com.ikalagaming.factory;

import com.ikalagaming.event.Listener;
import com.ikalagaming.factory.client.GameControls;
import com.ikalagaming.factory.gui.*;
import com.ikalagaming.factory.lab.LabTool;
import com.ikalagaming.factory.lab.WorldgenLab;
import com.ikalagaming.factory.save.Hosting;
import com.ikalagaming.factory.saves.UserDataUtil;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.gui.WindowManager;
import com.ikalagaming.graphics.gui.windows.GraphicsDebug;
import com.ikalagaming.graphics.gui.windows.IkGuiDemo;
import com.ikalagaming.graphics.gui.windows.IkScriptDebugger;
import com.ikalagaming.graphics.gui.windows.ScriptMonitor;
import com.ikalagaming.localization.Localization;
import com.ikalagaming.plugins.Plugin;
import com.ikalagaming.plugins.config.ConfigManager;
import com.ikalagaming.scripting.ScriptLaunch;
import com.ikalagaming.scripting.ScriptManager;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;

/**
 * Handles the client-side parts of Lotomation.
 *
 * @author Ches Burks
 */
@Slf4j
public class FactoryClientPlugin extends Plugin {
    /** The name of the plugin in Java for convenience, should match the name in plugin.yml. */
    public static final String PLUGIN_NAME = "Factory-Client";

    /**
     * The resource bundle for the Graphics plugin.
     *
     * @return The bundle.
     * @param resourceBundle The new bundle to use.
     */
    @Getter @Setter private static ResourceBundle resourceBundle;

    private Set<Listener> listeners;

    @Getter private WindowManager guiManager;

    /** The main menu, Single Player and New Game screens, outside editor mode. */
    private GameMenus menus;

    /**
     * Opens the Worldgen Lab.
     *
     * @return The Lab's opener, while the plugin is enabled.
     */
    @Getter private static LabTool labTool;

    /** The startup property naming a script to run once the menus are up, for checks. */
    private static final String SCRIPT_PROPERTY = "ikala.test.script";

    /** The startup property whose text the startup script gets as its global {@code args}. */
    private static final String SCRIPT_ARGS_PROPERTY = "ikala.test.scriptArgs";

    /** The config key that turns on editor mode. */
    private static final String EDITOR_MODE_KEY = "editor-mode";

    @Override
    public Set<Listener> getListeners() {
        if (null == listeners) {
            listeners = Collections.synchronizedSet(new HashSet<>());
        }
        return listeners;
    }

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean onEnable() {
        var graphics = GraphicsManager.forPlugin(getName());
        labTool = new LabTool(graphics);
        if (ConfigManager.loadConfig(PLUGIN_NAME).getOrDefault(EDITOR_MODE_KEY, false)) {
            // In the editor, the Lab is a tool, and the game's menus stay closed
            graphics.ui().registerTool(WorldgenLab.SURFACE_ID, LabTool.LABEL, labTool::open);
            return true;
        }
        UserDataUtil.createUserDataFolder();
        GameControls.register(graphics);
        guiManager = GraphicsManager.getWindowManager();
        guiManager.addWindow(graphics, IkGuiDemo.WINDOW_NAME, new IkGuiDemo());
        guiManager.addWindow(graphics, GraphicsDebug.WINDOW_NAME, new GraphicsDebug());
        guiManager.addWindow(graphics, IkScriptDebugger.WINDOW_NAME, new IkScriptDebugger());
        guiManager.addWindow(graphics, ScriptMonitor.WINDOW_NAME, new ScriptMonitor());
        menus = new GameMenus(graphics, SinglePlayerGame::start);
        menus.open();
        SinglePlayerGame.setMenus(menus);
        new GameOverlay(graphics).install();
        startTestScript();
        joinServerAtStart();

        return true;
    }

    /**
     * Join the server named by {@value SinglePlayerGame#CONNECT_PROPERTY}, as host or host:port, if
     * set, until the menus can do it.
     */
    private void joinServerAtStart() {
        final String target = System.getProperty(SinglePlayerGame.CONNECT_PROPERTY);
        if (target == null || target.isBlank()) {
            return;
        }
        final int colon = target.lastIndexOf(':');
        final String host = colon < 0 ? target : target.substring(0, colon);
        int port = Hosting.DEFAULT_PORT;
        if (colon >= 0) {
            try {
                port = Integer.parseInt(target.substring(colon + 1));
            } catch (NumberFormatException e) {
                log.warn("Can't join {}: the port isn't a number", target);
                return;
            }
        }
        final InetSocketAddress address = new InetSocketAddress(host, port);
        GraphicsManager.runOnMainThread(
                () -> {
                    // The menus would keep the keyboard
                    menus.hideAll();
                    SinglePlayerGame.joinServer(address);
                });
    }

    /**
     * Start the script named by {@value #SCRIPT_PROPERTY}, if any, for checks and captures that
     * drive the menus. In editor mode the editor runs it instead.
     */
    private void startTestScript() {
        final String script = System.getProperty(SCRIPT_PROPERTY);
        if (script != null
                && ScriptManager.start(
                                ScriptLaunch.file(Path.of(script))
                                        .owner(getName())
                                        .global(
                                                "args",
                                                System.getProperty(SCRIPT_ARGS_PROPERTY, "")))
                        .isEmpty()) {
            log.warn("Could not start the startup script {}", script);
        }
    }

    @Override
    public boolean onDisable() {
        var graphics = GraphicsManager.forPlugin(getName());
        if (!graphics.isClosed()) {
            // Otherwise menus keep offering it, and opening it builds a Lab while disabled
            graphics.ui().removeTool(WorldgenLab.SURFACE_ID);
        }
        if (labTool != null) {
            labTool.close();
            labTool = null;
        }
        // Their surfaces are removed with the plugin's graphics
        menus = null;
        SinglePlayerGame.stop();
        SinglePlayerGame.setMenus(null);
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
            log.warn("Locale not found for Factory-Client in onLoad()");
        }
        return true;
    }

    @Override
    public boolean onUnload() {
        setResourceBundle(null);
        return true;
    }
}
