#!/usr/bin/env python3
import csv, json, os, re, time, io
from urllib.request import Request, urlopen
from urllib.parse import urlencode

OUT = "app/src/main/assets/businesses.csv"
LOCALITIES_OUT = "app/src/main/assets/localities.csv"
os.makedirs(os.path.dirname(OUT), exist_ok=True)

UA = "IsraelBusinessDirectory/3.0"

def get(url, p=None, t=120):
    if p:
        url += ("&" if "?" in url else "?") + urlencode(p)
    for n in range(3):
        try:
            with urlopen(Request(url, headers={"User-Agent": UA}), timeout=t) as r:
                return json.loads(r.read().decode("utf-8", "replace"))
        except Exception:
            if n == 2:
                raise
            time.sleep(2)

rows = []
seen = set()

def digits(v):
    return re.sub(r"[^0-9]", "", str(v or ""))

def clean(v):
    return str(v or "").strip()

def add(city, category, name, address, phone, status="", license_date="",
        lat="", lon="", website="", hours="", source=""):
    city, category, name, address, phone = map(clean, (city, category, name, address, phone))
    status, license_date, lat, lon, website, hours, source = map(
        clean, (status, license_date, lat, lon, website, hours, source)
    )
    if not name or not phone:
        return
    # Public/business phone boundary: keep only plausible phone strings.
    nums = re.findall(r"\+?\d[\d()\- .]{5,}\d", phone)
    if not nums:
        nums = [phone]
    for ph in nums:
        if len(digits(ph)) < 6:
            continue
        key = (city.lower(), name.lower(), digits(ph), address.lower())
        if key in seen:
            continue
        seen.add(key)
        rows.append([city, category, name, address, ph, status, license_date,
                     lat, lon, website, hours, source])

def fetch_datastore(rid, limit=250000):
    out, off = [], 0
    while off < limit:
        b = get("https://data.gov.il/api/3/action/datastore_search",
                {"resource_id": rid, "limit": 5000, "offset": off}, 180).get("result", {}).get("records", [])
        out.extend(b)
        if len(b) < 5000:
            break
        off += len(b)
    print("FETCH", rid, len(out))
    return out

def pick(r, words):
    low = {str(k).lower(): v for k, v in r.items()}
    for w in words:
        if w.lower() in low and low[w.lower()] not in ("", None):
            return low[w.lower()]
    for k, v in low.items():
        if v not in ("", None) and any(w.lower() in k for w in words):
            return v
    return ""

# 1) All Israeli localities / municipal metadata from CBS / data.gov.il.
localities = []
try:
    for r in fetch_datastore("d47a54ff-87f0-44b3-b33a-f284c0c38e5a"):
        name = clean(r.get("שם יישוב"))
        if not name:
            continue
        localities.append([
            name,
            clean(r.get("סמל יישוב")),
            clean(r.get("שם מחוז")),
            clean(r.get("שם מעמד מונציפאלי")),
            clean(r.get("אשכול רשויות מקומיות")),
            clean(r.get("סך הכל אוכלוסייה 2023 - ארעי")),
            clean(r.get("קואורדינטות")),
            clean(r.get("גובה ממוצע")),
            clean(r.get("שם יישוב באנגלית"))
        ])
except Exception as e:
    print("LOCALITIES", e)

# 2) Known official/public business sources.
for r in fetch_datastore("5555edc5-532d-46b5-8415-01a54d5a5b73"):
    add(r.get("city"), r.get("category"), r.get("name"), r.get("address"), r.get("phone"),
        source="משרד התיירות")

for r in fetch_datastore("7d4c61e2-2416-453e-8efb-bd02ec89db35"):
    add("באר שבע", r.get("תאור רישיון"), r.get("שם עסק"),
        " ".join(x for x in [r.get("שם רחוב"), clean(r.get("בית"))] if x),
        r.get("טלפון בעסק"), r.get("תאור סטטוס"), r.get("תאריך תוקף"),
        source="עיריית באר שבע")

