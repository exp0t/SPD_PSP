/*
 * PSP port: drawn on top of every scene instead of SPD's pointer arrow: a frame around the focused
 * menu element (PSPFocus) and around the tile under the cell cursor (PSPCellCursor).
 */
package com.shatteredpixel.shatteredpixeldungeon.psp;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.noosa.Camera;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Game;

public class PSPOverlay {

	// 0xRRGGBB without an alpha byte: Visual.hardlight(int) does not mask the red channel
	private static final int FOCUS_COLOR = 0x00FF44, EDIT_COLOR = 0xFF9922, TARGET_COLOR = 0xFF5533, CELL_COLOR = 0xFFFFFF;

	private static ColorBlock line;
	private static Object lineScene;   // scene changes can clear the texture cache, so one block per scene
	private static final float[] rect = new float[4];
	private static float time;

	public static void draw() {
		time += Game.elapsed;
		float pulse = 0.65f + 0.35f * (float) Math.abs(((time * 1.5f) % 2f) - 1f);
		PSPButtonHints.draw();
		if (PSPFocus.focusRect(rect)) {
			if (PSPFocus.editingSlider()) frame(rect[0] - 1, rect[1] - 1, rect[2] + 1, rect[3] + 1, 2, EDIT_COLOR, 1f);
			else frame(rect[0] - 1, rect[1] - 1, rect[2] + 1, rect[3] + 1, 2, FOCUS_COLOR, pulse);
			return;
		}
		int cell = PSPCellCursor.cell();
		if (cell >= 0 && Dungeon.level != null) {
			Camera c = Camera.main;
			int w = Dungeon.level.width();
			float x0 = (cell % w * DungeonTilemap.SIZE - c.scroll.x) * c.zoom + c.x;
			float y0 = (cell / w * DungeonTilemap.SIZE - c.scroll.y) * c.zoom + c.y;
			float s = DungeonTilemap.SIZE * c.zoom;
			frame(x0, y0, x0 + s, y0 + s, 2, PSPCellCursor.targeting() ? TARGET_COLOR : CELL_COLOR, pulse);
		}
	}

	// outline of a screen-pixel rectangle, 't' pixels thick, drawn through the UI camera
	private static void frame(float x0, float y0, float x1, float y1, float t, int color, float alpha) {
		Camera c = PixelScene.uiCamera;
		if (c == null) return;
		if (line == null || lineScene != Game.scene()) {
			line = new ColorBlock(1, 1, 0xFFFFFFFF);
			lineScene = Game.scene();
		}
		line.camera = c;
		line.hardlight(color);
		line.alpha(alpha);
		bar(c, x0, y0, x1 - x0, t);
		bar(c, x0, y1 - t, x1 - x0, t);
		bar(c, x0, y0 + t, t, y1 - y0 - 2 * t);
		bar(c, x1 - t, y0 + t, t, y1 - y0 - 2 * t);
	}

	private static void bar(Camera c, float x, float y, float w, float h) {
		if (w <= 0 || h <= 0) return;
		line.x = (x - c.x) / c.zoom + c.scroll.x;
		line.y = (y - c.y) / c.zoom + c.scroll.y;
		line.size(w / c.zoom, h / c.zoom);
		line.draw();
	}
}
