package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.rpg.inventory.Inventory;
import com.ikalagaming.rpg.item.Affix;
import com.ikalagaming.rpg.item.Equipment;
import com.ikalagaming.rpg.item.Item;
import com.ikalagaming.rpg.item.ItemCatalog;
import com.ikalagaming.rpg.item.ItemPersistence;
import com.ikalagaming.rpg.item.ItemRoller;
import com.ikalagaming.rpg.item.template.AccessoryTemplate;
import com.ikalagaming.rpg.item.template.ArmorTemplate;
import com.ikalagaming.rpg.item.template.EquipmentTemplate;
import com.ikalagaming.rpg.item.template.WeaponTemplate;
import com.ikalagaming.rpg.item.testing.ItemGenerator;
import com.ikalagaming.rpg.utils.ItemRendering;

import lombok.NonNull;
import lombok.Setter;

import java.util.List;

/**
 * A catalog of items.
 *
 * @author Ches Burks
 */
public class ItemCatalogWindow implements GUIWindow {
    /** Show inventory full pop-up. */
    private static void showInventoryFullPopup() {
        if (IkGui.beginPopupModal("Full Inventory")) {
            IkGui.text("Inventory Full!");
            if (IkGui.button("I'm sorry")) {
                IkGui.closeCurrentPopup();
            }
            IkGui.endPopup();
        }
    }

    /** The item catalog, all items in the game. */
    private ItemCatalog catalog;

    /**
     * The current index in whatever list of items we are looking through. Reset when we look at a
     * new list.
     */
    private int currentIndex;

    @Setter private Inventory inventory;

