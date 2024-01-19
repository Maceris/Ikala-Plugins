package com.ikalagaming.factory.kvt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.util.List;

/**
 * A node that is an array of the same type of item.
 *
 * @author Ches Burks
 * @param <T> The type that this node stores.
 *
 */
@Getter
@AllArgsConstructor
public class ArrayNode<T> implements NodeTree {

	/**
	 * The type of the node.
	 */
	private final NodeType type;

	/**
	 * The array of values stored by the node.
	 */
	private final List<T> values;

	@Override
	public void add(final @NonNull String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(String name, @NonNull NodeType newType,
		@NonNull Boolean value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <U> void add(String name, @NonNull NodeType newType,
		@NonNull List<U> value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(String name, @NonNull NodeType newType,
		@NonNull Node value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(String name, @NonNull NodeType newType,
		@NonNull String value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <U extends Number> void add(String name, @NonNull NodeType newType,
		@NonNull U value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <U> void add(final @NonNull String name,
		final @NonNull U childValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <U> U get(final @NonNull String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public NodeType getType() {
		return this.type;
	}

	@Override
	public boolean hasChild(final @NonNull String name) {
		return false;
	}
}
