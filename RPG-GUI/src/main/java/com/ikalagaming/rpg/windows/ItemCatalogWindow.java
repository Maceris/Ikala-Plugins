package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.inventory.Inventory;
import com.ikalagaming.item.Affix;
import com.ikalagaming.item.Equipment;
import com.ikalagaming.item.Item;
import com.ikalagaming.item.ItemCatalog;
import com.ikalagaming.item.ItemPersistence;
import com.ikalagaming.item.ItemRoller;
import com.ikalagaming.item.template.AccessoryTemplate;
import com.ikalagaming.item.template.ArmorTemplate;
import com.ikalagaming.item.template.EquipmentTemplate;
import com.ikalagaming.item.template.WeaponTemplate;
import com.ikalagaming.item.testing.ItemGenerator;
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
 *
 */
public class ItemCatalogWindow implements GUIWindow {
	/**
	 * Show inventory full pop-up.
	 */
	private static void showInventoryFullPopup() {
		if (ImGui.beginPopupModal("Full Inventory")) {
			ImGui.text("Inventory Full!");
			if (ImGui.button("I'm sorry")) {
				ImGui.closeCurrentPopup();
			}
			ImGui.endPopup();
		}
	}

	/**
	 * The item catalog, all items in the game.
	 */
	private ItemCatalog catalog;

	/**
	 * The current index in whatever list of items we are looking through. Reset
	 * when we look at a new list.
	 */
	private int currentIndex;

	@Setter
	private Inventory inventory;

