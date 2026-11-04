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
 * PSP port: the PSP reports itself as a console: no desktop, no hard keyboard.
 */

package com.watabou.utils;

import com.badlogic.gdx.Gdx;
import com.watabou.noosa.Game;

//TODO migrate to platformSupport class
public class DeviceCompat {

	//return APi level on Android, major OS version on iOS, 0 on desktop
	public static int getPlatformVersion(){
		return 0;
	}

	public static boolean isAndroid(){
		return false;
	}

	public static boolean isiOS(){
		return false;
	}

	public static boolean isDesktop(){
		return false;
	}

	public static boolean hasHardKeyboard(){
		return false;
	}

	public static boolean isDebug(){
		return Game.version.contains("INDEV");
	}

	public static void log( String tag, String message ){
		Gdx.app.log( tag, message );
	}

	public static float getRealPixelScaleX(){
		return 1f;
	}

	public static float getRealPixelScaleY(){
		return 1f;
	}

}
