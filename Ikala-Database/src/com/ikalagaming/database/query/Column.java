package com.ikalagaming.database.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.jooq.DataType;

/**
 * A column to add to a table.
 * 
 * @author Ches Burks
 *
 */
@Getter
@AllArgsConstructor
public class Column {
	/**
	 * The name of the column.
	 * 
	 * @param name The name.
	 * @return The column name.
	 */
	@NonNull
	private String name;
	/**
	 * The data type to store in the column.
	 * 
	 * @param type The type of data this column stores.
	 * @return The type of data in this column.
	 */
	@NonNull
	private DataType<?> type;
}
