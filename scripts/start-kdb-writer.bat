@echo off
REM Start KDB Writer (Chronicle Queue -> kdb+) in a separate console window
cd /d C:\Project\oms-kdb
start "KDB Writer" cmd /k mvn exec:java -Dexec.mainClass="com.satya.oms.kdb.KdbWriterMain"
pause
