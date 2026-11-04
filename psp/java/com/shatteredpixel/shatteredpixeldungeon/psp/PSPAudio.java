/*
 * PSP port: libGDX audio over the native mixer. Sound effects are preloaded PCM, music is streamed
 * (one track at a time, which is all SPD ever plays).
 */
package com.shatteredpixel.shatteredpixeldungeon.psp;

import com.badlogic.gdx.Audio;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.watabou.psp.PSPNative;

import org.teavm.interop.Address;

public class PSPAudio implements Audio {

	private static PSPMusic current;

	public Sound newSound(FileHandle file) {
		final int id = PSPNative.soundLoad(Address.ofData(PSPNative.cstr(file.fullPath())));
		return new Sound() {
			public long play(float volume, float pitch, float pan) {
				if (id == 0) return -1;
				float l = volume * Math.min(1f, 1f - pan), r = volume * Math.min(1f, 1f + pan);
				return PSPNative.soundPlay(id, l, r, pitch);
			}
			public void pause() { }
			public void resume() { }
			public void stop() { }
			public void dispose() { }
		};
	}

	public Music newMusic(FileHandle file) { return new PSPMusic(file.fullPath()); }

	// fires completion listeners; called once per frame
	static void update() {
		if (current != null && current.playing && !current.paused && PSPNative.musicPlaying() == 0) {
			PSPMusic m = current;
			m.playing = false;
			current = null;
			if (m.listener != null) m.listener.onCompletion(m);
		}
	}

	static class PSPMusic implements Music {
		final String path;
		boolean looping, playing, paused;
		float volume = 1f;
		OnCompletionListener listener;

		PSPMusic(String path) { this.path = path; }

		public void play() {
			if (current == this && playing) {
				if (paused) {
					paused = false;
					PSPNative.musicPause(0);
				}
				return;
			}
			if (current != null) current.playing = false;
			current = this;
			playing = true;
			paused = false;
			PSPNative.musicVolume(volume);
			PSPNative.musicPlay(Address.ofData(PSPNative.cstr(path)), looping ? 1 : 0);
		}

		public void pause() {
			if (current == this && playing) {
				paused = true;
				PSPNative.musicPause(1);
			}
		}

		public void stop() {
			if (current == this) {
				PSPNative.musicStop();
				current = null;
			}
			playing = paused = false;
		}

		public boolean isPlaying() { return current == this && playing && !paused; }
		public void setLooping(boolean isLooping) { looping = isLooping; }
		public boolean isLooping() { return looping; }

		public void setVolume(float volume) {
			this.volume = volume;
			if (current == this) PSPNative.musicVolume(volume);
		}

		public void setOnCompletionListener(OnCompletionListener listener) { this.listener = listener; }
		public void dispose() { stop(); }
	}
}
