package com.ikalagaming.graphics.testplugin;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.CustomItem;
import com.ikalagaming.graphics.ui.ItemState;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.spec.Observable;
import com.ikalagaming.graphics.ui.spec.ObservableList;
import com.ikalagaming.graphics.ui.spec.SpecBindings;
import com.ikalagaming.graphics.ui.spec.SpecInstance;
import com.ikalagaming.graphics.ui.spec.SpecLoader;

import lombok.NonNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Stands in for a plugin in the unload test. It is loaded by its own class loader, so the test can
 * check that nothing in graphics keeps its classes alive after it unloads.
 */
public final class PluginSide {
    /** A node type of the plugin's own. */
    public static final class Slot extends CustomItem<Slot> {
        Slot(String id) {
            super(id);
            width(Sizing.fixed(20));
            height(Sizing.fixed(20));
        }

        @Override
        protected void draw(
                @NonNull DrawList drawList, @NonNull RectFloat bounds, @NonNull ItemState state) {
            drawList.addRectFilled(
                    bounds.getLeft(), bounds.getTop(), bounds.getRight(), bounds.getBottom(), -1);
        }
    }

    /**
     * A value class of the plugin's own.
     *
     * @param name A field bindings read.
     */
    public record Value(String name) {}

    /** The plugin keeps its observables, as a real plugin would. */
    private static Observable<Value> value;

    /** The plugin keeps its list too. */
    private static ObservableList<Value> list;

    /**
     * Register a node type and open a spec that uses it, the plugin's observable, a repeat over the
     * plugin's values, and a handler.
     *
     * @param manager The UI manager.
     * @param context The plugin's context.
     */
    public static void install(UiManager manager, GraphicsContext context) {
        manager.getNodeTypes().register(context, "testplugin.slot", (id, p) -> new Slot(id));
        value = Observable.of(new Value("first"));
        list = new ObservableList<>(List.of(new Value("a"), new Value("b")));
        String yaml =
                """
                surface: { id: plugin-ui, anchors: top-left }
                templates:
                  row: { node: { type: button, id: row, text: "{v.name}", onClick: pick } }
                content:
                  type: column
                  id: root
                  children:
                    - { type: testplugin.slot, id: slot }
                    - { type: label, id: value, text: "{value.name}" }
                  repeat: { list: items, as: v, template: row, key: "{v.name}" }
                """;
        SpecInstance.open(
                manager,
                context,
                SpecLoader.load(
                        new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "plugin"),
                new SpecBindings()
                        .value("value", value)
                        .list("items", list)
                        .handler("pick", event -> value.set((Value) event.item())));
    }

    /**
     * How many listeners graphics has on the plugin's observables.
     *
     * @return The total.
     */
    public static int listeners() {
        return value.listenerCount() + list.listenerCount();
    }

    /** Change the plugin's values, as it might after unloading. */
    public static void poke() {
        value.set(new Value("changed"));
        list.add(new Value("c"));
    }

    /** Static entry points only. */
    private PluginSide() {}
}
