/**
 * Retained UI: trees of nodes that graphics lays out and submits to IkGui every frame.
 *
 * <p>A {@link com.ikalagaming.graphics.ui.Surface} is one IkGui window, placed on the screen by its
 * {@link com.ikalagaming.graphics.ui.Anchors} and {@link com.ikalagaming.graphics.ui.Sizing}.
 * Inside it, containers ({@link com.ikalagaming.graphics.ui.Row}, {@link
 * com.ikalagaming.graphics.ui.Column}, {@link com.ikalagaming.graphics.ui.Overlay}) lay out leaves
 * such as {@link com.ikalagaming.graphics.ui.Button} and {@link
 * com.ikalagaming.graphics.ui.CustomItem}. Sizes mix UI units, fractions of the parent and ems
 * ({@link com.ikalagaming.graphics.ui.Length}), so the same UI works at any screen size and scale.
 *
 * <p>{@link com.ikalagaming.graphics.ui.Grid} lays children out in cells, and {@link
 * com.ikalagaming.graphics.ui.VirtualGrid} only makes the cells it shows, for very long lists.
 * {@link com.ikalagaming.graphics.ui.Canvas} places children freely under pan and zoom, with links
 * between them from {@link com.ikalagaming.graphics.ui.graph}.
 *
 * <p>Every button and custom item is a real IkGui item, so mouse, Tab, keyboard and gamepad
 * navigation all work. Actions they fire run on the render thread after the GUI is drawn.
 *
 * <p>Plugins show surfaces through {@code GraphicsContext.ui()}, which owns them, so they are
 * removed when the plugin unloads.
 *
 * <p>{@link com.ikalagaming.graphics.ui.automation} drives the UI like a player would, for tests
 * and scripted checks.
 */
package com.ikalagaming.graphics.ui;
