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
 * PSP port: text is entered with the system on-screen keyboard (sceUtilityOsk); the box only displays it.
 */

package com.watabou.noosa;

import com.badlogic.gdx.Gdx;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.ui.Component;
import com.watabou.psp.PSPNative;

import org.teavm.interop.Address;

public class TextInput extends Component {

	private NinePatch bg;
	private RenderedText display;
	private PointerArea hotArea;

	private final boolean multiline;
	private final int size;
	private String text = "";
	private int maxLength = 255;
	private boolean openOnNextUpdate = true;

	public TextInput( NinePatch bg, boolean multiline, int size ){
		super();
		this.bg = bg;
		this.multiline = multiline;
		this.size = size;
		add(bg);

		display = new RenderedText( size );
		display.hardlight( 0x000000 );
		add(display);

		hotArea = new PointerArea( 0, 0, 0, 0 ) {
			@Override
			protected void onClick( PointerEvent event ) {
				openOnNextUpdate = true;
			}
		};
		add(hotArea);
	}

	public void enterPressed(){
		//fires any time enter is pressed, do nothing by default
	};

	public void onChanged(){
		//fires any time the text box is changed, do nothing by default
	}

	public void onClipBoardUpdate(){
		//fires any time the clipboard is updated via cut or copy, do nothing by default
	}

	public void setText(String text){
		this.text = text == null ? "" : text;
		if (this.text.length() > maxLength) this.text = this.text.substring(0, maxLength);
		refresh();
	}

	public void setMaxLength(int maxLength){
		this.maxLength = maxLength;
	}

	public String getText(){
		return text;
	}

	public void copyToClipboard(){
		Gdx.app.getClipboard().setContents(text);
		onClipBoardUpdate();
	}

	public void pasteFromClipboard(){
		String contents = Gdx.app.getClipboard().getContents();
		if (contents == null) return;
		setText(text + contents);
		onChanged();
	}

	private void refresh(){
		String shown = multiline ? text.replace('\n', ' ') : text;
		display.text( shown + "_" );
		layout();
	}

	// runs the blocking OSK dialog; the game loop is suspended while it is open
	private void openKeyboard(){
		char[] title = new char[0];
		char[] init = text.toCharArray();
		char[] out = new char[Math.max(maxLength, 1) + 1];
		int len = PSPNative.osk( Address.ofData(title), 0, Address.ofData(init), init.length, Address.ofData(out), maxLength );
		if (len >= 0) {
			String result = new String(out, 0, len);
			if (!multiline) result = result.replace('\n', ' ');
			setText(result);
			onChanged();
		}
	}

	@Override
	protected void layout() {
		super.layout();

		float contX = x;
		float contY = y;
		float contW = width;
		float contH = height;

		if (bg != null){
			bg.x = x;
			bg.y = y;
			bg.size(width, height);

			contX += bg.marginLeft();
			contY += bg.marginTop();
			contW -= bg.marginHor();
			contH -= bg.marginVer();
		}

		if (display != null) {
			display.x = contX;
			display.y = contY;
			// keep the end of long text visible
			display.x = Math.min(contX, contX + contW - display.width);
			PixelScene_align(display);
		}

		if (hotArea != null) {
			hotArea.x = x;
			hotArea.y = y;
			hotArea.width = width;
			hotArea.height = height;
		}
	}

	private static void PixelScene_align(Visual v){
		v.x = Math.round(v.x);
		v.y = Math.round(v.y);
	}

	@Override
	public void update() {
		super.update();
		if (openOnNextUpdate) {
			openOnNextUpdate = false;
			openKeyboard();
		}
	}

	@Override
	public synchronized void destroy() {
		super.destroy();
	}
}
