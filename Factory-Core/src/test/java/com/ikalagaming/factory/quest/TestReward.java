package com.ikalagaming.factory.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.crafting.IngredientPower;
import com.ikalagaming.factory.crafting.OutputItem;
import com.ikalagaming.factory.crafting.Recipe;
import com.ikalagaming.factory.item.Item;
import com.ikalagaming.factory.item.ItemStack;
import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.localization.Localization;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

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
        var item1 = new Item("lotomation:foo");
        var item2 = new Item("lotomation:bar");
        var choice1 = new ItemStack(item1, 10);
        var choice2 = new ItemStack(item2);
        var choices = List.of(choice1, choice2);

        var result = new RewardChoice(choices);

        assertEquals(choices, result.getChoices());
    }

    @Test
    void testRewardCommand() {
        var script = "Lotomation.shutDown();";

        var result = new RewardCommand(script);

        assertEquals(script, result.getScript());
    }

    @Test
    void testRewardItem() {
        KVT kvt = new Node();
        kvt.addLong("charge", 1000);
        var item =
                Item.builder()
                        .name("lotomation:battery")
                        .material("iron")
                        .tags(Set.of("chargeable", "electronic"))
                        .kvt(kvt)
                        .build();
        var itemStack = new ItemStack(item, 5);

        var result = new RewardItem(itemStack);

        assertEquals(itemStack, result.getItem());
    }

    @Test
    void testRewardRecipe() {
        var item = new Item("lotomation:coal");
        var itemStack = new ItemStack(item);
        var input = new OutputItem(itemStack);
        var output = new IngredientPower(50);

        var recipe =
                Recipe.builder()
                        .withInput(input)
                        .withOutput(output)
                        .withMachine("furnace")
                        .withTime(10)
                        .build();

        var result = new RewardRecipe(recipe);

        assertEquals(recipe, result.getRecipe());
    }
}
