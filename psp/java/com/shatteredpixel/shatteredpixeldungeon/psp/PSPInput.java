/*
 * PSP port: reads the PSP buttons and analog stick each frame and feeds them to SPD as a game controller.
 * There is no free pointer: the D-pad and the analog stick move the hero (SPD's left stick), pick menu
 * items (PSPFocus) or move the tile cursor (PSPCellCursor). Holding Select switches the face buttons,
 * L/R, Start and the D-pad to a second layer (see PSPButtons).
 */
package com.shatteredpixel.shatteredpixeldungeon.psp;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;
import com.watabou.input.ControllerHandler;
import com.watabou.input.KeyEvent;
import com.watabou.noosa.Game;
import com.watabou.psp.PSPButtons;
import com.watabou.psp.PSPNative;

import org.teavm.interop.Address;

public class PSPInput implements Input {

	// PSP_CTRL_* bits from pspctrl.h
	static final int SELECT = 0x000001, START = 0x000008, UP = 0x000010, RIGHT = 0x000020, DOWN = 0x000040, LEFT = 0x000080;
	static final int LTRIGGER = 0x000100, RTRIGGER = 0x000200, TRIANGLE = 0x001000, CIRCLE = 0x002000, CROSS = 0x004000, SQUARE = 0x008000;

	static final int[] BITS     = { CROSS, CIRCLE, SQUARE, TRIANGLE, LTRIGGER, RTRIGGER, START, UP, DOWN, LEFT, RIGHT };
	static final int[] LAYER1   = { PSPButtons.CROSS, PSPButtons.CIRCLE, PSPButtons.SQUARE, PSPButtons.TRIANGLE,
			PSPButtons.L, PSPButtons.R, PSPButtons.START, 0, 0, 0, 0 };
	static final int[] LAYER2   = { PSPButtons.SEL_CROSS, PSPButtons.SEL_CIRCLE, PSPButtons.SEL_SQUARE, PSPButtons.SEL_TRIANGLE,
			PSPButtons.SEL_L, PSPButtons.SEL_R, PSPButtons.SEL_START,
			PSPButtons.SEL_UP, PSPButtons.SEL_DOWN, PSPButtons.SEL_LEFT, PSPButtons.SEL_RIGHT };
	static final int DPAD = UP | DOWN | LEFT | RIGHT;

	static final int AXIS_LX = 0, AXIS_LY = 1, AXIS_RX = 2, AXIS_RY = 3;

	// only the axes go through the libGDX controller path; buttons are sent as key events directly
	static final ControllerMapping MAPPING = new ControllerMapping(
			AXIS_LX, AXIS_LY, AXIS_RX, AXIS_RY,
			-1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1);

	static final Controller CONTROLLER = new Controller() {
		public String getName() { return "PSP"; }
		public ControllerMapping getMapping() { return MAPPING; }
		public boolean canVibrate() { return false; }
		public void startVibration(int duration, float strength) { }
	};

	private InputProcessor processor;
	private final int[] state = new int[3];
	private int lastButtons;
	// key code each held button sent on press, so the release matches even if Select changed meanwhile
	private final int[] sent = new int[BITS.length];
	private boolean selectUsed;
	private float lastLX, lastLY, lastRX, lastRY;

	public PSPInput() {
		Controllers.current = CONTROLLER;
	}

	private static void key(int code, boolean down) {
		ControllerHandler.controllerActive = true;
		ControllerHandler.lastUsedType = ControllerHandler.ControllerType.PLAYSTATION;
		KeyEvent.addKeyEvent(new KeyEvent(code, down));
	}

	// Test script (autotest.txt next to the EBOOT): lines "<frame> <BUTTON>[+BUTTON...]" press for 6 frames,
	// "<frame> EXIT" quits cleanly (killing PPSSPP loses the last file writes)
	private static final int EXIT = 1 << 30;
	private int frame;
	private int[][] script;
	boolean exitRequested;

	private static int bit(String name) {
		switch (name) {
			case "CROSS": return CROSS; case "CIRCLE": return CIRCLE; case "SQUARE": return SQUARE; case "TRIANGLE": return TRIANGLE;
			case "L": return LTRIGGER; case "R": return RTRIGGER; case "START": return START; case "SELECT": return SELECT;
			case "UP": return UP; case "DOWN": return DOWN; case "LEFT": return LEFT; case "RIGHT": return RIGHT;
			case "EXIT": return EXIT;
			default: return 0;
		}
	}

