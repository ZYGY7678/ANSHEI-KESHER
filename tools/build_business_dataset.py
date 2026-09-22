#!/usr/bin/env python3
import csv, json, os, re, time
from urllib.request import Request, urlopen
from urllib.parse import urlencode

OUT = "app/src/main/assets/businesses.csv"
os.makedirs(os.path.dirname(OUT), exist_ok=True)

def get_json(url, params=None, timeout=120):
    if params:
        url += ("&" if "?" in url else "?") + urlencode(params)
    req = Request(url, headers={"User-Agent":"IsraelBusinessDirectory/1.0"})
    with urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8", "replace"))

rows = []
seen = set()

def add(city, category, name, address, phone, source):
    city = str(city or "").strip()
    category = str(category or "").strip()
    name = str(name or "").strip()
    address = str(address or "").strip()
    phone = str(phone or "").strip()
    phone = re.sub(r"\\s+", " ", phone)
    digits = re.sub(r"[^0-9+]", "", phone)
    if len(re.sub(r"[^0-9]", "", phone)) < 6 or not name:
        return
    key = (name.lower(), digits)
    if key in seen:
        return
    seen.add(key)
    rows.append([city, category, name, address, phone, source])

def fetch_ckan(resource_id):
    url = "https://data.gov.il/api/3/action/datastore_search"
    data = get_json(url, {"resource_id":resource_id, "limit":5000}, 180)
    return data.get("result", {}).get("records", [])

# Government/public datasets with explicit business phone fields.
for rec in fetch_ckan("5555edc5-532d-46b5-8415-01a54d5a5b73"):
    add(rec.get("city"), rec.get("category"), rec.get("name"), rec.get("address"), rec.get("phone"), "data.gov.il/משרד התיירות")

for rec in fetch_ckan("7d4c61e2-2416-453e-8efb-bd02ec89db35"):
    add("באר שבע", rec.get("תאור רישיון") or "רישוי עסקים", rec.get("שם עסק"), 
        " ".join(x for x in [rec.get("שם רחוב"), str(rec.get("בית") or "")] if x),
        rec.get("טלפון בעסק"), "data.gov.il/באר שבע")

# Tel Aviv municipal business layer. Paginate because the service caps responses.
base = "https://gisn.tel-aviv.gov.il/ArcGIS/rest/services/WM/IView2WMTest/MapServer/925/query"
offset = 0
while True:
    data = get_json(base, {
        "where":"1=1","outFields":"*","returnGeometry":"false",
        "resultOffset":offset,"resultRecordCount":2000,"f":"json"
    }, 180)
    feats = data.get("features", [])
    if not feats: break
    for f in feats:
        a = f.get("attributes", {})
        name = a.get("shem") or a.get("name") or a.get("business_name")
        phone = a.get("ms_telephone") or a.get("phone") or a.get("telephone")
        address = a.get("address") or a.get("ktovet") or a.get("street")
        cat = a.get("category") or a.get("sug") or "עסקים"
        add("תל אביב-יפו", cat, name, address, phone, "עיריית תל אביב-יפו")
    if len(feats) < 2000: break
    offset += len(feats)
    if offset > 50000: break

# OpenStreetMap public/business listings with public phone numbers.
overpass = r'''[out:json][timeout:180];
area["ISO3166-1"="IL"][admin_level=2]->.il;
(
  nwr["phone"](area.il);
  nwr["contact:phone"](area.il);
);
out tags center;'''
try:
    req = Request("https://overpass-api.de/api/interpreter", data=overpass.encode(), headers={"User-Agent":"IsraelBusinessDirectory/1.0","Content-Type":"text/plain"})
    with urlopen(req, timeout=240) as r:
        data = json.loads(r.read().decode("utf-8","replace"))
    for el in data.get("elements", []):
        t = el.get("tags", {})
        # Keep public-facing organizations/businesses, not arbitrary residential objects.
        if not any(k in t for k in ("shop","office","amenity","craft","tourism","healthcare","leisure","public_transport","industrial","building","man_made")):
            continue
        name = t.get("name") or t.get("name:he") or t.get("name:en")
        phone = t.get("phone") or t.get("contact:phone")
        if not name or not phone: continue
        city = t.get("addr:city") or t.get("addr:town") or t.get("addr:village") or ""
        address = " ".join(x for x in [t.get("addr:street"), t.get("addr:housenumber")] if x)
        cat = t.get("shop") or t.get("amenity") or t.get("office") or t.get("craft") or t.get("tourism") or "עסק/שירות"
        add(city, cat, name, address, phone, "OpenStreetMap")
except Exception as e:
    print("OSM import warning:", e)

with open(OUT, "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f)
    w.writerow(["city","category","name","address","phone","source"])
    w.writerows(rows)

print("BUSINESS_ROWS=", len(rows))
print("OUTPUT=", OUT)
