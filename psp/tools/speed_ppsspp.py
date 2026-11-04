"""Measures emulation speed: runs the EBOOT in PPSSPP (no debug log, which slows emulation down) and
times the launcher's fps lines, which come every 5 s of emulated time.
Usage: python speed_ppsspp.py <EBOOT.PBP> [seconds]"""
import os, subprocess, sys, time
PPSSPP = r'C:\Program Files\PPSSPP\PPSSPPWindows64.exe'
eboot = os.path.abspath(sys.argv[1])
secs = float(sys.argv[2]) if len(sys.argv) > 2 else 40
log = os.path.join(os.path.dirname(eboot), 'spd_log.txt')
if os.path.exists(log):
    os.remove(log)
p = subprocess.Popen([PPSSPP, '--windowed', eboot])
t0 = time.time()
seen, stamps = 0, []
while time.time() - t0 < secs and p.poll() is None:
    time.sleep(0.1)
    if not os.path.exists(log):
        continue
    lines = [l for l in open(log, 'rb').read().decode('utf-8', 'replace').splitlines() if l.startswith('fps ')]
    for l in lines[seen:]:
        stamps.append(time.time())
        print('%6.1fs  %s' % (time.time() - t0, l))
    seen = len(lines)
p.kill(); p.wait()
for a, b in zip(stamps, stamps[1:]):
    print('5 s emulated took %.2f s real -> speed %d%%' % (b - a, 500 / (b - a)))
