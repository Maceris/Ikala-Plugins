/**
 * UI specs: retained UI described in YAML, built into the same nodes and surfaces as the Java API.
 *
 * <p>{@link com.ikalagaming.graphics.ui.spec.SpecLoader} loads a {@link
 * com.ikalagaming.graphics.ui.spec.UiSpec}: the surface settings, templates with parameters, and
 * the node tree. Opening it through {@code GraphicsContext.ui().open(spec, bindings)} builds it
 * with {@link com.ikalagaming.graphics.ui.spec.SpecBindings}, which supply what the spec names:
 *
 * <ul>
 *   <li>handlers for events like {@code onClick: start-game};
 *   <li>{@link com.ikalagaming.graphics.ui.spec.Observable}s for {@code {name}} bindings in text
 *       and flags, which update the UI when they change, from any thread;
 *   <li>{@link com.ikalagaming.graphics.ui.spec.ObservableList}s for {@code repeat}, which keeps
 *       the nodes of unchanged items;
 *   <li>a resource bundle for {@code @KEY} text.
 * </ul>
 *
 * Anything missing is reported before anything is shown. Plugins add node types with {@code
 * registerNodeType}, and the editor rebuilds open specs when their files change.
 *
 * <p>Everything a plugin supplies is released when it unloads: its specs are closed and stop
 * listening to observables, its node types are removed, and other plugins' specs built from those
 * types are closed too, so no plugin class outlives its plugin.
 */
package com.ikalagaming.graphics.ui.spec;
