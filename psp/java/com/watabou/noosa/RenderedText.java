/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2026 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * PSP port: glyphs are rasterized and cached by native FreeType (psp/native/psp_font.c) and drawn as quads.
 * They are rasterized at the size the text really has on screen (not at SPD's oversized render size
 * scaled down), one font pixel per screen pixel, so text stays sharp at the fractional UI zoom.
 */

package com.watabou.noosa;

import com.watabou.glscripts.Script;
import com.watabou.glwrap.Matrix;
import com.watabou.psp.PSPNative;

import org.teavm.interop.Address;

public class RenderedText extends Image {

	// screen pixels per unit of 'size' for UI text: uiZoom / defaultZoom (set by PixelScene)
	public static float screenScale = 1f;

	private int size;
	private String text;
	private char[] chars;

	public RenderedText( ) {
		text = null;
	}

	public RenderedText( int size ){
		text = null;
		this.size = size;
	}

	public RenderedText(String text, int size){
		this.text = text;
		this.size = size;

		measure();
	}

	public void text( String text ){
		this.text = text;

		measure();
	}

	public String text(){
		return text;
	}

	public void size( int size ){
		this.size = size;
		measure();
	}

	private static final float[] metrics = new float[2];

	private synchronized void measure(){

		if ( text == null || text.equals("") ) {
			text = "";
			chars = null;
			width=height=0;
			visible = false;
			return;
		} else {
			visible = true;
		}

		chars = text.toCharArray();
		PSPNative.fontMeasure( Address.ofData( chars ), chars.length, pixelSize( screenScale ), Address.ofData( metrics ) );
		width = metrics[0] / screenScale;

		//this is identical to l.height in most cases, but we force this for consistency.
		height = Math.round(size*0.75f);
		renderedHeight = metrics[1] / screenScale;
	}

	private int pixelSize( float scale ) {
		return Math.max( 1, Math.round( size * scale ) );
	}

	private float renderedHeight = 0;

	@Override
	protected void updateMatrix() {
		super.updateMatrix();
		//sometimes the font is rendered oddly, so we offset here to put it in the correct spot
		if (renderedHeight != height) {
			Matrix.translate(matrix, 0, Math.round(height - renderedHeight));
		}
	}

	@Override
	public synchronized void draw() {
		if (chars != null) {
			Camera c = camera();
			float s = c.zoom * scale.x;
			if (s <= 0) return;
			updateMatrix();

			// snap the origin to a screen pixel and draw glyph pixels 1:1 on screen
			float[] cm = c.matrix;
			float hw = Game.width / 2f, hh = Game.height / 2f;
			float sx = Math.round( (cm[0] * matrix[12] + cm[12] + 1) * hw );
			float sy = Math.round( (1 - cm[5] * matrix[13] - cm[13]) * hh );
			matrix[12] = (sx / hw - 1 - cm[12]) / cm[0];
			matrix[13] = (1 - sy / hh - cm[13]) / cm[5];
			Matrix.scale( matrix, 1 / s, 1 / s );

			NoosaScript script = NoosaScript.get();
			com.watabou.glwrap.Texture.clear();

			script.camera( c );

			script.uModel.valueM4( matrix );
			script.lighting(
					rm, gm, bm, am,
					ra, ga, ba, aa );

			PSPNative.fontDraw( Address.ofData( chars ), chars.length, pixelSize( s ) );
		}
	}
}
