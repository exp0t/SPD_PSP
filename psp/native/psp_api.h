// Native functions called from Java (com.watabou.psp.PSPNative). Strings are NUL-terminated UTF-8,
// text for the font/OSK is UTF-16, image/VBO handles are native pointers cast to int32.
#pragma once
#include <stdint.h>

// files (psp_file.c)
int32_t psp_file_size(void* path);
int32_t psp_file_read(void* path, void* buf, int32_t len);
int32_t psp_file_write(void* path, void* data, int32_t len, int32_t append);
int32_t psp_file_delete(void* path);
int32_t psp_file_rename(void* from, void* to);
int32_t psp_file_mkdirs(void* path);
int64_t psp_file_mtime(void* path);
int32_t psp_dir_list(void* path, void* out, int32_t cap);

// images (psp_img.c)
int32_t psp_img_load(void* path);
int32_t psp_img_create(int32_t w, int32_t h);
int32_t psp_img_from_rgba(int32_t w, int32_t h, void* data);
int32_t psp_img_from_alpha(int32_t w, int32_t h, void* data);
void psp_img_free(int32_t img);
int32_t psp_img_width(int32_t img);
int32_t psp_img_height(int32_t img);
int32_t psp_img_get(int32_t img, int32_t x, int32_t y);
void psp_img_set(int32_t img, int32_t x, int32_t y, int32_t rgba, int32_t blend);
void psp_img_fill_rect(int32_t img, int32_t x, int32_t y, int32_t w, int32_t h, int32_t rgba, int32_t blend);
void psp_img_draw(int32_t dst, int32_t src, int32_t sx, int32_t sy, int32_t sw, int32_t sh,
                  int32_t dx, int32_t dy, int32_t dw, int32_t dh, int32_t blend);
void psp_img_dirty(int32_t img);

// rendering (psp_gfx.c)
void psp_gfx_spinner_start(void);   // boot loading indicator until the first frame
void psp_gfx_frame_begin(void);
void psp_gfx_frame_end(void);
void psp_gfx_texture(int32_t img, int32_t flags);
void psp_gfx_camera(void* mat16);
void psp_gfx_scissor(int32_t x, int32_t y, int32_t w, int32_t h);
void psp_gfx_model(void* mat16);
void psp_gfx_color(float rm, float gm, float bm, float am, float ra, float ga, float ba, float aa);
void psp_gfx_lighting(int32_t enabled);
void psp_gfx_blend(int32_t mode);
void psp_gfx_quads(void* xyuv, int32_t quads);
void psp_gfx_indexed(void* xyuv, int32_t vertices, void* indices, int32_t count);
int32_t psp_vbo_create(int32_t floats);
void psp_vbo_update(int32_t vbo, void* xyuv, int32_t start, int32_t end);
void psp_vbo_draw(int32_t vbo, int32_t quadOffset, int32_t quads);
void psp_vbo_free(int32_t vbo);

// text (psp_font.c)
int32_t psp_font_measure(void* utf16, int32_t len, int32_t size, void* outFloats);
void psp_font_draw(void* utf16, int32_t len, int32_t size);

// input, audio, system (psp_input.c, psp_audio.c, psp_sys.c)
int32_t psp_input_poll(void* outInts);
int32_t psp_snd_load(void* path);
int32_t psp_snd_play(int32_t snd, float left, float right, float rate);
void psp_snd_stop_all(void);
int32_t psp_music_play(void* path, int32_t loop);
void psp_music_stop(void);
void psp_music_pause(int32_t paused);
void psp_music_volume(float vol);
int32_t psp_music_playing(void);
int32_t psp_audio_stats(void);
void psp_sys_exit(void);
int32_t psp_sys_language(void);
int32_t psp_osk(void* title, int32_t titleLen, void* init, int32_t initLen, void* out, int32_t maxLen);
int32_t psp_sys_free_kb(void);
void psp_log_line(void* utf8);

// internal
void psp_log(const char* fmt, ...);   // printf to stdout and spd_log.txt, one line per call
void psp_boot_log(const char* msg);   // line to spd_boot.txt, safe from any thread

typedef struct PspImage {
    int w, h;          // stored pixel size
    int scale;         // logical size = stored size * scale (pre-shrunk assets, see tools/assets.py)
    int stride;        // pixels per row in px (multiple of 4)
    int pw, ph;        // power-of-two size used for GU addressing
    int rows;          // allocated rows (h rounded up to 8, as the swizzled layout needs)
    uint8_t* px;       // RGBA8888, 16-byte aligned; swizzled (16 byte x 8 row blocks) when swz is set
    int dirty;         // CPU wrote pixels since the last GU use
    int swzOk;         // static image (loaded from a file): swizzled on its first GU use
    int swz;           // px is in the GE's swizzled layout (reads much faster than linear rows)
    struct PspImage* gpu; // downscaled copy for images over 512 px
    unsigned gpuFrame; // psp_gfx_use_tag() of the last frame that bound it (0 = never)
} PspImage;

// The GE draws frame N-1 while the CPU builds frame N, so memory a sent list may still read is
// released through psp_gfx_retire, which calls 'release' once the GE has finished those lists.
unsigned psp_gfx_use_tag(void);
int psp_gfx_in_flight(unsigned tag);   // a list the GE may not have finished uses what carries this tag
void psp_gfx_retire(void* p, void (*release)(void*));

void psp_gfx_init(void);
void psp_gfx_stats(void* outInts);
void psp_audio_init(void);
void psp_font_init(void);
PspImage* psp_img_alloc(int w, int h);
void psp_img_prepare_gpu(PspImage* img); // flushes CPU writes and refreshes the >512 proxy
