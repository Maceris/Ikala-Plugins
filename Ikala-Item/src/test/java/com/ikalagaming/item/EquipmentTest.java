package com.ikalagaming.item;

import com.ikalagaming.attributes.Attribute;
import com.ikalagaming.attributes.DamageType;
import com.ikalagaming.item.enums.AffixType;
import com.ikalagaming.item.enums.ModifierType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests for the item util class.
 *
 * @author Ches Burks
 *
 */
class EquipmentTest {

	/**
	 * Search through and find a modifier if it exists.
	 *
	 * @param modifiers The list of modifiers to look through.
	 * @param attribute The attribute we are searching for.
	 * @param type The type of modifier we are looking for.
	 * @return The attribute, or null.
	 */
	private AttributeModifier findModifier(List<AttributeModifier> modifiers,
		Attribute attribute, ModifierType type) {
		for (AttributeModifier modifier : modifiers) {
			if (attribute.equals(modifier.getAttribute())
				&& type.equals(modifier.getType())) {
				return modifier;
			}
		}
		return null;
	}

	/**
	 * Search through and find a modifier if it exists.
	 *
	 * @param modifiers The list of modifiers to look through.
	 * @param damage The damage type we are searching for.
	 * @param type The type of modifier we are looking for.
	 * @return The attribute, or null.
	 */
	private DamageModifier findModifier(List<DamageModifier> modifiers,
		DamageType damage, ModifierType type) {
		for (DamageModifier modifier : modifiers) {
			if (damage.equals(modifier.getDamageType())
				&& type.equals(modifier.getType())) {
				return modifier;
			}
		}
		return null;
	}

