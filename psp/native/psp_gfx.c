// sceGu renderer for NoosaScript: textured quads, gl_FragColor = texture * colorM + colorA.
// colorM is the primitive colour (GU_TFX_MODULATE); a non-zero colorA is added by a second additive pass.
// Frames are pipelined: the CPU builds frame N into one display list (GU_SEND) while the GE draws frame
// N-1 from the other, into three framebuffers, so a frame that takes a bit longer than a vblank no longer
// halves the frame rate.
#include "psp_api.h"
#include <malloc.h>
#include <pspdisplay.h>
#include <pspgu.h>
#include <pspge.h>
#include <pspkernel.h>
#include <pspsdk.h>
#include <stdio.h>
#include <string.h>

#define SCR_W 480
#define SCR_H 272
#define BUF_W 512
#define LIST_SIZE (1024 * 1024)
#define LIST_RESERVE (16 * 1024)   // left free at the end of a list for the frame's closing commands
#define MAX_QUADS 8192
#define FB_SIZE (BUF_W * SCR_H * 4)

typedef struct { float u, v, x, y, z; } Vtx;

static unsigned int __attribute__((aligned(64))) lists[2][LIST_SIZE / 4];
static unsigned int* list;            // list being built
static PspGeContext geContext;
static void* vram;
static unsigned frameNo;
static int inFlight = -1;             // framebuffer the GE is drawing (or has drawn) for the previous frame
static int drawBuf;                   // framebuffer of the frame being built
static unsigned lastSetVcount;
static int haveSet;
static int listFull;

// per-frame statistics, averaged by psp_gfx_stats()
static unsigned statFrames, statCpuUs, statGeUs, statVsyncUs, statDraws, statQuads, statBinds, statListMax;
static unsigned frameStart;
static uint16_t* quadIndices;

static PspImage* boundTex;
static int texFlags;
static int lighting = 1;
static float cm[4] = {1, 1, 1, 1}, ca[4] = {0, 0, 0, 0};
static int blendMode = 1;
static int frameOpen;

static void* fbp0;
static void* fbp1;

typedef struct { void* p; void (*release)(void*); unsigned frame; } Retired;
static Retired* retired;
static int retiredN, retiredCap;
static void applyBlend(int mode);
static int traceFrames;   // the first frames are traced to spd_boot.txt (survives a crash on the PSP)

// Boot spinner: the game loads for a few seconds on one thread before its first frame, so a thread of its
// own draws a ring of dots in the bottom-right corner meanwhile. It uses a 16-bit buffer past the game's
// three framebuffers (still inside the 2 MB of VRAM) and stops before the game shows its first frame.
#define SPIN_BUF (3 * FB_SIZE)
static volatile int spinRun;
static SceUID spinThread = -1;

static void spinDot(volatile uint16_t* fb, int cx, int cy, uint16_t c) {
    for (int y = 0; y < 4; y++)
        for (int x = 0; x < 4; x++)
            if (!((x == 0 || x == 3) && (y == 0 || y == 3))) fb[(cy + y) * BUF_W + cx + x] = c;
}

static int spinner(SceSize args, void* argp) {
    pspSdkDisableFPUExceptions();   // FPU traps are per thread, see psp_main.c
    char* edram = sceGeEdramGetAddr();   // runs before psp_gfx_init
    volatile uint16_t* fb = (volatile uint16_t*) (((uintptr_t) edram + SPIN_BUF) | 0x40000000);   // uncached
    for (int i = 0; i < BUF_W * SCR_H; i++) fb[i] = 0;
    static const signed char pos[8][2] = { {0, -9}, {6, -6}, {9, 0}, {6, 6}, {0, 9}, {-6, 6}, {-9, 0}, {-6, -6} };
    const int cx = SCR_W - 22, cy = SCR_H - 22;
    for (int tick = 0; spinRun; tick++) {
        if (tick % 3 == 0) {
            // re-set every step: psp_gfx_init's sceGuDisplay queues its own buffer for display
            sceDisplaySetFrameBuf(edram + SPIN_BUF, BUF_W, PSP_DISPLAY_PIXEL_FORMAT_565, PSP_DISPLAY_SETBUF_NEXTFRAME);
            int head = (tick / 3) % 8;
            for (int i = 0; i < 8; i++) {
                int age = (head - i + 8) % 8;   // 0 = brightest, the tail fades behind it
                int g = age < 6 ? 31 - age * 5 : 4;
                uint16_t c = (uint16_t) (g | ((g * 2) << 5) | (g << 11));
                spinDot(fb, cx + pos[i][0] - 2, cy + pos[i][1] - 2, c);
            }
        }
        sceKernelDelayThread(30 * 1000);
    }
    return 0;
}

