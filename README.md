# FocusCoin

공부 시간을 코인으로 적립하고, 사용자가 직접 고른 앱의 이용 시간을 코인으로 관리하는 Android 집중 습관 앱입니다. 모든 앱 데이터는 기기에 저장하며 로그인, 서버 동기화, 광고, 결제 기능은 포함하지 않습니다.

## APK 다운로드

[최신 FocusCoin APK 다운로드](https://github.com/bocado-app/focuscoin1/releases/latest/download/FocusCoin.apk)

휴대폰에서 링크를 열어 APK를 내려받고 설치하세요. Android에서 설치를 차단하면 설정에서 브라우저의 '알 수 없는 앱 설치'를 허용해야 할 수 있습니다. 설치 후에는 이 권한을 다시 끌 수 있습니다.

## 기술 구성

- Kotlin, Jetpack Compose, Material 3
- MVVM + Navigation Compose
- DataStore: 코인 잔액, 설정, 타이머 복원 정보
- Room: 공부 세션, 코인 거래, 앱 잠금 설정
- 최소 SDK 26 (Android 8.0)
- Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17

## 실행

1. Android Studio에서 이 저장소의 `FocusCoin` 폴더를 엽니다.
2. JDK 17과 Android SDK 35를 설치합니다.
3. Gradle 동기화가 끝나면 에뮬레이터 또는 기기를 선택해 `app` 구성을 실행합니다.

명령줄에서는 프로젝트 폴더에서 실행합니다.

```bash
sh ./gradlew assembleDebug
```

Windows에서는 다음 명령을 사용합니다.

```powershell
.\gradlew.bat assembleDebug
```

첫 실행 시 Gradle 8.9 배포 파일을 다운로드합니다. 실행 환경에는 JDK 17이 필요합니다. APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

## 주요 파일 구조

```text
FocusCoin/
├── .github/workflows/       # 디버그 CI 및 APK GitHub Release
├── app/src/main/
│   ├── AndroidManifest.xml  # 앱 진입점, 앱 조회 범위, 선택 권한, 접근성 서비스
│   ├── java/com/focuscoin/app/
│   │   ├── FocusCoinUi.kt                 # Compose 화면, Navigation, 한국어 UI
│   │   ├── FocusCoinViewModel.kt          # 화면 상태와 MVVM 액션
│   │   ├── FocusCoinRepository.kt         # DataStore/Room, 타이머·코인 정산
│   │   ├── FocusCoinDatabase.kt           # Room 엔티티와 DAO
│   │   ├── AppLockAccessibilityService.kt # 선택 앱 패키지만 감지하고 잠금 오버레이 표시
│   │   └── UsageStatsAccess.kt            # 선택 앱 화면 사용 시간의 일시 조회
│   └── res/                 # 앱 아이콘과 접근성 서비스 설명
├── app/build.gradle.kts
└── README.md
```

## GitHub 빌드

`.github/workflows/android.yml`은 `main`/`master` 브랜치 push와 pull request마다 디버그 APK를 빌드합니다. 성공한 실행의 `FocusCoin-debug` 아티팩트를 Actions 페이지에서 받을 수 있습니다. 친구들에게 배포할 때는 아래 Release APK 링크를 사용하면 됩니다.

`v1.0.0` 형식의 태그를 push하면 `.github/workflows/release.yml`이 APK를 빌드해 GitHub Release에 `FocusCoin.apk` 이름으로 첨부합니다. 서명 키 비밀을 등록하지 않아도 설치 테스트용 APK를 받을 수 있습니다. 이 APK는 Actions 실행 환경에서 만들어진 debug 키로 서명되므로, 추후 업데이트를 같은 앱으로 설치할 때 서명이 달라질 수 있습니다. 키가 다르면 이전 앱을 제거한 뒤 설치해야 하고, 앱을 제거하면 기기에 저장된 데이터가 삭제됩니다.

지속적인 앱 업데이트를 배포하려면 저장소 설정의 **Settings → Secrets and variables → Actions**에 동일한 release keystore로 다음 저장소 비밀을 등록하세요. keystore 파일과 비밀번호는 저장소에 커밋하지 않습니다.

- `ANDROID_KEYSTORE_BASE64`: release keystore 파일의 Base64 문자열
- `ANDROID_KEYSTORE_PASSWORD`: keystore 비밀번호
- `ANDROID_KEY_ALIAS`: 서명 키 별칭
- `ANDROID_KEY_PASSWORD`: 서명 키 비밀번호

태그 버전은 APK 버전명과 버전 코드에 반영됩니다. Release APK는 아래 고정 주소에서 받을 수 있습니다.

```text
https://github.com/bocado-app/focuscoin1/releases/latest/download/FocusCoin.apk
```

저장소를 GitHub에 올릴 때는 GitHub에서 빈 저장소를 만든 뒤 아래 예시처럼 연결합니다.

```bash
git init
git add .
git commit -m "Build FocusCoin MVP"
git branch -M main
git remote add origin https://github.com/bocado-app/focuscoin1.git
git push -u origin main
```

## MVP 동작

- 첫 실행 목적 및 알림·사용 기록·접근성 권한의 선택적 안내
- 25분, 50분, 90분 및 직접 입력 집중 타이머
- 분 단위 코인 정산, 완주 보너스, 중도 종료 확인
- 타이머 상태를 DataStore에 저장해 앱 재실행 후 복원
- 실행 가능한 앱 목록과 선택 앱의 잠금·이용권 가격 설정
- 접근성 서비스는 `TYPE_WINDOW_STATE_CHANGED` 이벤트의 패키지명만 검사하며 노드 트리나 화면 내용을 가져오지 않음
- 잠금 안내, 코인 구매, 하루 1회 긴급 해제
- 오늘·주간 리포트, 주간 공부 막대 그래프, 규칙 기반 피드백
- 설정, 개인정보 안내, 로컬 데이터 초기화, 다크 모드

## 개인정보 범위

Room에는 사용자가 잠금 대상으로 고른 앱의 패키지명·앱 이름·이용권 설정, 공부 세션과 코인 거래 내역을 저장합니다. DataStore에는 코인 잔액, 설정, 타이머 복원 상태를 저장합니다. 사용 기록 접근을 허용하면 선택 앱의 이용 시간을 Android `UsageStatsManager`에서 조회해 리포트 화면에서만 보여주며 이 조회값은 저장하지 않습니다. 접근성 서비스는 화면 텍스트, 입력, 메시지, 비밀번호, 연락처, 캡처를 수집하지 않습니다.

## 수동 확인 방법

에뮬레이터 또는 Android 8.0 이상 기기에서 다음 순서로 확인합니다.

1. 앱을 설치하고 온보딩을 완료합니다. 권한을 거부한 상태에서도 홈과 타이머가 열리는지 확인합니다.
2. 1~2분 직접 타이머를 실행한 뒤 일시정지·재개하고, 앱을 닫았다 다시 열어 남은 시간이 이어지는지 확인합니다.
3. 1분 이상 공부를 종료하고 코인 잔액·거래 내역·오늘 리포트가 갱신되는지 확인합니다. 짧은 타이머를 완주해 보너스 적립도 확인합니다.
4. 잠금 앱 화면에서 테스트 앱을 선택하고 해제 시간·비용을 바꿉니다. 접근성 설정에서 FocusCoin 서비스를 켠 뒤 선택 앱을 열어 잠금 안내가 나타나는지 확인합니다.
5. 코인이 충분할 때 이용권을 확인 구매하고 앱이 설정한 시간 동안 열리는지 확인합니다. 코인이 부족한 상태와 긴급 해제 1회 제한도 확인합니다.
6. 리포트의 오늘/이번 주 전환, 주간 막대, 앱 구매 합계와 설정의 코인 비율·완주 보너스·데이터 초기화를 확인합니다.

Android와 제조사별 접근성/사용 기록 설정 화면은 표시 방식이 다를 수 있습니다. 접근성 허용을 거부해도 타이머, 코인, 리포트 기능은 사용할 수 있습니다.
