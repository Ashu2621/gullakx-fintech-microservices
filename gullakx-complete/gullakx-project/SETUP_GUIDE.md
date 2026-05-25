# 🚀 GullakX - Complete Local Setup Guide

A production-grade fintech backend platform. This guide will help you set up and run the entire system locally.

---

## 📋 Prerequisites (10 minutes)

### 1. Check System Requirements

**Minimum Requirements:**
- RAM: 8GB (16GB recommended)
- Disk Space: 10GB free
- OS: macOS, Linux, or Windows with WSL2

**Install Required Software:**

#### Docker Desktop (Required)
- **macOS/Windows**: https://www.docker.com/products/docker-desktop
- **Linux**: https://docs.docker.com/engine/install/

Verify installation:
```bash
docker --version
docker ps
```

#### Java 21 (Required)
- Download: https://adoptium.net/
- Choose: Eclipse Temurin 21

Verify installation:
```bash
java -version
# Expected: openjdk version "21.x.x"
```

#### Maven 3.9+ (Required)
- Download: https://maven.apache.org/download.cgi
- Extract and add to PATH

Verify installation:
```bash
mvn -version
# Expected: Apache Maven 3.9.x
```

#### Git (Recommended)
- Download: https://git-scm.com

---

## 🎯 Step-by-Step Startup (Total: 15 minutes)

### STEP 1: Extract/Clone Project (2 min)

**Option A: From ZIP File**
```bash
# Extract the ZIP file
unzip gullakx.zip
cd gullakx

# Verify structure
ls -la
# You should see: docker-compose.yml, pom.xml, README.md, etc.
```

**Option B: From GitHub**
```bash
git clone https://github.com/your-org/gullakx.git
cd gullakx
```

---

### STEP 2: Start Infrastructure Services (3 min)

All supporting services (databases, caches, message queues) run in Docker:

```bash
# Navigate to project root
cd gullakx

# Start all Docker services
docker-compose up -d

# Monitor startup progress
docker-compose ps

# Wait for all services to be "Up" and "healthy"
# Expected output:
# NAME                          STATUS
# gullakx-postgres-auth         Up (healthy)
# gullakx-postgres-wallet       Up (healthy)
# gullakx-redis                 Up (healthy)
# gullakx-kafka                 Up (healthy)
# gullakx-elasticsearch         Up (healthy)
# gullakx-kibana                Up (healthy)
# gullakx-prometheus            Up
# gullakx-grafana               Up
# gullakx-kafka-ui              Up
```

**⏳ First startup takes 30-60 seconds. Grab a ☕**

**Verify services are accessible:**
```bash
# PostgreSQL
psql -h localhost -U gullakx -d gullakx_auth -c "SELECT 1" 2>/dev/null && echo "✓ PostgreSQL OK"

# Redis
redis-cli -a gullakx_redis_secret ping 2>/dev/null && echo "✓ Redis OK"

# Kafka
docker exec gullakx-kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092 2>/dev/null | head -1 && echo "✓ Kafka OK"

# Elasticsearch
curl -s http://localhost:9200 | grep version && echo "✓ Elasticsearch OK"
```

---

### STEP 3: Build Microservices (4 min)

```bash
# From project root: gullakx/

# Build entire project
mvn clean install -DskipTests -q

# Wait for completion (shows "BUILD SUCCESS")
# On slow network, first build may take 3-5 minutes
```

**What's happening:**
- Downloading Maven dependencies
- Compiling Java source code
- Packaging JAR files

**Expected output at end:**
```
[INFO] Building auth-service 1.0.0
[INFO] --- jar:3.x.x:jar (default-jar) @ auth-service ---
[INFO] Building jar: /gullakx/auth-service/target/auth-service-1.0.0.jar
[INFO] BUILD SUCCESS
```

---

### STEP 4: Start Microservices (3 min)

Open **FOUR separate terminal windows/tabs** and run these commands from the `gullakx/` root directory:

**Terminal 1 - API Gateway (Port 8080)**
```bash
cd api-gateway
mvn spring-boot:run
```

**Terminal 2 - Auth Service (Port 8081)**
```bash
cd auth-service
mvn spring-boot:run
```

**Terminal 3 - Wallet Service (Port 8082)**
```bash
cd wallet-service
mvn spring-boot:run
```

**Terminal 4 - Monitoring (Monitor logs)**
```bash
# Just watch the logs
docker-compose logs -f
```

**Wait for all services to start:**

Each service should print:
```
Tomcat started on port XXXX
Started AuthServiceApplication in X.XXX seconds
```

All services should start within 60 seconds.

---

### STEP 5: Verify System is Running (2 min)

**Check all services are healthy:**
```bash
# Health check each service
curl http://localhost:8080/actuator/health && echo "\n✓ API Gateway OK"
curl http://localhost:8081/actuator/health && echo "\n✓ Auth Service OK"
curl http://localhost:8082/actuator/health && echo "\n✓ Wallet Service OK"
```

