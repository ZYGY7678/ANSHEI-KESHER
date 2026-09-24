#!/usr/bin/env python3
import csv, json, os, re, time, io, threading, concurrent.futures
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
rows_lock = threading.Lock()

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
        with rows_lock:
            if key in seen:
                continue
            seen.add(key)
            rows.append([city, category, name, address, ph, status, license_date,
                         lat, lon, website, hours, source])

def fetch_datastore(rid, limit=250000, page_size=5000, timeout=120):
    out, off = [], 0
    while off < limit:
        take = min(page_size, limit - off)
        b = get("https://data.gov.il/api/3/action/datastore_search",
                {"resource_id": rid, "limit": take, "offset": off}, timeout).get("result", {}).get("records", [])
        out.extend(b)
        if len(b) < take:
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


# 2b) Full regional OpenStreetMap extract, matching the POI model used by OsmAnd.
# OsmAnd processes OpenStreetMap POIs. The actual geographic data is taken from the
# regional OSM PBF extract, filtered to Israel and exported to GeoJSON with osmium-tool.
def import_osm_full_extract():
    try:
        import subprocess
        import urllib.request

        pbf_url = "https://download.geofabrik.de/asia/israel-and-palestine-latest.osm.pbf"
        work = os.path.join("build", "osm")
        os.makedirs(work, exist_ok=True)
        pbf_path = os.path.join(work, "israel-and-palestine-latest.osm.pbf")
        filtered_path = os.path.join(work, "israel-phone.osm.pbf")
        geojson_path = os.path.join(work, "israel-phone.geojson")

        if not os.path.exists(pbf_path) or os.path.getsize(pbf_path) < 1000000:
            print("OSM_PBF_DOWNLOAD=", pbf_url)
            urllib.request.urlretrieve(pbf_url, pbf_path)
        print("OSM_PBF_SIZE=", os.path.getsize(pbf_path))

        phone_tags = [
            "nwr/phone",
            "nwr/contact:phone",
            "nwr/contact:mobile",
            "nwr/contact:telephone",
            "nwr/mobile",
            "nwr/telephone"
        ]

        # Israel bounding box: west,south,east,north.
        subprocess.run([
            "osmium", "tags-filter", pbf_path,
            "--bbox", "34.2,29.4,35.9,33.4",
            *phone_tags, "-o", filtered_path, "--overwrite"
        ], check=True)

        subprocess.run([
            "osmium", "export", filtered_path,
            "--output-format", "geojson",
            "-o", geojson_path, "--overwrite"
        ], check=True)

        with open(geojson_path, "r", encoding="utf-8") as fh:
            fc = json.load(fh)

        features = fc.get("features", [])
        scanned = 0
        for feat in features:
            if len(rows) >= 120000:
                break
            scanned += 1
            props = feat.get("properties") or {}

            def pt(keys):
                for k in keys:
                    v = props.get(k)
                    if v not in (None, ""):
                        return clean(v)
                return ""

            name = pt(["name", "name:he", "name:en", "brand", "operator"])
            if not name:
                continue

            phone = pt([
                "phone", "contact:phone", "contact:mobile",
                "contact:telephone", "mobile", "telephone"
            ])
            if not phone:
                continue

            # Avoid obvious personal/residential records.
            if any(pt([k]) for k in ["contact:person", "person", "firstname", "lastname"]):
                continue
            if pt(["office"]) == "home":
                continue
            if not any(pt([k]) for k in [
                "shop","amenity","office","craft","tourism","healthcare","leisure",
                "education","industrial","public_transport","government","club",
                "sport","emergency","railway","aeroway","attraction","man_made",
                "operator","brand"
            ]):
                continue

            city = pt([
                "addr:city","addr:town","addr:village","addr:municipality",
                "addr:suburb","is_in:city","is_in:town","is_in"
            ])
            address = " ".join(
                x for x in [
                    pt(["addr:street","addr:place"]),
                    pt(["addr:housenumber","addr:housename"]),
                    pt(["addr:postcode"])
                ] if x
            )
            category = pt([
                "shop","amenity","office","craft","tourism","healthcare",
                "leisure","education","industrial","government","club",
                "sport","emergency","railway","aeroway","attraction","man_made"
            ]) or "עסק/שירות"
            website = pt(["website","contact:website","url"])
            hours = pt(["opening_hours","opening_hours:covid19"])
            status = "פעיל/מפורסם ב-OpenStreetMap" if hours else ""

            lat = lon = ""
            geom = feat.get("geometry") or {}
            coords = geom.get("coordinates")
            if geom.get("type") == "Point" and isinstance(coords, list) and len(coords) >= 2:
                lon, lat = str(coords[0]), str(coords[1])
            else:
                pts = []
                def collect(x):
                    if isinstance(x, list):
                        if len(x) >= 2 and isinstance(x[0], (int, float)) and isinstance(x[1], (int, float)):
                            pts.append((float(x[0]), float(x[1])))
                        else:
                            for y in x:
                                collect(y)
                collect(coords)
                if pts:
                    lon = str(sum(p[0] for p in pts) / len(pts))
                    lat = str(sum(p[1] for p in pts) / len(pts))

            add(city, category, name, address, phone, status, "", lat, lon,
                website, hours, "OpenStreetMap / OsmAnd POI data source")

        print("OSM_PBF_FEATURES_SCANNED=", scanned)
        print("OSM_PBF_ROWS_COMMITTED=", len(rows))
    except Exception as e:
        print("OSM_PBF_IMPORT_ERROR=", repr(e))

