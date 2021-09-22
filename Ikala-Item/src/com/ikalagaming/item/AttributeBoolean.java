package com.ikalagaming.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * Stores a boolean value.
 * 
 * @author Ches Burks
 *
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AttributeBoolean implements Attribute<Boolean> {
	@Id
	@GeneratedValue
	private Integer id;
	private String name;
	private Boolean value;
	private String componentType;
}
