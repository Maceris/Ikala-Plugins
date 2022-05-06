package com.ikalagaming.item;

import com.ikalagaming.attributes.Attribute;
import com.ikalagaming.attributes.DamageType;
import com.ikalagaming.item.template.AttributeModifierTemplate;
import com.ikalagaming.item.template.DamageModifierTemplate;
import com.ikalagaming.item.template.WeaponTemplate;
import com.ikalagaming.random.RandomGen;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;

/**
 * Tests for the weapon template class.
 *
 * @author Ches Burks
 *
 */
class WeaponTemplateTest {

	private Random rand = new Random();
	private RandomGen random = new RandomGen();
	private int nextID = 0;

	private AttributeModifierTemplate generateRandomAttribute() {
		AttributeModifierTemplate attribute = new AttributeModifierTemplate();
		attribute.setAttribute(this.random.selectEnumValue(Attribute.class));
		attribute.setType(this.random.selectEnumValue(ModifierType.class));
		attribute.setAmount(Math.abs(this.rand.nextInt(100)));
		attribute.setVariance(this.rand.nextInt(attribute.getAmount()));
		return attribute;
	}

	private DamageModifier generateRandomDamage() {
		DamageModifierTemplate damage = new DamageModifierTemplate();
		damage.setAmount(Math.abs(this.rand.nextInt(100)));
		damage.setDamageType(this.random.selectEnumValue(DamageType.class));
		damage.setType(this.random.selectEnumValue(ModifierType.class));
		damage.setVariance(this.rand.nextInt(damage.getAmount()));
		return damage;
	}

	private WeaponTemplate generateRandomWeapon() {
		WeaponTemplate weapon = new WeaponTemplate();

		// Item traits, plus weapon type so we can name it properly
		weapon.setType(ItemType.WEAPON);
		weapon.setWeaponType(this.random.selectEnumValue(WeaponType.class));
		weapon.setQuality(this.random.selectEnumValue(Quality.class));
		weapon.setItemLevel(Math.abs(this.rand.nextInt(100)));
		weapon.setID(weapon.getType().getPrefix()
			+ weapon.getWeaponType().getPrefix() + this.nextID);

		// weapon traits
		weapon.setMaxDamage(Math.abs(this.rand.nextInt(100)));
		weapon.setMinDamage(this.rand.nextInt(weapon.getMaxDamage()));
		final int maxRequirements = this.rand.nextInt(4) + 1;
		for (int i = 0; i < maxRequirements; ++i) {
			weapon.getAttributeRequirements()
				.add(this.generateRandomAttribute());
		}
		final int maxDamages = this.rand.nextInt(4) + 1;
		for (int i = 0; i < maxDamages; ++i) {
			weapon.getItemStats().getDamageBuffs()
				.add(this.generateRandomDamage());
		}
		final int maxBuffs = this.rand.nextInt(4) + 1;
		for (int i = 0; i < maxBuffs; ++i) {
			weapon.getItemStats().getResistanceBuffs()
				.add(this.generateRandomDamage());
		}
		final int maxAttributes = this.rand.nextInt(4) + 1;
		for (int i = 0; i < maxAttributes; ++i) {
			weapon.getItemStats().getAttributeBuffs()
				.add(this.generateRandomAttribute());
		}

		return weapon;
	}

	/**
	 * Generate a random weapon, then convert to and from json to make sure it
	 * can be stored in the database properly.
	 */
	@Test
	void testPersistence() {
		Gson gson = new Gson();
		WeaponTemplate template = this.generateRandomWeapon();
		WeaponTemplate parsed =
			gson.fromJson(gson.toJson(template), WeaponTemplate.class);
		Assertions.assertEquals(template, parsed);
	}

}
