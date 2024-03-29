package com.ikalagaming.rpg.windows;

import com.ikalagaming.ecs.ECSManager;
import com.ikalagaming.graphics.graph.Texture;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.rpg.GUIPlugin;
import com.ikalagaming.rpg.GameManager;
import com.ikalagaming.rpg.inventory.InvUtil;
import com.ikalagaming.rpg.inventory.Inventory;
import com.ikalagaming.rpg.item.Accessory;
import com.ikalagaming.rpg.item.Armor;
import com.ikalagaming.rpg.item.Component;
import com.ikalagaming.rpg.item.Consumable;
import com.ikalagaming.rpg.item.Item;
import com.ikalagaming.rpg.item.ItemUtil;
import com.ikalagaming.rpg.item.Junk;
import com.ikalagaming.rpg.item.Material;
import com.ikalagaming.rpg.item.Quest;
import com.ikalagaming.rpg.item.Weapon;
import com.ikalagaming.rpg.item.enums.AccessoryType;
import com.ikalagaming.rpg.item.enums.ArmorType;
import com.ikalagaming.rpg.item.enums.WeaponType;
import com.ikalagaming.rpg.item.testing.ItemGenerator;
import com.ikalagaming.rpg.utils.ItemRendering;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

/**
 * An inventory for items.
 *
 * @author Ches Burks
 */
@Slf4j
public class PlayerInventory implements GUIWindow {
    /** The number of slots in a row of the inventory. */
    private static final int INVENTORY_WIDTH = 10;

    /** The number of slots in a column of the inventory. */
    private static final int INVENTORY_HEIGHT = 10;

    /** The width of an inventory slot in pixels. */
    private static final int SLOT_WIDTH = 25;

    /** The height of an inventory slot in pixels. */
    private static final int SLOT_HEIGHT = 25;

    private static final int SLOT_PADDING = 4;

    private InventoryDrag itemDragInfo;
    @Getter private Inventory inventory;

    /**
     * The texture to use for items.
     *
     * @param itemTexture The texture to use.
     */
    @Setter private Texture itemTexture;

    @Override
    public void draw() {
        ImGui.setNextWindowPos(650, 200, ImGuiCond.Once);
        ImGui.setNextWindowSize(650, 750, ImGuiCond.Once);
        ImGui.begin("Inventory");

        drawEquipmentSlots();

        if (ImGui.beginTable(
                "InventoryGrid", 10, ImGuiTableFlags.NoHostExtendX | ImGuiTableFlags.Borders)) {
            for (int col = 0; col < PlayerInventory.INVENTORY_WIDTH; ++col) {
                ImGui.tableSetupColumn(
                        "Column" + col,
                        ImGuiTableColumnFlags.WidthFixed,
                        PlayerInventory.SLOT_WIDTH + PlayerInventory.SLOT_PADDING);
            }
            int position;
            for (int row = 0; row < PlayerInventory.INVENTORY_HEIGHT; ++row) {
                ImGui.tableNextRow();
                for (int col = 0; col < PlayerInventory.INVENTORY_WIDTH; ++col) {
                    ImGui.tableSetColumnIndex(col);
                    position = row * PlayerInventory.INVENTORY_WIDTH + col;

                    if (inventory.hasItem(position)) {
                        Item item = inventory.getItem(position).get();

                        ImGui.pushStyleColor(
                                ImGuiCol.Button, ItemRendering.getQualityColor(item.getQuality()));
                        drawItem(item, row, col);
                        ImGui.popStyleColor();

                        setupDragDropSource();
                        drawItemCount(position, item);
                    } else {
                        ImGui.invisibleButton(
                                String.format("Invisible_%d_%d", row, col),
                                PlayerInventory.SLOT_WIDTH,
                                PlayerInventory.SLOT_HEIGHT);
                    }

                    setupDragDropTarget(position);

                    if (inventory.hasItem(position)) {
                        Item item = inventory.getItem(position).get();
                        if (ImGui.isItemHovered()) {
                            if (!ImGui.isMouseDown(0)) {
                                itemDragInfo.setDragInProgress(false);
                            }
                            itemDragInfo.setIndex(position);

                            if (InvUtil.canStack(item) && ImGui.isMouseClicked(1, false)) {
                                int maxCount = inventory.getItemCount(position);
                                if (maxCount >= 2) {
                                    inventory.splitStack(position, maxCount / 2);
                                }
                            }
                            showToolTip(item);
                        }
                    }
                }
            }
            ImGui.endTable();
        }

        ImGui.button("Trash", PlayerInventory.SLOT_WIDTH, PlayerInventory.SLOT_HEIGHT);
        if (ImGui.beginDragDropTarget()) {
            InventoryDrag payload = ImGui.acceptDragDropPayload("ItemDrag", InventoryDrag.class);
            if (payload != null) {
                payload.setDragInProgress(false);
                inventory.clearSlot(payload.getSourceIndex());
            }
            ImGui.endDragDropTarget();
        }

        ImGui.end();
    }

