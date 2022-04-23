package com.ikalagaming.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stores a floating point number.
 * 
 * @author Ches Burks
 *
 */
@Getter
@Setter
@NoArgsConstructor
public class AttributeFloat implements Attribute<Double> {
	private Integer id;
	private String name;
	private Double value;
	private String componentType;
}
