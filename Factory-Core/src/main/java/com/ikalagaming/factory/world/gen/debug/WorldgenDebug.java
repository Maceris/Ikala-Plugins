package com.ikalagaming.factory.world.gen.debug;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.BiomeSelector;
import com.ikalagaming.factory.world.gen.Parameter;
import com.ikalagaming.factory.world.gen.Stage;
import com.ikalagaming.factory.world.gen.WorldGenerator;
import com.ikalagaming.factory.world.gen.WorldType;
import com.ikalagaming.factory.world.gen.data.Diagnostic;
import com.ikalagaming.factory.world.gen.data.Kind;
import com.ikalagaming.factory.world.gen.data.WorldgenCompiler;
import com.ikalagaming.factory.world.gen.data.WorldgenData;
import com.ikalagaming.factory.world.gen.density.Box;
import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.factory.world.gen.density.Interval;
import com.ikalagaming.factory.world.gen.density.Sources;
import com.ikalagaming.factory.world.gen.noise.Noise;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.*;

/**
 * The headless debug API over world generation: everything the CLI tools, the Worldgen Lab and
 * in-game commands use to see what the data does. It runs the exact same code as the game; there is
 * never a second implementation of noise or density.
 *
 * <p>Targets are any noise, density or parameter ID, or a node inside a file, written {@code
 * id#path}, like {@code lotomation:overworld/terrain#args[1].arg}.
 */
public final class WorldgenDebug {

    /** Which plane a slice lies in. The first axis runs across the image, the second down it. */
    public enum Plane {
        /** Looking down: x across, z down. */
        XZ,
        /** Looking along z: x across, y up. */
        XY,
        /** Looking along x: z across, y up. */
        ZY
    }

    /**
     * What loading gave.
     *
     * @param debug The debug context, if the world type compiled.
     * @param diagnostics Every problem found.
     */
    public record LoadResult(
            Optional<WorldgenDebug> debug, @NonNull List<Diagnostic> diagnostics) {}

    /**
     * One node's value at a point, and its children's.
     *
     * @param origin Where the node is defined, like {@code density/lotomation:x#args[1]}.
     * @param type The node's type.
     * @param value The value, or NaN if the node isn't evaluated at a single point here.
     * @param bounds The node's range over the block around the point.
     * @param children The children's traces.
     */
    public record Trace(
            String origin,
            @NonNull String type,
            double value,
            @NonNull Interval bounds,
            @NonNull List<Trace> children) {}

    /**
     * One node's range over a box, and its children's over the boxes they see.
     *
     * @param origin Where the node is defined.
     * @param type The node's type.
     * @param bounds The range.
     * @param children The children's ranges.
     */
    public record Bounds(
            String origin,
            @NonNull String type,
            @NonNull Interval bounds,
            @NonNull List<Bounds> children) {}

    /**
     * Why a biome is where it is.
     *
     * @param parameters Each parameter's value, by ID, in the world type's order.
     * @param ranked Every biome's fit, best first.
     * @param chosen The biome generation picks.
     */
    public record BiomeReport(
            @NonNull Map<String, Double> parameters,
            @NonNull List<BiomeSelector.Candidate> ranked,
            @NonNull String chosen) {}

    /** The compiled data. */
    private final WorldgenCompiler compiler;

    /** Generates chunks. */
    private final WorldGenerator generator;

    /**
     * Set up debugging for a compiled world type.
     *
     * @param compiler The compiler it came from.
     * @param generator Its generator.
     */
    private WorldgenDebug(@NonNull WorldgenCompiler compiler, @NonNull WorldGenerator generator) {
        this.compiler = compiler;
        this.generator = generator;
    }

    /**
     * Load a data set on its own, separate from any running game.
     *
     * @param worldType The world type's ID.
     * @param dataFolders The data folders, in load order.
     * @param seed The world seed.
     * @param knownBlocks The block IDs that exist, or null to only check their format.
     * @return The debug context if the world type compiled, and every problem found.
     */
    public static LoadResult load(
            @NonNull String worldType,
            @NonNull List<Path> dataFolders,
            long seed,
            Set<String> knownBlocks) {
        return load(worldType, WorldgenData.load(dataFolders), seed, knownBlocks);
    }