    private void drawEquipmentSlots() {
        ItemUtil.ImageCoordinates coordinates =
                ItemUtil.getSlotTextureCoordinates(AccessoryType.TRINKET);
        ImGui.pushID("Trinket Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();
        ImGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(ArmorType.HEAD);
        ImGui.pushID("Head Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();
        ImGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(ArmorType.SHOULDERS);
        ImGui.pushID("Shoulders Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();

        coordinates = ItemUtil.getSlotTextureCoordinates(AccessoryType.AMULET);
        ImGui.pushID("Amulet Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();
        ImGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(ArmorType.CHEST);
        ImGui.pushID("Chest Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();
        ImGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(AccessoryType.CAPE);
        ImGui.pushID("Cape Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();

        coordinates = ItemUtil.getSlotTextureCoordinates(ArmorType.WRIST);
        ImGui.pushID("Wrist Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();
        ImGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(AccessoryType.BELT);
        ImGui.pushID("Belt Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();
        ImGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(AccessoryType.TRINKET);
        ImGui.pushID("Cape Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();

        coordinates = ItemUtil.getSlotTextureCoordinates(WeaponType.ONE_HANDED_MELEE);
        ImGui.pushID("Main Hand Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();
        ImGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(ArmorType.LEGS);
        ImGui.pushID("Legs Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();
        ImGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(WeaponType.OFF_HAND);
        ImGui.pushID("Off Hand Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();

        coordinates = ItemUtil.getSlotTextureCoordinates(AccessoryType.RING);
        ImGui.pushID("Right Ring Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();
        ImGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(ArmorType.FEET);
        ImGui.pushID("Feet Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();
        ImGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(AccessoryType.RING);
        ImGui.pushID("Left Ring Slot");
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();
    }

    /**
     * Draw an item from the spritesheet.
     *
     * @param item The item to draw.
     * @param row The row in the inventory, for naming things.
     * @param col The column in the inventory, for naming things.
     */
    private void drawItem(@NonNull Item item, int row, int col) {
        ItemUtil.ImageCoordinates coordinates = ItemUtil.getTextureCoordinates(item);
        ImGui.pushID(String.format("Item_%d_%d", row, col));
        ImGui.imageButton(
                itemTexture.getTextureID(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.getWidth(),
                ((float) coordinates.y0()) / itemTexture.getHeight(),
                ((float) coordinates.x1()) / itemTexture.getWidth(),
                ((float) coordinates.y1()) / itemTexture.getHeight());
        ImGui.popID();
    }

    private void drawItemCount(int position, Item item) {
        if (InvUtil.canStack(item)) {
            float x = ImGui.getCursorScreenPosX();
            float y = ImGui.getCursorScreenPosY() - 20;
            final int count = inventory.getItemCount(position);
            final int digits = Math.max(1, (int) (Math.log10(count) + 1));

            ImGui.getWindowDrawList()
                    .addRectFilled(
                            x,
                            y,
                            x + 10 * digits,
                            y + 15,
                            ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f));
            ImGui.getWindowDrawList()
                    .addText(x, y, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f), count + "");
        }
    }

    @Override
    public void setup(@NonNull Scene scene) {
        itemDragInfo = new InventoryDrag();
        UUID player = GameManager.getPlayer();

        if (player == null) {
            inventory =
                    new Inventory(
                            PlayerInventory.INVENTORY_HEIGHT * PlayerInventory.INVENTORY_WIDTH);
            log.warn(
                    SafeResourceLoader.getString(
                            "INVALID_INVENTORY", GUIPlugin.getResourceBundle()));
        } else {
            Optional<Inventory> inventoryMaybe = ECSManager.getComponent(player, Inventory.class);
            if (inventoryMaybe.isEmpty()) {
                inventory =
                        new Inventory(
                                PlayerInventory.INVENTORY_HEIGHT * PlayerInventory.INVENTORY_WIDTH);
                log.warn(
                        SafeResourceLoader.getString(
                                "INVALID_INVENTORY", GUIPlugin.getResourceBundle()));
            } else {
                inventory = inventoryMaybe.get();
            }
        }

        for (int row = 0; row < PlayerInventory.INVENTORY_HEIGHT; ++row) {
            for (int col = 0; col < PlayerInventory.INVENTORY_WIDTH; ++col) {
                if (Math.random() < 0.5) {
                    inventory.setItem(
                            PlayerInventory.INVENTORY_WIDTH * row + col,
                            ItemGenerator.getRandomItem(),
                            1);
                }
            }
        }
    }

    /** Make the previous item into a drag drop source. */
    private void setupDragDropSource() {
        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
            ImGui.setDragDropPayload("ItemDrag", itemDragInfo);

            itemDragInfo.setDragInProgress(true);
            ImGui.text(inventory.getItem(itemDragInfo.getSourceIndex()).get().getID());
            ImGui.endDragDropSource();
        }
    }

    /**
     * Make the previous item into a drag drop target.
     *
     * @param position The position in the inventory the target is in.
     */
    private void setupDragDropTarget(int position) {
        if (ImGui.beginDragDropTarget()) {
            InventoryDrag payload = ImGui.acceptDragDropPayload("ItemDrag", InventoryDrag.class);
            if (payload != null) {
                payload.setDragInProgress(false);

                Optional<Item> maybeTargetItem = inventory.getItem(position);
                boolean sameType = inventory.areSameType(payload.getSourceIndex(), position);
                if (maybeTargetItem.isEmpty() || !sameType) {
                    inventory.swapSlots(payload.getSourceIndex(), position);
                } else {
                    inventory.combineSlots(payload.getSourceIndex(), position);
                }
            }
            ImGui.endDragDropTarget();
        }
    }

    /**
     * Show an information tool tip for the given item.
     *
     * @param item The item we are drawing details for.
     */
    private void showToolTip(Item item) {
        ImGui.beginTooltip();
        switch (item.getItemType()) {
            case ACCESSORY:
                ItemRendering.drawAccessoryInfo((Accessory) item);
                break;
            case ARMOR:
                ItemRendering.drawArmorInfo((Armor) item);
                break;
            case COMPONENT:
                ItemRendering.drawComponentInfo((Component) item);
                break;
            case CONSUMABLE:
                ItemRendering.drawConsumableInfo((Consumable) item);
                break;
            case JUNK:
                ItemRendering.drawJunkInfo((Junk) item);
                break;
            case MATERIAL:
                ItemRendering.drawMaterialInfo((Material) item);
                break;
            case QUEST:
                ItemRendering.drawQuestInfo((Quest) item);
                break;
            case WEAPON:
                ItemRendering.drawWeaponInfo((Weapon) item);
                break;
            default:
                ImGui.text("Unrecognized item type " + item.getItemType().toString());
                break;
        }
        ImGui.endTooltip();
    }
}
