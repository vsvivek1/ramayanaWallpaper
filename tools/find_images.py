"""Finds a freely licensed painting on Wikimedia Commons for each incident.

Runs on a GitHub Actions runner (the build machine can't reach Wikimedia).
Writes tools/out/images.json and tools/out/img/<id>.jpg (1080 px wide).
"""
import io, json, re, sys, time, html, urllib.parse, urllib.request
from PIL import Image

API = "https://commons.wikimedia.org/w/api.php"
UA = "RamayanamWallpapers-research/1.0 (https://github.com/vsvivek1/ramayanaWallpaper)"
NAMES = """Rama Sita Lakshmana Bharata Shatrughna Hanuman Ravana Dasharatha Kaikeyi Kausalya Sumitra
Manthara Vishwamitra Vasishtha Janaka Valmiki Narada Ahalya Gautama Parashurama Tataka Maricha Subahu
Ganga Bhagiratha Sagara Guha Bharadvaja Sumantra Jatayu Sampati Shurpanakha Khara Dushana Agastya Atri
Anasuya Shabari Kabandha Viradha Sugriva Vali Tara Angada Jambavan Nala Nila Vibhishana Kumbhakarna
Indrajit Meghanada Mandodari Lanka Ashoka Pushpaka Sushena Lava Kusha Ayodhya Kishkindha Chitrakuta
Panchavati Dandaka Mithila Shiva Vishnu Brahma Indra Varuna Surasa Simhika Lankini Akshaya Prahasta
Atikaya Kamadhenu Sharabhanga Sutikshna Pampa Rishyamuka Kartavirya Shambuka Ravi Varma""".split()
ALIAS = {"Meghanada": "Indrajit", "Kamadhenu": "Nandini", "Lakshmana": "Lakshman", "Vishwamitra": "Vishvamitra"}
BAD = re.compile(r"ballet|doll|golu|kecak|puppet|wayang|tropenmuseum|alamy|album-online|\balbum\b|bhishma|draupadi|"
                 r"shakuntala|damayanti|razmnama|asvatthama|pandava|prambanan|banteay|phnom|kerta.gosa|darbar|"
                 r"statue|sculpt|temple|idol|murti|relief|festival|dance|kathakali|yakshagana|perform|ramlila|"
                 r"actor|stamp|coin|map|logo|poster|banner|procession|puppet|tattoo|mural_in_progress|photo|"
                 r"\bfilm\b|movie|serial|cosplay|effigy|dussehra|dasara|carving|bronze|stone|terracotta|wood",
                 re.I)
GOOD = re.compile(r"ravi.?varma|painting|folio|miniature|mewar|pahari|kangra|guler|basohli|mughal|"
                  r"rajasthan|print|lithograph|manuscript|ramayana|gouache|watercolou?r|kalighat|tanjore|bazaar",
                  re.I)
FREE = re.compile(r"public domain|^pd|cc0|cc[ -]by", re.I)
KANDA_Q = {
    "Bala Kanda": ["Rama Vishwamitra painting", "Rama childhood Ramayana painting", "Bala Kanda Ramayana"],
    "Ayodhya Kanda": ["Ayodhya Kanda Ramayana folio", "Rama exile forest painting", "Bharata Rama painting"],
    "Aranya Kanda": ["Aranya Kanda Ramayana folio", "Rama Sita Lakshmana forest painting", "Panchavati painting"],
    "Kishkindha Kanda": ["Kishkindha Kanda Ramayana folio", "Sugriva Rama painting", "Vali Sugriva painting"],
    "Sundara Kanda": ["Sundara Kanda Ramayana folio", "Hanuman Lanka painting", "Hanuman Sita painting"],
    "Yuddha Kanda": ["Yuddha Kanda Ramayana folio", "Rama Ravana battle painting", "Lanka battle Ramayana painting"],
    "Uttara Kanda": ["Uttara Kanda Ramayana", "Lava Kusha painting", "Rama court Ayodhya painting"],
}

def get(params):
    params = dict(params, format="json", formatversion="2")
    url = API + "?" + urllib.parse.urlencode(params)
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except Exception as e:
            print("retry", e, file=sys.stderr); time.sleep(3 * (attempt + 1))
    return {}

def plain(s):
    return html.unescape(re.sub(r"<[^>]+>", "", s or "")).strip()