    /**
     * Load already-read data.
     *
     * @param worldType The world type's ID.
     * @param data The data.
     * @param seed The world seed.
     * @param knownBlocks The block IDs that exist, or null to only check their format.
     * @return The debug context if the world type compiled, and every problem found.
     */
    public static LoadResult load(
            @NonNull String worldType,
            @NonNull WorldgenData data,
            long seed,
            Set<String> knownBlocks) {
        WorldgenCompiler compiler = new WorldgenCompiler(data, seed, knownBlocks);
        final Optional<WorldType> type = compiler.worldType(worldType, null);
        if (type.isEmpty() && data.get(Kind.WORLD_TYPE, worldType).isEmpty()) {
            compiler.report(
                    WorldgenCompiler.label(Kind.WORLD_TYPE, worldType),
                    "",
                    Diagnostic.Severity.ERROR,
                    "WORLDGEN_NO_WORLD_TYPE",
                    worldType);
        }
        return new LoadResult(
                type.map(t -> new WorldgenDebug(compiler, new WorldGenerator(t, seed))),
                compiler.getDiagnostics());
    }

    /**
     * The generator, for generating chunks and reading its counters.
     *
     * @return The generator.
     */
    public WorldGenerator getGenerator() {
        return generator;
    }

    /**
     * Find a target's function.
     *
     * @param target An ID, or {@code id#path} for a node inside a file.
     * @param channel The noise channel, for noise targets, or null for the first.
     * @return The function.
     * @throws IllegalArgumentException If the target doesn't exist or has errors.
     */
    public DensityNode resolve(@NonNull String target, String channel) {
        final int hash = target.indexOf('#');
        final String id = hash < 0 ? target : target.substring(0, hash);
        final String path = hash < 0 ? "" : target.substring(hash + 1);
        if (path.isEmpty()) {
            final Optional<DensityNode> density = compiler.density(id, null);
            if (density.isPresent()) {
                return density.get();
            }
            final Optional<Parameter> parameter = compiler.parameter(id, null);
            if (parameter.isPresent()) {
                return parameter.get().density();
            }
            if (compiler.getData().get(Kind.NOISE, id).isPresent()) {
                return noiseNode(id, channel);
            }
            throw new IllegalArgumentException("No usable density, parameter or noise " + id);
        }
        // Compile whichever file it is, then find the node by where it was defined
        compiler.density(id, null);
        compiler.parameter(id, null);
        for (Kind kind :
                new Kind[] {
                    Kind.DENSITY, Kind.PARAMETER, Kind.BIOME, Kind.BLOCK_RULES, Kind.WORLD_TYPE
                }) {
            final Optional<DensityNode> node =
                    compiler.nodeAt(WorldgenCompiler.label(kind, id) + "#" + path);
            if (node.isPresent()) {
                return node.get();
            }
        }
        throw new IllegalArgumentException("No density node at " + target);
    }

    /**
     * The target for the world type's terrain, its {@code density} field.
     *
     * @return The target, like {@code lotomation:overworld#density}.
     */
    public String terrainTarget() {
        return generator.getWorldType().id() + "#density";
    }

    /**
     * A density node by where it was defined.
     *
     * @param origin Its origin, as {@link Trace#origin()} and {@link Bounds#origin()} give it.
     * @return The node.
     */
    public Optional<DensityNode> nodeAt(@NonNull String origin) {
        return compiler.nodeAt(origin);
    }

    /**
     * The target that names a node, from its origin: the origin without its kind.
     *
     * @param origin The origin, like {@code density/lotomation:x#args[1]}.
     * @return The target, like {@code lotomation:x#args[1]}.
     */
    public static String targetOf(@NonNull String origin) {
        final int slash = origin.indexOf('/');
        final int colon = origin.indexOf(':');
        // The kind is a folder name before the ID, which has no slash before its colon
        return slash >= 0 && slash < colon ? origin.substring(slash + 1) : origin;
    }

    /**
     * The data's content hash, for exports to record what made them.
     *
     * @return The hash.
     */
    public long dataHash() {
        return compiler.getData().contentHash();
    }

