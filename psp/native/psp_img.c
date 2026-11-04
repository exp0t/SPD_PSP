// Images for Pixmap/Texture: RGBA8888 rows in RAM that the GU samples directly.
// Images loaded from files are converted in place to the GE's swizzled layout the first time they are
// drawn, which the GE reads several times faster; CPU pixel access translates addresses (see pixel()).
#include "psp_api.h"
#include <malloc.h>
#include <pspkernel.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define STBI_ONLY_PNG
#define STBI_ONLY_JPEG
#define STBI_NO_STDIO
#define STB_IMAGE_IMPLEMENTATION
#include <stb_image.h>

static int pow2(int v) {
    int p = 1;
    while (p < v) p <<= 1;
    return p;
}

PspImage* psp_img_alloc(int w, int h) {
    PspImage* img = calloc(1, sizeof(PspImage));
    if (!img) return NULL;
    img->w = w;
    img->h = h;
    img->scale = 1;
    img->stride = (w + 3) & ~3;
    img->rows = (h + 7) & ~7;
    img->pw = pow2(w);
    img->ph = pow2(h);
    img->px = memalign(16, (size_t) img->stride * img->rows * 4);
    if (!img->px) {
        psp_log("img alloc failed %dx%d\n", w, h);
        free(img);
        return NULL;
    }
    memset(img->px, 0, (size_t) img->stride * img->rows * 4);
    img->dirty = 1;
    return img;
}

int32_t psp_img_create(int32_t w, int32_t h) {
    return (int32_t) psp_img_alloc(w, h);
}

// assets/scale.txt: "<path relative to assets/> <factor>" for images stored downscaled
static int assetScale(const char* path) {
    static char* table;
    static int loaded;
    if (!loaded) {
        loaded = 1;
        FILE* f = fopen("assets/scale.txt", "rb");
        if (f) {
            fseek(f, 0, SEEK_END);
            long n = ftell(f);
            fseek(f, 0, SEEK_SET);
            table = calloc(n + 2, 1);
            fread(table + 1, 1, n, f);
            table[0] = '\n';
            fclose(f);
        }
    }
    if (!table) return 1;
    const char* rel = strncmp(path, "assets/", 7) == 0 ? path + 7 : path;
    size_t len = strlen(rel);
    for (const char* p = table; (p = strstr(p, rel)) != NULL; p += len) {
        if (p[-1] == '\n' && p[len] == ' ') return atoi(p + len + 1);
    }
    return 1;
}

int32_t psp_img_load(void* path) {
    FILE* f = fopen((const char*) path, "rb");
    if (!f) {
        psp_log("img missing %s\n", (const char*) path);
        return 0;
    }
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    unsigned char* data = malloc(size);
    if (!data) { fclose(f); return 0; }
    fread(data, 1, size, f);
    fclose(f);

    int w, h, n;
    unsigned char* rgba = stbi_load_from_memory(data, (int) size, &w, &h, &n, 4);
    free(data);
    if (!rgba) {
        psp_log("img decode failed %s: %s\n", (const char*) path, stbi_failure_reason());
        return 0;
    }
    PspImage* img = psp_img_alloc(w, h);
    if (img) {
        for (int y = 0; y < h; y++) memcpy(img->px + (size_t) y * img->stride * 4, rgba + (size_t) y * w * 4, (size_t) w * 4);
        img->scale = assetScale((const char*) path);
        // a file named noswizzle next to the EBOOT keeps textures linear (for comparisons)
        static int noSwizzle = -1;
        if (noSwizzle < 0) {
            FILE* nf = fopen("noswizzle", "rb");
            noSwizzle = nf != NULL;
            if (nf) fclose(nf);
            psp_log("texture swizzling %s", noSwizzle ? "off (noswizzle file)" : "on");
        }
        img->swzOk = !noSwizzle;
    }
    stbi_image_free(rgba);
    psp_log("img %s %dx%d native %dKB", (const char*) path, w, h, (int) (mallinfo().uordblks >> 10));
    return (int32_t) img;
}