	/**
	 * Test the stat combining of equipment and their affixes.
	 */
	@Test
	void testCombineStats() {
		Equipment equipment = new Equipment();

		Affix prefix = new Affix();
		prefix.setAffixType(AffixType.PREFIX);
		ItemStats prefixStats = new ItemStats();
		prefix.setItemStats(prefixStats);

		AttributeModifier charismaFlat1 = new AttributeModifier();
		charismaFlat1.setAmount(1);
		charismaFlat1.setAttribute(Attribute.CHARISMA);
		charismaFlat1.setType(ModifierType.FLAT);
		prefixStats.getAttributeBuffs().add(charismaFlat1);
		AttributeModifier charismaPercent1 = new AttributeModifier();
		charismaPercent1.setAmount(2);
		charismaPercent1.setAttribute(Attribute.CHARISMA);
		charismaPercent1.setType(ModifierType.PERCENTAGE);
		prefixStats.getAttributeBuffs().add(charismaPercent1);
		AttributeModifier dexFlat1 = new AttributeModifier();
		dexFlat1.setAmount(3);
		dexFlat1.setAttribute(Attribute.DEXTERITY);
		dexFlat1.setType(ModifierType.FLAT);
		prefixStats.getAttributeBuffs().add(dexFlat1);

		DamageModifier fireDamageFlat1 = new DamageModifier();
		fireDamageFlat1.setAmount(4);
		fireDamageFlat1.setDamageType(DamageType.FIRE);
		fireDamageFlat1.setType(ModifierType.FLAT);
		prefixStats.getDamageBuffs().add(fireDamageFlat1);
		DamageModifier fireDamagePercent1 = new DamageModifier();
		fireDamagePercent1.setAmount(5);
		fireDamagePercent1.setDamageType(DamageType.FIRE);
		fireDamagePercent1.setType(ModifierType.PERCENTAGE);
		prefixStats.getDamageBuffs().add(fireDamagePercent1);
		DamageModifier poisonDamagePercent1 = new DamageModifier();
		poisonDamagePercent1.setAmount(5);
		poisonDamagePercent1.setDamageType(DamageType.POISON);
		poisonDamagePercent1.setType(ModifierType.PERCENTAGE);
		prefixStats.getDamageBuffs().add(poisonDamagePercent1);

		DamageModifier holyResistFlat1 = new DamageModifier();
		holyResistFlat1.setAmount(4);
		holyResistFlat1.setDamageType(DamageType.HOLY);
		holyResistFlat1.setType(ModifierType.FLAT);
		prefixStats.getResistanceBuffs().add(holyResistFlat1);
		DamageModifier holyResistPercent1 = new DamageModifier();
		holyResistPercent1.setAmount(5);
		holyResistPercent1.setDamageType(DamageType.HOLY);
		holyResistPercent1.setType(ModifierType.PERCENTAGE);
		prefixStats.getResistanceBuffs().add(holyResistPercent1);
		DamageModifier psychicResistPercent1 = new DamageModifier();
		psychicResistPercent1.setAmount(5);
		psychicResistPercent1.setDamageType(DamageType.PSYCHIC);
		psychicResistPercent1.setType(ModifierType.PERCENTAGE);
		prefixStats.getResistanceBuffs().add(psychicResistPercent1);

		Affix suffix = new Affix();
		suffix.setAffixType(AffixType.SUFFIX);
		ItemStats suffixStats = new ItemStats();
		suffix.setItemStats(suffixStats);

		AttributeModifier charismaFlat2 = new AttributeModifier();
		charismaFlat2.setAmount(10);
		charismaFlat2.setAttribute(Attribute.CHARISMA);
		charismaFlat2.setType(ModifierType.FLAT);
		suffixStats.getAttributeBuffs().add(charismaFlat2);
		AttributeModifier charismaPercent2 = new AttributeModifier();
		charismaPercent2.setAmount(20);
		charismaPercent2.setAttribute(Attribute.CHARISMA);
		charismaPercent2.setType(ModifierType.PERCENTAGE);
		suffixStats.getAttributeBuffs().add(charismaPercent2);

		DamageModifier fireDamageFlat2 = new DamageModifier();
		fireDamageFlat2.setAmount(40);
		fireDamageFlat2.setDamageType(DamageType.FIRE);
		fireDamageFlat2.setType(ModifierType.FLAT);
		suffixStats.getDamageBuffs().add(fireDamageFlat2);
		DamageModifier fireDamagePercent2 = new DamageModifier();
		fireDamagePercent2.setAmount(50);
		fireDamagePercent2.setDamageType(DamageType.FIRE);
		fireDamagePercent2.setType(ModifierType.PERCENTAGE);
		suffixStats.getDamageBuffs().add(fireDamagePercent2);

		DamageModifier holyResistFlat2 = new DamageModifier();
		holyResistFlat2.setAmount(40);
		holyResistFlat2.setDamageType(DamageType.HOLY);
		holyResistFlat2.setType(ModifierType.FLAT);
		suffixStats.getResistanceBuffs().add(holyResistFlat2);
		DamageModifier psychicResistPercent2 = new DamageModifier();
		psychicResistPercent2.setAmount(50);
		psychicResistPercent2.setDamageType(DamageType.PSYCHIC);
		psychicResistPercent2.setType(ModifierType.PERCENTAGE);
		suffixStats.getResistanceBuffs().add(psychicResistPercent2);

		equipment.setPrefix(prefix);
		equipment.setSuffix(suffix);

		AttributeModifier charismaFlatEquip = new AttributeModifier();
		charismaFlatEquip.setAmount(100);
		charismaFlatEquip.setAttribute(Attribute.CHARISMA);
		charismaFlatEquip.setType(ModifierType.FLAT);
		equipment.getItemStats().getAttributeBuffs().add(charismaFlatEquip);
		AttributeModifier intFlatEquip = new AttributeModifier();
		intFlatEquip.setAmount(200);
		intFlatEquip.setAttribute(Attribute.INTELLIGENCE);
		intFlatEquip.setType(ModifierType.FLAT);
		equipment.getItemStats().getAttributeBuffs().add(intFlatEquip);

		DamageModifier fireDamagePercentEquip = new DamageModifier();
		fireDamagePercentEquip.setAmount(300);
		fireDamagePercentEquip.setDamageType(DamageType.FIRE);
		fireDamagePercentEquip.setType(ModifierType.PERCENTAGE);
		equipment.getItemStats().getDamageBuffs().add(fireDamagePercentEquip);
		DamageModifier electricDamagePercentEquip = new DamageModifier();
		electricDamagePercentEquip.setAmount(400);
		electricDamagePercentEquip.setDamageType(DamageType.ELECTRIC);
		electricDamagePercentEquip.setType(ModifierType.PERCENTAGE);
		equipment.getItemStats().getDamageBuffs()
			.add(electricDamagePercentEquip);

		DamageModifier holyResistFlatEquip = new DamageModifier();
		holyResistFlatEquip.setAmount(500);
		holyResistFlatEquip.setDamageType(DamageType.HOLY);
		holyResistFlatEquip.setType(ModifierType.FLAT);
		equipment.getItemStats().getResistanceBuffs().add(holyResistFlatEquip);
		DamageModifier unholyResistPercentEquip = new DamageModifier();
		unholyResistPercentEquip.setAmount(600);
		unholyResistPercentEquip.setDamageType(DamageType.UNHOLY);
		unholyResistPercentEquip.setType(ModifierType.PERCENTAGE);
		equipment.getItemStats().getResistanceBuffs()
			.add(unholyResistPercentEquip);

		ItemStats combined = equipment.getCombinedStats();

		// Attributes
		// -------------------------------------------------------------------
		AttributeModifier expectedCharismaFlat = new AttributeModifier();
		expectedCharismaFlat.setAttribute(Attribute.CHARISMA);
		expectedCharismaFlat.setType(ModifierType.FLAT);
		expectedCharismaFlat.setAmount(charismaFlat1.getAmount()
			+ charismaFlat2.getAmount() + charismaFlatEquip.getAmount());

		// Stat in all 3
		Assertions.assertEquals(expectedCharismaFlat,
			this.findModifier(combined.getAttributeBuffs(), Attribute.CHARISMA,
				ModifierType.FLAT));

		AttributeModifier expectedCharismaPercent = new AttributeModifier();
		expectedCharismaPercent.setAttribute(Attribute.CHARISMA);
		expectedCharismaPercent.setType(ModifierType.PERCENTAGE);
		expectedCharismaPercent.setAmount(
			charismaPercent1.getAmount() + charismaPercent2.getAmount());

		// Stat only in both affixes
		Assertions.assertEquals(expectedCharismaPercent,
			this.findModifier(combined.getAttributeBuffs(), Attribute.CHARISMA,
				ModifierType.PERCENTAGE));

		AttributeModifier expectedIntFlat = new AttributeModifier();
		expectedIntFlat.setAttribute(Attribute.INTELLIGENCE);
		expectedIntFlat.setType(ModifierType.FLAT);
		expectedIntFlat.setAmount(intFlatEquip.getAmount());

		// Stat only on item
		Assertions.assertEquals(expectedIntFlat,
			this.findModifier(combined.getAttributeBuffs(),
				Attribute.INTELLIGENCE, ModifierType.FLAT));

		AttributeModifier expectedDexFlat = new AttributeModifier();
		expectedDexFlat.setAttribute(Attribute.DEXTERITY);
		expectedDexFlat.setType(ModifierType.FLAT);
		expectedDexFlat.setAmount(dexFlat1.getAmount());

		// Stat only on one affix
		Assertions.assertEquals(expectedDexFlat,
			this.findModifier(combined.getAttributeBuffs(), Attribute.DEXTERITY,
				ModifierType.FLAT));

		// Damages
		// -------------------------------------------------------------------

		DamageModifier expectedFireDmgFlat = new DamageModifier();
		expectedFireDmgFlat.setDamageType(DamageType.FIRE);
		expectedFireDmgFlat.setType(ModifierType.FLAT);
		expectedFireDmgFlat.setAmount(
			fireDamageFlat1.getAmount() + fireDamageFlat2.getAmount());

		// Damage buff only on both affixes
		Assertions.assertEquals(expectedFireDmgFlat, this.findModifier(
			combined.getDamageBuffs(), DamageType.FIRE, ModifierType.FLAT));

		DamageModifier expectedFireDmgPercent = new DamageModifier();
		expectedFireDmgPercent.setDamageType(DamageType.FIRE);
		expectedFireDmgPercent.setType(ModifierType.PERCENTAGE);
		expectedFireDmgPercent.setAmount(
			fireDamagePercent1.getAmount() + fireDamagePercent2.getAmount()
				+ fireDamagePercentEquip.getAmount());

		// Damage buff on all 3
		Assertions.assertEquals(expectedFireDmgPercent,
			this.findModifier(combined.getDamageBuffs(), DamageType.FIRE,
				ModifierType.PERCENTAGE));

		DamageModifier expectedPoisonDmgPercent = new DamageModifier();
		expectedPoisonDmgPercent.setDamageType(DamageType.POISON);
		expectedPoisonDmgPercent.setType(ModifierType.PERCENTAGE);
		expectedPoisonDmgPercent.setAmount(poisonDamagePercent1.getAmount());

		// Damage buff only on one affix
		Assertions.assertEquals(expectedPoisonDmgPercent,
			this.findModifier(combined.getDamageBuffs(), DamageType.POISON,
				ModifierType.PERCENTAGE));

		DamageModifier expectedElectricDmgPercent = new DamageModifier();
		expectedElectricDmgPercent.setDamageType(DamageType.ELECTRIC);
		expectedElectricDmgPercent.setType(ModifierType.PERCENTAGE);
		expectedElectricDmgPercent
			.setAmount(electricDamagePercentEquip.getAmount());

		// Damage buff only on one affix
		Assertions.assertEquals(expectedElectricDmgPercent,
			this.findModifier(combined.getDamageBuffs(), DamageType.ELECTRIC,
				ModifierType.PERCENTAGE));

		// Resists
		// -------------------------------------------------------------------
		DamageModifier expectedHolyFlat = new DamageModifier();
		expectedHolyFlat.setDamageType(DamageType.HOLY);
		expectedHolyFlat.setType(ModifierType.FLAT);
		expectedHolyFlat.setAmount(holyResistFlat1.getAmount()
			+ holyResistFlat2.getAmount() + holyResistFlatEquip.getAmount());

		// Damage resist on all 3
		Assertions.assertEquals(expectedHolyFlat, this.findModifier(
			combined.getResistanceBuffs(), DamageType.HOLY, ModifierType.FLAT));

		DamageModifier expectedHolyPercent = new DamageModifier();
		expectedHolyPercent.setDamageType(DamageType.HOLY);
		expectedHolyPercent.setType(ModifierType.PERCENTAGE);
		expectedHolyPercent.setAmount(holyResistPercent1.getAmount());

		// Damage resist only on one affix
		Assertions.assertEquals(expectedHolyPercent,
			this.findModifier(combined.getResistanceBuffs(), DamageType.HOLY,
				ModifierType.PERCENTAGE));

		DamageModifier expectedPsychichPercent = new DamageModifier();
		expectedPsychichPercent.setDamageType(DamageType.PSYCHIC);
		expectedPsychichPercent.setType(ModifierType.PERCENTAGE);
		expectedPsychichPercent.setAmount(psychicResistPercent1.getAmount()
			+ psychicResistPercent2.getAmount());

		// Damage resist only on both affixes
		Assertions.assertEquals(expectedPsychichPercent,
			this.findModifier(combined.getResistanceBuffs(), DamageType.PSYCHIC,
				ModifierType.PERCENTAGE));

		DamageModifier expectedUnholyPercent = new DamageModifier();
		expectedUnholyPercent.setDamageType(DamageType.UNHOLY);
		expectedUnholyPercent.setType(ModifierType.PERCENTAGE);
		expectedUnholyPercent.setAmount(unholyResistPercentEquip.getAmount());

		// Damage buff only on item
		Assertions.assertEquals(expectedUnholyPercent,
			this.findModifier(combined.getResistanceBuffs(), DamageType.UNHOLY,
				ModifierType.PERCENTAGE));
	}

}
