/*
 * PSP port: the PSP has no L2/R2, no stick clicks and no right stick. Every physical button maps to one
 * libGDX controller key code; holding Select gives a second layer that reuses the codes the PSP lacks.
 */
package com.watabou.psp;

import com.badlogic.gdx.Input;
import com.watabou.input.ControllerHandler;

public final class PSPButtons {

	// first layer
	public static final int CROSS    = Input.Keys.BUTTON_A;
	public static final int CIRCLE   = Input.Keys.BUTTON_B;
	public static final int SQUARE   = Input.Keys.BUTTON_X;
	public static final int TRIANGLE = Input.Keys.BUTTON_Y;
	public static final int L        = Input.Keys.BUTTON_L1;
	public static final int R        = Input.Keys.BUTTON_R1;
	public static final int START    = Input.Keys.BUTTON_START;
	public static final int SELECT   = Input.Keys.BUTTON_SELECT; // sent when Select is tapped on its own

	// Select held
	public static final int SEL_CROSS    = Input.Keys.BUTTON_THUMBR;
	public static final int SEL_CIRCLE   = Input.Keys.BUTTON_THUMBL;
	public static final int SEL_SQUARE   = Input.Keys.BUTTON_C;
	public static final int SEL_TRIANGLE = Input.Keys.BUTTON_Z;
	public static final int SEL_L        = Input.Keys.BUTTON_L2;
	public static final int SEL_R        = Input.Keys.BUTTON_R2;
	public static final int SEL_START    = Input.Keys.BUTTON_MODE;
	public static final int SEL_UP       = Input.Keys.DPAD_UP + ControllerHandler.DPAD_KEY_OFFSET;
	public static final int SEL_DOWN     = Input.Keys.DPAD_DOWN + ControllerHandler.DPAD_KEY_OFFSET;
	public static final int SEL_LEFT     = Input.Keys.DPAD_LEFT + ControllerHandler.DPAD_KEY_OFFSET;
	public static final int SEL_RIGHT    = Input.Keys.DPAD_RIGHT + ControllerHandler.DPAD_KEY_OFFSET;

	// names shown in key binding windows and hints
	public static String name(int keyCode) {
		if (keyCode == CROSS) return "Cross";
		if (keyCode == CIRCLE) return "Circle";
		if (keyCode == SQUARE) return "Square";
		if (keyCode == TRIANGLE) return "Triangle";
		if (keyCode == L) return "L";
		if (keyCode == R) return "R";
		if (keyCode == START) return "Start";
		if (keyCode == SELECT) return "Select";
		if (keyCode == SEL_CROSS) return "Select+Cross";
		if (keyCode == SEL_CIRCLE) return "Select+Circle";
		if (keyCode == SEL_SQUARE) return "Select+Square";
		if (keyCode == SEL_TRIANGLE) return "Select+Triangle";
		if (keyCode == SEL_L) return "Select+L";
		if (keyCode == SEL_R) return "Select+R";
		if (keyCode == SEL_START) return "Select+Start";
		if (keyCode == SEL_UP) return "Select+Up";
		if (keyCode == SEL_DOWN) return "Select+Down";
		if (keyCode == SEL_LEFT) return "Select+Left";
		if (keyCode == SEL_RIGHT) return "Select+Right";
		return null;
	}
}
