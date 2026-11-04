// Minimal libGDX IntArray: only what IntMap.Keys.toArray needs.
package com.badlogic.gdx.utils;

import java.util.Arrays;

public class IntArray {
	public int[] items;
	public int size;

	public IntArray(boolean ordered, int capacity) { items = new int[Math.max(capacity, 1)]; }

	public void add(int value) {
		if (size == items.length) items = Arrays.copyOf(items, size * 2);
		items[size++] = value;
	}

	public int[] toArray() { return Arrays.copyOf(items, size); }
}
