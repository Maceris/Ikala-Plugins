package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.inventory.Inventory;
import com.ikalagaming.item.Armor;
import com.ikalagaming.item.Component;
import com.ikalagaming.item.Consumable;
import com.ikalagaming.item.ItemCatalog;
import com.ikalagaming.item.ItemRoller;
import com.ikalagaming.item.Junk;
import com.ikalagaming.item.Material;
import com.ikalagaming.item.Quest;
import com.ikalagaming.item.Weapon;
import com.ikalagaming.rpg.utils.ItemRendering;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import lombok.NonNull;
import lombok.Setter;

/**
 * A catalog of items.
 *
 * @author Ches Burks
 *
 */
public class ItemCatalogWindow implements GUIWindow {
	/**
	 * Show inventory full popup.
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

	private Armor demoArmor;
	private Component demoComponent;
	private Consumable demoConsumable;
	private Junk demoJunk;
	private Material demoMaterial;
	private Quest demoQuest;
	private Weapon demoWeapon;

	private ItemCatalog catalog;
	private int currentIndex;

	@Setter
	private Inventory inventory;

	@Override
	public void draw() {
		ImGui.setNextWindowPos(650, 10, ImGuiCond.Once);
		ImGui.setNextWindowSize(600, 800, ImGuiCond.Once);
		ImGui.begin("Item Catalog");

		if (ImGui.beginTabBar("Catalog Bar")) {
			if (ImGui.beginTabItem("Accessory")) {
				if (ImGui.isItemClicked()) {
					this.currentIndex = 0;
				}
				final int maxIndex =
					this.catalog.getAccessoryTemplates().size() - 1;

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
				ImGui.sameLine();
				if (ImGui.button("Roll") && !this.inventory
					.addItem(ItemRoller.rollAccessory(this.catalog
						.getAccessoryTemplates().get(this.currentIndex)))) {
					ImGui.openPopup("Full Inventory");
				}
				ItemCatalogWindow.showInventoryFullPopup();
				ItemRendering.drawAccessoryTemplateInfo(this.catalog
					.getAccessoryTemplates().get(this.currentIndex));
				ImGui.endTabItem();
			}
			if (ImGui.isItemClicked()) {
				this.currentIndex = 0;
			}
			if (ImGui.beginTabItem("Armor")) {
				ItemRendering.drawArmorInfo(this.demoArmor);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem("Component")) {
				ItemRendering.drawComponentInfo(this.demoComponent);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem("Consumable")) {
				ItemRendering.drawConsumableInfo(this.demoConsumable);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem("Junk")) {
				ItemRendering.drawJunkInfo(this.demoJunk);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem("Material")) {
				ItemRendering.drawMaterialInfo(this.demoMaterial);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem("Quest")) {
				ItemRendering.drawQuestInfo(this.demoQuest);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem("Weapon")) {
				ItemRendering.drawWeaponInfo(this.demoWeapon);
				ImGui.endTabItem();
			}
			ImGui.endTabBar();
		}

		ImGui.end();
	}

	@Override
	public void setup(@NonNull Scene scene) {
		this.demoArmor = ItemGenerator.getArmor();
		this.demoComponent = ItemGenerator.getComponent();
		this.demoConsumable = ItemGenerator.getConsumable();
		this.demoJunk = ItemGenerator.getJunk();
		this.demoMaterial = ItemGenerator.getMaterial();
		this.demoQuest = ItemGenerator.getQuest();
		this.demoWeapon = ItemGenerator.getWeapon();
		this.catalog = ItemCatalog.getInstance();
		this.catalog.getAccessoryTemplates()
			.add(ItemGenerator.getAccessoryTemplate());
		this.catalog.getAccessoryTemplates()
			.add(ItemGenerator.getAccessoryTemplate());
		this.catalog.getAccessoryTemplates()
			.add(ItemGenerator.getAccessoryTemplate());

	}
}
