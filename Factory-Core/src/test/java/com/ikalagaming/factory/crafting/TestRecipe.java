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

    private ItemStack expectedInputItem;
    private Ingredient ingredientInputItem;
    private ItemStack expectedOutputItem;
    private Ingredient ingredientOutputItem;
    private String expectedMachine;
    private long expectedTime;

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
    void testTime() {
        var result = Recipe.builder().withTime(expectedTime).build().time();

        assertEquals(expectedTime, result);
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
}
