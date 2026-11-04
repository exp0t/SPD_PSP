/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2026 Evan Debenham
 *
 * PSP port: NoosaScript drawing goes to the native sceGu renderer.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.watabou.noosa;

import com.watabou.glscripts.Script;
import com.watabou.glwrap.Attribute;
import com.watabou.glwrap.Quad;
import com.watabou.glwrap.Uniform;
import com.watabou.glwrap.Vertexbuffer;
import com.watabou.psp.PSPBuffers;
import com.watabou.psp.PSPNative;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class NoosaScript extends Script {

	public Uniform uCamera;
	public Uniform uModel;
	public Uniform uTex;
	public Uniform uColorM;
	public Uniform uColorA;
	public Attribute aXY;
	public Attribute aUV;

	private Camera lastCamera;

	public NoosaScript() {
		super();
		compile( shader() );

		uCamera	= uniform( "uCamera" );
		uModel	= uniform( "uModel" );
		uTex	= uniform( "uTex" );
		uColorM	= uniform( "uColorM" );
		uColorA	= uniform( "uColorA" );
		aXY		= attribute( "aXYZW" );
		aUV		= attribute( "aUV" );
		Quad.setupIndices();
		Quad.bindIndices();

	}

	@Override
	public void use() {
		super.use();
		PSPNative.lightingEnabled( this instanceof NoosaScriptNoLighting ? 0 : 1 );
	}

	public void drawElements( FloatBuffer vertices, ShortBuffer indices, int size ) {
		int vertCount = vertices.limit() / 4;
		PSPNative.indexed( PSPBuffers.address( vertices, 0, vertCount * 4 ), vertCount,
				PSPBuffers.address( indices, 0, size ), size );
	}

	public void drawQuad( FloatBuffer vertices ) {
		PSPNative.quads( PSPBuffers.address( vertices, 0, 16 ), 1 );
	}

	public void drawQuad( Vertexbuffer buffer ) {
		buffer.updateGLData();
		PSPNative.vboDraw( buffer.handle(), 0, 1 );
	}

	public void drawQuadSet( FloatBuffer vertices, int size ) {

		if (size == 0) {
			return;
		}
		PSPNative.quads( PSPBuffers.address( vertices, 0, size * 16 ), size );
	}

	public void drawQuadSet( Vertexbuffer buffer, int length, int offset ){
		if (length == 0) {
			return;
		}
		buffer.updateGLData();
		PSPNative.vboDraw( buffer.handle(), offset, length );
	}

	public void lighting( float rm, float gm, float bm, float am, float ra, float ga, float ba, float aa ) {
		uColorM.value4f( rm, gm, bm, am );
		uColorA.value4f( ra, ga, ba, aa );
	}

	public void resetCamera() {
		lastCamera = null;
	}

	public void camera( Camera camera ) {
		if (camera == null) {
			camera = Camera.main;
		}
		if (camera != lastCamera && camera.matrix != null) {
			lastCamera = camera;
			uCamera.valueM4( camera.matrix );
			if (!camera.fullScreen) {
				PSPNative.scissor(
						Math.round(camera.x),
						Math.round(camera.y),
						Math.round(camera.screenWidth),
						Math.round(camera.screenHeight));
			} else {
				PSPNative.scissor( 0, 0, -1, -1 );
			}
		}
	}

	public static NoosaScript get() {
		return Script.use( NoosaScript.class );
	}

	protected String shader() {
		return "vertex//\nfragment";
	}
}