print("OSM_PBF_IMPORT_START rows=", len(rows))
import_osm_full_extract()

# 3) Exhaustive data.gov.il catalogue sweep.
# CKAN package_search defaults to *:* when q is omitted, and allows up to 1000 datasets/page.
# We inspect the full public catalogue, then fetch only DataStore resources containing a
# business/entity name and a public phone/contact field.
resource_meta = {}
for start_offset in (0, 1000, 2000):
    try:
        packs = get("https://data.gov.il/api/3/action/package_search",
                    {"q": "*:*", "rows": 1000, "start": start_offset}, 120).get("result", {}).get("results", [])
        if not packs:
            break
        for pack in packs:
            title = clean(pack.get("title"))
            notes = clean(pack.get("notes"))
            hay = (title + " " + notes).lower()
            if any(x in hay for x in [
                "אנשים פרטיים", "תושבים", "מצביעים", "מטופלים", "עובדים",
                "דורשי עבודה", "נישומים", "תלמידים בודדים", "בעלי רכב פרטיים"
            ]):
                continue
            for res in pack.get("resources", []):
                rid = res.get("id")
                if rid and res.get("datastore_active") and rid not in resource_meta:
                    resource_meta[rid] = {
                        "title": title,
                        "notes": notes,
                        "score": len(pack.get("resources", []))
                    }
    except Exception as e:
        print("FULL_CATALOG", start_offset, e)

# Also ensure high-value known public datasets are never missed by catalogue ranking.
known = {
    "3c5f8d70-b04d-49e0-acb8-e56231ef9d26": "מתחמי מרכזים מסחריים",
    "edf3a5f6-713c-4166-ad75-427f47496e39": "תורנות בתי מרקחת",
    "d65660ac-f895-463e-a8ed-3cdecc5d2c1b": "בתי מרקחת",
    "016c3f10-fd7d-4f34-a08a-178f93472f00": "בתי ספר שדה",
    "99b92311-9675-4351-85cd-9ed5ee69a787": "בתי ספר",
    "9c55a7dd-3b92-4141-811c-5e30cc74a8a4": "יצרני ועסקי מזון בעלי רישיון",
    "74cfb7ef-202a-409b-be08-33fb499b77de": "קבלני כוח אדם בעלי היתר",
    "c54032cb-5306-4be9-a20d-a0be0ba49cc1": "בתי עסק המחזיקים בתעודת כשרות",
    "c595a623-cbd1-45d8-9308-3fd71dfd7a5c": "תחנות דלק",
    "a323d256-e6fe-4804-a4ff-f2b87289cb53": "תחנות מוניות",
    "7f3009dd-b299-462c-8a6d-9c645b68059f": "תחנות כיבוי"
}
for rid, title in known.items():
    if rid not in resource_meta:
        resource_meta[rid] = {"title": title, "notes": "", "score": 999}

