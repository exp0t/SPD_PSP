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

// No shaders on the PSP: the fixed-function GU path implements NoosaScript's "texture * colorM + colorA".
public class Program {

	public Program() { }

	public int handle() { return 0; }

	public void attach( Shader shader ) { }

	public void link() { }

	public Attribute attribute( String name ) {
		return new Attribute( 0 );
	}

	public Uniform uniform( String name ) {
		switch (name) {
			case "uCamera": return new Uniform( Uniform.CAMERA );
			case "uModel":  return new Uniform( Uniform.MODEL );
			case "uColorM": return new Uniform( Uniform.COLOR_M );
			case "uColorA": return new Uniform( Uniform.COLOR_A );
			default:        return new Uniform( Uniform.NONE );
		}
	}

	public void use() { }

	public void delete() { }

	public static Program create( Shader ...shaders ) {
		return new Program();
	}
}
