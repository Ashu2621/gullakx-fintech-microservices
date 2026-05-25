# 🎯 GullakX - COMPLETE STEP-BY-STEP INSTRUCTIONS TO RUN LOCALLY

**Total Setup Time: ~15 minutes**

---

## WHAT IS GULLAKX?

GullakX is a **production-grade fintech backend** built with:
- **Java 21 + Spring Boot 3.2.5** (Latest LTS versions)
- **PostgreSQL** (Two databases for different services)
- **Redis** (Caching & distributed locking)
- **Apache Kafka** (Event streaming)
- **Elasticsearch + Kibana** (Logging)
- **Prometheus + Grafana** (Monitoring)

It demonstrates real-world microservices architecture used by companies like **PhonePe, Razorpay, and Stripe**.

---

# 🚀 STEP-BY-STEP SETUP

## PHASE 1: INSTALL REQUIRED SOFTWARE (10 minutes)

### Step 1A: Install Docker Desktop ✅

**What it is:** Container runtime (think: lightweight virtual machines for databases, message queues, etc.)

**macOS/Windows:**
1. Go to: https://www.docker.com/products/docker-desktop
2. Download and install
3. Open Docker Desktop application (important!)
4. Wait for "Docker Desktop is running" message

**Linux:**
```bash
sudo apt-get update
sudo apt-get install docker.io docker-compose
sudo usermod -aG docker $USER
# Log out and log back in
```

**Verify installation:**
```bash
docker --version
docker ps
# Both should work without errors
```

---

### Step 1B: Install Java 21 ✅

**What it is:** Programming language runtime (required to run Spring Boot)

1. Go to: https://adoptium.net/
2. Download: **Eclipse Temurin 21** (Latest LTS)
3. Install (accept default options)

**Verify installation:**
```bash
java -version
# Should show: openjdk version "21.x.x"
```

**Troubleshooting:**
- If command not found after installation:
  - **macOS**: Restart terminal or run: `source ~/.bashrc`
  - **Windows**: Restart terminal or restart computer
  - **Linux**: Add to PATH or restart terminal

---

### Step 1C: Install Maven 3.9+ ✅

**What it is:** Build tool (downloads dependencies, compiles Java code)

1. Go to: https://maven.apache.org/download.cgi
2. Download: **apache-maven-3.9.x-bin.zip** (latest version)
3. Extract to a folder (e.g., `/opt/maven` on Mac/Linux, `C:\maven` on Windows)
4. Add Maven `bin/` folder to PATH

**Add to PATH (macOS/Linux):**
```bash
# Edit ~/.bashrc or ~/.zshrc
export PATH="/opt/maven/bin:$PATH"

# Reload
source ~/.bashrc  # or ~/.zshrc
```

**Add to PATH (Windows):**
1. Right-click "This PC" → Properties
2. Click "Advanced system settings"
3. Click "Environment Variables"
4. Under "System variables", click "New"
5. Variable name: `MAVEN_HOME`
6. Variable value: `C:\maven` (or wherever you extracted it)
7. Click "New" again
8. Variable name: `PATH`
9. Variable value: `%MAVEN_HOME%\bin;` (add to existing)
10. Click OK and restart Command Prompt

**Verify installation:**
```bash
mvn -version
# Should show: Apache Maven 3.9.x
```

---

## PHASE 2: EXTRACT PROJECT (1 minute)

### Step 2A: Extract ZIP file

```bash
# Navigate to Downloads folder (or wherever you saved the ZIP)
cd ~/Downloads

# Extract the ZIP
unzip gullakx-complete.zip

# Navigate into project
cd gullakx-project

# Verify structure - you should see:
ls
# Output should include:
# pom.xml  docker-compose.yml  START_HERE.md  SETUP_GUIDE.md
# api-gateway/  auth-service/  wallet-service/  common/  monitoring/
```

---

## PHASE 3: START INFRASTRUCTURE (3 minutes)

### Step 3A: Start all Docker services

All databases, caches, message queues, and monitoring tools start automatically:

