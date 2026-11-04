package com.badlogic.gdx.controllers;

public class ControllerMapping {
	public final int axisLeftX, axisLeftY, axisRightX, axisRightY;
	public final int buttonA, buttonB, buttonX, buttonY, buttonBack, buttonStart;
	public final int buttonL1, buttonL2, buttonR1, buttonR2, buttonLeftStick, buttonRightStick;
	public final int buttonDpadUp, buttonDpadDown, buttonDpadLeft, buttonDpadRight;

	public ControllerMapping(int axisLeftX, int axisLeftY, int axisRightX, int axisRightY,
			int buttonA, int buttonB, int buttonX, int buttonY, int buttonBack, int buttonStart,
			int buttonL1, int buttonL2, int buttonR1, int buttonR2, int buttonLeftStick, int buttonRightStick,
			int buttonDpadUp, int buttonDpadDown, int buttonDpadLeft, int buttonDpadRight) {
		this.axisLeftX = axisLeftX; this.axisLeftY = axisLeftY; this.axisRightX = axisRightX; this.axisRightY = axisRightY;
		this.buttonA = buttonA; this.buttonB = buttonB; this.buttonX = buttonX; this.buttonY = buttonY;
		this.buttonBack = buttonBack; this.buttonStart = buttonStart;
		this.buttonL1 = buttonL1; this.buttonL2 = buttonL2; this.buttonR1 = buttonR1; this.buttonR2 = buttonR2;
		this.buttonLeftStick = buttonLeftStick; this.buttonRightStick = buttonRightStick;
		this.buttonDpadUp = buttonDpadUp; this.buttonDpadDown = buttonDpadDown;
		this.buttonDpadLeft = buttonDpadLeft; this.buttonDpadRight = buttonDpadRight;
	}
}
