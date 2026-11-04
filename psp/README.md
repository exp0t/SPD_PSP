# Shattered Pixel Dungeon — PSP

The game's Java code is compiled to C by TeaVM and built with pspdev. Only the platform layer is new.

Port by yotta imoto and Claude. Target: PSP-2000/3000/Go with custom firmware (the game needs the 64 MB models).

## Build

```
python psp/tools/fetch_deps.py org.teavm:teavm-tooling:0.16.0 org.teavm:teavm-classlib:0.16.0 com.badlogicgames.gdx:gdx:1.14.0 org.json:json:20170516
python psp/build.py          # java + c + native
python psp/build.py assets   # resources -> psp/dist/SPD
```

Requires JDK 21, Python 3.8+ (Pillow and miniaudio for `assets`), and pspdev in WSL Ubuntu (`~/pspdev`).
`psp/tools/deps.txt` lists the jars the build expects in `psp/tools/lib`.
The result goes to `psp/dist/SPD/` (EBOOT.PBP + assets); copy the folder to `ms0:/PSP/GAME/`.

## Layout

- `java/` — libGDX shims (`com/badlogic/gdx`) and PSP versions of engine files (same path as the original wins)
- `patches.py` — small edits to SPD sources at build time (single-threaded actor loop and others)
- `native/` — C layer: sceGu renderer, images, FreeType text, controls, audio, files
- `tools/` — TeaVM runner and plugin, C runtime patches, resource conversion, PPSSPP run with log
- `assets/music/` — SPD's MP3 music set (from its iOS build), decoded in hardware on the PSP

The game sources (`SPD-classes/`, `core/`, `services/`) are Shattered Pixel Dungeon 4.0.2, unchanged: PSP edits live in `java/` and `patches.py`.