```bash
# Make sure you're in gullakx-project/ directory
cd gullakx-project

# Start all Docker services
docker-compose up -d

# Monitor startup (should take 30-60 seconds)
docker-compose ps

# Wait until ALL services show "Up" or "Up (healthy)"
# You should see:
# NAME                          STATUS
# gullakx-postgres-auth         Up (healthy)
# gullakx-postgres-wallet       Up (healthy)
# gullakx-redis                 Up (healthy)
# gullakx-kafka                 Up (healthy)
# gullakx-elasticsearch         Up (healthy)
# gullakx-kibana                Up
# gullakx-prometheus            Up
# gullakx-grafana               Up
# gullakx-kafka-ui              Up
# gullakx-jaeger                Up
# gullakx-alertmanager          Up
```

**⏳ Wait 30-60 seconds for all services to be healthy**

**Quick verification:**
```bash
# These should all work without errors:
curl http://localhost:9200 2>/dev/null | head -c 30 && echo " <- Elasticsearch ✓"
redis-cli -h localhost -a gullakx_redis_secret ping 2>/dev/null && echo "PONG <- Redis ✓"
curl http://localhost:5601 2>/dev/null | head -c 30 && echo " <- Kibana ✓"
```

---

## PHASE 4: BUILD PROJECT (4 minutes)

### Step 4A: Compile and package

```bash
# Make sure you're in gullakx-project/ directory
cd gullakx-project

# Build entire project (downloads dependencies, compiles, packages)
mvn clean install -DskipTests -q

# This will:
# 1. Download dependencies from Maven Central (5-10 sec on first run)
# 2. Compile all Java code
# 3. Package JAR files
# 4. Show "BUILD SUCCESS" at the end

# Expected output:
# [INFO] Building auth-service 1.0.0
# [INFO] Building jar: /path/to/auth-service/target/auth-service-1.0.0.jar
# [INFO] BUILD SUCCESS
```

**If build fails:**
```bash
# Clear Maven cache and retry
rm -rf ~/.m2/repository
mvn clean install -DskipTests -q
```

---

## PHASE 5: START MICROSERVICES (3 minutes)

### Step 5A: Open FOUR separate terminal windows

**Important:** You must use **4 separate terminal windows/tabs** because each service runs in the foreground.

**Terminal 1 - API Gateway (Port 8080)**
```bash
cd /path/to/gullakx-project/api-gateway
mvn spring-boot:run

# Wait for output like:
# Tomcat started on port 8080 (http)
# Started GatewayApplication in X.XXX seconds
```

**Terminal 2 - Auth Service (Port 8081)**
```bash
cd /path/to/gullakx-project/auth-service
mvn spring-boot:run

# Wait for output like:
# Tomcat started on port 8081 (http)
# Started AuthServiceApplication in X.XXX seconds
```

**Terminal 3 - Wallet Service (Port 8082)**
```bash
cd /path/to/gullakx-project/wallet-service
mvn spring-boot:run

# Wait for output like:
# Tomcat started on port 8082 (http)
# Started WalletServiceApplication in X.XXX seconds
```

**Terminal 4 - Watch Logs (Optional but helpful)**
```bash
cd /path/to/gullakx-project
docker-compose logs -f
```

**✅ All three services should start successfully within 60 seconds!**

---

## 🎉 YOUR SYSTEM IS NOW RUNNING!

### Quick Health Check

Open **Terminal 5** and run:

```bash
# Check if all services are responding
curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}

curl http://localhost:8081/actuator/health
# Should return: {"status":"UP"}

curl http://localhost:8082/actuator/health
# Should return: {"status":"UP"}
```

---

# 📊 ACCESS DASHBOARDS

### Grafana (Live Metrics) 📈
```
URL: http://localhost:3000
Username: admin
Password: admin
```
- Click "Dashboards" to see live metrics
- Watch throughput, latency, error rates update in real-time

### Kibana (Logs) 📝
```
URL: http://localhost:5601
```
- Click "Discover"
- Search for logs (e.g., `service: "wallet-service"`)
- Watch transactions being logged

