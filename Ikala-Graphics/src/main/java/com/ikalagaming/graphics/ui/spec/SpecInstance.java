package com.ikalagaming.graphics.ui.spec;

import static com.ikalagaming.graphics.ui.spec.SpecKeys.*;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.ui.Container;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.Surface;
import com.ikalagaming.graphics.ui.UiManager;
import com.ikalagaming.graphics.ui.VirtualGrid;
import com.ikalagaming.graphics.ui.style.Style;
import com.ikalagaming.graphics.ui.style.StyleParser;
import com.ikalagaming.graphics.ui.style.ThemeException;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.scripting.ScriptLaunch;
import com.ikalagaming.scripting.ScriptManager;
import com.ikalagaming.scripting.interpreter.ScriptRuntime;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;

/**
 * A spec that has been opened: its surface, its nodes, and the listeners keeping them up to date.
 * Owned by the plugin that opened it; closed when it is closed, its surface is removed, or the
 * plugin unloads. Closing detaches every listener, so no observable keeps it alive, and drops the
 * handlers and bindings, so it keeps no plugin objects alive either.
 */
@Slf4j
public final class SpecInstance {
    /** The manager it is shown through. */
    private final UiManager manager;

    /**
     * The plugin context that opened it.
     *
     * @return The owner.
     */
    @Getter private final GraphicsContext owner;

    /** The node types it is built from. */
    private final NodeTypes types;

    /**
     * The spec it was built from, which changes when it is hot reloaded.
     *
     * @return The spec.
     */
    @Getter private volatile UiSpec spec;

    /**
     * The handlers, observables and bundle, or null once closed. An immutable snapshot taken when
     * it was opened, so the plugin changing its bindings afterwards can't race with the render
     * thread.
     */
    private volatile SpecBindings bindings;

    /** What the current build made, or null once closed. */
    private volatile Build build;

    /** Whether it is closed. Updates and handlers queued after this are skipped. */
    private volatile boolean closed;

    /** Whether its build has been torn down. Render thread only. */
    private boolean released;

    /**
     * Item accessors by class and field, cached here rather than statically so plugin classes go
     * with it.
     */
    private final Map<Class<?>, Map<String, Function<Object, Object>>> accessors = new HashMap<>();

    /**
     * Everything one build of the spec made.
     *
     * @param surface The surface.
     * @param root The root node.
     * @param subscriptions Every listener on an observable.
     * @param repeats The repeats, which hold their items' listeners.
     * @param typeOwners The plugins whose node types were used.
     */
    record Build(
            Surface surface,
            Node<?> root,
            List<Subscription> subscriptions,
            List<Listening> repeats,
            Set<GraphicsContext> typeOwners) {}

    /**
     * Create and build an instance. It isn't shown until the manager adds it.
     *
     * @param manager The manager it is shown through.
     * @param owner The plugin context opening it.
     * @param types The node types.
     * @param spec The spec.
     * @param bindings The handlers, observables and bundle.
     * @throws SpecException If the spec can't be built with these bindings.
     */
    SpecInstance(
            @NonNull UiManager manager,
            @NonNull GraphicsContext owner,
            @NonNull NodeTypes types,
            @NonNull UiSpec spec,
            @NonNull SpecBindings bindings) {
        this.manager = manager;
        this.owner = owner;
        this.types = types;
        this.bindings = bindings.snapshot();
        this.spec = spec;
        this.build = build(spec);
    }

    /**
     * Open a spec: build it, then show it from the next frame. Called through {@code UI.open}.
     *
     * @param manager The manager to show it through.
     * @param owner The plugin context opening it.
     * @param spec The spec.
     * @param bindings The handlers, observables and bundle.
     * @return The instance.
     * @throws SpecException If the spec can't be built with these bindings; nothing is shown then.
     */
    public static SpecInstance open(
            @NonNull UiManager manager,
            @NonNull GraphicsContext owner,
            @NonNull UiSpec spec,
            @NonNull SpecBindings bindings) {
        SpecInstance instance =
                new SpecInstance(manager, owner, manager.getNodeTypes(), spec, bindings);
        manager.post(() -> manager.addSpec(instance));
        return instance;
    }

