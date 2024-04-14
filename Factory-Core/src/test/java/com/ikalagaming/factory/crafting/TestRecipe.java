package com.ikalagaming.factory.crafting;

import static com.ikalagaming.factory.crafting.PowerConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.item.Item;
import com.ikalagaming.factory.item.ItemStack;
import com.ikalagaming.localization.Localization;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.ResourceBundle;

class TestRecipe {

    private static MockedStatic<FactoryPlugin> fakePlugin;

    /** Set up before all the tests. */
    @BeforeAll
    static void setUpBeforeClass() {
        var bundle =
                ResourceBundle.getBundle(
                        "com.ikalagaming.factory.strings", Localization.getLocale());

        fakePlugin = Mockito.mockStatic(FactoryPlugin.class);
        fakePlugin.when(FactoryPlugin::getResourceBundle).thenReturn(bundle);
    }

    /** Tear down after all the tests. */
    @AfterAll
    static void tearDownAfterClass() {
        fakePlugin.close();
    }

    private ItemStack expectedInputItem;
    private Ingredient ingredientInputItem;
    private ItemStack expectedOutputItem;
    private Ingredient ingredientOutputItem;

    private String expectedMachine;

    private long expectedTime;

    @BeforeEach
    void setup() {
        expectedInputItem = new ItemStack(new Item("lotomation:sandwich"));
        ingredientInputItem = new InputItem(expectedInputItem);
        expectedOutputItem = new ItemStack(new Item("lotomation:sword"));
        ingredientOutputItem = new OutputItem(expectedOutputItem);
        expectedMachine = "lotomation:hand";
        expectedTime = 1;
    }

    @Test
    void testAndIngredientForMachine() {
        var builder = Recipe.builder().withMachine(expectedMachine);

        assertThrows(IllegalArgumentException.class, () -> builder.and(ingredientInputItem));
    }

    @Test
    void testAndIngredientWithoutList() {
        var builder = Recipe.builder();

        assertThrows(UnsupportedOperationException.class, () -> builder.and(ingredientInputItem));
    }

    @Test
    void testAndStringForInputs() {
        var builder = Recipe.builder().withInput(ingredientInputItem);

        assertThrows(IllegalArgumentException.class, () -> builder.and(expectedMachine));
    }

    @Test
    void testAndStringForOutputs() {
        var builder = Recipe.builder().withOutput(ingredientOutputItem);

        assertThrows(IllegalArgumentException.class, () -> builder.and(expectedMachine));
    }

    @Test
    void testAndStringWithoutList() {
        var builder = Recipe.builder();

        assertThrows(UnsupportedOperationException.class, () -> builder.and(expectedMachine));
    }

    @Test
    void testInputs() {
        IngredientFluid fluid = new IngredientFluid("lotomation:water", 1000);
        IngredientPower power = new IngredientPower(KILOJOULE);

        var result =
                Recipe.builder()
                        .withInputs(List.of(ingredientInputItem, fluid, power))
                        .build()
                        .inputs();

        assertEquals(3, result.size());
        assertTrue(result.contains(ingredientInputItem));
        assertTrue(result.contains(fluid));
        assertTrue(result.contains(power));
    }

    @Test
    void testInputsAndWithOutputType() {
        var builder = Recipe.builder().withInput(ingredientInputItem);

        assertThrows(IllegalArgumentException.class, () -> builder.and(ingredientOutputItem));
    }

    @Test
    void testInputsWithAnd() {
        IngredientFluid fluid = new IngredientFluid("lotomation:water", 1000);
        IngredientPower power = new IngredientPower(KILOJOULE);

        var result =
                Recipe.builder()
                        .withInput(ingredientInputItem)
                        .and(fluid)
                        .and(power)
                        .build()
                        .inputs();

        assertEquals(3, result.size());
        assertTrue(result.contains(ingredientInputItem));
        assertTrue(result.contains(fluid));
        assertTrue(result.contains(power));
    }

    @Test
    void testInputsWithOutputType() {
        var builder = Recipe.builder();

        var invalidInput = List.of(ingredientOutputItem);

        assertThrows(IllegalArgumentException.class, () -> builder.withInputs(invalidInput));
    }

    @Test
    void testInputWithOutputType() {
        var builder = Recipe.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.withInput(ingredientOutputItem));
    }

    @Test
    void testMachines() {
        var machineTwo = "lotomation:crusher";
        var expectedResult = List.of(expectedMachine, machineTwo);
        var result =
                Recipe.builder()
                        .withMachines(List.of(expectedMachine, machineTwo))
                        .build()
                        .machines();

        assertNotNull(result);
        assertArrayEquals(expectedResult.toArray(), result.toArray());
    }

    @Test
    void testMachinesWithAnd() {
        var machineTwo = "lotomation:crusher";
        var expectedResult = List.of(expectedMachine, machineTwo);
        var result =
                Recipe.builder().withMachine(expectedMachine).and(machineTwo).build().machines();

        assertNotNull(result);
        assertArrayEquals(expectedResult.toArray(), result.toArray());
    }

    @Test
    void testOutputs() {
        IngredientFluid fluid = new IngredientFluid("lotomation:water", 1000);
        IngredientPower power = new IngredientPower(KILOJOULE);

        var result =
                Recipe.builder()
                        .withInput(ingredientInputItem)
                        .withOutputs(List.of(ingredientOutputItem, fluid, power))
                        .build()
                        .outputs();

        assertEquals(3, result.size());
        assertTrue(result.contains(ingredientOutputItem));
        assertTrue(result.contains(fluid));
        assertTrue(result.contains(power));
    }

    @Test
    void testOutputsAndWithInputType() {
        var builder = Recipe.builder().withOutput(ingredientOutputItem);

        assertThrows(IllegalArgumentException.class, () -> builder.and(ingredientInputItem));
    }

    @Test
    void testOutputsWithAnd() {
        IngredientFluid fluid = new IngredientFluid("lotomation:water", 1000);
        IngredientPower power = new IngredientPower(KILOJOULE);

        var result =
                Recipe.builder()
                        .withInput(ingredientInputItem)
                        .withOutput(ingredientOutputItem)
                        .and(fluid)
                        .and(power)
                        .build()
                        .outputs();

        assertEquals(3, result.size());
        assertTrue(result.contains(ingredientOutputItem));
        assertTrue(result.contains(fluid));
        assertTrue(result.contains(power));
    }

    @Test
    void testOutputsWithInputType() {
        var builder = Recipe.builder();

        var invalidOutput = List.of(ingredientInputItem);

        assertThrows(IllegalArgumentException.class, () -> builder.withOutputs(invalidOutput));
    }

    @Test
    void testOutputWithInputType() {
        var builder = Recipe.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.withOutput(ingredientInputItem));
    }

    @Test
    void testTime() {
        var result = Recipe.builder().withTime(expectedTime).build().time();

        assertEquals(expectedTime, result);
    }

    @Test
    void testTimeNegative() {
        var builder = Recipe.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.withTime(-1));
    }
}
