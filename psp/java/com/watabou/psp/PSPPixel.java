/*
 * PSP port: pixel snapping for the bitmap pixel font. At the fractional UI zoom (1.7) a 3x5 font pixel
 * would cover 1 or 2 screen pixels unevenly; this draws it at a whole number of screen pixels
 * (grown around the label's centre) with the label's origin on a screen pixel.
 */
package com.watabou.psp;

import com.watabou.glwrap.Matrix;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;

public class PSPPixel {

	private static final float[] out = new float[16];

	/** Returns the model matrix to draw with (a shared array, valid until the next call). */
	public static float[] snap( float[] m, Camera c, float scaleX, float w, float h ) {
		if (c == null) return m;
		float s = c.zoom * scaleX;
		if (s <= 0) return m;
		System.arraycopy( m, 0, out, 0, 16 );
		int k = Math.max( 1, Math.round( s ) );
		if (Math.abs( k - s ) > 0.01f) {
			float f = k / s;
			Matrix.translate( out, w / 2, h / 2 );
			Matrix.scale( out, f, f );
			Matrix.translate( out, -w / 2, -h / 2 );
		}
		float[] cm = c.matrix;
		float hw = Game.width / 2f, hh = Game.height / 2f;
		float sx = Math.round( (cm[0] * out[12] + cm[12] + 1) * hw );
		float sy = Math.round( (1 - cm[5] * out[13] - cm[13]) * hh );
		out[12] = (sx / hw - 1 - cm[12]) / cm[0];
		out[13] = (1 - sy / hh - cm[13]) / cm[5];
		return out;
	}
}
