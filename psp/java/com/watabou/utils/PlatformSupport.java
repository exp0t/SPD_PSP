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
 * PSP port: fonts are rendered by native FreeType (psp/native/font.c), so the libGDX font fields are gone.
 */

package com.watabou.utils;

import com.watabou.input.ControllerHandler;

public abstract class PlatformSupport {

	public abstract void updateDisplaySize();

	public boolean supportsFullScreen(){
		return true; //default
	}

	public static final int INSET_ALL = 3; //All insets, from hole punches to nav bars
	public static final int INSET_LRG = 2; //Only big insets, full size notches and nav bars
	public static final int INSET_BLK = 1; //only complete blocker assets like navbars

	public RectF getSafeInsets( int level ){
		return new RectF();
	}

	//returns a display cutout (if one is present) in device pixels, or empty if none is present
	public RectF getDisplayCutout(){
		return new RectF();
	}

	public abstract void updateSystemUI();

	public abstract boolean connectedToUnmeteredNetwork();

	public abstract boolean supportsVibration();

	public void vibrate( int millis ){
		if (ControllerHandler.isControllerConnected()) {
			ControllerHandler.vibrate(millis);
		}
	}

	public void setHonorSilentSwitch( boolean value ){
		//does nothing by default
	}

	public boolean openURI( String uri ){
		return false;
	}

	public void setOnscreenKeyboardVisible(boolean value, boolean multiline){
		//text input on the PSP uses the system OSK dialog, see TextInput
	}

	protected int pageSize;
	protected boolean systemfont;

	public abstract void setupFontGenerators(int pageSize, boolean systemFont );

	public abstract String[] splitforTextBlock( String text, boolean multiline );

	public void resetGenerators(){
		resetGenerators( true );
	}

	public void resetGenerators( boolean setupAfter ){
		if (setupAfter) setupFontGenerators(pageSize, systemfont);
	}

	public void reloadGenerators(){
	}

}
