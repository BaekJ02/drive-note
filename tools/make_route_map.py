#!/usr/bin/env python3
"""
최근 주행 경로(MockData.lastDriveRoute)를 감싸는 지도 타일을 내려받아 PNG 한 장으로 이어 붙인다.
결과: app/src/main/res/drawable-nodpi/route_map.png

외부 패키지 없이 파이썬 표준 라이브러리(zlib, urllib)만 사용한다.

사용법 (프로젝트 루트에서):
    python3 tools/make_route_map.py            # 내려받아 이미지 생성
    python3 tools/make_route_map.py --dry-run  # 영역·타일 범위만 출력

환경 변수:
    TILE_URL   타일 주소 템플릿 (기본: CARTO Positron). {s} {z} {x} {y} 사용 가능.
               예) API 키가 필요한 서버면 키를 포함한 주소를 직접 지정
    MAP_ZOOM   줌 레벨 (기본 14, 타일이 너무 많으면 13)

실패 처리:
    HTTP 200 이미지가 아니거나, 위치가 다른 타일들이 같은 이미지(차단·"API KEY REQUIRED" 안내)이면
    이미지를 만들지 않고 종료 코드 1로 끝난다. 앱은 이미지가 없으면 Canvas 대체 지도를 쓴다.

출력되는 타일 범위(MAP_ZOOM, TILE_X0, TILE_Y0, COLS, ROWS)는 MockData.kt의 routeMap 상수와 같아야 한다.
"""
import math
import os
import re
import struct
import sys
import time
import urllib.request
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOCK = os.path.join(ROOT, "app/src/main/java/com/example/drivenote/MockData.kt")
OUT = os.path.join(ROOT, "app/src/main/res/drawable-nodpi/route_map.png")
UA = "DriveNote/1.0 (com.example.drivenote)"
TILE_URL = os.environ.get("TILE_URL", "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png")
SUBDOMAINS = "abcd"
MARGIN = 0.18  # 경로 영역 바깥 여유 (가로·세로 비율)
MAX_TILES = 64
ASPECT = 2.2  # 홈 지도 카드 가로:세로 비율 (약 760x360dp)


def read_route():
    src = open(MOCK, encoding="utf-8").read()
    block = src[src.index("val lastDriveRoute"):]
    block = block[:block.index(")\n\n")]
    pts = [(float(a), float(b)) for a, b in re.findall(r"GeoPt\(([\d.]+),\s*([\d.]+)\)", block)]
    if len(pts) < 2:
        sys.exit("경로 좌표를 찾지 못했어요: " + MOCK)
    return pts


def tile_xy(lat, lon, z):
    """위경도 → 타일 좌표(실수). Web Mercator."""
    n = 2 ** z
    x = (lon + 180.0) / 360.0 * n
    r = math.radians(lat)
    y = (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.pi) / 2.0 * n
    return x, y


def area(pts, z):
    xs, ys = zip(*(tile_xy(lat, lon, z) for lat, lon in pts))
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    w = (max(xs) - min(xs)) * (1 + 2 * MARGIN)
    h = (max(ys) - min(ys)) * (1 + 2 * MARGIN)
    # 지도 카드 비율(가로:세로)에 맞게 짧은 쪽을 넓혀서, 카드를 꽉 채워도 경로가 잘리지 않게 함
    if w / h < ASPECT:
        w = h * ASPECT
    else:
        h = w / ASPECT
    x0, x1 = math.floor(cx - w / 2), math.floor(cx + w / 2)
    y0, y1 = math.floor(cy - h / 2), math.floor(cy + h / 2)
    return x0, y0, x1 - x0 + 1, y1 - y0 + 1


# ---------------- PNG 디코드/인코드 (표준 라이브러리만)

