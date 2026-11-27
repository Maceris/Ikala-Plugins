package com.ikalagaming.graphics.ui.graph.model;

import java.util.List;

/** Small node and pin types for graph tests: scalars, masks, colors and textures. */
public final class TestGraphTypes {
    /** Not made. */
    private TestGraphTypes() {}

    /**
     * Pin types: a mask links into a scalar directly, a scalar into a color through a gray node.
     *
     * @return The types.
     */
    public static PinTypes pins() {
        return new PinTypes()
                .register(new PinTypes.PinType("scalar", "Scalar", 0xFF999999))
                .register(new PinTypes.PinType("mask", "Mask", 0xFFFFFFFF))
                .register(new PinTypes.PinType("color", "Color", 0xFF33CCFF))
                .register(new PinTypes.PinType("texture", "Texture", 0xFFFF66CC))
                .convertDirectly("mask", "scalar")
                .convertWith("scalar", "color", "math/gray");
    }

    /**
     * Node types: constant, add, gray and output.
     *
     * @return The types.
     */
    public static NodeTypes nodes() {
        return new NodeTypes()
                .register(
                        new NodeTypes.NodeType(
                                "math/constant",
                                "Constant",
                                "Math",
                                List.of(),
                                List.of(new NodeTypes.Pin("value", "scalar")),
                                List.of(new NodeTypes.Param("value", "0")),
                                1))
                .register(
                        new NodeTypes.NodeType(
                                "math/add",
                                "Add",
                                "Math",
                                List.of(
                                        new NodeTypes.Pin("a", "scalar"),
                                        new NodeTypes.Pin("b", "scalar")),
                                List.of(new NodeTypes.Pin("sum", "scalar")),
                                List.of(new NodeTypes.Param("b", "0")),
                                2))
                .register(
                        new NodeTypes.NodeType(
                                "math/gray",
                                "Gray",
                                "Convert",
                                List.of(new NodeTypes.Pin("value", "scalar")),
                                List.of(new NodeTypes.Pin("color", "color")),
                                List.of(),
                                1))
                .register(
                        new NodeTypes.NodeType(
                                "out/output",
                                "Output",
                                "Output",
                                List.of(new NodeTypes.Pin("color", "color")),
                                List.of(),
                                List.of(),
                                1));
    }
}
