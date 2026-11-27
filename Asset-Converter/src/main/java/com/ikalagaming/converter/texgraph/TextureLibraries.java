package com.ikalagaming.converter.texgraph;

/** The node libraries texture graphs are made from. */
public final class TextureLibraries {
    /** Not made. */
    private TextureLibraries() {}

    /**
     * The standard library: the core nodes, the Outputs and the parameter nodes.
     *
     * @return A new library.
     */
    public static TextureLibrary standard() {
        final TextureLibrary library = new TextureLibrary();
        CoreNodes.register(library);
        OutputNodes.register(library);
        ParameterNodes.register(library);
        return library;
    }
}
