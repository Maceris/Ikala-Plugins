package com.ikalagaming.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * An item in the game.
 * 
 * @author Ches Burks
 *
 */
@NoArgsConstructor
@Getter
@Setter
public class Item {
	private Integer id;
	private List<Attribute<?>> attributes = new ArrayList<>();
	private List<String> tags = new ArrayList<>();

}
