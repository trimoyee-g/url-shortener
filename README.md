<div align="center">

# URL Shortener

**High-throughput URL shortening with sub-millisecond redirects, distributed rate limiting, and full observability.**

A production-oriented URL shortener built with Java, Spring Boot, React, Redis, RabbitMQ, MySQL, Docker, Prometheus, and Grafana. Designed for high-concurrency URL generation, ultra-fast cached redirects, asynchronous analytics, and resilient distributed caching.

[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-green?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-blue?style=flat-square&logo=react)](https://react.dev)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)

</div>

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Features](#features)
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

- **Sub-millisecond redirects** via Redis-first caching with Cuckoo Filter cache penetration protection
- **Synchronous write path** — URL records are persisted to MySQL immediately on creation, ensuring instant readability with zero consistency lag
- **Asynchronous analytics** — click events are published to RabbitMQ and processed off the hot redirect path, so redirect latency is never blocked by analytics writes
- **Distributed rate limiting** — leaky bucket via Redis Lua scripts, enforced atomically across all instances
- **Full observability** — Prometheus and Grafana tracking P95/P99 latencies, cache hit ratios, and queue depth
- **Load tested** — verified at 1500 concurrent users, 1232 req/s at peak, sub-139ms P95 redirect latency

---

## Architecture

### Write Path — URL Shortening

```
React Frontend
      │ POST /api/v1/urls/shorten  (JWT auth · rate limiter)
      ▼
Spring Boot API
      │
      ├─ Idempotency check (MySQL): has this user already shortened this URL?
      │        │ YES → return existing short code
      │        │ NO
      │  Snowflake ID + Base62 → shortCode   (or use custom alias)
      │  Optionally: hash password, append UTM params, set TTL
      │
      ├─→ MySQL         (persist URL record — synchronous)
      ├─→ Redis         (cache URL)
      └─→ Cuckoo Filter (add short code)
            │
            ▼
      Return short URL immediately
```

### Read Path — Redirect

```
Client  GET /{shortCode}
           │
           ▼
     Spring Boot API
           │
           ├─ 1. Cuckoo Filter
           │        │ NOT IN FILTER
           │        └──────────────→ 404
           ├─ 2. Redis Cache
           │        ├─ HIT
           │        │     ├─ protected (sentinel)
           │        │     │        └──→ Serve unlock page
           │        │     └─ open
           │        │          └──→ 302 Redirect
           │        └─ MISS
           └─ 3. MySQL
                    │
                    ├─ not found ────────────────────────→ 404
                    └─ found → repopulate Redis
                             ├─ protected=true
                             │        └──→ Serve unlock page
                             └─ protected=false
                                      └──→ 302 Redirect
           │
           │ (non-blocking, fire-and-forget)
           ▼
     RabbitMQ [url.clicks.exchange]
           │ async
           ▼
     AnalyticsConsumer
           ├─ GeoIP lookup (MaxMind)
           ├─ Device classification (User-Agent → MOBILE/TABLET/DESKTOP)
           ├─→ MySQL   (persist ClickEvent)
           └─→ Redis   (increment click counter)
```

### Password Unlock Path

```
Client  POST /{shortCode}/unlock  { "password": "…" }
           │
           ▼
     Spring Boot API
           │
           ├─ MySQL lookup
           │        │ not found → 404
           │        │ found
           ├─ BCrypt verify against stored hash
           │        │ WRONG → 401 Unauthorized
           │        │ OK
           └─ Return { shortCode, redirectUrl }
                  │
                  ▼ (click event published, client redirects)
```

### Delete Path — URL Deletion

```
DELETE /api/v1/urls/{shortCode}
      │
      ▼
Spring Boot API
      ├─→ MySQL         (set active = false)
      ├─→ Redis         (evict cache entry)
      └─→ Cuckoo Filter (CF.DEL — remove fingerprint)
```

### Observability

```
Spring Boot (Micrometer) → Prometheus → Grafana
```

---

## Tech Stack

| Category | Technologies |
|---|---|
| Frontend | React 19, Vite, Material UI |
| Backend | Java 17, Spring Boot |
| Security | Spring Security, JWT, BCrypt |
| Database | MySQL |
| Caching | Redis Stack (Cuckoo Filter via CF commands) |
| Messaging | RabbitMQ (Spring AMQP) |
| Monitoring | Prometheus, Grafana |
| Containerization | Docker, Docker Compose |
| Testing | JUnit 5, Mockito, RestAssured, Testcontainers, Awaitility |
| Build | Maven |

---

## Features

### Custom aliases

When shortening a URL, supply an optional `customAlias` (3–20 characters, `[a-zA-Z0-9_-]`) to get a human-readable short code instead of a generated one. Alias uniqueness is enforced at the database level.

### URL expiration (TTL)

Set `ttlSeconds` on a shorten request to create a time-limited link. Expired links are detected at redirect time, removed from the Cuckoo Filter, and return 410 Gone.

### Password-protected links

Set `password` on a shorten request to create a private link. When a visitor hits the short URL, the server detects the password hash and serves a self-contained HTML unlock page (no external dependencies, styled to match the app theme). The visitor enters the password, which is verified via BCrypt server-side at `POST /{shortCode}/unlock`. On success the client receives the destination URL and performs the redirect.

### UTM campaign tracking

Supply any combination of `utmSource`, `utmMedium`, and `utmCampaign` when creating a URL. The parameters are automatically appended to the destination URL on every redirect, so campaign attribution lands in your analytics stack without requiring changes to destination pages.

### Device-type analytics

Every click event is classified into `MOBILE`, `TABLET`, or `DESKTOP` by parsing the `User-Agent` header. The breakdown is surfaced per-link in the stats view and rolled up in the account dashboard.

### Account-level dashboard analytics

`GET /api/v1/analytics/dashboard?days=N` returns aggregate stats across all of a user's active links for the requested time window: total clicks, unique visitors, total active links, click trend vs. the prior period, clicks-by-day time series, top countries, top referrers, and device breakdown.

### QR code generation

`GET /api/v1/urls/{shortCode}/qr?size=300` returns a PNG QR code for the short URL, generated server-side via ZXing.

---

## Performance

### Baseline Load Test

Load tested using k6 with 1,000 concurrent virtual users  
(500 redirect readers + 500 authenticated writers) over 10 minutes.

| Metric | Result    |
|---|-----------|
| Redirect P95 latency | 56.89ms   |
| Redirect P99 latency | 119.68ms  |
| Redirect success rate | 100%      |
| Shorten P95 latency | 117.57ms  |
| Shorten P99 latency | 251.03s   |
| Shorten success rate | 100%      |
| Peak throughput | 548 req/s |
| Total requests served | 383K+     |
| Rate-limited requests absorbed | 84,574    |

Metrics were tracked live using Prometheus and Grafana during the test.  
The distributed rate limiter successfully absorbed burst traffic (HTTP 429 responses) without affecting system stability or request success rates.

---

### Stress Test

Stress tested using k6 with 1,500 concurrent virtual users  
(1,000 redirect readers + 500 authenticated writers) over 17 minutes.

| Metric | Result     |
|---|------------|
| Redirect P95 latency | 138.05ms   |
| Redirect P99 latency | 227.91ms   |
| Redirect success rate | 100%       |
| Shorten P95 latency | 812.4ms    |
| Shorten P99 latency | 1.29s      |
| Shorten success rate | 99.71%     |
| Peak throughput | 1232 req/s |
| Total requests served | 1.25M+     |
| Rate-limited requests absorbed | 137,690    |

Metrics were tracked live using Prometheus and Grafana throughout the stress test.  
The distributed rate limiter successfully absorbed high burst traffic (HTTP 429 responses) without destabilizing the system under sustained load.

---

## Caching Strategy

Multi-layer caching architecture optimised for the redirect hot path:

**Cuckoo Filter first** — every redirect checks the Cuckoo Filter before touching Redis or MySQL. A short code that has never existed is rejected in microseconds (cache penetration protection). Only codes that might exist proceed further.

Unlike a Bloom Filter, the Cuckoo Filter supports deletion. When a URL is deleted or expires, its fingerprint is removed from the filter via `CF.DEL`, preventing the false-positive rate from climbing over time as codes are recycled or purged.

**Redis cache** — on a Cuckoo Filter pass, Redis is checked next. A cache hit returns instantly with no DB touch, achieving sub-millisecond lookup on warm keys.

**Cache-aside** on miss — queries MySQL, writes result back to Redis for subsequent requests.

**Write-through** on URL creation — MySQL, Redis, and the Cuckoo Filter are all updated synchronously during the shorten request, so newly created URLs are immediately readable with no consistency window.

---

## Rate Limiting

Distributed leaky bucket algorithm implemented via Redis Lua scripts — atomic execution across all instances with no race conditions.

- Rate limit: 20 requests per 60-second window
- Enforced per IP address and per user email independently
- Protects against DDoS, abusive traffic bursts, and excessive scraping
- 429 responses are counted separately in observability and excluded from error rate thresholds

---

## Analytics Pipeline

RabbitMQ-based asynchronous event streaming decouples analytics from the critical request path:

- Click events are published to a RabbitMQ direct exchange immediately after redirect (or after a successful password unlock), without blocking the response
- The `AnalyticsConsumer` processes events off the queue: performs GeoIP lookup, classifies the device type from the User-Agent, persists a `ClickEvent` to MySQL, and increments the per-URL click counter in Redis
- Per-URL stats: click counts, unique visitors (distinct IPs), geographic breakdown, referrer breakdown, device breakdown, and clicks-by-day
- Account dashboard: aggregate totals and trends across all a user's active links
- RabbitMQ's task queue model (message deleted after ACK) is a natural fit: each click event needs to be processed exactly once with no need for replay or multiple consumer groups

---

## Testing Strategy

Follows the testing pyramid with three layers:

**Unit tests** — isolated testing of services, controllers, repositories, JWT logic, Base62 encoder, Snowflake ID generator, and rate limiter logic using JUnit 5 and Mockito.

**Integration tests** — verifies interaction between Spring Boot, MySQL, Redis Stack, and RabbitMQ using Testcontainers, MockMvc, and Awaitility. Validates async event-driven workflows end-to-end.

**End-to-end tests** — full black-box API testing via RestAssured against real infrastructure. Covers authentication, URL shortening, redirects, analytics, and authorization flows.

---

## Observability

Prometheus and Grafana provide full-stack visibility:

- Request throughput and error rates
- P95/P99 latency per endpoint
- Redis cache hit ratio
- Redis performance metrics
- RabbitMQ queue depth and consumer throughput

Grafana is available at `http://localhost:3000` (default credentials: admin / admin).  
The Prometheus datasource is provisioned automatically on first boot — no manual setup required.

---

## Key Design Decisions

**Snowflake ID + Base62 encoding** — Snowflake generates distributed unique 64-bit IDs without coordination. Base62 encoding produces compact 7–10 character short codes with negligible collision probability at scale.

**Synchronous URL persistence** — URL records are written to MySQL, Redis, and the Cuckoo Filter in a single synchronous transaction during the shorten request. This eliminates the consistency window that an async approach creates: a URL is immediately resolvable after creation with no race between the write path and read path.

**BCrypt for link passwords** — link passwords are stored as BCrypt hashes (strength 12). Protected links are cached in Redis using a sentinel value (`__PROTECTED__`) rather than the actual destination URL. A cache hit on the sentinel triggers the unlock page without ever exposing the destination. On a cache miss, the MySQL record is checked and the sentinel is written back — so subsequent requests for a protected link never reach MySQL again.

**UTM params appended at resolve time** — UTM parameters are stored alongside the URL record and appended to the destination URL at redirect time by `UtmUtils`. This means the short URL remains clean for sharing while campaign attribution is always injected correctly.

**RabbitMQ for analytics** — click events are fire-and-forget from the redirect path's perspective. RabbitMQ's task queue model is the right fit: each message is consumed once and deleted on ACK, which is exactly the semantics analytics needs. Unlike a log-based broker, there is no offset to manage and no risk of replay buildup from consumer lag.

**Redis Lua scripts for rate limiting** — Lua scripts execute atomically on Redis, ensuring the leaky bucket counter check-and-decrement is a single operation with no race conditions across instances.

**Cuckoo Filter before Redis** — a non-existent short code lookup (e.g. a bot scanning random codes) would cause a Redis miss and a MySQL query on every request. The Cuckoo Filter catches these at near-zero cost before they touch any data store. Crucially, unlike a Bloom Filter, it supports deletion: when URLs are deleted or expire, their fingerprints are removed, keeping the false-positive rate bounded regardless of churn.

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

**Shorten request body fields:**

| Field | Required | Description |
|---|---|---|
| `longUrl` | yes | Destination URL |
| `customAlias` | no | Human-readable alias (3–20 chars, `[a-zA-Z0-9_-]`) |
| `ttlSeconds` | no | Link lifetime in seconds; omit for default expiry |
| `password` | no | Password to protect the link (BCrypt hashed at rest) |
| `utmSource` | no | `utm_source` appended to the destination URL |
| `utmMedium` | no | `utm_medium` appended to the destination URL |
| `utmCampaign` | no | `utm_campaign` appended to the destination URL |

### Redirect

```
GET  /{shortCode}           — redirects (302), or serves unlock page if password-protected
POST /{shortCode}/unlock    — verify password, returns { shortCode, redirectUrl }
```

### Analytics

```
GET /api/v1/urls/{shortCode}/stats?days=30   — per-link analytics
GET /api/v1/analytics/dashboard?days=30      — account-level aggregate analytics
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
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
BASE_URL=http://localhost:8080
MACHINE_ID=1
```

`BASE_URL` is used when constructing short URLs returned by the API. Set it to your public domain in production.  
`MACHINE_ID` is the Snowflake node ID (0–1023). Must be unique per running app instance when scaling horizontally.

### 3. Start the stack

```bash
docker compose up -d
```

Builds the application image locally and starts MySQL, Redis Stack, RabbitMQ, Prometheus, Grafana, and the Spring Boot backend. Grafana connects to Prometheus automatically — no manual datasource configuration needed.

RabbitMQ management UI is available at `http://localhost:15672` (default credentials: guest / guest).

### 4. Run the frontend

```bash
cd url-shortener-ui
npm install
npm run dev
```

Frontend available at `http://localhost:5173`, backend at `http://localhost:8080`.

### Docker Hub

```bash
docker pull trimoyeeg/url-shortener:latest
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
