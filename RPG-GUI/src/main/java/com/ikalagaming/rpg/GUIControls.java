package com.ikalagaming.rpg;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GuiInstance;
import com.ikalagaming.graphics.MouseInput;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.render.Render;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.scene.lights.SceneLights;
import com.ikalagaming.inventory.Inventory;
import com.ikalagaming.item.Accessory;
import com.ikalagaming.item.Armor;
import com.ikalagaming.item.AttributeModifier;
import com.ikalagaming.item.Component;
import com.ikalagaming.item.Consumable;
import com.ikalagaming.item.DamageModifier;
import com.ikalagaming.item.Equipment;
import com.ikalagaming.item.Item;
import com.ikalagaming.item.ItemStats;
import com.ikalagaming.item.Junk;
import com.ikalagaming.item.Material;
import com.ikalagaming.item.Quest;
import com.ikalagaming.item.Weapon;
import com.ikalagaming.item.enums.AccessoryType;
import com.ikalagaming.item.enums.AffixType;
import com.ikalagaming.item.enums.ArmorType;
import com.ikalagaming.item.enums.ItemType;
import com.ikalagaming.item.enums.ModifierType;
import com.ikalagaming.item.enums.Quality;
import com.ikalagaming.item.enums.WeaponType;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import lombok.NonNull;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * A GUI for manipulating lights
 */
public class GUIControls implements GuiInstance {

	private static int INVENTORY_WIDTH = 10;
	private static int INVENTORY_HEIGHT = 10;

	/**
	 * Draw a name including prefixes and affixes.
	 *
	 * @param equipment The equipment to draw a name for.
	 */
	private static void drawEquipmentName(Equipment equipment) {
		ImGui.text("Name: ");
		// Prefixes
		equipment.getAffixes().stream()
			.filter(affix -> AffixType.PREFIX.equals(affix.getAffixType()))
			.forEach(prefix -> {
				ImGui.sameLine();
				ImGui.textColored(
					GUIControls.getQualityColor(prefix.getQuality()),
					prefix.getID() + " ");
			});
		ImGui.sameLine();
		ImGui.textColored(GUIControls.getQualityColor(equipment.getQuality()),
			equipment.getID());
		// Suffixes
		equipment.getAffixes().stream()
			.filter(affix -> AffixType.SUFFIX.equals(affix.getAffixType()))
			.forEach(suffix -> {
				ImGui.sameLine();
				ImGui.textColored(
					GUIControls.getQualityColor(suffix.getQuality()),
					" " + suffix.getID());
			});
	}

	/**
	 * Draw an item name, colored based on quality.
	 *
	 * @param item The item to draw a name for.
	 */
	private static void drawName(Item item) {
		ImGui.text("Name: ");
		ImGui.sameLine();
		ImGui.textColored(GUIControls.getQualityColor(item.getQuality()),
			item.getID());
	}

