// RenderedText backend: FreeType glyphs of fonts/pixel_font.ttf cached in one 512x512 atlas.
// Glyphs are rendered 1-bit (FreeType mono) at the exact on-screen pixel size, so they stay sharp;
// white glyph with a black border, flipped (y down) like SPD's libGDX font setup.
#include "psp_api.h"
#include <ft2build.h>
#include FT_FREETYPE_H
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define ATLAS 512
#define CACHE 4096

typedef struct {
    uint32_t key;              // size << 16 | char, 0 = empty
    int16_t ax, ay, w, h;      // atlas rect
    int16_t left, top;         // bitmap offset from pen / baseline
    int16_t advance;
} Glyph;

static FT_Library ft;
static FT_Face face;
static int ready;
static PspImage* atlas;
static Glyph cache[CACHE];
static int shelfX, shelfY, shelfH;
static int capHeights[128];

void psp_font_init(void) {
    if (FT_Init_FreeType(&ft)) { psp_log("FT init failed\n"); return; }
    FILE* f = fopen("assets/fonts/pixel_font.ttf", "rb");
    if (!f) { psp_log("font missing\n"); return; }
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    unsigned char* data = malloc(n);
    fread(data, 1, n, f);
    fclose(f);
    if (FT_New_Memory_Face(ft, data, n, 0, &face)) { psp_log("font load failed\n"); return; }
    atlas = psp_img_alloc(ATLAS, ATLAS);
    ready = atlas != NULL;
}

static void resetAtlas(void) {
    memset(cache, 0, sizeof cache);
    memset(atlas->px, 0, ATLAS * ATLAS * 4);
    atlas->dirty = 1;
    shelfX = shelfY = shelfH = 0;
}

static int border(int size) {
    // libGDX uses size / 10f; a crisp 1 px outline reads better on the PSP screen up to large titles
    return size < 24 ? 1 : 2;
}

static void setSize(int size) {
    static int cur = -1;
    if (cur != size) {
        FT_Set_Pixel_Sizes(face, 0, size);
        cur = size;
    }
}

static Glyph* glyph(int size, uint32_t c) {
    uint32_t key = ((uint32_t) size << 16) | c;
    uint32_t h = (key * 2654435761u) & (CACHE - 1);
    for (int probe = 0; probe < CACHE; probe++, h = (h + 1) & (CACHE - 1)) {
        if (cache[h].key == key) return &cache[h];
        if (cache[h].key == 0) break;
    }

    setSize(size);
    FT_UInt gi = FT_Get_Char_Index(face, c);
    if (gi == 0) gi = FT_Get_Char_Index(face, 0xFFFD);
    if (FT_Load_Glyph(face, gi, FT_LOAD_RENDER | FT_LOAD_TARGET_MONO)) return NULL;
    FT_GlyphSlot s = face->glyph;
    int b = border(size);
    int bw = s->bitmap.width, bh = s->bitmap.rows;
    int w = bw ? bw + 2 * b : 0, hgt = bh ? bh + 2 * b : 0;

    // two cells side by side: black border (dilated coverage), then the white glyph itself.
    // Text draws all borders first, so a border never covers the neighbouring letter.
    int cellW = 2 * w + 1;
    if (shelfX + cellW + 1 > ATLAS) { shelfX = 0; shelfY += shelfH + 1; shelfH = 0; }
    if (shelfY + hgt + 1 > ATLAS) {
        resetAtlas();
        return glyph(size, c);
    }
    if (hgt > shelfH) shelfH = hgt;

    if (w && hgt) {
        unsigned char* cov = calloc(w * hgt, 1);
        int mono = s->bitmap.pixel_mode == FT_PIXEL_MODE_MONO;
        for (int y = 0; y < bh; y++) {
            const unsigned char* src = s->bitmap.buffer + y * s->bitmap.pitch;
            for (int x = 0; x < bw; x++)
                cov[(y + b) * w + x + b] = mono ? ((src[x >> 3] >> (7 - (x & 7))) & 1) * 255 : src[x];
        }
        for (int y = 0; y < hgt; y++) {
            uint8_t* row = atlas->px + ((size_t) (shelfY + y) * atlas->stride + shelfX) * 4;
            uint8_t* fill = row + (w + 1) * 4;
            for (int x = 0; x < w; x++) {
                int d = 0;
                for (int yy = y - b; yy <= y + b; yy++) {
                    if (yy < 0 || yy >= hgt) continue;
                    for (int xx = x - b; xx <= x + b; xx++) {
                        if (xx < 0 || xx >= w) continue;
                        int v = cov[yy * w + xx];
                        if (v > d) d = v;
                    }
                }
                row[x * 4 + 0] = row[x * 4 + 1] = row[x * 4 + 2] = 0;
                row[x * 4 + 3] = (uint8_t) d;
                fill[x * 4 + 0] = fill[x * 4 + 1] = fill[x * 4 + 2] = 255;
                fill[x * 4 + 3] = cov[y * w + x];
            }
        }
        free(cov);
        atlas->dirty = 1;
    }

    Glyph* out = &cache[h];
    out->key = key;
    out->ax = shelfX;
    out->ay = shelfY;
    out->w = w;
    out->h = hgt;
    out->left = s->bitmap_left - b;
    out->top = s->bitmap_top + b;
    // libGDX: spaceX = -(int) borderWidth, i.e. no change below size 10
    out->advance = (int16_t) ((s->advance.x >> 6) - size / 10);
    shelfX += cellW + 1;
    return out;
}

