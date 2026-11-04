package com.badlogic.gdx;

import com.badlogic.gdx.utils.Clipboard;

public interface Application {
	enum ApplicationType { Android, Desktop, HeadlessDesktop, Applet, WebGL, iOS }

	ApplicationType getType();
	int getVersion();
	Clipboard getClipboard();
	Preferences getPreferences(String name);
	void log(String tag, String message);
	void error(String tag, String message);
	void error(String tag, String message, Throwable exception);
	void postRunnable(Runnable runnable);
	void exit();
}
