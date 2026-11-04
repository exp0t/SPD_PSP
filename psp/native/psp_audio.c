// Audio: one stereo 44.1 kHz hardware channel fed by a mixer thread.
// Sound effects are WAV (converted from SPD's mp3 by tools/assets.py) kept in RAM. Music is SPD's MP3 set
// (made for iOS): the whole file is loaded into RAM and decoded by the PSP's hardware MP3 decoder (sceMp3,
// Media Engine) in a decoder thread that keeps a ring buffer filled. tremor from pspdev decoded garbage.
#include "psp_api.h"
#include <malloc.h>
#include <pspaudio.h>
#include <pspkernel.h>
#include <pspsdk.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <pspmp3.h>
#include <psputility.h>

#define OUT_RATE 44100
#define FRAMES 1024
#define VOICES 12
#define RING 32768                 // music frames (stereo) decoded ahead, ~0.74 s; power of two
#define FRAC 12                    // voice positions are 20.12 fixed point frames (sounds up to 1M frames)

typedef struct { int16_t* pcm; int frames, channels, rate; } Snd;
typedef struct { Snd* s; uint32_t pos, step; int lv, rv; } Voice;

static Voice voices[VOICES];
static SceUID lock;
static int channel = -1;

// music requests from the game thread; the decoder thread picks them up by generation
static char reqPath[256];
static int reqLoop;
static volatile int reqGen, curGen;
static volatile int musicActive;   // ring holds data of the current request
static volatile int musicEof;      // decoder reached the end of a non-looping track
static volatile int musicPaused;
static volatile int musicVol = 256;
static int musicRate = OUT_RATE;
static int16_t ring[RING * 2];
static volatile uint32_t ringRd, ringWr;
static uint32_t musicFrac;

// stats for the launcher log: slowest mix in microseconds, times the music ring ran dry
static volatile int statMaxMix, statStarve;

static void acquire(void) { sceKernelWaitSema(lock, 1, NULL); }
static void release(void) { sceKernelSignalSema(lock, 1); }

static inline int16_t clip(int v) { return v > 32767 ? 32767 : v < -32768 ? -32768 : (int16_t) v; }

static void mix(int16_t* out) {
    static int acc[FRAMES * 2];
    memset(acc, 0, sizeof acc);
    acquire();
    for (int v = 0; v < VOICES; v++) {
        Voice* vo = &voices[v];
        if (!vo->s) continue;
        Snd* s = vo->s;
        for (int i = 0; i < FRAMES; i++) {
            int f = vo->pos >> FRAC;
            if (f >= s->frames) { vo->s = NULL; break; }
            int l, r;
            if (s->channels == 1) l = r = s->pcm[f];
            else { l = s->pcm[f * 2]; r = s->pcm[f * 2 + 1]; }
            acc[i * 2] += (l * vo->lv) >> 8;
            acc[i * 2 + 1] += (r * vo->rv) >> 8;
            vo->pos += vo->step;
        }
    }
    if (musicActive && !musicPaused) {
        uint32_t step = (uint32_t) (((uint64_t) musicRate << 16) / OUT_RATE);
        int vol = musicVol;
        for (int i = 0; i < FRAMES; i++) {
            if ((int) (ringWr - ringRd) <= 0) {
                if (!musicEof) statStarve++;
                break;
            }
            int idx = (ringRd & (RING - 1)) * 2;
            acc[i * 2] += (ring[idx] * vol) >> 8;
            acc[i * 2 + 1] += (ring[idx + 1] * vol) >> 8;
            musicFrac += step;
            ringRd += musicFrac >> 16;
            musicFrac &= 0xFFFF;
        }
        if ((int) (ringWr - ringRd) < 0) ringRd = ringWr;
    }
    release();
    for (int i = 0; i < FRAMES * 2; i++) out[i] = clip(acc[i]);
}

