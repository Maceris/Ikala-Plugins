package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.enums.StyleVariable;
import com.ikalagaming.graphics.ui.style.ComputedStyle;
import com.ikalagaming.graphics.ui.style.StyleKey;
import com.ikalagaming.graphics.ui.style.StyleState;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a node's computed style into the IkGui style colors and variables to push around it. The
 * mapping is pure, so it can be tested without IkGui; {@link #push(List)} applies it. Properties
 * the style doesn't set push nothing, so IkGui's own style shows through.
 */
final class IkGuiStyler {

    /** The kinds of IkGui item a node is drawn as, each with its own style slots. */
    enum Kind {
        /** {@code IkGui.button}. */
        BUTTON,
        /** {@code IkGui.inputText}. */
        TEXT_INPUT,
        /** {@code IkGui.selectable}. */
        SELECTABLE,
        /** Plain text. */
        LABEL,
        /** A surface's window. */
        SURFACE,
        /** A child window, for scrolls and immediate areas. */
        CHILD
    }

    /** One IkGui style push. */
    sealed interface Push {}

    /**
     * Push a color.
     *
     * @param type The color slot.
     * @param color The packed color.
     */
    record ColorPush(@NonNull ColorType type, int color) implements Push {}

    /**
     * Push a one-number style variable.
     *
     * @param variable The variable.
     * @param value The value, in pixels.
     */
    record FloatPush(@NonNull StyleVariable variable, float value) implements Push {}

    /**
     * Push a two-number style variable.
     *
     * @param variable The variable.
     * @param x The first value, in pixels.
     * @param y The second value, in pixels.
     */
    record PairPush(@NonNull StyleVariable variable, float x, float y) implements Push {}

    /**
     * How many colors and variables were pushed, so they can be popped.
     *
     * @param colors The number of colors.
     * @param variables The number of variables.
     */
    record Pushed(int colors, int variables) {
        /** Pop everything that was pushed. */
        void pop() {
            if (colors > 0) {
                IkGui.popStyleColor(colors);
            }
            if (variables > 0) {
                IkGui.popStyleVar(variables);
            }
        }
    }

    /**
     * Work out the pushes for a node.
     *
     * @param kind What the node is drawn as.
     * @param style The node's computed style.
     * @param padding The padding to use as frame or window padding, or null for none.
     * @param scale Pixels per UI unit.
     * @param fontPixels The node's font size in pixels, for ems.
     * @return The pushes, in order.
     */
    static List<Push> pushes(
            @NonNull Kind kind,
            @NonNull ComputedStyle style,
            Insets padding,
            float scale,
            float fontPixels) {
        Builder out = new Builder(style, scale, fontPixels);
        switch (kind) {
            case BUTTON -> {
                out.color(ColorType.BUTTON, style.get(StyleKey.BACKGROUND));
                out.color(ColorType.BUTTON_HOVERED, hovered(style));
                out.color(ColorType.BUTTON_ACTIVE, held(style));
                out.frame(padding);
            }
            case TEXT_INPUT -> {
                out.color(ColorType.FRAME_BACKGROUND, style.get(StyleKey.BACKGROUND));
                out.color(ColorType.FRAME_BACKGROUND_HOVERED, hovered(style));
                out.color(ColorType.FRAME_BACKGROUND_ACTIVE, held(style));
                out.frame(padding);
            }
            case SELECTABLE -> {
                out.color(
                        ColorType.HEADER,
                        style.getInState(StyleKey.BACKGROUND, StyleState.SELECTED));
                out.color(ColorType.HEADER_HOVERED, hovered(style));
                out.color(ColorType.HEADER_ACTIVE, held(style));
                out.color(ColorType.TEXT, style.get(StyleKey.TEXT));
                out.length(StyleVariable.SELECTABLE_ROUNDING, StyleKey.ROUNDING);
            }
            case LABEL -> out.color(ColorType.TEXT, style.get(StyleKey.TEXT));
            case SURFACE -> {
                out.color(ColorType.WINDOW_BACKGROUND, style.get(StyleKey.BACKGROUND));
                out.color(ColorType.BORDER, style.get(StyleKey.BORDER));
                out.length(StyleVariable.WINDOW_BORDER_SIZE, StyleKey.BORDER_SIZE);
                out.length(StyleVariable.WINDOW_ROUNDING, StyleKey.ROUNDING);
                out.pair(StyleVariable.WINDOW_PADDING, padding);
            }
            case CHILD -> {
                out.color(ColorType.CHILD_BACKGROUND, style.get(StyleKey.BACKGROUND));
                out.color(ColorType.BORDER, style.get(StyleKey.BORDER));
                out.length(StyleVariable.CHILD_BORDER_SIZE, StyleKey.BORDER_SIZE);
                out.length(StyleVariable.CHILD_ROUNDING, StyleKey.ROUNDING);
            }
        }
        return out.pushes;
    }

    /**
     * Apply pushes to IkGui.
     *
     * @param pushes The pushes, from {@link #pushes}.
     * @return What to pop afterwards.
     */
    static Pushed push(@NonNull List<Push> pushes) {
        int colors = 0;
        int variables = 0;
        for (Push push : pushes) {
            switch (push) {
                case ColorPush color -> {
                    IkGui.pushStyleColor(color.type(), color.color());
                    ++colors;
                }
                case FloatPush value -> {
                    IkGui.pushStyleVarFloat(value.variable(), value.value());
                    ++variables;
                }
                case PairPush pair -> {
                    IkGui.pushStyleVarFloat2(pair.variable(), pair.x(), pair.y());
                    ++variables;
                }
            }
        }
        return new Pushed(colors, variables);
    }

    /**
     * Work out and apply the pushes for a node in one go.
     *
     * @param kind What the node is drawn as.
     * @param node The node, after layout.
     * @param frame Details about the current frame.
     * @return What to pop afterwards.
     */
    static Pushed push(@NonNull Kind kind, @NonNull Node<?> node, @NonNull UiFrame frame) {
        return push(
                pushes(
                        kind,
                        node.style,
                        node.resolvedPadding,
                        frame.context().scale(),
                        node.fontPixels));
    }

    /**
     * The color set only for the hovered state.
     *
     * @param style The style.
     * @return The color, or null.
     */
    private static Object hovered(ComputedStyle style) {
        return style.getInState(StyleKey.BACKGROUND, StyleState.HOVERED);
    }

    /**
     * The color set only for the held state.
     *
     * @param style The style.
     * @return The color, or null.
     */
    private static Object held(ComputedStyle style) {
        return style.getInState(StyleKey.BACKGROUND, StyleState.HELD);
    }

    /** Collects pushes for one node. */
    private static final class Builder {
        /** The node's style. */
        private final ComputedStyle style;

        /** Pixels per UI unit. */
        private final float scale;

        /** The node's font size in pixels. */
        private final float fontPixels;

        /** The pushes so far. */
        private final List<Push> pushes = new ArrayList<>();

        Builder(ComputedStyle style, float scale, float fontPixels) {
            this.style = style;
            this.scale = scale;
            this.fontPixels = fontPixels;
        }

        /**
         * Push a color, if it is set.
         *
         * @param type The color slot.
         * @param value The color, or null.
         */
        void color(ColorType type, Object value) {
            if (value instanceof Integer color) {
                pushes.add(new ColorPush(type, color));
            }
        }

        /**
         * Push a length property as a one-number variable, if it is set.
         *
         * @param variable The variable.
         * @param key The property.
         */
        void length(StyleVariable variable, StyleKey key) {
            Length length = style.length(key);
            if (length != null) {
                pushes.add(new FloatPush(variable, length.resolve(0, scale, fontPixels)));
            }
        }

        /**
         * Push padding as a two-number variable, if there is any.
         *
         * @param variable The variable.
         * @param padding The padding, or null.
         */
        void pair(StyleVariable variable, Insets padding) {
            if (padding != null) {
                pushes.add(
                        new PairPush(
                                variable,
                                padding.left().resolve(0, scale, fontPixels),
                                padding.top().resolve(0, scale, fontPixels)));
            }
        }

        /**
         * Push the shared frame properties of buttons and text fields.
         *
         * @param padding The frame padding, or null.
         */
        void frame(Insets padding) {
            color(ColorType.TEXT, style.get(StyleKey.TEXT));
            color(ColorType.BORDER, style.get(StyleKey.BORDER));
            length(StyleVariable.FRAME_ROUNDING, StyleKey.ROUNDING);
            length(StyleVariable.FRAME_BORDER_SIZE, StyleKey.BORDER_SIZE);
            pair(StyleVariable.FRAME_PADDING, padding);
        }
    }

    /** Static helpers only. */
    private IkGuiStyler() {}
}
