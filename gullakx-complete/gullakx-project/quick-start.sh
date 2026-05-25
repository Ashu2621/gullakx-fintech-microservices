#!/bin/bash

# GullakX Quick Start Script for macOS/Linux

set -e

echo ""
echo "╔════════════════════════════════════════════════════════╗"
echo "║          GullakX - Quick Start Script                  ║"
echo "║    Digital Wallet & Digital Gold Platform              ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Check prerequisites
echo -e "${YELLOW}[1/6] Checking prerequisites...${NC}"

if ! command -v docker &> /dev/null; then
    echo -e "${RED}✗ Docker not found${NC}"
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo -e "${RED}✗ Java not found${NC}"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}✗ Maven not found${NC}"
    exit 1
fi

echo -e "${GREEN}✓ All prerequisites installed${NC}"

# Start Docker
echo -e "${YELLOW}[2/6] Starting Docker services...${NC}"
docker-compose up -d
echo -e "${GREEN}✓ Docker services started${NC}"
echo "Waiting 30 seconds for services to be healthy..."
sleep 30

# Build project
echo -e "${YELLOW}[3/6] Building project...${NC}"
mvn clean install -DskipTests -q
echo -e "${GREEN}✓ Project built${NC}"

echo ""
echo -e "${YELLOW}[4/6] Services ready!${NC}"
echo ""
echo "╔════════════════════════════════════════════════════════╗"
echo "║  Now run these commands in SEPARATE terminals:         ║"
echo "║                                                        ║"
echo "║  Terminal 1 - API Gateway:                            ║"
echo "║    cd api-gateway && mvn spring-boot:run              ║"
echo "║                                                        ║"
echo "║  Terminal 2 - Auth Service:                           ║"
echo "║    cd auth-service && mvn spring-boot:run             ║"
echo "║                                                        ║"
echo "║  Terminal 3 - Wallet Service:                         ║"
echo "║    cd wallet-service && mvn spring-boot:run           ║"
echo "║                                                        ║"
echo "║  Then access:                                          ║"
echo "║  - Grafana:  http://localhost:3000 (admin/admin)     ║"
echo "║  - Kibana:   http://localhost:5601                   ║"
echo "║  - API:      http://localhost:8080/actuator/health   ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""
echo -e "${GREEN}✓ Setup complete!${NC}"
echo ""
