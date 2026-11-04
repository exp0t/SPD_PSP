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
import com.watabou.psp.PSPBuffers;

import java.nio.FloatBuffer;
import java.util.ArrayList;

// Keeps a converted copy of the quads in native memory, refreshed from the FloatBuffer on updateGLData().
public class Vertexbuffer {

	private int id;
	private int capacity;
	private FloatBuffer vertices;
	private int updateStart, updateEnd;

	private static final ArrayList<Vertexbuffer> buffers = new ArrayList<>();

	public Vertexbuffer( FloatBuffer vertices ) {
		synchronized (buffers) {
			this.vertices = vertices;
			buffers.add(this);

			updateStart = 0;
			updateEnd = vertices.limit();
		}
	}

	//For flagging the buffer for a full update without changing anything
	public void updateVertices(){
		updateVertices(vertices);
	}

	//For flagging an update with a full set of new data
	public void updateVertices( FloatBuffer vertices ){
		updateVertices(vertices, 0, vertices.limit());
	}

	//For flagging an update with a subset of data changed
	public void updateVertices( FloatBuffer vertices, int start, int end){
		this.vertices = vertices;

		if (updateStart == -1)
			updateStart = start;
		else
			updateStart = Math.min(start, updateStart);

		if (updateEnd == -1)
			updateEnd = end;
		else
			updateEnd = Math.max(end, updateEnd);
	}

	public void updateGLData(){
		if (updateStart == -1) return;

		if (id == 0 || capacity < vertices.capacity()) {
			if (id != 0) PSPNative.vboFree( id );
			capacity = vertices.capacity();
			id = PSPNative.vboCreate( capacity );
			updateStart = 0;
			updateEnd = vertices.limit();
		}
		PSPNative.vboUpdate( id, PSPBuffers.address( vertices, updateStart, updateEnd ), updateStart, updateEnd );
		updateStart = updateEnd = -1;
	}

	public int handle(){
		return id;
	}

	public void bind(){ }

	public void release(){ }

	public void delete(){
		synchronized (buffers) {
			if (id != 0) PSPNative.vboFree( id );
			id = 0;
			buffers.remove(this);
		}
	}

	public static void clear(){
		synchronized (buffers) {
			for (Vertexbuffer buf : buffers.toArray(new Vertexbuffer[0])) {
				buf.delete();
			}
		}
	}

	public static void reload(){
		synchronized (buffers) {
			for (Vertexbuffer buf : buffers) {
				buf.updateVertices();
				buf.updateGLData();
			}
		}
	}

}
