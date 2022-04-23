package com.ikalagaming.item.template;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
public class Component {
	private UUID id;
	private List<Modifier> damage = new ArrayList<>();
	private List<Modifier> userBuffs = new ArrayList<>();
	private List<Modifier> targetDebuffs = new ArrayList<>();
}
