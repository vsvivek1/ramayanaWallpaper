"""Finds freely licensed paintings on Wikimedia Commons for each Bhagavatham incident.

Runs on a GitHub Actions runner (the build machine can't reach Wikimedia).
MODE=candidates (default): up to 4 candidates per incident -> tools/out/candidates.json,
  thumbnails tools/out/cand/<id>_<k>.jpg and contact sheets tools/out/sheets/<n>.jpg for review.
MODE=fetch: downloads the chosen file for each incident listed in tools/picks.json
  ({"<id>": "Commons file name"}) at 1000 px -> tools/out/img/<id>.jpg, metadata -> tools/out/images.json.
"""
import io, json, os, re, sys, time, html, urllib.parse, urllib.request
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
API = "https://commons.wikimedia.org/w/api.php"
UA = "BhagavathamWallpapers-research/1.0 (https://github.com/vsvivek1/ramayanaWallpaper)"
BAD = re.compile(r"ballet|doll|golu|kecak|puppet|wayang|tropenmuseum|alamy|album-online|razmnama|"
                 r"prambanan|banteay|phnom|angkor|kerta.gosa|statue|sculpt|temple|idol|murti|relief|festival|"
                 r"dance|kathakali|yakshagana|perform|ramlila|raslila|actor|stamp|coin|\bmap\b|logo|poster|banner|"
                 r"procession|tattoo|photo|\bfilm\b|movie|serial|cosplay|effigy|carving|bronze|stone|terracotta|"
                 r"wood|iskcon|janmashtami|pandal|devotees|\bdeity\b|shrine|chariot.festival|ratha.?yatra", re.I)
GOOD = re.compile(r"ravi.?varma|painting|folio|miniature|mewar|pahari|kangra|guler|basohli|mughal|nurpur|chamba|"
                  r"rajasthan|bikaner|kishangarh|jaipur|malwa|deccan|print|lithograph|manuscript|bhagavat|gouache|"
                  r"watercolou?r|kalighat|tanjore|mysore|bazaar|pattachitra|gita press|chromolithograph", re.I)
TOPIC = re.compile(r"krishna|vishnu|bhagavat|purana|narayana|balarama|gopi|radha|yashoda|nanda|kamsa|shiva|brahma|"
                   r"narasimha|prahlad|vamana|varaha|matsya|kurma|dhruva|rama\b|parashurama|lakshmi|indra|narada|"
                   r"avatar|garuda|devaki|vasudeva|rukmini|sudama|uddhava|markandeya|ganga|churning|samudra", re.I)
FREE = re.compile(r"public domain|^pd|cc0|cc[ -]by", re.I)
SK_Q = ["Bhagavata Purana folio", "Bhagavata Purana painting", "Krishna painting Pahari", "Vishnu painting"]

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

def info(pages):
    out = []
    for p in pages:
        ii = (p.get("imageinfo") or [{}])[0]
        md = ii.get("extmetadata", {})
        out.append(dict(title=p["title"][5:], index=p.get("index", 99),
                        lic=plain(md.get("LicenseShortName", {}).get("value")),
                        desc=plain(md.get("ImageDescription", {}).get("value"))[:400],
                        cats=plain(md.get("Categories", {}).get("value")),
                        artist=plain(md.get("Artist", {}).get("value"))[:120],
                        w=ii.get("width", 0), h=ii.get("height", 0), mime=ii.get("mime", ""),
                        thumb=ii.get("thumburl"), page=ii.get("descriptionurl")))
    return out

cache = {}
def search(q):
    if q in cache: return cache[q]
    d = get({"action": "query", "generator": "search", "gsrsearch": q + " filetype:bitmap", "gsrnamespace": 6,
             "gsrlimit": 40, "prop": "imageinfo", "iiprop": "url|size|mime|extmetadata", "iiurlwidth": 1000})
    cache[q] = info((d.get("query", {}) or {}).get("pages", []))
    time.sleep(0.25)
    return cache[q]

def score(c, names, scene):
    text = c["title"] + " " + c["desc"] + " " + c["cats"]
    if c["mime"] not in ("image/jpeg", "image/png") or not FREE.search(c["lic"]): return None
    if c["w"] < 600 or c["h"] < 450 or BAD.search(c["title"]) or BAD.search(c["desc"]): return None
    if not GOOD.search(text) or not TOPIC.search(text): return None
    s = 10 - c["index"] * 0.15
    s += 7 * sum(1 for n in names if re.search(n[:6], text, re.I))
    s += 2 * sum(1 for w in re.findall(r"[a-z]{5,}", scene.lower()) if re.search(w, text, re.I))
    s += 6
    if re.search(r"public domain|pd|cc0", c["lic"], re.I): s += 3
    if BAD.search(c["cats"]): s -= 6
    return s

def fetch_img(url, width):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    im = Image.open(io.BytesIO(urllib.request.urlopen(req, timeout=60).read())).convert("RGB")
    if im.width > width: im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
    return im

incidents = json.load(open(os.path.join(ROOT, "app/src/main/assets/incidents.json")))["incidents"]
OUT = os.path.join(ROOT, "tools/out")
MODE = os.environ.get("MODE", "candidates")
ONLY = [int(x) for x in os.environ.get("ONLY", "").split(",") if x]

