package com.ikalagaming.graphics.ui;

import lombok.NonNull;

import java.util.function.Consumer;

/**
 * A tool a plugin offers, like the factory plugin's Worldgen Lab, for an editor's menus to list
 * without knowing the plugin. Tools are owned by the plugin that registered them and disappear when
 * it unloads.
 *
 * <p>A menu hides itself while a tool is open, and gives the tool a way back: something to run when
 * the tool closes, which shows the menu again. A tool that leaves the menu showing behind it runs
 * that straight away.
 *
 * @param id The tool's ID, unique among all tools, like {@code factory/worldgen-lab}.
 * @param label What menus show for it.
 * @param open Opens the tool, given the way back to the menu. Run on the render thread, like other
 *     UI events.
 */
public record Tool(@NonNull String id, @NonNull String label, @NonNull Consumer<Runnable> open) {
    /**
     * A tool that leaves the menu showing behind it.
     *
     * @param id The tool's ID, unique among all tools.
     * @param label What menus show for it.
     * @param open Opens the tool.
     */
    public Tool(@NonNull String id, @NonNull String label, @NonNull Runnable open) {
        this(
                id,
                label,
                back -> {
                    open.run();
                    back.run();
                });
    }
}
