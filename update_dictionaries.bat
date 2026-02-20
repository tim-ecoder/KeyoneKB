@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM Update SymSpell dictionaries from hermitdave/FrequencyWords
REM Downloads full EN and RU frequency lists, filters and converts
REM to KeyoneKB format (word\tfrequency, scaled 45-255)
REM ============================================================

set "PROJECT_DIR=%~dp0"
set "DICT_DIR=%PROJECT_DIR%app\src\main\assets\dictionaries"
set "CURL=C:\Windows\System32\curl.exe"
set "ADB=C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe"

echo === Downloading FrequencyWords (EN + RU) ===

%CURL% -sL "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_full.txt" -o "%PROJECT_DIR%en_full_raw.txt"
if errorlevel 1 (echo ERROR: Failed to download EN dictionary & exit /b 1)
echo   EN downloaded.

%CURL% -sL "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ru/ru_full.txt" -o "%PROJECT_DIR%ru_full_raw.txt"
if errorlevel 1 (echo ERROR: Failed to download RU dictionary & exit /b 1)
echo   RU downloaded.

echo === Converting to KeyoneKB format ===

python "%PROJECT_DIR%convert_dict.py"
if errorlevel 1 (echo ERROR: Conversion failed & exit /b 1)

echo === Cleanup ===
del "%PROJECT_DIR%en_full_raw.txt" 2>nul
del "%PROJECT_DIR%ru_full_raw.txt" 2>nul

echo === Building APK ===
cd /d "%PROJECT_DIR%"
call gradlew.bat assembleDebug
if errorlevel 1 (echo ERROR: Build failed & exit /b 1)

echo === Installing on device ===
for /f "delims=" %%F in ('dir /b /s "%PROJECT_DIR%app\build\outputs\apk\debug\*.apk" 2^>nul') do (
    echo Installing %%F
    "%ADB%" install -r "%%F"
    if errorlevel 1 (echo WARNING: Install failed) else (echo   Installed OK.)
    goto :launch
)
echo WARNING: No APK found, skipping install.
:launch

echo === Launching app ===
"%ADB%" shell am start -n com.ai10.k12kb/.ActivityMain 2>nul

echo === Done ===
pause
