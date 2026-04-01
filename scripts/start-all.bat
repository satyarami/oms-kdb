@echo off
REM Start all oms-kdb processes in separate console windows
REM Prerequisites: Aeron Media Driver running, kdb+ listening on port 5000

echo Starting all oms-kdb processes...
echo.

REM Start Aeron Listener (Aeron -> Chronicle Queue)
echo [1/3] Starting Aeron Listener...
call "%~dp0start-aeron-listener.bat"
timeout /t 3 /nobreak

REM Start KDB Writer (Chronicle Queue -> kdb+)
echo [2/3] Starting KDB Writer...
call "%~dp0start-kdb-writer.bat"
timeout /t 2 /nobreak

REM Start Chronicle Queue Monitor (stdout)
echo [3/3] Starting Chronicle Queue Monitor...
call "%~dp0start-queue-monitor.bat"

echo.
echo All oms-kdb processes have been started in separate console windows.
echo Close each window individually to stop the processes.
