package com.ikalagaming.factory.world.gen.data;

import com.ikalagaming.factory.registry.RegistryConstants;
import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.*;
import com.ikalagaming.factory.world.gen.density.Box;
import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.factory.world.gen.density.Interval;
import com.ikalagaming.factory.world.gen.density.Sources;
import com.ikalagaming.factory.world.gen.noise.Noise;

import lombok.NonNull;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Turns world generation data into the objects generation runs, checking it as it goes: unknown
 * types, missing references, reference cycles, values out of range and trees that are too deep.
 * Every problem names the file and field. A file with errors is rejected, and so is anything that
 * depends on it, but everything else still compiles, so one pass finds every problem.
 *
 * <p>Compiled objects depend on the world seed, since noise seeds are derived from it. Files are
 * compiled when first needed and then shared, so two references to one noise are the same field.
 * Not thread safe; the compiled objects are, once built.
 */
public final class WorldgenCompiler implements FieldReader.Problems {

    /** The deepest a density tree can nest, so a mistake can't overflow the stack. */
    public static final int MAX_DEPTH = 64;

    /** How many numbers a range is written with: its min and max. */
    private static final int RANGE_LENGTH = 2;

    /** What a block ID must look like. */
    private static final Pattern BLOCK_ID =
            Pattern.compile(RegistryConstants.FULLY_QUALIFIED_NAME_FORMAT);

    /** The data being compiled. */
    private final WorldgenData data;

    /** The world seed noise seeds derive from. */
    private final long seed;

    /** The blocks that exist, or null to only check the ID format. */
    private final Set<String> knownBlocks;

    /** Every problem found, starting with those from reading the files. */
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    /** The files that have errors, by label. */
    private final Set<String> failed = new HashSet<>();

    /** Compiled files by kind, then ID; empty when the file failed. */
    private final Map<Kind, Map<String, Optional<?>>> compiled = new EnumMap<>(Kind.class);

    /** Compiled noise fields, by ID, salt and wavelength. */
    private final Map<String, Noise> noises = new HashMap<>();

    /** The files being compiled, innermost last, to find reference cycles. */
    private final Deque<String> compiling = new ArrayDeque<>();

    /** Where each density node came from, as {@code label#path}, for tracing. */
    private final Map<DensityNode, String> origins = new IdentityHashMap<>();

    /** How deep the density tree being read is. */
    private int depth;

    /** The next free cache slot. */
    private int slots;

    /**
     * Set up a compiler.
     *
     * @param data The data.
     * @param seed The world seed.
     * @param knownBlocks The block IDs that exist, or null to only check their format.
     */
    public WorldgenCompiler(@NonNull WorldgenData data, long seed, Set<String> knownBlocks) {
        this.data = data;
        this.seed = seed;
        this.knownBlocks = knownBlocks;
        diagnostics.addAll(data.getDiagnostics());
        for (Kind kind : Kind.values()) {
            compiled.put(kind, new HashMap<>());
        }
    }

    /**
     * The label a file's diagnostics use: its kind and ID, like {@code
     * density/lotomation:overworld/terrain}, since IDs are only unique within a kind.
     *
     * @param kind The kind.
     * @param id The ID.
     * @return The label.
     */
    public static String label(@NonNull Kind kind, @NonNull String id) {
        return kind.getFolder() + "/" + id;
    }

    @Override
    public void report(
            @NonNull String file,
            @NonNull String field,
            Diagnostic.Severity severity,
            @NonNull String code,
            Object... args) {
        diagnostics.add(
                new Diagnostic(file, field, severity, code, WorldgenStrings.format(code, args)));
        if (severity == Diagnostic.Severity.ERROR) {
            failed.add(file);
        }
    }

    /**
     * Every problem found so far.
     *
     * @return The diagnostics, in the order found.
     */
    public List<Diagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    /**
     * Whether any problem so far is an error.
     *
     * @return True if something was rejected.
     */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

