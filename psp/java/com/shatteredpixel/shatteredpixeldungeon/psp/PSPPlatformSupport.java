/*
 * PSP port: platform hooks. Fixed 480x272 screen, no network, text split without regexes.
 */
package com.shatteredpixel.shatteredpixeldungeon.psp;

import com.watabou.utils.PlatformSupport;

import java.util.ArrayList;

public class PSPPlatformSupport extends PlatformSupport {

	@Override
	public void updateDisplaySize() { }

	@Override
	public boolean supportsFullScreen() { return false; }

	@Override
	public void updateSystemUI() { }

	@Override
	public boolean connectedToUnmeteredNetwork() { return false; }

	@Override
	public boolean supportsVibration() { return false; }

	@Override
	public void setupFontGenerators(int pageSize, boolean systemFont) {
		this.pageSize = pageSize;
		this.systemfont = systemFont;
	}

	// Hiragana, Katakana, CJK symbols/punctuation and unified ideographs
	private static boolean isCJK(char c) {
		return (c >= 0x3000 && c <= 0x30FF) || (c >= 0x4E00 && c <= 0x9FFF);
	}

	// Same splits as the desktop regexes: around newlines, '_', '**', CJK characters and (multiline) spaces.
	@Override
	public String[] splitforTextBlock(String text, boolean multiline) {
		ArrayList<String> out = new ArrayList<>();
		int start = 0, n = text.length();
		int i = 0;
		while (i < n) {
			char c = text.charAt(i);
			int tokenLen = 0;
			if (c == '\n' || c == '_' || (multiline && c == ' ') || isCJK(c)) {
				tokenLen = 1;
			} else if (c == '*' && i + 1 < n && text.charAt(i + 1) == '*') {
				tokenLen = 2;
			}
			if (tokenLen > 0) {
				if (i > start) out.add(text.substring(start, i));
				out.add(text.substring(i, i + tokenLen));
				i += tokenLen;
				start = i;
			} else {
				i++;
			}
		}
		if (start < n) out.add(text.substring(start));
		return out.toArray(new String[0]);
	}
}
