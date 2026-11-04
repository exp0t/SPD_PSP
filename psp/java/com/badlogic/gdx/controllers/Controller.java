package com.badlogic.gdx.controllers;

public interface Controller {
	String getName();
	ControllerMapping getMapping();
	boolean canVibrate();
	void startVibration(int duration, float strength);
}
