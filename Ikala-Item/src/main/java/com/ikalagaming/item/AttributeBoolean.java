package com.ikalagaming.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stores a boolean value.
 * 
 * @author Ches Burks
 *
 */
@Getter
@Setter
@NoArgsConstructor
public class AttributeBoolean implements Attribute<Boolean> {
	private Integer id;
	private String name;
	private Boolean value;
	private String componentType;
}
