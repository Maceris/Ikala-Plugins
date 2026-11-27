package com.ikalagaming.converter.texgraph;

import com.ikalagaming.graphics.ui.graph.model.Graph;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes a subgraph from part of a graph: the chosen nodes go to a subgraph file, keeping their IDs,
 * with an Input node for each value flowing in and an Output node for each flowing out, and a node
 * placing the file takes their place, linked as they were. As the subgraph runs with the graph's
 * seed and reads its parameters by name, the graph makes the same pixels after as before.
 */
public final class SubgraphMaker {
    /** How far left of the chosen nodes the Input nodes stand, and right the Output nodes. */
    private static final float MARGIN = 220;

    /** How far apart Input or Output nodes stand, top to bottom. */
    private static final float SPACING = 80;

    /** Not made. */
    private SubgraphMaker() {}

    /**
     * Where a placed subgraph's pin connects in the graph.
     *
     * @param pin The subgraph's pin.
     * @param node The node at the other end.
     * @param nodePin That node's pin.
     */
    public record Link(@NonNull String pin, @NonNull String node, @NonNull String nodePin) {}

    /**
     * A subgraph made, and how to put it in the graph.
     *
     * @param type The node type placing it, from the graph's folder.
     * @param nodes The nodes it took.
     * @param x Where the node placing it goes across.
     * @param y Where it goes down.
     * @param inputs What links into its inputs.
     * @param outputs What its outputs link to.
     */
    public record Made(
            @NonNull String type,
            @NonNull Set<String> nodes,
            float x,
            float y,
            @NonNull List<Link> inputs,
            @NonNull List<Link> outputs) {}

    /**
     * Write a subgraph file from part of a graph. The graph isn't changed; {@link #replace} puts
     * the subgraph in the nodes' place.
     *
     * @param texture The graph.
     * @param folder Its file's folder.
     * @param ids The nodes to take.
     * @param target The subgraph file to write.
     * @return The subgraph made.
     * @throws IllegalArgumentException If the nodes can't make a subgraph, like a value of a type a
     *     subgraph can't take.
     * @throws IOException If the file can't be written.
     */
    public static Made make(
            @NonNull TextureGraph texture,
            @NonNull Path folder,
            @NonNull Set<String> ids,
            @NonNull Path target)
            throws IOException {
        final Graph graph = texture.getGraph();
        final Set<String> taken = new LinkedHashSet<>();
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float sumY = 0;
        for (String id : ids) {
            final Graph.Node node = graph.node(id);
            if (node == null || OutputNodes.isOutput(node.getType())) {
                continue;
            }
            taken.add(id);
            minX = Math.min(minX, node.getX());
            maxX = Math.max(maxX, node.getX());
            sumY += node.getY();
        }
        if (taken.isEmpty()) {
            throw new IllegalArgumentException(
                    "Choose nodes, other than the Output, to make it of");
        }
        // A node outside fed by the chosen ones and feeding them would make a loop through it
        final Set<String> below = new LinkedHashSet<>();
        final Set<String> above = new LinkedHashSet<>();
        for (String id : taken) {
            below.addAll(graph.downstream(id));
            above.addAll(graph.upstream(id));
        }
        below.removeAll(taken);
        below.retainAll(above);
        if (!below.isEmpty()) {
            throw new IllegalArgumentException(
                    "The chosen nodes feed "
                            + below.iterator().next()
                            + ", which feeds them back; choose it too, or fewer nodes");
        }
        final float middleY = sumY / taken.size();
        final TextureGraph sub = new TextureGraph(texture.getLibrary());
        sub.setWidth(texture.getWidth());
        sub.setHeight(texture.getHeight());
        sub.setSeed(texture.getSeed());
        final Graph inner = sub.getGraph();
        inner.restore(graph.part(taken));

        // A value flowing in from one output becomes one Input, named for where it goes first
        final Map<String, String> inputOf = new LinkedHashMap<>();
        final List<Link> inputs = new ArrayList<>();
        final Set<String> names = new LinkedHashSet<>();
        for (Graph.Link link : graph.links()) {
            if (!taken.contains(link.toNode()) || taken.contains(link.fromNode())) {
                continue;
            }
            final String from = link.fromNode() + "." + link.fromPin();
            String name = inputOf.get(from);
            if (name == null) {
                final String type = graph.effectiveType(link.fromNode(), link.fromPin(), false);
                checkType(type, from);
                name = unique(link.toPin(), names);
                inputOf.put(from, name);
                final String id = uniqueId("input_" + name, graph, inner);
                inner.put(
                        id,
                        SubgraphNodes.INPUT_PREFIX + type,
                        minX - MARGIN,
                        middleY + (inputOf.size() - 1) * SPACING);
                inner.setParam(id, SubgraphNodes.NAME, name);
                inputs.add(new Link(name, link.fromNode(), link.fromPin()));
            }
            inner.link(
                    inputIdOf(inner, name), SubgraphNodes.INPUT_OUT, link.toNode(), link.toPin());
        }

        // A value flowing out of one output becomes one Output
        final Map<String, String> outputOf = new LinkedHashMap<>();
        final List<Link> outputs = new ArrayList<>();
        names.clear();
        for (Graph.Link link : graph.links()) {
            if (!taken.contains(link.fromNode()) || taken.contains(link.toNode())) {
                continue;
            }
            final String from = link.fromNode() + "." + link.fromPin();
            String name = outputOf.get(from);
            if (name == null) {
                final String type = graph.effectiveType(link.fromNode(), link.fromPin(), false);
                checkType(type, from);
                name = unique(link.fromPin(), names);
                outputOf.put(from, name);
                final String id = uniqueId("output_" + name, graph, inner);
                inner.put(
                        id,
                        SubgraphNodes.OUTPUT_PREFIX + type,
                        maxX + MARGIN,
                        middleY + (outputOf.size() - 1) * SPACING);
                inner.setParam(id, SubgraphNodes.NAME, name);
                inner.link(link.fromNode(), link.fromPin(), id, SubgraphNodes.OUTPUT_IN);
            }
            outputs.add(new Link(name, link.toNode(), link.toPin()));
        }

        // The graph's own parameters the nodes read, which a node placing it then reads by name
        final Set<String> read = new LinkedHashSet<>();
        for (String id : taken) {
            final Graph.Node node = graph.node(id);
            final String type = node.getType();
            if (type.startsWith(ParameterNodes.PARAMETER_PREFIX)
                    || type.startsWith(ParameterNodes.SWITCH_PREFIX)) {
                read.add(String.valueOf(graph.param(id, ParameterNodes.NAME)).trim());
            }
            final TextureNode does = texture.getLibrary().get(type);
            if (does != null) {
                read.addAll(does.parametersRead(Map.of()));
            }
        }
        for (String name : read) {
            final GraphParameter parameter = texture.parameter(name);
            if (parameter != null) {
                sub.putParameter(parameter);
            }
        }
        final Path targetFolder = target.toAbsolutePath().normalize().getParent();
        final List<String> shared = new ArrayList<>();
        for (String file : texture.getShared()) {
            shared.add(
                    targetFolder
                            .relativize(folder.resolve(file).toAbsolutePath().normalize())
                            .toString()
                            .replace('\\', '/'));
        }
        sub.setShared(shared);

        // Subgraphs it places are named from the new file's folder
        String text = sub.write();
        for (String id : taken) {
            final String type = graph.node(id).getType();
            final Path placed = SubgraphNodes.fileOf(folder, type);
            if (placed != null) {
                text = text.replace(type, SubgraphNodes.typeFor(targetFolder, placed));
            }
        }
        Files.writeString(target, text);
        return new Made(
                SubgraphNodes.typeFor(folder, target),
                taken,
                (minX + maxX) / 2,
                middleY,
                inputs,
                outputs);
    }