    /** Compile every file, to check all of them. */
    public void compileAll() {
        for (Kind kind : Kind.values()) {
            for (WorldgenData.DataFile file : data.all(kind)) {
                switch (kind) {
                    case NOISE -> noiseFile(file.id(), null, null, null);
                    case DENSITY -> density(file.id(), null);
                    case PARAMETER -> parameter(file.id(), null);
                    case BIOME -> biome(file.id(), null);
                    case BLOCK_RULES -> blockRules(file.id(), null);
                    case FLUIDS -> fluidsFile(file.id(), null);
                    case WORLD_TYPE -> worldType(file.id(), null);
                }
            }
        }
    }

    /**
     * Where a density node was defined, for tracing.
     *
     * @param node The node.
     * @return Its label and field path, like {@code density/lotomation:x#args[1]}, or null.
     */
    public String origin(@NonNull DensityNode node) {
        return origins.get(node);
    }

    /**
     * Find a density node by where it was defined.
     *
     * @param origin The label and field path, as {@link #origin} gives.
     * @return The node.
     */
    public Optional<DensityNode> nodeAt(@NonNull String origin) {
        return origins.entrySet().stream()
                .filter(e -> e.getValue().equals(origin))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * A new cache slot, for a node that caches values per chunk.
     *
     * @return The slot.
     */
    public int nextSlot() {
        return slots++;
    }

    /**
     * Compile a file once, sharing the result, with cycle detection.
     *
     * @param kind The kind of file.
     * @param id The ID.
     * @param from The field that references it, or null when compiling it directly.
     * @param build Builds the object from the file's root.
     * @param <T> The compiled type.
     * @return The object, or empty if the file is missing or has errors.
     */
    @SuppressWarnings("unchecked")
    private <T> Optional<T> compileFile(
            @NonNull Kind kind,
            @NonNull String id,
            FieldReader from,
            @NonNull Function<FieldReader, T> build) {
        final Map<String, Optional<?>> done = compiled.get(kind);
        final String label = label(kind, id);
        if (done.containsKey(id)) {
            final Optional<T> result = (Optional<T>) done.get(id);
            if (result.isEmpty() && from != null) {
                from.error("WORLDGEN_BROKEN_REFERENCE", label);
            }
            return result;
        }
        if (compiling.contains(label)) {
            if (from != null) {
                from.error(
                        "WORLDGEN_REFERENCE_CYCLE",
                        String.join(" -> ", compiling) + " -> " + label);
            }
            return Optional.empty();
        }
        final Optional<WorldgenData.DataFile> file = data.get(kind, id);
        if (file.isEmpty()) {
            if (from != null) {
                from.error("WORLDGEN_MISSING_REFERENCE", label);
            }
            return Optional.empty();
        }
        compiling.addLast(label);
        final int outerDepth = depth;
        depth = 0;
        T result;
        try {
            result = build.apply(new FieldReader(label, "", file.get().root(), this));
        } finally {
            depth = outerDepth;
            compiling.removeLast();
        }
        final Optional<T> outcome =
                failed.contains(label) ? Optional.empty() : Optional.ofNullable(result);
        done.put(id, outcome);
        if (outcome.isEmpty() && from != null) {
            from.error("WORLDGEN_BROKEN_REFERENCE", label);
        }
        return outcome;
    }

    /**
     * A density file's function.
     *
     * @param id The ID.
     * @param from The referencing field, or null.
     * @return The function, if the file is good.
     */
    public Optional<DensityNode> density(@NonNull String id, FieldReader from) {
        return compileFile(Kind.DENSITY, id, from, this::density);
    }

    /**
     * Read a density value: a string ID, an inline {@code {type:...}} or a bare number.
     *
     * @param reader The value.
     * @return The node; a constant 0 after an error, which also rejects the file.
     */
    public DensityNode density(@NonNull FieldReader reader) {
        if (++depth > MAX_DEPTH) {
            reader.error("WORLDGEN_TOO_DEEP", MAX_DEPTH);
            --depth;
            return new Sources.Constant(0);
        }
        try {
            DensityNode node;
            if (reader.isNumber()) {
                node = new Sources.Constant(reader.number());
            } else if (reader.isString()) {
                node = reference("ref", reader.string(), reader);
            } else if (reader.isNode()) {
                final FieldReader typeField = reader.get("type");
                final String type = typeField.string();
                final Optional<WorldgenTypes.DensityFactory> factory =
                        WorldgenTypes.DENSITY.get(type);
                if (factory.isEmpty()) {
                    if (!type.isEmpty()) {
                        typeField.error(
                                "WORLDGEN_UNKNOWN_TYPE",
                                type,
                                String.join(", ", WorldgenTypes.DENSITY.names()));
                    }
                    node = new Sources.Constant(0);
                } else {
                    node = factory.get().create(reader, this);
                }
            } else {
                reader.error(
                        reader.exists() ? "WORLDGEN_EXPECTED_DENSITY" : "WORLDGEN_MISSING_FIELD");
                node = new Sources.Constant(0);
            }
            origins.putIfAbsent(
                    node,
                    reader.getPath().isEmpty()
                            ? reader.getFile()
                            : reader.getFile() + "#" + reader.getPath());
            return node;
        } finally {
            --depth;
        }
    }

    /**
     * A reference to another density file or a parameter, cached where it ignores an axis.
     *
     * @param type {@code ref} or {@code parameter}.
     * @param id The referenced ID.
     * @param from The referencing field.
     * @return The reference node, or a constant 0 if the target is missing or broken.
     */
    public DensityNode reference(
            @NonNull String type, @NonNull String id, @NonNull FieldReader from) {
        final Optional<DensityNode> target =
                "parameter".equals(type)
                        ? parameter(id, from).map(Parameter::density)
                        : density(id, from);
        if (target.isEmpty()) {
            return new Sources.Constant(0);
        }
        final int axes = target.get().axes();
        final int slot = axes != 0 && axes != DensityNode.ALL ? nextSlot() : -1;
        return new Sources.Ref(type, id, target.get(), slot);
    }

    /**
     * Read a noise value: a noise file's ID, or an inline noise.
     *
     * @param reader The value.
     * @param salt A salt to use instead of the noise's own, for an independent copy, or null.
     * @param wavelength A wavelength to use instead of the noise's own, or null.
     * @return The noise, or empty after an error.
     */
    public Optional<Noise> noise(@NonNull FieldReader reader, String salt, double[] wavelength) {
        if (reader.isString()) {
            return noiseFile(reader.string(), reader, salt, wavelength);
        }
        if (reader.isNode()) {
            final String inlineSalt =
                    salt != null ? salt : reader.getFile() + "#" + reader.getPath();
            return Optional.ofNullable(buildNoise(reader, inlineSalt, wavelength));
        }
        reader.error(reader.exists() ? "WORLDGEN_EXPECTED_NOISE" : "WORLDGEN_MISSING_FIELD");
        return Optional.empty();
    }

    /**
     * A noise file's field, shared between references with the same overrides.
     *
     * @param id The ID.
     * @param from The referencing field, or null.
     * @param salt The salt override, or null for the file's own.
     * @param wavelength The wavelength override, or null.
     * @return The noise, if the file is good.
     */
    private Optional<Noise> noiseFile(
            @NonNull String id, FieldReader from, String salt, double[] wavelength) {
        final Optional<Noise> plain =
                compileFile(Kind.NOISE, id, from, root -> buildNoise(root, null, null));
        if (plain.isEmpty() || (salt == null && wavelength == null)) {
            return plain;
        }
        final String key = id + "|" + salt + "|" + Arrays.toString(wavelength);
        if (!noises.containsKey(key)) {
            final WorldgenData.DataFile file = data.get(Kind.NOISE, id).orElseThrow();
            noises.put(
                    key,
                    buildNoise(
                            new FieldReader(label(Kind.NOISE, id), "", file.root(), this),
                            salt,
                            wavelength));
        }
        return Optional.ofNullable(noises.get(key));
    }

    /**
     * Build a noise from its definition.
     *
     * @param reader The definition.
     * @param salt The salt override, or null to use the definition's, or its file's ID.
     * @param wavelength The wavelength override, or null.
     * @return The noise, or null after an error.
     */
    private Noise buildNoise(@NonNull FieldReader reader, String salt, double[] wavelength) {
        final FieldReader typeField = reader.get("type");
        final String type = typeField.string();
        final Optional<WorldgenTypes.NoiseFactory> factory = WorldgenTypes.NOISE.get(type);
        if (factory.isEmpty()) {
            if (!type.isEmpty()) {
                typeField.error(
                        "WORLDGEN_UNKNOWN_TYPE",
                        type,
                        String.join(", ", WorldgenTypes.NOISE.names()));
            }
            return null;
        }
        String fileId = reader.getFile();
        fileId = fileId.substring(fileId.indexOf('/') + 1);
        final String saltText = salt != null ? salt : reader.get("salt").string(fileId);
        return factory.get().create(reader, WorldgenHash.seed(seed, saltText), wavelength, this);
    }

    /**
     * A parameter file.
     *
     * @param id The ID.
     * @param from The referencing field, or null.
     * @return The parameter, if the file is good.
     */
    public Optional<Parameter> parameter(@NonNull String id, FieldReader from) {
        return compileFile(
                Kind.PARAMETER,
                id,
                from,
                root -> {
                    root.warnUnknownFields(Set.of("density", "display_range"));
                    final DensityNode density = density(root.get("density"));
                    Interval display = Interval.UNIT;
                    if (root.has("display_range")) {
                        final double[] range = root.get("display_range").numbers(RANGE_LENGTH);
                        display = new Interval(range[0], range[1]);
                    }
                    return new Parameter(id, density, display);
                });
    }

    /**
     * A biome file.
     *
     * @param id The ID.
     * @param from The referencing field, or null.
     * @return The biome, if the file is good.
     */
    public Optional<Biome> biome(@NonNull String id, FieldReader from) {
        return compileFile(
                Kind.BIOME,
                id,
                from,
                root -> {
                    // visual and tags are read by later steps
                    root.warnUnknownFields(
                            Set.of("climate", "weight", "block_rules", "visual", "tags"));
                    final FieldReader climateField = root.get("climate");
                    Map<String, ParameterRange> climate = new TreeMap<>();
                    for (String name : climateField.keys()) {
                        final FieldReader range = climateField.get(name);
                        final double[] bounds = range.numbers(RANGE_LENGTH);
                        if (bounds[1] < bounds[0]) {
                            range.error("WORLDGEN_RANGE_REVERSED");
                            continue;
                        }
                        climate.put(name, new ParameterRange((float) bounds[0], (float) bounds[1]));
                    }
                    final FieldReader weightField = root.get("weight");
                    final double weight = weightField.number(1);
                    if (weight <= 0) {
                        weightField.error("WORLDGEN_NOT_POSITIVE");
                    }
                    BlockRules rules = null;
                    if (root.has("block_rules")) {
                        rules = blockRulesValue(root.get("block_rules")).orElse(null);
                    }
                    return new Biome(id, climate, weight, rules);
                });
    }

    /**
     * Read a block rules value: a file's ID, or an inline {@code {rules:[N;...]}}.
     *
     * @param reader The value.
     * @return The rules, or empty after an error.
     */
    public Optional<BlockRules> blockRulesValue(@NonNull FieldReader reader) {
        if (reader.isString()) {
            return blockRules(reader.string(), reader);
        }
        if (reader.isNode()) {
            return Optional.of(buildRules(reader, reader.getFile() + "#" + reader.getPath()));
        }
        reader.error(reader.exists() ? "WORLDGEN_EXPECTED_RULES" : "WORLDGEN_MISSING_FIELD");
        return Optional.empty();
    }

    /**
     * A block rules file.
     *
     * @param id The ID.
     * @param from The referencing field, or null.
     * @return The rules, if the file is good.
     */
    public Optional<BlockRules> blockRules(@NonNull String id, FieldReader from) {
        return compileFile(Kind.BLOCK_RULES, id, from, root -> buildRules(root, id));
    }

    /**
     * Build a rule list from {@code {rules:[N;...]}}.
     *
     * @param reader The object holding the list.
     * @param id The ID or location to name it by.
     * @return The rules.
     */
    private BlockRules buildRules(@NonNull FieldReader reader, @NonNull String id) {
        reader.warnUnknownFields(Set.of("rules"));
        List<BlockRules.Rule> rules = new ArrayList<>();
        for (FieldReader ruleField : reader.get("rules").list()) {
            ruleField.warnUnknownFields(Set.of("if", "then"));
            final BlockRules.Condition condition =
                    ruleField.has("if") ? condition(ruleField.get("if")) : null;
            final FieldReader then = ruleField.get("then");
            then.warnUnknownFields(Set.of("block", "rules"));
            if (then.has("block") == then.has("rules")) {
                then.error("WORLDGEN_RULE_NEEDS_ONE");
                continue;
            }
            if (then.has("block")) {
                rules.add(new BlockRules.Rule(condition, block(then.get("block")), null));
                continue;
            }
            final FieldReader nested = then.get("rules");
            final Optional<BlockRules> group =
                    nested.isList()
                            ? Optional.of(buildRules(then, id + "#" + then.getPath()))
                            : blockRulesValue(nested);
            group.ifPresent(g -> rules.add(new BlockRules.Rule(condition, null, g)));
        }
        return new BlockRules(id, rules);
    }

    /**
     * Read a block rule condition.
     *
     * @param reader The condition object.
     * @return The condition; one that never passes after an error.
     */
    public BlockRules.Condition condition(@NonNull FieldReader reader) {
        final FieldReader typeField = reader.get("type");
        final String type = typeField.string();
        final Optional<WorldgenTypes.ConditionFactory> factory = WorldgenTypes.CONDITION.get(type);
        if (factory.isEmpty()) {
            if (!type.isEmpty()) {
                typeField.error(
                        "WORLDGEN_UNKNOWN_TYPE",
                        type,
                        String.join(", ", WorldgenTypes.CONDITION.names()));
            }
            return context -> false;
        }
        return factory.get().create(reader, this);
    }

    /**
     * Check a biome exists, for conditions that name biomes.
     *
     * @param reader The field naming it.
     * @param id The biome ID.
     */
    public void checkBiomeExists(@NonNull FieldReader reader, @NonNull String id) {
        if (data.get(Kind.BIOME, id).isEmpty()) {
            reader.error("WORLDGEN_MISSING_REFERENCE", label(Kind.BIOME, id));
        }
    }

    /**
     * Read a block ID and check it.
     *
     * @param reader The field.
     * @return The block, air after an error.
     */
    public Block block(@NonNull FieldReader reader) {
        final String id = reader.string();
        if (id.isEmpty()) {
            return World.AIR;
        }
        if (World.AIR_NAME.equals(id)) {
            return World.AIR;
        }
        if (!BLOCK_ID.matcher(id).matches()) {
            reader.error("WORLDGEN_BAD_BLOCK_ID", id);
            return World.AIR;
        }
        if (knownBlocks != null && !knownBlocks.contains(id)) {
            reader.error("WORLDGEN_UNKNOWN_BLOCK", id);
        }
        return new Block(id, null);
    }

    /**
     * Read a fluids value: a file's ID or an inline object.
     *
     * @param reader The value.
     * @return The fluids, or empty after an error.
     */
    public Optional<Fluids> fluidsValue(@NonNull FieldReader reader) {
        if (reader.isString()) {
            return fluidsFile(reader.string(), reader);
        }
        if (reader.isNode()) {
            return Optional.ofNullable(buildFluids(reader));
        }
        reader.error(reader.exists() ? "WORLDGEN_EXPECTED_FLUIDS" : "WORLDGEN_MISSING_FIELD");
        return Optional.empty();
    }

    /**
     * A fluids file.
     *
     * @param id The ID.
     * @param from The referencing field, or null.
     * @return The fluids, if the file is good.
     */
    private Optional<Fluids> fluidsFile(@NonNull String id, FieldReader from) {
        return compileFile(Kind.FLUIDS, id, from, this::buildFluids);
    }

    /**
     * Build fluids from their definition.
     *
     * @param reader The definition.
     * @return The fluids, or null after an error.
     */
    private Fluids buildFluids(@NonNull FieldReader reader) {
        final FieldReader typeField = reader.get("type");
        final String type = typeField.string();
        final Optional<WorldgenTypes.FluidsFactory> factory = WorldgenTypes.FLUIDS.get(type);
        if (factory.isEmpty()) {
            if (!type.isEmpty()) {
                typeField.error(
                        "WORLDGEN_UNKNOWN_TYPE",
                        type,
                        String.join(", ", WorldgenTypes.FLUIDS.names()));
            }
            return null;
        }
        return factory.get().create(reader, this);
    }

    /**
     * A world type file.
     *
     * @param id The ID.
     * @param from The referencing field, or null.
     * @return The world type, if the file and everything it uses are good.
     */
    public Optional<WorldType> worldType(@NonNull String id, FieldReader from) {
        return compileFile(Kind.WORLD_TYPE, id, from, root -> buildWorldType(id, root));
    }

    /**
     * Build a world type.
     *
     * @param id The ID.
     * @param root The file.
     * @return The world type, or null after an error.
     */
    private WorldType buildWorldType(@NonNull String id, @NonNull FieldReader root) {
        // structure_sets and spawn are read by later steps
        root.warnUnknownFields(
                Set.of(
                        "border",
                        "density",
                        "interpolation",
                        "default_block",
                        "parameters",
                        "biomes",
                        "fallback_biome",
                        "fluids",
                        "structure_sets",
                        "spawn"));
        final DensityNode density = density(root.get("density"));

        int[] interpolation =
                WorldType.DEFAULT_INTERPOLATION.stream().mapToInt(Integer::intValue).toArray();
        if (root.has("interpolation")) {
            final FieldReader field = root.get("interpolation");
            final double[] cells = field.numbers(interpolation.length);
            for (int i = 0; i < cells.length; ++i) {
                if (cells[i] < 1 || cells[i] != Math.rint(cells[i])) {
                    field.error("WORLDGEN_CELL_SIZE");
                    break;
                }
                interpolation[i] = (int) cells[i];
            }
        }

        Box border = WorldType.DEFAULT_BORDER;
        int killDistance = WorldType.DEFAULT_KILL_DISTANCE;
        if (root.has("border")) {
            final FieldReader borderField = root.get("border");
            borderField.warnUnknownFields(Set.of("min", "max", "kill_distance"));
            final double[] min = borderField.get("min").numbers(DensityNode.AXIS_COUNT);
            final double[] max = borderField.get("max").numbers(DensityNode.AXIS_COUNT);
            for (int axis = 0; axis < min.length; ++axis) {
                if (min[axis] >= max[axis]
                        || min[axis] < -WorldType.MAX_BORDER
                        || max[axis] > WorldType.MAX_BORDER) {
                    borderField.error("WORLDGEN_BAD_BORDER", WorldType.MAX_BORDER);
                    break;
                }
            }
            border = new Box(min[0], min[1], min[2], max[0], max[1], max[2]);
            killDistance =
                    borderField.get("kill_distance").integer(WorldType.DEFAULT_KILL_DISTANCE);
            if (killDistance < 0) {
                borderField.get("kill_distance").error("WORLDGEN_NOT_POSITIVE");
            }
        }

        final Block defaultBlock = block(root.get("default_block"));

        List<Parameter> parameters = new ArrayList<>();
        // Every listed parameter's ID, even ones with errors, so biomes naming a broken parameter
        // don't also get a warning about it
        List<String> listedParameters = new ArrayList<>();
        for (FieldReader field : root.get("parameters").list()) {
            listedParameters.add(field.string());
            parameter(field.string(), field).ifPresent(parameters::add);
        }
        List<Biome> biomes = new ArrayList<>();
        List<FieldReader> biomeFields = new ArrayList<>();
        for (FieldReader field : root.get("biomes").list()) {
            final Optional<Biome> biome = biome(field.string(), field);
            if (biome.isPresent()) {
                biomes.add(biome.get());
                biomeFields.add(field);
            }
        }
        final FieldReader fallbackField = root.get("fallback_biome");
        final Optional<Biome> fallback = biome(fallbackField.string(), fallbackField);
        final Fluids fluids =
                root.has("fluids")
                        ? fluidsValue(root.get("fluids")).orElse(new Fluids.None())
                        : new Fluids.None();

        // Match each biome's climate names to this world type's parameters
        List<ParameterRange[]> climates = new ArrayList<>();
        for (int b = 0; b < biomes.size(); ++b) {
            final Biome biome = biomes.get(b);
            ParameterRange[] ranges = new ParameterRange[parameters.size()];
            for (Map.Entry<String, ParameterRange> entry : biome.climate().entrySet()) {
                final int index = parameterIndex(parameters, entry.getKey());
                if (index < 0) {
                    if (!namesListed(listedParameters, entry.getKey())) {
                        biomeFields
                                .get(b)
                                .warn("WORLDGEN_UNMATCHED_CLIMATE", biome.id(), entry.getKey());
                    }
                    continue;
                }
                ranges[index] = entry.getValue();
            }
            climates.add(ranges);
        }

        if (fallback.isEmpty()) {
            return null;
        }
        return new WorldType(
                id,
                density,
                interpolation,
                border,
                killDistance,
                defaultBlock,
                List.copyOf(parameters),
                List.copyOf(biomes),
                fallback.get(),
                fluids,
                List.copyOf(climates));
    }

    /**
     * Whether a climate name means one of the parameters a world type lists, compiled or not.
     *
     * @param listed The listed parameter IDs.
     * @param name The name a biome used.
     * @return True if it is a full ID or a short name of a listed parameter.
     */
    private static boolean namesListed(@NonNull List<String> listed, @NonNull String name) {
        return listed.stream()
                .anyMatch(id -> id.equals(name) || id.substring(id.indexOf(':') + 1).equals(name));
    }

    /**
     * Which parameter a biome's climate name means: the full ID, or the short name when only one
     * parameter has it.
     *
     * @param parameters The world type's parameters.
     * @param name The name the biome used.
     * @return The parameter's index, or -1 if none or more than one match.
     */
    private static int parameterIndex(@NonNull List<Parameter> parameters, @NonNull String name) {
        int found = -1;
        for (int i = 0; i < parameters.size(); ++i) {
            if (parameters.get(i).id().equals(name)) {
                return i;
            }
            if (parameters.get(i).shortName().equals(name)) {
                if (found >= 0) {
                    return -1;
                }
                found = i;
            }
        }
        return found;
    }

    /**
     * The data being compiled.
     *
     * @return The data.
     */
    public WorldgenData getData() {
        return data;
    }

    /**
     * The world seed.
     *
     * @return The seed.
     */
    public long getSeed() {
        return seed;
    }
}