    /**
     * The world seed.
     *
     * @return The seed.
     */
    public long seed() {
        return compiler.getSeed();
    }

    /**
     * A noise file as a density node.
     *
     * @param id The noise's ID.
     * @param channel The channel, or null for the first.
     * @return The node.
     */
    private DensityNode noiseNode(@NonNull String id, String channel) {
        final Noise noise =
                compiler.noise(
                                new com.ikalagaming.factory.world.gen.data.FieldReader(
                                        "debug", "", id, compiler),
                                null,
                                null)
                        .orElseThrow(() -> new IllegalArgumentException("Noise has errors: " + id));
        final int index = channel == null ? 0 : noise.channels().indexOf(channel);
        if (index < 0) {
            throw new IllegalArgumentException("Noise " + id + " has no channel " + channel);
        }
        return new Sources.NoiseNode(noise, index, -1);
    }

    /**
     * Where a pixel of a slice is in the world.
     *
     * @param plane The plane.
     * @param origin The world position of the top-left pixel.
     * @param scale Blocks per pixel.
     * @param column The pixel's column.
     * @param row The pixel's row.
     * @return The position, as x, y, z.
     */
    public static double[] pixelPosition(
            @NonNull Plane plane, double @NonNull [] origin, double scale, int column, int row) {
        final double across = column * scale;
        final double down = row * scale;
        return switch (plane) {
            case XZ -> new double[] {origin[0] + across, origin[1], origin[2] + down};
            case XY -> new double[] {origin[0] + across, origin[1] - down, origin[2]};
            case ZY -> new double[] {origin[0], origin[1] - down, origin[2] + across};
        };
    }

    /**
     * Sample a target over a 2D slice.
     *
     * @param target The target.
     * @param channel The noise channel, or null.
     * @param plane The plane.
     * @param origin The world position of the top-left pixel.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param scale Blocks per pixel.
     * @return The values, row by row from the top.
     */
    public double[] slice(
            @NonNull String target,
            String channel,
            @NonNull Plane plane,
            double @NonNull [] origin,
            int width,
            int height,
            double scale) {
        final DensityNode node = resolve(target, channel);
        double[] values = new double[width * height];
        for (int row = 0; row < height; ++row) {
            for (int column = 0; column < width; ++column) {
                final double[] p = pixelPosition(plane, origin, scale, column, row);
                values[row * width + column] = node.value(p[0], p[1], p[2], null);
            }
        }
        return values;
    }

    /**
     * The biome at every pixel of a slice.
     *
     * @param plane The plane.
     * @param origin The world position of the top-left pixel.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param scale Blocks per pixel.
     * @return The biome IDs, row by row from the top.
     */
    public String[] biomeSlice(
            @NonNull Plane plane, double @NonNull [] origin, int width, int height, double scale) {
        return categorical(
                SliceImages.BIOME_TARGET, plane, origin, width, height, scale, Stage.BIOMES);
    }

    /**
     * The block at every pixel of a slice, from generated chunks.
     *
     * @param plane The plane.
     * @param origin The world position of the top-left pixel.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param scale Blocks per pixel.
     * @param until The last stage to generate.
     * @return The block names, row by row from the top.
     */
    public String[] blockSlice(
            @NonNull Plane plane,
            double @NonNull [] origin,
            int width,
            int height,
            double scale,
            @NonNull Stage until) {
        return categorical(SliceImages.BLOCKS_TARGET, plane, origin, width, height, scale, until);
    }

    /**
     * Sample a categorical target over a slice.
     *
     * @param target The target.
     * @param plane The plane.
     * @param origin The world position of the top-left pixel.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @param scale Blocks per pixel.
     * @param until The last stage to generate, for blocks.
     * @return The IDs, row by row from the top.
     */
    private String[] categorical(
            @NonNull String target,
            @NonNull Plane plane,
            double @NonNull [] origin,
            int width,
            int height,
            double scale,
            @NonNull Stage until) {
        final SliceImages.Request request =
                new SliceImages.Request(target, null, plane, origin, width, height, scale, until);
        final SliceImages.Samples samples = SliceImages.Samples.allocate(request);
        SliceImages.sampleRows(this, request, null, 0, height, new HashMap<>(), samples);
        return samples.ids();
    }