void psp_gfx_spinner_start(void) {
    // above the main thread (0x20) so it runs while the game loads, and sleeps between steps
    spinRun = 1;
    spinThread = sceKernelCreateThread("spinner", spinner, 0x18, 0x1000, PSP_THREAD_ATTR_USER, 0);
    if (spinThread >= 0) sceKernelStartThread(spinThread, 0, 0);
}

static void spinnerStop(void) {
    if (spinThread < 0) return;
    spinRun = 0;
    sceKernelWaitThreadEnd(spinThread, NULL);
    sceKernelDeleteThread(spinThread);
    spinThread = -1;
    psp_boot_log("boot spinner stopped");
}

void psp_gfx_init(void) {
    quadIndices = memalign(16, MAX_QUADS * 6 * sizeof(uint16_t));
    for (int q = 0; q < MAX_QUADS; q++) {
        uint16_t b = (uint16_t) (q * 4);
        uint16_t* i = quadIndices + q * 6;
        i[0] = b; i[1] = b + 1; i[2] = b + 2; i[3] = b; i[4] = b + 2; i[5] = b + 3;
    }
    sceKernelDcacheWritebackRange(quadIndices, MAX_QUADS * 6 * sizeof(uint16_t));

    fbp0 = (void*) 0;
    fbp1 = (void*) FB_SIZE;
    vram = sceGeEdramGetAddr();
    list = lists[0];

    sceGuInit();
    sceGuStart(GU_DIRECT, list);
    sceGuDrawBuffer(GU_PSM_8888, fbp0, BUF_W);
    sceGuDispBuffer(SCR_W, SCR_H, fbp1, BUF_W);
    sceGuOffset(2048 - (SCR_W / 2), 2048 - (SCR_H / 2));
    sceGuViewport(2048, 2048, SCR_W, SCR_H);
    sceGuScissor(0, 0, SCR_W, SCR_H);
    sceGuEnable(GU_SCISSOR_TEST);
    sceGuDisable(GU_DEPTH_TEST);
    sceGuDepthMask(GU_TRUE);
    sceGuDisable(GU_CULL_FACE);
    sceGuDisable(GU_LIGHTING);
    sceGuEnable(GU_TEXTURE_2D);
    sceGuEnable(GU_BLEND);
    sceGuBlendFunc(GU_ADD, GU_SRC_ALPHA, GU_ONE_MINUS_SRC_ALPHA, 0, 0);
    sceGuShadeModel(GU_FLAT);
    sceGuTexFunc(GU_TFX_MODULATE, GU_TCC_RGBA);
    sceGuTexEnvColor(0xFFFFFFFF);
    sceGuAmbientColor(0xFFFFFFFF);
    sceKernelDcacheWritebackAll();   // the GE reads the list (vertices, matrices) from RAM, past the CPU cache
    sceGuFinish();
    sceGuSync(0, 0);
    sceDisplayWaitVblankStart();
    sceGuDisplay(GU_TRUE);

    ScePspFMatrix4 id;
    memset(&id, 0, sizeof id);
    id.x.x = id.y.y = id.z.z = id.w.w = 1;
    sceGuStart(GU_DIRECT, list);
    sceGuSetMatrix(GU_VIEW, &id);
    sceGuSetMatrix(GU_MODEL, &id);
    sceGuSetMatrix(GU_PROJECTION, &id);
    sceKernelDcacheWritebackAll();   // the GE reads the list (vertices, matrices) from RAM, past the CPU cache
    sceGuFinish();
    sceGuSync(0, 0);
}

unsigned psp_gfx_use_tag(void) {
    return frameNo + 1;
}

