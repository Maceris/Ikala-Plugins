package com.ikalagaming.window;

import lombok.Getter;
import lombok.NonNull;

/**
 * A textual item in a menu. This can have an associated action.
 *
 * @author Ches Burks
 *
 */
public class MenuItem extends Window {
	/**
	 * The text to display on this menu item.
	 *
	 * @return The current text.
	 */
	@Getter
	private String text;

	/**
	 * Creates a new menu item with default text.
	 */
	public MenuItem() {
		this.text = "Text";
		this.dirty();
	}

	/**
	 * Creates a new menu item with supplied text.
	 *
	 * @param theText The text to display for this item.
	 */
	public MenuItem(@NonNull final String theText) {
		this();
		this.text = theText;
		this.dirty();
	}

	@Override
	public void dirty() {
		this.dirty = true;
	}

	/**
	 * Returns the parent of the menu item, which is a menu.
	 *
	 * @return The menu that contains this item.
	 */
	public Menu getParent() {
		if (!(this.parent instanceof Menu)) {
			this.parent.removeChild(this);
		}
		return (Menu) this.parent;
	}

	/**
	 * Returns true if this item has a parent, false if the parent is null.
	 *
	 * @return true if this item has a parent, false otherwise.
	 */
	public boolean hasParent() {
		return this.parent != null;
	}

	@Override
	public void setParent(@NonNull Window newParent) {
		if (!(newParent instanceof Menu)) {
			return;
		}
		this.parent = newParent;
		this.updateHeights();
		this.dirty();
	}

	/**
	 * Sets the text to display for this item.
	 *
	 * @param newText The text to use for this item.
	 */
	public void setText(@NonNull final String newText) {
		this.text = newText;
		this.dirty();
	}
}
