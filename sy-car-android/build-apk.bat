@echo off
echo ============================================
echo   sy-car APK 빌드 스크립트
echo ============================================
echo.

REM Check Java
java -version >nul 2>&1
if errorlevel 1 (
    echo [오류] Java가 설치되어 있지 않습니다.
    echo https://adoptium.net/ 에서 JDK 17 이상을 설치해주세요.
    pause
    exit /b 1
)

echo [1/3] Gradle Wrapper 다운로드 중...
if not exist gradlew.bat (
    echo Gradle wrapper가 없습니다. 아래 명령을 실행해주세요:
    echo   gradle wrapper --gradle-version 8.2
    echo 또는 Android Studio로 이 프로젝트를 열어주세요.
    pause
    exit /b 1
)

echo [2/3] APK 빌드 중... (처음에는 몇 분 소요)
call gradlew.bat assembleDebug

if errorlevel 1 (
    echo.
    echo [오류] 빌드 실패!
    echo Android Studio에서 이 프로젝트 폴더를 열어서 빌드해주세요.
    pause
    exit /b 1
)

echo.
echo [3/3] 완료!
echo ============================================
echo   APK 파일 위치:
echo   app\build\outputs\apk\debug\app-debug.apk
echo ============================================
echo.

REM Copy to project root for easy access
copy app\build\outputs\apk\debug\app-debug.apk sy-car.apk >nul 2>&1
if not errorlevel 1 (
    echo   루트에도 복사됨: sy-car.apk
)

echo.
pause
