@echo off
REM Start Aeron Listener (Aeron -> Chronicle Queue) in a separate console window
cd /d C:\Project\oms-kdb
start "Aeron Listener" cmd /k mvn exec:java -Dexec.mainClass="com.satya.oms.kdb.AeronListenerMain"
pause