print("FULL_CATALOG_RESOURCES=", len(resource_meta))

phone_terms = ["phone","telephone","tel","mobile","contact","טלפון","נייד"]
name_terms = ["name","business","company","shop","facility","enterprise","עסק","שם","מוסד","סניף","חברה"]
addr_terms = ["address","street","city","town","locality","יישוב","ישוב","עיר","כתובת","רחוב"]
lat_terms = ["lat","latitude","קו רוחב"]
lon_terms = ["lon","longitude","קו אורך"]

# Rank metadata first; after that every eligible DataStore resource is inspected.

cands = sorted(resource_meta.items(), key=lambda kv: (-(1000 if kv[0] in known else 0), kv[1]["title"]))
print("RESOURCES_TO_SCAN=", len(cands))

phone_terms = ["phone","telephone","tel","mobile","contact","טלפון","נייד"]
name_terms = ["name","business","company","shop","facility","enterprise","עסק","שם","מוסד","סניף","חברה"]
addr_terms = ["address","street","city","town","locality","יישוב","ישוב","עיר","כתובת","רחוב"]
lat_terms = ["lat","latitude","קו רוחב"]
lon_terms = ["lon","longitude","קו אורך"]

def scan_resource(item):
    rid, meta = item
    if len(rows) >= 120000:
        return ("skip", rid, meta["title"], 0, len(rows))
    try:
        sample = get("https://data.gov.il/api/3/action/datastore_search",
                     {"resource_id": rid, "limit": 5}, 45).get("result", {}).get("records", [])
        if not sample:
            return ("none", rid, meta["title"], 0, len(rows))
        keys = [str(k) for k in sample[0].keys()]
        low = [k.lower() for k in keys]
        pk = [keys[i] for i,k in enumerate(low) if any(t in k for t in phone_terms)]
        nk = [keys[i] for i,k in enumerate(low) if any(t in k for t in name_terms)]
        ak = [keys[i] for i,k in enumerate(low) if any(t in k for t in addr_terms)]
        lak = [keys[i] for i,k in enumerate(low) if any(t in k for t in lat_terms)]
        lok = [keys[i] for i,k in enumerate(low) if any(t in k for t in lon_terms)]
        if not pk or not nk:
            return ("no-phone", rid, meta["title"], 0, len(rows))
        fetched = 0
        off = 0
        while off < 250000 and len(rows) < 120000:
            b2 = get("https://data.gov.il/api/3/action/datastore_search",
                     {"resource_id": rid, "limit": 5000, "offset": off}, 180).get("result", {}).get("records", [])
            if not b2:
                break
            for rec in b2:
                add(
                    pick(rec, ["city","עיר","יישוב","ישוב","yishuv","town","locality"]),
                    pick(rec, ["category","קטגוריה","סוג עסק","תחום","industry","תיאור","סוג"]),
                    pick(rec, nk + ["name","שם עסק","שם","שם המוסד","שם החברה"]),
                    pick(rec, ak + ["address","כתובת","רחוב"]),
                    pick(rec, pk + ["phone","טלפון","telephone","mobile"]),
                    pick(rec, ["status","סטטוס","מצב","פעיל","operating"]),
                    pick(rec, ["license_date","תאריך רישיון","תאריך היתר","תאריך"]),
                    pick(rec, lak + ["lat","latitude","קו רוחב"]),
                    pick(rec, lok + ["lon","longitude","קו אורך"]),
                    pick(rec, ["website","אתר","url","קישור"]),
                    pick(rec, ["hours","שעות פעילות","opening_hours"]),
                    meta["title"] or "data.gov.il"
                )
            fetched += len(b2)
            if len(b2) < 5000:
                break
            off += len(b2)
        return ("ok", rid, meta["title"], fetched, len(rows))
    except Exception as e:
        return ("error", rid, meta["title"], 0, len(rows))