	private static int getQualityColor(Quality quality) {
		switch (quality) {
			case COMMON:
				return ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f);
			case EPIC:
				return ImGui.colorConvertFloat4ToU32(1f, 0.6f, 0f, 1f);
			case LEGENDARY:
				return ImGui.colorConvertFloat4ToU32(1f, 0f, 1f, 1f);
			case MAGIC:
				return ImGui.colorConvertFloat4ToU32(1f, 0.9f, 0f, 1f);
			case RARE:
				return ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 1f, 1f);
			case TRASH:
			default:
				return ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 1f);
		}
	}

	private static void increaseIndent(int level) {
		ImGui.setCursorPosX(
			ImGui.getCursorPosX() + ImGui.getTreeNodeToLabelSpacing() * level);
	}

	private static String modifierText(AttributeModifier modifier) {
		StringBuilder result = new StringBuilder();
		result.append(modifier.getAmount());
		if (ModifierType.PERCENTAGE.equals(modifier.getType())) {
			result.append("%%");
		}
		result.append(' ');
		result.append(modifier.getAttribute().toString());
		return result.toString();
	}

	private static String modifierText(DamageModifier modifier) {
		StringBuilder result = new StringBuilder();
		result.append(modifier.getAmount());
		if (ModifierType.PERCENTAGE.equals(modifier.getType())) {
			result.append("%%");
		}
		result.append(' ');
		result.append(modifier.getDamageType().toString());
		return result.toString();
	}

	private float[] ambientColor;

	private float[] ambientFactor;
	private float[] dirLightColor;

	private float[] dirLightIntensity;

	private float[] dirLightX;
	private float[] dirLightY;
	private float[] dirLightZ;
	private ImBoolean wireframe;
	private ImBoolean windowDemo;
	private Inventory inventory;

	private Accessory demoAccessory;
	private Armor demoArmor;
	private Component demoComponent;
	private Consumable demoConsumable;
	private Junk demoJunk;
	private Material demoMaterial;
	private Quest demoQuest;
	private Weapon demoWeapon;

	private ImBoolean windowInventory;
	private ImBoolean windowItemCatalog;
	private InventoryDrag itemDragInfo;

	/**
	 * Set up the light controls.
	 *
	 * @param scene The scene.
	 */
	public GUIControls(@NonNull Scene scene) {
		SceneLights sceneLights = scene.getSceneLights();
		AmbientLight ambientLight = sceneLights.getAmbientLight();
		Vector3f color = ambientLight.getColor();

		this.ambientFactor = new float[] {ambientLight.getIntensity()};
		this.ambientColor = new float[] {color.x, color.y, color.z};

		DirectionalLight dirLight = sceneLights.getDirLight();
		color = dirLight.getColor();
		Vector3f pos = dirLight.getDirection();
		this.dirLightColor = new float[] {color.x, color.y, color.z};
		this.dirLightX = new float[] {pos.x};
		this.dirLightY = new float[] {pos.y};
		this.dirLightZ = new float[] {pos.z};
		this.dirLightIntensity = new float[] {dirLight.getIntensity()};
		this.wireframe = new ImBoolean(false);
		this.inventory = new Inventory(
			GUIControls.INVENTORY_WIDTH * GUIControls.INVENTORY_HEIGHT);

		for (int row = 0; row < GUIControls.INVENTORY_HEIGHT; ++row) {
			for (int col = 0; col < GUIControls.INVENTORY_WIDTH; ++col) {
				if (Math.random() < 0.5) {
					this.inventory.setItem(
						GUIControls.INVENTORY_WIDTH * row + col,
						ItemGenerator.getRandomItem(), 1);
				}
			}
		}
		this.windowInventory = new ImBoolean(false);
		this.windowItemCatalog = new ImBoolean(false);
		this.windowDemo = new ImBoolean(false);

		this.demoAccessory = ItemGenerator.getAccessory();
		this.demoArmor = ItemGenerator.getArmor();
		this.demoComponent = ItemGenerator.getComponent();
		this.demoConsumable = ItemGenerator.getConsumable();
		this.demoJunk = ItemGenerator.getJunk();
		this.demoMaterial = ItemGenerator.getMaterial();
		this.demoQuest = ItemGenerator.getQuest();
		this.demoWeapon = ItemGenerator.getWeapon();
		this.itemDragInfo = new InventoryDrag();
	}

	/**
	 * Draw details for an accessory.
	 *
	 * @param accessory The accessory to draw details for.
	 */
	private void drawAccessoryInfo(Accessory accessory) {
		GUIControls.drawEquipmentName(accessory);
		ImGui
			.text("Accessory Type: " + accessory.getAccessoryType().toString());
		this.drawEquipmentInfo(accessory);
	}

	/**
	 * Draw details for armor.
	 *
	 * @param armor The armor to draw details for.
	 */
	private void drawArmorInfo(Armor armor) {
		GUIControls.drawEquipmentName(armor);
		ImGui.text("Armor Type: " + armor.getArmorType().toString());
		this.drawEquipmentInfo(armor);
	}

	/**
	 * Draw component information.
	 *
	 * @param component The component to draw details for.
	 */
	private void drawComponentInfo(Component component) {
		GUIControls.drawName(component);
		ImGui
			.text("Component Type: " + component.getComponentType().toString());
		this.drawItemStats(component.getItemStats());
		ImGui.text("Can be applied to: ");
		for (ItemType type : component.getItemCriteria().getItemTypes()) {
			ImGui.bulletText(type.toString());
			switch (type) {
				case ACCESSORY:
					for (AccessoryType subType : component.getItemCriteria()
						.getAccessoryTypes()) {
						GUIControls.increaseIndent(1);
						ImGui.bulletText(subType.toString());
					}
					break;
				case ARMOR:
					for (ArmorType subType : component.getItemCriteria()
						.getArmorTypes()) {
						GUIControls.increaseIndent(1);
						ImGui.bulletText(subType.toString());
					}
					break;
				case WEAPON:
					for (WeaponType subType : component.getItemCriteria()
						.getWeaponTypes()) {
						GUIControls.increaseIndent(1);
						ImGui.bulletText(subType.toString());
					}
					break;
				case COMPONENT:
				case CONSUMABLE:
				case JUNK:
				case MATERIAL:
				case QUEST:
				default:
					// Just list the type
					break;
			}
		}
	}

	/**
	 * Draw details for a consumable.
	 *
	 * @param consumable The consumable.
	 */
	private void drawConsumableInfo(Consumable consumable) {
		GUIControls.drawName(consumable);
		ImGui.text(
			"Consumable type: " + consumable.getConsumableType().toString());
	}

	/**
	 * Draw details for equipment.
	 *
	 * @param equipment The equipment to draw details for.
	 */
	private void drawEquipmentInfo(Equipment equipment) {
		ImGui.text("Item Level: " + equipment.getItemLevel());
		ImGui.text("Quality: ");
		ImGui.sameLine();
		ImGui.textColored(GUIControls.getQualityColor(equipment.getQuality()),
			equipment.getQuality().toString());
		ImGui.text("Level Requirement: " + equipment.getLevelRequirement());
		ImGui.text("Attribute Requirements:");
		for (AttributeModifier mod : equipment.getAttributeRequirements()) {
			ImGui.bulletText(GUIControls.modifierText(mod));
		}
		ItemStats combinedStats = equipment.getCombinedStats();
		this.drawItemStats(combinedStats);
	}

	@Override
	public void drawGui() {
		ImGui.newFrame();

		if (this.windowDemo.get()) {
			ImGui.showDemoWindow();
		}

		ImGui.setNextWindowPos(10, 10, ImGuiCond.Once);
		ImGui.setNextWindowSize(450, 400, ImGuiCond.Once);
		ImGui.begin("Debug");

		ImGui.text(String.format("FPS: %d", GraphicsManager.getLastFPS()));

		Vector2f rotation =
			GraphicsManager.getCameraManager().getCamera().getRotation();
		ImGui.text(
			String.format("Camera rotation: (%f, %f)", rotation.x, rotation.y));
		MouseInput input = GraphicsManager.getWindow().getMouseInput();
		ImGui.text(String.format("Mouse position: (%f, %f)",
			input.getCurrentPos().x, input.getCurrentPos().y));
		ImGui.text(String.format("Displace vector: (%f, %f)",
			input.getDisplVec().x, input.getDisplVec().y));

		ImGui.checkbox("Wireframe", this.wireframe);
		ImGui.checkbox("Show Demo", this.windowDemo);
		ImGui.checkbox("Show Inventory", this.windowInventory);
		ImGui.checkbox("Show Item Catalog", this.windowItemCatalog);

		if (ImGui.collapsingHeader("Lights controls")) {
			if (ImGui.collapsingHeader("Ambient Light")) {
				ImGui.sliderFloat("Ambient factor", this.ambientFactor, 0.0f,
					1.0f, "%.2f");
				ImGui.colorEdit3("Ambient color", this.ambientColor);
			}

			if (ImGui.collapsingHeader("Dir Light")) {
				ImGui.sliderFloat("Dir Light - x", this.dirLightX, -1.0f, 1.0f,
					"%.2f");
				ImGui.sliderFloat("Dir Light - y", this.dirLightY, -1.0f, 1.0f,
					"%.2f");
				ImGui.sliderFloat("Dir Light - z", this.dirLightZ, -1.0f, 1.0f,
					"%.2f");
				ImGui.colorEdit3("Dir Light color", this.dirLightColor);
				ImGui.sliderFloat("Dir Light Intensity", this.dirLightIntensity,
					0.0f, 1.0f, "%.2f");
			}
		}
		ImGui.end();

		if (this.windowInventory.get()) {
			this.drawInventory();
		}
		if (this.windowItemCatalog.get()) {
			this.drawItemCatalog();
		}

		ImGui.endFrame();
		ImGui.render();
	}

	private void drawInventory() {
		ImGui.setNextWindowPos(200, 200, ImGuiCond.Once);
		ImGui.setNextWindowSize(600, 600, ImGuiCond.Once);
		ImGui.begin("Inventory");

		if (ImGui.beginTable("InventoryGrid", 10,
			ImGuiTableFlags.SizingFixedFit | ImGuiTableFlags.Borders)) {
			for (int x = 0; x < 10; ++x) {
				ImGui.tableSetupColumn("", ImGuiTableFlags.Borders, 50);
			}
			int position;
			for (int row = 0; row < GUIControls.INVENTORY_HEIGHT; ++row) {
				ImGui.tableNextRow();
				for (int col = 0; col < GUIControls.INVENTORY_WIDTH; ++col) {
					ImGui.tableSetColumnIndex(col);
					position = row * GUIControls.INVENTORY_WIDTH + col;

					if (this.inventory.hasItem(position)) {
						Item item = this.inventory.getItem(position).get();
						ItemType type = item.getItemType();

						ImGui.pushStyleColor(ImGuiCol.Button,
							GUIControls.getQualityColor(item.getQuality()));
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
									this.drawAccessoryInfo((Accessory) item);
									break;
								case ARMOR:
									this.drawArmorInfo((Armor) item);
									break;
								case COMPONENT:
									this.drawComponentInfo((Component) item);
									break;
								case CONSUMABLE:
									this.drawConsumableInfo((Consumable) item);
									break;
								case JUNK:
									this.drawJunkInfo((Junk) item);
									break;
								case MATERIAL:
									this.drawMaterialInfo((Material) item);
									break;
								case QUEST:
									this.drawQuestInfo((Quest) item);
									break;
								case WEAPON:
									this.drawWeaponInfo((Weapon) item);
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

	private void drawItemCatalog() {
		ImGui.setNextWindowPos(650, 10, ImGuiCond.Once);
		ImGui.setNextWindowSize(600, 800, ImGuiCond.Once);
		ImGui.begin("Item Catalog");

		if (ImGui.beginTabBar("Catalog Bar")) {
			if (ImGui.beginTabItem("Accessory")) {
				this.drawAccessoryInfo(this.demoAccessory);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem("Armor")) {
				this.drawArmorInfo(this.demoArmor);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem("Component")) {
				this.drawComponentInfo(this.demoComponent);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem("Consumable")) {
				this.drawConsumableInfo(this.demoConsumable);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem("Junk")) {
				this.drawJunkInfo(this.demoJunk);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem("Material")) {
				this.drawMaterialInfo(this.demoMaterial);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem("Quest")) {
				this.drawQuestInfo(this.demoQuest);
				ImGui.endTabItem();
			}
			if (ImGui.beginTabItem("Weapon")) {
				this.drawWeaponInfo(this.demoWeapon);
				ImGui.endTabItem();
			}
			ImGui.endTabBar();
		}

		ImGui.end();
	}

	/**
	 * Draw item stats, used as part of drawing item details.
	 *
	 * @param itemStats The item stats to draw.
	 */
	private void drawItemStats(ItemStats itemStats) {
		ImGui.text("Item Stats");
		ImGui.bulletText("Damage Buffs");
		for (DamageModifier mod : itemStats.getDamageBuffs()) {
			GUIControls.increaseIndent(1);
			ImGui.bulletText(GUIControls.modifierText(mod));
		}
		ImGui.bulletText("Resistance Buffs");
		for (DamageModifier mod : itemStats.getResistanceBuffs()) {
			GUIControls.increaseIndent(1);
			ImGui.bulletText(GUIControls.modifierText(mod));
		}
		ImGui.bulletText("Attribute Buffs");
		for (AttributeModifier mod : itemStats.getAttributeBuffs()) {
			GUIControls.increaseIndent(1);
			ImGui.bulletText(GUIControls.modifierText(mod));
		}
	}

	private void drawJunkInfo(Junk junk) {
		GUIControls.drawName(junk);
	}

	private void drawMaterialInfo(Material material) {
		GUIControls.drawName(material);
	}

	private void drawQuestInfo(Quest quest) {
		GUIControls.drawName(quest);
	}

	/**
	 * Draw weapon info for the provided weapon.
	 *
	 * @param weapon The weapon to draw.
	 */
	private void drawWeaponInfo(Weapon weapon) {
		GUIControls.drawEquipmentName(weapon);
		ImGui.text("Weapon Type: " + weapon.getWeaponType().toString());
		ImGui.text(
			"Damage: " + weapon.getMinDamage() + "-" + weapon.getMaxDamage());
		this.drawEquipmentInfo(weapon);
	}

	@Override
	public boolean handleGuiInput(@NonNull Scene scene,
		@NonNull Window window) {
		ImGuiIO imGuiIO = ImGui.getIO();
		MouseInput mouseInput = window.getMouseInput();
		Vector2f mousePos = mouseInput.getCurrentPos();
		imGuiIO.setMousePos(mousePos.x, mousePos.y);
		imGuiIO.setMouseDown(0, mouseInput.isLeftButtonPressed());
		imGuiIO.setMouseDown(1, mouseInput.isRightButtonPressed());
		Render.configuration.setWireframe(this.wireframe.get());

		boolean consumed =
			imGuiIO.getWantCaptureMouse() || imGuiIO.getWantCaptureKeyboard();
		if (consumed) {
			SceneLights sceneLights = scene.getSceneLights();
			AmbientLight ambientLight = sceneLights.getAmbientLight();
			ambientLight.setIntensity(this.ambientFactor[0]);
			ambientLight.setColor(this.ambientColor[0], this.ambientColor[1],
				this.ambientColor[2]);

			DirectionalLight dirLight = sceneLights.getDirLight();
			dirLight.setPosition(this.dirLightX[0], this.dirLightY[0],
				this.dirLightZ[0]);
			dirLight.setColor(this.dirLightColor[0], this.dirLightColor[1],
				this.dirLightColor[2]);
			dirLight.setIntensity(this.dirLightIntensity[0]);
		}
		return consumed;
	}

}