// while frame N is built, the GE may still be drawing list N-1 (tag N); list N itself has tag N+1
int psp_gfx_in_flight(unsigned tag) {
    return tag != 0 && tag >= frameNo;
}

void psp_gfx_retire(void* p, void (*release)(void*)) {
    if (!p) return;
    if (retiredN == retiredCap) {
        int cap = retiredCap ? retiredCap * 2 : 64;
        Retired* r = realloc(retired, cap * sizeof(Retired));
        if (!r) { release(p); return; }
        retired = r;
        retiredCap = cap;
    }
    retired[retiredN].p = p;
    retired[retiredN].release = release;
    retired[retiredN].frame = frameNo;
    retiredN++;
}

// the GE has finished the list of frame 'done': memory retired up to then is no longer read
static void releaseRetired(unsigned done) {
    int k = 0;
    for (int i = 0; i < retiredN; i++) {
        if (retired[i].frame <= done) retired[i].release(retired[i].p);
        else retired[k++] = retired[i];
    }
    retiredN = k;
}

void psp_gfx_frame_begin(void) {
    if (traceFrames < 4) psp_boot_log("frame begin");
    frameStart = sceKernelGetSystemTimeLow();
    list = lists[frameNo & 1];
    drawBuf = frameNo % 3;
    listFull = 0;
    sceGuStart(GU_SEND, list);
    frameOpen = 1;
    sceGuDrawBufferList(GU_PSM_8888, (void*) (drawBuf * FB_SIZE), BUF_W);
    // the GU_SEND context keeps its own state flags, so the base state is set in every list
    sceGuEnable(GU_SCISSOR_TEST);
    sceGuDisable(GU_DEPTH_TEST);
    sceGuDisable(GU_CULL_FACE);
    sceGuDisable(GU_LIGHTING);
    sceGuEnable(GU_TEXTURE_2D);
    sceGuShadeModel(GU_FLAT);
    sceGuTexFunc(GU_TFX_MODULATE, GU_TCC_RGBA);
    sceGuTexEnvColor(0xFFFFFFFF);
    applyBlend(blendMode);
    ScePspFMatrix4 id;
    memset(&id, 0, sizeof id);
    id.x.x = id.y.y = id.z.z = id.w.w = 1;
    sceGuSetMatrix(GU_VIEW, &id);
    sceGuScissor(0, 0, SCR_W, SCR_H);
    sceGuClearColor(0xFF000000);
    sceGuClear(GU_COLOR_BUFFER_BIT);
    boundTex = NULL;
}

void psp_gfx_frame_end(void) {
    if (!frameOpen) return;
    frameOpen = 0;
    if (traceFrames < 4) psp_boot_log("frame drawn, GE start");
    unsigned t0 = sceKernelGetSystemTimeLow();
    sceKernelDcacheWritebackAll();   // the GE reads the list (vertices, matrices) from RAM, past the CPU cache
    unsigned used = (unsigned) sceGuFinish();

    // wait for the GE to finish the previous frame, then queue it for display at the next vblank
    sceGuSync(0, 0);
    if (frameNo > 0) releaseRetired(frameNo - 1);
    unsigned t1 = sceKernelGetSystemTimeLow();
    if (inFlight >= 0) {
        spinnerStop();   // the first game frame is about to be shown
        // this frame draws into the buffer shown two frames ago: it is free once the last frame set for
        // display has been latched, i.e. a vblank has passed since then (caps the rate at 60 fps)
        if (haveSet && sceDisplayGetVcount() == lastSetVcount) sceDisplayWaitVblankStart();
        sceDisplaySetFrameBuf((char*) vram + inFlight * FB_SIZE, BUF_W, PSP_DISPLAY_PIXEL_FORMAT_8888,
                              PSP_DISPLAY_SETBUF_NEXTFRAME);
        lastSetVcount = sceDisplayGetVcount();
        haveSet = 1;
    }
    unsigned t2 = sceKernelGetSystemTimeLow();
    sceGuSendList(GU_TAIL, list, &geContext);
    inFlight = drawBuf;
    frameNo++;

    statFrames++;
    statCpuUs += t0 - frameStart;
    statGeUs += t1 - t0;
    statVsyncUs += t2 - t1;
    if (used > statListMax) statListMax = used;
    if (traceFrames < 4) { traceFrames++; psp_boot_log("frame done"); }
}

