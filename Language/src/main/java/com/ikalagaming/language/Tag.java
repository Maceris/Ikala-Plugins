package com.ikalagaming.language;

import lombok.NonNull;

/**
 * Additional information used to categorize things and encode information.
 *
 * @param value The tag.
 * @param parent The parent tag.
 * @author Ches Burks
 */
public record Tag(@NonNull String value, Tag parent) {
	/**
	 * Create a tag without a parent.
	 *
	 * @param value The value of the tag.
	 */
	public Tag(String value) {
		this(value, null);
	}
}
