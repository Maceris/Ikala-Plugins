package com.ikalagaming.rpg.windows;

import com.ikalagaming.ecs.ECSManager;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.DragDropFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TableColumnFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TableFlags;
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
        IkGui.setNextWindowPos(650, 200, Condition.ONCE);
        IkGui.setNextWindowSize(650, 750, Condition.ONCE);
        IkGui.begin("Inventory");

        drawEquipmentSlots();

        if (IkGui.beginTable(
                "InventoryGrid", 10, TableFlags.NO_HOST_EXTEND_X | TableFlags.BORDERS)) {
            for (int col = 0; col < PlayerInventory.INVENTORY_WIDTH; ++col) {
                IkGui.tableSetupColumn(
                        "Column" + col,
                        TableColumnFlags.WIDTH_FIXED,
                        PlayerInventory.SLOT_WIDTH + PlayerInventory.SLOT_PADDING);
            }
            int position;
            for (int row = 0; row < PlayerInventory.INVENTORY_HEIGHT; ++row) {
                IkGui.tableNextRow();
                for (int col = 0; col < PlayerInventory.INVENTORY_WIDTH; ++col) {
                    IkGui.tableSetColumnIndex(col);
                    position = row * PlayerInventory.INVENTORY_WIDTH + col;

                    if (inventory.hasItem(position)) {
                        Item item = inventory.getItem(position).get();

                        IkGui.pushStyleColor(
                                ColorType.BUTTON, ItemRendering.getQualityColor(item.getQuality()));
                        drawItem(item, row, col);
                        IkGui.popStyleColor();

                        setupDragDropSource();
                        drawItemCount(position, item);
                    } else {
                        IkGui.invisibleButton(
                                String.format("Invisible_%d_%d", row, col),
                                PlayerInventory.SLOT_WIDTH,
                                PlayerInventory.SLOT_HEIGHT);
                    }

                    setupDragDropTarget(position);

                    if (inventory.hasItem(position)) {
                        Item item = inventory.getItem(position).get();
                        if (IkGui.isItemHovered()) {
                            if (!IkGui.isMouseDown(MouseButton.LEFT)) {
                                itemDragInfo.setDragInProgress(false);
                            }
                            itemDragInfo.setIndex(position);

                            if (InvUtil.canStack(item)
                                    && IkGui.isMouseClicked(MouseButton.RIGHT, false)) {
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
            IkGui.endTable();
        }

        IkGui.button("Trash", PlayerInventory.SLOT_WIDTH, PlayerInventory.SLOT_HEIGHT);
        if (IkGui.beginDragDropTarget()) {
            InventoryDrag payload = IkGui.acceptDragDropPayload("ItemDrag");
            if (payload != null) {
                payload.setDragInProgress(false);
                inventory.clearSlot(payload.getSourceIndex());
            }
            IkGui.endDragDropTarget();
        }

        IkGui.end();
    }

    private void drawEquipmentSlots() {
        ItemUtil.ImageCoordinates coordinates =
                ItemUtil.getSlotTextureCoordinates(AccessoryType.TRINKET);
        IkGui.imageButton(
                "Trinket Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());
        IkGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(ArmorType.HEAD);
        IkGui.imageButton(
                "Head Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());
        IkGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(ArmorType.SHOULDERS);
        IkGui.imageButton(
                "Shoulders Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());

        coordinates = ItemUtil.getSlotTextureCoordinates(AccessoryType.AMULET);
        IkGui.imageButton(
                "Amulet Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());
        IkGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(ArmorType.CHEST);
        IkGui.imageButton(
                "Chest Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());
        IkGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(AccessoryType.CAPE);
        IkGui.imageButton(
                "Cape Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());

        coordinates = ItemUtil.getSlotTextureCoordinates(ArmorType.WRIST);
        IkGui.imageButton(
                "Wrist Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());
        IkGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(AccessoryType.BELT);
        IkGui.imageButton(
                "Belt Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());
        IkGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(AccessoryType.TRINKET);
        IkGui.imageButton(
                "Second Trinket Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());

        coordinates = ItemUtil.getSlotTextureCoordinates(WeaponType.ONE_HANDED_MELEE);
        IkGui.imageButton(
                "Main Hand Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());
        IkGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(ArmorType.LEGS);
        IkGui.imageButton(
                "Legs Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());
        IkGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(WeaponType.OFF_HAND);
        IkGui.imageButton(
                "Off Hand Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());

        coordinates = ItemUtil.getSlotTextureCoordinates(AccessoryType.RING);
        IkGui.imageButton(
                "Right Ring Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());
        IkGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(ArmorType.FEET);
        IkGui.imageButton(
                "Feet Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());
        IkGui.sameLine();
        coordinates = ItemUtil.getSlotTextureCoordinates(AccessoryType.RING);
        IkGui.imageButton(
                "Left Ring Slot",
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());
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
        IkGui.imageButton(
                String.format("Item_%d_%d", row, col),
                itemTexture.info(),
                PlayerInventory.SLOT_WIDTH,
                PlayerInventory.SLOT_HEIGHT,
                ((float) coordinates.x0()) / itemTexture.width(),
                ((float) coordinates.y0()) / itemTexture.height(),
                ((float) coordinates.x1()) / itemTexture.width(),
                ((float) coordinates.y1()) / itemTexture.height());
    }

    private void drawItemCount(int position, Item item) {
        if (InvUtil.canStack(item)) {
            float x = IkGui.getCursorScreenPosX();
            float y = IkGui.getCursorScreenPosY() - 20;
            final int count = inventory.getItemCount(position);
            final int digits = Math.max(1, (int) (Math.log10(count) + 1));

            IkGui.getWindowDrawList()
                    .addRectFilled(
                            x,
                            y,
                            x + 10 * digits,
                            y + 15,
                            IkGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f));
            IkGui.getWindowDrawList()
                    .addText(
                            IkGui.getFontSize(),
                            x,
                            y,
                            IkGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f),
                            count + "",
                            0.0f);
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
        if (IkGui.beginDragDropSource(DragDropFlags.NONE)) {
            IkGui.setDragDropPayload("ItemDrag", itemDragInfo);

            itemDragInfo.setDragInProgress(true);
            IkGui.text(inventory.getItem(itemDragInfo.getSourceIndex()).get().getID());
            IkGui.endDragDropSource();
        }
    }

    /**
     * Make the previous item into a drag drop target.
     *
     * @param position The position in the inventory the target is in.
     */
    private void setupDragDropTarget(int position) {
        if (IkGui.beginDragDropTarget()) {
            InventoryDrag payload = IkGui.acceptDragDropPayload("ItemDrag");
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
            IkGui.endDragDropTarget();
        }
    }

    /**
     * Show an information tool tip for the given item.
     *
     * @param item The item we are drawing details for.
     */
    private void showToolTip(Item item) {
        IkGui.beginTooltip();
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
                IkGui.text("Unrecognized item type " + item.getItemType().toString());
                break;
        }
        IkGui.endTooltip();
    }
}
