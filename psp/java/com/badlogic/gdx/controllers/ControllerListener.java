package com.badlogic.gdx.controllers;

public interface ControllerListener {
	void connected(Controller controller);
	void disconnected(Controller controller);
	boolean buttonDown(Controller controller, int buttonCode);
	boolean buttonUp(Controller controller, int buttonCode);
	boolean axisMoved(Controller controller, int axisCode, float value);
}