static int capHeight(int size) {
    if (size < 128 && capHeights[size]) return capHeights[size];
    Glyph* g = glyph(size, 'X');
    int ch = g ? g->top : size;
    if (size < 128) capHeights[size] = ch;
    return ch;
}

// out[0] = width as RenderedText computes it, out[1] = libGDX layout height (cap height)
int32_t psp_font_measure(void* utf16, int32_t len, int32_t size, void* outFloats) {
    float* out = outFloats;
    out[0] = out[1] = 0;
    if (!ready || len <= 0) return 0;
    const uint16_t* t = utf16;
    int x = 0;
    Glyph* last = NULL;
    for (int i = 0; i < len; i++) {
        Glyph* g = glyph(size, t[i]);
        if (!g) continue;
        if (i == len - 1) last = g;
        else x += g->advance;
    }
    float width = x;
    if (last) {
        width += last->left + last->w;
        if (last->advance > last->w * 1.5f) width = x + last->advance;
    }
    out[0] = width;
    out[1] = capHeight(size);
    return 1;
}

void psp_font_draw(void* utf16, int32_t len, int32_t size) {
    if (!ready || len <= 0) return;
    const uint16_t* t = utf16;
    int base = capHeight(size);
    // make sure every glyph is cached before the atlas is bound for drawing
    for (int i = 0; i < len; i++) glyph(size, t[i]);

    // borders for the whole string first (quads [0, n)), glyph fills after them (quads [len, len + n))
    static float* quads;
    static int quadCap;
    if (len * 2 > quadCap) {
        free(quads);
        quadCap = len * 2 + 64;
        quads = malloc(quadCap * 16 * sizeof(float));
    }
    int n = 0, pen = 0;
    const float inv = 1.0f / ATLAS;
    for (int i = 0; i < len; i++) {
        Glyph* g = glyph(size, t[i]);
        if (!g) continue;
        if (g->w && g->h) {
            float x1 = pen + g->left, y1 = base - g->top, x2 = x1 + g->w, y2 = y1 + g->h;
            float v1 = g->ay * inv, v2 = (g->ay + g->h) * inv;
            for (int layer = 0; layer < 2; layer++) {
                float u1 = (g->ax + layer * (g->w + 1)) * inv, u2 = u1 + g->w * inv;
                float* q = quads + (layer * len + n) * 16;
                q[0] = x1; q[1] = y1; q[2] = u1; q[3] = v1;
                q[4] = x2; q[5] = y1; q[6] = u2; q[7] = v1;
                q[8] = x2; q[9] = y2; q[10] = u2; q[11] = v2;
                q[12] = x1; q[13] = y2; q[14] = u1; q[15] = v2;
            }
            n++;
        }
        pen += g->advance;
    }
    if (n == 0) return;
    psp_gfx_texture((int32_t) atlas, 0);
    psp_gfx_quads(quads, n);
    psp_gfx_quads(quads + len * 16, n);
}
