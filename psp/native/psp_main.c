// PSP entry point and platform layer for the TeaVM C runtime (heap, clock).
#include <pspkernel.h>
#include <pspsysmem.h>
#include <psprtc.h>
#include <psppower.h>
#include <pspsdk.h>
#include <stdio.h>
#include <stdint.h>
#include <stdarg.h>
#include <wchar.h>
#include <stdlib.h>
#include <string.h>
#include "psp_api.h"

PSP_MODULE_INFO("SPD", 0, 1, 0);
PSP_MAIN_THREAD_ATTR(THREAD_ATTR_USER | THREAD_ATTR_VFPU);
// native heap (newlib malloc): textures, audio, fonts; the Java heap takes what is left
PSP_HEAP_SIZE_KB(18 * 1024);
PSP_MAIN_THREAD_STACK_SIZE_KB(1024);

int teavm_java_main(int argc, char** argv);

static void boot_log(const char* fmt, ...);

static char* heap_cur;
static char* heap_end;

// GC needs heap + heap/16 work area + small region tables; returns the heap size that fits.
int64_t psp_heap_budget(int64_t requested) {
    SceSize free = sceKernelMaxFreeMemSize() - 256 * 1024;
    SceUID id = sceKernelAllocPartitionMemory(PSP_MEMORY_PARTITION_USER, "javaheap", PSP_SMEM_Low, free, NULL);
    if (id < 0) {
        psp_log("javaheap alloc failed %08x (%u)\n", id, (unsigned) free);
        boot_log("java heap alloc FAILED %08x (%u bytes)", id, (unsigned) free);
        return 0;
    }
    heap_cur = sceKernelGetBlockHeadAddr(id);
    heap_end = heap_cur + free;
    int64_t heap = ((int64_t) free * 15 / 16 - 64 * 1024) & ~(int64_t) 0xFFFF;
    if (requested > 0 && heap > requested) heap = requested;
    // debug: heap_kb.txt next to the EBOOT caps the Java heap (to reproduce a PSP with less free memory)
    FILE* cap = fopen("heap_kb.txt", "r");
    if (cap) {
        int kb = 0;
        if (fscanf(cap, "%d", &kb) == 1 && kb > 1024 && ((int64_t) kb << 10) < heap) heap = ((int64_t) kb << 10) & ~(int64_t) 0xFFFF;
        fclose(cap);
    }
    psp_log("java heap %d KB (free block %u KB)\n", (int) (heap >> 10), (unsigned) free >> 10);
    boot_log("java heap %d KB (free block %u KB)", (int) (heap >> 10), (unsigned) free >> 10);
    return heap;
}

void* psp_heap_alloc(int64_t size) {
    size = (size + 63) & ~(int64_t) 63;
    if (heap_cur + size > heap_end) { psp_log("javaheap overflow %d\n", (int) size); return NULL; }
    void* p = heap_cur;
    heap_cur += size;
    return p;
}

static int64_t rtc_base_us;

int64_t psp_time_millis(void) {
    u64 tick;
    sceRtcGetCurrentTick(&tick); // microseconds since 0001-01-01 UTC
    return (int64_t) (tick - 62135596800000000ULL) / 1000;
}

int64_t psp_time_nanos(void) {
    return (int64_t) sceKernelGetSystemTimeWide() * 1000;
}

// Boot log (spd_boot.txt next to the EBOOT): written with sceIo only, no malloc/stdio, so it works even
// when the C heap could not be set up. Each line is appended and the file closed, so a crash keeps it.
static char boot_path[256];
static char log_path[256];   // spd_log.txt, same folder

static void append_file(const char* path, const char* data, int n) {
    SceUID fd = sceIoOpen(path, PSP_O_WRONLY | PSP_O_CREAT | PSP_O_APPEND, 0777);
    if (fd < 0) return;
    sceIoWrite(fd, data, n);
    sceIoClose(fd);
}

static void boot_log(const char* fmt, ...) {
    char buf[200];
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(buf, sizeof buf - 1, fmt, ap);
    va_end(ap);
    if (n < 0) return;
    if (n > (int) sizeof buf - 2) n = sizeof buf - 2;
    buf[n++] = '\n';
    // milliseconds since main() started, to see where the boot time goes
    static unsigned t0;
    unsigned now = sceKernelGetSystemTimeLow();
    if (!t0) t0 = now;
    char line[220];
    int m = snprintf(line, sizeof line, "%6u ", (now - t0) / 1000);
    memcpy(line + m, buf, n);
    append_file(boot_path, line, m + n);
}

// boot log line from any thread (psp_api.h)
void psp_boot_log(const char* msg) {
    boot_log("%s", msg);
}

static void boot_start(const char* argv0) {
    int len = 0;
    if (argv0) {
        const char* slash = strrchr(argv0, '/');
        if (slash && slash - argv0 < (int) sizeof boot_path - 16) len = (int) (slash - argv0) + 1;
        memcpy(boot_path, argv0, len);
    }
    strcpy(boot_path + len, "spd_boot.txt");
    memcpy(log_path, boot_path, len);
    strcpy(log_path + len, "spd_log.txt");
    SceUID fd = sceIoOpen(boot_path, PSP_O_WRONLY | PSP_O_CREAT | PSP_O_TRUNC, 0777);
    if (fd >= 0) sceIoClose(fd);
    fd = sceIoOpen(log_path, PSP_O_WRONLY | PSP_O_CREAT | PSP_O_TRUNC, 0777);
    if (fd >= 0) sceIoClose(fd);
}

