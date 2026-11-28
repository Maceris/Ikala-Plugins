package com.ikalagaming.factory.world.gen.data;

import com.ikalagaming.util.schema.Schema;
import com.ikalagaming.util.schema.Variants;

import lombok.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The code-defined types of one kind of world generation object, by the {@code type} name data
 * uses, each with the schema of its fields. Data can only configure and combine these; mods with
 * code can register more, and must unregister them when they unload. As {@link Variants}, it is the
 * set of types a typed field may be, for editors.
 *
 * @param <F> What builds an object of a type.
 */
public final class TypeRegistry<F> implements Variants {

    /** What the types are, like {@code density}. */
    private final String name;

    /** The factories by type name. */
    private final Map<String, F> factories = new ConcurrentHashMap<>();

    /** Each type's fields, besides {@code type}, by type name. */
    private final Map<String, Schema> schemas = new ConcurrentHashMap<>();

    /**
     * Make an empty registry.
     *
     * @param name What its types are, like {@code density}.
     */
    public TypeRegistry(@NonNull String name) {
        this.name = name;
    }

    /**
     * Add a type, with the schema of its fields besides {@code type}: unknown fields are warned
     * against it before the factory runs, and editors make forms from it.
     *
     * @param type The type name, as data writes it.
     * @param schema Its fields.
     * @param factory What builds it.
     * @return False if the name was already taken, in which case nothing changes.
     */
    public boolean register(@NonNull String type, @NonNull Schema schema, @NonNull F factory) {
        if (factories.putIfAbsent(type, factory) != null) {
            return false;
        }
        schemas.put(type, schema);
        return true;
    }

    /**
     * Add a type without a schema: none of its fields are checked or offered by editors.
     *
     * @param type The type name, as data writes it.
     * @param factory What builds it.
     * @return False if the name was already taken, in which case nothing changes.
     */
    public boolean register(@NonNull String type, @NonNull F factory) {
        return factories.putIfAbsent(type, factory) == null;
    }

    /**
     * Remove a type.
     *
     * @param type The type name.
     */
    public void unregister(@NonNull String type) {
        factories.remove(type);
        schemas.remove(type);
    }

    /**
     * The factory for a type.
     *
     * @param type The type name.
     * @return The factory, if the type is known.
     */
    public Optional<F> get(@NonNull String type) {
        return Optional.ofNullable(factories.get(type));
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Every type name, for messages.
     *
     * @return The names, sorted.
     */
    @Override
    public SortedSet<String> names() {
        return new TreeSet<>(factories.keySet());
    }

    @Override
    public Optional<Schema> schemaOf(@NonNull String type) {
        return Optional.ofNullable(schemas.get(type));
    }
}
