package com.ikalagaming.factory.client;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.input.InputAction;
import com.ikalagaming.graphics.input.KeyBinding;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;

import java.util.List;
import java.util.Locale;

/**
 * The actions Lotomation registers for players to bind. Each one's name and category come from the
 * plugin's strings, as {@code CONTROL_<name>} and {@code CONTROL_CATEGORY_<category>}.
 */
public final class GameControls {

    /** The namespace of the IDs. */
    private static final String NAMESPACE = "lotomation:";

    /** Category string keys. */
    private static final String MOVEMENT = "MOVEMENT";

    private static final String INTERFACE = "INTERFACE";

    public static final String FORWARD = NAMESPACE + "forward";
    public static final String BACK = NAMESPACE + "back";
    public static final String LEFT = NAMESPACE + "left";
    public static final String RIGHT = NAMESPACE + "right";
    public static final String JUMP = NAMESPACE + "jump";
    public static final String SNEAK = NAMESPACE + "sneak";
    public static final String SPRINT = NAMESPACE + "sprint";
    public static final String PAUSE = NAMESPACE + "pause";
    public static final String CHAT = NAMESPACE + "chat";
    public static final String CHAT_ALTERNATE = NAMESPACE + "chat_alternate";
    public static final String COMMAND = NAMESPACE + "command";
    public static final String FREE_MOUSE = NAMESPACE + "free_mouse";

    /**
     * An action's definition before its text is looked up.
     *
     * @param id The ID.
     * @param category The category's string key suffix.
     * @param binding The default binding.
     */
    private record Definition(
            @NonNull String id, @NonNull String category, @NonNull String binding) {}

    /** Every action, in the order the Controls screen lists them within a category. */
    private static final List<Definition> DEFINITIONS =
            List.of(
                    new Definition(FORWARD, MOVEMENT, "w"),
                    new Definition(BACK, MOVEMENT, "s"),
                    new Definition(LEFT, MOVEMENT, "a"),
                    new Definition(RIGHT, MOVEMENT, "d"),
                    new Definition(JUMP, MOVEMENT, "space"),
                    new Definition(SNEAK, MOVEMENT, "left_shift"),
                    new Definition(SPRINT, MOVEMENT, "left_ctrl"),
                    new Definition(PAUSE, INTERFACE, "escape"),
                    new Definition(CHAT, INTERFACE, "t"),
                    new Definition(CHAT_ALTERNATE, INTERFACE, "enter"),
                    new Definition(COMMAND, INTERFACE, "forward_slash"),
                    new Definition(FREE_MOUSE, INTERFACE, "left_alt"));

    /** Not made. */
    private GameControls() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * Register every action.
     *
     * @param graphics The plugin's graphics.
     */
    public static void register(@NonNull GraphicsContext graphics) {
        for (Definition definition : DEFINITIONS) {
            final String suffix = definition.id().substring(NAMESPACE.length());
            graphics.registerAction(
                    new InputAction(
                            definition.id(),
                            text("CONTROL_CATEGORY_" + definition.category()),
                            text("CONTROL_" + suffix.toUpperCase(Locale.ROOT)),
                            KeyBinding.parse(definition.binding())));
        }
    }

    /**
     * A string from the plugin's bundle.
     *
     * @param key The key.
     * @return The string.
     */
    private static String text(@NonNull String key) {
        return SafeResourceLoader.getString(key, FactoryClientPlugin.getResourceBundle());
    }
}