    /**
     * Put a made subgraph in its nodes' place: take them out, add a node placing it, and link it as
     * they were. Its node type must be registered; see {@link SubgraphNodes#refresh}.
     *
     * @param graph The graph.
     * @param made The subgraph made.
     * @return The new node's ID.
     */
    public static String replace(@NonNull Graph graph, @NonNull Made made) {
        graph.remove(made.nodes());
        final String id = graph.add(made.type(), made.x(), made.y()).getId();
        for (Link link : made.inputs()) {
            graph.link(link.node(), link.nodePin(), id, link.pin());
        }
        for (Link link : made.outputs()) {
            graph.link(id, link.pin(), link.node(), link.nodePin());
        }
        return id;
    }

    /**
     * Check a subgraph can carry a pin type.
     *
     * @param type The type.
     * @param from The pin, for the message.
     */
    private static void checkType(String type, String from) {
        if (!SubgraphNodes.PIN_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    from + " is a " + type + ", which a subgraph can't take or give");
        }
    }

    /**
     * A name not taken yet, the wanted one or it with a number.
     *
     * @param wanted The name wanted.
     * @param taken Names taken, which gets it.
     * @return The name.
     */
    private static String unique(String wanted, Set<String> taken) {
        String name = wanted;
        for (int i = 2; taken.contains(name); ++i) {
            name = wanted + i;
        }
        taken.add(name);
        return name;
    }

    /**
     * A node ID neither graph uses.
     *
     * @param wanted The ID wanted.
     * @param graph The graph.
     * @param inner The subgraph.
     * @return The ID.
     */
    private static String uniqueId(String wanted, Graph graph, Graph inner) {
        final String clean = wanted.replaceAll("[^A-Za-z0-9_]", "_");
        String id = clean;
        for (int i = 2; graph.node(id) != null || inner.node(id) != null; ++i) {
            id = clean + i;
        }
        return id;
    }

    /**
     * The Input node with a name.
     *
     * @param inner The subgraph.
     * @param name The name.
     * @return Its ID.
     */
    private static String inputIdOf(Graph inner, String name) {
        for (Graph.Node node : inner.nodes()) {
            if (node.getType().startsWith(SubgraphNodes.INPUT_PREFIX)
                    && name.equals(inner.param(node.getId(), SubgraphNodes.NAME))) {
                return node.getId();
            }
        }
        throw new IllegalStateException("No input " + name);
    }
}
