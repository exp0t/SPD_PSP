/*
 * PSP port: native functions implemented in psp/native (C), called through TeaVM's C interop.
 * Strings are passed as NUL-terminated UTF-8 byte arrays, data arrays through Address.ofData().
 */
package com.watabou.psp;

import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

@Include(value = "psp_api.h", isSystem = false)
public final class PSPNative {

	public static byte[] cstr(String s) {
		byte[] utf = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		byte[] out = new byte[utf.length + 1];
		System.arraycopy(utf, 0, out, 0, utf.length);
		return out;
	}

	// ---- files (paths are relative to the EBOOT folder) ----
	@Import(name = "psp_file_size") public static native int fileSize(Address path);           // -1 missing, -2 directory
	@Import(name = "psp_file_read") public static native int fileRead(Address path, Address buf, int len);
	@Import(name = "psp_file_write") public static native int fileWrite(Address path, Address data, int len, int append);
	@Import(name = "psp_file_delete") public static native int fileDelete(Address path);
	@Import(name = "psp_file_rename") public static native int fileRename(Address from, Address to);
	@Import(name = "psp_file_mkdirs") public static native int fileMkdirs(Address path);
	@Import(name = "psp_file_mtime") public static native long fileMtime(Address path);
	@Import(name = "psp_dir_list") public static native int dirList(Address path, Address out, int cap); // names joined by '\n'

	// ---- images (RGBA8888, byte order R,G,B,A; handles are native pointers) ----
	@Import(name = "psp_img_load") public static native int imgLoad(Address path);
	@Import(name = "psp_img_create") public static native int imgCreate(int w, int h);
	@Import(name = "psp_img_from_rgba") public static native int imgFromRGBA(int w, int h, Address argb);
	@Import(name = "psp_img_from_alpha") public static native int imgFromAlpha(int w, int h, Address alpha);
	@Import(name = "psp_img_free") public static native void imgFree(int img);
	@Import(name = "psp_img_width") public static native int imgWidth(int img);
	@Import(name = "psp_img_height") public static native int imgHeight(int img);
	@Import(name = "psp_img_get") public static native int imgGet(int img, int x, int y);
	@Import(name = "psp_img_set") public static native void imgSet(int img, int x, int y, int rgba, int blend);
	@Import(name = "psp_img_fill_rect") public static native void imgFillRect(int img, int x, int y, int w, int h, int rgba, int blend);
	@Import(name = "psp_img_draw") public static native void imgDraw(int dst, int src, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh, int blend);
	@Import(name = "psp_img_dirty") public static native void imgDirty(int img);

	// ---- rendering ----
	@Import(name = "psp_gfx_frame_begin") public static native void frameBegin();
	@Import(name = "psp_gfx_frame_end") public static native void frameEnd();
	@Import(name = "psp_gfx_stats") public static native void gfxStats(Address outInts); // see psp_gfx.c
	@Import(name = "psp_gfx_texture") public static native void texture(int img, int flags); // 1 = linear, 2 = repeat s, 4 = repeat t
	@Import(name = "psp_gfx_camera") public static native void camera(Address mat16);
	@Import(name = "psp_gfx_scissor") public static native void scissor(int x, int y, int w, int h); // w < 0 disables
	@Import(name = "psp_gfx_model") public static native void model(Address mat16);
	@Import(name = "psp_gfx_color") public static native void color(float rm, float gm, float bm, float am, float ra, float ga, float ba, float aa);
	@Import(name = "psp_gfx_lighting") public static native void lightingEnabled(int enabled);
	@Import(name = "psp_gfx_blend") public static native void blend(int mode); // 0 off, 1 normal, 2 additive
	@Import(name = "psp_gfx_quads") public static native void quads(Address xyuv, int quadCount);
	@Import(name = "psp_gfx_indexed") public static native void indexed(Address xyuv, int vertexCount, Address indices, int indexCount);
	@Import(name = "psp_vbo_create") public static native int vboCreate(int floats);
	@Import(name = "psp_vbo_update") public static native void vboUpdate(int vbo, Address xyuv, int start, int end);
	@Import(name = "psp_vbo_draw") public static native void vboDraw(int vbo, int quadOffset, int quadCount);
	@Import(name = "psp_vbo_free") public static native void vboFree(int vbo);

	// ---- text (FreeType, glyph atlas lives in C) ----
	@Import(name = "psp_font_measure") public static native int fontMeasure(Address utf16, int len, int size, Address outFloats);
	@Import(name = "psp_font_draw") public static native void fontDraw(Address utf16, int len, int size);

	// ---- input ----
	@Import(name = "psp_input_poll") public static native int inputPoll(Address outInts); // [buttons, lx, ly]

	// ---- audio ----
	@Import(name = "psp_snd_load") public static native int soundLoad(Address path);
	@Import(name = "psp_snd_play") public static native int soundPlay(int snd, float leftVol, float rightVol, float rate);
	@Import(name = "psp_snd_stop_all") public static native void soundStopAll();
	@Import(name = "psp_music_play") public static native int musicPlay(Address path, int loop);
	@Import(name = "psp_music_stop") public static native void musicStop();
	@Import(name = "psp_music_pause") public static native void musicPause(int paused);
	@Import(name = "psp_music_volume") public static native void musicVolume(float vol);
	@Import(name = "psp_music_playing") public static native int musicPlaying();
	@Import(name = "psp_audio_stats") public static native int audioStats(); // slowest mix us | underruns << 20

	// ---- system ----
	@Import(name = "psp_sys_exit") public static native void exit();
	@Import(name = "psp_sys_language") public static native int systemLanguage();
	@Import(name = "psp_osk") public static native int osk(Address titleUtf16, int titleLen, Address initUtf16, int initLen, Address outUtf16, int maxLen);
	@Import(name = "psp_sys_free_kb") public static native int freeMemoryKb(); // actually: native heap in use, KB
	@Import(name = "psp_log_line") public static native void logLine(Address utf8);

	/** Writes one line to stdout and spd_log.txt immediately (System.out is buffered by TeaVM). */
	public static void log(String line) {
		logLine(Address.ofData(cstr(line)));
	}
}