if MODE == "pool":
    # Numbered contact sheets of tools/pool.json (unused candidates), 30 per sheet, for a last review pass.
    pool = json.load(open(os.path.join(ROOT, "tools/pool.json")))
    os.makedirs(OUT + "/pool", exist_ok=True)
    try: font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
    except Exception: font = ImageFont.load_default()
    for n in range(0, len(pool), 30):
        sheet = Image.new("RGB", (6 * 250, 5 * 250), "white"); d = ImageDraw.Draw(sheet)
        for k, c in enumerate(pool[n:n + 30]):
            try:
                im = fetch_img(c["thumb"], 360); im.thumbnail((240, 220))
                sheet.paste(im, ((k % 6) * 250 + 5, (k // 6) * 250 + 26))
            except Exception as e:
                print("pool thumb failed", n + k, e, file=sys.stderr)
            d.text(((k % 6) * 250 + 6, (k // 6) * 250 + 2), str(n + k), fill="red", font=font)
        sheet.save(f"{OUT}/pool/{n}-{min(n + 29, len(pool) - 1)}.jpg", quality=72)
elif MODE == "candidates":
    os.makedirs(OUT + "/cand", exist_ok=True); os.makedirs(OUT + "/sheets", exist_ok=True)
    path = OUT + "/candidates.json"
    result = json.load(open(path)) if (ONLY and os.path.exists(path)) else {}
    used = {}
    pk = os.path.join(ROOT, "tools/picks.json")
    if ONLY and os.path.exists(pk):
        for t in json.load(open(pk)).values(): used[t] = used.get(t, 0) + 1
    for inc in incidents:
        if ONLY and inc["id"] not in ONLY: continue
        names, scene = inc.get("names", []), inc.get("scene", "")
        extra = [q for q in os.environ.get("Q_%d" % inc["id"], "").split("|") if q]
        queries = extra + [" ".join(names) + " painting", " ".join(names[:2]) + " Bhagavata Purana",
                   scene, names[0] + " painting" if names else "", inc["title"]] + SK_Q
        found = {}
        for qi, q in enumerate([q for q in queries if q.strip()]):
            for c in search(q):
                s = score(c, names, scene)
                if s is None: continue
                s -= qi * 2 + used.get(c["title"], 0) * 12
                if c["title"] not in found or found[c["title"]][0] < s: found[c["title"]] = (s, c)
        best = sorted(found.values(), key=lambda x: -x[0])[:4]
        cands = []
        for k, (s, c) in enumerate(best):
            try:
                fetch_img(c["thumb"], 360).save(f"{OUT}/cand/{inc['id']}_{k}.jpg", quality=72)
                cands.append(dict(image=c["title"], artist=c["artist"], license=c["lic"], page=c["page"],
                                  thumb=c["thumb"], score=round(s, 1), desc=c["desc"][:160]))
            except Exception as e:
                print("thumb failed", inc["id"], e, file=sys.stderr)
        if cands: used[cands[0]["image"]] = used.get(cands[0]["image"], 0) + 1
        result[str(inc["id"])] = cands
        print(inc["id"], inc["title"], "->", [c["image"][:50] for c in cands], flush=True)
    json.dump(result, open(path, "w"), indent=1, ensure_ascii=False)
    # Contact sheets: 10 incidents per sheet, a row of up to 4 candidates each.
    try: font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 18)
    except Exception: font = ImageFont.load_default()
    ids = [i["id"] for i in incidents if not ONLY or i["id"] in ONLY]
    titles = {i["id"]: i["title"] for i in incidents}
    for n in range(0, len(ids), 10):
        chunk = ids[n:n + 10]
        sheet = Image.new("RGB", (4 * 370 + 10, len(chunk) * 300), "white")
        d = ImageDraw.Draw(sheet)
        for r, iid in enumerate(chunk):
            d.text((8, r * 300 + 4), f"{iid}. {titles[iid]}", fill="black", font=font)
            for k in range(4):
                f = f"{OUT}/cand/{iid}_{k}.jpg"
                if not os.path.exists(f): continue
                im = Image.open(f); im.thumbnail((360, 268))
                sheet.paste(im, (10 + k * 370, r * 300 + 28))
                d.text((14 + k * 370, r * 300 + 30), str(k), fill="red", font=font)
        tag = "-".join(map(str, [chunk[0], chunk[-1]]))
        sheet.save(f"{OUT}/sheets/{'redo-' if ONLY else ''}{tag}.jpg", quality=70)
else:
    picks = json.load(open(os.path.join(ROOT, "tools/picks.json")))
    os.makedirs(OUT + "/img", exist_ok=True)
    meta_path = OUT + "/images.json"
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    titles = sorted(set(picks.values()))
    for i in range(0, len(titles), 40):
        d = get({"action": "query", "titles": "|".join("File:" + t for t in titles[i:i + 40]),
                 "prop": "imageinfo", "iiprop": "url|size|mime|extmetadata", "iiurlwidth": 1000})
        for c in info((d.get("query", {}) or {}).get("pages", [])):
            for iid, t in picks.items():
                if t.replace("_", " ") == c["title"].replace("_", " "):
                    meta[iid] = dict(image=c["title"], artist=c["artist"], license=c["lic"], page=c["page"], thumb=c["thumb"])
    for iid, m in meta.items():
        f = f"{OUT}/img/{iid}.jpg"
        if os.path.exists(f) and picks.get(iid, "").replace("_", " ") == m["image"].replace("_", " "): continue
        try:
            fetch_img(m["thumb"], 1000).save(f, quality=82, optimize=True, progressive=True)
            time.sleep(0.2)
        except Exception as e:
            print("download failed", iid, e, file=sys.stderr)
    json.dump(dict(sorted(meta.items(), key=lambda x: int(x[0]))), open(meta_path, "w"), indent=1, ensure_ascii=False)
    print("fetched", len(meta), "of", len(picks))
