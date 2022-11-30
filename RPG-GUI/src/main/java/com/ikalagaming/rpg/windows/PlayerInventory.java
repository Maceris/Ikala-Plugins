package com.ikalagaming.rpg.windows;

import com.ikalagaming.graphics.graph.Texture;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.inventory.InvUtil;
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
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * An inventory for items.
 *
 * @author Ches Burks
 *
 */
public class PlayerInventory implements GUIWindow {
	/**
	 * The width of an item texture in pixels.
	 */
	private static final int ITEM_WIDTH = 16;
	/**
	 * The height of an item texture in pixels.
	 */
	private static final int ITEM_HEIGHT = 16;

	/**
	 * The number of slots in a row of the inventory.
	 */
	private static final int INVENTORY_WIDTH = 10;
	/**
	 * The number of slots in a column of the inventory.
	 */
	private static final int INVENTORY_HEIGHT = 10;

	/**
	 * The width of an inventory slot in pixels.
	 */
	private static final int SLOT_WIDTH = 50;

	/**
	 * The height of an inventory slot in pixels.
	 */
	private static final int SLOT_HEIGHT = 50;

	private InventoryDrag itemDragInfo;
	@Getter
	private Inventory inventory;

	/**
	 * The texture to use for items.
	 *
	 * @param itemTexture The texture to use.
	 */
	@Setter
	private Texture itemTexture;

	@Override
	public void draw() {
		ImGui.setNextWindowPos(650, 200, ImGuiCond.Once);
		ImGui.setNextWindowSize(650, 750, ImGuiCond.Once);
		ImGui.begin("Inventory");

		if (ImGui.beginTable("InventoryGrid", 10,
			ImGuiTableFlags.SizingFixedFit | ImGuiTableFlags.Borders)) {
			for (int col = 0; col < PlayerInventory.INVENTORY_WIDTH; ++col) {
				ImGui.tableSetupColumn("Column" + col, ImGuiTableFlags.Borders,
					PlayerInventory.SLOT_WIDTH + 4);
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

						ImGui.pushStyleColor(ImGuiCol.Button,
							ItemRendering.getQualityColor(item.getQuality()));
						this.drawItem(item, row, col);
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

						if (InvUtil.canStack(item)) {
							float x = ImGui.getCursorScreenPosX();
							float y = ImGui.getCursorScreenPosY() - 20;
							final int count =
								this.inventory.getItemCount(position);
							final int digits =
								Math.max(1, (int) (Math.log10(count) + 1));

							ImGui.getWindowDrawList().addRectFilled(x, y,
								x + 10 * digits, y + 15,
								ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f));
							ImGui.getWindowDrawList().addText(x, y,
								ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f),
								count + "");
						}
					}
					else {
						ImGui.invisibleButton(
							String.format("Invisible_%d_%d", row, col),
							PlayerInventory.SLOT_WIDTH,
							PlayerInventory.SLOT_HEIGHT);
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

		ImGui.button("Trash", PlayerInventory.SLOT_WIDTH,
			PlayerInventory.SLOT_HEIGHT);
		if (ImGui.beginDragDropTarget()) {
			InventoryDrag payload =
				ImGui.acceptDragDropPayload("ItemDrag", InventoryDrag.class);
			if (payload != null) {
				payload.setDragInProgress(false);
				this.inventory.clearSlot(payload.getSourceIndex());
			}
			ImGui.endDragDropTarget();
		}

		ImGui.end();
	}