    /**
     * The surface currently shown.
     *
     * @return The surface, or null once closed.
     */
    public Surface getSurface() {
        Build current = build;
        return current == null ? null : current.surface();
    }

    /**
     * Whether it has been closed.
     *
     * @return True once closed.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Whether any of its nodes came from a plugin's node type.
     *
     * @param plugin The plugin context.
     * @return True if a node type that plugin registered was used.
     */
    public boolean usesTypesFrom(@NonNull GraphicsContext plugin) {
        Build current = build;
        return current != null && current.typeOwners().contains(plugin);
    }

    /**
     * Find a node by its ID path from the root, like {@code buttons/start}. Render thread only once
     * shown.
     *
     * @param path The IDs, separated by slashes, not including the root's ID.
     * @return The node, or null if there is none.
     */
    public Node<?> find(@NonNull String path) {
        Build current = build;
        if (current == null) {
            return null;
        }
        Node<?> node = current.root();
        if (path.isEmpty()) {
            return node;
        }
        for (String id : path.split("/")) {
            if (!(node instanceof Container<?> container)) {
                return null;
            }
            node = container.find(id);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    /**
     * Close it. Safe from any thread; closing again does nothing. From now on its pending updates
     * and handlers are skipped. On the next frame its surface is removed and it stops listening to
     * every observable; that part runs on the render thread, which owns its nodes and repeats.
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        manager.post(
                () -> {
                    Build closing = detachBuild();
                    manager.removeSpec(this, closing == null ? null : closing.surface());
                });
    }

    /**
     * Mark it closed and drop everything it holds, without touching the manager. For the manager,
     * when it is already removing the instance, like when its plugin unloads. Render thread only.
     */
    public void detach() {
        detachBuild();
    }

    /**
     * Mark it closed and drop everything it holds. Render thread only.
     *
     * @return What it had built, or null if it was already torn down.
     */
    private Build detachBuild() {
        closed = true;
        if (released) {
            return null;
        }
        released = true;
        Build closing = build;
        build = null;
        bindings = null;
        accessors.clear();
        if (closing != null) {
            release(closing);
        }
        return closing;
    }

    /**
     * Stop every listener a build made.
     *
     * @param old The build.
     */
    private static void release(Build old) {
        old.subscriptions().forEach(Subscription::close);
        old.repeats().forEach(Listening::close);
    }

    /**
     * Rebuild from a changed spec, keeping the bindings, and show the new surface in place of the
     * old. Render thread only. If the new spec is broken, nothing changes.
     *
     * @param changed The new spec.
     * @throws SpecException If it can't be built.
     */
    public void reload(@NonNull UiSpec changed) {
        if (closed) {
            return;
        }
        Build next = build(changed);
        Build old = build;
        spec = changed;
        build = next;
        release(old);
        manager.addSpecSurface(this, old.surface(), next.surface());
    }

    /**
     * Whether a surface is the one this instance shows now, for the manager.
     *
     * @param surface The surface.
     * @return True if it is.
     */
    public boolean shows(@NonNull Surface surface) {
        Build current = build;
        return current != null && current.surface() == surface;
    }

    /**
     * Build the surface and nodes for a spec.
     *
     * @param from The spec.
     * @return What was built.
     */
    private Build build(UiSpec from) {
        List<Subscription> subscriptions = new ArrayList<>();
        List<Listening> repeats = new ArrayList<>();
        Set<GraphicsContext> typeOwners = new HashSet<>();
        Build[] holder = new Build[1];
        Scope scope = new Scope(this, holder, Map.of(), null, subscriptions, repeats, typeOwners);
        Node<?> root;
        try {
            root = node(from.content(), scope);
        } catch (RuntimeException e) {
            subscriptions.forEach(Subscription::close);
            repeats.forEach(Listening::close);
            throw e;
        }

        UiSpec.SurfaceSpec settings = from.surface();
        Surface surface =
                new Surface(settings.id())
                        .anchors(settings.anchors())
                        .width(settings.width())
                        .height(settings.height())
                        .layer(settings.layer())
                        .classes(settings.classes().toArray(String[]::new))
                        .style(settings.style())
                        .content(root);
        if (settings.movable()) {
            surface.movable();
        }
        if (settings.transparent()) {
            surface.transparent();
        }
        Build made = new Build(surface, root, subscriptions, repeats, typeOwners);
        holder[0] = made;
        return made;
    }

    /**
     * Build one node and everything below it.
     *
     * @param spec The node.
     * @param scope Where bindings are looked up.
     * @return The node.
     */
    private Node<?> node(NodeSpec spec, Scope scope) {
        NodeTypes.Entry type = types.get(spec.type());
        if (type == null) {
            throw new SpecException(
                    spec.path()
                            + ": unknown node type '"
                            + spec.type()
                            + "', known: "
                            + types.names());
        }
        if (type.owner() != null) {
            scope.typeOwners().add(type.owner());
        }
        SpecProperties properties = new SpecProperties(spec, scope);
        Node<?> node = type.factory().create(spec.id(), properties);
        common(node, properties);
        properties.checkAllUsed();

        if (node instanceof VirtualGrid grid) {
            if (!spec.children().isEmpty()) {
                throw new SpecException(
                        spec.path()
                                + ": a virtual grid makes its cells from a repeat, not children");
            }
            if (spec.repeat() != null) {
                VirtualRepeat cells = new VirtualRepeat(grid, spec.repeat(), scope);
                scope.repeats().add(cells);
                cells.start();
            }
            return node;
        }
        if (!spec.children().isEmpty() || spec.repeat() != null) {
            if (!(node instanceof Container<?> container)) {
                throw new SpecException(
                        spec.path() + ": a " + spec.type() + " can't have children");
            }
            for (NodeSpec child : spec.children()) {
                add(container, node(child, scope), child.path());
            }
            if (spec.repeat() != null) {
                Repeat repeat = new Repeat(container, spec.repeat(), scope);
                scope.repeats().add(repeat);
                repeat.start();
            }
        }
        return node;
    }

    /**
     * Add a child, turning a refusal into a spec problem.
     *
     * @param container The parent.
     * @param child The child.
     * @param path Where the child is, for messages.
     */
    private static void add(Container<?> container, Node<?> child, String path) {
        try {
            container.add(child);
        } catch (IllegalArgumentException e) {
            throw new SpecException(path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Apply the properties every node has.
     *
     * @param node The node.
     * @param properties Its properties.
     */
    private static void common(Node<?> node, SpecProperties properties) {
        String path = properties.path();
        if (properties.has(WIDTH)) {
            node.width(SpecValues.sizing(properties.raw(WIDTH), at(path, WIDTH)));
        }
        if (properties.has(HEIGHT)) {
            node.height(SpecValues.sizing(properties.raw(HEIGHT), at(path, HEIGHT)));
        }
        if (properties.has(ANCHORS)) {
            node.anchors(SpecValues.anchors(properties.raw(ANCHORS), at(path, ANCHORS)));
        }
        if (properties.has(PADDING)) {
            Object padding = properties.raw(PADDING);
            if (StyleParser.isToken(padding)) {
                properties
                        .getTokens()
                        .put(
                                com.ikalagaming.graphics.ui.style.StyleKey.PADDING,
                                new com.ikalagaming.graphics.ui.style.Token(
                                        String.valueOf(padding).substring(1)));
            } else {
                node.padding(SpecValues.insets(padding, at(path, PADDING)));
            }
        }
        if (properties.has(FONT_SIZE)) {
            Object size = properties.raw(FONT_SIZE);
            if (StyleParser.isToken(size)) {
                properties
                        .getTokens()
                        .put(
                                com.ikalagaming.graphics.ui.style.StyleKey.FONT_SIZE,
                                new com.ikalagaming.graphics.ui.style.Token(
                                        String.valueOf(size).substring(1)));
            } else {
                node.fontSize(SpecValues.number(size, at(path, FONT_SIZE)));
            }
        }
        if (properties.has(CLASSES)) {
            node.classes(
                    SpecValues.names(properties.raw(CLASSES), at(path, CLASSES))
                            .toArray(String[]::new));
        }
        properties.flag(VISIBLE, true, node::visible);
        if (properties.has(POSITION)) {
            float[] position = SpecValues.numbers(properties.raw(POSITION), 2, at(path, POSITION));
            node.position(position[0], position[1]);
        }

        Style style = Style.EMPTY;
        if (properties.has(STYLE)) {
            try {
                style = StyleParser.style(properties.raw(STYLE), at(path, STYLE), null);
            } catch (ThemeException e) {
                throw new SpecException(e.getMessage(), e);
            }
        }
        if (!properties.getTokens().isEmpty()) {
            Style.Builder tokens = Style.builder();
            properties.getTokens().forEach(tokens::set);
            // Tokens given as properties win over the style map, like explicit setters do
            style = style.with(tokens.build());
        }
        if (!style.isEmpty()) {
            node.style(style);
        }
    }

    /**
     * Read a field of a list item: a map key, a record component, or a getter.
     *
     * @param item The item.
     * @param field The field name.
     * @return The value, or null if the item has no such field.
     */
    private Object field(Object item, String field) {
        if (item == null) {
            return null;
        }
        if (item instanceof Map<?, ?> map) {
            return map.get(field);
        }
        Function<Object, Object> accessor =
                accessors
                        .computeIfAbsent(item.getClass(), c -> new HashMap<>())
                        .computeIfAbsent(field, f -> accessorFor(item.getClass(), f));
        return accessor.apply(item);
    }

    /**
     * Read a field of an object without caching, for values handed out of the spec, like a script's
     * event: a map key, a record component, or a getter.
     *
     * @param item The object.
     * @param field The field name.
     * @return The value, or null if there is no such field.
     */
    static Object readField(@NonNull Object item, @NonNull String field) {
        if (item instanceof Map<?, ?> map) {
            return map.get(field);
        }
        return accessorFor(item.getClass(), field).apply(item);
    }

    /**
     * Find how to read a field of a class.
     *
     * @param type The class.
     * @param field The field name.
     * @return Reads the field, or gives null if there is no such field.
     */
    private static Function<Object, Object> accessorFor(Class<?> type, String field) {
        Method method = null;
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                if (component.getName().equals(field)) {
                    method = component.getAccessor();
                }
            }
        }
        String capitalized = Character.toUpperCase(field.charAt(0)) + field.substring(1);
        for (String name : List.of("get" + capitalized, "is" + capitalized, field)) {
            if (method != null) {
                break;
            }
            try {
                method = type.getMethod(name);
            } catch (NoSuchMethodException e) {
                // Try the next name
            }
        }
        if (method == null) {
            return item -> null;
        }
        Method found = method;
        found.setAccessible(true);
        return item -> {
            try {
                return found.invoke(item);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        };
    }

    /**
     * Where a node's bindings are looked up, and where its listeners are kept.
     *
     * @param instance The instance being built.
     * @param build Holds the build these nodes belong to, once it is finished.
     * @param items Repeat items in scope, by alias.
     * @param item The innermost repeat item, or null.
     * @param subscriptions Where listeners are kept.
     * @param repeats Where repeats are kept.
     * @param typeOwners Collects the plugins whose node types were used.
     */
    record Scope(
            SpecInstance instance,
            Build[] build,
            Map<String, Object> items,
            Object item,
            List<Subscription> subscriptions,
            List<Listening> repeats,
            Set<GraphicsContext> typeOwners) {

        /**
         * Look up a binding.
         *
         * @param name The binding name.
         * @return The value.
         */
        Object lookup(String name) {
            return instance.lookup(this, name);
        }

        /**
         * Fill in a text's bindings.
         *
         * @param template The text.
         * @return The filled in text.
         */
        String render(TextTemplate template) {
            return template.render(this::lookup);
        }

        /**
         * Apply a binding now and after changes.
         *
         * @param template The text with bindings.
         * @param where Where it is, for messages.
         * @param apply Applies the current value.
         */
        void bind(TextTemplate template, String where, Runnable apply) {
            instance.bind(this, template, where, apply);
        }

        /**
         * Find a named handler.
         *
         * @param name The handler name.
         * @param where Where it is, for messages.
         * @param node The node the event happens to.
         * @return Runs the handler.
         */
        Consumer<String> handler(String name, String where, Node<?> node) {
            return instance.handler(this, name, where, node);
        }

        /**
         * Find a named handler for events that can happen to different nodes.
         *
         * @param name The handler name.
         * @param where Where it is, for messages.
         * @return Runs the handler with the node and the event's value.
         */
        BiConsumer<Node<?>, String> nodeHandler(String name, String where) {
            return instance.nodeHandler(this, name, where);
        }

        /**
         * The resource bundle for {@code @KEY} text.
         *
         * @return The bundle, or null.
         */
        ResourceBundle bundle() {
            return instance.bundle();
        }
    }

    /**
     * A scope for an item of a repeat.
     *
     * @param parent The scope of the repeating container.
     * @param alias The item's name.
     * @param value The item.
     * @param subscriptions Where the item's listeners are kept.
     * @return The scope.
     */
    private static Scope itemScope(
            Scope parent, String alias, Object value, List<Subscription> subscriptions) {
        Map<String, Object> items = new HashMap<>(parent.items());
        items.put(alias, value);
        return new Scope(
                parent.instance(),
                parent.build(),
                items,
                value,
                subscriptions,
                parent.repeats(),
                parent.typeOwners());
    }

    /**
     * Look up a binding: a repeat item or one of its fields, or an observable.
     *
     * @param scope The scope.
     * @param name The binding name, like {@code file.name} or {@code player.health}.
     * @return The value.
     */
    Object lookup(Scope scope, String name) {
        int dot = name.indexOf('.');
        String head = dot < 0 ? name : name.substring(0, dot);
        if (scope.items().containsKey(head)) {
            Object value = scope.items().get(head);
            if (dot >= 0) {
                for (String part : name.substring(dot + 1).split("\\.")) {
                    value = field(value, part);
                }
            }
            return value;
        }
        SpecBindings current = bindings;
        if (current == null) {
            return null;
        }
        int split = observableSplit(current, name);
        if (split < 0) {
            return null;
        }
        Object value = current.value(name.substring(0, split)).get();
        if (split < name.length()) {
            for (String part : name.substring(split + 1).split("\\.")) {
                value = field(value, part);
            }
        }
        return value;
    }

    /**
     * Find which part of a binding name is an observable: the whole name, or the longest leading
     * part before a dot, with the rest naming fields of its value, like {@code player.stats.hp} for
     * an observable {@code player} or {@code player.stats}.
     *
     * @param from The bindings.
     * @param name The binding name.
     * @return The length of the observable's name, or -1 if no part of it is an observable.
     */
    private static int observableSplit(SpecBindings from, String name) {
        for (int end = name.length(); end > 0; end = name.lastIndexOf('.', end - 1)) {
            if (from.value(name.substring(0, end)) != null) {
                return end;
            }
        }
        return -1;
    }

    /**
     * Apply a binding now, and again on the render thread whenever an observable it uses changes.
     *
     * @param scope The scope.
     * @param template The text with bindings.
     * @param where Where it is, for messages.
     * @param apply Applies the current value to the node.
     * @throws SpecException If a binding names an observable that wasn't supplied.
     */
    void bind(Scope scope, TextTemplate template, String where, Runnable apply) {
        apply.run();
        for (TextTemplate.Part part : template.parts()) {
            if (!part.binding()) {
                continue;
            }
            String head =
                    part.text().contains(".")
                            ? part.text().substring(0, part.text().indexOf('.'))
                            : part.text();
            if (scope.items().containsKey(head)) {
                // Item fields don't change while the item's node exists
                continue;
            }
            int split = observableSplit(bindings, part.text());
            Observable<?> observable =
                    split < 0 ? null : bindings.value(part.text().substring(0, split));
            if (observable == null) {
                throw new SpecException(
                        where + ": no observable '" + part.text() + "' in the bindings");
            }
            Build[] holder = scope.build();
            scope.subscriptions()
                    .add(
                            observable.subscribe(
                                    value ->
                                            manager.post(
                                                    () -> {
                                                        // Skip updates for a closed or replaced
                                                        // build
                                                        if (!closed && holder[0] == build) {
                                                            apply.run();
                                                        }
                                                    })));
        }
    }

    /**
     * Find a named handler.
     *
     * @param scope The scope, for the repeat item.
     * @param name The handler name.
     * @param where Where it is, for messages.
     * @param node The node the event happens to.
     * @return Runs the handler with an event's value.
     * @throws SpecException If no such handler was supplied.
     */
    Consumer<String> handler(Scope scope, String name, String where, Node<?> node) {
        BiConsumer<Node<?>, String> handler = nodeHandler(scope, name, where);
        return value -> handler.accept(node, value);
    }

    /**
     * Find a named handler for events that can happen to different nodes, like a child of a canvas
     * being moved.
     *
     * @param scope The scope, for the repeat item.
     * @param name The handler name.
     * @param where Where it is, for messages.
     * @return Runs the handler with the node the event happened to and the event's value.
     * @throws SpecException If no such handler was supplied.
     */
    BiConsumer<Node<?>, String> nodeHandler(Scope scope, String name, String where) {
        SpecAction action = SpecAction.parse(name, where);
        if (action instanceof SpecAction.Resume resume) {
            Consumer<String> run = resumeAction(scope, resume, where);
            return (node, value) -> run.accept(value);
        }
        if (action instanceof SpecAction.RunScript script) {
            Consumer<String> run = scriptAction(scope, script, where);
            return (node, value) -> run.accept(value);
        }
        Consumer<SpecEvent> handler = bindings.handler(name);
        if (handler == null) {
            throw new SpecException(where + ": no handler '" + name + "' in the bindings");
        }
        Object item = scope.item();
        return (node, value) -> {
            if (!closed) {
                handler.accept(new SpecEvent(node, value, item));
            }
        };
    }

    /**
     * Make a {@code resume(tag, value)} action, which resumes the script that opened the spec.
     *
     * @param scope The scope, for bindings in the value.
     * @param action The action.
     * @param where Where it is, for messages.
     * @return Runs the action with an event's value.
     * @throws SpecException If no script opened the spec.
     */
    private Consumer<String> resumeAction(Scope scope, SpecAction.Resume action, String where) {
        ScriptRuntime opener = bindings.script();
        if (opener == null) {
            throw new SpecException(
                    where + ": " + RESUME + "(...) needs a spec opened by a script, to resume");
        }
        String written = action.value();
        Matcher binding = written == null ? null : SpecAction.BINDING.matcher(written);
        Object literal = written == null || binding.matches() ? null : SpecAction.literal(written);
        String bound = binding != null && binding.matches() ? binding.group(1).trim() : null;
        return value -> {
            if (closed) {
                return;
            }
            Object resumed;
            if (written == null) {
                resumed = value;
            } else if (bound != null) {
                resumed = lookup(scope, bound);
            } else {
                resumed = literal;
            }
            ScriptManager.resume(opener, action.tag(), resumed);
        };
    }

    /**
     * Make a {@code script(file#label)} action, which starts a script owned by the spec's plugin,
     * with the event as the global {@code event}.
     *
     * @param scope The scope, for the repeat item.
     * @param action The action.
     * @param where Where it is, for messages.
     * @return Runs the action with an event's value.
     * @throws SpecException If the script file doesn't exist.
     */
    private Consumer<String> scriptAction(Scope scope, SpecAction.RunScript action, String where) {
        Path folder = bindings.scriptFolder();
        if (folder == null) {
            folder = PluginFolder.getResource(owner.getOwner(), ResourceType.SCRIPTS, "").toPath();
        }
        folder = folder.toAbsolutePath().normalize();
        Path file = folder.resolve(action.file()).normalize();
        if (!file.startsWith(folder) || !Files.isRegularFile(file)) {
            throw new SpecException(
                    where + ": no script '" + action.file() + "' in the plugin's scripts folder");
        }
        String plugin = owner.getOwner();
        Object item = scope.item();
        return value -> {
            if (closed) {
                return;
            }
            ScriptManager.start(
                    ScriptLaunch.file(file)
                            .owner(plugin)
                            .at(action.label())
                            .global(EVENT, new ScriptEvent(value, item)));
        };
    }

    /**
     * The resource bundle for {@code @KEY} text.
     *
     * @return The bundle, or null.
     */
    ResourceBundle bundle() {
        SpecBindings current = bindings;
        return current == null ? null : current.bundle();
    }

    /**
     * Fills a container with one node per item of an observable list, keeping the nodes of items
     * that are still there when the list changes.
     */
    /** Something built from a spec that listens to observables, and stops when closed. */
    interface Listening {
        /** Stop listening and drop what it made. */
        void close();
    }

    /**
     * Makes a virtual grid's cells from an observable list, one per item, only while they are
     * shown. Each cell's listeners are closed when the grid releases it.
     */
    final class VirtualRepeat implements Listening, VirtualGrid.Factory {
        /** The grid to fill. */
        private final VirtualGrid grid;

        /** What to repeat. */
        private final NodeSpec.RepeatSpec spec;

        /** The grid's scope. */
        private final Scope scope;

        /** The list. */
        private final ObservableList<?> list;

        /** The items the cells are made from now. */
        private List<?> items = List.of();

        /** The listeners of each made cell, by index. */
        private final Map<Integer, List<Subscription>> cells = new HashMap<>();

        /** Listens to the list. */
        private Subscription subscription;

        /**
         * Create the cells of a virtual grid.
         *
         * @param grid The grid to fill.
         * @param spec What to repeat.
         * @param scope The grid's scope.
         * @throws SpecException If the list wasn't supplied.
         */
        VirtualRepeat(VirtualGrid grid, NodeSpec.RepeatSpec spec, Scope scope) {
            this.grid = grid;
            this.spec = spec;
            this.scope = scope;
            list = bindings.list(spec.list());
            if (list == null) {
                throw new SpecException(
                        spec.path() + ".list: no list '" + spec.list() + "' in the bindings");
            }
        }

        /** Show the list now, and follow its changes. */
        void start() {
            items = list.get();
            grid.items(items.size(), this);
            Build[] holder = scope.build();
            subscription =
                    list.subscribe(
                            changed ->
                                    manager.post(
                                            () -> {
                                                if (!closed && holder[0] == build) {
                                                    // Cells show items by index, so they are all
                                                    // made again
                                                    items = changed;
                                                    grid.count(changed.size());
                                                    grid.refresh();
                                                }
                                            }));
        }

        @Override
        public Node<?> create(int index) {
            if (closed || index >= items.size()) {
                return null;
            }
            Object item = items.get(index);
            List<Subscription> listeners = new ArrayList<>();
            NodeSpec template = spec.item();
            NodeSpec indexed =
                    new NodeSpec(
                            template.type(),
                            template.id() + "#" + index,
                            template.properties(),
                            template.children(),
                            template.repeat(),
                            template.path());
            Node<?> node = node(indexed, itemScope(scope, spec.alias(), item, listeners));
            cells.put(index, listeners);
            return node;
        }

        @Override
        public void release(int index, @NonNull Node<?> node) {
            List<Subscription> listeners = cells.remove(index);
            if (listeners != null) {
                listeners.forEach(Subscription::close);
            }
        }

        @Override
        public void close() {
            if (subscription != null) {
                subscription.close();
            }
            grid.releaseAll();
            cells.values().forEach(listeners -> listeners.forEach(Subscription::close));
            cells.clear();
            items = List.of();
        }
    }

    final class Repeat implements Listening {
        /**
         * One item's node.
         *
         * @param item The item.
         * @param node Its node.
         * @param subscriptions Its listeners.
         */
        private record Made(Object item, Node<?> node, List<Subscription> subscriptions) {}

        /** The container to fill. */
        private final Container<?> container;

        /** What to repeat. */
        private final NodeSpec.RepeatSpec spec;

        /** The container's scope. */
        private final Scope scope;

        /** The key text, or null to use positions. */
        private final TextTemplate key;

        /** The list. */
        private final ObservableList<?> list;

        /** The nodes made, by key, in list order. */
        private Map<String, Made> made = new LinkedHashMap<>();

        /** Listens to the list. */
        private Subscription subscription;

        /** The nodes this repeat added, so the container's own children are left alone. */
        private Set<Node<?>> repeated = new HashSet<>();

        /**
         * Whether a child was added by this repeat.
         *
         * @param child The child.
         * @return True if it was.
         */
        private boolean isRepeated(Node<?> child) {
            return repeated.contains(child);
        }

        /**
         * Create a repeat.
         *
         * @param container The container to fill.
         * @param spec What to repeat.
         * @param scope The container's scope.
         * @throws SpecException If the list wasn't supplied.
         */
        Repeat(Container<?> container, NodeSpec.RepeatSpec spec, Scope scope) {
            this.container = container;
            this.spec = spec;
            this.scope = scope;
            key = spec.key() == null ? null : TextTemplate.parse(spec.key(), spec.path() + ".key");
            list = bindings.list(spec.list());
            if (list == null) {
                throw new SpecException(
                        spec.path() + ".list: no list '" + spec.list() + "' in the bindings");
            }
        }

        /** Fill the container now, and listen for changes. */
        void start() {
            update(list.get());
            Build[] holder = scope.build();
            subscription =
                    list.subscribe(
                            items ->
                                    manager.post(
                                            () -> {
                                                if (!closed && holder[0] == build) {
                                                    update(items);
                                                }
                                            }));
        }

        /**
         * Rebuild the children for new list contents.
         *
         * @param items The items.
         */
        void update(List<?> items) {
            Map<String, Made> next = new LinkedHashMap<>();
            for (int i = 0; i < items.size(); ++i) {
                Object item = items.get(i);
                String itemKey =
                        key == null
                                ? Integer.toString(i)
                                : key.render(
                                        name ->
                                                lookup(
                                                        itemScope(
                                                                scope,
                                                                spec.alias(),
                                                                item,
                                                                List.of()),
                                                        name));
                if (next.containsKey(itemKey)) {
                    // Two items with the same key still get their own nodes
                    itemKey = itemKey + "~" + i;
                }
                Made old = made.remove(itemKey);
                if (old != null && Objects.equals(old.item(), item)) {
                    next.put(itemKey, old);
                    continue;
                }
                if (old != null) {
                    old.subscriptions().forEach(Subscription::close);
                }
                List<Subscription> itemSubscriptions = new ArrayList<>();
                NodeSpec template = spec.item();
                NodeSpec keyed =
                        new NodeSpec(
                                template.type(),
                                template.id() + "#" + itemKey,
                                template.properties(),
                                template.children(),
                                template.repeat(),
                                template.path());
                Node<?> node = node(keyed, itemScope(scope, spec.alias(), item, itemSubscriptions));
                next.put(itemKey, new Made(item, node, itemSubscriptions));
            }
            made.values().forEach(gone -> gone.subscriptions().forEach(Subscription::close));
            // Only the repeated nodes are replaced; the container's own children stay first
            for (Node<?> child : List.copyOf(container.getChildren())) {
                if (isRepeated(child)) {
                    container.remove(child);
                }
            }
            next.values().forEach(entry -> container.add(entry.node()));
            made = next;
            repeated = new HashSet<>();
            next.values().forEach(entry -> repeated.add(entry.node()));
        }

        /** Stop listening and drop the items. */
        @Override
        public void close() {
            if (subscription != null) {
                subscription.close();
            }
            made.values().forEach(entry -> entry.subscriptions().forEach(Subscription::close));
            made = new LinkedHashMap<>();
            repeated = new HashSet<>();
        }
    }
}
