/*
 * PSP port: console-style menu navigation. Outside of the dungeon view (title screens, any open window)
 * the D-pad (or analog stick) jumps the controller pointer between clickable elements and Cross clicks
 * the focused one; PSPOverlay frames it, the pointer itself is never drawn. Targets are SPD's own
 * PointerAreas plus the rows of scrolling lists (one PointerArea for the whole list), so every window
 * works without per-window code. Left/right move a focused slider; up/down scroll a list or text when
 * there is nothing further to jump to.
 */
package com.shatteredpixel.shatteredpixeldungeon.psp;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.OptionSlider;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RadialMenu;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollingGridPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollingListPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.input.ControllerHandler;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.PointerArea;
import com.watabou.noosa.Scene;
import com.watabou.noosa.ScrollArea;
import com.watabou.noosa.Visual;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.PointF;
import com.watabou.utils.Signal;

import java.util.ArrayList;
import java.util.List;

public class PSPFocus {

	public static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;
	private static final float[] DX = { 0, 0, -1, 1 };
	private static final float[] DY = { -1, 1, 0, 0 };

	private static final float FIRST_REPEAT = 0.35f, REPEAT = 0.12f;

	/** Lists every target in the log when a window or scene appears (enabled by autotest scripts). */
	static boolean debug;
	private static int debugCount = -1;

	private static int heldDir = -1;
	private static float repeatTimer;
	private static Object context;
	private static int snapFrames;
	private static boolean emptyContext;
	private static int recheckFrames;
	// the focused element: the area a click goes to, and the visual that gives its rectangle
	private static PointerArea focusedArea;
	private static Gizmo focusedVisual;
	private static ScrollPane focusedPane;
	// Cross on a slider starts editing it: left/right change the value until Cross (or Circle) ends it
	private static OptionSlider editing;

	private static class Target {
		PointerArea area;          // receives the click (a list's own area for list rows)
		Gizmo v;                   // what is framed and scrolled into view (a Visual or a Component)
		float x0, y0, x1, y1;      // screen rect
		ScrollPane pane;           // set when the element lives in a scrolling list

		float cx() { return (x0 + x1) / 2f; }
		float cy() { return (y0 + y1) / 2f; }
	}

	/** True while the D-pad drives the menu cursor instead of the hero. */
	static boolean navigationMode() {
		Scene s = Game.scene();
		if (s == null || Game.switchingScene()) return false;
		if (!(s instanceof GameScene)) return true;
		Window w = topWindow(PointerEvent.listeners());
		if (w == null) return gameOver();
		return !(w instanceof RadialMenu);
	}

	private static final Object GAME_OVER = new Object();

	/** The hero died: the Game Over buttons on the dungeon view take the D-pad (no window open). */
	static boolean gameOver() {
		return Game.scene() instanceof GameScene && Dungeon.hero != null && !Dungeon.hero.isAlive()
				&& topWindow(PointerEvent.listeners()) == null;
	}

	/** True while a window is open over the dungeon view. */
	static boolean windowOpen() {
		return topWindow(PointerEvent.listeners()) != null;
	}

	/** RadialMenu (quickslot / inventory selector) picks by stick direction, not by focus. */
	static boolean radialMenu() {
		Scene s = Game.scene();
		return s != null && !Game.switchingScene() && topWindow(PointerEvent.listeners()) instanceof RadialMenu;
	}

