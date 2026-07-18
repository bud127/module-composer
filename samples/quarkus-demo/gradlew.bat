@echo off
setlocal
if "%GRADLE_VERSION%"=="" set GRADLE_VERSION=8.14.3
set CACHE_DIR=%USERPROFILE%\.gradle\module-composer-bootstrap
set DIST_DIR=%CACHE_DIR%\gradle-%GRADLE_VERSION%
set ZIP_FILE=%CACHE_DIR%\gradle-%GRADLE_VERSION%-bin.zip
set DIST_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip

if not exist "%DIST_DIR%\bin\gradle.bat" (
  if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%ZIP_FILE%'; Expand-Archive -Force '%ZIP_FILE%' '%CACHE_DIR%'"
)

call "%DIST_DIR%\bin\gradle.bat" %*
endlocal
