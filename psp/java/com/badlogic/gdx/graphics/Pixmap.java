/*
 * PSP port: libGDX Pixmap subset. Pixels live in native memory (RGBA8888) and are used directly as GU textures.
 */
package com.badlogic.gdx.graphics;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.watabou.psp.PSPNative;

import org.teavm.interop.Address;

public class Pixmap {

	public enum Format { Alpha, Intensity, LuminanceAlpha, RGB565, RGBA4444, RGB888, RGBA8888 }
	public enum Blending { None, SourceOver }
	public enum Filter { NearestNeighbour, BiLinear }

	private int handle;
	private final int width, height;
	private int color = 0;
	private Blending blending = Blending.SourceOver;

	public Pixmap(FileHandle file) {
		handle = PSPNative.imgLoad(Address.ofData(PSPNative.cstr(file.fullPath())));
		if (handle == 0) throw new GdxRuntimeException("Couldn't load image: " + file.fullPath());
		width = PSPNative.imgWidth(handle);
		height = PSPNative.imgHeight(handle);
	}

	public Pixmap(int width, int height, Format format) {
		handle = PSPNative.imgCreate(width, height);
		if (handle == 0) throw new GdxRuntimeException("Out of memory for " + width + "x" + height + " image");
		this.width = width;
		this.height = height;
	}

	/** Native image handle (0 once disposed). */
	public int handle() { return handle; }

	public int getWidth() { return width; }
	public int getHeight() { return height; }
	public Format getFormat() { return Format.RGBA8888; }

	public void setBlending(Blending blending) { this.blending = blending; }
	public Blending getBlending() { return blending; }
	public void setFilter(Filter filter) { }

	public void setColor(int rgba) { color = rgba; }

	public void setColor(float r, float g, float b, float a) {
		color = ((int) (r * 255) << 24) | ((int) (g * 255) << 16) | ((int) (b * 255) << 8) | (int) (a * 255);
	}

	public void setColor(Color c) { setColor(c.r, c.g, c.b, c.a); }

	private int blend() { return blending == Blending.SourceOver ? 1 : 0; }

	public int getPixel(int x, int y) {
		if (x < 0 || y < 0 || x >= width || y >= height) return 0;
		return PSPNative.imgGet(handle, x, y);
	}

	public void drawPixel(int x, int y) { drawPixel(x, y, color); }

	public void drawPixel(int x, int y, int rgba) {
		if (x < 0 || y < 0 || x >= width || y >= height) return;
		PSPNative.imgSet(handle, x, y, rgba, blend());
	}

	public void fill() { PSPNative.imgFillRect(handle, 0, 0, width, height, color, 0); }

	public void fillRectangle(int x, int y, int w, int h) { PSPNative.imgFillRect(handle, x, y, w, h, color, blend()); }

	public void fillCircle(int cx, int cy, int radius) {
		for (int y = -radius; y <= radius; y++) {
			int dx = (int) Math.sqrt((float) (radius * radius - y * y));
			PSPNative.imgFillRect(handle, cx - dx, cy + y, dx * 2 + 1, 1, color, blend());
		}
	}

	public void drawPixmap(Pixmap src, int x, int y) {
		drawPixmap(src, 0, 0, src.width, src.height, x, y, src.width, src.height);
	}

	public void drawPixmap(Pixmap src, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh) {
		PSPNative.imgDraw(handle, src.handle, sx, sy, sw, sh, dx, dy, dw, dh, blend());
	}

	public void dispose() {
		if (handle != 0) PSPNative.imgFree(handle);
		handle = 0;
	}

	public boolean isDisposed() { return handle == 0; }
}
