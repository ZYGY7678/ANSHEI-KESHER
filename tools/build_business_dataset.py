#!/usr/bin/env python3
import csv,json,os,re,time
from urllib.request import Request,urlopen
from urllib.parse import urlencode
OUT="app/src/main/assets/businesses.csv"; os.makedirs(os.path.dirname(OUT),exist_ok=True)
def get(url,p=None,t=120):
    if p:url+=("&" if "?" in url else "?")+urlencode(p)
    for n in range(3):
        try:
            with urlopen(Request(url,headers={"User-Agent":"IsraelBusinessDirectory/2.0"}),timeout=t) as r:return json.loads(r.read().decode("utf-8","replace"))
        except Exception:
            if n==2:raise
            time.sleep(2)
rows=[];seen=set()
def add(city,cat,name,address,phone,source):
    name=str(name or "").strip(); phone=str(phone or "").strip(); digits=re.sub(r"[^0-9]","",phone)
    if not name or len(digits)<6:return
    key=(name.lower(),digits)
    if key in seen:return
    seen.add(key); rows.append([str(city or "").strip(),str(cat or "").strip(),name,str(address or "").strip(),phone,source])
def fetch(rid,limit=250000):
    out=[]; off=0
    while off<limit:
        b=get("https://data.gov.il/api/3/action/datastore_search",{"resource_id":rid,"limit":5000,"offset":off},180).get("result",{}).get("records",[])
        out+=b
        if len(b)<5000:break
        off+=len(b)
    print("FETCH",rid,len(out)); return out
def pick(r,words):
    low={str(k).lower():v for k,v in r.items()}
    for w in words:
        if w.lower() in low and low[w.lower()] not in ("",None):return low[w.lower()]
    for k,v in low.items():
        if v not in ("",None) and any(w.lower() in k for w in words):return v
    return ""
# Known nationwide/public sources
for r in fetch("5555edc5-532d-46b5-8415-01a54d5a5b73"):add(r.get("city"),r.get("category"),r.get("name"),r.get("address"),r.get("phone"),"משרד התיירות")
for r in fetch("7d4c61e2-2416-453e-8efb-bd02ec89db35"):add("באר שבע",r.get("תאור רישיון"),r.get("שם עסק")," ".join(x for x in [r.get("שם רחוב"),str(r.get("בית") or "")] if x),r.get("טלפון בעסק"),"עיריית באר שבע")
for rid,cat,name,address,phone,city in [
("bb68386a-a331-4bbc-b668-bba2766d517d","sug_mosah","shem_mosah","ktovet","telephone","yishuv"),
("42e73a60-7acc-4c5d-b4ec-b0e468a73c51","isuk","shem_esek","ktovet","telephone","yishuv")]:
    for r in fetch(rid):add(r.get(city),r.get(cat) or "עסק",r.get(name),r.get(address),r.get(phone),"משרד התחבורה")
for r in fetch("3f06e2f2-e2ad-41ac-9665-37d0625537f2"):add(r.get("ezor"),"בית ספר לנהיגה",r.get("shem_beit_sefer"),r.get("ktovet"),r.get("telefon"),"משרד התחבורה")
# Automatically discover additional public business datastores on data.gov.il
qs=["עסקים טלפון","רישוי עסקים טלפון","בתי עסק טלפון","מסחר טלפון","business phone"]
rids=[]
for q in qs:
    try:
        for p in get("https://data.gov.il/api/3/action/package_search",{"q":q,"rows":50},120).get("result",{}).get("results",[]):
            title=str(p.get("title") or ""); hay=(title+" "+str(p.get("notes") or "")).lower()
            if not any(x in hay for x in ["עסק","רישוי","מסחר","business","trade","license"]):continue
            for res in p.get("resources",[]):
                rid=res.get("id")
                if rid and res.get("datastore_active") and rid not in rids:rids.append(rid)
    except Exception as e:print("DISCOVERY",e)
for rid in rids[:100]:
    try:
        sample=get("https://data.gov.il/api/3/action/datastore_search",{"resource_id":rid,"limit":2},60).get("result",{}).get("records",[])
        if not sample:continue
        keys=[str(k).lower() for k in sample[0]]
        pk=[k for k in keys if any(x in k for x in ["phone","tel","טלפון","telephone"])]
        nk=[k for k in keys if any(x in k for x in ["business","עסק","שם עסק","company","חברה","מסחר"])]
        if not pk or not nk:continue
        for r in fetch(rid):
            add(pick(r,["city","עיר","ישוב","יישוב","yishuv","town"]),pick(r,["category","קטגוריה","סוג עסק","industry","תחום"]),pick(r,nk+["name","שם עסק"]),pick(r,["address","כתובת","street","רחוב","ktovet"]),pick(r,pk+["phone","טלפון","telephone"]),"data.gov.il")
    except Exception as e:print("DATASET",rid,e)
# Tel Aviv public business layer
try:
    off=0
    while True:
        fs=get("https://gisn.tel-aviv.gov.il/ArcGIS/rest/services/WM/IView2WMTest/MapServer/925/query",{"where":"1=1","outFields":"*","returnGeometry":"false","resultOffset":off,"resultRecordCount":2000,"f":"json"},180).get("features",[])
        if not fs:break
        for f in fs:
            a=f.get("attributes",{});add("תל אביב-יפו",a.get("category") or a.get("sug"),a.get("shem") or a.get("name") or a.get("business_name"),a.get("address") or a.get("ktovet") or a.get("street"),a.get("ms_telephone") or a.get("phone") or a.get("telephone"),"עיריית תל אביב-יפו")
        if len(fs)<2000:break
        off+=len(fs)
        if off>100000:break
except Exception as e:print("TELAVIV",e)
# OSM public businesses with phone numbers
q='''[out:json][timeout:300];area["ISO3166-1"="IL"][admin_level=2]->.il;(nwr["phone"]["name"](area.il);nwr["contact:phone"]["name"](area.il););out tags center;'''
try:
    with urlopen(Request("https://overpass-api.de/api/interpreter",data=q.encode(),headers={"User-Agent":"IsraelBusinessDirectory/2.0","Content-Type":"text/plain"}),timeout=360) as r:d=json.loads(r.read().decode("utf-8","replace"))
    for e in d.get("elements",[]):
        t=e.get("tags",{})
        if not any(k in t for k in ("shop","office","amenity","craft","tourism","healthcare","leisure","public_transport","industrial","building","man_made")):continue
        add(t.get("addr:city") or t.get("addr:town") or t.get("addr:village"),t.get("shop") or t.get("amenity") or t.get("office") or t.get("craft") or t.get("tourism") or "עסק/שירות",t.get("name") or t.get("name:he") or t.get("name:en")," ".join(x for x in [t.get("addr:street"),t.get("addr:housenumber")] if x),t.get("phone") or t.get("contact:phone"),"OpenStreetMap")
except Exception as e:print("OSM",e)
with open(OUT,"w",newline="",encoding="utf-8") as f:
    w=csv.writer(f);w.writerow(["city","category","name","address","phone","source"]);w.writerows(rows)
print("BUSINESS_ROWS=",len(rows));print("TARGET_100K_REACHED=",len(rows)>=100000)
