package com.ikalagaming.item.template;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@Getter
@Setter
public class WeaponTemplate extends ItemTemplate {
	private Integer minDamage;
	private Integer maxDamage;
	private List<ModifierTemplate> extraDamage = new ArrayList<>();
	private List<ModifierTemplate> userBuffs = new ArrayList<>();
	private List<ModifierTemplate> targetDebuffs = new ArrayList<>();
}