**Expected response:**
```json
{"status":"UP"}
```

---

## 🎨 Access Dashboards & Tools

### Grafana (Metrics Dashboard)
```
URL: http://localhost:3000
Username: admin
Password: admin
```

**What you'll see:**
- Real-time request throughput
- API response times (p50, p95, p99)
- Error rates by service
- Database connection pool usage

### Kibana (Log Explorer)
```
URL: http://localhost:5601
```

**How to use:**
1. Click "Discover"
2. Search: `level: ERROR` or `service: auth-service`
3. View logs in real-time

### Prometheus (Metrics)
```
URL: http://localhost:9090
```

**Example queries:**
```
http_server_requests_seconds_bucket
rate(http_server_requests_seconds_count[5m])
jvm_memory_used_bytes
```

### Kafka UI (Message Queue)
```
URL: http://localhost:8090
```

**Watch topics:**
- `wallet.transfer` — user transfers
- `notifications` — user alerts
- `fraud.events` — fraud signals

### Jaeger (Distributed Tracing)
```
URL: http://localhost:16686
Service: Select any service
```

---

## 🧪 Test the API (First Use)

Open **Terminal 5** for API testing:

### Test 1: Register User (30 sec)

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
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440001"
  },
  "message": "User registered successfully"
}
```

**Save these values:**
```bash
export USER_ID="550e8400-e29b-41d4-a716-446655440000"
export ACCESS_TOKEN="eyJhbGciOiJIUzUxMiJ9..."
```

### Test 2: Get User's Wallet

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

### Test 3: Register Another User (for transfer test)

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Priya Singh",
    "email": "priya@example.com",
    "phoneNumber": "9876543211",
    "password": "SecurePass@123"
  }' | jq . > /tmp/user2.json

# Extract User 2's ID
USER2_ID=$(cat /tmp/user2.json | grep -o '"userId":"[^"]*' | cut -d'"' -f4)
USER2_TOKEN=$(cat /tmp/user2.json | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)

echo "User 2 ID: $USER2_ID"
```

### Test 4: Transfer Money

```bash
# Get User 1's wallet ID first
curl -X GET http://localhost:8080/api/v1/wallets/me \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-User-Id: $USER_ID" | jq '.data.walletId' > /tmp/wallet1.txt

WALLET1_ID=$(cat /tmp/wallet1.txt | tr -d '"')

# Now transfer ₹500 from User 1 to User 2
curl -X POST http://localhost:8080/api/v1/wallets/transfer \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-User-Id: $USER_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "receiverWalletId": "'$USER2_ID'",
    "amount": 500.00,
    "description": "Test transfer",
    "idempotencyKey": "transfer-001"
  }' | jq .
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "transactionId": "tx-uuid",
    "senderWalletId": "wallet-uuid-1",
    "receiverWalletId": "wallet-uuid-2",
    "amount": 500.00,
    "status": "COMPLETED"
  }
}
```

---

## 📊 View Live Data

### In Grafana
1. Open http://localhost:3000
2. Go to Dashboards → Search "GullakX"
3. Watch metrics update in real-time

### In Kibana
1. Open http://localhost:5601
2. Click "Discover"
3. Search: `service: "wallet-service" AND level: "INFO"`
4. See transfer logs

### In Prometheus
1. Open http://localhost:9090
2. Query: `rate(http_server_requests_seconds_count[1m])`
3. See request throughput

---

## 🛑 Stop Everything

### Stop Microservices

In each microservice terminal, press:
```
Ctrl+C
```

### Stop Docker Services

```bash
# From project root
docker-compose down

# To delete all data (clean slate for next run):
docker-compose down -v
```

### Kill any lingering processes

```bash
# On macOS/Linux
killall java

# On Windows PowerShell
Get-Process java | Stop-Process -Force
```

---

## 🔧 Troubleshooting

### Issue 1: "Connection refused" on localhost:8080

**Cause:** Services not started yet

**Fix:**
```bash
# Check if services are running
curl http://localhost:8080/actuator/health

# If no response, check Maven startup (Terminal 1)
# Should show: "Tomcat started on port 8080"
```

### Issue 2: "Docker daemon not running"

**Cause:** Docker Desktop not open

**Fix:**
```bash
# macOS/Windows: Open Docker Desktop application
# Linux: sudo systemctl start docker
docker ps  # Should work after Docker starts
```

### Issue 3: "Port 8080 already in use"

**Cause:** Another application using the port

**Fix:**
```bash
# macOS/Linux - Find and kill process
lsof -i :8080
kill -9 <PID>

# Windows PowerShell
Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess | Stop-Process
```

### Issue 4: Maven build fails

**Cause:** Corrupted cache or missing dependencies

**Fix:**
```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Rebuild
mvn clean install -DskipTests -X  # -X for debug info
```

### Issue 5: PostgreSQL connection error

**Cause:** Docker services not healthy

