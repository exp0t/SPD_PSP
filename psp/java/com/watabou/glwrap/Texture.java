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

import com.badlogic.gdx.graphics.Pixmap;
import com.watabou.psp.PSPNative;

import org.teavm.interop.Address;

// A texture is just a native image (see Pixmap); the GU samples it in place.
public class Texture {

	public static final int NEAREST	= 0x2600;
	public static final int LINEAR	= 0x2601;

	public static final int REPEAT	= 0x2901;
	public static final int MIRROR	= 0x8370;
	public static final int CLAMP	= 0x812F;

	public int id = -1;
	private static int bound_id = 0; //id of the currently bound texture

	public boolean premultiplied = false;

	// native image used for drawing; owned when created from raw pixels
	protected int image = 0;
	private boolean ownsImage = false;
	private int flags = 0;

	protected void generate(){
		id = 0;
	}

	public static void activate( int index ) { }

	public void bind() {
		if (id == -1){
			generate();
		}
		PSPNative.texture( image, flags );
		bound_id = id;
	}

	public static void clear(){
		bound_id = 0;
	}

	public void filter( int minMode, int maxMode ) {
		flags = maxMode == LINEAR ? (flags | 1) : (flags & ~1);
	}

	public void wrap( int s, int t ) {
		flags = (flags & 1) | (s == CLAMP ? 0 : 2) | (t == CLAMP ? 0 : 4);
	}

	public void delete() {
		if (ownsImage && image != 0) PSPNative.imgFree( image );
		image = 0;
		ownsImage = false;
		id = -1;
	}

	public void bitmap( Pixmap pixmap ) {
		if (ownsImage && image != 0 && image != pixmap.handle()) PSPNative.imgFree( image );
		ownsImage = false;
		image = pixmap.handle();
		if (id == -1) id = 0;
		PSPNative.imgDirty( image );
		premultiplied = true;
	}

	// pixels are copied as raw little-endian ints, exactly like the GL_RGBA upload did
	public void pixels( int w, int h, int[] pixels ) {
		if (ownsImage && image != 0) PSPNative.imgFree( image );
		image = PSPNative.imgFromRGBA( w, h, Address.ofData( pixels ) );
		ownsImage = true;
		if (id == -1) id = 0;
	}

	public void pixels( int w, int h, byte[] pixels ) {
		if (ownsImage && image != 0) PSPNative.imgFree( image );
		image = PSPNative.imgFromAlpha( w, h, Address.ofData( pixels ) );
		ownsImage = true;
		if (id == -1) id = 0;
	}

	public static Texture create( Pixmap pix ) {
		Texture tex = new Texture();
		tex.bitmap( pix );
		return tex;
	}

	public static Texture create( int width, int height, int[] pixels ) {
		Texture tex = new Texture();
		tex.pixels( width, height, pixels );
		return tex;
	}

	public static Texture create( int width, int height, byte[] pixels ) {
		Texture tex = new Texture();
		tex.pixels( width, height, pixels );
		return tex;
	}
}
