package com.ikalagaming.factory.world;

import lombok.NonNull;

/**
 * Additional information used to categorize things and encode information. Tags
 * describe either a property of something, like an adjective describing it, or
 * how something can be used.
 *
 * @param name The name of the tag.
 * @param parent The parent tag.
 * @author Ches Burks
 */
public record Tag(@NonNull String name, Tag parent) {

	/**
	 * Create a tag without a parent.
	 *
	 * @param name The name of the tag.
	 */
	public Tag(String name) {
		this(name, null);
	}

	@Override
	public String toString() {
		if (this.parent == null) {
			return "Tag[" + "name=" + this.name + ']';
		}
		return "Tag[" + "name=" + this.name + ", parent=" + this.parent.name()
			+ ']';
	}
}