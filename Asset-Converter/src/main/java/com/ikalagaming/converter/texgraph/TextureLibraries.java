package com.ikalagaming.converter.texgraph;

/** The node libraries texture graphs are made from. */
public final class TextureLibraries {
    /** Not made. */
    private TextureLibraries() {}

    /**
     * The standard library: the core nodes, the Outputs, the parameter nodes, patterns, operations,
     * color nodes, the Expression node and subgraphs' Input and Output nodes.
     *
     * @return A new library.
     */
    public static TextureLibrary standard() {
        final TextureLibrary library = new TextureLibrary();
        CoreNodes.register(library);
        OutputNodes.register(library);
        ParameterNodes.register(library);
        PatternNodes.register(library);
        OperationNodes.register(library);
        ColorNodes.register(library);
        Expression.register(library);
        SubgraphNodes.register(library);
        return library;
    }
}
