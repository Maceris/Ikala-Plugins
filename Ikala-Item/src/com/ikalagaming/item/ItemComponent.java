package com.ikalagaming.item;

import com.ikalagaming.ecs.Component;

import java.util.List;

/**
 * The base class for components that store information for items.
 *
 * @author Ches Burks
 *
 */
public abstract class ItemComponent extends Component<ItemComponent> {

	/**
	 * Take in a list of attributes and populate the fields of this component
	 * using them.
	 *
	 * @param attributes The attributes to use for populating fields.
	 */
	@SuppressWarnings("rawtypes")
	public abstract void populateFromAttributes(List<Attribute> attributes);

	/**
	 * Convert all the fields of this component into Attributes to be stored in
	 * a database.
	 *
	 * @return The list of attributes representing each field.
	 */
	@SuppressWarnings("rawtypes")
	public abstract List<Attribute> toAttributes();
}