	/**
	 * Draw an item from the spritesheet.
	 *
	 * @param item The item to draw.
	 * @param row The row in the inventory, for naming things.
	 * @param col The column in the inventory, for naming things.
	 */
	private void drawItem(@NonNull Item item, int row, int col) {
		int uvUpperLeftX = 0;
		int uvUpperLeftY = 0;
		// Please have mercy on me.
		switch (item.getItemType()) {
			case ACCESSORY:
				switch (((Accessory) item).getAccessoryType()) {
					case AMULET:
						uvUpperLeftX = 0;
						uvUpperLeftY = 80;
						break;
					case BELT:
						uvUpperLeftX = 256;
						uvUpperLeftY = 80;
						break;
					case CAPE:
						uvUpperLeftX = 416;
						uvUpperLeftY = 256;
						break;
					case RING:
						uvUpperLeftX = 224;
						uvUpperLeftY = 64;
						break;
					case TRINKET:
						uvUpperLeftX = 192;
						uvUpperLeftY = 736;
						break;
					default:
						break;
				}
				break;
			case ARMOR:
				switch (((Armor) item).getArmorType()) {
					case CHEST:
						uvUpperLeftX = 224;
						uvUpperLeftY = 400;
						break;
					case FEET:
						uvUpperLeftX = 272;
						uvUpperLeftY = 400;
						break;
					case HANDS:
						uvUpperLeftX = 256;
						uvUpperLeftY = 400;
						break;
					case HEAD:
						uvUpperLeftX = 208;
						uvUpperLeftY = 400;
						break;
					case LEGS:
						uvUpperLeftX = 240;
						uvUpperLeftY = 400;
						break;
					case SHOULDERS:
						uvUpperLeftX = 544;
						uvUpperLeftY = 321;
						break;
					case WRIST:
						uvUpperLeftX = 800;
						uvUpperLeftY = 112;
						break;
					default:
						break;
				}
				break;
			case COMPONENT:
				switch (((Component) item).getComponentType()) {
					case AUGMENT:
						uvUpperLeftX = 144;
						uvUpperLeftY = 752;
						break;
					case GEM:
						uvUpperLeftX = 224;
						uvUpperLeftY = 720;
						break;
					default:
						break;
				}
				break;
			case CONSUMABLE:
				switch (((Consumable) item).getConsumableType()) {
					case BANDAGE:
						uvUpperLeftX = 208;
						uvUpperLeftY = 112;
						break;
					case DRINK:
						uvUpperLeftX = 752;
						uvUpperLeftY = 16;
						break;
					case ELIXIR:
						uvUpperLeftX = 240;
						uvUpperLeftY = 16;
						break;
					case FOOD:
						uvUpperLeftX = 768;
						uvUpperLeftY = 0;
						break;
					case POTION:
						uvUpperLeftX = 16;
						uvUpperLeftY = 16;
						break;
					case SCROLL:
						uvUpperLeftX = 32;
						uvUpperLeftY = 112;
						break;
					default:
						break;
				}
				break;
			case JUNK:
				uvUpperLeftX = 16;
				uvUpperLeftY = 704;
				break;
			case MATERIAL:
				uvUpperLeftX = 944;
				uvUpperLeftY = 128;
				break;
			case QUEST:
				uvUpperLeftX = 48;
				uvUpperLeftY = 112;
				break;
			case WEAPON:
				switch (((Weapon) item).getWeaponType()) {
					case OFF_HAND:
						uvUpperLeftX = 96;
						uvUpperLeftY = 96;
						break;
					case ONE_HANDED_MAGIC:
						uvUpperLeftX = 384;
						uvUpperLeftY = 560;
						break;
					case ONE_HANDED_MELEE:
						uvUpperLeftX = 96;
						uvUpperLeftY = 400;
						break;
					case ONE_HANDED_RANGED:
						uvUpperLeftX = 464;
						uvUpperLeftY = 144;
						break;
					case SHIELD:
						uvUpperLeftX = 80;
						uvUpperLeftY = 208;
						break;
					case TWO_HANDED_MAGIC:
						uvUpperLeftX = 416;
						uvUpperLeftY = 576;
						break;
					case TWO_HANDED_MELEE:
						uvUpperLeftX = 144;
						uvUpperLeftY = 400;
						break;
					case TWO_HANDED_RANGED:
						uvUpperLeftX = 176;
						uvUpperLeftY = 400;
						break;
					default:
						break;
				}
				break;
			default:
				break;
		}
		int uvLowerRightX = uvUpperLeftX + PlayerInventory.ITEM_WIDTH;
		int uvLowerRightY = uvUpperLeftY + PlayerInventory.ITEM_HEIGHT;
		ImGui.pushID(String.format("Item_%d_%d", row, col));
		ImGui.imageButton(this.itemTexture.getTextureID(),
			PlayerInventory.SLOT_WIDTH, PlayerInventory.SLOT_HEIGHT,
			((float) uvUpperLeftX) / this.itemTexture.getWidth(),
			((float) uvUpperLeftY) / this.itemTexture.getHeight(),
			((float) uvLowerRightX) / this.itemTexture.getWidth(),
			((float) uvLowerRightY) / this.itemTexture.getHeight());
		ImGui.popID();
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