    @Override
    public void draw() {
        IkGui.setNextWindowPos(650, 10, Condition.ONCE);
        IkGui.setNextWindowSize(700, 800, Condition.ONCE);
        IkGui.begin("Item Catalog");

        if (IkGui.beginTabBar("Catalog Bar")) {
            if (IkGui.beginTabItem("Accessory Template")) {
                if (IkGui.isItemClicked()) {
                    currentIndex = 0;
                }
                AccessoryTemplate template = catalog.getAccessoryTemplates().get(currentIndex);
                this.drawRollable(
                        catalog.getAccessoryTemplates(), ItemRoller.rollAccessory(template));
                ItemRendering.drawAccessoryTemplateInfo(template);
                IkGui.endTabItem();
            }
            if (IkGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (IkGui.beginTabItem("Affix")) {
                if (IkGui.isItemClicked()) {
                    currentIndex = 0;
                }
                List<Affix> affixes = catalog.getAffixes();
                this.drawListButtons(affixes);
                IkGui.sameLine();
                ItemRendering.drawAffix(affixes.get(currentIndex));
                IkGui.endTabItem();
            }
            if (IkGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (IkGui.beginTabItem("Armor Template")) {
                if (IkGui.isItemClicked()) {
                    currentIndex = 0;
                }
                ArmorTemplate template = catalog.getArmorTemplates().get(currentIndex);
                this.drawRollable(catalog.getArmorTemplates(), ItemRoller.rollArmor(template));
                ItemRendering.drawArmorTemplateInfo(template);
                IkGui.endTabItem();
            }
            if (IkGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (IkGui.beginTabItem("Component")) {
                if (IkGui.isItemClicked()) {
                    currentIndex = 0;
                }
                this.drawStatic(catalog.getComponents());
                ItemRendering.drawComponentInfo(catalog.getComponents().get(currentIndex));
                IkGui.endTabItem();
            }
            if (IkGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (IkGui.beginTabItem("Consumable")) {
                if (IkGui.isItemClicked()) {
                    currentIndex = 0;
                }
                this.drawStatic(catalog.getConsumables());
                ItemRendering.drawConsumableInfo(catalog.getConsumables().get(currentIndex));
                IkGui.endTabItem();
            }
            if (IkGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (IkGui.beginTabItem("Junk")) {
                if (IkGui.isItemClicked()) {
                    currentIndex = 0;
                }
                this.drawStatic(catalog.getJunk());
                ItemRendering.drawJunkInfo(catalog.getJunk().get(currentIndex));
                IkGui.endTabItem();
            }
            if (IkGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (IkGui.beginTabItem("Material")) {
                if (IkGui.isItemClicked()) {
                    currentIndex = 0;
                }
                this.drawStatic(catalog.getMaterials());
                ItemRendering.drawMaterialInfo(catalog.getMaterials().get(currentIndex));
                IkGui.endTabItem();
            }
            if (IkGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (IkGui.beginTabItem("Quest")) {
                if (IkGui.isItemClicked()) {
                    currentIndex = 0;
                }
                this.drawStatic(catalog.getQuests());
                ItemRendering.drawQuestInfo(catalog.getQuests().get(currentIndex));
                IkGui.endTabItem();
            }
            if (IkGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (IkGui.beginTabItem("Weapon Template")) {
                if (IkGui.isItemClicked()) {
                    currentIndex = 0;
                }
                WeaponTemplate template = catalog.getWeaponTemplates().get(currentIndex);
                this.drawRollable(catalog.getArmorTemplates(), ItemRoller.rollWeapon(template));
                ItemRendering.drawWeaponTemplateInfo(template);
                IkGui.endTabItem();
            }
            if (IkGui.isItemClicked()) {
                currentIndex = 0;
            }
            IkGui.endTabBar();
        }

        IkGui.end();
    }

    /**
     * Draw the current index, max index, and buttons to move the current index.
     *
     * @param <T> The type of items we are choosing from.
     * @param items The list of items we are choosing from.
     */
    private <T> void drawListButtons(List<T> items) {
        final int maxIndex = items.size() - 1;

        final boolean decreaseDisabled = currentIndex <= 0;
        if (decreaseDisabled) {
            IkGui.beginDisabled();
        }
        if (IkGui.arrowButton("Decr ID", Direction.LEFT)) {
            --currentIndex;
        }
        if (decreaseDisabled) {
            IkGui.endDisabled();
        }

        IkGui.sameLine();
        IkGui.text(currentIndex + "/" + maxIndex);
        IkGui.sameLine();

        final boolean increaseDisabled = currentIndex >= maxIndex;
        if (increaseDisabled) {
            IkGui.beginDisabled();
        }
        if (IkGui.arrowButton("Incr ID", Direction.RIGHT)) {
            ++currentIndex;
        }
        if (increaseDisabled) {
            IkGui.endDisabled();
        }
    }

    /**
     * Draws the controls for items that can be rolled and have unique stats.
     *
     * @param <T> The type of template we are rolling.
     * @param <Q> The type of item we have rolled.
     * @param items The list of items we can choose from.
     * @param rolled A pre-rolled item.
     */
    private <T extends EquipmentTemplate, Q extends Equipment> void drawRollable(
            List<T> items, Q rolled) {
        this.drawListButtons(items);
        IkGui.sameLine();
        if (IkGui.button("Roll")) {
            tryAddingItem(rolled);
        }
        ItemCatalogWindow.showInventoryFullPopup();
    }

    /**
     * Draw the controls for items that can't be rolled, and are all equivalent.
     *
     * @param <T> The type of item we are dealing with.
     * @param items The list of items we can choose from.
     */
    private <T extends Item> void drawStatic(List<T> items) {
        this.drawListButtons(items);
        IkGui.sameLine();
        if (IkGui.button("Add")) {
            tryAddingItem(items.get(currentIndex));
        }
        ItemCatalogWindow.showInventoryFullPopup();
    }

    @Override
    public void setup(@NonNull Scene scene) {
        catalog = ItemCatalog.getInstance();

        ItemPersistence persist = new ItemPersistence();

        persist.loadContext();
        persist.fetchAffix();

        catalog.getAccessoryTemplates().add(ItemGenerator.getAccessoryTemplate());
        catalog.getAccessoryTemplates().add(ItemGenerator.getAccessoryTemplate());
        catalog.getAccessoryTemplates().add(ItemGenerator.getAccessoryTemplate());

        catalog.getArmorTemplates().add(ItemGenerator.getArmorTemplate());
        catalog.getComponents().add(ItemGenerator.getComponent());
        catalog.getConsumables().add(ItemGenerator.getConsumable());
        catalog.getJunk().add(ItemGenerator.getJunk());
        catalog.getMaterials().add(ItemGenerator.getMaterial());
        catalog.getQuests().add(ItemGenerator.getQuest());
        catalog.getWeaponTemplates().add(ItemGenerator.getWeaponTemplate());
    }

    /**
     * Try to add an item to the inventory, and show a pop-up if it can't.
     *
     * @param item The item to try adding.
     */
    private void tryAddingItem(Item item) {
        if (!inventory.addItem(item)) {
            IkGui.openPopup("Full Inventory");
        }
    }
}
