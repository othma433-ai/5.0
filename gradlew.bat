@echo off
setlocal
set DIR=%~dp0
set JAR=%DIR%gradle\wrapper\gradle-wrapper.jar
if exist "%JAR%" goto run
powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='https://services.gradle.org/distributions/gradle-8.9-wrapper.jar'; $p='%JAR%'; New-Item -ItemType Directory -Force -Path (Split-Path $p) | Out-Null; Invoke-WebRequest -UseBasicParsing $u -OutFile $p; $h=(Get-FileHash $p -Algorithm SHA256).Hash.ToLower(); if($h -ne '498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17'){Remove-Item $p -Force; throw 'Gradle wrapper checksum mismatch'}"
if errorlevel 1 exit /b 1
:run
if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)
"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
