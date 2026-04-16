# sy-car APK 빌드 가이드

## 방법 1: Android Studio 사용 (가장 쉬움, 추천)

### 1단계: Android Studio 설치
- https://developer.android.com/studio 에서 다운로드
- 설치 시 "Android SDK"도 함께 설치됨 (자동)

### 2단계: 프로젝트 열기
- Android Studio 실행
- **Open** 클릭
- 이 폴더(`sy-car-android`)를 선택
- Gradle Sync가 자동으로 시작됨 (처음 한번만 오래 걸림, 약 5~10분)

### 3단계: APK 빌드
- 상단 메뉴: **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
- 빌드 완료 후 우측 하단 **locate** 클릭하면 APK 파일 위치 열림
- 파일 위치: `app/build/outputs/apk/debug/app-debug.apk`

### 4단계: 휴대폰에 설치
- APK 파일을 카카오톡, 이메일, USB 등으로 휴대폰에 전송
- 휴대폰 설정 → 보안 → "출처를 알 수 없는 앱 설치" 허용
- APK 파일 탭하여 설치

---

## 방법 2: APK 없이 바로 사용 (서버 불필요)

APK 빌드가 어려우면 `app/src/main/assets/index.html` 파일을
휴대폰으로 전송한 후 **크롬 브라우저**로 열면 됩니다.

크롬 메뉴 → **"홈 화면에 추가"** 를 누르면 앱처럼 아이콘이 생깁니다.

---

## 프로젝트 구조

```
sy-car-android/
├── app/
│   ├── build.gradle          # 앱 빌드 설정
│   └── src/main/
│       ├── AndroidManifest.xml   # 앱 권한 및 설정
│       ├── assets/
│       │   └── index.html        # sy-car 웹앱 (전체 기능 포함)
│       ├── java/com/sycar/app/
│       │   └── MainActivity.java # WebView 액티비티
│       └── res/
│           ├── mipmap-*/         # 앱 아이콘 (5개 해상도)
│           ├── values/styles.xml # 앱 테마
│           └── xml/              # 네트워크 설정
├── build.gradle              # 루트 빌드 설정
├── settings.gradle           # Gradle 설정
├── gradle.properties         # Gradle 옵션
├── build-apk.bat             # Windows 빌드 스크립트
├── build-apk.sh              # Mac/Linux 빌드 스크립트
└── README.md                 # 이 파일
```
