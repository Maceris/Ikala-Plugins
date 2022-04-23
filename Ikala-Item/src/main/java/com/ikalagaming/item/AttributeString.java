package com.ikalagaming.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stores a text string.
 * 
 * @author Ches Burks
 *
 */
@Getter
@Setter
@NoArgsConstructor
public class AttributeString implements Attribute<String> {
	private Integer id;
	private String name;
	private String value;
	private String componentType;
}
