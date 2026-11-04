/*
 * PSP port: tile cursor that replaces SPD's free controller pointer in the dungeon.
 * - When the game asks for a cell (throwing, wands, examine...), the cursor appears on the last or
 *   nearest visible enemy; the D-pad or analog stick moves it one tile at a time, Cross picks the cell,
 *   Select+Cross right-clicks it, Circle cancels.
 * - Outside of that, Cross opens the same cursor on the hero to act on any tile (walk there, attack,
 *   open, pick up), like clicking it with a mouse.
 */
package com.shatteredpixel.shatteredpixeldungeon.psp;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.shatteredpixel.shatteredpixeldungeon.ui.QuickSlotButton;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.psp.PSPButtons;
import com.watabou.utils.PointF;

public class PSPCellCursor {

	private static final float FIRST_REPEAT = 0.3f, REPEAT = 0.1f;

	private static boolean manual;       // opened with Cross, not by a targeting request
	private static boolean wasTargeting;
	private static int cell = -1;
	private static int heldDx, heldDy;
	private static float repeatTimer;

	static boolean gameplay() {
		return Game.scene() instanceof GameScene && !Game.switchingScene()
				&& Dungeon.hero != null && Dungeon.hero.isAlive() && Dungeon.level != null && !PSPFocus.windowOpen();
	}

	/** The cell under the cursor, or -1 when it is hidden. */
	static int cell() {
		return gameplay() && (manual || GameScene.pspTargeting()) ? cell : -1;
	}

	static boolean targeting() {
		return GameScene.pspTargeting();
	}

	/** Per-frame update with the pressed directions (bitmask of 1 << PSPFocus dir); true while the cursor uses them. */
	static boolean update(int dirs, float dt) {
		if (!gameplay()) {
			manual = false;
			wasTargeting = false;
			return false;
		}
		boolean targeting = GameScene.pspTargeting();
		if (targeting && !wasTargeting) {
			manual = false;
			cell = initialTarget();
			heldDx = heldDy = 0;
			log("target cursor at " + cell + " (hero " + Dungeon.hero.pos + ")");
		} else if (!targeting && wasTargeting && !manual) {
			followHero();
		}
		wasTargeting = targeting;
		if (!manual && !targeting) return false;

		int dx = ((dirs & (1 << PSPFocus.RIGHT)) != 0 ? 1 : 0) - ((dirs & (1 << PSPFocus.LEFT)) != 0 ? 1 : 0);
		int dy = ((dirs & (1 << PSPFocus.DOWN)) != 0 ? 1 : 0) - ((dirs & (1 << PSPFocus.UP)) != 0 ? 1 : 0);
		if (dx == 0 && dy == 0) {
			heldDx = heldDy = 0;
		} else if (dx != heldDx || dy != heldDy) {
			heldDx = dx;
			heldDy = dy;
			repeatTimer = FIRST_REPEAT;
			step(dx, dy);
		} else if ((repeatTimer -= dt) <= 0) {
			repeatTimer += REPEAT;
			step(dx, dy);
		}
		return true;
	}

	/** Handles a button press in the dungeon; true when the press was used here (and must not reach SPD). */
	static boolean button(int code) {
		if (!gameplay()) return false;
		boolean active = manual || GameScene.pspTargeting();
		if (!active) {
			if (code == PSPButtons.CROSS || code == PSPButtons.SEL_CROSS) {
				manual = true;
				cell = Dungeon.hero.pos;
				heldDx = heldDy = 0;
				log("tile cursor at " + cell);
				return true;
			}
			return false;
		}
		if (code == PSPButtons.CROSS || code == PSPButtons.SEL_CROSS) {
			int c = cell;
			boolean wasManual = manual;
			log((code == PSPButtons.CROSS ? "pick " : "right-click ") + c);
			manual = false;
			if (code == PSPButtons.CROSS) GameScene.handleCell(c);
			else GameScene.handleCellRight(c);
			if (wasManual && !GameScene.pspTargeting()) followHero();
			return true;
		}
		if (code == PSPButtons.CIRCLE || code == PSPButtons.START) {
			log("cursor cancelled");
			if (manual) {
				manual = false;
				followHero();
			} else {
				GameScene.cancelCellSelector();
			}
			return true;
		}
		return false;
	}

	private static void log(String s) {
		if (PSPFocus.debug) com.watabou.psp.PSPNative.log("cell " + s);
	}

	private static void step(int dx, int dy) {
		int w = Dungeon.level.width(), h = Dungeon.level.height();
		int x = Math.max(0, Math.min(w - 1, cell % w + dx));
		int y = Math.max(0, Math.min(h - 1, cell / w + dy));
		cell = x + y * w;
		keepVisible();
	}

	// pans the dungeon camera when the cursor gets close to the screen edge
	private static void keepVisible() {
		Camera c = Camera.main;
		int w = Dungeon.level.width();
		float wx = (cell % w + 0.5f) * DungeonTilemap.SIZE, wy = (cell / w + 0.5f) * DungeonTilemap.SIZE;
		float sx = (wx - c.scroll.x) * c.zoom + c.x, sy = (wy - c.scroll.y) * c.zoom + c.y;
		float margin = DungeonTilemap.SIZE * c.zoom * 2.5f;
		if (sx < margin || sx > Game.width - margin || sy < margin || sy > Game.height - margin) {
			c.panTo(new PointF(wx, wy), 8f);
		}
	}

	private static void followHero() {
		if (Dungeon.hero != null && Dungeon.hero.sprite != null) Camera.main.panFollow(Dungeon.hero.sprite, 5f);
	}

	private static int initialTarget() {
		Hero hero = Dungeon.hero;
		Char last = QuickSlotButton.lastTarget;
		if (last != null && last.isAlive() && last.pos >= 0 && Dungeon.level.heroFOV[last.pos]) return last.pos;
		Mob best = null;
		int bestDist = Integer.MAX_VALUE;
		for (int i = 0; i < hero.visibleEnemies(); i++) {
			Mob m = hero.visibleEnemy(i);
			int d = Dungeon.level.distance(hero.pos, m.pos);
			if (d < bestDist) {
				bestDist = d;
				best = m;
			}
		}
		return best != null ? best.pos : hero.pos;
	}
}
