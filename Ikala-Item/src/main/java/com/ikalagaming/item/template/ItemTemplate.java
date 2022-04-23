package com.ikalagaming.item.template;

import com.ikalagaming.item.ItemType;

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
public class ItemTemplate {
	/**
	 * The unique ID of this type of item.
	 * @param id The unique ID for this item type.
	 * @return The unique ID for this item type.
	 */
	private Integer id;
	private ItemType type;
	private String name;
	private Integer qualityID;
	private Integer itemLevel;
	private List<String> tags = new ArrayList<>();
}
