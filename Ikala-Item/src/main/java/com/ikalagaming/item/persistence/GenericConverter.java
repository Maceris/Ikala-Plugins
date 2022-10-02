package com.ikalagaming.item.persistence;

import com.google.gson.Gson;

import javax.persistence.AttributeConverter;

/**
 * Convert between an arbitrary type and JSON for persisting as a blob in a
 * database.
 * 
 * @author Ches Burks
 *
 * @param <T> The type we are converting.
 */
public abstract class GenericConverter<T>
	implements AttributeConverter<T, String> {

	private static final Gson gson = new Gson();

	@Override
	public String convertToDatabaseColumn(T attribute) {
		return GenericConverter.gson.toJson(attribute);
	}

	@Override
	public T convertToEntityAttribute(String dbData) {
		@SuppressWarnings("unchecked")
		final Class<T> clazz = (Class<T>) this.getClass();
		return GenericConverter.gson.fromJson(dbData, clazz);
	}

}
