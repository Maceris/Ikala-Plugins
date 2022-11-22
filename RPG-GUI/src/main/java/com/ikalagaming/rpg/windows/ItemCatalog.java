package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.item.Accessory;
import com.ikalagaming.item.Armor;
import com.ikalagaming.item.Component;
import com.ikalagaming.item.Consumable;
import com.ikalagaming.item.Junk;
import com.ikalagaming.item.Material;
import com.ikalagaming.item.Quest;
import com.ikalagaming.item.Weapon;
import com.ikalagaming.rpg.utils.ItemRendering;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import lombok.NonNull;

/**
 * A catalog of items.
 *
 * @author Ches Burks
 *
 */
public class ItemCatalog implements GUIWindow {
	private Accessory demoAccessory;
	private Armor demoArmor;
	private Component demoComponent;
	private Consumable demoConsumable;
	private Junk demoJunk;
	private Material demoMaterial;
	private Quest demoQuest;
	private Weapon demoWeapon;

	@Override
	public void draw() {
		ImGui.setNextWindowPos(650, 10, ImGuiCond.Once);
		ImGui.setNextWindowSize(600, 800, ImGuiCond.Once);
		ImGui.begin("Item Catalog");

		if (ImGui.beginTabBar("Catalog Bar")) {
			if (ImGui.beginTabItem("Accessory")) {
				ItemRendering.drawAccessoryInfo(this.demoAccessory);
				ImGui.endTabItem();
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
		this.demoAccessory = ItemGenerator.getAccessory();
		this.demoArmor = ItemGenerator.getArmor();
		this.demoComponent = ItemGenerator.getComponent();
		this.demoConsumable = ItemGenerator.getConsumable();
		this.demoJunk = ItemGenerator.getJunk();
		this.demoMaterial = ItemGenerator.getMaterial();
		this.demoQuest = ItemGenerator.getQuest();
		this.demoWeapon = ItemGenerator.getWeapon();
	}
}