// debug: with a file named audiodump next to the EBOOT, the first DUMP_FRAMES of output go to audio_dump.raw
#define DUMP_FRAMES (12 * OUT_RATE)
static int16_t* dump;
static int dumpLen;

static int audioThread(SceSize args, void* argp) {
    pspSdkDisableFPUExceptions();   // FPU traps are per thread, see psp_main.c
    static int16_t __attribute__((aligned(64))) buf[2][FRAMES * 2];
    int cur = 0;
    for (;;) {
        unsigned t0 = sceKernelGetSystemTimeLow();
        mix(buf[cur]);
        int dt = (int) (sceKernelGetSystemTimeLow() - t0);
        if (dt > statMaxMix) statMaxMix = dt;
        if (dump && dumpLen < DUMP_FRAMES) {
            memcpy(dump + dumpLen * 2, buf[cur], sizeof buf[cur]);
            dumpLen += FRAMES;
            if (dumpLen >= DUMP_FRAMES) {
                FILE* f = fopen("audio_dump.raw", "wb");
                if (f) { fwrite(dump, 4, dumpLen, f); fclose(f); }
            }
        }
        // the audio DMA reads RAM, not the CPU cache
        sceKernelDcacheWritebackRange(buf[cur], sizeof buf[cur]);
        sceAudioOutputPannedBlocking(channel, PSP_AUDIO_VOLUME_MAX, PSP_AUDIO_VOLUME_MAX, buf[cur]);
        cur ^= 1;
    }
    return 0;
}

// --- music decoder ---

static uint8_t* loadFile(const char* path, long* size) {
    FILE* f = fopen(path, "rb");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    uint8_t* data = malloc(n);
    if (data && fread(data, 1, n, f) != (size_t) n) { free(data); data = NULL; }
    fclose(f);
    *size = n;
    return data;
}

static int mp3Ready;
static uint8_t mp3Buf[16 * 1024] __attribute__((aligned(64)));
static uint8_t pcmBuf[16 * 1152] __attribute__((aligned(64)));

// copies the part of the file sceMp3 asks for from RAM into its stream buffer; returns bytes added
static int mp3Feed(int handle, const uint8_t* data, long size) {
    unsigned char* dst;
    SceInt32 want, pos;
    if (sceMp3GetInfoToAddStreamData(handle, &dst, &want, &pos) < 0) return -1;
    long n = size - pos;
    if (n > want) n = want;
    if (n < 0) n = 0;
    memcpy(dst, data + pos, n);
    sceKernelDcacheWritebackRange(dst, (unsigned) n);   // the Media Engine reads RAM, not the CPU cache
    sceMp3NotifyAddStreamData(handle, (int) n);
    return (int) n;
}

// first steps of each run go to spd_boot.txt, which survives a crash on the PSP
static int traceDecodes;
static void trace(const char* fmt, ...) {
    char buf[200];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);
    static int lines;
    if (lines++ < 40) psp_boot_log(buf);
}

