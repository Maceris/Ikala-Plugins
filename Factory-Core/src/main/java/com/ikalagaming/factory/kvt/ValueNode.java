package com.ikalagaming.factory.kvt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.List;

/**
 * A node that is just a value and the type.
 *
 * @author Ches Burks
 * @param <T> The type that this node stores.
 *
 */
@Getter
@Setter
@AllArgsConstructor
public class ValueNode<T> extends NodeTree {

	/**
	 * The type of the node.
	 */
	private final NodeType type;

	/**
	 * The value stored by the node.
	 */
	private T value;

	@Override
	public void add(final @NonNull String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(String name, @NonNull NodeType newType,
		@NonNull Boolean newValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <U> void add(String name, @NonNull NodeType newType,
		@NonNull List<U> newValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(String name, @NonNull NodeType newType,
		@NonNull NodeTree newValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(String name, @NonNull NodeType newType,
		@NonNull String newValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <U extends Number> void add(String name, @NonNull NodeType newType,
		@NonNull U newValue) {
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
