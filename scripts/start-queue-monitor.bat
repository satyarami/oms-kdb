@echo off
REM Start Chronicle Queue Monitor (decode SBE -> stdout) in a separate console window
cd /d C:\Project\oms-kdb
start "Queue Monitor" cmd /k mvn exec:java -Dexec.mainClass="com.satya.oms.kdb.ChronicleQueueMonitor"
pause
