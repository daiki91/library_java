@echo off
rem GesBiblio 2 - compile puis lance l'application (Windows)
cd /d "%~dp0"
if not exist bin mkdir bin
echo Compilation...
javac -encoding UTF-8 -cp mysql-connector-java-5.1.41-bin.jar -d bin src\gesbiblio\db\*.java src\gesbiblio\metier\*.java src\gesbiblio\ui\*.java
if errorlevel 1 (
  echo La compilation a echoue.
  pause
  exit /b 1
)
echo Lancement...
java -cp "bin;mysql-connector-java-5.1.41-bin.jar" gesbiblio.ui.App
