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

import com.watabou.psp.PSPNative;

import org.teavm.interop.Address;

public class Uniform {

	public static final int NONE    = 0;
	public static final int CAMERA  = 1;
	public static final int MODEL   = 2;
	public static final int COLOR_M = 3;
	public static final int COLOR_A = 4;

	private final int role;

	// lighting is applied as one native call, so remember both halves
	private static float rm = 1, gm = 1, bm = 1, am = 1, ra, ga, ba, aa;

	public Uniform( int role ) {
		this.role = role;
	}

	public int location() { return role; }

	public void enable() { }

	public void disable() { }

	public void value1f( float value ) { }

	public void value2f( float v1, float v2 ) { }

	public void value4f( float v1, float v2, float v3, float v4 ) {
		if (role == COLOR_M) {
			rm = v1; gm = v2; bm = v3; am = v4;
		} else if (role == COLOR_A) {
			ra = v1; ga = v2; ba = v3; aa = v4;
		} else {
			return;
		}
		PSPNative.color( rm, gm, bm, am, ra, ga, ba, aa );
	}

	public void valueM3( float[] value ) { }

	public void valueM4( float[] value ) {
		if (role == MODEL) {
			PSPNative.model( Address.ofData( value ) );
		} else if (role == CAMERA) {
			PSPNative.camera( Address.ofData( value ) );
		}
	}
}
