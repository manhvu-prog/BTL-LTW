@echo off
chcp 65001 >nul
cd /d "%~dp0"
java -Xmx128m -Xms32m --module-path lib --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -cp "out;C:/Users/Mai/.m2/repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar" TestClient.Client
