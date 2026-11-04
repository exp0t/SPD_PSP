/*
 * PSP port: libGDX Application services (logging, preferences, clipboard, posted runnables).
 */
package com.shatteredpixel.shatteredpixeldungeon.psp;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Clipboard;
import com.watabou.psp.PSPNative;

import java.util.ArrayList;
import java.util.HashMap;

public class PSPApplication implements Application {

	private final HashMap<String, Preferences> prefs = new HashMap<>();
	private final ArrayList<Runnable> runnables = new ArrayList<>();
	boolean exitRequested;

	private final Clipboard clipboard = new Clipboard() {
		private String contents;
		public boolean hasContents() { return contents != null && !contents.isEmpty(); }
		public String getContents() { return contents; }
		public void setContents(String content) { contents = content; }
	};

	public ApplicationType getType() { return ApplicationType.Desktop; }
	public int getVersion() { return 0; }
	public Clipboard getClipboard() { return clipboard; }

	public Preferences getPreferences(String name) {
		Preferences p = prefs.get(name);
		if (p == null) {
			p = new PSPPreferences(name);
			prefs.put(name, p);
		}
		return p;
	}

	public void log(String tag, String message) { PSPNative.log(tag + ": " + message); }
	public void error(String tag, String message) {
		int nl = message.indexOf('\n');
		PSPNative.log("ERROR " + tag + " (" + message.length() + " chars)");
		PSPNative.log(nl < 0 ? message : message.substring(0, nl));
		if (nl >= 0) PSPNative.log(message.substring(nl + 1));
	}

	public void error(String tag, String message, Throwable exception) {
		error(tag, message);
		exception.printStackTrace();
	}

	public void postRunnable(Runnable runnable) {
		synchronized (runnables) {
			runnables.add(runnable);
		}
	}

	void runPosted() {
		Runnable[] list;
		synchronized (runnables) {
			if (runnables.isEmpty()) return;
			list = runnables.toArray(new Runnable[0]);
			runnables.clear();
		}
		for (Runnable r : list) r.run();
	}

	public void exit() { exitRequested = true; }
}
