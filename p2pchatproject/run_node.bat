@echo off
if "%~2"=="" (
  echo Usage: run_node.bat ^<port^> ^<username^>
  goto :eof
)
javac ChatNode.java
if errorlevel 1 goto :eof
java ChatNode %1 %2