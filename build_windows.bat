@echo off
setlocal
cd /d "%~dp0"
if not exist build\classes mkdir build\classes
javac --release 17 -encoding UTF-8 -d build\classes src\*.java
if errorlevel 1 (echo Java compilation failed. Install JDK 17 or later. & exit /b 1)
> build\MANIFEST.MF echo Main-Class: Launcher
>> build\MANIFEST.MF echo.
jar --create --file GeoSketch.jar --manifest build\MANIFEST.MF -C build\classes .
if errorlevel 1 exit /b 1
echo Built: GeoSketch.jar
