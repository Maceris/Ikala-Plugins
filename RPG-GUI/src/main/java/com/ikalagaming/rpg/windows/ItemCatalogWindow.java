package com.ikalagaming.rpg.windows;

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

import imgui.ImGui;
import imgui.flag.ImGuiCond;
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
        if (ImGui.beginPopupModal("Full Inventory")) {
            ImGui.text("Inventory Full!");
            if (ImGui.button("I'm sorry")) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
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
        ImGui.setNextWindowPos(650, 10, ImGuiCond.Once);
        ImGui.setNextWindowSize(700, 800, ImGuiCond.Once);
        ImGui.begin("Item Catalog");

        if (ImGui.beginTabBar("Catalog Bar")) {
            if (ImGui.beginTabItem("Accessory Template")) {
                if (ImGui.isItemClicked()) {
                    currentIndex = 0;
                }
                AccessoryTemplate template = catalog.getAccessoryTemplates().get(currentIndex);
                this.drawRollable(
                        catalog.getAccessoryTemplates(), ItemRoller.rollAccessory(template));
                ItemRendering.drawAccessoryTemplateInfo(template);
                ImGui.endTabItem();
            }
            if (ImGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (ImGui.beginTabItem("Affix")) {
                if (ImGui.isItemClicked()) {
                    currentIndex = 0;
                }
                List<Affix> affixes = catalog.getAffixes();
                this.drawListButtons(affixes);
                ImGui.sameLine();
                ItemRendering.drawAffix(affixes.get(currentIndex));
                ImGui.endTabItem();
            }
            if (ImGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (ImGui.beginTabItem("Armor Template")) {
                if (ImGui.isItemClicked()) {
                    currentIndex = 0;
                }
                ArmorTemplate template = catalog.getArmorTemplates().get(currentIndex);
                this.drawRollable(catalog.getArmorTemplates(), ItemRoller.rollArmor(template));
                ItemRendering.drawArmorTemplateInfo(template);
                ImGui.endTabItem();
            }
            if (ImGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (ImGui.beginTabItem("Component")) {
                if (ImGui.isItemClicked()) {
                    currentIndex = 0;
                }
                this.drawStatic(catalog.getComponents());
                ItemRendering.drawComponentInfo(catalog.getComponents().get(currentIndex));
                ImGui.endTabItem();
            }
            if (ImGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (ImGui.beginTabItem("Consumable")) {
                if (ImGui.isItemClicked()) {
                    currentIndex = 0;
                }
                this.drawStatic(catalog.getConsumables());
                ItemRendering.drawConsumableInfo(catalog.getConsumables().get(currentIndex));
                ImGui.endTabItem();
            }
            if (ImGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (ImGui.beginTabItem("Junk")) {
                if (ImGui.isItemClicked()) {
                    currentIndex = 0;
                }
                this.drawStatic(catalog.getJunk());
                ItemRendering.drawJunkInfo(catalog.getJunk().get(currentIndex));
                ImGui.endTabItem();
            }
            if (ImGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (ImGui.beginTabItem("Material")) {
                if (ImGui.isItemClicked()) {
                    currentIndex = 0;
                }
                this.drawStatic(catalog.getMaterials());
                ItemRendering.drawMaterialInfo(catalog.getMaterials().get(currentIndex));
                ImGui.endTabItem();
            }
            if (ImGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (ImGui.beginTabItem("Quest")) {
                if (ImGui.isItemClicked()) {
                    currentIndex = 0;
                }
                this.drawStatic(catalog.getQuests());
                ItemRendering.drawQuestInfo(catalog.getQuests().get(currentIndex));
                ImGui.endTabItem();
            }
            if (ImGui.isItemClicked()) {
                currentIndex = 0;
            }
            if (ImGui.beginTabItem("Weapon Template")) {
                if (ImGui.isItemClicked()) {
                    currentIndex = 0;
                }
                WeaponTemplate template = catalog.getWeaponTemplates().get(currentIndex);
                this.drawRollable(catalog.getArmorTemplates(), ItemRoller.rollWeapon(template));
                ItemRendering.drawWeaponTemplateInfo(template);
                ImGui.endTabItem();
            }
            if (ImGui.isItemClicked()) {
                currentIndex = 0;
            }
            ImGui.endTabBar();
        }

        ImGui.end();
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
            ImGui.beginDisabled();
        }
        if (ImGui.arrowButton("Decr ID", 0)) {
            --currentIndex;
        }
        if (decreaseDisabled) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        ImGui.text(currentIndex + "/" + maxIndex);
        ImGui.sameLine();

        final boolean increaseDisabled = currentIndex >= maxIndex;
        if (increaseDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.arrowButton("Incr ID", 1)) {
            ++currentIndex;
        }
        if (increaseDisabled) {
            ImGui.endDisabled();
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
        ImGui.sameLine();
        if (ImGui.button("Roll")) {
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
        ImGui.sameLine();
        if (ImGui.button("Add")) {
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
            ImGui.openPopup("Full Inventory");
        }
    }
}
