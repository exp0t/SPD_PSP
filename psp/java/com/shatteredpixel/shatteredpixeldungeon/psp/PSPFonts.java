/*
 * PSP port: the GU only takes textures up to 512 px wide, and SPD's pixel font (HUD numbers, item counts)
 * is a 1024x8 strip, which the renderer would otherwise shrink (and blur) to 512x4. Its glyphs are copied
 * into a 512 px wide atlas, row after row, and the font's frames are pointed at the new places.
 */
package com.shatteredpixel.shatteredpixeldungeon.psp;

import com.badlogic.gdx.graphics.Pixmap;
import com.watabou.gltextures.SmartTexture;
import com.watabou.gltextures.TextureCache;
import com.watabou.glwrap.Texture;
import com.watabou.noosa.BitmapText;
import com.watabou.utils.RectF;

import java.util.HashMap;
import java.util.Map;

public class PSPFonts {

	private static final int MAX = 512;
	private static final String KEY = "psp_pixelfont";

	public static void repack( BitmapText.Font font ) {
		SmartTexture src = font.texture;
		int sw = src.width, sh = src.height;
		if (sw <= MAX && sh <= MAX) return;
		HashMap<Object, RectF> frames = font.pspFrames();

		// shelf layout: same order as in the strip, one row per MAX pixels, 1 px apart
		HashMap<RectF, int[]> place = new HashMap<>();
		int x = 0, y = 0;
		for (RectF r : frames.values()) {
			int w = Math.round(r.width() * sw);
			if (place.containsKey(r) || w > MAX) continue;   // the whole-texture frame stays as it is
			if (x + w > MAX) { x = 0; y += sh + 1; }
			place.put(r, new int[]{ x, y });
			x += w + 1;
		}
		int width = MAX, height = y + sh;

		SmartTexture dst;
		if (TextureCache.contains(KEY)) {
			dst = TextureCache.get(KEY);
		} else {
			dst = TextureCache.create(KEY, width, height);
			dst.filter(Texture.NEAREST, Texture.NEAREST);   // create() defaults to linear
			Pixmap pm = dst.bitmap;
			pm.setBlending(Pixmap.Blending.None);
			for (Map.Entry<RectF, int[]> e : place.entrySet()) {
				RectF r = e.getKey();
				int gx = Math.round(r.left * sw), gy = Math.round(r.top * sh);
				int gw = Math.round(r.width() * sw), gh = Math.round(r.height() * sh);
				pm.drawPixmap(src.bitmap, gx, gy, gw, gh, e.getValue()[0], e.getValue()[1] + gy, gw, gh);
			}
			dst.bitmap(pm);
		}

		// frames are shared between characters, so map each distinct rect once
		HashMap<RectF, RectF> moved = new HashMap<>();
		for (Map.Entry<Object, RectF> e : frames.entrySet()) {
			RectF r = e.getValue();
			if (!place.containsKey(r)) continue;
			RectF n = moved.get(r);
			if (n == null) {
				int[] p = place.get(r);
				float gw = r.width() * sw, gh = r.height() * sh, gy = r.top * sh;
				n = new RectF(p[0] / (float) width, (p[1] + gy) / height,
						(p[0] + gw) / width, (p[1] + gy + gh) / height);
				moved.put(r, n);
			}
			e.setValue(n);
		}
		font.pspRetarget(width, height);
		font.texture = dst;
	}
}
