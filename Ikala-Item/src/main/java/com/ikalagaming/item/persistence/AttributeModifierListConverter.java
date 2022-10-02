package com.ikalagaming.item.persistence;

import com.ikalagaming.item.AttributeModifier;

import java.util.List;

import javax.persistence.Converter;

/**
 * Convert lists of affixes.
 * 
 * @author Ches Burks
 *
 */
@Converter
public class AttributeModifierListConverter
	extends GenericConverter<List<AttributeModifier>> {}
