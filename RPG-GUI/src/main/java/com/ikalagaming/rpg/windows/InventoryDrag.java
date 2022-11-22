package com.ikalagaming.rpg.windows;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Used to track what we are dragging and dropping in inventories.
 *
 * @author Ches Burks
 *
 */
@Getter
@Setter
@NoArgsConstructor
class InventoryDrag {
	/**
	 * The index in the source inventory we are dragging from.
	 *
	 * @param sourceIndex The index in the source inventory.
	 * @return The index in the source inventory.
	 */
	private int sourceIndex;

	/**
	 * Whether we are currently in the middle of a drag operation.
	 *
	 * @param dragInProgress Whether we have started a drag.
	 * @return Whether we are currently already dragging an item.
	 */
	private boolean dragInProgress;

	/**
	 * Set the source index unless a drag is in progress.
	 *
	 * @param index The index we started dragging from.
	 */
	public void setIndex(int index) {
		if (this.dragInProgress) {
			return;
		}
		this.sourceIndex = index;
	}
}
