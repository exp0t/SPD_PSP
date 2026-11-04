// Minimal libGDX Array: only what IntMap.Values.toArray needs.
package com.badlogic.gdx.utils;

import java.util.Arrays;

public class Array<T> {
	public Object[] items;
	public int size;

	public Array(boolean ordered, int capacity) { items = new Object[Math.max(capacity, 1)]; }

	public void add(T value) {
		if (size == items.length) items = Arrays.copyOf(items, size * 2);
		items[size++] = value;
	}

	@SuppressWarnings("unchecked")
	public T[] toArray() { return (T[]) Arrays.copyOf(items, size); }
}
