"""Patch the TeaVM C runtime files in a generated output dir for the PSP platform layer.
Usage: python patch_runtime.py <generated C dir>"""
import os, sys
d = sys.argv[1]
CRLF = chr(13) + chr(10)


def patch(name, old, new):
    p = os.path.join(d, name)
    s = open(p, encoding='utf-8', newline='').read()
    if CRLF in s:
        old, new = old.replace('\n', CRLF), new.replace('\n', CRLF)
    if new in s:
        return
    if old not in s:
        sys.exit('patch_runtime: anchor not found in %s: %r' % (name, old[:60]))
    open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))


# Java heap: one partition block sized from free memory (psp_heap_budget in psp_main.c), carved by a bump allocator.
patch('memory.c', '''void* teavm_virtualAlloc(int64_t size) {
    return malloc(size);
}''', '''void* psp_heap_alloc(int64_t size);
void* teavm_virtualAlloc(int64_t size) {
    return psp_heap_alloc(size);
}''')
patch('memory.c', '''void teavm_initHeap(int64_t minHeap, int64_t maxHeap) {
''', '''void teavm_initHeap(int64_t minHeap, int64_t maxHeap) {
#if TEAVM_PSP
    int64_t psp_heap_budget(int64_t requested);
    maxHeap = psp_heap_budget(maxHeap);
    minHeap = maxHeap;
#endif
''')
patch('time.c', '''        return (int64_t) sceKernelLibcTime(NULL) * 1000;
    }
    int64_t teavm_currentTimeNano() {
        return (int64_t) sceKernelLibcTime(NULL) * 1000000000;
    }''', '''        int64_t psp_time_millis(void);
        return psp_time_millis();
    }
    int64_t teavm_currentTimeNano() {
        int64_t psp_time_nanos(void);
        return psp_time_nanos();
    }''')

# Packed class references are (cls - teavm_beforeClasses) >> 3, so every class must be 8-aligned.
# On 32-bit MIPS the dynamic (array) class pool is only 4-aligned and its element size is not a multiple of 8.
patch('arrayclass.c', '''static TEAVM_OBJECT_CLASS teavm_dynamicClassPool[TEAVM_DYNAMIC_CLASS_POOL_CAPACITY];''',
      '''typedef struct { TEAVM_OBJECT_CLASS cls; } __attribute__((aligned(8))) teavm_PaddedClass;
static teavm_PaddedClass teavm_dynamicClassPool[TEAVM_DYNAMIC_CLASS_POOL_CAPACITY];''')
patch('arrayclass.c', '''    TEAVM_OBJECT_CLASS* ptr = &teavm_dynamicClassPool[teavm_dynamicClassPoolSize++];''',
      '''    TEAVM_OBJECT_CLASS* ptr = &teavm_dynamicClassPool[teavm_dynamicClassPoolSize++].cls;''')
patch('arrayclass.c', '''    return &teavm_dynamicClassPool[index].parent;''',
      '''    return &teavm_dynamicClassPool[index].cls.parent;''')

# defined in a header: fine for the unity build (all.c), duplicate symbols when every class is its own object
patch('core.h', '''    int32_t teavm_initNewInstance(void* cls, void* obj) {''',
      '''    static inline int32_t teavm_initNewInstance(void* cls, void* obj) {''')
