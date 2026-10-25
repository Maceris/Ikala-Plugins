package com.ikalagaming.characters.registry;

import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.Definition;
import com.ikalagaming.characters.definition.Kind;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.NonNull;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * The usable character definitions, with a dense numeric ID for each within its kind, for the
 * network. Numeric IDs follow ID order, so two registries with the same content number it the same,
 * which the content hash confirms.
 *
 * <p>Registries are immutable, so they can be read from any thread.
 *
 * @author Ches Burks
 */
public final class CharacterRegistry {
    /** A registry with nothing in it. */
    public static final CharacterRegistry EMPTY = new CharacterRegistry(List.of());

    /** Definitions of each kind, in ID order, so the index is the numeric ID. */
    private final Map<Kind, List<Definition>> byKind = new EnumMap<>(Kind.class);

    /** Numeric IDs of each kind, by ID. */
    private final Map<Kind, Map<Id, Integer>> numeric = new EnumMap<>(Kind.class);

    /** A hash of every definition's content, as hex. */
    private final String contentHash;

    /**
     * Create a registry.
     *
     * @param definitions The definitions, which must have unique IDs within each kind.
     */
    CharacterRegistry(@NonNull List<? extends Definition> definitions) {
        for (Kind kind : Kind.values()) {
            byKind.put(kind, new ArrayList<>());
            numeric.put(kind, new HashMap<>());
        }
        for (Definition definition : definitions) {
            byKind.get(Kind.of(definition.getClass())).add(definition);
        }
        MessageDigest digest = sha256();
        for (Kind kind : Kind.values()) {
            List<Definition> list = byKind.get(kind);
            list.sort(Comparator.comparing(Definition::id));
            byKind.put(kind, List.copyOf(list));
            Map<Id, Integer> ids = numeric.get(kind);
            for (int i = 0; i < list.size(); ++i) {
                if (ids.put(list.get(i).id(), i) != null) {
                    throw new IllegalArgumentException(
                            "Two " + kind.getFolder() + " definitions use " + list.get(i).id());
                }
                // Written values are canonical: fields in record order, defaults filled in
                String entry =
                        kind.getFolder() + "\n" + DataFormat.write(list.get(i)).toString() + "\n";
                digest.update(entry.getBytes(StandardCharsets.UTF_8));
            }
        }
        contentHash = HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Write numbers exactly, since how {@code Float.toString} rounds has changed between Java
     * versions, and the hash must match between a server and clients.
     *
     * @param json The JSON.
     * @return The same JSON with every number replaced by its exact decimal value.
     */
    private static JsonElement exactNumbers(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            Number number = json.getAsNumber();
            BigDecimal exact =
                    number instanceof Float || number instanceof Double
                            ? BigDecimal.valueOf(number.doubleValue())
                            : new BigDecimal(number.toString());
            return new JsonPrimitive(exact.toPlainString());
        }
        if (json.isJsonArray()) {
            JsonArray array = new JsonArray();
            json.getAsJsonArray().forEach(item -> array.add(exactNumbers(item)));
            return array;
        }
        if (json.isJsonObject()) {
            JsonObject object = new JsonObject();
            json.getAsJsonObject()
                    .entrySet()
                    .forEach(e -> object.add(e.getKey(), exactNumbers(e.getValue())));
            return object;
        }
        return json;
    }

    /**
     * Create a SHA-256 digest.
     *
     * @return The digest.
     */
    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to have SHA-256
            throw new IllegalStateException(e);
        }
    }

    /**
     * Find a definition.
     *
     * @param <T> The kind of definition.
     * @param type The kind of definition.
     * @param id Its ID.
     * @return The definition, or null if there is none.
     */
    public <T extends Definition> T get(@NonNull Class<T> type, @NonNull Id id) {
        Integer index = numeric.get(Kind.of(type)).get(id);
        return index == null ? null : type.cast(byKind.get(Kind.of(type)).get(index));
    }

    /**
     * Find a definition by its numeric ID.
     *
     * @param <T> The kind of definition.
     * @param type The kind of definition.
     * @param numericId Its numeric ID.
     * @return The definition, or null if the number is out of range.
     */
    public <T extends Definition> T get(@NonNull Class<T> type, int numericId) {
        List<Definition> list = byKind.get(Kind.of(type));
        return numericId < 0 || numericId >= list.size() ? null : type.cast(list.get(numericId));
    }

    /**
     * All the definitions of a kind.
     *
     * @param <T> The kind of definition.
     * @param type The kind of definition.
     * @return The definitions, in ID order, which is numeric ID order.
     */
    @SuppressWarnings("unchecked")
    public <T extends Definition> List<T> all(@NonNull Class<T> type) {
        return (List<T>) byKind.get(Kind.of(type));
    }

    /**
     * A definition's numeric ID.
     *
     * @param kind The kind of definition.
     * @param id Its ID.
     * @return The numeric ID, or -1 if there is no such definition.
     */
    public int numericId(@NonNull Kind kind, @NonNull Id id) {
        return numeric.get(kind).getOrDefault(id, -1);
    }

    /**
     * A hash of every definition's content. Two registries with the same hash have the same
     * definitions and numeric IDs.
     *
     * @return The SHA-256 hash, as lowercase hex.
     */
    public String getContentHash() {
        return contentHash;
    }

    /**
     * How many definitions there are of all kinds.
     *
     * @return The count.
     */
    public int size() {
        return byKind.values().stream().mapToInt(List::size).sum();
    }
}
