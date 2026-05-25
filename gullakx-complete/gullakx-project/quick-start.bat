@echo off
REM GullakX Quick Start Script for Windows

echo.
echo ╔════════════════════════════════════════════════════════╗
echo ║          GullakX - Quick Start Script                  ║
echo ║    Digital Wallet ^& Digital Gold Platform              ║
echo ╚════════════════════════════════════════════════════════╝
echo.

REM Check Docker
echo [1/6] Checking Docker...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ✗ Docker not found. Please install Docker Desktop.
    echo   Download: https://www.docker.com/products/docker-desktop
    exit /b 1
)
echo ✓ Docker found

REM Check Java
echo [2/6] Checking Java 21...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ✗ Java not found. Please install Java 21.
    echo   Download: https://adoptium.net/
    exit /b 1
)
echo ✓ Java found

REM Check Maven
echo [3/6] Checking Maven...
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ✗ Maven not found. Please install Maven 3.9+
    echo   Download: https://maven.apache.org/download.cgi
    exit /b 1
)
echo ✓ Maven found

echo.
echo [4/6] Starting Docker services...
docker-compose up -d
echo ✓ Docker services started (wait 30 seconds for health checks)
timeout /t 30

echo.
echo [5/6] Building project...
mvn clean install -DskipTests -q
echo ✓ Project built successfully

echo.
echo [6/6] Services ready!
echo.
echo ╔════════════════════════════════════════════════════════╗
echo ║  Now run these commands in SEPARATE terminals:         ║
echo ║                                                        ║
echo ║  Terminal 1 - API Gateway:                            ║
echo ║    cd api-gateway && mvn spring-boot:run              ║
echo ║                                                        ║
echo ║  Terminal 2 - Auth Service:                           ║
echo ║    cd auth-service && mvn spring-boot:run             ║
echo ║                                                        ║
echo ║  Terminal 3 - Wallet Service:                         ║
echo ║    cd wallet-service && mvn spring-boot:run           ║
echo ║                                                        ║
echo ║  Then access:                                          ║
echo ║  - Grafana:  http://localhost:3000 (admin/admin)     ║
echo ║  - Kibana:   http://localhost:5601                   ║
echo ║  - API:      http://localhost:8080/actuator/health   ║
echo ╚════════════════════════════════════════════════════════╝
echo.
pause
