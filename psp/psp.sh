#!/bin/bash
# Run a command with the pspdev toolchain from WSL: wsl -d Ubuntu -- bash /mnt/d/CODE/SPD_PSP/psp/psp.sh <dir> <cmd...>
export PSPDEV="$HOME/pspdev"
export PATH="$PSPDEV/bin:/usr/local/bin:/usr/bin:/bin"
cd "$1" || exit 1; shift
exec "$@"
