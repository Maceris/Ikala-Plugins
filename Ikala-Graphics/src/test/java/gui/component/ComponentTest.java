package gui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikalagaming.graphics.frontend.gui.component.Component;
import com.ikalagaming.graphics.frontend.gui.util.Alignment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class ComponentTest {

    /** How much precision we expect in float comparisons. */
    private static final float DELTA = 0.001f;

    static Stream<Arguments> displacementAndAlignmentProvider() {
        return Stream.of(
                Arguments.of(Alignment.NORTH_WEST, 0.00f, 0.00f),
                Arguments.of(Alignment.NORTH, 0.45f, 0.00f),
                Arguments.of(Alignment.NORTH_EAST, 0.90f, 0.00f),
                Arguments.of(Alignment.WEST, 0.00f, 0.45f),
                Arguments.of(Alignment.CENTER, 0.45f, 0.45f),
                Arguments.of(Alignment.EAST, 0.90f, 0.45f),
                Arguments.of(Alignment.SOUTH_WEST, 0.00f, 0.90f),
                Arguments.of(Alignment.SOUTH, 0.45f, 0.90f),
                Arguments.of(Alignment.SOUTH_EAST, 0.90f, 0.90f));
    }

    @ParameterizedTest
    @MethodSource("displacementAndAlignmentProvider")
    void testActualDisplacementWithAlignment(
            Alignment alignment, float expectedX, float expectedY) {
        var component = new Component();
        component.setScale(0.10f, 0.10f);
        component.setDisplacement(0.00f, 0.00f);
        component.setAlignment(alignment);

        assertEquals(expectedX, component.getActualDisplaceX(), DELTA);
        assertEquals(expectedY, component.getActualDisplaceY(), DELTA);
    }

    @Test
    void testRootScale() {
        var component = new Component();
        component.setScale(0.50f, 0.50f);

        assertEquals(0.50f, component.getActualWidth(), DELTA);
        assertEquals(0.50f, component.getActualHeight(), DELTA);
    }

    @Test
    void testChildScale() {
        var parent = new Component();
        parent.setScale(0.50f, 0.50f);

        var child = new Component();
        child.setScale(0.50f, 0.50f);
        parent.addChild(child);

        assertEquals(0.25f, child.getActualWidth(), DELTA);
        assertEquals(0.25f, child.getActualHeight(), DELTA);

        child.setParent(null);

        assertEquals(0.50f, child.getActualWidth(), DELTA);
        assertEquals(0.50f, child.getActualHeight(), DELTA);
    }
}
