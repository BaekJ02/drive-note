#!/usr/bin/env python3
"""
네이버 클라우드 Maps Static Map(raster) API로 최근 주행 경로(MockData.lastDriveRoute) 전체가
들어가는 지도 이미지를 받아 저장한다.
결과: app/src/main/res/drawable-nodpi/route_map.png  (.gitignore 대상, APK에는 포함됨)

외부 패키지 없이 파이썬 표준 라이브러리만 사용한다.

사용법 (프로젝트 루트에서):
    export NAVER_MAP_KEY_ID=...   # 네이버 클라우드 콘솔의 Client ID
    export NAVER_MAP_KEY=...      # Client Secret
    python3 tools/make_route_map.py            # 지도 받아서 PNG 생성 + MockData 상수 갱신
    python3 tools/make_route_map.py --dry-run  # 계산한 center/level만 출력 (요청 안 함)

API 키는 환경 변수에서만 읽고, 화면·파일 어디에도 출력하지 않는다.

좌표계:
    네이버 Static Map은 Web Mercator(EPSG:3857), 256px 타일 기준 줌 = level 이다.
    scale=2면 같은 영역을 2배 해상도로 받는다 (이미지 크기 = w*2 x h*2).
    즉 이미지 픽셀 = (월드 픽셀(level) - 월드 픽셀(center)) * scale + 이미지 크기 / 2.
    앱(RouteMap.kt)의 imagePixel()이 같은 식을 쓴다.

실패 처리:
    두 엔드포인트 모두 이미지가 아닌 응답(에러 JSON 등)을 주면 상태 코드와 에러 메시지를 출력하고
    PNG를 만들지 않고 종료 코드 1로 끝난다. 앱은 이미지가 없으면 Canvas 대체 지도를 쓴다.
"""
import json
import math
import os
import re
import struct
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOCK = os.path.join(ROOT, "app/src/main/java/com/example/drivenote/MockData.kt")
OUT = os.path.join(ROOT, "app/src/main/res/drawable-nodpi/route_map.png")
ENDPOINTS = [
    "https://maps.apigw.ntruss.com/map-static/v2/raster",
    "https://naveropenapi.apigw.ntruss.com/map-static/v2/raster",
]
W, H = 1024, 466   # 요청 크기 (API 최대 1024). 홈 지도 카드 비율 약 2.2:1
SCALE = 2          # 고해상도 → 실제 이미지 2048x932
MARGIN = 0.12      # 경로 바깥 여유 (가로·세로 각 방향 비율)
MAX_LEVEL = 18


def read_route():
    src = open(MOCK, encoding="utf-8").read()
    block = src[src.index("val lastDriveRoute"):]
    block = block[:block.index(")\n\n")]
    pts = [(float(a), float(b)) for a, b in re.findall(r"GeoPt\(([\d.]+),\s*([\d.]+)\)", block)]
    if len(pts) < 2:
        sys.exit("경로 좌표를 찾지 못했어요: " + MOCK)
    return pts


def world(lat, lon):
    """위경도 → level 0의 월드 픽셀 (Web Mercator, 256px)"""
    x = (lon + 180.0) / 360.0 * 256.0
    s = math.sin(math.radians(lat))
    y = (0.5 - math.log((1 + s) / (1 - s)) / (4 * math.pi)) * 256.0
    return x, y


def unworld(x, y):
    lon = x / 256.0 * 360.0 - 180.0
    lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / 256.0))))
    return lat, lon


