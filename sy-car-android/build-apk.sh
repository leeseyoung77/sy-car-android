#!/bin/bash
echo "============================================"
echo "  sy-car APK 빌드 스크립트"
echo "============================================"
echo ""

# Check Java
if ! command -v java &> /dev/null; then
    echo "[오류] Java가 설치되어 있지 않습니다."
    echo "https://adoptium.net/ 에서 JDK 17 이상을 설치해주세요."
    exit 1
fi

echo "[1/3] Gradle Wrapper 확인 중..."
if [ ! -f "./gradlew" ]; then
    echo "Gradle wrapper가 없습니다."
    echo "Android Studio로 이 프로젝트를 열어주세요."
    exit 1
fi
chmod +x ./gradlew

echo "[2/3] APK 빌드 중... (처음에는 몇 분 소요)"
./gradlew assembleDebug

if [ $? -ne 0 ]; then
    echo ""
    echo "[오류] 빌드 실패!"
    echo "Android Studio에서 이 프로젝트 폴더를 열어서 빌드해주세요."
    exit 1
fi

echo ""
echo "[3/3] 완료!"
echo "============================================"
echo "  APK 파일 위치:"
echo "  app/build/outputs/apk/debug/app-debug.apk"
echo "============================================"

cp app/build/outputs/apk/debug/app-debug.apk ./sy-car.apk 2>/dev/null
if [ $? -eq 0 ]; then
    echo "  루트에도 복사됨: ./sy-car.apk"
fi
echo ""