int32_t psp_img_from_rgba(int32_t w, int32_t h, void* data) {
    PspImage* img = psp_img_alloc(w, h);
    if (!img) return 0;
    for (int y = 0; y < h; y++) memcpy(img->px + (size_t) y * img->stride * 4, (uint8_t*) data + (size_t) y * w * 4, (size_t) w * 4);
    return (int32_t) img;
}

// GL_ALPHA upload: samples as (0, 0, 0, a)
int32_t psp_img_from_alpha(int32_t w, int32_t h, void* data) {
    PspImage* img = psp_img_alloc(w, h);
    if (!img) return 0;
    const uint8_t* a = data;
    for (int y = 0; y < h; y++) {
        uint8_t* row = img->px + (size_t) y * img->stride * 4;
        for (int x = 0; x < w; x++) row[x * 4 + 3] = a[y * w + x];
    }
    return (int32_t) img;
}

static void imgRelease(void* p) {
    PspImage* img = p;
    if (img->gpu) imgRelease(img->gpu);
    free(img->px);
    free(img);
}

// a texture of the previous frame is still sampled by the GE, and its memory would be reused right away
// (scene and window switches, text and fog updates), so those frees wait for the GE
void psp_img_free(int32_t handle) {
    PspImage* img = (PspImage*) handle;
    if (!img) return;
    if (psp_gfx_in_flight(img->gpuFrame)) psp_gfx_retire(img, imgRelease);
    else imgRelease(img);
}

int32_t psp_img_width(int32_t handle) { return ((PspImage*) handle)->w * ((PspImage*) handle)->scale; }
int32_t psp_img_height(int32_t handle) { return ((PspImage*) handle)->h * ((PspImage*) handle)->scale; }

static inline uint8_t* pixel(PspImage* img, int x, int y) {
    if (img->swz) {
        // 16-byte (4 pixel) x 8-row blocks, stored block row by block row
        size_t block = (size_t) (y >> 3) * (img->stride >> 2) + (x >> 2);
        return img->px + block * 128 + (y & 7) * 16 + (x & 3) * 4;
    }
    return img->px + ((size_t) y * img->stride + x) * 4;
}

static void swizzle(PspImage* img) {
    size_t rowBytes = (size_t) img->stride * 4, size = rowBytes * img->rows;
    uint8_t* tmp = malloc(size);
    if (!tmp) return;   // stays linear
    uint32_t* d = (uint32_t*) tmp;
    for (int by = 0; by < img->rows; by += 8)
        for (size_t bx = 0; bx < rowBytes; bx += 16)
            for (int y = 0; y < 8; y++) {
                const uint32_t* s = (const uint32_t*) (img->px + (by + y) * rowBytes + bx);
                d[0] = s[0]; d[1] = s[1]; d[2] = s[2]; d[3] = s[3];
                d += 4;
            }
    memcpy(img->px, tmp, size);
    free(tmp);
    img->swz = 1;
}

// libGDX colour ints are 0xRRGGBBAA
int32_t psp_img_get(int32_t handle, int32_t x, int32_t y) {
    PspImage* img = (PspImage*) handle;
    x /= img->scale;
    y /= img->scale;
    if (x < 0 || y < 0 || x >= img->w || y >= img->h) return 0;
    uint8_t* p = pixel(img, x, y);
    return (int32_t) (((uint32_t) p[0] << 24) | ((uint32_t) p[1] << 16) | ((uint32_t) p[2] << 8) | p[3]);
}

static inline void put(uint8_t* p, uint32_t rgba, int blend) {
    uint8_t r = rgba >> 24, g = rgba >> 16, b = rgba >> 8, a = rgba;
    if (!blend || a == 255) {
        p[0] = r; p[1] = g; p[2] = b; p[3] = a;
    } else if (a != 0) {
        int da = p[3];
        int oa = a + da * (255 - a) / 255;
        if (oa == 0) { p[0] = p[1] = p[2] = p[3] = 0; return; }
        p[0] = (r * a + p[0] * da * (255 - a) / 255) / oa;
        p[1] = (g * a + p[1] * da * (255 - a) / 255) / oa;
        p[2] = (b * a + p[2] * da * (255 - a) / 255) / oa;
        p[3] = oa;
    }
}