// averages since the last call: [frames, cpu us, GE wait us, vsync wait us, draws, quads, texture binds, max list KB]
void psp_gfx_stats(void* outInts) {
    int32_t* o = outInts;
    unsigned f = statFrames ? statFrames : 1;
    o[0] = (int32_t) statFrames;
    o[1] = (int32_t) (statCpuUs / f);
    o[2] = (int32_t) (statGeUs / f);
    o[3] = (int32_t) (statVsyncUs / f);
    o[4] = (int32_t) (statDraws / f);
    o[5] = (int32_t) (statQuads / f);
    o[6] = (int32_t) (statBinds / f);
    o[7] = (int32_t) (statListMax >> 10);
    statFrames = statCpuUs = statGeUs = statVsyncUs = statDraws = statQuads = statBinds = statListMax = 0;
}

// true when the current list has room for 'bytes' more (a full list would overrun into other memory)
static int listRoom(int bytes) {
    if (listFull) return 0;
    if (sceGuCheckList() + bytes + LIST_RESERVE > LIST_SIZE) {
        if (!listFull) psp_log("display list full, dropping draws this frame");
        listFull = 1;
        return 0;
    }
    return 1;
}

void psp_gfx_texture(int32_t handle, int32_t flags) {
    PspImage* img = (PspImage*) handle;
    if (!img) { boundTex = NULL; return; }
    int changed = img->dirty;
    psp_img_prepare_gpu(img);
    PspImage* g = img->gpu ? img->gpu : img;
    img->gpuFrame = frameNo + 1;
    if (g != boundTex || changed) {
        statBinds++;
        sceGuTexMode(GU_PSM_8888, 0, 0, g->swz);
        sceGuTexImage(0, g->pw, g->ph, g->stride, g->px);
        sceGuTexScale((float) g->w / g->pw, (float) g->h / g->ph);
        sceGuTexOffset(0, 0);
        sceGuTexFlush();
        boundTex = g;
        texFlags = -1;
    }
    if (flags != texFlags) {
        int lin = flags & 1;
        sceGuTexFilter(lin ? GU_LINEAR : GU_NEAREST, lin ? GU_LINEAR : GU_NEAREST);
        // repeat on non-power-of-two images would wrap at the padded size, so only honour it when exact
        int rs = (flags & 2) && g->w == g->pw, rt = (flags & 4) && g->h == g->ph;
        sceGuTexWrap(rs ? GU_REPEAT : GU_CLAMP, rt ? GU_REPEAT : GU_CLAMP);
        texFlags = flags;
    }
}

void psp_gfx_camera(void* mat16) {
    sceGuSetMatrix(GU_PROJECTION, (ScePspFMatrix4*) mat16);
}

void psp_gfx_model(void* mat16) {
    sceGuSetMatrix(GU_MODEL, (ScePspFMatrix4*) mat16);
}

void psp_gfx_scissor(int32_t x, int32_t y, int32_t w, int32_t h) {
    if (w < 0) {
        sceGuScissor(0, 0, SCR_W, SCR_H);
        return;
    }
    int x1 = x + w, y1 = y + h;
    if (x < 0) x = 0;
    if (y < 0) y = 0;
    if (x1 > SCR_W) x1 = SCR_W;
    if (y1 > SCR_H) y1 = SCR_H;
    if (x1 <= x || y1 <= y) { x = y = 0; x1 = y1 = 1; }
    // pspdev's sceGuScissor takes width/height and also sets the drawing region from it:
    // a region past the framebuffer makes the GE write outside it on real hardware
    sceGuScissor(x, y, x1 - x, y1 - y);
}

void psp_gfx_color(float rm, float gm, float bm, float am, float ra, float ga, float ba, float aa) {
    cm[0] = rm; cm[1] = gm; cm[2] = bm; cm[3] = am;
    ca[0] = ra; ca[1] = ga; ca[2] = ba; ca[3] = aa;
}

void psp_gfx_lighting(int32_t enabled) {
    lighting = enabled;
}

