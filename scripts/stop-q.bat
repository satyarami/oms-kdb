@echo off
REM Stop q process (OMS-q window)

echo Stopping q (OMS-q)...
taskkill /FI "WINDOWTITLE eq OMS-q*" /T /F >nul 2>&1

if %ERRORLEVEL%==0 (
    echo q stopped.
) else (
    echo q was not running.
)
