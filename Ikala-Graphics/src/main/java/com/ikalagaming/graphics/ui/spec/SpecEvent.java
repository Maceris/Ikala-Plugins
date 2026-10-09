package com.ikalagaming.graphics.ui.spec;

import com.ikalagaming.graphics.ui.Node;

import lombok.NonNull;

/**
 * Something that happened to a node built from a spec, handed to a named handler.
 *
 * @param node The node, such as the button that was clicked.
 * @param value A value that came with the event, like the text of a submitted text field, or null.
 * @param item The list item the node was repeated for, or null if it isn't in a repeat.
 */
public record SpecEvent(@NonNull Node<?> node, String value, Object item) {}
