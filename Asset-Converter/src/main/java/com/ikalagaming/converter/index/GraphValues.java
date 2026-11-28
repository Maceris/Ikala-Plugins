package com.ikalagaming.converter.index;

import com.ikalagaming.converter.texgraph.GraphParameter;
import com.ikalagaming.converter.texgraph.SharedParameters;
import com.ikalagaming.converter.texgraph.TextureGraph;
import com.ikalagaming.converter.texgraph.TextureInstance;
import com.ikalagaming.util.schema.Field;
import com.ikalagaming.util.schema.Schema;

import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * The values of texture graph files that hold parameter values: texture instances, whose fields are
 * their settings and the parameters their graph reads, defaulting to their parent's values, and
 * shared parameter files, whose fields are their parameters' values. Parameter values are text in
 * the files, in each kind's form, and typed for schemas: a scalar a number, a switch a flag, a seed
 * a whole number, a color a color, a choice a choice, a texture text.
 */
public final class GraphValues implements AssetValues {
    /** An instance's parent's field key, apart from parameter names, which start with a letter. */
    public static final String PARENT = "_parent";

    /** An instance's seed's field key. */
    public static final String SEED = "_seed";

    /** An instance's texture's field key. */
    public static final String TEXTURE = "_texture";

    /** An instance's model's field key. */
    public static final String MODEL = "_model";

    /** An instance's content folder's field key. */
    public static final String DATA = "_data";

    /** An instance's maps folder's field key. */
    public static final String MAPS = "_maps";

    /** The fields. */
    private final Schema schema;

    /** The parameters, by name, each the kind its field is made from. */
    private final Map<String, GraphParameter> parameters;

    /** Whether the file is an instance, else a shared parameter file. */
    private final boolean instance;

    /**
     * Values of a file.
     *
     * @param schema The fields.
     * @param parameters The parameters, by name.
     * @param instance Whether the file is an instance.
     */
    private GraphValues(Schema schema, Map<String, GraphParameter> parameters, boolean instance) {
        this.schema = schema;
        this.parameters = parameters;
        this.instance = instance;
    }

    /**
     * An instance's values: its settings and its graph's parameters, the parent's values as
     * defaults, found by following its chain of instances up to the graph.
     *
     * @param file The instance file.
     * @return The values.
     * @throws IOException If its graph can't be read.
     */
    public static GraphValues ofInstance(@NonNull Path file) throws IOException {
        final TextureInstance.Chain chain = TextureInstance.chain(file);
        final List<String> problems = new ArrayList<>();
        final TextureGraph graph = TextureInstance.readGraph(chain, problems);
        if (graph == null || chain.instances().isEmpty()) {
            throw new IOException(
                    "Can't follow " + file.getFileName() + " to its graph: " + problems);
        }
        // The parent's values: the graph with every instance above this one applied
        final TextureGraph parent =
                TextureInstance.resolve(chain, graph, chain.instances().size() - 1, problems);
        final Map<String, GraphParameter> parameters =
                parent.resolveParameters(chain.graph().getParent(), problems);
        final List<Field> fields = new ArrayList<>();
        fields.add(
                Field.text(PARENT, null, "The graph or instance this one changes, by its path")
                        .asRequired());
        fields.add(
                Field.integer(
                        SEED,
                        parent.getSeed(),
                        Long.MIN_VALUE,
                        Long.MAX_VALUE,
                        "The seed its noise starts from"));
        fields.add(Field.text(TEXTURE, parent.getTexture(), "The texture it bakes, its own name"));
        fields.add(Field.text(MODEL, parent.getModel(), "The model whose faces lay it out"));
        fields.add(Field.text(DATA, null, "The content folder it bakes into, from this file"));
        fields.add(Field.text(MAPS, null, "The folder its maps bake under, from this file"));
        for (GraphParameter parameter : sorted(parameters)) {
            fields.add(field(parameter));
        }
        return new GraphValues(new Schema("texture instance", fields), parameters, true);
    }