	private int scripted() {
		if (script == null) {
			com.badlogic.gdx.files.FileHandle f = new com.badlogic.gdx.files.FileHandle("autotest.txt", com.badlogic.gdx.Files.FileType.Absolute);
			java.util.ArrayList<int[]> steps = new java.util.ArrayList<>();
			if (f.exists()) {
				for (String line : f.readString().split("\n")) {
					String[] p = line.trim().split("\\s+");
					if (p.length < 2 || p[0].startsWith("#")) continue;
					int mask = 0;
					for (String b : p[1].split("\\+")) mask |= bit(b);
					steps.add(new int[] { Integer.parseInt(p[0]), mask });
				}
				PSPNative.log("autotest: " + steps.size() + " steps");
				PSPFocus.debug = true;
			}
			script = steps.toArray(new int[0][]);
		}
		int mask = 0;
		for (int[] s : script) if (frame >= s[0] && frame < s[0] + 6) mask |= s[1];
		return mask;
	}

	void poll() {
		PSPNative.inputPoll(Address.ofData(state));
		frame++;
		int buttons = state[0] | scripted();
		if ((buttons & EXIT) != 0) {
			exitRequested = true;
			buttons &= ~EXIT;
		}
		boolean select = (buttons & SELECT) != 0;

		if (select && (lastButtons & SELECT) == 0) selectUsed = false;

		for (int i = 0; i < BITS.length; i++) {
			int bit = BITS[i];
			boolean now = (buttons & bit) != 0, was = (lastButtons & bit) != 0;
			if (now && !was) {
				int code = select ? LAYER2[i] : LAYER1[i];
				if (select) selectUsed = true;
				if (code != 0 && (PSPFocus.button(code) || PSPCellCursor.button(code))) code = 0;   // used by slider editing or the tile cursor
				// in the dungeon Circle opens the inventory (Start already pauses); in windows it stays Back
				else if (code == PSPButtons.CIRCLE && PSPCellCursor.gameplay()) code = PSPButtons.SEL_START;
				sent[i] = code;
				if (code != 0) key(code, true);
			} else if (!now && was) {
				if (sent[i] != 0) key(sent[i], false);
				sent[i] = 0;
			}
		}

		// Select tapped on its own
		if (!select && (lastButtons & SELECT) != 0 && !selectUsed) {
			key(PSPButtons.SELECT, true);
			key(PSPButtons.SELECT, false);
		}
		lastButtons = buttons;

		// the D-pad walks only while it is not used for Select combos
		int walk = select ? 0 : buttons & DPAD;
		for (int i = 7; i < BITS.length; i++) if (sent[i] != 0) walk &= ~BITS[i];
		// analog stick: 0..255, centre 128, with a dead zone; acts as a second D-pad (with diagonals)
		float ax = stick(state[1]), ay = stick(state[2]);
		int dirs = ((walk & UP) != 0 || ay < -0.5f ? 1 << PSPFocus.UP : 0) | ((walk & DOWN) != 0 || ay > 0.5f ? 1 << PSPFocus.DOWN : 0)
				| ((walk & LEFT) != 0 || ax < -0.5f ? 1 << PSPFocus.LEFT : 0) | ((walk & RIGHT) != 0 || ax > 0.5f ? 1 << PSPFocus.RIGHT : 0);
		float sx = Math.max(-1f, Math.min(1f, ((walk & RIGHT) != 0 ? 1 : 0) - ((walk & LEFT) != 0 ? 1 : 0) + ax));
		float sy = Math.max(-1f, Math.min(1f, ((walk & DOWN) != 0 ? 1 : 0) - ((walk & UP) != 0 ? 1 : 0) + ay));
		float lx = 0, ly = 0, rx = 0, ry = 0;
		if (PSPFocus.radialMenu()) {
			// SPD's radial selector reads the right stick
			rx = sx;
			ry = sy;
		} else if (!PSPFocus.update(dirs, Game.frameDeltaTime) && !PSPCellCursor.update(dirs, Game.frameDeltaTime)) {
			lx = sx;
			ly = sy;
		}
		axis(AXIS_RX, rx, lastRX); lastRX = rx;
		axis(AXIS_RY, ry, lastRY); lastRY = ry;
		axis(AXIS_LX, lx, lastLX); lastLX = lx;
		axis(AXIS_LY, ly, lastLY); lastLY = ly;
	}

	// -1..1 past a dead zone
	private static float stick(int raw) {
		float v = (raw - 128) / 127f;
		float a = Math.abs(v);
		if (a < 0.25f) return 0;
		return Math.signum(v) * Math.min(1f, (a - 0.25f) / 0.6f);
	}
	private static void axis(int code, float value, float last) {
		if (value == last) return;
		for (ControllerListener l : Controllers.listeners.toArray(new ControllerListener[0])) {
			l.axisMoved(CONTROLLER, code, value);
		}
	}

	public void setInputProcessor(InputProcessor processor) { this.processor = processor; }
	public InputProcessor getInputProcessor() { return processor; }
	public void setCatchKey(int keycode, boolean catchKey) { }
	public void setCursorCatched(boolean catched) { }
	public void setCursorPosition(int x, int y) { }
	public boolean isPeripheralAvailable(Peripheral peripheral) { return false; }
	public int getDeltaX() { return 0; }
	public int getDeltaY() { return 0; }
}
