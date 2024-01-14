package com.ikalagaming.factory.item;

import com.ikalagaming.factory.world.Tag;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The substance an object is primarily made of. These serve as groups of tags
 * that can be applied to all objects that share the material.
 * 
 * Materials can also extend from a parent, automatically including all of the
 * tags of the parent. When a child includes a more specific tag than the
 * parent, only the most specific tag will be preserved.
 * 
 * @author Ches Burks
 * 
 * @param name The name of the material.
 * @param tags The tags that apply to this material.
 * @param parent The parent material, null if this is a root material.
 *
 */
public record Material(@NonNull String name, @NonNull List<Tag> tags,
	Material parent) {

	/**
	 * Create a new material record.
	 * 
	 * @param name The name of the material.
	 * @param tags The tags that apply to this material. If this is not a
	 *            modifiable list, we will create a new list with a copy of the
	 *            provided elements instead of using the provided one.
	 * @param parent The parent material, null if this is a root material.
	 */
	public Material(@NonNull String name, @NonNull List<Tag> tags,
		Material parent) {

		boolean tagsModifiable = true;
		try {
			tags.addAll(Collections.emptyList());
		}
		catch (UnsupportedOperationException ignored) {
			tagsModifiable = false;
		}

		this.name = name;
		this.tags = tagsModifiable ? tags : new ArrayList<>(tags);
		this.parent = parent;
	}

	/**
	 * Create a new material record without a parent.
	 * 
	 * @param name The name of the material.
	 * @param tags The tags that apply to this material. If this is not a
	 *            modifiable list, we will create a new list with a copy of the
	 *            provided elements instead of using the provided one.
	 */
	public Material(@NonNull String name, @NonNull List<Tag> tags) {
		this(name, tags, null);
	}

	/**
	 * Create a new material record without any tags or a parent.
	 * 
	 * @param name The name of the material.
	 * @param parent The parent material, must not be null. If there is no
	 *            parent, there are other constructors to support that.
	 */
	public Material(@NonNull String name, @NonNull Material parent) {
		this(name, new ArrayList<>(), parent);
	}

	/**
	 * Create a new material record without any tags or a parent.
	 * 
	 * @param name The name of the material.
	 */
	public Material(@NonNull String name) {
		this(name, new ArrayList<>(), null);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || this.getClass() != o.getClass()) {
			return false;
		}

		Material other = (Material) o;
		return name.equals(other.name());
	}

	@Override
	public int hashCode() {
		return Objects.hash(name);
	}

	@Override
	public String toString() {
		return "Material[" + "name=" + name + ", tags=" + this.tags.toString()
			+ ", parent=" + parent.name() + ']';
	}
}
