# Links the TeaVM output (../build/c, one object per generated file) with the PSP platform layer.
TARGET = spd
GEN = ../build/c
GENSRC := $(shell cat $(GEN)/all.txt)
GENOBJ := $(addprefix obj/,$(GENSRC:.c=.o))
NATIVE = psp_main.o psp_gfx.o psp_img.o psp_file.o psp_font.o psp_input.o psp_audio.o psp_sys.o
OBJS = $(NATIVE) $(GENOBJ)

OPT ?= -Os
INCDIR = $(PSPDEV)/psp/include/freetype2
CFLAGS = $(OPT) -G0 -Wall -Wno-unused -fno-strict-aliasing
GENFLAGS = $(OPT) -G0 -w -fno-strict-aliasing -D_GNU_SOURCE -DTEAVM_CUSTOM_LOG=1 -DTEAVM_PSP=1 -Dmain=teavm_java_main \
	-iquote . -I$(PSPDEV)/psp/include -I$(PSPDEV)/psp/sdk/include -D_PSP_FW_VERSION=600

LIBS = -lpspmp3 -lfreetype -lpng -lz -lbz2 -lpspgu -lpspdisplay -lpspge -lpspctrl -lpsputility -lpsprtc -lpsppower \
	-lpspaudio -lpspdebug -lm

PSP_LARGE_MEMORY = 1
LDFLAGS = -Wl,--allow-multiple-definition
EXTRA_TARGETS = EBOOT.PBP
PSP_EBOOT_TITLE = Shattered Pixel Dungeon
PSP_EBOOT_ICON = ICON0.PNG
PSP_EBOOT_PIC1 = PIC1.PNG

PSPSDK = $(shell psp-config --pspsdk-path)
include $(PSPSDK)/lib/build.mak

obj/%.o: $(GEN)/%.c
	@mkdir -p $(dir $@)
	@$(CC) $(GENFLAGS) -MMD -MP -c -o $@ $<

$(NATIVE): psp_api.h

# header dependencies of the generated code (class layouts live in headers)
-include $(GENOBJ:.o=.d)