	/**
	 * Per-frame update with the D-pad state (pressed directions as a bitmask of 1 << dir).
	 * Returns true when the D-pad was used for navigation.
	 */
	static boolean update(int dirs, float dt) {
		if (!navigationMode()) {
			heldDir = -1;
			context = null;
			focusedArea = null;
			focusedVisual = null;
			focusedPane = null;
			editing = null;
			// no pointer in the dungeon view: the cell cursor works on tiles directly
			if (ControllerHandler.controllerPointerActive() && !radialMenu()) ControllerHandler.setControllerPointer(false);
			return false;
		}

		List<Signal.Listener<PointerEvent>> ls = PointerEvent.listeners();
		Window w = topWindow(ls);
		Object ctx = w != null ? w : gameOver() ? GAME_OVER : Game.scene();
		if (ctx != context) {
			context = ctx;
			editing = null;
			snapFrames = 2;   // let the new window or scene finish its layout first
		}
		boolean snap = snapFrames > 0 && --snapFrames == 0;
		// a screen that had nothing to press (e.g. a loading screen) gets its first button later
		boolean recheck = !snap && emptyContext && (++recheckFrames % 10) == 0;
		if (snap || recheck || debug) {
			ArrayList<Target> targets = collect(ls);
			if (snap || (emptyContext && !targets.isEmpty())) snapToFirst(targets);
			emptyContext = targets.isEmpty();
			if (snap || targets.size() != debugCount) {
				debugCount = targets.size();
				PointF p = pointer();
				com.watabou.psp.PSPNative.log("focus " + context.getClass().getName() + ": " + targets.size() + " targets, pointer " + (int) p.x + "," + (int) p.y);
				if (debug) {
					if (w != null) {
						Camera c = w.camera;
						com.watabou.psp.PSPNative.log("  window at " + c.x + "," + c.y + " size " + c.screenWidth() + "x" + c.screenHeight()
								+ " zoom " + c.zoom + ((c.x < 0 || c.y < 0 || c.x + c.screenWidth() > Game.width || c.y + c.screenHeight() > Game.height) ? " OFFSCREEN" : ""));
					}
					for (Target t : targets) {
						Gizmo owner = t.v != t.area ? t.v : (t.area.parent != null ? t.area.parent : t.area);
						com.watabou.psp.PSPNative.log("  target " + (int) t.cx() + "," + (int) t.cy() + " " + owner.getClass().getName());
					}
				}
			}
		}

		int dir = -1;
		for (int d = 0; d < 4; d++) if ((dirs & (1 << d)) != 0) { dir = d; break; }
		if (dir < 0) {
			heldDir = -1;
		} else if (dir != heldDir) {
			heldDir = dir;
			repeatTimer = FIRST_REPEAT;
			step(dir, ls);
		} else if ((repeatTimer -= dt) <= 0) {
			repeatTimer += REPEAT;
			step(dir, ls);
		}
		return true;
	}

	private static OptionSlider focusedSlider() {
		if (focusedArea == null || !focusedArea.isActive() || !(focusedArea.parent instanceof OptionSlider)) return null;
		return (OptionSlider) focusedArea.parent;
	}

	/** True while the focused slider is being edited (the focus frame changes colour). */
	static boolean editingSlider() {
		return editing != null && editing == focusedSlider();
	}

	/** Cross and Circle on a focused slider toggle its editing instead of clicking it; true when used. */
	static boolean button(int code) {
		if (!navigationMode()) return false;
		OptionSlider s = focusedSlider();
		if (s == null) {
			editing = null;
			return false;
		}
		if (code == com.watabou.psp.PSPButtons.CROSS || (code == com.watabou.psp.PSPButtons.CIRCLE && editing == s)) {
			editing = editing == s ? null : s;
			if (debug) com.watabou.psp.PSPNative.log("slider edit " + (editing != null ? "on" : "off") + " value " + s.getSelectedValue());
			return true;
		}
		return false;
	}

	private static void step(int dir, List<Signal.Listener<PointerEvent>> ls) {
		// an edited slider keeps the focus: left/right change it, up/down do nothing
		OptionSlider s = focusedSlider();
		if (s != null && editing == s) {
			if (dir == LEFT || dir == RIGHT) {
				s.pspStep(dir == LEFT ? -1 : 1);
				if (debug) com.watabou.psp.PSPNative.log("slider value " + s.getSelectedValue());
			}
			return;
		}
		editing = null;
		ArrayList<Target> targets = collect(ls);
		if (!move(dir, targets) && (dir == UP || dir == DOWN)) scroll(dir, targets);
	}

	/** Screen rect (x0, y0, x1, y1) of the focused element; false when there is none to show. */
	static boolean focusRect(float[] out) {
		if (focusedArea == null || !navigationMode() || !focusedArea.isActive() || focusedArea.parent == null) return false;
		Gizmo v = focusedVisual;
		if (v == null || v.parent == null || (v == focusedArea ? !focusedArea.parent.isVisible() : !v.isVisible())) return false;
		return rectOf(v, out);
	}

	private static boolean rectOf(Gizmo g, float[] out) {
		Camera c = g.camera();
		if (c == null) return false;
		float x, y, w, h;
		if (g instanceof Visual) {
			Visual v = (Visual) g;
			x = v.x; y = v.y; w = v.width(); h = v.height();
		} else if (g instanceof Component) {
			Component k = (Component) g;
			x = k.left(); y = k.top(); w = k.width(); h = k.height();
		} else {
			return false;
		}
		out[0] = (x - c.scroll.x) * c.zoom + c.x;
		out[1] = (y - c.scroll.y) * c.zoom + c.y;
		out[2] = out[0] + w * c.zoom;
		out[3] = out[1] + h * c.zoom;
		return true;
	}

