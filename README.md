# 드라이브 노트 (Drive Note)

**드라이브 노트** — 차량의 핸들, 가속·브레이크 페달, 방향지시등 데이터를 분석해 운전 습관을 진단하고 개선 방법을 코칭하는 Pleos Connect 앱

## 주요 화면

| 화면 | 설명 |
|---|---|
| **홈** | 최근 주행 경로를 지도에 표시하고, 문제 구간을 분류 색으로 강조합니다. 떠 있는 정보 바와 "개선할 점" 패널에서 소요 시간·거리·개선 항목을 바로 확인할 수 있습니다. |
| **리포트** | 월간 캘린더에서 날짜와 주행을 고르면 경로 지도(번호 핀)와 문제 목록을 보여 줍니다. 항목을 펼치면 "내 운전 vs 권장" 비교 시각화(가속 페달 그래프, 정지선 접근, 차로 변경, 횡단보도 앞 속도, 회전 궤적)가 나오고, 주행 전체의 쏠림 요약(G-G 다이어그램)도 볼 수 있습니다. |
| **개선 추이** | 항목별 최근 4주 발생 횟수를 꺾은선으로 보여 주고, 4주 전과 비교해 얼마나 줄었는지 알려 줍니다. |
| **설정** | 주행 중 음성 코칭 켜기/끄기, 리포트 공유, 집중 개선 항목을 선택합니다. |

주행 중(기어 D)에는 운전에 방해되지 않도록 조작 요소 없이 기록 상태와 최근 코칭만 표시합니다.

## 실행 방법

1. Android Studio에서 이 프로젝트를 엽니다.
2. 프로젝트 루트의 `local.properties`에 네이버 클라우드 Maps 키를 추가합니다. (이 파일은 Git에 올라가지 않습니다)
   ```properties
   NAVER_MAP_KEY_ID=YOUR_NCP_CLIENT_ID
   NAVER_MAP_KEY=YOUR_NCP_CLIENT_SECRET
   ```
   - `NAVER_MAP_KEY_ID`: 지도 SDK(Dynamic Map)용 Client ID. 빌드 시 매니페스트에 주입됩니다.
   - `NAVER_MAP_KEY`: `tools/make_route_map.py`로 내장 지도 이미지(Static Map)를 만들 때만 사용하며, 앱(APK)에는 포함되지 않습니다.
   - 키가 없거나 지도 인증에 실패하면 앱은 내장 이미지 지도(또는 대체 지도)로 자동 전환됩니다.
3. Pleos Connect Emulator v2.1.0을 실행합니다.
4. 앱을 설치합니다.
   ```bash
   ./gradlew installDebug
   ```
5. 차속(Android Car API) 권한을 부여합니다.
   ```bash
   adb shell pm grant --user 10 com.example.drivenote android.car.permission.CAR_SPEED
   ```

## 테스트 환경

- Pleos Connect Emulator v2.1.0 (AAOS, 2560×1440)
- Pleos Vehicle SDK 2.3.2
- minSdk 28
- Kotlin + Jetpack Compose

## 데모 방법

화면 왼쪽 아래의 **데모 P / 데모 D** 버튼으로 기어 상태를 전환할 수 있습니다.

- **데모 D**: 주행 중 화면(기록 상태, 최근 코칭)
- **데모 P**: 주행 종료 → 리포트 화면으로 이동

> 참고: 현재 분석 화면(리포트, 개선 추이 등)은 실제와 유사하게 생성한 주행 데이터로 동작합니다.

## APK

[Releases](../../releases/latest)에서 `drive-note.apk`를 내려받을 수 있습니다.