**Fix:**
```bash
# Check PostgreSQL is running
docker-compose ps postgres-auth

# Check logs
docker-compose logs postgres-auth

# Restart PostgreSQL
docker-compose restart postgres-auth
```

### Issue 6: "java.net.ConnectException: Connection refused" in service logs

**Cause:** Service trying to connect before infrastructure is ready

**Fix:**
```bash
# Ensure all Docker services are healthy first
docker-compose ps  # Check all show "(healthy)"

# Restart the failing service
cd auth-service
mvn spring-boot:run  # or wallet-service, etc.
```

---

## 📁 Project Structure

```
gullakx/
├── pom.xml                          # Parent Maven config
├── docker-compose.yml               # Infrastructure definition
├── README.md                         # This file
│
├── common/                          # Shared code
│   └── pom.xml
│   └── src/main/java/.../
│       └── dto/ApiResponse.java
│
├── api-gateway/                     # Gateway (8080)
│   ├── pom.xml
│   ├── src/main/java/.../
│   │   └── GatewayApplication.java
│   └── src/main/resources/
│       └── application.yml
│
├── auth-service/                    # Auth (8081)
│   ├── pom.xml
│   ├── src/main/java/.../
│   │   └── AuthServiceApplication.java
│   └── src/main/resources/
│       └── application.yml
│
├── wallet-service/                  # Wallet (8082)
│   ├── pom.xml
│   ├── src/main/java/.../
│   │   └── WalletServiceApplication.java
│   └── src/main/resources/
│       └── application.yml
│
└── monitoring/                      # Observability
    └── prometheus/
        ├── prometheus.yml
        └── alert-rules.yml
```

---

## ⚡ Quick Reference

| Component | URL | Access |
|-----------|-----|--------|
| API Gateway | http://localhost:8080 | Public REST API |
| Auth Service | http://localhost:8081 | Service (internal) |
| Wallet Service | http://localhost:8082 | Service (internal) |
| Grafana | http://localhost:3000 | admin/admin |
| Kibana | http://localhost:5601 | No auth |
| Prometheus | http://localhost:9090 | No auth |
| Kafka UI | http://localhost:8090 | No auth |
| Jaeger | http://localhost:16686 | No auth |
| PostgreSQL Auth | localhost:5432 | gullakx/gullakx_secret |
| PostgreSQL Wallet | localhost:5433 | gullakx/gullakx_secret |
| Redis | localhost:6379 | gullakx_redis_secret |
| Kafka | localhost:9092 | No auth |

---

## 💡 Tips & Tricks

### Real-time Service Logs
```bash
# Terminal 4 - Watch all Docker logs
docker-compose logs -f

# Terminal 5 - Watch single service
docker-compose logs -f auth-service
```

### Test API Endpoints Faster
```bash
# Save authentication to shell variable
export TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"arjun@example.com","password":"SecurePass@123"}' \
  | jq -r '.data.accessToken')

# Now use $TOKEN in all requests
curl -X GET http://localhost:8080/api/v1/wallets/me \
  -H "Authorization: Bearer $TOKEN"
```

### Reset Everything to Clean State
```bash
# Stop services
docker-compose down -v  # Remove volumes
mvn clean  # Remove build artifacts

# Start fresh
docker-compose up -d
mvn install -DskipTests
# Start services again...
```

### Monitor Resource Usage
```bash
# Watch Docker container stats
docker stats

# Watch system resources
top  # macOS/Linux
```

---

## 🎓 Learning Path

After everything is running:

1. **Understand the Architecture** → Read `docs/ARCHITECTURE.md`
2. **Explore API** → Visit http://localhost:8080/swagger-ui.html
3. **Test Scenarios** → Follow `docs/TEST_SCENARIOS.md`
4. **Check Databases** → Connect via `psql` or `pgAdmin`
5. **Study Code** → Start with `AuthServiceApplication.java`
6. **View Metrics** → Open Grafana dashboards
7. **Check Logs** → Search in Kibana
8. **Add New Features** → Start with a new endpoint

---

## ✅ Final Checklist

After following all steps, you should have:

- [ ] All Docker containers running and healthy
- [ ] All 3 microservices started successfully
- [ ] Can access API: http://localhost:8080/actuator/health
- [ ] Can register user and get JWT token
- [ ] Can view Grafana dashboard
- [ ] Can see logs in Kibana
- [ ] Can monitor Kafka topics
- [ ] Can transfer money between users

---

## 📞 Support

If stuck:
1. Check Docker services: `docker-compose ps`
2. Check service logs: `docker-compose logs service-name`
3. Verify ports: `lsof -i :8080` (macOS/Linux)
4. Review Kafka: http://localhost:8090
5. Check Prometheus: http://localhost:9090/targets

---

## 🎉 Success!

You now have GullakX running locally! 

**Next:** Explore the code, test the APIs, view the dashboards.

Questions? Check the logs or revisit the troubleshooting section above.

**Happy learning! 🚀**
