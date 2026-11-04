/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2026 Evan Debenham
 *
 * PSP port: GL wrapper replaced by the native sceGu renderer (psp/native/gfx.c).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.watabou.glwrap;

import java.nio.FloatBuffer;

public class Attribute {

	public Attribute( int location ) { }

	public int location() { return 0; }

	public void enable() { }

	public void disable() { }

	public void vertexPointer( int size, int stride, FloatBuffer ptr ) { }

	public void vertexBuffer( int size, int stride, int offset ) { }
}
