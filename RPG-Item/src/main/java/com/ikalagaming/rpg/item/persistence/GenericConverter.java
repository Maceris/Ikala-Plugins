package com.ikalagaming.rpg.item.persistence;

import com.google.gson.Gson;
import org.jooq.Converter;

import javax.persistence.AttributeConverter;

/**
 * Convert between an arbitrary type and JSON for persisting as a blob in a database.
 *
 * @author Ches Burks
 * @param <T> The type we are converting.
 */
public abstract class GenericConverter<T>
        implements AttributeConverter<T, String>, Converter<T, String> {

    /** Generated serial version ID. */
    private static final long serialVersionUID = 7941354008439669756L;

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

    @Override
    public String from(T databaseObject) {
        return this.convertToDatabaseColumn(databaseObject);
    }

    @Override
    public T to(String userObject) {
        return this.convertToEntityAttribute(userObject);
    }

    @Override
    public Class<String> toType() {
        return String.class;
    }
}
