/*
 * PSP port: small PSP button glyphs on the dungeon HUD buttons (Select on the journal, Start on the menu,
 * Square on wait, ...). Buttons are found by the GameAction they answer to, so every HUD button bound to a
 * single PSP button gets its glyph. Glyphs are drawn at screen-pixel size in the corner of the button.
 * Keys that SPD gives to hidden zero-size buttons are shown on the visible button they belong to.
 */
package com.shatteredpixel.shatteredpixeldungeon.psp;

import com.badlogic.gdx.graphics.Pixmap;
import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.watabou.gltextures.SmartTexture;
import com.watabou.gltextures.TextureCache;
import com.watabou.glwrap.Texture;
import com.watabou.input.GameAction;
import com.watabou.input.KeyBindings;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.Image;
import com.watabou.psp.PSPButtons;

import java.util.ArrayList;

class PSPButtonHints {

	private static final String KEY = "psp_button_hints";
	private static final int H = 9;   // glyph height in screen pixels

	// glyphs in the atlas: { button, x, width }
	private static final int[][] GLYPHS = {
			{ PSPButtons.CROSS, 0, 9 }, { PSPButtons.CIRCLE, 9, 9 }, { PSPButtons.SQUARE, 18, 9 },
			{ PSPButtons.TRIANGLE, 27, 9 }, { PSPButtons.L, 36, 9 }, { PSPButtons.R, 45, 9 },
			{ PSPButtons.SELECT, 54, 27 }, { PSPButtons.START, 81, 23 },
	};

	private static class Hint {
		Button button;   // answers to the key
		Button anchor;   // where the glyph is drawn
		boolean left;    // bottom-left corner instead of bottom-right
		Image image;
		int glyph = -1;
	}

	private static final ArrayList<Hint> hints = new ArrayList<>();
	private static Object scene;
	private static int scanTimer;

	static void draw() {
		if (!(Game.scene() instanceof GameScene) || Game.switchingScene()) {
			hints.clear();
			scene = null;
			return;
		}
		if (PSPFocus.windowOpen() || GameScene.pspTargeting() || PSPFocus.gameOver()) return;
		if (scene != Game.scene()) {
			scene = Game.scene();
			hints.clear();
			scanTimer = 0;
		}
		if (--scanTimer <= 0) {
			scanTimer = 20;   // HUD buttons come and go (attack/loot indicators), but not every frame
			rescan();
		}
		SmartTexture tex = atlas();
		for (Hint h : hints) {
			Button b = h.anchor;
			if (b.parent == null || !b.isVisible() || !b.isActive() || h.button.parent == null) continue;
			int g = glyphFor(h.button);
			Camera c = b.camera();
			if (g < 0 || c == null) continue;
			if (h.image == null || h.image.texture != tex) h.image = new Image(tex);
			if (h.glyph != g) {
				h.glyph = g;
				h.image.frame(GLYPHS[g][1], 0, GLYPHS[g][2], H);
			}
			// bottom corner of the button, inset by a screen pixel, snapped to screen pixels
			float sx = h.left ? (float) Math.ceil((b.left() - c.scroll.x) * c.zoom + c.x) + 1
					: (float) Math.floor((b.right() - c.scroll.x) * c.zoom + c.x) - GLYPHS[g][2] - 1;
			float sy = (float) Math.floor((b.bottom() - c.scroll.y) * c.zoom + c.y) - H - 1;
			if (sy < Game.height / 2) {
				// buttons at the top of the screen (journal, menu): centred just under the button
				sx = (float) Math.floor(((b.left() + b.right()) / 2f - c.scroll.x) * c.zoom + c.x - GLYPHS[g][2] / 2f);
				sy += H + 2;
			}
			Image img = h.image;
			img.camera = c;
			img.scale.set(1f / c.zoom);
			img.x = (sx - c.x) / c.zoom + c.scroll.x;
			img.y = (sy - c.y) / c.zoom + c.scroll.y;
			img.draw();
		}
	}

	private static void rescan() {
		ArrayList<Button> all = new ArrayList<>();
		collect(Game.scene(), all);
		ArrayList<Hint> next = new ArrayList<>();
		for (Button b : all) {
			if (glyphFor(b) < 0) continue;
			Button anchor = b;
			boolean left = false;
			if (b.width() <= 0 || b.height() <= 0) {
				GameAction a = b.keyAction();
				anchor = null;
				left = true;
				if (a == SPDAction.WAIT_OR_PICKUP) { anchor = withAction(all, SPDAction.WAIT); left = false; }
				else if (a == SPDAction.INVENTORY_SELECTOR) anchor = withAction(all, SPDAction.INVENTORY);
				else if (a == SPDAction.QUICKSLOT_SELECTOR) anchor = firstQuickslot(all);
				if (anchor == null) continue;
			}
			Hint h = null;
			for (Hint o : hints) if (o.button == b && o.anchor == anchor) { h = o; break; }
			if (h == null) {
				h = new Hint();
				h.button = b;
				h.anchor = anchor;
				h.left = left;
				if (PSPFocus.debug) {
					com.watabou.psp.PSPNative.log("hint " + PSPButtons.name(GLYPHS[glyphFor(b)][0]) + " on " + anchor.getClass().getName()
							+ " at " + (int) anchor.left() + "," + (int) anchor.top() + " " + (int) anchor.width() + "x" + (int) anchor.height());
				}
			}
			next.add(h);
		}
		hints.clear();
		hints.addAll(next);
	}