completed = 0
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    futures = [pool.submit(scan_resource, item) for item in cands]
    for fut in concurrent.futures.as_completed(futures):
        result = fut.result()
        completed += 1
        if result[0] == "ok":
            print("BUSINESS_DATASET", result[1], result[2], "FETCHED=", result[3], "TOTAL=", result[4])
        if completed % 20 == 0:
            print("CATALOG_PROGRESS=", completed, "/", len(futures), "BUSINESS_ROWS=", len(rows))


# 4) Municipal/open-data mirror sweep (odata.org.il / Datacity).
# This mirror exposes public municipal datasets, including business licensing datasets
# with business name, phone, address and coordinates. We never scrape commercial directories.
odata_resources = {}
for q in ["עסקים", "רישוי עסקים", "מאגר עסקים", "עסקים בעלי רישיון עסק",
          "business license", "business phone"]:
    try:
        packs = get("https://www.odata.org.il/api/3/action/package_search",
                    {"q": q, "rows": 1000, "start": 0}, 120).get("result", {}).get("results", [])
        for pack in packs:
            title = clean(pack.get("title"))
            notes = clean(pack.get("notes"))
            hay = (title + " " + notes).lower()
            if any(x in hay for x in [
                "אנשים פרטיים", "תושבים", "מצביעים", "מטופלים", "עובדים",
                "דורשי עבודה", "נישומים", "תלמידים בודדים", "בעלי רכב פרטיים"
            ]):
                continue
            license_name = clean(pack.get("license_title") or pack.get("license"))
            for res in pack.get("resources", []):
                rid = res.get("id")
                if rid and res.get("datastore_active") and rid not in odata_resources:
                    odata_resources[rid] = {
                        "title": title,
                        "notes": notes,
                        "license": license_name
                    }
    except Exception as e:
        print("ODATA_DISCOVERY", q, e)

print("ODATA_RESOURCES=", len(odata_resources))

def scan_odata_resource(item):
    rid, meta = item
    if len(rows) >= 120000:
        return ("skip", rid, meta["title"], 0, len(rows))
    try:
        sample = get("https://www.odata.org.il/api/3/action/datastore_search",
                     {"resource_id": rid, "limit": 5}, 45).get("result", {}).get("records", [])
        if not sample:
            return ("none", rid, meta["title"], 0, len(rows))
        keys = [str(k) for k in sample[0].keys()]
        low = [k.lower() for k in keys]
        pk = [keys[i] for i,k in enumerate(low) if any(t in k for t in [
            "phone","telephone","tel","mobile","contact","טלפון","נייד","tlpvn"
        ])]
        nk = [keys[i] for i,k in enumerate(low) if any(t in k for t in [
            "name","business","company","shop","facility","enterprise","עסק","שם",
            "מוסד","סניף","חברה","shm"
        ])]
        ak = [keys[i] for i,k in enumerate(low) if any(t in k for t in [
            "address","street","city","town","locality","יישוב","ישוב","עיר",
            "כתובת","רחוב","ktvbt"
        ])]
        lak = [keys[i] for i,k in enumerate(low) if any(t in k for t in ["lat","latitude","point_y","קו רוחב"]) ]
        lok = [keys[i] for i,k in enumerate(low) if any(t in k for t in ["lon","longitude","point_x","קו אורך"]) ]
        if not pk or not nk:
            return ("no-phone", rid, meta["title"], 0, len(rows))

        fetched = 0
        off = 0
        while off < 250000 and len(rows) < 120000:
            b2 = get("https://www.odata.org.il/api/3/action/datastore_search",
                     {"resource_id": rid, "limit": 5000, "offset": off}, 180).get("result", {}).get("records", [])
            if not b2:
                break
            for rec in b2:
                add(
                    pick(rec, ["city","עיר","יישוב","ישוב","yishuv","town","locality"]),
                    pick(rec, ["category","קטגוריה","סוג עסק","תחום","industry","תיאור","מהות","קבוצה"]),
                    pick(rec, nk + ["name","שם עסק","שם","shm"]),
                    pick(rec, ak + ["address","כתובת","רחוב","ktvbt"]),
                    pick(rec, pk + ["phone","טלפון","telephone","tlpvn"]),
                    pick(rec, ["status","סטטוס","מצב","פעיל","operating","status_name"]),
                    pick(rec, ["license_date","תאריך רישיון","תאריך היתר","תאריך"]),
                    pick(rec, lak + ["lat","latitude","POINT_Y"]),
                    pick(rec, lok + ["lon","longitude","POINT_X"]),
                    pick(rec, ["website","אתר","url","קישור"]),
                    pick(rec, ["hours","שעות פעילות","opening_hours"]),
                    meta["title"] or "מידע לעם"
                )
            fetched += len(b2)
            if len(b2) < 5000:
                break
            off += len(b2)
        return ("ok", rid, meta["title"], fetched, len(rows))
    except Exception as e:
        return ("error", rid, meta["title"], 0, len(rows))

