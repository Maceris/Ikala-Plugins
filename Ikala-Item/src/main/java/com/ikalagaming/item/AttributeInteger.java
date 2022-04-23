package com.ikalagaming.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stores an integer.
 * 
 * @author Ches Burks
 *
 */
@Getter
@Setter
@NoArgsConstructor
public class AttributeInteger implements Attribute<Integer> {
	private Integer id;
	private String name;
	private Integer value;
	private String componentType;
}
