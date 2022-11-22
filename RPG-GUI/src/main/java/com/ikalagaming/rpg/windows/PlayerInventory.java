package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.inventory.Inventory;
import com.ikalagaming.item.Accessory;
import com.ikalagaming.item.Armor;
import com.ikalagaming.item.Component;
import com.ikalagaming.item.Consumable;
import com.ikalagaming.item.Item;
import com.ikalagaming.item.Junk;
import com.ikalagaming.item.Material;
import com.ikalagaming.item.Quest;
import com.ikalagaming.item.Weapon;
import com.ikalagaming.item.enums.ItemType;
import com.ikalagaming.rpg.utils.ItemRendering;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiTableFlags;
import lombok.NonNull;

/**
 * An inventory for items.
 *
 * @author Ches Burks
 *
 */
public class PlayerInventory implements GUIWindow {
	private static int INVENTORY_WIDTH = 10;
	private static int INVENTORY_HEIGHT = 10;
	private InventoryDrag itemDragInfo;
	private Inventory inventory;

	@Override
	public void draw() {
		ImGui.setNextWindowPos(200, 200, ImGuiCond.Once);
		ImGui.setNextWindowSize(610, 600, ImGuiCond.Once);
		ImGui.begin("Inventory");

		if (ImGui.beginTable("InventoryGrid", 10,
			ImGuiTableFlags.SizingFixedFit | ImGuiTableFlags.Borders)) {
			for (int col = 0; col < PlayerInventory.INVENTORY_WIDTH; ++col) {
				ImGui.tableSetupColumn("Column" + col, ImGuiTableFlags.Borders,
					50);
			}
			int position;
			for (int row = 0; row < PlayerInventory.INVENTORY_HEIGHT; ++row) {
				ImGui.tableNextRow();
				for (int col =
					0; col < PlayerInventory.INVENTORY_WIDTH; ++col) {
					ImGui.tableSetColumnIndex(col);
					position = row * PlayerInventory.INVENTORY_WIDTH + col;

					if (this.inventory.hasItem(position)) {
						Item item = this.inventory.getItem(position).get();
						ItemType type = item.getItemType();

						ImGui.pushStyleColor(ImGuiCol.Button,
							ItemRendering.getQualityColor(item.getQuality()));
						ImGui.button(type.toString(), 50, 50);
						ImGui.popStyleColor();

						if (ImGui
							.beginDragDropSource(ImGuiDragDropFlags.None)) {
							ImGui.setDragDropPayload("ItemDrag",
								this.itemDragInfo);

							this.itemDragInfo.setDragInProgress(true);
							ImGui.text(this.inventory
								.getItem(this.itemDragInfo.getSourceIndex())
								.get().getID());
							ImGui.endDragDropSource();
						}
					}
					else {
						ImGui.invisibleButton(
							String.format("Invisible_%d_%d", row, col), 50, 50);
					}
					if (ImGui.beginDragDropTarget()) {
						InventoryDrag payload = ImGui.acceptDragDropPayload(
							"ItemDrag", InventoryDrag.class);
						if (payload != null) {
							payload.setDragInProgress(false);
							this.inventory.swapSlots(payload.getSourceIndex(),
								position);
						}
						ImGui.endDragDropTarget();
					}
					if (this.inventory.hasItem(position)) {
						Item item = this.inventory.getItem(position).get();
						ItemType type = item.getItemType();
						if (ImGui.isItemHovered()) {
							if (!ImGui.isMouseDown(0)) {
								this.itemDragInfo.setDragInProgress(false);
							}
							this.itemDragInfo.setIndex(position);
							ImGui.beginTooltip();
							switch (type) {
								case ACCESSORY:
									ItemRendering
										.drawAccessoryInfo((Accessory) item);
									break;
								case ARMOR:
									ItemRendering.drawArmorInfo((Armor) item);
									break;
								case COMPONENT:
									ItemRendering
										.drawComponentInfo((Component) item);
									break;
								case CONSUMABLE:
									ItemRendering
										.drawConsumableInfo((Consumable) item);
									break;
								case JUNK:
									ItemRendering.drawJunkInfo((Junk) item);
									break;
								case MATERIAL:
									ItemRendering
										.drawMaterialInfo((Material) item);
									break;
								case QUEST:
									ItemRendering.drawQuestInfo((Quest) item);
									break;
								case WEAPON:
									ItemRendering.drawWeaponInfo((Weapon) item);
									break;
								default:
									ImGui.text("Unrecognized item type "
										+ item.getItemType().toString());
									break;
							}
							ImGui.endTooltip();
						}
					}
				}
			}
			ImGui.endTable();
		}
		ImGui.end();
	}

	@Override
	public void setup(@NonNull Scene scene) {
		this.itemDragInfo = new InventoryDrag();
		this.inventory = new Inventory(
			PlayerInventory.INVENTORY_WIDTH * PlayerInventory.INVENTORY_HEIGHT);

		for (int row = 0; row < PlayerInventory.INVENTORY_HEIGHT; ++row) {
			for (int col = 0; col < PlayerInventory.INVENTORY_WIDTH; ++col) {
				if (Math.random() < 0.5) {
					this.inventory.setItem(
						PlayerInventory.INVENTORY_WIDTH * row + col,
						ItemGenerator.getRandomItem(), 1);
				}
			}
		}
	}
}
