package com.badlogic.gdx.audio;

public interface Music {
	interface OnCompletionListener {
		void onCompletion(Music music);
	}

	void play();
	void pause();
	void stop();
	boolean isPlaying();
	void setLooping(boolean isLooping);
	boolean isLooping();
	void setVolume(float volume);
	void setOnCompletionListener(OnCompletionListener listener);
	void dispose();
}