    /**
     * The value of every node in a target's tree at one point: why a block is solid.
     *
     * @param target The target.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
     * @return The tree.
     */
    public Trace trace(@NonNull String target, double x, double y, double z) {
        return trace(resolve(target, null), new double[] {x, y, z});
    }

    /**
     * Trace a node at a point.
     *
     * @param node The node.
     * @param point Where it is evaluated, or null if it isn't evaluated at a single point.
     * @return Its trace.
     */
    private Trace trace(@NonNull DensityNode node, double[] point) {
        final double value =
                point == null ? Double.NaN : node.value(point[0], point[1], point[2], null);
        final Box around =
                point == null
                        ? new Box(0, 0, 0, 0, 0, 0)
                        : new Box(
                                point[0],
                                point[1],
                                point[2],
                                point[0] + 1,
                                point[1] + 1,
                                point[2] + 1);
        List<Trace> children = new ArrayList<>();
        final List<DensityNode> kids = node.children();
        for (int i = 0; i < kids.size(); ++i) {
            final double[] childPoint =
                    point == null ? null : node.childPoint(i, point[0], point[1], point[2], null);
            children.add(trace(kids.get(i), childPoint));
        }
        return new Trace(
                compiler.origin(node),
                node.type(),
                value,
                point == null ? Interval.EVERYTHING : node.bounds(around),
                children);
    }

    /**
     * The range each node in a target's tree reports over a box, to check the all-air and all-solid
     * shortcut is working.
     *
     * @param target The target.
     * @param box The box.
     * @return The tree.
     */
    public Bounds bounds(@NonNull String target, @NonNull Box box) {
        return bounds(resolve(target, null), box);
    }

    /**
     * The bounds tree of a node.
     *
     * @param node The node.
     * @param box The box it sees.
     * @return Its bounds tree.
     */
    private Bounds bounds(@NonNull DensityNode node, @NonNull Box box) {
        List<Bounds> children = new ArrayList<>();
        final List<DensityNode> kids = node.children();
        for (int i = 0; i < kids.size(); ++i) {
            children.add(bounds(kids.get(i), node.childBox(i, box)));
        }
        return new Bounds(compiler.origin(node), node.type(), node.bounds(box), children);
    }

    /**
     * Why a point has the biome it has: every parameter's value, and every biome's fit.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
     * @return The report.
     */
    public BiomeReport biomeAt(double x, double y, double z) {
        // Biomes are chosen per cell, from the parameters at the cell's center
        final long cellX = (long) StrictMath.floor(x / Chunk.BIOME_CELL_SIZE);
        final long cellY = (long) StrictMath.floor(y / Chunk.BIOME_CELL_SIZE);
        final long cellZ = (long) StrictMath.floor(z / Chunk.BIOME_CELL_SIZE);
        final double half = Chunk.BIOME_CELL_SIZE / 2.0;
        final BiomeSelector selector = generator.getBiomes();
        final double[] values =
                selector.parameters(
                        cellX * Chunk.BIOME_CELL_SIZE + half,
                        cellY * Chunk.BIOME_CELL_SIZE + half,
                        cellZ * Chunk.BIOME_CELL_SIZE + half,
                        null);
        Map<String, Double> byId = new LinkedHashMap<>();
        final List<Parameter> parameters = generator.getWorldType().parameters();
        for (int i = 0; i < values.length; ++i) {
            byId.put(parameters.get(i).id(), values[i]);
        }
        return new BiomeReport(
                byId,
                selector.rank(values, cellX, cellY, cellZ),
                selector.choose(values, cellX, cellY, cellZ).id());
    }

    /**
     * Generate a chunk up to a stage.
     *
     * @param pos The chunk.
     * @param until The last stage.
     * @return The chunk.
     */
    public Chunk generate(@NonNull ChunkPos pos, @NonNull Stage until) {
        return generator.generate(pos, until);
    }

    /**
     * The name of empty space, for views that draw it differently.
     *
     * @return The air block's name.
     */
    public static String airName() {
        return World.AIR_NAME;
    }
}