void psp_img_set(int32_t handle, int32_t x, int32_t y, int32_t rgba, int32_t blend) {
    PspImage* img = (PspImage*) handle;
    if (x < 0 || y < 0 || x >= img->w || y >= img->h) return;
    put(pixel(img, x, y), (uint32_t) rgba, blend);
    img->dirty = 1;
}

void psp_img_fill_rect(int32_t handle, int32_t x, int32_t y, int32_t w, int32_t h, int32_t rgba, int32_t blend) {
    PspImage* img = (PspImage*) handle;
    int x0 = x < 0 ? 0 : x, y0 = y < 0 ? 0 : y;
    int x1 = x + w > img->w ? img->w : x + w, y1 = y + h > img->h ? img->h : y + h;
    for (int yy = y0; yy < y1; yy++) {
        if (img->swz) {
            for (int xx = x0; xx < x1; xx++) put(pixel(img, xx, yy), (uint32_t) rgba, blend);
            continue;
        }
        uint8_t* p = pixel(img, x0, yy);
        for (int xx = x0; xx < x1; xx++, p += 4) put(p, (uint32_t) rgba, blend);
    }
    img->dirty = 1;
}

void psp_img_draw(int32_t dsth, int32_t srch, int32_t sx, int32_t sy, int32_t sw, int32_t sh,
                  int32_t dx, int32_t dy, int32_t dw, int32_t dh, int32_t blend) {
    PspImage* dst = (PspImage*) dsth;
    PspImage* src = (PspImage*) srch;
    if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) return;
    for (int y = 0; y < dh; y++) {
        int ty = dy + y;
        if (ty < 0 || ty >= dst->h) continue;
        int fy = sy + y * sh / dh;
        if (fy < 0 || fy >= src->h) continue;
        for (int x = 0; x < dw; x++) {
            int tx = dx + x;
            if (tx < 0 || tx >= dst->w) continue;
            int fx = sx + x * sw / dw;
            if (fx < 0 || fx >= src->w) continue;
            uint8_t* s = pixel(src, fx, fy);
            put(pixel(dst, tx, ty), ((uint32_t) s[0] << 24) | ((uint32_t) s[1] << 16) | ((uint32_t) s[2] << 8) | s[3], blend);
        }
    }
    dst->dirty = 1;
}

void psp_img_dirty(int32_t handle) {
    if (handle) ((PspImage*) handle)->dirty = 1;
}

// The GU cannot address textures over 512x512: keep a box-filtered copy halved until it fits.
// SPD uses normalized UVs, so drawing with the smaller copy only loses detail.
void psp_img_prepare_gpu(PspImage* img) {
    if (!img->dirty) return;
    img->dirty = 0;
    if (img->w > 512 || img->h > 512) {
        int f = 1;
        while (img->w / f > 512 || img->h / f > 512) f <<= 1;
        int gw = img->w / f, gh = img->h / f;
        if (!img->gpu) {
            img->gpu = psp_img_alloc(gw, gh);
            psp_log("gpu proxy %dx%d -> %dx%d", img->w, img->h, gw, gh);
        }
        PspImage* g = img->gpu;
        if (!g) return;
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                int acc[4] = {0, 0, 0, 0};
                for (int yy = 0; yy < f; yy++) {
                    for (int xx = 0; xx < f; xx++) {
                        uint8_t* s = pixel(img, x * f + xx, y * f + yy);
                        acc[0] += s[0] * s[3]; acc[1] += s[1] * s[3]; acc[2] += s[2] * s[3]; acc[3] += s[3];
                    }
                }
                uint8_t* d = pixel(g, x, y);
                d[3] = acc[3] / (f * f);
                if (acc[3]) { d[0] = acc[0] / acc[3]; d[1] = acc[1] / acc[3]; d[2] = acc[2] / acc[3]; }
                else d[0] = d[1] = d[2] = 0;
            }
        }
        g->dirty = 0;
        if (img->swzOk && !g->swz) swizzle(g);
        sceKernelDcacheWritebackRange(g->px, g->stride * g->rows * 4);
    } else {
        if (img->swzOk && !img->swz) swizzle(img);
        sceKernelDcacheWritebackRange(img->px, img->stride * img->rows * 4);
    }
}
