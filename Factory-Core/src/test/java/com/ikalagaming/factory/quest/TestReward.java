package com.ikalagaming.factory.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.crafting.IngredientItem;
import com.ikalagaming.factory.crafting.IngredientPower;
import com.ikalagaming.factory.crafting.Recipe;
import com.ikalagaming.localization.Localization;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.ResourceBundle;

/**
 * Tests for rewards.
 *
 * @author Ches Burks
 */
class TestReward {

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

    @Test
    void testRewardChoice() {
        var items = List.of("lotomation:foo", "lotomation:bar");

        var result = new RewardChoice(items);

        assertEquals(items, result.getItems());
    }

    @Test
    void testRewardCommand() {
        var script = "Lotomation.shutDown();";

        var result = new RewardCommand(script);

        assertEquals(script, result.getScript());
    }

    @Test
    void testRewardItem() {
        var item = "lotomation:foo";

        var result = new RewardItem(item);

        assertEquals(item, result.getItemName());
    }

    @Test
    void testRewardRecipe() {
        var input = new IngredientItem("coal");
        var output = new IngredientPower(50);
        var recipe = new Recipe(List.of(input), List.of(output));

        var result = new RewardRecipe(recipe);

        assertEquals(recipe, result.getRecipe());
    }
}
