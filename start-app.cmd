@echo off
setlocal
set DB_HOST=localhost
set DB_PORT=5432
set DB_NAME=bookworm
set DB_USERNAME=bookworm
set DB_PASSWORD=5432
set JWT_SECRET=Ym9va3dvcm0tZGV2LWp3dC1zZWNyZXQtMzItYnl0ZXMhIQ==
mvn spring-boot:run > "%TEMP%\bookworm-app.log" 2>&1
endlocal