	@Override
	public void draw() {
		ImGui.setNextWindowPos(650, 10, ImGuiCond.Once);
		ImGui.setNextWindowSize(700, 800, ImGuiCond.Once);
		ImGui.begin("Item Catalog");

		if (ImGui.beginTabBar("Catalog Bar")) {
			if (ImGui.beginTabItem("Accessory Template")) {
				if (ImGui.isItemClicked()) {
					this.currentIndex = 0;
				}
				AccessoryTemplate template =
					this.catalog.getAccessoryTemplates().get(this.currentIndex);
				this.drawRollable(this.catalog.getAccessoryTemplates(),
					ItemRoller.rollAccessory(template));
				ItemRendering.drawAccessoryTemplateInfo(template);
				ImGui.endTabItem();
			}
			if (ImGui.isItemClicked()) {
				this.currentIndex = 0;
			}
			if (ImGui.beginTabItem("Affix")) {
				if (ImGui.isItemClicked()) {
					this.currentIndex = 0;
				}
				List<Affix> affixes = this.catalog.getAffixes();
				this.drawListButtons(affixes);
				ImGui.sameLine();
				ItemRendering.drawAffix(affixes.get(this.currentIndex));
				ImGui.endTabItem();
			}
			if (ImGui.isItemClicked()) {
				this.currentIndex = 0;
			}
			if (ImGui.beginTabItem("Armor Template")) {
				if (ImGui.isItemClicked()) {
					this.currentIndex = 0;
				}
				ArmorTemplate template =
					this.catalog.getArmorTemplates().get(this.currentIndex);
				this.drawRollable(this.catalog.getArmorTemplates(),
					ItemRoller.rollArmor(template));
				ItemRendering.drawArmorTemplateInfo(template);
				ImGui.endTabItem();
			}
			if (ImGui.isItemClicked()) {
				this.currentIndex = 0;
			}
			if (ImGui.beginTabItem("Component")) {
				if (ImGui.isItemClicked()) {
					this.currentIndex = 0;
				}
				this.drawStatic(this.catalog.getComponents());
				ItemRendering.drawComponentInfo(
					this.catalog.getComponents().get(this.currentIndex));
				ImGui.endTabItem();
			}
			if (ImGui.isItemClicked()) {
				this.currentIndex = 0;
			}
			if (ImGui.beginTabItem("Consumable")) {
				if (ImGui.isItemClicked()) {
					this.currentIndex = 0;
				}
				this.drawStatic(this.catalog.getConsumables());
				ItemRendering.drawConsumableInfo(
					this.catalog.getConsumables().get(this.currentIndex));
				ImGui.endTabItem();
			}
			if (ImGui.isItemClicked()) {
				this.currentIndex = 0;
			}
			if (ImGui.beginTabItem("Junk")) {
				if (ImGui.isItemClicked()) {
					this.currentIndex = 0;
				}
				this.drawStatic(this.catalog.getJunk());
				ItemRendering.drawJunkInfo(
					this.catalog.getJunk().get(this.currentIndex));
				ImGui.endTabItem();
			}
			if (ImGui.isItemClicked()) {
				this.currentIndex = 0;
			}
			if (ImGui.beginTabItem("Material")) {
				if (ImGui.isItemClicked()) {
					this.currentIndex = 0;
				}
				this.drawStatic(this.catalog.getMaterials());
				ItemRendering.drawMaterialInfo(
					this.catalog.getMaterials().get(this.currentIndex));
				ImGui.endTabItem();
			}
			if (ImGui.isItemClicked()) {
				this.currentIndex = 0;
			}
			if (ImGui.beginTabItem("Quest")) {
				if (ImGui.isItemClicked()) {
					this.currentIndex = 0;
				}
				this.drawStatic(this.catalog.getQuests());
				ItemRendering.drawQuestInfo(
					this.catalog.getQuests().get(this.currentIndex));
				ImGui.endTabItem();
			}
			if (ImGui.isItemClicked()) {
				this.currentIndex = 0;
			}
			if (ImGui.beginTabItem("Weapon Template")) {
				if (ImGui.isItemClicked()) {
					this.currentIndex = 0;
				}
				WeaponTemplate template =
					this.catalog.getWeaponTemplates().get(this.currentIndex);
				this.drawRollable(this.catalog.getArmorTemplates(),
					ItemRoller.rollWeapon(template));
				ItemRendering.drawWeaponTemplateInfo(template);
				ImGui.endTabItem();
			}
			if (ImGui.isItemClicked()) {
				this.currentIndex = 0;
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

		final boolean decreaseDisabled = this.currentIndex <= 0;
		if (decreaseDisabled) {
			ImGui.beginDisabled();
		}
		if (ImGui.arrowButton("Decr ID", 0)) {
			--this.currentIndex;
		}
		if (decreaseDisabled) {
			ImGui.endDisabled();
		}

		ImGui.sameLine();
		ImGui.text(this.currentIndex + "/" + maxIndex);
		ImGui.sameLine();

		final boolean increaseDisabled = this.currentIndex >= maxIndex;
		if (increaseDisabled) {
			ImGui.beginDisabled();
		}
		if (ImGui.arrowButton("Incr ID", 1)) {
			++this.currentIndex;
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
	private <T extends EquipmentTemplate, Q extends Equipment> void
		drawRollable(List<T> items, Q rolled) {
		this.drawListButtons(items);
		ImGui.sameLine();
		if (ImGui.button("Roll")) {
			this.tryAddingItem(rolled);
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
			this.tryAddingItem(items.get(this.currentIndex));
		}
		ItemCatalogWindow.showInventoryFullPopup();
	}

	@Override
	public void setup(@NonNull Scene scene) {
		this.catalog = ItemCatalog.getInstance();

		ItemPersistence persist = new ItemPersistence();

		persist.loadContext();
		persist.fetchAffix();

		this.catalog.getAccessoryTemplates()
			.add(ItemGenerator.getAccessoryTemplate());
		this.catalog.getAccessoryTemplates()
			.add(ItemGenerator.getAccessoryTemplate());
		this.catalog.getAccessoryTemplates()
			.add(ItemGenerator.getAccessoryTemplate());

		this.catalog.getArmorTemplates().add(ItemGenerator.getArmorTemplate());
		this.catalog.getComponents().add(ItemGenerator.getComponent());
		this.catalog.getConsumables().add(ItemGenerator.getConsumable());
		this.catalog.getJunk().add(ItemGenerator.getJunk());
		this.catalog.getMaterials().add(ItemGenerator.getMaterial());
		this.catalog.getQuests().add(ItemGenerator.getQuest());
		this.catalog.getWeaponTemplates()
			.add(ItemGenerator.getWeaponTemplate());

	}

	/**
	 * Try to add an item to the inventory, and show a pop-up if it can't.
	 *
	 * @param item The item to try adding.
	 */
	private void tryAddingItem(Item item) {
		if (!this.inventory.addItem(item)) {
			ImGui.openPopup("Full Inventory");
		}
	}
}
