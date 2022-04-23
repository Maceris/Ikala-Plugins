package com.ikalagaming.item.template;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * Slots into an item to give it extra bonus stats.
 * 
 * @author Ches Burks
 *
 */
@NoArgsConstructor
@Getter
@Setter
public class ComponentTemplate {
	private Integer id;
	private List<ModifierTemplate> damage = new ArrayList<>();
	private List<ModifierTemplate> userBuffs = new ArrayList<>();
	private List<ModifierTemplate> targetDebuffs = new ArrayList<>();
}
