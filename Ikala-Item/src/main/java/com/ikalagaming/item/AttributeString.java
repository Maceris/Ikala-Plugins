package com.ikalagaming.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * Stores a text string.
 * 
 * @author Ches Burks
 *
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AttributeString implements Attribute<String> {
	@Id
	@GeneratedValue
	private Integer id;
	private String name;
	private String value;
	private String componentType;
}