    /**
     * A shared parameter file's values: its parameters, each defaulting to its kind's starting
     * value.
     *
     * @param file The file.
     * @return The values.
     * @throws IOException If it can't be read.
     */
    public static GraphValues ofShared(@NonNull Path file) throws IOException {
        final Map<String, GraphParameter> parameters;
        try {
            parameters = SharedParameters.read(file).parameters();
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        final List<Field> fields = new ArrayList<>();
        for (GraphParameter parameter : sorted(parameters)) {
            fields.add(field(parameter.withValue(parameter.kind().initial)));
        }
        return new GraphValues(new Schema("shared parameters", fields), parameters, false);
    }

    /**
     * Parameters in the order they are listed: by group, then order, then name.
     *
     * @param parameters The parameters.
     * @return Them, sorted.
     */
    private static List<GraphParameter> sorted(Map<String, GraphParameter> parameters) {
        final List<GraphParameter> list = new ArrayList<>(parameters.values());
        list.sort(
                Comparator.comparing(GraphParameter::group)
                        .thenComparingInt(GraphParameter::order)
                        .thenComparing(GraphParameter::name));
        return list;
    }

    /**
     * A parameter as a field, its value the default.
     *
     * @param parameter The parameter.
     * @return The field.
     */
    static Field field(GraphParameter parameter) {
        final String name = parameter.name();
        final String about =
                parameter.group().isEmpty()
                        ? "A graph parameter"
                        : "A graph parameter, in " + parameter.group();
        final Object fallback = typed(parameter, parameter.value());
        return switch (parameter.kind()) {
            case SCALAR ->
                    Field.number(
                            name,
                            fallback == null ? 0 : (Double) fallback,
                            Math.min(parameter.min(), parameter.max()),
                            Math.max(parameter.min(), parameter.max()),
                            about);
            case SWITCH -> Field.flag(name, Boolean.TRUE.equals(fallback), about);
            case SEED ->
                    Field.integer(
                            name,
                            fallback == null ? 1 : (Long) fallback,
                            Long.MIN_VALUE,
                            Long.MAX_VALUE,
                            about);
            case COLOR -> Field.color(name, (String) fallback, about);
            case CHOICE ->
                    parameter.choices().isEmpty()
                            ? Field.text(name, (String) fallback, about)
                            : Field.choice(
                                    name,
                                    parameter.choices().contains(parameter.value())
                                            ? parameter.value()
                                            : parameter.choices().getFirst(),
                                    about,
                                    parameter.choices().toArray(String[]::new));
            case TEXTURE ->
                    Field.text(name, (String) fallback, about + "; an image, from the graph");
        };
    }

    /**
     * A parameter's value as its field's type.
     *
     * @param parameter The parameter, for its kind.
     * @param text The value as files write it.
     * @return The value, or null if it doesn't read as its kind.
     */
    static @Nullable Object typed(GraphParameter parameter, String text) {
        try {
            return switch (parameter.kind()) {
                case SCALAR -> Double.parseDouble(text.trim());
                case SWITCH -> Boolean.parseBoolean(text.trim());
                case SEED -> Long.parseLong(text.trim());
                case COLOR -> text.trim().toLowerCase(Locale.ROOT);
                case CHOICE, TEXTURE -> text;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A typed value as files write it.
     *
     * @param value The value.
     * @return The text: a whole number without a point.
     */
    static String text(Object value) {
        if (value instanceof Double number && number == Math.rint(number)) {
            return Long.toString(number.longValue());
        }
        if (value instanceof Number number && !(value instanceof Double)) {
            return Long.toString(number.longValue());
        }
        if (value instanceof Double number) {
            return Float.toString(number.floatValue());
        }
        return String.valueOf(value);
    }

    @Override
    public Schema schema() {
        return schema;
    }

    @Override
    public Schema.Values read(@NonNull Path file) throws IOException {
        final Map<String, Object> raw = new LinkedHashMap<>();
        if (instance) {
            final TextureInstance read = readInstance(file);
            put(raw, PARENT, read.getParent());
            put(raw, SEED, read.getSeed());
            put(raw, TEXTURE, read.getTexture());
            put(raw, MODEL, read.getModel());
            put(raw, DATA, read.getData());
            put(raw, MAPS, read.getMaps());
            for (Map.Entry<String, String> value : read.getValues().entrySet()) {
                final GraphParameter parameter = parameters.get(value.getKey());
                raw.put(
                        value.getKey(),
                        parameter == null ? value.getValue() : typed(parameter, value.getValue()));
            }
        } else {
            final Map<String, GraphParameter> own;
            try {
                own = SharedParameters.read(file).parameters();
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage(), e);
            }
            for (GraphParameter parameter : own.values()) {
                raw.put(parameter.name(), typed(parameter, parameter.value()));
            }
        }
        raw.values().removeIf(java.util.Objects::isNull);
        return schema.read(raw);
    }

    @Override
    public void write(@NonNull Path file, @NonNull Map<String, Object> values) throws IOException {
        if (instance) {
            final TextureInstance written = readInstance(file);
            written.setParent(asText(values.get(PARENT)));
            written.setSeed(values.get(SEED) instanceof Number seed ? seed.longValue() : null);
            written.setTexture(asText(values.get(TEXTURE)));
            written.setModel(asText(values.get(MODEL)));
            written.setData(asText(values.get(DATA)));
            written.setMaps(asText(values.get(MAPS)));
            // Overrides: what is given; a parameter not given is its parent's again
            for (String name : List.copyOf(written.getValues().keySet())) {
                if (parameters.containsKey(name)) {
                    written.removeValue(name);
                }
            }
            for (Map.Entry<String, Object> value : values.entrySet()) {
                if (parameters.containsKey(value.getKey()) && value.getValue() != null) {
                    written.setValue(value.getKey(), text(value.getValue()));
                }
            }
            Files.writeString(file, written.write(), StandardCharsets.UTF_8);
            return;
        }
        final List<GraphParameter> written = new ArrayList<>();
        try {
            for (GraphParameter parameter : SharedParameters.read(file).parameters().values()) {
                final Object value = values.get(parameter.name());
                written.add(
                        parameter.withValue(
                                value == null ? parameter.kind().initial : text(value)));
            }
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        written.sort(Comparator.comparing(GraphParameter::name));
        SharedParameters.save(file, written);
    }

    /**
     * Read an instance file.
     *
     * @param file The file.
     * @return The instance.
     * @throws IOException If it can't be read.
     */
    private static TextureInstance readInstance(Path file) throws IOException {
        final TextureInstance read = new TextureInstance();
        try {
            read.read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        return read;
    }

    /**
     * Put a value, if there is one.
     *
     * @param raw Where.
     * @param key The key.
     * @param value The value, or null.
     */
    private static void put(Map<String, Object> raw, String key, @Nullable Object value) {
        if (value != null) {
            raw.put(key, value);
        }
    }

    /**
     * A value as text, or null.
     *
     * @param value The value.
     * @return The text, or null if there is none or it is blank.
     */
    private static @Nullable String asText(@Nullable Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }
}
