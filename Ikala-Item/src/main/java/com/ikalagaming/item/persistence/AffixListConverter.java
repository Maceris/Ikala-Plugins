package com.ikalagaming.item.persistence;

import com.ikalagaming.item.AttributeModifier;

import java.util.List;

import javax.persistence.Converter;

/**
 * Convert lists of attribute modifiers.
 * 
 * @author Ches Burks
 *
 */
@Converter
public class AffixListConverter
	extends GenericConverter<List<AttributeModifier>> {}
