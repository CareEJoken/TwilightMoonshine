@echo off
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

call gradlew.bat build -x test
if errorlevel 1 (
    echo BUILD FAILED
    pause
    exit /b 1
)

echo DONE - copying to mods...
powershell -Command "$modsDir = (Get-ChildItem 'D:\Minecraft')[0].FullName + '\.minecraft\versions\1.21.1-NeoForge_21.1.243\mods'; Copy-Item -Path 'build\libs\twilightmoonshine-1.0.0.jar' -Destination $modsDir -Force"
if errorlevel 1 (
    echo COPY FAILED
    pause
    exit /b 1
)
echo ALL DONE
pause
