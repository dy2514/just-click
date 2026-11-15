# Just, Click

Pleos Connect에서 운전석과 동승석의 현재 좌석 위치를 조회하고 이름을 붙여 저장하는 좌석 프리셋 앱입니다.

## 실행 방법과 테스트 환경

1. Pleos Connect v2.1.0 에뮬레이터(Android API 34, 가로 2560 × 1440 px, 160 dpi)를 실행합니다.
2. 아래 릴리스 APK를 설치하고 앱 목록에서 **Just, Click**을 엽니다. 차량 좌석 정보 조회에 필요한 권한을 허용합니다.
3. 운전석 또는 동승석을 선택하고 현재 위치를 새로고침합니다. 위치가 조회되면 이름을 붙여 저장하고 목록에서 확인합니다.

소스로 실행하려면 Android Studio에서 저장소 루트를 열고 Gradle JDK를 17로 설정합니다. Android SDK Platform 35를 설치하고 Gradle Sync를 실행한 뒤 Pleos AVD에서 `app` 실행 구성을 선택합니다. 최초 동기화에는 인터넷 연결이 필요합니다. Windows 디버그 빌드는 `.\gradlew.bat :app:assembleDebug` 명령을 사용합니다.

## 구현 범위

현재 좌석 점유 조회, 좌석 선택, 현재 전후·등받이 위치 표시 및 기기 내 저장, 저장 항목 조회·이름 변경·삭제, 프리셋별 이동 capability 확인이 가능하다. 위치는 새로고침 시 갱신되며 도 단위의 각도가 아닌 SDK 원시값이다. 해당 API를 지원하지 않거나 권한이 없는 차량에서는 값을 저장할 수 없다. 저장 대상 중 지원되는 값만 기록하며, 저장 위치는 앱 내부 저장소다.

**아직 좌석 제어 기능은 구현되지 않았다.** 공식 Vehicle SDK 2.3.2 소스의 `Seat.kt`에 따르면 제어 권한은 `pleos.car.permission.CONTROL_CAR_SEATS`이며, `moveSeat*`는 절대 위치값이 아니라 `MoveControl`의 전진·후진·정지 명령을 받는다. Capability 조회는 `CAR_SEATS` 권한을 사용한다. 에뮬레이터는 운전석 전후와 등받이 이동 capability를 지원한다고 응답했지만, 이것이 안전한 자동 제어와 실제 위치 변경을 보장하지는 않는다.

저장 위치에 도달할 때까지의 안전한 제어, 명령 방향과 위치값의 관계, 통신 장애·앱 종료 시 정지 보장, 이동 완료 확인, 주차 상태 확인 및 제어 권한을 검증해야 프리셋 적용·되돌리기 기능을 제공할 수 있다. 현재 앱은 제어 API를 호출하지 않는다. 점유 자동 적용, 도착 제안, Gleo, TTS도 미구현이다.

## 제출용 APK

[PAL223_Just,Click_release.apk 다운로드](apk/PAL223_Just%2CClick_release.apk)

- 경로 `apk/PAL223_Just,Click_release.apk`
- SHA-256 `22136b80b756413fb89b1a936b7c45a8f0406cb7decedb9128f1ef167da0ba5c`
