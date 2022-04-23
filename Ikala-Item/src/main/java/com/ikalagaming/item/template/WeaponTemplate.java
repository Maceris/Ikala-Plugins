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
	private List<Modifier> extraDamage = new ArrayList<>();
	private List<Modifier> userBuffs = new ArrayList<>();
	private List<Modifier> targetDebuffs = new ArrayList<>();
}
