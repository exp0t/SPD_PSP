"""SPD PSP build: javac (SPD + PSP overrides) -> TeaVM C -> psp-gcc (WSL) -> EBOOT.PBP.
Usage: python psp/build.py [java] [c] [native] [assets] [all]   (default: java c native)"""
import os, shutil, subprocess, sys

PSP = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(PSP)
LIB = os.path.join(PSP, 'tools', 'lib')
BUILD = os.path.join(PSP, 'build')
CLASSES = os.path.join(BUILD, 'classes')
GEN = os.path.join(BUILD, 'c')
JAVA_SRC = [os.path.join(ROOT, p) for p in ('SPD-classes/src/main/java', 'core/src/main/java', 'services/src/main/java')]
OVERRIDES = os.path.join(PSP, 'java')
# originals dropped without replacement (GL-only helpers nothing else uses)
EXCLUDE = {'com/watabou/glwrap/Framebuffer.java', 'com/watabou/glwrap/Renderbuffer.java'}
MAIN = 'com.shatteredpixel.shatteredpixeldungeon.psp.PSPLauncher'
SEP = ';' if os.name == 'nt' else ':'


def run(cmd, **kw):
    print('>', ' '.join(cmd) if len(' '.join(cmd)) < 300 else ' '.join(cmd)[:300] + ' ...', flush=True)
    r = subprocess.run(cmd, **kw)
    if r.returncode != 0:
        sys.exit('command failed: %d' % r.returncode)


def java_sources():
    files = {}
    for base in JAVA_SRC + [OVERRIDES]:
        for d, _, names in os.walk(base):
            for n in names:
                if n.endswith('.java'):
                    rel = os.path.relpath(os.path.join(d, n), base).replace(os.sep, '/')
                    if rel not in EXCLUDE:
                        files[rel] = os.path.join(d, n)  # later bases (overrides) win
    return list(files.values())


def apply_patches(srcs):
    sys.path.insert(0, PSP)
    from patches import PATCHES
    out_dir = os.path.join(BUILD, 'patched')
    if os.path.isdir(out_dir):
        shutil.rmtree(out_dir)
    result = []
    for path in srcs:
        rel = next((r for r in PATCHES if path.replace('\\', '/').endswith('/' + r)), None)
        if rel is None:
            result.append(path)
            continue
        text = open(path, encoding='utf-8', newline='').read()
        for edit in PATCHES[rel]:
            for marker in edit[:-1]:
                if text.count(marker) != 1:
                    sys.exit('patch for %s does not match exactly once: %r' % (rel, marker[:70]))
            if len(edit) == 2:
                text = text.replace(edit[0], edit[1])
            else:
                a = text.index(edit[0])
                b = text.index(edit[1])
                if b < a:
                    sys.exit('patch for %s: end marker before start: %r' % (rel, edit[1][:70]))
                text = text[:a] + edit[2] + text[b:]
        dst = os.path.join(out_dir, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        open(dst, 'w', encoding='utf-8', newline='').write(text)
        result.append(dst)
    return result


def build_java():
    if os.path.isdir(CLASSES):
        shutil.rmtree(CLASSES)
    os.makedirs(CLASSES)
    srcs = apply_patches(java_sources())
    lst = os.path.join(BUILD, 'sources.txt')
    open(lst, 'w', encoding='utf-8').write('\n'.join('"%s"' % s.replace('\\', '/') for s in srcs))
    cp = SEP.join([os.path.join(LIB, 'teavm-interop-0.16.0.jar'), os.path.join(LIB, 'json-20170516.jar')])
    run(['javac', '--release', '11', '-encoding', 'UTF-8', '-nowarn', '-proc:none', '-Xmaxerrs', '400',
         '-cp', cp, '-d', CLASSES, '@' + lst])
    print('java: %d sources' % len(srcs))


def build_tools():
    # the TeaVM runner and its plugin are compiled on first use (and again when their source changes)
    runner = os.path.join(PSP, 'tools', 'runner')
    for src, out in (('TeaVMC.java', 'out'), (os.path.join('plugin', 'SingleThreadPlugin.java'), 'plugin')):
        src = os.path.join(runner, src)
        cls = os.path.join(runner, out, os.path.basename(src)[:-5] + '.class')
        if not os.path.exists(cls) or os.path.getmtime(cls) < os.path.getmtime(src):
            run(['javac', '--release', '11', '-nowarn', '-cp', os.path.join(LIB, '*'), '-d', os.path.join(runner, out), src])


def build_c():
    # TeaVM rewrites every file; generate into a scratch dir and copy over only what changed so make
    # recompiles just the touched classes
    build_tools()
    runner = os.path.join(PSP, 'tools', 'runner', 'out')
    fresh = GEN + '_new'
    if os.path.isdir(fresh):
        shutil.rmtree(fresh)
    jars = [os.path.join(LIB, j) for j in sorted(os.listdir(LIB)) if j.endswith('.jar')]
    plugin = os.path.join(PSP, 'tools', 'runner', 'plugin')
    run(['java', '-Xmx3g', '-cp', SEP.join([runner, plugin, os.path.join(LIB, '*')]), 'TeaVMC', MAIN, fresh, CLASSES, plugin] + jars)
    run([sys.executable, os.path.join(PSP, 'tools', 'patch_runtime.py'), fresh])
    changed = removed = 0
    keep = set()
    for d, _, names in os.walk(fresh):
        for n in names:
            src = os.path.join(d, n)
            rel = os.path.relpath(src, fresh)
            keep.add(rel)
            dst = os.path.join(GEN, rel)
            data = open(src, 'rb').read()
            if os.path.exists(dst) and open(dst, 'rb').read() == data:
                continue
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            open(dst, 'wb').write(data)
            changed += 1
    for d, _, names in os.walk(GEN):
        for n in names:
            rel = os.path.relpath(os.path.join(d, n), GEN)
            if rel not in keep:
                os.remove(os.path.join(d, n))
                removed += 1
    shutil.rmtree(fresh)
    print('c: %d files changed, %d removed' % (changed, removed))


def wsl_path(p):
    return '/mnt/' + p[0].lower() + p[2:].replace('\\', '/')


def build_native():
    # XMB icon (144x80) and background (480x272) from the project root
    for src, dst in (('ico.png', 'ICON0.PNG'), ('pic.png', 'PIC1.PNG')):
        if os.path.exists(os.path.join(ROOT, src)):
            shutil.copyfile(os.path.join(ROOT, src), os.path.join(PSP, 'native', dst))
    run(['wsl', '-d', 'Ubuntu', '--', 'bash', wsl_path(PSP) + '/native/wsl_build.sh'])


steps = sys.argv[1:] or ['java', 'c', 'native']
if 'all' in steps:
    steps = ['java', 'c', 'native', 'assets']
for s in steps:
    if s == 'java': build_java()
    elif s == 'c': build_c()
    elif s == 'native': build_native()
    elif s == 'assets':
        # assets.py needs Pillow and miniaudio (installed for Python 3.14 here)
        py = os.environ.get('PY314', os.path.expanduser(r'~\AppData\Local\Programs\Python\Python314\python.exe'))
        run([py, os.path.join(PSP, 'tools', 'assets.py')])
