// Buttons and analog stick for PSPInput.
#include "psp_api.h"
#include <pspctrl.h>

static int inited;

int32_t psp_input_poll(void* outInts) {
    int32_t* out = outInts;
    if (!inited) {
        sceCtrlSetSamplingCycle(0);
        sceCtrlSetSamplingMode(PSP_CTRL_MODE_ANALOG);
        inited = 1;
    }
    SceCtrlData pad;
    sceCtrlPeekBufferPositive(&pad, 1);
    out[0] = (int32_t) pad.Buttons;
    out[1] = pad.Lx;
    out[2] = pad.Ly;
    return 1;
}
