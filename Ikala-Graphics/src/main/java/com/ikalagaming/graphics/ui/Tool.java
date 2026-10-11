package com.ikalagaming.graphics.ui;

import lombok.NonNull;

/**
 * A tool a plugin offers, like the factory plugin's Worldgen Lab, for an editor's menus to list
 * without knowing the plugin. Tools are owned by the plugin that registered them and disappear when
 * it unloads.
 *
 * @param id The tool's ID, unique among all tools, like {@code factory/worldgen-lab}.
 * @param label What menus show for it.
 * @param open Opens the tool. Run on the render thread, like other UI events.
 */
public record Tool(@NonNull String id, @NonNull String label, @NonNull Runnable open) {}
