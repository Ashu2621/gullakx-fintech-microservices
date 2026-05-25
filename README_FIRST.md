# 🚀 GullakX – Fintech Microservices Backend Platform

A backend-focused fintech platform demonstrating microservices architecture, API Gateway patterns, containerized infrastructure, monitoring systems, and financial backend design principles.

Built to explore scalable backend engineering concepts including service decomposition, observability, infrastructure automation, and transaction-oriented systems.

---

## 🏗 Architecture

System designed using modular backend services:

```
Client
   |
API Gateway (8080)
   |
-------------------------
|                       |
Auth Service       Wallet Service
(8081)             (8082)
   |                   |
PostgreSQL        PostgreSQL
   |
Infrastructure Layer
-------------------------
Redis
Kafka
Prometheus
Grafana
Elasticsearch
Jaeger
AlertManager
```

---

## ⚙ Tech Stack

### Backend
- Java 21
- Spring Boot 3.2
- Spring Cloud Gateway
- Maven

### Database
- PostgreSQL

### Infrastructure
- Docker
- Docker Compose
- Redis
- Apache Kafka

### Monitoring & Observability
- Prometheus
- Grafana
- Elasticsearch
- Jaeger
- AlertManager

### Development Tools
- Git
- GitHub

---

## 📦 Services

### 1. API Gateway

Responsibilities:

- Centralized request routing
- Service endpoint management
- Backend traffic distribution
- Gateway-based architecture pattern

Port:

```
8080
```

---

### 2. Auth Service

Responsibilities:

- User authentication module structure
- User management foundation
- Authentication service separation
- Backend security layer foundation

Port:

```
8081
```

---

### 3. Wallet Service

Responsibilities:

- Wallet backend module foundation
- Transaction-oriented architecture
- Financial backend domain separation
- Wallet system service layer

Port:

```
8082
```

---

## 📊 Infrastructure Components

Docker Compose infrastructure includes:

| Component | Purpose |
|------------|----------|
| PostgreSQL | Persistent relational storage |
| Redis | Caching infrastructure |
| Kafka | Event streaming platform |
| Elasticsearch | Log aggregation |
| Prometheus | Metrics collection |
| Grafana | Dashboard visualization |
| Jaeger | Distributed tracing |
| AlertManager | Monitoring alerts |

---

## 📈 Project Metrics

- 3 backend microservices
- 10+ infrastructure components
- Multi-module Maven architecture
- 100% Dockerized local development setup
- Centralized observability stack
- Containerized development workflow

---

## 📂 Project Structure

```
gullakx-project/

├── api-gateway/
├── auth-service/
├── wallet-service/
├── common/
├── monitoring/
│   └── prometheus/
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## 🚀 Local Setup

### Clone Repository

```bash
git clone https://github.com/Ashu2621/gullakx-fintech-microservices.git

cd gullakx-fintech-microservices
```

### Start Infrastructure

```bash
docker-compose up -d
```

### Build Project

```bash
mvn clean install
```

### Start Services

Terminal 1

```bash
cd api-gateway

mvn spring-boot:run
```

Terminal 2

```bash
cd auth-service

mvn spring-boot:run
```

Terminal 3

```bash
cd wallet-service

mvn spring-boot:run
```

---

## 📊 Monitoring Stack

Access dashboards locally:

Grafana

```
http://localhost:3000
```

Prometheus

```
http://localhost:9090
```

Jaeger

```
http://localhost:16686
```

---

## 🎯 Backend Engineering Concepts Demonstrated

- Microservices architecture
- Service decomposition
- API Gateway pattern
- Docker containerization
- Infrastructure orchestration
- Monitoring systems
- Distributed tracing
- Log aggregation
- Financial backend design principles
- Multi-module backend architecture

---

## 🔮 Future Enhancements

Planned backend improvements:

- JWT Authentication
- Distributed transactions
- Event-driven architecture
- Wallet transfer APIs
- Ledger system
- Rate limiting
- Redis caching layer
- Circuit breaker implementation
- API documentation using Swagger
- CI/CD pipelines
- Automated testing coverage
- Kubernetes deployment

---

## 💼 Resume Highlights

Backend engineering project demonstrating:

- Java Spring Boot microservices
- Dockerized infrastructure
- PostgreSQL integration
- API Gateway architecture
- Monitoring and observability tooling
- Financial systems backend foundations

---

## 👨‍💻 Author

Ashu

Backend Engineering • Java • Spring Boot • PostgreSQL • Microservices

GitHub:

https://github.com/Ashu2621

---

Built for learning scalable backend engineering principles 🚀
