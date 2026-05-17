<div align="center">

# URL Shortener

**High-throughput URL shortening with sub-millisecond redirects, distributed rate limiting, and full observability.**

A production-oriented URL shortener built with Java, Spring Boot, React, Redis, Kafka, MySQL, Docker, Prometheus, and Grafana. Designed for high-concurrency URL generation, ultra-fast cached redirects, asynchronous analytics, and resilient distributed caching.

[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-green?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue?style=flat-square&logo=react)](https://react.dev)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)

</div>

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Performance](#performance)
- [Caching Strategy](#caching-strategy)
- [Rate Limiting](#rate-limiting)
- [Analytics Pipeline](#analytics-pipeline)
- [Testing Strategy](#testing-strategy)
- [Observability](#observability)
- [Key Design Decisions](#key-design-decisions)
- [API Reference](#api-reference)
- [Getting Started](#getting-started)

---

## Overview

A URL shortener built to handle real production load — not just a CRUD app. Key design goals:

- **Sub-millisecond redirects** via Redis-first caching with Bloom Filter cache penetration protection
- **Asynchronous write path** — Kafka decouples URL creation from database persistence, eliminating write-path latency
- **Distributed rate limiting** — leaky bucket via Redis Lua scripts, enforced atomically across all instances
- **Full observability** — Prometheus and Grafana tracking P95/P99 latencies, cache hit ratios, and Kafka consumer lag
- **Load tested** — verified at 500 concurrent users, 462 req/s, sub-20ms P95 redirect latency

---

## Architecture

### Write Path — URL Shortening

```
React Frontend
      │ POST /api/v1/urls/shorten  (JWT auth · rate limiter)
      ▼
Spring Boot API
      │ Snowflake ID + Base62 → shortCode
      │
      ├──────────────────────────────────┐
      │ async                            │ sync (immediate readability)
      ▼                                  ▼
Kafka [url-creations]          Redis (URL cache)
      │                      + Bloom Filter (stored in Redis)
      ▼ async
UrlCreateConsumer
      ├─→ MySQL   (persist URL record)
      ├─→ Redis   (cache URL)
      └─→ Bloom Filter (add short code)
```

### Read Path — Redirect

```
Client  GET /{shortCode}
           │
           ▼
     Spring Boot API
           │
           ├─ 1. Bloom Filter ──── NOT IN FILTER → 404
           │        │ MIGHT EXIST
           ├─ 2. Redis cache ──────────────────────── HIT → 302 Redirect
           │        │ MISS
           └─ 3. MySQL → repopulate Redis ──────────────── 302 Redirect
           │
           │ (non-blocking)
           ▼
     GeoIP lookup (MaxMind)
           │
           ▼
     Kafka [url-clicks]
           │ async
           ▼
     AnalyticsConsumer
           ├─→ MySQL   (persist ClickEvent)
           └─→ Redis   (increment click counter)
```

### Observability

```
Spring Boot (Micrometer) → Prometheus → Grafana
```

---

## Tech Stack

| Category | Technologies |
|---|---|
| Frontend | React 18, Vite, Material UI |
| Backend | Java 17, Spring Boot |
| Security | Spring Security, JWT |
| Database | MySQL |
| Caching | Redis, Redisson |
| Messaging | Apache Kafka (KRaft) |
| Monitoring | Prometheus, Grafana |
| Containerization | Docker, Docker Compose |
| Testing | JUnit 5, Mockito, RestAssured, Testcontainers, Awaitility |
| Build | Maven |

---

## Performance

Load tested with k6 at 500 concurrent users over 10 minutes:

| Metric | Result |
|---|---|
| Redirect P95 latency | 19.71ms |
| Redirect P99 latency | 70.07ms |
| Redirect success rate | 99.87% |
| Shorten P95 latency | 35.93ms |
| Shorten success rate | 100% |
| Peak throughput | 462 req/s |

Metrics tracked live via Prometheus and Grafana during the test. Rate limiter correctly absorbed 80,804 burst requests (429s) without affecting the error rate threshold.

---

## Caching Strategy

Multi-layer caching architecture optimised for the redirect hot path:

**Bloom Filter first** — every redirect checks the Bloom Filter before touching Redis or MySQL. A short code that has never existed is rejected in microseconds (cache penetration protection). Only codes that might exist proceed further.

**Redis cache** — on a Bloom Filter pass, Redis is checked next. A cache hit returns instantly with no DB touch, achieving sub-millisecond lookup on warm keys.

**Cache-aside** on miss — queries MySQL, writes result back to Redis for subsequent requests.

**Write-through** on URL creation — Redis and Bloom Filter updated immediately after Kafka publish, ensuring no stale reads on newly created URLs.

---

## Rate Limiting

Distributed leaky bucket algorithm implemented via Redis Lua scripts — atomic execution across all instances with no race conditions.

- Rate limit: 20 requests per 60-second window
- Enforced per IP address and per user email independently
- Protects against DDoS, abusive traffic bursts, and excessive scraping
- 429 responses are counted separately in observability and excluded from error rate thresholds

---

## Analytics Pipeline

Kafka-based asynchronous event streaming decouples analytics from the critical request path:

- URL creation events published to Kafka immediately on request
- Click events streamed asynchronously — redirect latency is never blocked by analytics writes
- Consumer group processes events independently, providing resilience under load
- Per-URL click counts, geographic breakdown, and clicks-by-day tracked in the dashboard

---

## Testing Strategy

Follows the testing pyramid with three layers:

**Unit tests** — isolated testing of services, controllers, repositories, JWT logic, Base62 encoder, Snowflake ID generator, and rate limiter logic using JUnit 5 and Mockito.

**Integration tests** — verifies interaction between Spring Boot, MySQL, Redis, and Kafka using Testcontainers, MockMvc, and Awaitility. Validates async event-driven workflows end-to-end.

**End-to-end tests** — full black-box API testing via RestAssured against real infrastructure. Covers authentication, URL shortening, redirects, analytics, and authorization flows.

---

## Observability

Prometheus and Grafana provide full-stack visibility:

- Request throughput and error rates
- P95/P99 latency per endpoint
- Kafka consumer lag
- Redis cache hit ratio
- Redis performance metrics

Grafana available at `http://localhost:3000` (default: admin/admin).

---

## Key Design Decisions

**Snowflake ID + Base62 encoding** — Snowflake generates distributed unique 64-bit IDs without coordination. Base62 encoding produces compact 7–10 character short codes with negligible collision probability at scale.

**Kafka for write-path decoupling** — URL creation publishes to Kafka and returns immediately. The database consumer processes asynchronously, eliminating write-path latency from the user-facing response time.

**Redis Lua scripts for rate limiting** — Lua scripts execute atomically on Redis, ensuring the leaky bucket counter check-and-decrement is a single operation with no race conditions across instances.

**Bloom Filter before Redis** — a non-existent short code lookup (e.g. a bot scanning random codes) would cause a Redis miss and a MySQL query on every request. The Bloom Filter catches these at near-zero cost before they touch any data store.

---

## API Reference

### Authentication

```
POST /api/v1/auth/register
POST /api/v1/auth/login
```

### URL management

```
POST   /api/v1/urls/shorten
GET    /api/v1/urls
DELETE /api/v1/urls/{shortCode}
```

### Redirect

```
GET /{shortCode}
```

### Analytics

```
GET /api/v1/urls/{shortCode}/stats
```

### QR code

```
GET /api/v1/urls/{shortCode}/qr?size=300
```

---

## Getting Started

### Prerequisites

- Docker and Docker Compose
- Node.js (for frontend)

### 1. Clone the repository

```bash
git clone https://github.com/trimoyee-g/url-shortener.git
cd url-shortener
```

### 2. Configure environment variables

Create a `.env` file in the project root:

```env
DB_URL=jdbc:mysql://mysql:3306/url_shortener
DB_USERNAME=appuser
DB_PASSWORD=apppassword
MYSQL_ROOT_PASSWORD=your_password
JWT_SECRET=your_secret
REDIS_HOST=redis
REDIS_PORT=6379
KAFKA_SERVERS=kafka:9092
```

### 3. Start the stack

```bash
docker compose up -d
```

Starts MySQL, Redis, Kafka, Prometheus, Grafana, and the Spring Boot backend automatically.

### 4. Run the frontend

```bash
cd url-shortener-ui
npm install
npm run dev
```

Frontend available at `http://localhost:5173`, backend at `http://localhost:8080`.

### Docker Hub

```bash
docker pull trimoyeeg/url-shortener:v3
```

### Stopping

```bash
docker compose down
```

---

## Contributing

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes
4. Push and open a pull request

Please add unit tests for any new service-layer logic.

---

<div align="center">
Built for speed, designed for resilience.
</div>
