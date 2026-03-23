package com.ikalagaming.graphics.frontend.gui.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class StyleVariableTest {

    /** How much precision we expect in float comparisons. */
    private static final float DELTA = 0.001f;

    static Stream<Arguments> styleVarProvider() {
        return Stream.of(StyleVariable.values()).map(Arguments::of);
    }

    /** Set up before all the tests. */
    @BeforeAll
    static void setUpBeforeClass() {
        IkGui.createContext();
    }

    /** Tear down after all the tests. */
    @AfterAll
    static void tearDownAfterClass() {
        IkGui.destroyContext();
    }

    @ParameterizedTest
    @MethodSource("styleVarProvider")
    void testSetterAndGetter(StyleVariable variable) {
        final int averageValue = (variable.getMaxValue() + variable.getMinValue()) / 2;
        if (variable.getExpectedType() == Float.class) {
            if (variable.getDimensions() == 1) {
                IkGui.getContext().style.variable.setStyleVarFloat(variable, averageValue);
                assertEquals(averageValue, IkGui.getStyleVarFloat(variable));
            } else if (variable.getDimensions() == 2) {
                IkGui.getContext()
                        .style
                        .variable
                        .setStyleVarFloat2(variable, averageValue, averageValue + 1);
                Vector2f actual = new Vector2f(-10_000, -10_000);
                IkGui.getStyleVarFloat2(variable, actual);
                assertEquals(averageValue, actual.x, DELTA);
                assertEquals(averageValue + 1, actual.y, DELTA);
            } else {
                fail("Unexpected dimensions");
            }
        } else if (variable.getExpectedType() == Integer.class) {
            if (variable.getDimensions() == 1) {
                IkGui.getContext().style.variable.setStyleVarInt(variable, averageValue);
                assertEquals(averageValue, IkGui.getStyleVarInt(variable));
            } else {
                fail("Unexpected dimensions");
            }
        } else {
            fail("Unexpected type");
        }
    }
}
