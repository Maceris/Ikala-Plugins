package com.ikalagaming.window;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

/**
 * A 2d array of menu items that can have some associated action.
 *
 * @author Ches Burks
 *
 */
@Slf4j
public class Menu extends Window {

	@Getter
	private int w;
	@Getter
	private int h;
	private int size;
	private ArrayList<MenuItem> items;

	/**
	 * Constructs a menu that has space for width*height items.
	 *
	 * @param width How many columns the menu should have
	 * @param height How many rows the menu should have
	 */
	public Menu(final int width, final int height) {
		this.w = width;
		this.h = height;
		this.size = this.w * this.h;
		this.items = new ArrayList<>(this.size);
		// take up all of parent by default
		this.scale.set(1.0f, 1.0f);
		this.localDisplace.set(0.0f, 0.0f);
		this.align = Alignment.NORTH_WEST;
		this.setConsumeTouches(false);
		this.dirty();
	}

	/**
	 * Adds a child if and only if it is a menu item. Increase the height
	 * (number of rows) if the menu is full.
	 */
	@Override
	public synchronized void addChild(@NonNull Window item) {
		if (!(item instanceof MenuItem)) {
			// TODO localize
			Menu.log.warn("item {} rejected from menu due to type",
				item.getClass());
			// reject it if it's not a menu item
			return;
		}
		if (this.items.size() >= this.size) {
			this.setHeight(this.h + 1);
		}
		this.items.add((MenuItem) item);
		item.setParent(this);
		item.updateHeights();
		this.dirty();
	}

	@Override
	public synchronized void dirty() {
		this.dirty = true;
		if (this.items != null) {
			for (MenuItem item : this.items) {
				item.dirty();
			}
		}
	}

	@Override
	public synchronized int getChildCount() {
		return this.items.size();
	}

	@Override
	protected synchronized void recalculate() {
		if (!this.dirty) {
			return;
		}
		super.recalculate();
		this.recalculateItems();
	}

	/**
	 * Recalculate the menu items contained in the menu so they fit well.
	 */
	protected synchronized void recalculateItems() {
		for (int j = 0; j < this.h; ++j) {
			for (int i = 0; i < this.w; ++i) {
				this.items.get((j) * this.w + i)
					.setLocalHeight((1.0f / this.h) * 0.80f);
				this.items.get((j) * this.w + i)
					.setLocalWidth((1.0f / this.w) * 0.80f);
				this.items.get((j) * this.w + i).setDisplacement(
					new Point((1.0f / this.w) * i + 0.1f / this.w,
						(1.0f / this.h) * j + 0.1f / this.h));
				this.items.get((j) * this.w + i)
					.setAlignment(Alignment.NORTH_WEST);
			}
		}
	}

	/**
	 * Sets the number of rows in the menu.
	 *
	 * @param height The height, as rows, in the table of entries.
	 */
	public synchronized void setHeight(final int height) {
		this.h = height;
		this.size = this.w * this.h;
		this.dirty();
	}

	/**
	 * Sets teh number of columns in the menu.
	 *
	 * @param width The width, as columns, in the table of entries.
	 */
	public synchronized void setWidth(final int width) {
		this.w = width;
		this.size = this.w * this.h;
		this.dirty();
	}

}
