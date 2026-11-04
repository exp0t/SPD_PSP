// System helpers: exit, language, free memory. The OSK dialog lives here too.
#include "psp_api.h"
#include <pspkernel.h>
#include <psputility.h>
#include <pspsysmem.h>
#include <malloc.h>

void psp_sys_exit(void) {
    sceKernelExitGame();
}

int32_t psp_sys_language(void) {
    int lang = 1;
    sceUtilityGetSystemParamInt(PSP_SYSTEMPARAM_ID_INT_LANGUAGE, &lang);
    return lang;
}

// native (newlib) heap in use, in KB: textures, sounds, font atlas
int32_t psp_sys_free_kb(void) {
    struct mallinfo mi = mallinfo();
    return (int32_t) (mi.uordblks >> 10);
}

int32_t psp_osk(void* title, int32_t titleLen, void* init, int32_t initLen, void* out, int32_t maxLen) {
    return -1;
}
