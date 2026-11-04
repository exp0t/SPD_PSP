#!/bin/bash
# Builds the EBOOT inside the WSL filesystem (much faster than compiling over /mnt/d) and copies it back.
set -e
export PSPDEV="$HOME/pspdev"
export PATH="$PSPDEV/bin:/usr/local/bin:/usr/bin:/bin"
SRC="$(cd "$(dirname "$0")/.." && pwd)"
DST="$HOME/spdbuild"
mkdir -p "$DST/build/c" "$DST/native"
rsync -rc --delete "$SRC/build/c/" "$DST/build/c/"
rsync -rc --exclude obj --exclude '*.o' --exclude '*.elf' --exclude '*.PBP' --exclude PARAM.SFO "$SRC/native/" "$DST/native/"
cd "$DST/native"
make -j"$(nproc)" "$@"
cp EBOOT.PBP "$SRC/native/EBOOT.PBP"
