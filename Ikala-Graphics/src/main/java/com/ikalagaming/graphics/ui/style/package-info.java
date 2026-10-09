/**
 * Themes for retained UI: tokens, type defaults, classes, interaction states and screen variants.
 *
 * <p>A {@link com.ikalagaming.graphics.ui.style.Theme} holds named tokens, a default {@link
 * com.ikalagaming.graphics.ui.style.Style} for each node type, classes that nodes opt into, and
 * variants that change tokens for some screens. Each node's style is resolved from, lowest priority
 * first: its type's style, its classes in order, its own inline style, and finally setters on the
 * node like {@code padding(...)}. Values may refer to tokens, so one change to a token changes
 * every style that uses it.
 *
 * <p>Themes are usually written in YAML and read with {@link
 * com.ikalagaming.graphics.ui.style.ThemeLoader}. Graphics bundles a default theme matching IkGui's
 * dark style. A plugin can replace it, or add its own tokens and classes on top, through {@code
 * GraphicsContext.ui()}.
 *
 * <p>Token names are free-form. The default theme names colors by role ({@code color.text}, {@code
 * color.panel}, {@code color.accent}) and spacing as named steps ({@code spacing.small}, {@code
 * spacing.medium}, {@code spacing.large}); plugins prefix their own, like {@code converter.error}.
 */
package com.ikalagaming.graphics.ui.style;
