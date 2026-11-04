"""Run an EBOOT in PPSSPP for N seconds and print what the homebrew wrote to stdout/stderr (log based, no screenshots).
Usage: python run_ppsspp.py <EBOOT.PBP> [seconds] [--all]"""
import os, re, subprocess, sys, time
PPSSPP = r'C:\Program Files\PPSSPP\PPSSPPWindows64.exe'
eboot = os.path.abspath(sys.argv[1])
secs = float(sys.argv[2]) if len(sys.argv) > 2 and not sys.argv[2].startswith('-') else 10
log = os.path.join(os.path.dirname(eboot), 'ppsspp.log')
if os.path.exists(log):
    os.remove(log)
p = subprocess.Popen([PPSSPP, '-d', '--log=' + log, '--windowed', eboot])
t0 = time.time()
while time.time() - t0 < secs and p.poll() is None:
    time.sleep(0.5)
    if os.path.exists(log) and b'SPD_EXIT' in open(log, 'rb').read():
        break
p.kill(); p.wait(); time.sleep(0.5)
out, other = [], []
for line in open(log, 'rb').read().decode('utf-8', 'replace').splitlines():
    m = re.search(r'\b(stdout|stderr): (.*)$', line)
    if m:
        out.append(m.group(2))
    elif '--all' in sys.argv or any(k in line for k in ('Bad memory', 'Invalid access', 'Unimpl', ' E[')):
        other.append(line)
print('\n'.join(out))
print('\n'.join(other[-30:]))