	private static Button withAction(ArrayList<Button> all, GameAction a) {
		for (Button b : all) if (b.keyAction() == a && b.width() > 0) return b;
		return null;
	}

	private static Button firstQuickslot(ArrayList<Button> all) {
		Button best = null;
		for (Button b : all) {
			if (b.getClass().getName().endsWith("QuickslotTool") && b.visible && b.width() > 0
					&& (best == null || b.left() < best.left())) best = b;
		}
		return best;
	}

	private static void collect(Group g, ArrayList<Button> out) {
		for (Gizmo m : g.pspMembers()) {
			if (m == null || !m.exists) continue;
			if (m instanceof Button) {
				out.add((Button) m);
			} else if (m instanceof Group) {
				collect((Group) m, out);
			}
		}
	}

	private static int glyphFor(Button b) {
		GameAction a = b.keyAction();
		if (a == null) a = b.secondaryTooltipAction();
		if (a == null) return -1;
		int key;
		if (a == SPDAction.INVENTORY) key = PSPButtons.CIRCLE;       // PSPInput: Circle opens the bag in the dungeon
		else if (a == GameAction.BACK) key = PSPButtons.START;       // Circle is Back in windows only
		else key = KeyBindings.getFirstKeyForAction(a, true);
		for (int i = 0; i < GLYPHS.length; i++) if (GLYPHS[i][0] == key) return i;
		return -1;   // unbound, or a Select combo (too long for a corner)
	}

	// ---- atlas, drawn once per texture cache ----

	private static final int BG = 0x101010D8, WHITE = 0xF0F0F0FF;

	private static SmartTexture atlas() {
		if (TextureCache.contains(KEY)) return TextureCache.get(KEY);
		SmartTexture t = TextureCache.create(KEY, 128, 16);
		t.filter(Texture.NEAREST, Texture.NEAREST);
		Pixmap pm = t.bitmap;
		pm.setBlending(Pixmap.Blending.None);
		pm.setColor(0);
		pm.fill();
		disc(pm, 0);  shape(pm, 2, 2, 0x7FA8FFFF, "X...X", ".X.X.", "..X..", ".X.X.", "X...X");
		disc(pm, 9);  shape(pm, 11, 2, 0xFF6B6BFF, ".XXX.", "X...X", "X...X", "X...X", ".XXX.");
		disc(pm, 18); shape(pm, 20, 2, 0xFF8AD8FF, "XXXXX", "X...X", "X...X", "X...X", "XXXXX");
		disc(pm, 27); shape(pm, 29, 2, 0x4FE3B0FF, "..X..", ".X.X.", ".X.X.", "X...X", "XXXXX");
		pill(pm, 36, 9); text(pm, 39, "L");
		pill(pm, 45, 9); text(pm, 48, "R");
		pill(pm, 54, 27); text(pm, 56, "SELECT");
		pill(pm, 81, 23); text(pm, 83, "START");
		t.bitmap(pm);
		return t;
	}

	private static void disc(Pixmap pm, int x0) {
		for (int y = 0; y < H; y++)
			for (int x = 0; x < 9; x++) {
				float dx = x - 4, dy = y - 4;
				if (dx * dx + dy * dy <= 20.5f) pm.drawPixel(x0 + x, y, BG);
			}
	}

	private static void pill(Pixmap pm, int x0, int w) {
		for (int y = 0; y < H; y++)
			for (int x = 0; x < w; x++) {
				boolean corner = (x == 0 || x == w - 1) && (y == 0 || y == H - 1);
				if (!corner) pm.drawPixel(x0 + x, y, BG);
			}
	}

	private static void shape(Pixmap pm, int x0, int y0, int color, String... rows) {
		for (int y = 0; y < rows.length; y++)
			for (int x = 0; x < rows[y].length(); x++)
				if (rows[y].charAt(x) == 'X') pm.drawPixel(x0 + x, y0 + y, color);
	}

	private static void text(Pixmap pm, int x0, String s) {
		for (int i = 0; i < s.length(); i++) shape(pm, x0 + i * 4, 2, WHITE, letter(s.charAt(i)));
	}

	private static String[] letter(char c) {
		switch (c) {
			case 'S': return new String[]{ "XXX", "X..", "XXX", "..X", "XXX" };
			case 'E': return new String[]{ "XXX", "X..", "XX.", "X..", "XXX" };
			case 'L': return new String[]{ "X..", "X..", "X..", "X..", "XXX" };
			case 'C': return new String[]{ "XXX", "X..", "X..", "X..", "XXX" };
			case 'T': return new String[]{ "XXX", ".X.", ".X.", ".X.", ".X." };
			case 'A': return new String[]{ ".X.", "X.X", "XXX", "X.X", "X.X" };
			case 'R': return new String[]{ "XX.", "X.X", "XX.", "X.X", "X.X" };
			default:  return new String[0];
		}
	}
}