static void applyBlend(int mode) {
    if (mode == 0) {
        sceGuDisable(GU_BLEND);
    } else {
        sceGuEnable(GU_BLEND);
        if (mode == 2) sceGuBlendFunc(GU_ADD, GU_SRC_ALPHA, GU_FIX, 0, 0xFFFFFFFF);
        else sceGuBlendFunc(GU_ADD, GU_SRC_ALPHA, GU_ONE_MINUS_SRC_ALPHA, 0, 0);
    }
}

void psp_gfx_blend(int32_t mode) {
    blendMode = mode;
    if (frameOpen) applyBlend(mode);
}

static inline unsigned clamp8(float v) {
    if (v <= 0) return 0;
    if (v >= 1) return 255;
    return (unsigned) (v * 255.0f + 0.5f);
}

static inline unsigned abgr(float r, float g, float b, float a) {
    return (clamp8(a) << 24) | (clamp8(b) << 16) | (clamp8(g) << 8) | clamp8(r);
}

// Draws with the current colour state: modulate pass, then the additive colorA pass if needed.
static void drawPrims(int prim, int vtype, int count, const void* indices, const void* verts) {
    if (!listRoom(256)) return;
    statDraws++;
    statQuads += count / 6;
    if (!boundTex) sceGuDisable(GU_TEXTURE_2D);
    // (a slightly negative channel is a hardlight(0xAARRGGBB) rounding artefact and is drawn clamped)
    if (lighting && (cm[0] < -0.01f || cm[1] < -0.01f || cm[2] < -0.01f)) {
        // inverted colours (WndStory text: colorM = -k, colorA = k), which the GE cannot multiply by:
        // blend colorA in at the texture's alpha, then subtract texture * |colorM| at that alpha
        unsigned add = abgr(ca[0], ca[1], ca[2], 1);
        sceGuColor((add & 0x00FFFFFF) | (clamp8(cm[3]) << 24));
        sceGuTexEnvColor(add);
        sceGuTexFunc(GU_TFX_BLEND, GU_TCC_RGBA);
        sceGuEnable(GU_BLEND);
        sceGuBlendFunc(GU_ADD, GU_SRC_ALPHA, GU_ONE_MINUS_SRC_ALPHA, 0, 0);
        sceGuDrawArray(prim, vtype, count, indices, verts);
        sceGuColor(abgr(-cm[0], -cm[1], -cm[2], cm[3]));
        sceGuTexFunc(GU_TFX_MODULATE, GU_TCC_RGBA);
        sceGuBlendFunc(GU_REVERSE_SUBTRACT, GU_SRC_ALPHA, GU_FIX, 0, 0xFFFFFFFF);
        sceGuDrawArray(prim, vtype, count, indices, verts);
        applyBlend(blendMode);
        if (!boundTex) sceGuEnable(GU_TEXTURE_2D);
        return;
    }
    if (lighting) sceGuColor(abgr(cm[0], cm[1], cm[2], cm[3]));
    else sceGuColor(0xFFFFFFFF);
    sceGuTexFunc(GU_TFX_MODULATE, GU_TCC_RGBA);
    sceGuDrawArray(prim, vtype, count, indices, verts);

    if (lighting && (ca[0] > 0.004f || ca[1] > 0.004f || ca[2] > 0.004f)) {
        // GU_TFX_BLEND with fragment and env colour both = colorA gives colorA, alpha = texture alpha * am
        unsigned add = abgr(ca[0], ca[1], ca[2], 1);
        sceGuColor((add & 0x00FFFFFF) | (clamp8(cm[3]) << 24));
        sceGuTexEnvColor(add);
        sceGuTexFunc(GU_TFX_BLEND, GU_TCC_RGBA);
        if (blendMode != 0) sceGuBlendFunc(GU_ADD, GU_SRC_ALPHA, GU_FIX, 0, 0xFFFFFFFF);
        else sceGuEnable(GU_BLEND), sceGuBlendFunc(GU_ADD, GU_SRC_ALPHA, GU_FIX, 0, 0xFFFFFFFF);
        sceGuDrawArray(prim, vtype, count, indices, verts);
        sceGuTexFunc(GU_TFX_MODULATE, GU_TCC_RGBA);
        applyBlend(blendMode);
    }
    if (!boundTex) sceGuEnable(GU_TEXTURE_2D);
}