### Prometheus (Raw Metrics) 📊
```
URL: http://localhost:9090
```
- Query: `http_server_requests_seconds_bucket`
- See raw metrics

### Kafka UI (Message Queue) 📬
```
URL: http://localhost:8090
```
- View topics: `wallet.transfer`, `notifications`, `fraud.events`
- Watch messages flow through the system

### Jaeger (Request Tracing) 🔍
```
URL: http://localhost:16686
```
- Select service: "auth-service"
- Trace individual requests

---

# 🧪 TEST YOUR FIRST API CALL

Open **Terminal 5** and run:

### Test 1: Register a User

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Arjun Sharma",
    "email": "arjun@example.com",
    "phoneNumber": "9876543210",
    "password": "SecurePass@123"
  }' | jq .
```

**Expected Response:**
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440001"
  },
  "timestamp": 1234567890
}
```

**Save these values for next tests:**
```bash
export USER_ID="550e8400-e29b-41d4-a716-446655440000"
export ACCESS_TOKEN="eyJhbGciOiJIUzUxMiJ9..."
```

### Test 2: Check Your Wallet

```bash
curl -X GET http://localhost:8080/api/v1/wallets/me \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-User-Id: $USER_ID" | jq .
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "walletId": "wallet-uuid-here",
    "balance": 0.00,
    "currency": "INR"
  }
}
```

### Test 3: Watch in Grafana

1. Open http://localhost:3000
2. Login with admin/admin
3. Search for "GullakX" in dashboards
4. Watch metrics update in real-time as you make API calls!

---

# 🛑 STOP EVERYTHING

### Method 1: Graceful shutdown

In each microservice terminal (1, 2, 3), press:
```
Ctrl+C
```

Then stop Docker:
```bash
cd /path/to/gullakx-project
docker-compose down
```

### Method 2: Force stop

```bash
# Stop all Java processes
killall java

# Stop Docker
docker-compose down
```

### Complete cleanup (delete all data):
```bash
# Delete all Docker volumes (clean slate)
docker-compose down -v

# Clear Maven cache (optional)
rm -rf ~/.m2/repository
```

---

# ❌ TROUBLESHOOTING

### Problem: "Connection refused" or "Failed to connect to localhost:8080"

**Cause:** Services not started yet

**Solution:**
1. Check Terminal 1, 2, 3 - do you see "Tomcat started on port XXXX"?
2. If not, wait more time (can take 60 seconds)
3. If still not showing, check for errors in the terminal

---

### Problem: "Docker daemon not running"

**Cause:** Docker Desktop not open

**Solution:**
- **macOS/Windows**: Open Docker Desktop application (look in Applications)
- **Linux**: Run `sudo systemctl start docker`

---

### Problem: "Port 8080 already in use"

**Cause:** Another application is using the port

**Solution:**
```bash
# Find process using port 8080
# macOS/Linux:
lsof -i :8080
kill -9 <PID>

# Windows PowerShell:
Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess | Stop-Process -Force
```

---

### Problem: "mvn: command not found"

**Cause:** Maven not in PATH

**Solution:**
1. Restart terminal completely
2. If still not working, add Maven to PATH manually (see Phase 1C above)

---

### Problem: "java.net.ConnectException: Connection refused"

**Cause:** Microservice trying to connect to database/Redis before Docker services are ready

**Solution:**
```bash
# Check Docker services are healthy
docker-compose ps

# All should show "Up" or "Up (healthy)"
# If not, wait 30 more seconds

# Restart the failing service
cd wallet-service
mvn spring-boot:run
```

---

### Problem: "BUILD FAILURE" during Maven build

**Cause:** Corrupted Maven cache or network issue

**Solution:**
```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Try build again
mvn clean install -DskipTests -q
```

---

### Problem: Can't connect to Grafana (http://localhost:3000)

**Cause:** Grafana container not running

