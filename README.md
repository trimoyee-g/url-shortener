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

- Redis-powered **cache-first redirect flow**
- Implements **Cache-aside** and **Write-through** caching strategies
- Designed for **sub-10ms redirect latency**
- Reduces database round-trips under heavy traffic

---

## 🛡️ Cache Shielding with Bloom Filters

- Uses **Redisson Bloom Filter** for fast negative lookups
- Prevents cache penetration attacks
- Shields MySQL from unnecessary invalid requests
- Minimizes database load during malicious or random traffic spikes

---

## 📊 Event-Driven Analytics Pipeline

- Kafka-based asynchronous event streaming
- URL creation and click analytics handled independently
- Decouples critical request paths from slow I/O operations
- Improves scalability and resilience under load

---

## 🚦 Distributed Rate Limiting

- Sliding Window Rate Limiter implemented using Redis
- Protects APIs against:
    - DDoS attacks
    - excessive scraping
    - abusive traffic bursts

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
docker pull trimoyeeg/url-shortener:v1
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

Remove containers and volumes:

```bash
docker compose down -v
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
- Clickstream analytics
- Geo-based analytics

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