#define VTYPE (GU_TEXTURE_32BITF | GU_VERTEX_32BITF | GU_TRANSFORM_3D)

static inline void convert(Vtx* out, const float* in, int vertices) {
    for (int i = 0; i < vertices; i++, in += 4, out++) {
        out->u = in[2];
        out->v = in[3];
        out->x = in[0];
        out->y = in[1];
        out->z = 0;
    }
}

void psp_gfx_quads(void* xyuv, int32_t quads) {
    const float* src = xyuv;
    while (quads > 0) {
        int n = quads > MAX_QUADS ? MAX_QUADS : quads;
        if (!listRoom(n * 4 * sizeof(Vtx) + 256)) return;
        Vtx* v = sceGuGetMemory(n * 4 * sizeof(Vtx));
        convert(v, src, n * 4);
        drawPrims(GU_TRIANGLES, VTYPE | GU_INDEX_16BIT, n * 6, quadIndices, v);
        src += n * 16;
        quads -= n;
    }
}

void psp_gfx_indexed(void* xyuv, int32_t vertices, void* indices, int32_t count) {
    if (vertices <= 0 || count <= 0) return;
    if (!listRoom(vertices * sizeof(Vtx) + count * 2 + 512)) return;
    Vtx* v = sceGuGetMemory(vertices * sizeof(Vtx));
    convert(v, xyuv, vertices);
    uint16_t* idx = sceGuGetMemory(((count * 2) + 15) & ~15);
    memcpy(idx, indices, count * 2);
    drawPrims(GU_TRIANGLES, VTYPE | GU_INDEX_16BIT, count, idx, v);
}

typedef struct { int floats; Vtx* verts; unsigned gpuFrame; } Vbo;

static void vboRelease(void* p) {
    free(p);
}

int32_t psp_vbo_create(int32_t floats) {
    Vbo* b = malloc(sizeof(Vbo));
    if (!b) return 0;
    b->floats = floats;
    b->gpuFrame = 0;
    b->verts = memalign(16, (floats / 4 + 1) * sizeof(Vtx));
    if (!b->verts) { free(b); return 0; }
    return (int32_t) b;
}

void psp_vbo_update(int32_t handle, void* xyuv, int32_t start, int32_t end) {
    Vbo* b = (Vbo*) handle;
    if (!b) return;
    if (end > b->floats) end = b->floats;
    int v0 = start / 4, v1 = (end + 3) / 4;
    if (v1 <= v0) return;
    if (psp_gfx_in_flight(b->gpuFrame)) {
        // a sent list still reads these vertices: the update goes into a copy
        size_t size = (b->floats / 4 + 1) * sizeof(Vtx);
        Vtx* copy = memalign(16, size);
        if (copy) {
            memcpy(copy, b->verts, size);
            psp_gfx_retire(b->verts, vboRelease);
            b->verts = copy;
            b->gpuFrame = 0;
            sceKernelDcacheWritebackRange(copy, size);
        }
    }
    // xyuv points at float index 'start'
    convert(b->verts + v0, (const float*) xyuv - (start - v0 * 4), v1 - v0);
    sceKernelDcacheWritebackRange(b->verts + v0, (v1 - v0) * sizeof(Vtx));
}

void psp_vbo_draw(int32_t handle, int32_t quadOffset, int32_t quads) {
    Vbo* b = (Vbo*) handle;
    if (!b) return;
    b->gpuFrame = frameNo + 1;
    int maxQuads = b->floats / 16 - quadOffset;
    if (quads > maxQuads) quads = maxQuads;
    while (quads > 0) {
        int n = quads > MAX_QUADS ? MAX_QUADS : quads;
        drawPrims(GU_TRIANGLES, VTYPE | GU_INDEX_16BIT, n * 6, quadIndices, b->verts + quadOffset * 4);
        quadOffset += n;
        quads -= n;
    }
}

void psp_vbo_free(int32_t handle) {
    Vbo* b = (Vbo*) handle;
    if (!b) return;
    if (psp_gfx_in_flight(b->gpuFrame)) psp_gfx_retire(b->verts, vboRelease);
    else free(b->verts);
    free(b);
}
