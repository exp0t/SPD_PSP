/*
 * PSP port: libGDX Preferences stored as a small "type key=value" text file in the save folder.
 */
package com.shatteredpixel.shatteredpixeldungeon.psp;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;

import java.util.HashMap;
import java.util.Map;

public class PSPPreferences implements Preferences {

	private final FileHandle file;
	private final HashMap<String, Object> values = new HashMap<>();

	public PSPPreferences(String name) {
		file = new FileHandle(name + ".prefs", Files.FileType.Local);
		if (!file.exists()) return;
		for (String line : file.readString("UTF-8").split("\n")) {
			int sp = line.indexOf(' '), eq = line.indexOf('=');
			if (sp < 1 || eq < sp) continue;
			String type = line.substring(0, sp), key = line.substring(sp + 1, eq), v = unescape(line.substring(eq + 1));
			try {
				switch (type) {
					case "b": values.put(key, Boolean.parseBoolean(v)); break;
					case "i": values.put(key, Integer.parseInt(v)); break;
					case "l": values.put(key, Long.parseLong(v)); break;
					case "f": values.put(key, Float.parseFloat(v)); break;
					default: values.put(key, v);
				}
			} catch (NumberFormatException ignored) {
			}
		}
	}

	private static String escape(String s) { return s.replace("\\", "\\\\").replace("\n", "\\n"); }

	private static String unescape(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < s.length()) {
				char n = s.charAt(++i);
				sb.append(n == 'n' ? '\n' : n);
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	public Preferences putBoolean(String key, boolean val) { values.put(key, val); return this; }
	public Preferences putInteger(String key, int val) { values.put(key, val); return this; }
	public Preferences putLong(String key, long val) { values.put(key, val); return this; }
	public Preferences putFloat(String key, float val) { values.put(key, val); return this; }
	public Preferences putString(String key, String val) { values.put(key, val); return this; }

	public boolean getBoolean(String key, boolean def) {
		Object v = values.get(key);
		return v instanceof Boolean ? (Boolean) v : v instanceof String ? Boolean.parseBoolean((String) v) : def;
	}

	public int getInteger(String key, int def) {
		Object v = values.get(key);
		if (v instanceof Number) return ((Number) v).intValue();
		try { return v instanceof String ? Integer.parseInt((String) v) : def; } catch (NumberFormatException e) { return def; }
	}

	public long getLong(String key, long def) {
		Object v = values.get(key);
		if (v instanceof Number) return ((Number) v).longValue();
		try { return v instanceof String ? Long.parseLong((String) v) : def; } catch (NumberFormatException e) { return def; }
	}

	public float getFloat(String key, float def) {
		Object v = values.get(key);
		if (v instanceof Number) return ((Number) v).floatValue();
		try { return v instanceof String ? Float.parseFloat((String) v) : def; } catch (NumberFormatException e) { return def; }
	}

	public String getString(String key, String def) {
		Object v = values.get(key);
		return v == null ? def : v.toString();
	}

	public boolean getBoolean(String key) { return getBoolean(key, false); }
	public int getInteger(String key) { return getInteger(key, 0); }
	public long getLong(String key) { return getLong(key, 0); }
	public float getFloat(String key) { return getFloat(key, 0); }
	public String getString(String key) { return getString(key, ""); }

	public Map<String, ?> get() { return new HashMap<>(values); }
	public boolean contains(String key) { return values.containsKey(key); }
	public void clear() { values.clear(); }
	public void remove(String key) { values.remove(key); }

	public void flush() {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Object> e : values.entrySet()) {
			Object v = e.getValue();
			String t = v instanceof Boolean ? "b" : v instanceof Integer ? "i" : v instanceof Long ? "l" : v instanceof Float ? "f" : "s";
			sb.append(t).append(' ').append(e.getKey()).append('=').append(escape(v.toString())).append('\n');
		}
		file.writeString(sb.toString(), false);
	}
}
