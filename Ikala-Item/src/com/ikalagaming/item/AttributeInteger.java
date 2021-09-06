package com.ikalagaming.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * Stores an integer.
 * 
 * @author Ches Burks
 *
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AttributeInteger implements Attribute<Integer> {
	@Id
	@GeneratedValue
	private Integer id;
	private String name;
	private Integer value;
}
