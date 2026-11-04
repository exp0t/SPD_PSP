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

public class Shader {

	public static final int VERTEX		= 0x8B31;
	public static final int FRAGMENT	= 0x8B30;

	public Shader( int type ) { }

	public int handle() { return 0; }

	public void source( String src ) { }

	public void compile() { }

	public void delete() { }

	public static Shader createCompiled( int type, String src ) {
		return new Shader( type );
	}
}
