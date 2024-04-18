package com.ikalagaming.factory.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.crafting.InputItem;
import com.ikalagaming.factory.crafting.OutputItem;
import com.ikalagaming.factory.crafting.Recipe;
import com.ikalagaming.factory.item.Item;
import com.ikalagaming.factory.item.ItemStack;
import com.ikalagaming.localization.Localization;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestRecipeRegistry {
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

    private RecipeRegistry registry;

    private Recipe expectedRecipe;

    private Recipe secondRecipe;

    @BeforeEach
    void setup() {
        var input = new InputItem(new ItemStack(new Item("lotomation:steel")));
        var output = new OutputItem(new ItemStack(new Item("lotomation:sword")));
        var secondInput = new InputItem(new ItemStack(new Item("lotomation:titanium")));

        registry = new RecipeRegistry();

        expectedRecipe = Recipe.builder().withInput(input).withOutput(output).withTime(60).build();
        secondRecipe =
                Recipe.builder()
                        .withInput(input)
                        .and(secondInput)
                        .withOutput(output)
                        .withTime(60)
                        .build();
    }

    @Test
    void testAdd() {
        var machine = "lotomation:forge";

        registry.add(machine, expectedRecipe);

        var maybeList = registry.find(machine);
        assertTrue(maybeList.isPresent());
        assertTrue(maybeList.get().contains(expectedRecipe));
    }

    @Test
    void testAddDuplicate() {
        var machine = "lotomation:forge";

        registry.add(machine, expectedRecipe);
        registry.add(machine, expectedRecipe);

        var maybeList = registry.find(machine);
        assertTrue(maybeList.isPresent());
        assertTrue(maybeList.get().contains(expectedRecipe));
        assertEquals(1, maybeList.get().size());
    }

    @Test
    void testAddList() {
        var machine1 = "lotomation:forge";
        var machine2 = "lotomation:furnace";
        var machine3 = "lotomation:tool_forge";
        var machines = List.of(machine1, machine2, machine3);

        registry.add(machines, expectedRecipe);

        assertTrue(registry.containsKey(machine1));
        assertTrue(registry.containsKey(machine2));
        assertTrue(registry.containsKey(machine3));
        assertTrue(registry.find(machine1).isPresent());
        assertTrue(registry.find(machine2).isPresent());
        assertTrue(registry.find(machine3).isPresent());
        assertEquals(expectedRecipe, registry.find(machine1).get().get(0));
        assertEquals(expectedRecipe, registry.find(machine2).get().get(0));
        assertEquals(expectedRecipe, registry.find(machine3).get().get(0));
    }

    @Test
    void testAddListDuplicate() {
        var machine = "lotomation:forge";
        var machines = List.of(machine, machine);

        registry.add(machines, expectedRecipe);

        var maybeList = registry.find(machine);
        assertTrue(maybeList.isPresent());
        assertTrue(maybeList.get().contains(expectedRecipe));
        assertEquals(1, maybeList.get().size());
    }

    @Test
    void testAddListNull() {
        var machine = "lotomation:forge";
        List<String> machines = new ArrayList<>();
        machines.add(null);
        machines.add(machine);

        registry.add(machines, expectedRecipe);

        assertTrue(registry.containsKey(machine));
        assertTrue(registry.find(machine).isPresent());
        assertEquals(expectedRecipe, registry.find(machine).get().get(0));
    }

    @Test
    void testContainsKey() {
        var machine = "lotomation:forge";

        registry.add(machine, expectedRecipe);

        assertTrue(registry.containsKey(machine));
        assertFalse(registry.containsKey("lotomation:furnace"));
    }

    @Test
    void testFind() {
        var machine = "lotomation:forge";

        registry.add(machine, expectedRecipe);

        assertTrue(registry.find(machine).isPresent());
        assertFalse(registry.find(machine).get().contains(secondRecipe));
        assertEquals(expectedRecipe, registry.find(machine).get().get(0));

        var missing = registry.find("lotomation:crusher");
        assertNotNull(missing);
        assertFalse(missing.isPresent());
    }

    @Test
    void testGetNames() {
        var machine1 = "lotomation:forge";
        var machine2 = "lotomation:furnace";
        var machine3 = "lotomation:tool_forge";
        var machines = List.of(machine1, machine2, machine3);

        registry.add(machines, expectedRecipe);
        registry.add(machines, secondRecipe);

        var result = registry.getNames();

        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(machine1));
        assertTrue(result.contains(machine2));
        assertTrue(result.contains(machine3));
    }

    @Test
    void testGetValues() {
        var machine1 = "lotomation:forge";
        var machine2 = "lotomation:furnace";
        var machine3 = "lotomation:tool_forge";
        var machines = List.of(machine1, machine2, machine3);

        registry.add(machines, expectedRecipe);
        registry.add(machine3, secondRecipe);

        var result = registry.getValues();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(expectedRecipe));
        assertTrue(result.contains(secondRecipe));
    }
}
