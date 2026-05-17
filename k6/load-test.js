/**
 * k6 Load Test — URL Shortener
 *
 * Scenarios:
 *   shorten  — POST /api/v1/urls/shorten  (authenticated, write path)
 *   redirect — GET  /{shortCode}          (public, cached read path)
 *
 * Both scenarios ramp: 10 → 100 → 500 VUs, run in parallel.
 *
 * Run (export to Prometheus remote-write):
 *   k6 run \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e NUM_TEST_USERS=50 \
 *     --out experimental-prometheus-rw \
 *     k6/load-test.js
 *
 * Env vars consumed by the output extension:
 *   K6_PROMETHEUS_RW_SERVER_URL   (default: http://localhost:9090/api/v1/write)
 *   K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=true  (recommended for Grafana)
 *
 * Rate-limit note:
 *   The app enforces 20 req/60 s per (user + IP). At high VU counts a share of
 *   POST /shorten requests will receive 429. This is expected system behaviour;
 *   429s are excluded from the error-rate threshold and counted separately via
 *   url_rate_limit_hits_total.  Raise NUM_TEST_USERS or lower VUs if you need
 *   a clean shorten success rate.
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ─── Custom metrics (all exported to Prometheus with the k6_ prefix) ──────────
const shortenDuration     = new Trend('url_shorten_duration_ms',   true);
const redirectDuration    = new Trend('url_redirect_duration_ms',  true);
const shortenErrors       = new Counter('url_shorten_errors_total');
const redirectErrors      = new Counter('url_redirect_errors_total');
const rateLimitHits       = new Counter('url_rate_limit_hits_total');
const shortenSuccessRate  = new Rate('url_shorten_success_rate');
const redirectSuccessRate = new Rate('url_redirect_success_rate');

// ─── Config ───────────────────────────────────────────────────────────────────
const BASE_URL       = __ENV.BASE_URL        || 'http://localhost:8080';
const NUM_TEST_USERS = parseInt(__ENV.NUM_TEST_USERS || '50');
const TEST_PASSWORD  = __ENV.TEST_PASSWORD   || 'LoadTest@12345!';

// Per-VU accumulator for short codes created during this test run.
// VU-local (not shared across VUs), capped at 500 entries to bound memory.
let vuShortCodes = [];

// ─── Ramp stages (shared shape used by both scenarios) ────────────────────────
const RAMP_STAGES = [
  { duration: '30s', target: 10  },  // warm-up
  { duration: '1m',  target: 10  },  // hold
  { duration: '1m',  target: 100 },  // ramp to 100
  { duration: '2m',  target: 100 },  // hold
  { duration: '2m',  target: 500 },  // ramp to peak
  { duration: '3m',  target: 500 },  // hold at peak
  { duration: '30s', target: 0   },  // ramp down
];

// ─── Test options ─────────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    shorten: {
      executor: 'ramping-vus',
      stages: RAMP_STAGES,
      exec: 'shortenScenario',
    },
    redirect: {
      executor: 'ramping-vus',
      // Start 90 s in so the shorten scenario has populated URLs first.
      startTime: '1m30s',
      stages: RAMP_STAGES,
      exec: 'redirectScenario',
    },
  },

  thresholds: {
    // Write path: P95 < 1 s, P99 < 2 s
    'url_shorten_duration_ms':  ['p(95)<1000', 'p(99)<2000'],
    // Read/redirect path: P95 < 200 ms, P99 < 500 ms (Redis-cached)
    'url_redirect_duration_ms': ['p(95)<200',  'p(99)<500'],
    // Success rates (201 or 429 counts as "server behaved correctly" for shorten)
    'url_shorten_success_rate':  ['rate>0.80'],
    'url_redirect_success_rate': ['rate>0.95'],
    // Overall HTTP error rate (5xx only — 4xx are expected/rate-limited)
    'http_req_failed': ['rate<0.05'],
  },
};

// ─── Helpers ──────────────────────────────────────────────────────────────────
function randInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

/** Builds a spoofed X-Forwarded-For that varies per-VU to spread the IP-based rate limit. */
function fakeIp() {
  return `10.${Math.floor(__VU / 255)}.${__VU % 255}.${randInt(1, 254)}`;
}

