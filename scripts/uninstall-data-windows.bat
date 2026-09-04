@echo off
:: Removes all NoctuList data from this Windows machine.
:: Run this AFTER uninstalling NoctuList via Control Panel / Settings, or from the app itself.
::
:: What is deleted:
::   %USERPROFILE%\.noctulist\         — database, auth tokens, credentials, cache, exports
::   %APPDATA%\NoctuList\              — crash reports
::   Registry key (Java Preferences)  — HKCU\Software\JavaSoft\Prefs\com\eventmanager\app\noctulist
::
:: Usage: double-click this file or run it from a command prompt.

echo NoctuList data removal -- Windows
echo ==================================

set /p CONFIRM="This will permanently delete all NoctuList data. Continue? (Y/N): "
if /i "%CONFIRM%" NEQ "Y" (
    echo Aborted.
    exit /b 0
)

:: ── App data directory ────────────────────────────────────────────────────────
set "DATA_DIR=%USERPROFILE%\.noctulist"
if exist "%DATA_DIR%\" (
    echo Deleting %DATA_DIR% ...
    rmdir /s /q "%DATA_DIR%"
    echo   Done
) else (
    echo   (not found: %DATA_DIR%)
)

:: ── Crash reports ─────────────────────────────────────────────────────────────
set "CRASH_DIR=%APPDATA%\NoctuList"
if exist "%CRASH_DIR%\" (
    echo Deleting %CRASH_DIR% ...
    rmdir /s /q "%CRASH_DIR%"
    echo   Done
) else (
    echo   (not found: %CRASH_DIR%)
)

:: ── Java Preferences (Registry) ───────────────────────────────────────────────
set "REG_KEY=HKCU\Software\JavaSoft\Prefs\com\eventmanager\app\noctulist"
echo Removing Java Preferences registry key ...
reg query "%REG_KEY%" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    reg delete "%REG_KEY%" /f >nul 2>&1
    echo   Done
) else (
    echo   (registry key not found)
)

echo.
echo All NoctuList data has been removed.
pause
