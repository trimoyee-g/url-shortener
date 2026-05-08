# 🚀 URL Shortener 

A scalable, production-style **URL Shortener system** built using **Spring Boot, Redis, MySQL, Kafka, and Docker**, designed for **low-latency redirects, high concurrency, and real-time analytics**.

---

## ✨ Key Features
### 1. High-Performance URL Engine
*   **Distributed ID Generation:** Utilizes **Snowflake ID + Base62 encoding** to generate globally unique identifiers, eliminating database sequence bottlenecks.
*   **Asynchronous Resource Creation:** Leverages **Apache Kafka** for URL persistence. By acknowledging requests as soon as events are produced, the system achieves extreme write-throughput and resilience against DB downtime.

### 2. Multi-Layered Cache & Shielding
*   **Ultra-Low Latency:** Targets **sub-10ms redirects** using a combination of **Cache-aside** and **Write-through** strategies in Redis.
*   **Database Protection Layer:** Implements a **Redisson-based Bloom Filter** to intercept "Cache Penetration" attacks, shielding MySQL from 99% of non-existent URL lookups.

### 3. Event-Driven Architecture
*   **Dual-Purpose Event Streaming:** Kafka serves as the backbone for both **resource creation** and **click analytics**, decoupling the "hot path" from slow I/O operations.
*   **Backpressure & Resilience:** Kafka acts as a buffer during traffic surges, ensuring the application remains responsive even during high database latency.

### 4. Reliability & Observability
*   **Distributed Rate Limiting:** Employs a **Sliding Window Rate Limiter** via Redis (Redisson) to mitigate DDoS risks and API scraping.
*   **Full-Stack Monitoring:** Deep visibility via **Prometheus & Grafana**, tracking P95/P99 latencies, Kafka consumer lag, and cache-hit ratios.
*   **Cloud-Native Deployment:** Fully orchestrated ecosystem using **Docker Compose** for seamless environment parity.
---

## 🏗️ System Architecture

Client → Spring Boot API → Redis / Bloom Filter / MySQL / Kafka → Analytics Pipeline

---

## ⚙️ Tech Stack

- Java 17, Spring Boot, Spring Security  
- MySQL  
- Redis  
- Apache Kafka (KRaft)  
- Prometheus + Grafana  
- Docker, Docker Compose  
- Maven  

---

## 🧠 Core Design Concepts

- Snowflake ID Generator  
- Base62 Encoding  
- Bloom Filter (fast negative lookup)  
- Cache-aside + write-through caching  
- Event-driven architecture (Kafka)  
- Hot-path optimization for low latency  

---

## 🚀 Getting Started

### 1️⃣ Clone the repository
```bash
git clone https://github.com/trimoyee-g/url-shortener.git
cd url-shortener
```

### 2️⃣ Configure environment variables
Create `.env` file:

```env
DB_URL=jdbc:mysql://mysql:3306/urlshortener
DB_USERNAME=appuser
DB_PASSWORD=apppassword
MYSQL_ROOT_PASSWORD=your_password
JWT_SECRET=your_secret
REDIS_HOST=redis
REDIS_PORT=6379
KAFKA_SERVERS=kafka:9092
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT
```

### 3️⃣ Build project
```bash
mvn clean install
```

### 4️⃣ Run with Docker
```bash
docker-compose up --build
```

---

## 📡 API Endpoints

### 🔐 Auth
```
POST /api/v1/auth/register
POST /api/v1/auth/login
```

### 🔗 URL Shortening, Redirect
```
POST   /api/v1/urls
GET    /api/v1/urls
DELETE /api/v1/urls/{shortCode}
```

### 🔁 Redirect
```
GET /{shortCode}
```

### 📊 Analytics
```
GET /api/v1/urls/{shortCode}/stats
```

### 📷 QR Code
```
GET /api/v1/urls/{shortCode}/qr?size=300
```
---

## 📈 Monitoring

- Prometheus → http://localhost:9090  
- Grafana → http://localhost:3000  

Metrics:
- Cache hit/miss ratio  
- P95 latency  
- Kafka throughput  
- Request rate  

---

## ⚡ Performance Highlights

- Redis-first lookup (O(1))  
- Bloom filter reduces DB load  
- Kafka async analytics pipeline  
- Snowflake ID for distributed uniqueness  
- Optimized for sub-10ms redirects  

---

## 🐳 Services

- Spring Boot App  
- MySQL  
- Redis  
- Kafka  
- Prometheus  
- Grafana  

---

## 📦 Future Improvements

- CDN-based redirects  
- Custom domains  
- Multi-region deployment  
- Fraud detection  
- Advanced caching policies  


