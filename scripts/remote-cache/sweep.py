#!/usr/bin/env python3
"""THE REMOTE CACHE SWEEP (1.10.2026). One cadastral municipality, read whole from the state's four
services and kept as files, so ARKOD Layer can be answered when the state is down, on a new install too.

    python3 scripts/sweep.py 334723 KUKLJICA 1354 [stage ...]

Stages, in order (each resumes: a file already kept is never asked again):
    sheets   KAT: every posjedovni list, by number, from OSS's public search (1, 2, 3 ... until 60 empty)
    records  KAT: every parcel's record (parcel-info): uses, holders, the land-book link
    folios   ZK:  every land-book folio a record links to, current (lr-unit) and with history
    zoning   WMS: the k.o.'s extent, asked point by point of the zoning layer (GetFeatureInfo)
    tiles    WMS: the ARKOD layer's own tiles, z14 to z18, over the k.o., as the app asks for them
    outlines WFS: every parcel outline, in pages over the k.o.; the WFS fails in spells, so a page that
             fails is asked again later (ORA-01000 waited out)

Layout under ko/<reg>-<NAME>/ is written so an app can compute a file's path from what it would have
asked the state; see README.md. Asked as the apps ask: their User-Agent, no Origin. A few at a time.
"""
import json, math, os, sys, time, urllib.parse, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor

UA = 'ARKOD-Layer-cache/1 (+https://github.com/markoboskoauroville/mantra_arkod)'
OSS = 'https://oss.uredjenazemlja.hr/oss/public'
WMS = 'https://api.uredjenazemlja.hr/services/inspire/cp_wms/wms'
WFS = 'https://api.uredjenazemlja.hr/services/inspire/cp/wfs'
HALF = 20037508.342789244

REG, NAME, MUNI = sys.argv[1], sys.argv[2], sys.argv[3]
STAGES = sys.argv[4:] or ['sheets', 'records', 'folios', 'zoning', 'tiles', 'outlines']
ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'ko', f'{REG}-{NAME}')
WORKERS = 6


def log(*a):
    print(time.strftime('%H:%M:%S'), *a, flush=True)


def ask(url, body=None, tries=6, binary=False):
    """The state's answer: (status, bytes). Network trouble and 5xx are asked again, with a wait."""
    for n in range(1, tries + 1):
        req = urllib.request.Request(url, data=body, headers={'User-Agent': UA, 'Accept': '*/*',
                                     **({'Content-Type': 'application/json'} if body else {})})
        try:
            with urllib.request.urlopen(req, timeout=90) as r:
                return r.status, r.read()
        except urllib.error.HTTPError as e:
            data = e.read()
            if e.code < 500 or n == tries:
                return e.code, data
        except Exception as e:  # timeouts, resets
            if n == tries:
                return 0, str(e).encode()
        time.sleep(min(60, 3 * n * n))


