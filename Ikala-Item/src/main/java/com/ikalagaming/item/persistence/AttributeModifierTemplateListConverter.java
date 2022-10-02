package com.ikalagaming.item.persistence;

import com.ikalagaming.item.template.AttributeModifierTemplate;

import java.util.List;

import javax.persistence.Converter;

/**
 * Convert lists of affixes.
 * 
 * @author Ches Burks
 *
 */
@Converter
public class AttributeModifierTemplateListConverter
	extends GenericConverter<List<AttributeModifierTemplate>> {}
