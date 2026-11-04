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

public class Blending {

	private static boolean enabled = true;
	private static boolean light = false;

	public static void useDefault(){
		enable();
		setNormalMode();
	}

	public static void enable(){
		enabled = true;
		apply();
	}

	public static void disable(){
		enabled = false;
		apply();
	}

	//in this mode colors overwrite eachother, based on alpha value
	public static void setNormalMode(){
		light = false;
		apply();
	}

	//in this mode colors add to eachother, eventually reaching pure white
	public static void setLightMode(){
		light = true;
		apply();
	}

	private static void apply(){
		PSPNative.blend( !enabled ? 0 : light ? 2 : 1 );
	}
}