cache = {}
def search(q):
    if q in cache: return cache[q]
    d = get({"action": "query", "generator": "search", "gsrsearch": q + " filetype:bitmap", "gsrnamespace": 6,
             "gsrlimit": 30, "prop": "imageinfo", "iiprop": "url|size|mime|extmetadata", "iiurlwidth": 1080})
    out = []
    for p in (d.get("query", {}) or {}).get("pages", []):
        ii = (p.get("imageinfo") or [{}])[0]
        md = ii.get("extmetadata", {})
        lic = plain(md.get("LicenseShortName", {}).get("value"))
        desc = plain(md.get("ImageDescription", {}).get("value"))[:400]
        cats = plain(md.get("Categories", {}).get("value"))
        out.append(dict(title=p["title"][5:], index=p.get("index", 99), lic=lic, desc=desc, cats=cats,
                        artist=plain(md.get("Artist", {}).get("value"))[:120],
                        w=ii.get("width", 0), h=ii.get("height", 0), mime=ii.get("mime", ""),
                        thumb=ii.get("thumburl"), page=ii.get("descriptionurl")))
    cache[q] = out
    time.sleep(0.3)
    return out

def score(c, names):
    text = c["title"] + " " + c["desc"] + " " + c["cats"]
    if c["mime"] not in ("image/jpeg", "image/png") or not FREE.search(c["lic"]): return None
    if c["w"] < 700 or c["h"] < 500 or BAD.search(c["title"]) or BAD.search(c["desc"]): return None
    if REDO and not GOOD.search(text): return None
    if not re.search(r"ramayan|rama\b|sita|hanuman|ravana|ravi.?varma", text, re.I): return None
    s = 10 - c["index"] * 0.2
    s += 6 * sum(1 for n in names if re.search(n, text, re.I))
    if GOOD.search(text): s += 8
    if re.search(r"public domain|pd|cc0", c["lic"], re.I): s += 3
    if BAD.search(c["cats"]): s -= 6
    return s

import os
incidents = json.load(open("app/src/main/assets/incidents.json"))["incidents"]
REDO = [int(x) for x in os.environ.get("REDO", "").split(",") if x]
used, result = {}, {}
if REDO:
    result = json.load(open("tools/out/images.json"))
    for k, v in result.items():
        if int(k) not in REDO: used[v["image"]] = 1
    for k in REDO: result.pop(str(k), None)
    incidents = [i for i in incidents if i["id"] in REDO]
    result = {int(k): v for k, v in result.items()}
for inc in incidents:
    words = re.findall(r"[A-Z][a-z]+", inc["title"] + " " + inc["story"][:160])
    names = []
    for w in words:
        w = ALIAS.get(w, w) if w in ALIAS else w
        if w in NAMES or w in ALIAS.values():
            if w not in names: names.append(w)
    names = names[:3]
    queries = []
    if names: queries.append(" ".join(names) + " Ramayana")
    if names: queries.append(" ".join(names[:2]) + " painting")
    if names: queries.append(names[0] + " Ramayana painting")
    queries += KANDA_Q.get(inc["kanda"], [])
    best = None
    for qi, q in enumerate(queries):
        for c in search(q):
            s = score(c, names)
            if s is None: continue
            if REDO and c["title"] in used: continue
            s -= qi * 4 + used.get(c["title"], 0) * 25
            if best is None or s > best[0]: best = (s, c, q)
        if best and best[0] > 20 and qi < 3: break
    if best:
        s, c, q = best
        used[c["title"]] = used.get(c["title"], 0) + 1
        result[inc["id"]] = dict(image=c["title"], artist=c["artist"], license=c["lic"], page=c["page"],
                                 thumb=c["thumb"], score=round(s, 1), query=q, desc=c["desc"][:160])
        print(inc["id"], inc["title"], "->", c["title"], round(s, 1), flush=True)
    else:
        print(inc["id"], inc["title"], "-> none", flush=True)

os.makedirs("tools/out/img", exist_ok=True)
from PIL import ImageStat
for iid, r in result.items():
    if REDO and iid not in REDO: continue
    try:
        req = urllib.request.Request(r["thumb"], headers={"User-Agent": UA})
        data = urllib.request.urlopen(req, timeout=60).read()
        im = Image.open(io.BytesIO(data)).convert("RGB")
        if sum(ImageStat.Stat(im.convert("L")).stddev) < 28:
            print("too faint", iid, r["image"]); r["faint"] = True
        if im.width > 1080: im = im.resize((1080, round(im.height * 1080 / im.width)), Image.LANCZOS)
        im.save(f"tools/out/img/{iid}.jpg", quality=80, optimize=True, progressive=True)
        r["file"] = f"{iid}.jpg"
        time.sleep(0.2)
    except Exception as e:
        print("download failed", iid, e, file=sys.stderr)
json.dump({str(k): v for k, v in sorted(result.items())}, open("tools/out/images.json", "w"), indent=1, ensure_ascii=False)
print("found", len(result), "of", len(incidents), "unique", len(used))
