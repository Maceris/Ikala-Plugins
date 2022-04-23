package com.ikalagaming.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * Stores a floating point number.
 * 
 * @author Ches Burks
 *
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AttributeFloat implements Attribute<Double> {
	@Id
	@GeneratedValue
	private Integer id;
	private String name;
	private Double value;
	private String componentType;
}