def png_decode(data):
    """8비트 PNG(RGB/RGBA/팔레트/회색) → (너비, 높이, RGB 바이트 행 리스트)"""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("PNG 아님")
    pos, idat, plte = 8, b"", None
    while pos < len(data):
        ln, typ = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif typ == b"PLTE":
            plte = body
        elif typ == b"IDAT":
            idat += body
        pos += 12 + ln
    if depth != 8 or interlace:
        raise ValueError(f"지원하지 않는 PNG (depth={depth}, interlace={interlace})")
    bpp = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = w * bpp
    rows, prev, i = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[i]; line = bytearray(raw[i + 1:i + 1 + stride]); i += 1 + stride
        for k in range(stride):
            a = line[k - bpp] if k >= bpp else 0
            b = prev[k]
            c = prev[k - bpp] if k >= bpp else 0
            if f == 1: line[k] = (line[k] + a) & 255
            elif f == 2: line[k] = (line[k] + b) & 255
            elif f == 3: line[k] = (line[k] + ((a + b) >> 1)) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[k] = (line[k] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        if ctype == 2:
            rgb = bytes(line)
        elif ctype == 6:
            rgb = bytes(v for j in range(w) for v in line[j * 4:j * 4 + 3])
        elif ctype == 3:
            rgb = b"".join(plte[idx * 3:idx * 3 + 3] for idx in line)
        elif ctype == 0:
            rgb = bytes(v for g in line for v in (g, g, g))
        else:  # 4: 회색+알파
            rgb = bytes(v for j in range(w) for v in (line[j * 2],) * 3)
        rows.append(rgb)
    return w, h, rows


def png_encode(w, h, rows):
    def chunk(t, b):
        return struct.pack(">I", len(b)) + t + b + struct.pack(">I", zlib.crc32(t + b) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + r for r in rows)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


# ---------------- 메인

def main():
    pts = read_route()
    z = int(os.environ.get("MAP_ZOOM", "14"))
    x0, y0, cols, rows = area(pts, z)
    if cols * rows > MAX_TILES and "MAP_ZOOM" not in os.environ:
        z = 13
        x0, y0, cols, rows = area(pts, z)
    print(f"줌 {z}, 타일 x {x0}..{x0 + cols - 1}, y {y0}..{y0 + rows - 1} ({cols}x{rows}={cols * rows}장)")
    print(f"MockData 상수: routeMapZoom = {z}, routeMapTileX0 = {x0}, routeMapTileY0 = {y0}, "
          f"routeMapCols = {cols}, routeMapRows = {rows}")
    if "--dry-run" in sys.argv:
        return 0

    tiles, n = {}, 0
    for ty in range(y0, y0 + rows):
        for tx in range(x0, x0 + cols):
            url = TILE_URL.format(s=SUBDOMAINS[n % 4], z=z, x=tx, y=ty); n += 1
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            try:
                with urllib.request.urlopen(req, timeout=15) as r:
                    if r.status != 200 or not r.headers.get("Content-Type", "").startswith("image"):
                        print(f"실패: HTTP {r.status} {url}"); return 1
                    tiles[(tx, ty)] = r.read()
            except Exception as e:  # 403, 네트워크 오류 등
                print(f"실패: {e} {url}"); return 1
            time.sleep(0.1)  # 서버 부담 줄이기

    unique = len(set(tiles.values()))
    if len(tiles) > 1 and unique <= len(tiles) // 2:
        print(f"실패: 위치가 다른 타일 {len(tiles)}장 중 서로 다른 이미지가 {unique}장뿐이에요. "
              "차단 또는 'API KEY REQUIRED' 안내 이미지로 판단해 지도 이미지를 만들지 않아요.")
        return 1

    out_rows = []
    for ty in range(y0, y0 + rows):
        decoded = [png_decode(tiles[(tx, ty)]) for tx in range(x0, x0 + cols)]
        th = decoded[0][1]
        for r in range(th):
            out_rows.append(b"".join(d[2][r] for d in decoded))
    tw = sum(d[0] for d in decoded)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    tmp = OUT + ".tmp"
    with open(tmp, "wb") as f:
        f.write(png_encode(tw, len(out_rows), out_rows))
    os.replace(tmp, OUT)
    print(f"완료: {OUT} ({tw}x{len(out_rows)}px)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