odata_items = sorted(odata_resources.items(), key=lambda kv: kv[1]["title"])
completed = 0
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    futures = [pool.submit(scan_odata_resource, item) for item in odata_items]
    for fut in concurrent.futures.as_completed(futures):
        result = fut.result()
        completed += 1
        if result[0] == "ok":
            print("ODATA_DATASET", result[1], result[2], "FETCHED=", result[3], "TOTAL=", result[4])
        if completed % 20 == 0:
            print("ODATA_PROGRESS=", completed, "/", len(futures), "BUSINESS_ROWS=", len(rows))

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
nwr["name:he"]["phone"](area.il);
nwr["name:he"]["contact:phone"](area.il);
nwr["name:en"]["phone"](area.il);
nwr["name:en"]["contact:phone"](area.il);
out tags center;'''
try:
    req = Request("https://overpass-api.de/api/interpreter", data=overpass.encode(),
                  headers={"User-Agent": UA, "Content-Type": "text/plain"})
    with urlopen(req, timeout=240) as r:
        data = json.loads(r.read().decode("utf-8","replace"))
    for e in data.get("elements", []):
        t = e.get("tags", {})
        # Keep named public places/services while excluding obvious residential/person nodes.
        if any(t.get(k) in ("house","apartments","residential","dwelling","home")
               for k in ("building","place","office")):
            continue
        if any(k in t for k in ("contact:person","person","firstname","lastname")):
            continue
        if not (t.get("shop") or t.get("office") or t.get("amenity") or t.get("craft") or
                t.get("tourism") or t.get("healthcare") or t.get("leisure") or
                t.get("public_transport") or t.get("industrial") or t.get("building") or
                t.get("man_made") or t.get("brand") or t.get("operator") or
                t.get("addr:street") or t.get("addr:city")):
            continue
        city = t.get("addr:city") or t.get("addr:town") or t.get("addr:village")
        lat = (e.get("lat") or (e.get("center") or {}).get("lat") or "")
        lon = (e.get("lon") or (e.get("center") or {}).get("lon") or "")
        add(
            city,
            t.get("shop") or t.get("amenity") or t.get("office") or t.get("craft") or
            t.get("tourism") or t.get("healthcare") or "עסק/שירות",
            t.get("name") or t.get("name:he") or t.get("name:en") or t.get("brand") or t.get("operator"),
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
            "", r.get("שעות קבלת קהל") or "", "משרד הרווחה - שירות שי\"ל")
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
