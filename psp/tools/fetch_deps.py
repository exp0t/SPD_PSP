# Resolve and download Maven artifacts (compile+runtime scope, transitive) into tools/lib.
import os, re, sys, urllib.request, xml.etree.ElementTree as ET
REPO = "https://repo1.maven.org/maven2"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "lib")
NS = "{http://maven.apache.org/POM/4.0.0}"
seen = {}

def get(url):
    with urllib.request.urlopen(url) as r:
        return r.read()

def pom(g, a, v):
    return ET.fromstring(get(f"{REPO}/{g.replace('.', '/')}/{a}/{v}/{a}-{v}.pom"))

def props(root, g, v):
    p = {"project.version": v, "project.groupId": g}
    par = root.find(NS + "parent")
    if par is not None:
        pg, pa, pv = (par.find(NS + x).text for x in ("groupId", "artifactId", "version"))
        p.update(props(pom(pg, pa, pv), pg, pv)); p["project.parent.version"] = pv
    pe = root.find(NS + "properties")
    if pe is not None:
        for e in pe: p[e.tag.replace(NS, "")] = (e.text or "").strip()
    return p

def sub(s, p):
    for _ in range(5):
        s = re.sub(r"\$\{([^}]+)\}", lambda m: p.get(m.group(1), m.group(0)), s)
    return s

def resolve(g, a, v):
    if a in seen: return
    seen[a] = v
    root = pom(g, a, v)
    p = props(root, g, v)
    pk = root.find(NS + "packaging")
    if pk is None or pk.text in ("jar", "bundle"):
        dst = os.path.join(OUT, f"{a}-{v}.jar")
        if not os.path.exists(dst):
            print("get", a, v)
            open(dst, "wb").write(get(f"{REPO}/{g.replace('.', '/')}/{a}/{v}/{a}-{v}.jar"))
    deps = root.find(NS + "dependencies")
    if deps is None: return
    for d in deps:
        sc = d.find(NS + "scope"); op = d.find(NS + "optional")
        if sc is not None and sc.text in ("test", "provided", "system"): continue
        if op is not None and op.text == "true": continue
        dv = d.find(NS + "version")
        if dv is None: continue
        resolve(sub(d.find(NS + "groupId").text, p), sub(d.find(NS + "artifactId").text, p), sub(dv.text, p))

os.makedirs(OUT, exist_ok=True)
for spec in sys.argv[1:]:
    resolve(*spec.split(":"))
