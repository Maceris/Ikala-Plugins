package com.ikalagaming.graphics.ui.spec;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.ui.Align;
import com.ikalagaming.graphics.ui.Button;
import com.ikalagaming.graphics.ui.Column;
import com.ikalagaming.graphics.ui.Flex;
import com.ikalagaming.graphics.ui.Justify;
import com.ikalagaming.graphics.ui.Label;
import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Overlay;
import com.ikalagaming.graphics.ui.Row;
import com.ikalagaming.graphics.ui.Scroll;
import com.ikalagaming.graphics.ui.Selectable;
import com.ikalagaming.graphics.ui.TextInput;
import com.ikalagaming.graphics.ui.style.StyleKey;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The node types specs can use, by name. Graphics registers the built-in types; plugins add their
 * own, which are removed when the plugin unloads. Safe from any thread.
 */
@Slf4j
public final class NodeTypes {

    /**
     * A registered type.
     *
     * @param factory Makes nodes of the type.
     * @param owner The plugin context that registered it, or null for built-in types.
     */
    record Entry(@NonNull NodeFactory factory, GraphicsContext owner) {}

    /** Types by name. */
    private final Map<String, Entry> types = new ConcurrentHashMap<>();

    /** Create a registry with the built-in types. */
    public NodeTypes() {
        builtIn("column", (id, p) -> flex(new Column(id), p));
        builtIn("row", (id, p) -> flex(new Row(id), p));
        builtIn("overlay", (id, p) -> new Overlay(id));
        builtIn("scroll", (id, p) -> new Scroll(id).border(p.bool("border", false)));
        builtIn(
                "label",
                (id, p) -> {
                    Label label = new Label(id, "");
                    p.text("text", "", label::text);
                    return label;
                });
        builtIn(
                "button",
                (id, p) -> {
                    Button button = new Button(id, "");
                    p.text("text", "", button::text);
                    button.onClick(p.handler("onClick", button));
                    button.autofocus(p.bool("autofocus", false));
                    return button;
                });
        builtIn(
                "selectable",
                (id, p) -> {
                    Selectable selectable = new Selectable(id, "");
                    p.text("text", "", selectable::text);
                    p.flag("selected", false, selectable::selected);
                    selectable.onClick(p.handler("onClick", selectable));
                    return selectable;
                });
        builtIn(
                "text-input",
                (id, p) -> {
                    TextInput input = new TextInput(id, "");
                    p.text("text", "", input::text);
                    input.onSubmit(p.valueHandler("onSubmit", input));
                    return input;
                });
    }

    /**
     * Read the properties every row and column has.
     *
     * @param flex The row or column.
     * @param properties Its properties.
     * @return The row or column.
     */
    private static Flex<?> flex(Flex<?> flex, SpecProperties properties) {
        Length gap = properties.length("gap", StyleKey.GAP);
        if (gap != null) {
            flex.gap(gap);
        }
        flex.align(properties.choice("align", Align.class, Align.START));
        flex.justify(properties.choice("justify", Justify.class, Justify.START));
        return flex;
    }

    /**
     * Register a built-in type.
     *
     * @param name The type name.
     * @param factory Makes nodes of the type.
     */
    private void builtIn(String name, NodeFactory factory) {
        types.put(name, new Entry(factory, null));
    }

    /**
     * Register a plugin's node type.
     *
     * @param owner The plugin's context, which owns the type until it unloads.
     * @param name The type name. Prefix it with the plugin, like {@code converter.slot}, so it
     *     doesn't clash.
     * @param factory Makes nodes of the type.
     * @throws IllegalArgumentException If the name is taken by a built-in type or another plugin.
     */
    public void register(
            @NonNull GraphicsContext owner, @NonNull String name, @NonNull NodeFactory factory) {
        types.compute(
                name,
                (key, existing) -> {
                    if (existing != null
                            && (existing.owner() == null
                                    || !existing.owner().getOwner().equals(owner.getOwner()))) {
                        throw new IllegalArgumentException(
                                "The node type " + name + " is already registered");
                    }
                    // A reloaded plugin may register again before its old instance unloads
                    return new Entry(factory, owner);
                });
    }

    /**
     * Find a type.
     *
     * @param name The type name.
     * @return The type, or null if there is none with that name.
     */
    Entry get(@NonNull String name) {
        return types.get(name);
    }

    /**
     * The names of every type, for messages.
     *
     * @return The names, sorted.
     */
    public Set<String> names() {
        return new TreeSet<>(types.keySet());
    }

    /**
     * Remove every type a plugin registered. Called when it unloads.
     *
     * @param owner The plugin's context.
     * @return The names removed.
     */
    public Set<String> removeAllOwnedBy(@NonNull GraphicsContext owner) {
        Set<String> removed = new HashSet<>();
        types.entrySet()
                .removeIf(
                        entry -> {
                            if (entry.getValue().owner() == owner) {
                                removed.add(entry.getKey());
                                return true;
                            }
                            return false;
                        });
        if (!removed.isEmpty()) {
            log.debug("Removed node types {} owned by {}", removed, owner.getOwnerKey());
        }
        return removed;
    }
}
