@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b Server\*.java TestClient\*.java > sources.txt
javac -encoding UTF-8 --module-path lib --add-modules javafx.controls,javafx.fxml -sourcepath . -d out -cp "lib/*;C:/Users/Mai/.m2/repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar" @sources.txt
copy /Y resources/fx.css out/fx.css >nul
echo BUILD DONE
