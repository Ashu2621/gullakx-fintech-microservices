# 🚀 GullakX - START HERE (5 Minute Setup)

## This is the FASTEST way to get GullakX running on your machine

---

## Step 1️⃣: Download & Install Required Software (3 min)

You need to install 3 things. Follow the links below:

### ✅ Docker Desktop (REQUIRED)
- **macOS/Windows**: https://www.docker.com/products/docker-desktop
- **Linux**: Follow instructions at https://docs.docker.com/engine/install/

After installing:
```bash
docker --version
# Should show: Docker version 25.x.x
```

### ✅ Java 21 (REQUIRED)
- Download: https://adoptium.net/
- Choose: Eclipse Temurin 21

After installing:
```bash
java -version
# Should show: openjdk version "21.x.x"
```

### ✅ Maven 3.9+ (REQUIRED)
- Download: https://maven.apache.org/download.cgi
- Extract and add to PATH

After installing:
```bash
mvn -version
# Should show: Apache Maven 3.9.x
```

---

## Step 2️⃣: Extract GullakX (30 seconds)

```bash
# Extract the ZIP file
unzip gullakx.zip
cd gullakx

# Verify you see these files:
ls -la
# docker-compose.yml, pom.xml, README.md, etc. should be visible
```

---

## Step 3️⃣: Start Infrastructure (1 min)

```bash
# From inside gullakx/ directory:
docker-compose up -d

# Wait 30 seconds for all containers to start
docker-compose ps

# All should show "Up" or "Up (healthy)"
```

---

## Step 4️⃣: Build Project (3 min)

```bash
# Still in gullakx/ directory:
mvn clean install -DskipTests -q

# This downloads dependencies and compiles code
# Wait for "BUILD SUCCESS"
```

---

## Step 5️⃣: Start Services (2 min)

**Open 3 SEPARATE terminal windows/tabs** from the `gullakx/` directory:

### Terminal 1 (API Gateway - Port 8080)
```bash
cd api-gateway
mvn spring-boot:run
# Wait for: "Tomcat started on port 8080"
```

### Terminal 2 (Auth Service - Port 8081)
```bash
cd auth-service
mvn spring-boot:run
# Wait for: "Tomcat started on port 8081"
```

### Terminal 3 (Wallet Service - Port 8082)
```bash
cd wallet-service
mvn spring-boot:run
# Wait for: "Tomcat started on port 8082"
```

✅ All three services should start successfully!

---

## 🎉 YOU'RE DONE! Your system is running!

### Access the system:

**API Gateway** (Main entry point)
```bash
curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}
```

**Grafana** (Dashboards)
- Open: http://localhost:3000
- Username: `admin`
- Password: `admin`
- View live metrics in real-time!

**Kibana** (Logs)
- Open: http://localhost:5601

**Kafka UI** (Message queue)
- Open: http://localhost:8090

---

## 🧪 Test Your First API Call

Open **Terminal 4** and run:

```bash
# Register a new user
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Arjun Sharma",
    "email": "arjun@example.com",
    "phoneNumber": "9876543210",
    "password": "SecurePass@123"
  }'

# You should get back a token and user ID!
```

---

## ❌ Having Issues?

### Issue: "Connection refused"
**Solution:** Make sure all 3 services are running (check all 3 terminals)

### Issue: "Port already in use"
**Solution:** 
```bash
# macOS/Linux
lsof -i :8080
kill -9 <PID>

# Windows PowerShell
Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess | Stop-Process -Force
```

### Issue: "Docker not running"
**Solution:** Open Docker Desktop application

### Issue: "mvn command not found"
**Solution:** Restart terminal after installing Maven, or add Maven to PATH

---

## 📚 Full Documentation

For more details, see:
- **SETUP_GUIDE.md** - Complete setup guide with troubleshooting
- **README.md** - Project overview

---

## 🛑 Stop Everything

In each terminal window, press:
```
Ctrl+C
```

Then in a new terminal:
```bash
docker-compose down
```

---

## ✅ Checklist Before You Start

- [ ] Docker installed and running
- [ ] Java 21 installed
- [ ] Maven installed
- [ ] ZIP file extracted to `gullakx/` folder
- [ ] Opened 3 terminal windows

**Ready? Let's go! Follow Steps 1-5 above.** 🚀

---

Questions? Check **SETUP_GUIDE.md** for detailed troubleshooting.
