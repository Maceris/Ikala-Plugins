package com.ikalagaming.item;

import lombok.NonNull;

/**
 * Utilities related to items.
 * 
 * @author Ches Burks
 *
 */
public class ItemUtil {
	/**
	 * The width of an item texture in pixels.
	 */
	private static final int ITEM_WIDTH = 16;
	/**
	 * The height of an item texture in pixels.
	 */
	private static final int ITEM_HEIGHT = 16;

	/**
	 * Return the texture coordinates within the item sprite sheet for the given
	 * item.
	 * 
	 * @param item The item we are looking for.
	 * @return The texture coordinates on the item sprite sheet.
	 */
	public static ImageCoordinates getTextureCoordinates(@NonNull Item item) {
		int uvUpperLeftX = 0;
		int uvUpperLeftY = 0;
		// Please forgive me.
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

		return new ImageCoordinates(uvUpperLeftX, uvUpperLeftY,
			uvUpperLeftX + ITEM_WIDTH, uvUpperLeftY + ITEM_HEIGHT);
	}

	/**
	 * Coordinates within an item texture where the item icon is found.
	 * 
	 * @param x0 The upper left x coordinate.
	 * @param y0 The upper left y coordinate.
	 * @param x1 The lower right x coordinate.
	 * @param y1 The lower right y coordinate.
	 */
	public static record ImageCoordinates(int x0, int y0, int x1, int y1) {}

	/**
	 * Private constructor so this class is not instantiated.
	 */
	private ItemUtil() {
		throw new UnsupportedOperationException(
			"This utility class should not be instantiated");
	}
}
