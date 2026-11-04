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
 * PSP port: there is no mouse cursor on the PSP.
 */

package com.watabou.noosa.ui;

import com.watabou.utils.PointF;

public class Cursor {

	public enum Type {

		DEFAULT("gdx/cursor_mouse.png"),
		CONTROLLER("gdx/cursor_controller.png");

		public final String file;

		Type(String file){
			this.file = file;
		}

	}

	public static void setCustomCursor(Type type, int zoom){
	}

	public static void captureCursor(boolean captured){
	}

	public static PointF getCursorDelta(){
		return new PointF();
	}

	public static boolean isCursorCaptured(){
		return false;
	}

}
