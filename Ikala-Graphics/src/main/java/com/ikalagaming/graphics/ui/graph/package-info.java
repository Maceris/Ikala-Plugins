/**
 * Graphs on a {@link com.ikalagaming.graphics.ui.Canvas}: links between its children, how they are
 * routed and drawn, and a stock node to put on them, for things like quest chains and skill trees.
 *
 * <p>It comes in layers that can be used on their own:
 *
 * <ul>
 *   <li>{@link com.ikalagaming.graphics.ui.graph.PathStroke} draws a path: width, color, dashes,
 *       parallel strokes, corners and arrowheads.
 *   <li>{@link com.ikalagaming.graphics.ui.graph.Router} routes links from the nodes' rectangles:
 *       straight, curved, or horizontal and vertical lines, where links leaving or entering the
 *       same side of a node share a trunk.
 *   <li>{@link com.ikalagaming.graphics.ui.graph.Edge}s on a canvas are routed by it and styled by
 *       the theme, and {@link com.ikalagaming.graphics.ui.graph.Highlight} picks the ones that
 *       light up while the mouse is over a node.
 * </ul>
 *
 * Nodes are placed by hand, with {@code position}; arranging them automatically is left to later.
 */
package com.ikalagaming.graphics.ui.graph;