for r in fetch_datastore("5f4f8927-b890-42ed-bb25-58d48b5f180f"):
    add("באר שבע", pick(r, ["category","קטגוריה","סוג עסק","תאור רישיון"]),
        pick(r, ["name","שם עסק","שם העסק"]),
        pick(r, ["address","כתובת","רחוב"]),
        pick(r, ["phone","טלפון","telephone"]),
        pick(r, ["status","סטטוס"]),
        pick(r, ["license_date","תאריך רישיון"]),
        pick(r, ["lat","latitude","קו רוחב"]),
        pick(r, ["lon","longitude","קו אורך"]),
        pick(r, ["website","אתר"]),
        pick(r, ["hours","שעות"]),
        "עיריית באר שבע")

for rid, cat, name, address, phone, city in [
    ("bb68386a-a331-4bbc-b668-bba2766d517d","sug_mosah","shem_mosah","ktovet","telephone","yishuv"),
    ("42e73a60-7acc-4c5d-b4ec-b0e468a73c51","isuk","shem_esek","ktovet","telephone","yishuv")
]:
    for r in fetch_datastore(rid):
        add(r.get(city), r.get(cat) or "עסק", r.get(name), r.get(address), r.get(phone),
            source="משרד התחבורה")

for r in fetch_datastore("3f06e2f2-e2ad-41ac-9665-37d0625537f2"):
    add(r.get("ezor"), "בית ספר לנהיגה", r.get("shem_beit_sefer"),
        r.get("ktovet"), r.get("telefon"), source="משרד התחבורה")

# 3) Discover additional open government business datasets automatically.
queries = ["עסקים טלפון", "רישוי עסקים טלפון", "בתי עסק טלפון",
           "מסחר טלפון", "business phone", "business license phone"]
resource_ids = []
for q in queries:
    try:
        packs = get("https://data.gov.il/api/3/action/package_search",
                    {"q": q, "rows": 100}, 120).get("result", {}).get("results", [])
        for pack in packs:
            title = clean(pack.get("title"))
            hay = (title + " " + clean(pack.get("notes"))).lower()
            if not any(x in hay for x in ["עסק","רישוי","מסחר","business","trade","license","commerce"]):
                continue
            for res in pack.get("resources", []):
                rid = res.get("id")
                if rid and res.get("datastore_active") and rid not in resource_ids:
                    resource_ids.append(rid)
    except Exception as e:
        print("DISCOVERY", e)

for rid in resource_ids[:150]:
    try:
        sample = get("https://data.gov.il/api/3/action/datastore_search",
                     {"resource_id": rid, "limit": 3}, 60).get("result", {}).get("records", [])
        if not sample:
            continue
        keys = [str(k).lower() for k in sample[0].keys()]
        pk = [k for k in keys if any(x in k for x in ["phone","tel","טלפון","telephone"])]
        nk = [k for k in keys if any(x in k for x in ["business","עסק","שם עסק","company","חברה","מסחר","name"])]
        if not pk or not nk:
            continue
        for r in fetch_datastore(rid):
            add(
                pick(r, ["city","עיר","ישוב","יישוב","yishuv","town"]),
                pick(r, ["category","קטגוריה","סוג עסק","industry","תחום"]),
                pick(r, nk + ["name","שם עסק"]),
                pick(r, ["address","כתובת","street","רחוב","ktovet"]),
                pick(r, pk + ["phone","טלפון","telephone"]),
                pick(r, ["status","סטטוס","מצב","operating"]),
                pick(r, ["license_date","תאריך רישיון","תאריך היתר","permit"]),
                pick(r, ["lat","latitude","קו רוחב","x"]),
                pick(r, ["lon","longitude","קו אורך","y"]),
                pick(r, ["website","אתר","url"]),
                pick(r, ["hours","שעות פעילות","opening_hours"]),
                "data.gov.il"
            )
    except Exception as e:
        print("DATASET", rid, e)