def view(pts):
    """경로 영역의 중심(위경도)과, 여유를 두고 W x H 안에 들어가는 가장 큰 level"""
    xs, ys = zip(*(world(lat, lon) for lat, lon in pts))
    lat, lon = unworld((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
    lat, lon = round(lat, 6), round(lon, 6)  # 요청·MockData에 같은 값을 쓰도록 반올림 후 사용
    dx, dy = max(xs) - min(xs), max(ys) - min(ys)
    level = 0
    for z in range(MAX_LEVEL, -1, -1):
        if dx * 2 ** z <= W * (1 - 2 * MARGIN) and dy * 2 ** z <= H * (1 - 2 * MARGIN):
            level = z
            break
    return lat, lon, level


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", data[16:24])


def request(url, key_id, key):
    """(이미지 바이트, None) 또는 (None, 에러 설명). 에러 설명에 키 값은 들어가지 않음."""
    req = urllib.request.Request(url, headers={
        "x-ncp-apigw-api-key-id": key_id,
        "x-ncp-apigw-api-key": key,
    })
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            status, ctype, body = r.status, r.headers.get("Content-Type", ""), r.read()
    except urllib.error.HTTPError as e:
        status, ctype, body = e.code, e.headers.get("Content-Type", ""), e.read()
    except Exception as e:  # 네트워크 오류 등
        return None, f"요청 실패: {type(e).__name__}: {e}"
    if status == 200 and ctype.startswith("image") and png_size(body):
        return body, None
    msg = body[:500].decode("utf-8", "replace")
    try:  # 네이버 API 에러 형식: {"error": {"errorCode", "message", "details"}} 등
        j = json.loads(msg)
        err = j.get("error", j)
        msg = " / ".join(str(err[k]) for k in ("errorCode", "message", "details") if k in err) or msg
    except ValueError:
        pass
    return None, f"HTTP {status} ({ctype or 'Content-Type 없음'}): {msg.strip()}"


def update_mock(lat, lon, level, img_w, img_h):
    src = open(MOCK, encoding="utf-8").read()
    block = (
        "    // 내장 지도 이미지(res/drawable-nodpi/route_map.png): 네이버 Static Map 요청값\n"
        "    // tools/make_route_map.py가 자동으로 갱신함. Web Mercator, 256px 타일 기준 줌 = level,\n"
        "    // 이미지 크기 = 요청 크기(w, h) x scale, 이미지 중심 = center\n"
        f"    const val routeMapCenterLat = {lat}\n"
        f"    const val routeMapCenterLon = {lon}\n"
        f"    const val routeMapLevel = {level}\n"
        f"    const val routeMapScale = {SCALE}\n"
        f"    const val routeMapWidth = {img_w}  // px (= {W} x {SCALE})\n"
        f"    const val routeMapHeight = {img_h} // px (= {H} x {SCALE})\n"
    )
    new, n = re.subn(r"    // 내장 지도 이미지.*?(?:    const val routeMap\w+ = [^\n]*\n)+", block, src, flags=re.S)
    if n != 1:
        print("주의: MockData.kt에서 지도 상수 블록을 찾지 못해 갱신하지 않았어요. 아래 값을 직접 넣어주세요.")
        print(block)
        return
    with open(MOCK, "w", encoding="utf-8") as f:
        f.write(new)
    print("MockData.kt 지도 상수 갱신")


def main():
    lat, lon, level = view(read_route())
    print(f"center {lat},{lon}  level {level}  크기 {W}x{H} scale {SCALE}")
    if "--dry-run" in sys.argv:
        return 0

    key_id, key = os.environ.get("NAVER_MAP_KEY_ID"), os.environ.get("NAVER_MAP_KEY")
    if not key_id or not key:
        print("실패: 환경 변수 NAVER_MAP_KEY_ID, NAVER_MAP_KEY를 설정해주세요.")
        return 1

    query = urllib.parse.urlencode({
        "w": W, "h": H, "center": f"{lon},{lat}", "level": level,
        "scale": SCALE, "format": "png", "maptype": "basic",
    })
    data = None
    for ep in ENDPOINTS:
        data, err = request(f"{ep}?{query}", key_id, key)
        if data:
            print(f"성공: {ep}")
            break
        print(f"실패: {ep}\n  {err}")
    if not data:
        print("지도 이미지를 만들지 않았어요.")
        return 1

    img_w, img_h = png_size(data)
    if (img_w, img_h) != (W * SCALE, H * SCALE):
        print(f"주의: 이미지 크기 {img_w}x{img_h}가 예상({W * SCALE}x{H * SCALE})과 달라요. 받은 크기로 저장해요.")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    tmp = OUT + ".tmp"
    with open(tmp, "wb") as f:
        f.write(data)
    os.replace(tmp, OUT)
    print(f"완료: {OUT} ({img_w}x{img_h}px)")
    update_mock(lat, lon, level, img_w, img_h)
    return 0


if __name__ == "__main__":
    sys.exit(main())
