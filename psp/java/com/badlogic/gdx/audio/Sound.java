package com.badlogic.gdx.audio;

public interface Sound {
	long play(float volume, float pitch, float pan);
	void pause();
	void resume();
	void stop();
	void dispose();
}