	// the newest open window: its blocker is the first active window-owned area in dispatch order
	private static Window topWindow(List<Signal.Listener<PointerEvent>> ls) {
		for (Signal.Listener<PointerEvent> l : ls) {
			if (l instanceof PointerArea) {
				PointerArea a = (PointerArea) l;
				if (a.parent instanceof Window && a.parent.parent != null && a.isActive()) return (Window) a.parent;
			}
		}
		return null;
	}

	private static ScrollPane paneOf(Gizmo g) {
		for (Gizmo p = g.parent; p != null; p = p.parent) {
			if (p instanceof ScrollPane) return (ScrollPane) p;
		}
		return null;
	}

	private static final float[] tmp = new float[4];

	private static ArrayList<Target> collect(List<Signal.Listener<PointerEvent>> ls) {
		ArrayList<Target> out = new ArrayList<>();
		boolean over = gameOver();
		for (Signal.Listener<PointerEvent> l : ls) {
			if (!(l instanceof PointerArea)) continue;
			PointerArea a = (PointerArea) l;
			if (!a.isActive() || a.parent == null) continue;
			// on Game Over only its buttons (added straight to the scene) count, not the HUD behind them
			if (over && (!(a.parent instanceof StyledButton) || a.parent.parent != Game.scene())) continue;
			if (l instanceof ScrollArea) {
				// rows of ScrollingListPane / ScrollingGridPane: clicks on the list's area reach the row under them
				if (a.parent instanceof ScrollPane && a.parent.isVisible()) {
					ScrollPane pane = (ScrollPane) a.parent;
					for (Gizmo g : pane.content().pspMembers()) {
						if ((g instanceof ScrollingListPane.ListItem || g instanceof ScrollingGridPane.GridItem) && g.isVisible()) {
							add(out, ls, a, g, pane);
						}
					}
				}
				continue;
			}
			Visual v = a.target;
			if (v == null) continue;
			// invisible hot areas follow their owner's visibility
			if (v == a ? !a.parent.isVisible() : !v.isVisible()) continue;
			add(out, ls, a, v, paneOf(a));
		}
		return out;
	}

	private static void add(ArrayList<Target> out, List<Signal.Listener<PointerEvent>> ls, PointerArea a, Gizmo v, ScrollPane pane) {
		float sw = Game.width, sh = Game.height;
		Camera c = v.camera();
		if (c == null || !rectOf(v, tmp)) return;
		float w = tmp[2] - tmp[0], h = tmp[3] - tmp[1];
		if (w < 2 || h < 2) return;
		if (w >= sw * 0.9f && h >= sh * 0.7f) return;   // window blockers, full-screen areas
		Target t = new Target();
		t.area = a;
		t.v = v;
		t.x0 = tmp[0]; t.y0 = tmp[1]; t.x1 = tmp[2]; t.y1 = tmp[3];
		t.pane = pane;
		float px = t.cx(), py = t.cy();
		if (pane != null) {
			// judge reachability at the list's viewport, the item gets scrolled into view on focus
			px = Math.max(c.x + 1, Math.min(c.x + c.width * c.zoom - 1, px));
			py = Math.max(c.y + 1, Math.min(c.y + c.height * c.zoom - 1, py));
		} else if (px < 0 || py < 0 || px >= sw || py >= sh || !c.hitTest(px, py)) {
			return;
		}
		if (reachable(ls, a, (int) px, (int) py)) out.add(t);
	}

	// mirrors PointerArea.onSignal: is 'a' the area a tap at (x, y) would reach, nothing blocking before it?
	private static boolean reachable(List<Signal.Listener<PointerEvent>> ls, PointerArea a, int x, int y) {
		for (Signal.Listener<PointerEvent> l : ls) {
			if (l == a) return true;
			if (!(l instanceof PointerArea) || l instanceof ScrollArea) continue;
			PointerArea o = (PointerArea) l;
			if (o.target == null || o.parent == null) continue;
			boolean hit = o.target.overlapsScreenPoint(x, y);
			if (!hit) continue;
			// full-screen areas usually just watch taps (HeroSelectScene's fade resetter); only window
			// backgrounds really block what lies under them
			if (!(o.parent instanceof Window) && o.target.width() * o.target.camera().zoom >= Game.width * 0.9f
					&& o.target.height() * o.target.camera().zoom >= Game.height * 0.7f) continue;
			if (o.isActive() ? o.blockLevel != PointerArea.NEVER_BLOCK : o.blockLevel == PointerArea.ALWAYS_BLOCK) {
				return false;
			}
		}
		return false;
	}

