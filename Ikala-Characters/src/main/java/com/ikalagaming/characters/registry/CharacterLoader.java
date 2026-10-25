package com.ikalagaming.characters.registry;

import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonPath;
import com.ikalagaming.characters.data.JsonText;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.data.Problem.Severity;
import com.ikalagaming.characters.data.Report;
import com.ikalagaming.characters.definition.Checks;
import com.ikalagaming.characters.definition.Definition;
import com.ikalagaming.characters.definition.Kind;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads character definitions from JSON folders into a {@link CharacterRegistry}, reporting every
 * problem with its file and line. A definition with errors is left out, along with anything that
 * needs it, and the rest still load.
 *
 * <p>Loading happens in stages: read every file, resolve {@value DataFormat#EXTENDS}, read each
 * definition into its record, then check the definitions against each other until no more drop out.
 *
 * @author Ches Burks
 */
public final class CharacterLoader {
    /**
     * What loading produced.
     *
     * @param registry The usable definitions.
     * @param problems Everything wrong, in the order found.
     */
    public record Result(@NonNull CharacterRegistry registry, @NonNull List<Problem> problems) {
        /**
         * Whether any definition was left out.
         *
         * @return Whether there are errors.
         */
        public boolean hasErrors() {
            return problems.stream().anyMatch(p -> p.severity() == Severity.ERROR);
        }
    }

    /**
     * Where a definition came from.
     *
     * @param file The file, as shown to people.
     * @param text The file's text, to find lines in.
     * @param prefix The definition's JSON path in the file, {@code $} or {@code $[2]}.
     */
    private record Origin(String file, String text, String prefix) {
        /**
         * The full path of something within the definition.
         *
         * @param path The path relative to the definition, starting with {@code $}.
         * @return The path in the file.
         */
        String full(String path) {
            return prefix + path.substring(JsonPath.ROOT.length());
        }
    }

    /** One definition on its way through the stages. */
    private static final class Entry {
        /** Its kind. */
        final Kind kind;

        /** Its ID. */
        final Id id;

        /** Its JSON as written. */
        final JsonObject json;

        /** Where it came from. */
        final Origin origin;

        /** Its JSON with its parents merged in, once resolved. */
        JsonObject resolved;

        /** Whether it is being resolved, to catch loops. */
        boolean resolving;

        /** The record, once read. */
        Definition definition;

        /** Whether it can't be used. */
        boolean failed;

        Entry(Kind kind, Id id, JsonObject json, Origin origin) {
            this.kind = kind;
            this.id = id;
            this.json = json;
            this.origin = origin;
        }
    }

    /** Everything wrong so far. */
    private final List<Problem> problems = new ArrayList<>();

    /** Every definition found, by kind and ID, in the order found. */
    private final Map<Kind, Map<Id, Entry>> entries = new EnumMap<>(Kind.class);

    private CharacterLoader() {
        for (Kind kind : Kind.values()) {
            entries.put(kind, new LinkedHashMap<>());
        }
    }

    /**
     * Load definitions from folders. Sources are read in order, so for a repeated ID the first one
     * is used.
     *
     * @param sources The folders.
     * @return The registry and the problems found.
     */
    public static Result load(@NonNull List<ContentSource> sources) {
        CharacterLoader loader = new CharacterLoader();
        for (ContentSource source : sources) {
            loader.readSource(source);
        }
        for (Map<Id, Entry> kind : loader.entries.values()) {
            for (Entry entry : kind.values()) {
                loader.resolve(entry);
                loader.readRecord(entry);
            }
        }
        List<Definition> usable = loader.check();
        return new Result(new CharacterRegistry(usable), List.copyOf(loader.problems));
    }

    /**
     * Record a problem with a definition.
     *
     * @param origin Where the definition came from.
     * @param severity How bad it is.
     * @param path Where, relative to the definition.
     * @param message What is wrong.
     */
    private void problem(Origin origin, Severity severity, String path, String message) {
        String full = origin.full(path);
        JsonText.Position position = JsonText.locate(origin.text(), full);
        problems.add(
                new Problem(
                        severity,
                        origin.file(),
                        position.line(),
                        position.column(),
                        full,
                        message));
    }

    /**
     * A report that records problems with one definition.
     *
     * @param origin Where the definition came from.
     * @param into Where the problems go.
     * @return The report.
     */
    private Report report(Origin origin, List<Runnable> into) {
        return new Report() {
            @Override
            public void error(@NonNull String path, @NonNull String message) {
                into.add(() -> problem(origin, Severity.ERROR, path, message));
            }

            @Override
            public void warning(@NonNull String path, @NonNull String message) {
                into.add(() -> problem(origin, Severity.WARNING, path, message));
            }
        };
    }

    /**
     * Read every file in a source.
     *
     * @param source The source.
     */
    private void readSource(ContentSource source) {
        for (Kind kind : Kind.values()) {
            Path folder = source.root().resolve(kind.getFolder());
            if (!Files.isDirectory(folder)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(folder)) {
                files =
                        walk.filter(Files::isRegularFile)
                                .filter(path -> path.toString().endsWith(".json"))
                                .sorted()
                                .toList();
            } catch (IOException e) {
                problems.add(
                        new Problem(
                                Severity.ERROR,
                                source.name() + "/" + kind.getFolder(),
                                0,
                                0,
                                "",
                                "Couldn't list the folder: " + e.getMessage()));
                continue;
            }
            for (Path file : files) {
                String name =
                        source.name()
                                + "/"
                                + source.root().relativize(file).toString().replace('\\', '/');
                readFile(source, kind, file, name);
            }
        }
    }

    /**
     * Read the definitions in one file.
     *
     * @param source The source it is in.
     * @param kind The kind of definitions it holds.
     * @param file The file.
     * @param name What to call it in messages.
     */
    private void readFile(ContentSource source, Kind kind, Path file, String name) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            problems.add(
                    new Problem(
                            Severity.ERROR, name, 0, 0, "", "Couldn't read: " + e.getMessage()));
            return;
        }
        JsonElement json =
                JsonText.parse(
                        text,
                        error ->
                                problems.add(
                                        new Problem(
                                                Severity.ERROR,
                                                name,
                                                error.position().line(),
                                                error.position().column(),
                                                error.path(),
                                                error.message())));
        if (json == null) {
            return;
        }
        if (json.isJsonObject()) {
            add(source, kind, json.getAsJsonObject(), new Origin(name, text, JsonPath.ROOT));
        } else if (json.isJsonArray()) {
            for (int i = 0; i < json.getAsJsonArray().size(); ++i) {
                JsonElement item = json.getAsJsonArray().get(i);
                Origin origin = new Origin(name, text, JsonPath.index(JsonPath.ROOT, i));
                if (item.isJsonObject()) {
                    add(source, kind, item.getAsJsonObject(), origin);
                } else {
                    problem(origin, Severity.ERROR, JsonPath.ROOT, "Expected a definition in { }");
                }
            }
        } else {
            problem(
                    new Origin(name, text, JsonPath.ROOT),
                    Severity.ERROR,
                    JsonPath.ROOT,
                    "Expected a definition in { } or a list of them in [ ]");
        }
    }

    /**
     * Add a definition found in a file, if its ID is usable.
     *
     * @param source The source it is in.
     * @param kind Its kind.
     * @param json Its JSON.
     * @param origin Where it is.
     */
    private void add(ContentSource source, Kind kind, JsonObject json, Origin origin) {
        JsonElement idJson = json.get(Definition.FIELD_ID);
        if (idJson == null
                || !idJson.isJsonPrimitive()
                || !idJson.getAsJsonPrimitive().isString()) {
            problem(
                    origin,
                    Severity.ERROR,
                    JsonPath.ROOT,
                    "Every definition needs an \"" + Definition.FIELD_ID + "\" string");
            return;
        }
        Id id;
        try {
            id = Id.parse(idJson.getAsString());
        } catch (IllegalArgumentException e) {
            problem(origin, Severity.ERROR, JsonPath.at(Definition.FIELD_ID), e.getMessage());
            return;
        }
        if (!id.namespace().equals(source.namespace())) {
            problem(
                    origin,
                    Severity.ERROR,
                    JsonPath.at(Definition.FIELD_ID),
                    "IDs here must be in the namespace '"
                            + source.namespace()
                            + "', not '"
                            + id.namespace()
                            + "'");
            return;
        }
        Entry existing = entries.get(kind).get(id);
        if (existing != null) {
            problem(
                    origin,
                    Severity.ERROR,
                    JsonPath.at(Definition.FIELD_ID),
                    "The "
                            + kind.getFolder()
                            + " "
                            + id
                            + " is already defined in "
                            + existing.origin.file());
            return;
        }
        entries.get(kind).put(id, new Entry(kind, id, json, origin));
    }

    /**
     * Merge a definition's parents into it.
     *
     * @param entry The definition.
     */
    private void resolve(Entry entry) {
        if (entry.resolved != null || entry.failed) {
            return;
        }
        JsonElement parentJson = entry.json.get(DataFormat.EXTENDS);
        if (parentJson == null) {
            entry.resolved = entry.json;
            return;
        }
        String path = JsonPath.at(DataFormat.EXTENDS);
        Id parentId;
        try {
            if (!parentJson.isJsonPrimitive() || !parentJson.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Expected the parent's ID as a string");
            }
            parentId = Id.parse(parentJson.getAsString());
        } catch (IllegalArgumentException e) {
            fail(entry, path, e.getMessage());
            return;
        }
        Entry parent = entries.get(entry.kind).get(parentId);
        if (parent == null) {
            fail(entry, path, "There is no " + entry.kind.getFolder() + " " + parentId);
            return;
        }
        if (parent.resolving) {
            fail(entry, path, "The parents loop back to " + parentId);
            return;
        }
        entry.resolving = true;
        resolve(parent);
        entry.resolving = false;
        if (parent.failed) {
            fail(entry, path, "The parent " + parentId + " has errors");
            return;
        }
        JsonObject own = entry.json.deepCopy();
        own.remove(DataFormat.EXTENDS);
        entry.resolved = merge(parent.resolved, own);
    }

    /**
     * Merge a child's JSON over its parent's. Objects merge field by field, a null removes the
     * parent's field, and anything else replaces it.
     *
     * @param parent The parent's JSON.
     * @param child The child's JSON.
     * @return The merged JSON.
     */
    static JsonObject merge(JsonObject parent, JsonObject child) {
        JsonObject merged = parent.deepCopy();
        merged.remove(DataFormat.EXTENDS);
        for (Map.Entry<String, JsonElement> field : child.entrySet()) {
            String key = field.getKey();
            JsonElement value = field.getValue();
            JsonElement inherited = merged.get(key);
            if (value.isJsonNull()) {
                merged.remove(key);
            } else if (value.isJsonObject() && inherited != null && inherited.isJsonObject()) {
                merged.add(key, merge(inherited.getAsJsonObject(), value.getAsJsonObject()));
            } else {
                merged.add(key, value.deepCopy());
            }
        }
        return merged;
    }

    /**
     * Mark a definition unusable.
     *
     * @param entry The definition.
     * @param path Where the problem is.
     * @param message What is wrong.
     */
    private void fail(Entry entry, String path, String message) {
        entry.failed = true;
        problem(entry.origin, Severity.ERROR, path, message);
    }

    /**
     * Read a resolved definition into its record.
     *
     * @param entry The definition.
     */
    private void readRecord(Entry entry) {
        if (entry.failed) {
            return;
        }
        List<Runnable> found = new ArrayList<>();
        Report report = report(entry.origin, found);
        Definition definition =
                (Definition)
                        DataFormat.readDefinition(
                                entry.resolved,
                                entry.kind.getType().asSubclass(Record.class),
                                report);
        found.forEach(Runnable::run);
        if (definition == null) {
            entry.failed = true;
        } else {
            entry.definition = definition;
        }
    }

    /**
     * Check the definitions against each other, leaving out the ones with errors, until no more
     * drop out, since leaving one out can break those that need it.
     *
     * @return The usable definitions.
     */
    private List<Definition> check() {
        Map<Kind, Map<Id, Definition>> usable = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            Map<Id, Definition> ofKind = new LinkedHashMap<>();
            entries.get(kind).values().stream()
                    .filter(entry -> !entry.failed)
                    .sorted((a, b) -> a.id.compareTo(b.id))
                    .forEach(entry -> ofKind.put(entry.id, entry.definition));
            usable.put(kind, ofKind);
        }

        Map<Entry, List<Runnable>> lastWarnings = new LinkedHashMap<>();
        boolean dropped = true;
        while (dropped) {
            dropped = false;
            lastWarnings.clear();
            Set<Entry> failing = new HashSet<>();
            for (Kind kind : Kind.values()) {
                for (Definition definition : usable.get(kind).values()) {
                    Entry entry = entries.get(kind).get(definition.id());
                    List<Runnable> found = new ArrayList<>();
                    boolean[] errors = {false};
                    Report report = report(entry.origin, found);
                    definition.check(new EntryChecks(usable, report, errors));
                    if (errors[0]) {
                        found.forEach(Runnable::run);
                        failing.add(entry);
                    } else {
                        lastWarnings.put(entry, found);
                    }
                }
            }
            for (Entry entry : failing) {
                entry.failed = true;
                usable.get(entry.kind).remove(entry.id);
                dropped = true;
            }
        }
        lastWarnings.values().forEach(found -> found.forEach(Runnable::run));

        List<Definition> result = new ArrayList<>();
        usable.values().forEach(ofKind -> result.addAll(ofKind.values()));
        return result;
    }

    /** What a definition sees while it is checked. */
    private final class EntryChecks implements Checks {
        /** The definitions still usable. */
        private final Map<Kind, Map<Id, Definition>> usable;

        /** Where problems go. */
        private final Report report;

        /** Set to true by the first error. */
        private final boolean[] errors;

        EntryChecks(Map<Kind, Map<Id, Definition>> usable, Report report, boolean[] errors) {
            this.usable = usable;
            this.report = report;
            this.errors = errors;
        }

        @Override
        public void error(@NonNull String path, @NonNull String message) {
            errors[0] = true;
            report.error(path, message);
        }

        @Override
        public void warning(@NonNull String path, @NonNull String message) {
            report.warning(path, message);
        }

        @Override
        public <T extends Definition> T require(
                @NonNull Class<T> type, @NonNull Id id, @NonNull String path) {
            Kind kind = Kind.of(type);
            Definition found = usable.get(kind).get(id);
            if (found != null) {
                return type.cast(found);
            }
            if (entries.get(kind).containsKey(id)) {
                error(path, "The " + kind.getFolder() + " " + id + " has errors");
            } else {
                error(path, "There is no " + kind.getFolder() + " " + id);
            }
            return null;
        }

        @Override
        public <T extends Definition> List<T> all(@NonNull Class<T> type) {
            List<T> all = new ArrayList<>();
            usable.get(Kind.of(type)).values().forEach(d -> all.add(type.cast(d)));
            return all;
        }
    }
}
