/*
 * PSP port: entry point. Sets up the libGDX shims, creates the game and runs the frame loop.
 */
package com.shatteredpixel.shatteredpixeldungeon.psp;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.watabou.noosa.Game;
import com.watabou.psp.PSPNative;
import com.watabou.utils.FileUtils;

public class PSPLauncher {

	public static final int WIDTH = 480;
	public static final int HEIGHT = 272;

	private static final int[] gfx = new int[8];

	public static void main(String[] args) {
		PSPApplication app = new PSPApplication();
		PSPInput input = new PSPInput();
		Gdx.app = app;
		Gdx.files = new Files();
		Gdx.input = input;
		Gdx.audio = new PSPAudio();

		Files.internalRoot = "assets/";
		Files.localRoot = "save/";
		FileUtils.setDefaultFileProperties(Files.FileType.Local, "");

		Game.version = "4.0.2";
		Game.versionCode = 922;

		ShatteredPixelDungeon game;
		try {
			game = new ShatteredPixelDungeon(new PSPPlatformSupport());
			game.create();
			game.resize(WIDTH, HEIGHT);
		} catch (Throwable t) {
			Game.reportException(t);
			return;
		}

		long last = System.nanoTime();
		int frames = 0;
		long fpsStart = last;
		while (!app.exitRequested && !input.exitRequested) {
			long now = System.nanoTime();
			Game.frameDeltaTime = (now - last) / 1e9f;
			last = now;

			input.poll();
			PSPNative.frameBegin();
			try {
				game.render();
			} catch (Throwable t) {
				PSPNative.frameEnd();
				fatal(t);
				return;
			}
			PSPNative.frameEnd();
			app.runPosted();
			PSPAudio.update();

			frames++;
			if (now - fpsStart > 5_000_000_000L) {
				int audio = PSPNative.audioStats();
				PSPNative.gfxStats(org.teavm.interop.Address.ofData(gfx));
				PSPNative.log("fps " + (frames * 1e9f / (now - fpsStart)) + " | cpu " + gfx[1] / 1000f + "ms ge wait " + gfx[2] / 1000f + "ms vsync wait " + gfx[3] / 1000f
						+ "ms, draws " + gfx[4] + " quads " + gfx[5] + " binds " + gfx[6] + " list " + gfx[7] + "KB | native " + PSPNative.freeMemoryKb() + "KB used, java free " + (Runtime.getRuntime().freeMemory() >> 10) + "KB"
						+ ", mix max " + (audio & 0xFFFFF) + "us, music underruns " + (audio >>> 20)
						+ (com.shatteredpixel.shatteredpixeldungeon.Dungeon.hero != null ? " hero at " + com.shatteredpixel.shatteredpixeldungeon.Dungeon.hero.pos + " turn " + (int) com.shatteredpixel.shatteredpixeldungeon.actors.Actor.now()
								+ " cam " + (int) com.watabou.noosa.Camera.main.scroll.x + "," + (int) com.watabou.noosa.Camera.main.scroll.y : ""));
				frames = 0;
				fpsStart = now;
			}
		}
		game.destroy();
		PSPNative.exit();
	}

	// written line by line so it survives into spd_log.txt even if printing itself goes wrong
	private static void fatal(Throwable t) {
		for (Throwable e = t; e != null; e = e.getCause() == e ? null : e.getCause()) {
			PSPNative.log((e == t ? "FATAL " : "Caused by: ") + e.getClass().getName() + ": " + e.getMessage());
			for (StackTraceElement s : e.getStackTrace()) {
				PSPNative.log("  at " + s);
			}
		}
	}
}
