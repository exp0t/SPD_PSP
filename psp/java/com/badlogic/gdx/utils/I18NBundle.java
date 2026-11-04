/*
 * PSP port: minimal libGDX I18NBundle. Loads <base>.properties and <base>_<lang>.properties (UTF-8),
 * the localized file overriding the root one, and returns "???key???" for missing keys like libGDX.
 */
package com.badlogic.gdx.utils;

import com.badlogic.gdx.files.FileHandle;

import java.util.HashMap;
import java.util.Locale;

public class I18NBundle {

	private static boolean exceptionOnMissingKey = true;

	private final HashMap<String, String> properties = new HashMap<>();

	public static void setExceptionOnMissingKey(boolean enabled) { exceptionOnMissingKey = enabled; }

	public static I18NBundle createBundle(FileHandle baseFileHandle, Locale locale) {
		I18NBundle bundle = new I18NBundle();
		FileHandle root = baseFileHandle.sibling(baseFileHandle.name() + ".properties");
		if (root.exists()) parse(root.readString("UTF-8"), bundle.properties);
		String lang = locale.getLanguage();
		if (!lang.isEmpty()) {
			String country = locale.getCountry();
			if (!country.isEmpty()) lang = lang + "_" + country;
			FileHandle loc = baseFileHandle.sibling(baseFileHandle.name() + "_" + lang + ".properties");
			if (!loc.exists() && lang.indexOf('_') > 0) {
				loc = baseFileHandle.sibling(baseFileHandle.name() + "_" + locale.getLanguage() + ".properties");
			}
			if (loc.exists()) parse(loc.readString("UTF-8"), bundle.properties);
		}
		return bundle;
	}

	public String get(String key) {
		String v = properties.get(key);
		if (v == null) {
			if (exceptionOnMissingKey) throw new java.util.MissingResourceException("Can't find bundle key " + key, "I18NBundle", key);
			return "???" + key + "???";
		}
		return v;
	}

	// java.util.Properties syntax: comments, key separators '=', ':' or whitespace, line continuations, escapes
	static void parse(String text, HashMap<String, String> out) {
		int i = 0, n = text.length();
		if (n > 0 && text.charAt(0) == '﻿') i = 1;
		StringBuilder key = new StringBuilder(), val = new StringBuilder();
		while (i < n) {
			while (i < n && (text.charAt(i) == ' ' || text.charAt(i) == '\t' || text.charAt(i) == '\f' || text.charAt(i) == '\r' || text.charAt(i) == '\n')) i++;
			if (i >= n) break;
			char c = text.charAt(i);
			if (c == '#' || c == '!') {
				while (i < n && text.charAt(i) != '\n' && text.charAt(i) != '\r') i++;
				continue;
			}
			key.setLength(0);
			val.setLength(0);
			i = read(text, i, key, true);
			while (i < n && (text.charAt(i) == ' ' || text.charAt(i) == '\t' || text.charAt(i) == '\f')) i++;
			if (i < n && (text.charAt(i) == '=' || text.charAt(i) == ':')) i++;
			while (i < n && (text.charAt(i) == ' ' || text.charAt(i) == '\t' || text.charAt(i) == '\f')) i++;
			i = read(text, i, val, false);
			out.put(key.toString(), val.toString());
		}
	}

	private static int read(String t, int i, StringBuilder sb, boolean isKey) {
		int n = t.length();
		while (i < n) {
			char c = t.charAt(i);
			if (c == '\n' || c == '\r') return i;
			if (isKey && (c == '=' || c == ':' || c == ' ' || c == '\t' || c == '\f')) return i;
			if (c == '\\') {
				i++;
				if (i >= n) return i;
				c = t.charAt(i);
				if (c == '\r' || c == '\n') {
					// line continuation: skip the newline and the next line's leading whitespace
					if (c == '\r' && i + 1 < n && t.charAt(i + 1) == '\n') i++;
					i++;
					while (i < n && (t.charAt(i) == ' ' || t.charAt(i) == '\t' || t.charAt(i) == '\f')) i++;
					continue;
				}
				switch (c) {
					case 't': sb.append('\t'); break;
					case 'n': sb.append('\n'); break;
					case 'r': sb.append('\r'); break;
					case 'f': sb.append('\f'); break;
					case 'u':
						if (i + 4 < n) {
							sb.append((char) Integer.parseInt(t.substring(i + 1, i + 5), 16));
							i += 4;
						}
						break;
					default: sb.append(c);
				}
				i++;
				continue;
			}
			sb.append(c);
			i++;
		}
		return i;
	}
}