	private static PointF pointer() {
		return ControllerHandler.controllerPointerActive()
				? ControllerHandler.getControllerPointerPos()
				: new PointF(PointerEvent.currentHoverPos());
	}

	// jumps to the next element in 'dir'; elements in the same row (or column) as the focused one win
	private static boolean move(int dir, ArrayList<Target> targets) {
		PointF p = pointer();
		float[] cur = new float[4];
		if (!focusRect(cur)) { cur[0] = cur[2] = p.x; cur[1] = cur[3] = p.y; }
		float dx = DX[dir], dy = DY[dir];
		Target best = null;
		float bestScore = Float.MAX_VALUE;
		for (Target t : targets) {
			if (t.v == focusedVisual) continue;
			float vx = t.cx() - p.x, vy = t.cy() - p.y;
			float along = vx * dx + vy * dy;
			if (along <= 2) continue;
			boolean overlap = dx != 0 ? (t.y0 < cur[3] - 1 && t.y1 > cur[1] + 1) : (t.x0 < cur[2] - 1 && t.x1 > cur[0] + 1);
			float perp = Math.abs(vx * dy - vy * dx);
			float score = overlap ? along + perp * 0.5f : 100000 + along + perp * 2.5f;
			if (score < bestScore) {
				bestScore = score;
				best = t;
			}
		}
		if (best == null) return false;
		focus(best);
		return true;
	}

	// nothing further up/down: scroll the list or text the focus is in (or the first one on screen)
	private static void scroll(int dir, ArrayList<Target> targets) {
		ScrollPane pane = focusedPane;
		if (pane == null || pane.parent == null) {
			pane = null;
			for (Signal.Listener<PointerEvent> l : PointerEvent.listeners()) {
				if (l instanceof ScrollArea && ((PointerArea) l).isActive() && ((PointerArea) l).parent instanceof ScrollPane
						&& ((PointerArea) l).parent.isVisible()) {
					pane = (ScrollPane) ((PointerArea) l).parent;
					break;
				}
			}
		}
		if (pane == null) return;
		Camera c = pane.content().camera;
		float stepY = Math.max(12, pane.height() / 3f);
		pane.scrollTo(c.scroll.x, c.scroll.y + (dir == DOWN ? stepY : -stepY));
	}

	private static void snapToFirst(ArrayList<Target> targets) {
		PointF p = pointer();
		for (Target t : targets) {
			if (p.x >= t.x0 && p.x < t.x1 && p.y >= t.y0 && p.y < t.y1) {   // already on something
				focusedArea = t.area;
				focusedVisual = t.v;
				focusedPane = t.pane;
				return;
			}
		}
		Target best = null;
		for (Target t : targets) {
			if (best == null || t.y0 < best.y0 - 3 || (Math.abs(t.y0 - best.y0) <= 3 && t.x0 < best.x0)) best = t;
		}
		if (best != null) focus(best);
	}

	private static void focus(Target t) {
		focusedArea = t.area;
		focusedVisual = t.v;
		focusedPane = t.pane;
		float cx = t.cx(), cy = t.cy();
		if (t.pane != null) {
			Camera c = t.v.camera();
			float top = c.y, bottom = c.y + c.height * c.zoom;
			float shift = 0;
			if (t.y0 < top) shift = t.y0 - top - 2;
			else if (t.y1 > bottom) shift = t.y1 - bottom + 2;
			if (shift != 0) {
				t.pane.scrollTo(c.scroll.x, c.scroll.y + shift / c.zoom);
				if (rectOf(t.v, tmp)) cy = (tmp[1] + tmp[3]) / 2f;
			}
		}
		if (!ControllerHandler.controllerPointerActive()) ControllerHandler.setControllerPointer(true);
		ControllerHandler.updateControllerPointer(new PointF(cx, cy), true);
		if (debug) {
			Gizmo owner = t.v != t.area ? t.v : (t.area.parent != null ? t.area.parent : t.area);
			com.watabou.psp.PSPNative.log("  -> " + owner.getClass().getName() + " at " + (int) cx + "," + (int) cy);
		}
	}
}