static int exit_cb(int a, int b, void* c) { sceKernelExitGame(); return 0; }
static int cb_thread(SceSize args, void* argp) {
    int id = sceKernelCreateCallback("exit", exit_cb, NULL);
    sceKernelRegisterExitCallback(id);
    sceKernelSleepThreadCB();
    return 0;
}

int main(int argc, char** argv) {
    boot_start(argc > 0 ? argv[0] : NULL);
    psp_gfx_spinner_start();
    boot_log("SPD boot: firmware %08x", sceKernelDevkitVersion());
    // The real PSP traps float divide-by-zero / invalid operations by default (PPSSPP does not).
    // Java relies on IEEE results (Infinity, NaN), so the traps must be off in every thread that runs floats.
    unsigned fcr;
    __asm__ volatile("cfc1 %0, $31" : "=r"(fcr));
    pspSdkDisableFPUExceptions();
    unsigned fcr2;
    __asm__ volatile("cfc1 %0, $31" : "=r"(fcr2));
    boot_log("fpu fcr31 %08x -> %08x", fcr, fcr2);
    boot_log("user memory: total free %d KB, largest block %d KB",
             (int) (sceKernelTotalFreeMemSize() >> 10), (int) (sceKernelMaxFreeMemSize() >> 10));
    void* probe = malloc(1024 * 1024);
    boot_log("C heap (%d KB): %s", 18 * 1024, probe ? "ok" : "FAILED - large memory (64 MB) is not available");
    if (!probe) {
        // nothing below can work; leave the boot log and go back to the XMB instead of crashing
        sceKernelDelayThread(500 * 1000);
        sceKernelExitGame();
        return 1;
    }
    free(probe);

    int th = sceKernelCreateThread("cb", cb_thread, 0x11, 0xFA0, 0, 0);
    if (th >= 0) sceKernelStartThread(th, 0, 0);
    // homebrew starts at 222 MHz; SPD needs the full 333
    int clk = scePowerSetClockFrequency(333, 333, 166);
    boot_log("clock 333: %08x", clk);
    psp_gfx_init();
    boot_log("gfx ok");
    psp_font_init();
    boot_log("font ok");
    psp_audio_init();
    boot_log("audio ok, free %d KB, largest block %d KB, starting game",
             (int) (sceKernelTotalFreeMemSize() >> 10), (int) (sceKernelMaxFreeMemSize() >> 10));
    int r = teavm_java_main(argc, argv);
    printf("SPD_EXIT %d\n", r);
    fflush(stdout); fflush(stderr);
    sceKernelSleepThreadCB();
    return r;
}

// TeaVM log hooks (TEAVM_CUSTOM_LOG): UTF-8 to stdout, flushed per line so PPSSPP logs whole lines.
// Every line also goes to spd_log.txt next to the EBOOT, for reports from real hardware: appended and
// closed per line, since a hard crash on the PSP powers it off and loses whatever was not closed.
static char log_line[512];
static int log_len;

void teavm_logCodePoint(int32_t c) {
    if (c == '\n' || log_len > (int) sizeof(log_line) - 5) {
        log_line[log_len] = 0;
        puts(log_line);
        fflush(stdout);
        log_line[log_len] = '\n';
        append_file(log_path, log_line, log_len + 1);
        log_len = 0;
        if (c == '\n') return;
    }
    if (c < 0x80) log_line[log_len++] = (char) c;
    else if (c < 0x800) { log_line[log_len++] = 0xC0 | (c >> 6); log_line[log_len++] = 0x80 | (c & 0x3F); }
    else if (c < 0x10000) { log_line[log_len++] = 0xE0 | (c >> 12); log_line[log_len++] = 0x80 | ((c >> 6) & 0x3F); log_line[log_len++] = 0x80 | (c & 0x3F); }
    else { log_line[log_len++] = 0xF0 | (c >> 18); log_line[log_len++] = 0x80 | ((c >> 12) & 0x3F); log_line[log_len++] = 0x80 | ((c >> 6) & 0x3F); log_line[log_len++] = 0x80 | (c & 0x3F); }
}

void psp_log(const char* fmt, ...) {
    char buf[256];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);
    for (char* p = buf; *p; p++) teavm_logCodePoint((unsigned char) *p);
    if (log_len > 0) teavm_logCodePoint('\n');
}

void psp_log_line(void* utf8) {
    // bytes go through as-is; teavm_logCodePoint only cares about '\n' and copies the UTF-8 bytes below 0x80 path
    for (const unsigned char* p = utf8; *p; p++) {
        if (*p < 0x80) teavm_logCodePoint(*p);
        else if (log_len < (int) sizeof(log_line) - 1) log_line[log_len++] = (char) *p;
    }
    teavm_logCodePoint('\n');
}

void teavm_printString(uint16_t* s) {
    while (*s) {
        int32_t c = *s++;
        if ((c & 0xFC00) == 0xD800 && (*s & 0xFC00) == 0xDC00) c = (((c & 0x3FF) << 10) | (*s++ & 0x3FF)) + 0x10000;
        teavm_logCodePoint(c);
    }
}

void teavm_printWString(wchar_t* s) {
    while (*s) teavm_logCodePoint(*s++);
}

void teavm_printInt(int32_t i) {
    char buf[16];
    snprintf(buf, sizeof buf, "%d", (int) i);
    for (char* p = buf; *p; p++) teavm_logCodePoint(*p);
}