def path(*parts):
    p = os.path.join(ROOT, *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


def keep(p, data):
    tmp = p + '.part'
    with open(tmp, 'wb') as f:
        f.write(data)
    os.replace(tmp, p)


def parallel(fn, items):
    done = 0
    with ThreadPoolExecutor(WORKERS) as ex:
        for _ in ex.map(fn, items):
            done += 1
            if done % 250 == 0:
                log(f'  {done}/{len(items)}')


# --- KAT: possession sheets ------------------------------------------------------------------------

def sheets():
    """In batches of 60 numbers, asked a few at a time; it stops after 60 empty numbers in a row."""
    def one(n):
        p = path('kat', 'possession-sheet', f'{n}.json')
        for _ in range(5):
            if os.path.exists(p):
                return bool(json.load(open(p)))
            body = json.dumps({'cadMunicipalityId': int(MUNI), 'possessionSheetNumber': str(n)}).encode()
            code, data = ask(f'{OSS}/cad/search-parcels', body)
            if code == 200:
                keep(p, data)
            else:
                log(f'sheet {n}: {code} {data[:120]!r}')
                time.sleep(10)
        return None
    start, found, empty_run = 1, 0, 0
    with ThreadPoolExecutor(WORKERS) as ex:
        while empty_run < 60:
            batch = list(range(start, start + 60))
            for n, has in zip(batch, ex.map(one, batch)):
                if has:
                    found, empty_run = found + 1, 0
                elif has is False:
                    empty_run += 1
            start += 60
            if start % 600 == 1:
                log(f'sheets: {start - 1} asked, {found} with parcels')
    log(f'sheets: done, {found} sheets with parcels (last asked {start - 1})')


def parcel_ids():
    ids = {}
    d = os.path.join(ROOT, 'kat', 'possession-sheet')
    for f in os.listdir(d):
        for p in json.load(open(os.path.join(d, f))):
            ids[p['parcelId']] = p.get('parcelNumber')
    return ids


# --- KAT: records ----------------------------------------------------------------------------------

def records():
    ids = sorted(parcel_ids())
    log(f'records: {len(ids)} parcels')

    def one(i):
        p = path('kat', 'parcel-info', f'{i}.json')
        if os.path.exists(p):
            return
        code, data = ask(f'{OSS}/cad/parcel-info?parcelId={i}')
        if code == 200:
            keep(p, data)
        else:
            log(f'record {i}: {code} {data[:100]!r}')
    parallel(one, ids)
    # number → id, the index an app asks first (OSS's own search answers the same question)
    index = {}
    for i in ids:
        p = path('kat', 'parcel-info', f'{i}.json')
        if os.path.exists(p):
            index[json.load(open(p))['parcelNumber']] = i
    keep(path('kat', 'index.json'), json.dumps(index, ensure_ascii=False, sort_keys=True).encode())
    log(f'records: {len(index)} kept, index written')


# --- ZK: folios ------------------------------------------------------------------------------------

def folios():
    units = {}
    d = os.path.join(ROOT, 'kat', 'parcel-info')
    for f in os.listdir(d):
        for u in json.load(open(os.path.join(d, f))).get('lrUnitsFromParcelLinks') or []:
            units[(u['mainBookId'], u['lrUnitNumber'])] = u['lrUnitId']
    log(f'folios: {len(units)} land-book folios linked')

    def one(k):
        (book, unit), uid = k
        p = path('zk', 'lr-unit', f'{book}-{unit}.json')
        if not os.path.exists(p):
            q = urllib.parse.urlencode({'lrUnitNumber': unit, 'mainBookId': book, 'historicalOverview': 'false'})
            code, data = ask(f'{OSS}/lr/lr-unit?{q}')
            if code == 200:
                keep(p, data)
            else:
                log(f'folio {book}-{unit}: {code} {data[:100]!r}')
        h = path('zk', 'history', f'{uid}.json')
        if not os.path.exists(h):
            code, data = ask(f'{OSS}/lr-units/for-ldb-extract?lrUnitId={uid}&historical=1')
            if code == 200:
                keep(h, data)
            else:
                log(f'history {uid}: {code} {data[:100]!r}')
    parallel(one, sorted(units.items()))


# --- WMS: the k.o.'s extent ------------------------------------------------------------------------

def info(lat, lon, layer):
    d = 0.0002
    q = (f'SERVICE=WMS&VERSION=1.3.0&REQUEST=GetFeatureInfo&LAYERS={layer}&QUERY_LAYERS={layer}&STYLES='
         f'&CRS=EPSG:4326&BBOX={lat - d},{lon - d},{lat + d},{lon + d}&WIDTH=101&HEIGHT=101&I=50&J=50'
         f'&FEATURE_COUNT=1&INFO_FORMAT=text/plain')
    return ask(f'{WMS}?{q}')


def zoning():
    p = path('wms', 'zoning-grid.json')
    grid = json.load(open(p)) if os.path.exists(p) else {}
    # A generous box round southern Ugljan, sampled every 0.002° (about 220 m by 160 m).
    lat0, lat1, lon0, lon1, step = 43.97, 44.09, 15.15, 15.34, 0.002
    pts = [(round(lat0 + i * step, 4), round(lon0 + j * step, 4))
           for i in range(int((lat1 - lat0) / step) + 1) for j in range(int((lon1 - lon0) / step) + 1)]
    todo = [pt for pt in pts if f'{pt[0]},{pt[1]}' not in grid]
    log(f'zoning: {len(pts)} points, {len(todo)} to ask')

    def one(pt):
        code, data = info(pt[0], pt[1], 'cp:CP.CadastralZoning')
        if code != 200:
            return
        t = data.decode('utf-8', 'replace')
        label = next((l.split(' = ', 1)[1].strip() for l in t.splitlines() if l.strip().startswith('LABEL =')), '')
        grid[f'{pt[0]},{pt[1]}'] = label
    for k in range(0, len(todo), 500):
        parallel(one, todo[k:k + 500])
        keep(p, json.dumps(grid, sort_keys=True).encode())
    inside = [k for k, v in grid.items() if v.startswith(REG)]
    log(f'zoning: {len(inside)} points inside {REG}')


def tiles_over():
    grid = json.load(open(path('wms', 'zoning-grid.json')))
    inside = [tuple(map(float, k.split(','))) for k, v in grid.items() if v.startswith(REG)]
    out = set()
    for z in range(14, 19):
        n = 1 << z
        for lat, lon in inside:
            # each sample stands for its 0.002° cell: the tiles of the cell's four corners
            for dl in (-0.001, 0.001):
                for dn in (-0.001, 0.001):
                    la, lo = lat + dl, lon + dn
                    x = int((lo + 180) / 360 * n)
                    y = int((1 - math.asinh(math.tan(math.radians(la))) / math.pi) / 2 * n)
                    out.add((z, x, y))
    return sorted(out)


def tile_url(z, x, y):
    size = 2 * HALF / (1 << z)
    b = (-HALF + x * size, HALF - (y + 1) * size, -HALF + (x + 1) * size, HALF - y * size)
    return (f'{WMS}?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=cp:CP.CadastralParcel&STYLES='
            f'&FORMAT=image/png&TRANSPARENT=true&CRS=EPSG:3857&WIDTH=512&HEIGHT=512'
            f'&FORMAT_OPTIONS=dpi:180&BBOX={b[0]},{b[1]},{b[2]},{b[3]}')


def tiles():
    want = tiles_over()
    log(f'tiles: {len(want)} WMS tiles z14..z18')

    def one(t):
        z, x, y = t
        p = path('wms', str(z), str(x), f'{y}.png')
        if os.path.exists(p):
            return
        code, data = ask(tile_url(z, x, y))
        if code == 200 and data[:4] == b'\x89PNG':
            keep(p, data)
        else:
            log(f'tile {z}/{x}/{y}: {code} {data[:100]!r}')
    parallel(one, want)


# --- WFS: outlines ---------------------------------------------------------------------------------

def outlines():
    grid = json.load(open(path('wms', 'zoning-grid.json')))
    inside = [tuple(map(float, k.split(','))) for k, v in grid.items() if v.startswith(REG)]
    s, n = min(p[0] for p in inside) - 0.002, max(p[0] for p in inside) + 0.002
    w, e = min(p[1] for p in inside) - 0.002, max(p[1] for p in inside) + 0.002
    # boxes of 0.01° (about 1.1 km by 0.8 km), each read in pages of 500
    boxes = [(round(a, 3), round(b, 3)) for a in frange(s, n, 0.01) for b in frange(w, e, 0.01)]
    log(f'outlines: {len(boxes)} boxes')
    pending = list(boxes)
    rounds = 0
    while pending and rounds < 40:
        rounds += 1
        failed = []
        for (a, b) in pending:
            p = path('wfs', 'box', f'{a}_{b}.json')
            if os.path.exists(p):
                continue
            feats, start, ok = [], 0, True
            while True:
                q = (f'SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature&OUTPUTFORMAT=application/json'
                     f'&SRSNAME=urn:ogc:def:crs:EPSG::4326&TYPENAMES=cp:CadastralParcel'
                     f'&BBOX={a},{b},{a + 0.01},{b + 0.01},urn:ogc:def:crs:EPSG::4326&COUNT=500&STARTINDEX={start}')
                code, data = ask(f'{WFS}?{q}', tries=2)
                if code != 200:
                    ok = False
                    reason = data.decode('utf-8', 'replace')
                    reason = reason[reason.find('ORA-'):][:60] if 'ORA-' in reason else reason[:60]
                    log(f'outlines {a},{b}: {code} {reason}')
                    break
                page = json.loads(data).get('features') or []
                feats += page
                if len(page) < 500:
                    break
                start += 500
            if ok:
                keep(p, json.dumps({'type': 'FeatureCollection', 'features': feats}).encode())
            else:
                failed.append((a, b))
        pending = failed
        if pending:
            log(f'outlines: {len(pending)} boxes still waiting (round {rounds}); asking again in 10 minutes')
            time.sleep(600)
    # one file per parcel reference, the app's own question (nationalCadastralReference)
    by_ref = {}
    d = os.path.join(ROOT, 'wfs', 'box')
    for f in os.listdir(d) if os.path.isdir(d) else []:
        for ft in json.load(open(os.path.join(d, f)))['features']:
            ref = ft.get('properties', {}).get('nationalCadastralReference', '')
            if ref.startswith(REG + '-'):
                by_ref[ref] = ft
    if by_ref:
        keep(path('wfs', 'outlines.geojson'), json.dumps({'type': 'FeatureCollection',
             'features': [by_ref[k] for k in sorted(by_ref)]}, ensure_ascii=False).encode())
    log(f'outlines: {len(by_ref)} parcels of {REG}; {len(pending)} boxes never answered')


def frange(a, b, s):
    x = a
    while x < b:
        yield x
        x += s


if __name__ == '__main__':
    log(f'sweep {REG} {NAME} (OSS municipality {MUNI}): {", ".join(STAGES)} → {ROOT}')
    for st in STAGES:
        t = time.time()
        globals()[st]()
        log(f'{st}: {time.time() - t:.0f} s')
    log('sweep: done')