**Solution:**
```bash
# Check Docker services
docker-compose ps

# Look for "gullakx-grafana"
# If not running, restart
docker-compose up -d grafana
```

---

# 📋 FINAL CHECKLIST

Before you start, verify you have:

- [ ] Docker Desktop installed and running
- [ ] Java 21 installed (`java -version` works)
- [ ] Maven 3.9+ installed (`mvn -version` works)
- [ ] ZIP file extracted to `gullakx-project/` folder
- [ ] You're ready to open 4 terminal windows

---

# 🎯 QUICK REFERENCE

| Component | URL | Login | Status |
|-----------|-----|-------|--------|
| **API Gateway** | http://localhost:8080 | None | Main entry point |
| **Grafana** | http://localhost:3000 | admin/admin | Dashboards |
| **Kibana** | http://localhost:5601 | None | Logs |
| **Prometheus** | http://localhost:9090 | None | Metrics |
| **Kafka UI** | http://localhost:8090 | None | Message queue |
| **Jaeger** | http://localhost:16686 | None | Tracing |
| **PostgreSQL Auth** | localhost:5432 | gullakx/gullakx_secret | Database 1 |
| **PostgreSQL Wallet** | localhost:5433 | gullakx/gullakx_secret | Database 2 |
| **Redis** | localhost:6379 | gullakx_redis_secret | Cache |
| **Kafka** | localhost:9092 | None | Message queue |

---

# 💡 TIPS & TRICKS

### See Real-time Logs
```bash
# Watch all Docker services
docker-compose logs -f

# Watch single service
docker-compose logs -f auth-service

# Watch microservice output
# (look at Terminal 1, 2, 3 output)
```

### Save API Token for Reuse
```bash
# Save to file for later use
echo "export ACCESS_TOKEN='your-token-here'" >> ~/.bashrc
source ~/.bashrc

# Now you can use $ACCESS_TOKEN in any curl command
```

### Monitor System Resources
```bash
# Watch Docker stats
docker stats

# Watch system CPU/memory
top  # macOS/Linux
```

### Restart Everything
```bash
# Stop all services (press Ctrl+C in each terminal)
# Then:
docker-compose down -v
docker-compose up -d
mvn clean install -DskipTests
# Start services again in 3 terminals
```

---

# ✅ SUCCESS INDICATORS

After following all steps, you should see:

1. ✅ All 3 microservices running with "Tomcat started on port XXXX"
2. ✅ Can access http://localhost:8080/actuator/health (returns {"status":"UP"})
3. ✅ Can register user via API call and get JWT token
4. ✅ Can view Grafana dashboard
5. ✅ Can see logs in Kibana
6. ✅ Can see Kafka topics in Kafka UI

---

# 🎓 NEXT STEPS

After successful setup:

1. **Explore API**: Visit http://localhost:8080/swagger-ui.html
2. **Study Code**: Start with `AuthServiceApplication.java`
3. **Run Tests**: Follow `TEST_SCENARIOS.md` in the project
4. **Monitor**: Watch Grafana dashboard while making API calls
5. **Check Logs**: Search in Kibana for specific services
6. **Learn Architecture**: Read `ARCHITECTURE.md` and `SETUP_GUIDE.md`

---

# 📞 GETTING HELP

If you get stuck:

1. **Check logs**: Look at the terminal output where services are running
2. **View Docker logs**: `docker-compose logs service-name`
3. **Verify ports**: `lsof -i :8080` (macOS/Linux)
4. **Read SETUP_GUIDE.md**: More detailed troubleshooting there
5. **Check Kafka UI**: http://localhost:8090 to see message flow

---

# 🎉 YOU'RE READY!

You now have a **complete production-grade fintech backend** running locally.

**This demonstrates real enterprise architecture used by:**
- ✅ PhonePe (Indian unicorn)
- ✅ Razorpay (Payment platform)
- ✅ Stripe (Global payments)
- ✅ Amazon Pay
- ✅ Google Pay

**Start with Step 1 above and follow through to Phase 5.** Total time: ~15 minutes.

**Good luck! 🚀**
