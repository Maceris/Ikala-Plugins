package com.ikalagaming.graphics.gui.windows;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.UI;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.CustomItem;
import com.ikalagaming.graphics.ui.Insets;
import com.ikalagaming.graphics.ui.ItemState;
import com.ikalagaming.graphics.ui.Justify;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Overlay;
import com.ikalagaming.graphics.ui.Row;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.style.StyleKey;

import lombok.NonNull;

/**
 * A surface that shows off every retained UI node type and layout option, for checking the layout
 * engine by eye and trying keyboard and gamepad navigation. Toggled from the graphics debug window.
 */
public final class UiShowcase {
    /** The ID of the showcase surface. */
    public static final String SURFACE_ID = "graphics/ui-showcase";

    /** An inventory-style slot that counts how often it was activated. */
    private static final class Slot extends CustomItem<Slot> {
        /** Slot size in UI units. */
        private static final float SIZE = 48;

        /** How often the slot was activated. */
        private int count;

        Slot(String id) {
            super(id);
            width(Sizing.fixed(SIZE));
            height(Sizing.fixed(SIZE));
            onActivate(() -> ++count);
        }

        @Override
        protected void draw(
                @NonNull DrawList drawList, @NonNull RectFloat bounds, @NonNull ItemState state) {
            // The theme's "custom" type gives the colors for each state
            int background = color(StyleKey.BACKGROUND, state, Color.rgba(0.2f, 0.22f, 0.28f, 1f));
            int accent = color(StyleKey.ACCENT, state, Color.rgba(0.8f, 0.8f, 0.85f, 1f));
            float rounding = bounds.getWidth() * 0.15f;
            drawList.addRectFilled(
                    bounds.getLeft(),
                    bounds.getTop(),
                    bounds.getRight(),
                    bounds.getBottom(),
                    background,
                    rounding);
            // An icon placeholder
            drawList.addCircleFilled(
                    bounds.getCenterX(), bounds.getCenterY(), bounds.getWidth() * 0.25f, accent);
            if (count > 0) {
                int fontSize = IkGui.getFontSize();
                drawList.addText(
                        fontSize,
                        bounds.getLeft() + 4,
                        bounds.getBottom() - fontSize - 2,
                        Color.rgba(1.0f, 1.0f, 1.0f, 1.0f),
                        Integer.toString(count),
                        0);
            }
        }
    }

    /**
     * Show or remove the showcase. It is owned by the graphics plugin itself.
     *
     * @param shown Whether it should be shown.
     */
    public static void setShown(boolean shown) {
        UI ui = GraphicsManager.forPlugin(GraphicsPlugin.PLUGIN_NAME).ui();
        if (shown) {
            ui.show(build(ui));
        } else {
            ui.remove(SURFACE_ID);
        }
    }

    /**
     * Build the showcase surface.
     *
     * @param ui The UI to create it through.
     * @return The surface, ready to show.
     */
    public static Surface build(@NonNull UI ui) {
        Column content =
                new Column("content")
                        .gap(10)
                        .padding(Insets.all(4))
                        .align(Align.STRETCH)
                        .add(
                                new Label("theme-title", "Theme classes").classes("title"),
                                themeRow(),
                                new Label("grow-title", "Row: fixed 80, then grow 1 : 2"),
                                growRow(),
                                new Label("justify-title", "Justify: start, center, end, between"),
                                justifyRow("justify-start", Justify.START),
                                justifyRow("justify-center", Justify.CENTER),
                                justifyRow("justify-end", Justify.END),
                                justifyRow("justify-between", Justify.SPACE_BETWEEN),
                                new Label(
                                        "align-title",
                                        "Align in columns: start, center, end, stretch"),
                                alignRow(),
                                new Label("overlay-title", "Overlay anchors"),
                                overlay(),
                                new Label("slots-title", "Custom items: click, Tab or gamepad"),
                                slots());

        return ui.surface(SURFACE_ID)
                .anchors(Anchors.at(1, 0.5f).offset(Length.u(-20), Length.ZERO))
                .width(Sizing.fixed(520))
                .movable()
                .content(content);
    }

    /**
     * A row showing a few of the default theme's classes.
     *
     * @return The row.
     */
    private static Row themeRow() {
        return new Row("theme")
                .gap(8)
                .align(Align.CENTER)
                .add(
                        new Button("normal", "Normal"),
                        new Button("danger", "Danger").classes("danger"),
                        new Label("muted", "Muted text").classes("muted"),
                        new Label("error", "Error text").classes("error-text"));
    }

    /**
     * A row with one fixed button and two growing ones.
     *
     * @return The row.
     */
    private static Row growRow() {
        return new Row("grow")
                .gap(8)
                .add(
                        new Button("fixed", "Fixed").width(Sizing.fixed(80)).autofocus(true),
                        new Button("one", "Grow 1").width(Sizing.grow(1)),
                        new Button("two", "Grow 2").width(Sizing.grow(2)));
    }

    /**
     * A row of two buttons with a justification.
     *
     * @param id The row ID.
     * @param justify The justification.
     * @return The row.
     */
    private static Row justifyRow(String id, Justify justify) {
        return new Row(id).gap(8).justify(justify).add(new Button("a", "A"), new Button("b", "B"));
    }

    /**
     * Four columns, each with a different alignment for its button.
     *
     * @return The row of columns.
     */
    private static Row alignRow() {
        Row row = new Row("align").gap(8);
        for (Align align : Align.values()) {
            row.add(
                    new Column(align.name())
                            .width(Sizing.grow())
                            .align(align)
                            .add(new Button("button", align.name().toLowerCase())));
        }
        return row;
    }

    /**
     * An overlay with a label in each corner and the center.
     *
     * @return The overlay.
     */
    private static Overlay overlay() {
        Length inset = Length.u(4);
        return new Overlay("overlay")
                .classes("panel")
                .height(Sizing.fixed(90))
                .add(
                        new Label("top-left", "top left")
                                .anchors(Anchors.topLeft().offset(inset, inset)),
                        new Label("top-right", "top right")
                                .anchors(Anchors.topRight().offset(Length.u(-4), inset)),
                        new Label("center", "center").anchors(Anchors.center()),
                        new Label("bottom-left", "bottom left")
                                .anchors(Anchors.bottomLeft().offset(inset, Length.u(-4))),
                        new Label("bottom-right", "bottom right")
                                .anchors(Anchors.bottomRight().offset(Length.u(-4), Length.u(-4))));
    }

    /**
     * A row of custom slots.
     *
     * @return The row.
     */
    private static Row slots() {
        Row row = new Row("slots").gap(8);
        for (int i = 0; i < 6; ++i) {
            row.add(new Slot("slot" + i));
        }
        return row;
    }

    /** Static builders only. */
    private UiShowcase() {}
}