// ─── Setup: register test users and seed redirect pool ───────────────────────
export function setup() {
  const tokens = [];
  const codes  = [];

  for (let i = 0; i < NUM_TEST_USERS; i++) {
    const email = `k6_${i}_${Date.now()}@loadtest.invalid`;

    const reg = http.post(
      `${BASE_URL}/api/v1/auth/register`,
      JSON.stringify({ email, password: TEST_PASSWORD, name: `k6 User ${i}` }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup_register' } }
    );

    if (reg.status !== 201) {
      console.warn(`[setup] register ${i} → ${reg.status}: ${reg.body}`);
      continue;
    }

    const { accessToken } = JSON.parse(reg.body);
    tokens.push(accessToken);

    // Seed 5 short URLs per user so the redirect scenario has codes from the start.
    for (let j = 0; j < 5; j++) {
      const seed = http.post(
        `${BASE_URL}/api/v1/urls/shorten`,
        JSON.stringify({ longUrl: `https://example.com/seed/${i}/${j}` }),
        {
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${accessToken}`,
          },
          tags: { name: 'setup_seed' },
        }
      );
      if (seed.status === 201) {
        codes.push(JSON.parse(seed.body).shortCode);
      }
    }
  }

  console.log(`[setup] registered ${tokens.length}/${NUM_TEST_USERS} users, seeded ${codes.length} URLs`);
  return { tokens, codes };
}

// ─── Scenario: POST /api/v1/urls/shorten ─────────────────────────────────────
export function shortenScenario(data) {
  const { tokens } = data;
  if (!tokens.length) { sleep(1); return; }

  // Round-robin tokens by VU id to spread user-level rate-limit windows.
  const token = tokens[__VU % tokens.length];

  const sampleUrls = [
    'https://www.google.com/search?q=url+shortener',
    'https://github.com/grafana/k6',
    'https://developer.mozilla.org/en-US/docs/Web/HTTP/Status',
    'https://spring.io/guides/gs/rest-service',
    `https://example.com/article/${randInt(1, 999999)}`,
  ];

  group('shorten', () => {
    const res = http.post(
      `${BASE_URL}/api/v1/urls/shorten`,
      JSON.stringify({ longUrl: randItem(sampleUrls) }),
      {
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
          'X-Forwarded-For': fakeIp(),
        },
        tags: { name: 'POST /api/v1/urls/shorten', scenario: 'shorten' },
      }
    );

    shortenDuration.add(res.timings.duration, { scenario: 'shorten' });

    if (res.status === 429) {
      rateLimitHits.add(1);
      // 429 is expected at high concurrency — not a server error.
      shortenSuccessRate.add(true);
    } else {
      const ok = check(res, {
        'shorten: status 201':          (r) => r.status === 201,
        'shorten: body has shortCode':  (r) => {
          try { return !!JSON.parse(r.body).shortCode; } catch { return false; }
        },
      });

      shortenSuccessRate.add(ok);

      if (!ok) {
        shortenErrors.add(1);
      } else {
        // Accumulate for this VU's redirect pool (cap at 500 to bound memory).
        const shortCode = JSON.parse(res.body).shortCode;
        vuShortCodes.push(shortCode);
        if (vuShortCodes.length > 500) vuShortCodes.shift();
      }
    }
  });

  sleep(randInt(1, 2));
}

// ─── Scenario: GET /{shortCode} ───────────────────────────────────────────────
export function redirectScenario(data) {
  // Merge seeded codes (from setup) with codes this VU created during the run.
  const pool = [...(data.codes || []), ...vuShortCodes];
  if (!pool.length) { sleep(1); return; }

  const code = randItem(pool);

  group('redirect', () => {
    const res = http.get(`${BASE_URL}/${code}`, {
      redirects: 0,  // capture the 302 itself; don't follow to the destination
      tags: { name: 'GET /{shortCode}', scenario: 'redirect' },
    });

    redirectDuration.add(res.timings.duration, { scenario: 'redirect' });

    const ok = check(res, {
      'redirect: status 302':          (r) => r.status === 302,
      'redirect: has Location header': (r) => r.headers['Location'] !== undefined,
    });

    redirectSuccessRate.add(ok);

    if (!ok) {
      redirectErrors.add(1);
    }
  });

  sleep(randInt(0, 1));
}

// ─── Teardown ─────────────────────────────────────────────────────────────────
export function teardown(data) {
  console.log(`[teardown] seeded codes: ${(data.codes || []).length}`);
}
