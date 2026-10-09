/**
 * Retained UI for IkalaScript: the {@code ui} global scripts get, and the handles they hold.
 *
 * <p>A script opens a spec with {@code ui.open(path)}, sets the values its bindings show through
 * the returned {@link com.ikalagaming.graphics.ui.script.ScriptSpec}, and waits for the player with
 * {@code await(tag)}. The spec's {@code resume(tag, value)} actions resume it with a value. Specs
 * can also start scripts with {@code script(file.iks#label)}.
 *
 * <p>Scripts hold handles, never nodes, and everything they open is owned by their plugin. When it
 * unloads, its scripts are terminated and its specs closed, so a script can't keep a UI or a plugin
 * alive.
 */
package com.ikalagaming.graphics.ui.script;
