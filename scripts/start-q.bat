@echo off
REM Start q with schema.q on port 5000 (if not already running)

REM Check if port 5000 is already in use
netstat -ano | findstr ":5000 " | findstr "LISTENING" >nul 2>&1
if %ERRORLEVEL%==0 (
    echo q is already running on port 5000, skipping start.
    goto :eof
)

echo Starting q (schema.q, port 5000)...
start "OMS-q" cmd /k "cd /d C:\Project\oms-kdb\src\main\resources && q schema.q -p 5000"
timeout /t 2 /nobreak >nul
echo q started on port 5000.