static int decoderThread(SceSize args, void* argp) {
    pspSdkDisableFPUExceptions();   // FPU traps are per thread, see psp_main.c
    int handle = -1, loop = 0;
    uint8_t* data = NULL;
    long size = 0;
    for (;;) {
        int gen = reqGen;
        if (gen != curGen) {
            char path[256];
            acquire();
            musicActive = 0;
            ringRd = ringWr = 0;
            musicFrac = 0;
            strcpy(path, reqPath);
            loop = reqLoop;
            release();
            if (handle >= 0) sceMp3ReleaseMp3Handle(handle);
            handle = -1;
            free(data);
            data = NULL;
            if (path[0] && mp3Ready) {
                char* dot = strrchr(path, '.');
                if (dot) strcpy(dot, ".mp3");
                data = loadFile(path, &size);
                trace("music: loaded %s (%ld bytes)", path, data ? size : -1L);
                SceMp3InitArg init;
                init.mp3StreamStart = 0;
                init.mp3StreamEnd = size;
                init.mp3Buf = mp3Buf;
                init.mp3BufSize = sizeof mp3Buf;
                init.pcmBuf = pcmBuf;
                init.pcmBufSize = sizeof pcmBuf;
                if (data) handle = sceMp3ReserveMp3Handle(&init);
                trace("music: reserve handle %08x", handle);
                if (handle >= 0) {
                    mp3Feed(handle, data, size);
                    trace("music: fed");
                    int r = sceMp3Init(handle);
                    trace("music: init %08x", r);
                    if (r < 0) {
                        psp_log("sceMp3Init %s failed %08x", path, r);
                        sceMp3ReleaseMp3Handle(handle);
                        handle = -1;
                    }
                }
                if (handle >= 0) {
                    sceMp3SetLoopNum(handle, loop ? -1 : 0);
                    musicRate = sceMp3GetSamplingRate(handle);
                    if (dump) psp_log("music %s", path);
                    musicEof = 0;
                    acquire();
                    if (reqGen == gen) musicActive = 1;
                    release();
                } else {
                    psp_log("music open failed %s", path);
                }
            }
            curGen = gen;
            continue;
        }
        if (handle >= 0 && !musicEof && RING - (int) (ringWr - ringRd) >= 1152) {
            if (sceMp3CheckStreamDataNeeded(handle) > 0) mp3Feed(handle, data, size);
            short* pcm;
            int n = sceMp3Decode(handle, &pcm);
            if (traceDecodes < 3) { traceDecodes++; trace("music: decode %08x", n); }
            // the decoder wrote pcmBuf behind the cache; drop stale lines (buffer is 64-byte aligned and sized)
            sceKernelDcacheInvalidateRange(pcmBuf, sizeof pcmBuf);
            if (n > 0) {
                int frames = n / 4;   // sceMp3 output is 16-bit stereo
                uint32_t wr = ringWr;
                for (int i = 0; i < frames; i++, wr++) {
                    int idx = (wr & (RING - 1)) * 2;
                    ring[idx] = pcm[i * 2];
                    ring[idx + 1] = pcm[i * 2 + 1];
                }
                ringWr = wr;   // publish after the samples are written
            } else if (n == 0 || n == (int) 0x80671402) {
                if (loop) {
                    sceMp3ResetPlayPosition(handle);
                } else {
                    musicEof = 1;
                }
            } else if (sceMp3CheckStreamDataNeeded(handle) <= 0) {
                psp_log("sceMp3Decode %08x", n);
                musicEof = 1;
            }
            continue;
        }
        sceKernelDelayThread(5000);
    }
    return 0;
}

void psp_audio_init(void) {
    lock = sceKernelCreateSema("audio", 0, 1, 1, NULL);
    FILE* flag = fopen("audiodump", "rb");
    if (flag) {
        fclose(flag);
        dump = malloc((DUMP_FRAMES + FRAMES) * 4);
    }
    channel = sceAudioChReserve(PSP_AUDIO_NEXT_CHANNEL, FRAMES, PSP_AUDIO_FORMAT_STEREO);
    if (channel < 0) { psp_log("audio channel failed %08x\n", channel); return; }
    int r1 = sceUtilityLoadModule(PSP_MODULE_AV_AVCODEC), r2 = sceUtilityLoadModule(PSP_MODULE_AV_MP3);
    int r3 = sceMp3InitResource();
    mp3Ready = r3 >= 0;
    if (!mp3Ready) psp_log("mp3 init failed %08x %08x %08x\n", r1, r2, r3);
    // mixer above the decoder, both above the game thread (0x20)
    int th = sceKernelCreateThread("mixer", audioThread, 0x12, 0x8000, PSP_THREAD_ATTR_USER, NULL);
    if (th >= 0) sceKernelStartThread(th, 0, NULL);
    th = sceKernelCreateThread("musicdec", decoderThread, 0x16, 0x20000, PSP_THREAD_ATTR_USER, NULL);
    if (th >= 0) sceKernelStartThread(th, 0, NULL);
}