# 4) Public municipal information in Tel Aviv.
try:
    off = 0
    while True:
        fs = get(
            "https://gisn.tel-aviv.gov.il/ArcGIS/rest/services/WM/IView2WMTest/MapServer/925/query",
            {"where":"1=1","outFields":"*","returnGeometry":"true",
             "resultOffset":off,"resultRecordCount":2000,"f":"json"}, 180
        ).get("features", [])
        if not fs:
            break
        for f in fs:
            a = f.get("attributes", {})
            g = f.get("geometry") or {}
            add("תל אביב-יפו",
                a.get("category") or a.get("sug") or a.get("type"),
                a.get("shem") or a.get("name") or a.get("business_name"),
                a.get("address") or a.get("ktovet") or a.get("street"),
                a.get("ms_telephone") or a.get("phone") or a.get("telephone"),
                a.get("status") or a.get("STATUS"),
                a.get("license_date"),
                g.get("y") or a.get("lat") or a.get("latitude"),
                g.get("x") or a.get("lon") or a.get("longitude"),
                a.get("website") or a.get("URL"),
                a.get("hours") or a.get("opening_hours"),
                "עיריית תל אביב-יפו")
        if len(fs) < 2000:
            break
        off += len(fs)
        if off > 150000:
            break
except Exception as e:
    print("TELAVIV", e)

# 5) Nationwide named public businesses from OpenStreetMap.
overpass = '''[out:json][timeout:300];
area["ISO3166-1"="IL"][admin_level=2]->.il;
nwr["name"]["phone"](area.il);
nwr["name"]["contact:phone"](area.il);
out tags center;'''
try:
    req = Request("https://overpass-api.de/api/interpreter", data=overpass.encode(),
                  headers={"User-Agent": UA, "Content-Type": "text/plain"})
    with urlopen(req, timeout=420) as r:
        data = json.loads(r.read().decode("utf-8","replace"))
    for e in data.get("elements", []):
        t = e.get("tags", {})
        if not any(k in t for k in (
            "shop","office","amenity","craft","tourism","healthcare","leisure",
            "public_transport","industrial","building","man_made"
        )):
            continue
        city = t.get("addr:city") or t.get("addr:town") or t.get("addr:village")
        lat = (e.get("lat") or (e.get("center") or {}).get("lat") or "")
        lon = (e.get("lon") or (e.get("center") or {}).get("lon") or "")
        add(
            city,
            t.get("shop") or t.get("amenity") or t.get("office") or t.get("craft") or
            t.get("tourism") or t.get("healthcare") or "עסק/שירות",
            t.get("name") or t.get("name:he") or t.get("name:en"),
            " ".join(x for x in [t.get("addr:street"), t.get("addr:housenumber")] if x),
            t.get("phone") or t.get("contact:phone"),
            t.get("opening_hours") and "פעיל/מפורסם ב-OSM" or "",
            "",
            lat, lon,
            t.get("website") or t.get("contact:website"),
            t.get("opening_hours") or "",
            "OpenStreetMap"
        )
except Exception as e:
    print("OSM", e)

# 6) Public government/community service lines (no private individuals).
try:
    for r in fetch_datastore("1687655a-79ac-4858-bb35-dac71377b2e0"):
        add(r.get("עיר"), "שירות ממשלתי/ציבורי", r.get("שם תחנה"),
            r.get("כתובת") or " ".join(x for x in [r.get("רחוב"), clean(r.get("מספר"))] if x),
            r.get("טלפון"), "פעיל", "", "", "",
            "", r.get("שעות קבלת קהל") or "", "משרד הרווחה - שי"ל")
except Exception as e:
    print("SHIL", e)

with open(OUT, "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f)
    w.writerow(["city","category","name","address","phone","status","license_date",
                "lat","lon","website","hours","source"])
    w.writerows(rows)

with open(LOCALITIES_OUT, "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f)
    w.writerow(["name","code","district","municipal_status","authority_cluster",
                "population_2023","grid_coordinates","elevation","name_en"])
    w.writerows(localities)

print("LOCALITIES=", len(localities))
print("BUSINESS_ROWS=", len(rows))
print("UNIQUE_PHONE_NUMBERS=", len(set(digits(r[4]) for r in rows)))
print("TARGET_100K_REACHED=", len(rows) >= 100000)
