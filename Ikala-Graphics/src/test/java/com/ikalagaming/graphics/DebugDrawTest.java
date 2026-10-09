package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ikalagaming.graphics.scene.debug.DebugLine;
import com.ikalagaming.graphics.scene.debug.DebugShape;

import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class DebugDrawTest {

    private GraphicsContext context;

    private static DebugShape line(double x) {
        return new DebugLine(new Vector3d(x, 0, 0), new Vector3d(x, 1, 0), 0xFFFFFFFF);
    }

    private List<DebugShape> collect() {
        List<DebugShape> shapes = new ArrayList<>();
        context.collectDebugShapes(shapes::add);
        return shapes;
    }

    @BeforeEach
    void setUp() {
        context = new GraphicsContext("Test-Plugin", null);
    }

    @Test
    void immediateShapesLastOneFrame() {
        context.debug().draw(line(1));
        context.debug().draw(line(2));

        assertEquals(2, collect().size());
        assertEquals(0, collect().size(), "Gone after the frame they were drawn in");
    }

    @Test
    void persistentShapesStayUntilRemoved() {
        DebugShapeHandle first = context.debug().add(line(1));
        context.debug().add(line(2));

        assertEquals(2, collect().size());
        assertEquals(2, collect().size());

        context.debug().remove(first);
        assertEquals(List.of(line(2)), collect());

        context.debug().clear();
        assertEquals(0, collect().size());
    }

    @Test
    void closingTheContextDropsEverything() {
        context.debug().add(line(1));
        context.debug().draw(line(2));

        context.close();

        assertEquals(0, collect().size());
        assertThrows(IllegalStateException.class, () -> context.debug());
    }
}
