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
import java.nio.ShortBuffer;

public class Quad {

	// 0---1
	// | \ |
	// 3---2
	public static final short[] VALUES = {0, 1, 2, 0, 2, 3};

	public static final int SIZE = VALUES.length;

	private static ShortBuffer indices;
	private static int indexSize = 0;

	// heap buffers so the renderer can pass the backing array to native code without copying
	public static FloatBuffer create() {
		return FloatBuffer.wrap( new float[16] );
	}

	public static FloatBuffer createSet( int size ) {
		return FloatBuffer.wrap( new float[size * 16] );
	}

	// the native renderer has its own static quad index list
	public static void setupIndices(){ }

	public static void bindIndices(){ }

	public static void releaseIndices(){ }

	public static ShortBuffer getIndices( int size ) {

		if (size > indexSize) {

			indexSize = size;

			short[] values = new short[size * 6];
			int pos = 0;
			int limit = size * 4;
			for (int ofs=0; ofs < limit; ofs += 4) {
				values[pos++] = (short)(ofs + 0);
				values[pos++] = (short)(ofs + 1);
				values[pos++] = (short)(ofs + 2);
				values[pos++] = (short)(ofs + 0);
				values[pos++] = (short)(ofs + 2);
				values[pos++] = (short)(ofs + 3);
			}

			indices = ShortBuffer.wrap( values );
		}

		return indices;
	}

	public static void fill( float[] v,
		float x1, float x2, float y1, float y2,
		float u1, float u2, float v1, float v2 ) {

		v[0] = x1;
		v[1] = y1;
		v[2] = u1;
		v[3] = v1;

		v[4] = x2;
		v[5] = y1;
		v[6] = u2;
		v[7] = v1;

		v[8] = x2;
		v[9] = y2;
		v[10]= u2;
		v[11]= v2;

		v[12]= x1;
		v[13]= y2;
		v[14]= u1;
		v[15]= v2;
	}

	public static void fillXY( float[] v, float x1, float x2, float y1, float y2 ) {

		v[0] = x1;
		v[1] = y1;

		v[4] = x2;
		v[5] = y1;

		v[8] = x2;
		v[9] = y2;

		v[12]= x1;
		v[13]= y2;
	}

	public static void fillUV( float[] v, float u1, float u2, float v1, float v2 ) {

		v[2] = u1;
		v[3] = v1;

		v[6] = u2;
		v[7] = v1;

		v[10]= u2;
		v[11]= v2;

		v[14]= u1;
		v[15]= v2;
	}
}
