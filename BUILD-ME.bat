@echo off
echo Building Block Rotation mod (first time takes a few minutes)...
call gradlew.bat build
if errorlevel 1 (
  echo.
  echo BUILD FAILED - copy the red error text above and send it to Claude.
  pause
  exit /b 1
)
del /q build\libs\*-sources.jar 2>nul
echo.
echo Done! Your mod is blockrotation-1.0.0.jar in the folder that just opened.
explorer build\libs
pause
