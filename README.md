# 🚀 URL Shortener

A scalable, production-oriented **URL Shortener Platform** built using **Spring Boot, Redis, Kafka, MySQL, Docker, Prometheus, and Grafana**.  
Designed to support **high-throughput URL generation, ultra-fast redirects, asynchronous analytics processing, and resilient distributed caching**.

---

# ✨ Features

## 🔗 High-Speed URL Shortening

- Distributed unique ID generation using **Snowflake Algorithm**
- Compact short links using **Base62 Encoding**
- Optimized for high concurrency and low collision probability
- Supports extremely fast redirect resolution

---

## ⚡ Multi-Layer Caching Architecture

- **Bloom Filter** pre-screens every request — blocks non-existent key lookups before they reach Redis or MySQL (cache penetration protection).
- **Redis-first** on every redirect — cache hit returns instantly, no DB touch, sub-10ms latency.
- **Cache-aside** on miss — queries MySQL, writes result back to Redis for subsequent requests.
- **Write-through** on URL creation — Redis and MySQL updated atomically, no stale reads.

---

## 📊 Event-Driven Analytics Pipeline

- Kafka-based asynchronous event streaming
- URL creation and click analytics handled independently
- Decouples critical request paths from slow I/O operations
- Improves scalability and resilience under load

---

## 🚦 Distributed Rate Limiting

- **Leaky bucket** algorithm implemented via **Redis Lua scripts** — atomic execution across all instances, no race conditions.
- Rate limits enforced per **IP and email** — 20 requests per 60s window.
- Protects against DDoS, abusive traffic bursts, and excessive scraping.

---

# 🧪 Testing Strategy

This project follows the **Testing Pyramid** approach to ensure fast feedback, high confidence, and production reliability.

```text
                    ┌──────────────────────┐
                    │      E2E Tests       │
                    │  Full HTTP flows     │
                    │ RestAssured + Docker │
                    └──────────────────────┘
                               ▲
                    ┌──────────────────────┐
                    │  Integration Tests   │
                    │ Spring + Redis + DB  │
                    │ Kafka + Testcontainers│
                    └──────────────────────┘
                               ▲
                    ┌──────────────────────┐
                    │     Unit Tests       │
                    │ Services / Controllers│
                    │ Repositories / Utils │
                    └──────────────────────┘
```

### ✅ Unit Tests
- Fast isolated testing of:
    - Services
    - Controllers
    - Repositories
    - JWT logic
    - Base62 encoder
    - Snowflake ID generator
    - Rate limiter logic
- Uses Mockito for dependency isolation

### ✅ Integration Tests
- Verifies interaction between:
    - Spring Boot
    - MySQL
    - Redis
    - Kafka
- Uses:
    - Testcontainers
    - MockMvc
    - Awaitility
- Validates async event-driven workflows

### ✅ End-to-End (E2E) Tests
- Full black-box API testing using:
    - RestAssured
    - Real HTTP requests
    - Full security chain
    - Real infrastructure
- Tests complete production-like user flows:
    - Authentication
    - URL shortening
    - Redirects
    - Analytics
    - Authorization
### ✅ Test Infrastructure
- Testcontainers-based isolated test environments
- Production-like Dockerized testing setup
- Automated container lifecycle management

---


## 📈 Observability & Monitoring

Integrated monitoring stack using:

- Prometheus
- Grafana

Tracks:
- Request throughput
- P95 / P99 latency
- Kafka consumer lag
- Cache hit ratio
- Redis performance metrics

---

# 🏗️ Architecture Overview

```text
Client
   ↓
Spring Boot API
   ↓
Redis Cache / Bloom Filter
   ↓
Kafka Event Stream
   ↓
MySQL Persistence Layer
   ↓
Analytics Pipeline
```

---

# ⚙️ Tech Stack

| Category | Technologies |
|---|---|
| Backend | Java 17, Spring Boot |
| Security | Spring Security, JWT |
| Database | MySQL |
| Caching | Redis, Redisson |
| Messaging | Apache Kafka (KRaft) |
| Monitoring | Prometheus, Grafana |
| Containerization | Docker, Docker Compose |
| Testing | JUnit 5, MockMvc, RestAssured, Testcontainers, Awaitility |
| Build Tool | Maven |

---

# 🧠 Core System Design Concepts

- Snowflake Distributed ID Generation
- Base62 Encoding
- Bloom Filter Optimization
- Cache-aside Caching
- Write-through Caching
- Event-Driven Architecture
- Hot-path Optimization
- Distributed Rate Limiting
- Asynchronous Analytics Processing
- Containerized Integration Testing
---

# 🚀 Getting Started

## 1️⃣ Clone the Repository

```bash
git clone https://github.com/trimoyee-g/url-shortener.git
cd url-shortener
```

---

## 2️⃣ Configure Environment Variables

Create a `.env` file in the project root:

```env
DB_URL=jdbc:mysql://mysql:3306/urlshortener
DB_USERNAME=appuser
DB_PASSWORD=apppassword

MYSQL_ROOT_PASSWORD=your_password

JWT_SECRET=your_secret

REDIS_HOST=redis
REDIS_PORT=6379

KAFKA_SERVERS=kafka:9092
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT
```

---

## 3️⃣ Start the Platform

```bash
docker compose up
```

Docker Compose will automatically:

- Pull the backend image from Docker Hub
- Start MySQL
- Start Redis
- Start Kafka
- Start Prometheus
- Start Grafana
- Start the Spring Boot application

---

# 🌐 Access Services

| Service | URL |
|---|---|
| Backend API | http://localhost:8080 |
| Grafana Dashboard | http://localhost:3000 |
| Prometheus | http://localhost:9090 |

---

# 🐳 Docker Hub Image

```bash
docker pull trimoyeeg/url-shortener:v2
```

---

# 🔄 Updating to Latest Images

```bash
docker compose pull
docker compose up -d
```

---

# 🛑 Stopping the Application

```bash
docker compose down
```

---

# 📡 API Endpoints

## 🔐 Authentication

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
```

---

## 🔗 URL Management

```http
POST   /api/v1/urls
GET    /api/v1/urls
DELETE /api/v1/urls/{shortCode}
```

---

## 🔁 Redirect

```http
GET /{shortCode}
```

---

## 📊 Analytics

```http
GET /api/v1/urls/{shortCode}/stats
```

---

## 📷 QR Code Generation

```http
GET /api/v1/urls/{shortCode}/qr?size=300
```

---

# 📈 Performance Characteristics

- Redis-first O(1) redirect lookups
- Bloom filter optimized invalid request handling
- Kafka-based asynchronous processing
- Distributed Snowflake ID generation
- Low-latency redirect pipeline
- Horizontally scalable architecture

---

# 🐳 Dockerized Services

- Spring Boot Application
- MySQL Database
- Redis Cache
- Apache Kafka
- Prometheus
- Grafana

---

# 🔮 Future Enhancements

- Custom domains
- Multi-region deployment
- CDN-backed redirects
- Advanced fraud detection
- Distributed cache invalidation

---

# 👩‍💻 Author

Trimoyee Ghosh

---

# 🤝 Contributing

Contributions, issues, and feature requests are welcome.

If you'd like to contribute:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Open a Pull Request


---

# ⭐ Support

If you found this project useful, consider:
- Starring the repository
- Forking the project
- Opening issues for bugs or feature requests