// slowest mix in us (low 20 bits) and music underruns (high bits) since the last call
int32_t psp_audio_stats(void) {
    int r = (statMaxMix & 0xFFFFF) | (statStarve << 20);
    statMaxMix = 0;
    statStarve = 0;
    return r;
}

static uint32_t rd32(const uint8_t* p) { return p[0] | (p[1] << 8) | (p[2] << 16) | ((uint32_t) p[3] << 24); }
static uint16_t rd16(const uint8_t* p) { return p[0] | (p[1] << 8); }

int32_t psp_snd_load(void* pathArg) {
    char path[256];
    strncpy(path, (const char*) pathArg, sizeof path - 5);
    path[sizeof path - 5] = 0;
    char* dot = strrchr(path, '.');
    if (dot) strcpy(dot, ".wav");
    long n;
    uint8_t* data = loadFile(path, &n);
    if (!data) { psp_log("sound missing %s\n", path); return 0; }
    Snd* s = NULL;
    if (n > 12 && memcmp(data, "RIFF", 4) == 0 && memcmp(data + 8, "WAVE", 4) == 0) {
        int channels = 1, rate = 22050;
        long p = 12;
        while (p + 8 <= n) {
            uint32_t sz = rd32(data + p + 4);
            if (memcmp(data + p, "fmt ", 4) == 0) {
                channels = rd16(data + p + 10);
                rate = (int) rd32(data + p + 12);
            } else if (memcmp(data + p, "data", 4) == 0) {
                if (p + 8 + sz > (uint32_t) n) sz = n - p - 8;
                s = malloc(sizeof(Snd));
                s->channels = channels;
                s->rate = rate;
                s->frames = (int) (sz / (2 * channels));
                s->pcm = malloc(sz);
                memcpy(s->pcm, data + p + 8, sz);
                break;
            }
            p += 8 + sz + (sz & 1);
        }
    }
    free(data);
    if (!s) psp_log("bad wav %s\n", path);
    return (int32_t) s;
}

int32_t psp_snd_play(int32_t snd, float left, float right, float rate) {
    Snd* s = (Snd*) snd;
    if (!s || channel < 0) return -1;
    acquire();
    int slot = 0;
    uint32_t best = 0;
    for (int v = 0; v < VOICES; v++) {
        if (!voices[v].s) { slot = v; break; }
        if (voices[v].pos > best) { best = voices[v].pos; slot = v; }   // steal the voice that played longest
    }
    Voice* vo = &voices[slot];
    vo->s = s;
    vo->pos = 0;
    vo->step = (uint32_t) ((float) s->rate * rate / OUT_RATE * (1 << FRAC));
    vo->lv = (int) (left * 256);
    vo->rv = (int) (right * 256);
    release();
    return slot;
}

void psp_snd_stop_all(void) {
    acquire();
    for (int v = 0; v < VOICES; v++) voices[v].s = NULL;
    release();
}

static void request(const char* path, int loop) {
    acquire();
    strncpy(reqPath, path, sizeof reqPath - 1);
    reqPath[sizeof reqPath - 1] = 0;
    reqLoop = loop;
    reqGen++;
    musicActive = 0;
    musicPaused = 0;
    release();
}

int32_t psp_music_play(void* path, int32_t loop) {
    if (channel < 0) return 0;
    request((const char*) path, loop);
    return 1;
}

void psp_music_stop(void) { request("", 0); }

void psp_music_pause(int32_t paused) { musicPaused = paused; }

void psp_music_volume(float vol) {
    musicVol = (int) (vol * 256);
}

// true from the request until a non-looping track has played out (or failed to open)
int32_t psp_music_playing(void) {
    if (!reqPath[0]) return 0;
    if (curGen != reqGen) return 1;
    return musicActive && !(musicEof && ringWr == ringRd);
}
