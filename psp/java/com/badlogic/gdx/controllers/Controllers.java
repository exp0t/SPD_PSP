package com.badlogic.gdx.controllers;

import java.util.ArrayList;

// The PSP has exactly one built-in controller; the backend sets it and feeds events to the listeners.
public class Controllers {
	public static Controller current;
	public static final ArrayList<ControllerListener> listeners = new ArrayList<>();

	public static Controller getCurrent() { return current; }
	public static void addListener(ControllerListener listener) { listeners.add(listener); }
	public static void removeListener(ControllerListener listener) { listeners.remove(listener); }
}
