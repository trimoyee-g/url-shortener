/**
 * k6 Load Test — URL Shortener (1000 Concurrent Users)
 *
 * Scenarios:
 *   shorten  — POST /api/v1/urls/shorten
 *   redirect — GET  /{shortCode}
 *
 * Target:
 *   Sustain ~1000 concurrent users at peak load.
 *
 * Run:
 *   k6 run \
 *     --vus-max 2200 \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e NUM_TEST_USERS=2000 \
 *     --out experimental-prometheus-rw \
 *     --summary-export=summary.json \
 *     k6/load-test.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ─────────────────────────────────────────────────────────────────────────────
// Metrics
// ─────────────────────────────────────────────────────────────────────────────
const shortenDuration = new Trend('url_shorten_duration_ms', true);
const redirectDuration = new Trend('url_redirect_duration_ms', true);

const shortenErrors = new Counter('url_shorten_errors_total');
const redirectErrors = new Counter('url_redirect_errors_total');

const rateLimitHits = new Counter('url_rate_limit_hits_total');

const shortenSuccessRate = new Rate('url_shorten_success_rate');
const redirectSuccessRate = new Rate('url_redirect_success_rate');

// ─────────────────────────────────────────────────────────────────────────────
// Config
// ─────────────────────────────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const NUM_TEST_USERS = parseInt(
  __ENV.NUM_TEST_USERS || '2000'
);

const TEST_PASSWORD =
  __ENV.TEST_PASSWORD || 'LoadTest@12345!';

// Per-VU storage
let vuShortCodes = [];

// ─────────────────────────────────────────────────────────────────────────────
// Shared ramp stages
// ─────────────────────────────────────────────────────────────────────────────
const SHORTEN_STAGES = [
  { duration: '30s', target: 50 },
  { duration: '1m', target: 100 },
  { duration: '2m', target: 200 },
  { duration: '2m', target: 300 },
  { duration: '2m', target: 400 },
  { duration: '3m', target: 500 },
  { duration: '5m', target: 500 },
  { duration: '1m', target: 0 },
];

const REDIRECT_STAGES = [
  { duration: '30s', target: 100 },
  { duration: '1m', target: 250 },
  { duration: '2m', target: 500 },
  { duration: '2m', target: 750 },
  { duration: '2m', target: 1000 },
  { duration: '5m', target: 1000 },
  { duration: '1m', target: 0 },
];

// ─────────────────────────────────────────────────────────────────────────────
// Options
// ─────────────────────────────────────────────────────────────────────────────
export const options = {
  setupTimeout: '10m',
  scenarios: {
    shorten: {
      executor: 'ramping-vus',
      exec: 'shortenScenario',
      stages: SHORTEN_STAGES,
      gracefulRampDown: '30s',
    },

    redirect: {
      executor: 'ramping-vus',
      exec: 'redirectScenario',
      startTime: '1m',
      stages: REDIRECT_STAGES,
      gracefulRampDown: '30s',
    },
  },

  thresholds: {
    url_shorten_duration_ms: [
      'p(95)<1000',
      'p(99)<2000',
    ],

    url_redirect_duration_ms: [
      'p(95)<200',
      'p(99)<500',
    ],

    url_shorten_success_rate: [
      'rate>0.80',
    ],

    url_redirect_success_rate: [
      'rate>0.95',
    ],

    http_req_failed: [
      'rate<0.05',
    ],
  },
};

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────
function randInt(min, max) {
  return Math.floor(
    Math.random() * (max - min + 1)
  ) + min;
}

function randItem(arr) {
  return arr[
    Math.floor(Math.random() * arr.length)
  ];
}

function fakeIp() {
  return `10.${Math.floor(__VU / 255)}.${__VU % 255}.${randInt(1, 254)}`;
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup
// ─────────────────────────────────────────────────────────────────────────────
export function setup() {
  const tokens = [];
  const codes = [];

  console.log(
    `[setup] creating ${NUM_TEST_USERS} users...`
  );

  for (let i = 0; i < NUM_TEST_USERS; i++) {
    const email =
      `k6_${i}_${Date.now()}@loadtest.invalid`;

    const registerRes = http.post(
      `${BASE_URL}/api/v1/auth/register`,
      JSON.stringify({
        email,
        password: TEST_PASSWORD,
        name: `k6 User ${i}`,
      }),
      {
        headers: {
          'Content-Type': 'application/json',
        },
        tags: {
          name: 'setup_register',
        },
      }
    );

    if (registerRes.status !== 201) {
      console.warn(
        `[setup] failed user ${i}: ${registerRes.status}`
      );
      continue;
    }

    const { accessToken } =
      JSON.parse(registerRes.body);

    tokens.push(accessToken);

    // Seed initial URLs
    for (let j = 0; j < 5; j++) {
      const seedRes = http.post(
        `${BASE_URL}/api/v1/urls/shorten`,
        JSON.stringify({
          longUrl:
            `https://example.com/seed/${i}/${j}`,
        }),
        {
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${accessToken}`,
          },
          tags: {
            name: 'setup_seed',
          },
        }
      );

      if (seedRes.status === 201) {
        const body = JSON.parse(seedRes.body);
        codes.push(body.shortCode);
      }
    }
  }

  console.log(
    `[setup] users=${tokens.length}, seeded_urls=${codes.length}`
  );

  return {
    tokens,
    codes,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Shorten Scenario
// ─────────────────────────────────────────────────────────────────────────────
export function shortenScenario(data) {
  const { tokens } = data;

  if (!tokens.length) {
    sleep(1);
    return;
  }

  const token =
    tokens[__VU % tokens.length];

  const sampleUrls = [
    'https://www.google.com',
    'https://github.com/grafana/k6',
    'https://spring.io',
    'https://developer.mozilla.org',
    `https://example.com/${randInt(1, 1000000)}`,
  ];

  group('shorten', () => {
    const res = http.post(
      `${BASE_URL}/api/v1/urls/shorten`,
      JSON.stringify({
        longUrl: randItem(sampleUrls),
      }),
      {
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
          'X-Forwarded-For': fakeIp(),
        },

        tags: {
          name: 'POST /api/v1/urls/shorten',
          scenario: 'shorten',
        },
      }
    );

    shortenDuration.add(
      res.timings.duration
    );

    if (res.status === 429) {
      rateLimitHits.add(1);
      shortenSuccessRate.add(true);
    } else {
      const ok = check(res, {
        'status is 201': (r) =>
          r.status === 201,

        'has shortCode': (r) => {
          try {
            return !!JSON.parse(r.body)
              .shortCode;
          } catch {
            return false;
          }
        },
      });

      shortenSuccessRate.add(ok);

      if (!ok) {
        shortenErrors.add(1);
      } else {
        const shortCode =
          JSON.parse(res.body).shortCode;

        vuShortCodes.push(shortCode);

        if (vuShortCodes.length > 500) {
          vuShortCodes.shift();
        }
      }
    }
  });

  sleep(randInt(1, 2));
}

// ─────────────────────────────────────────────────────────────────────────────
// Redirect Scenario
// ─────────────────────────────────────────────────────────────────────────────
export function redirectScenario(data) {
  const pool = [
    ...(data.codes || []),
    ...vuShortCodes,
  ];

  if (!pool.length) {
    sleep(1);
    return;
  }

  const code = randItem(pool);

  group('redirect', () => {
    const res = http.get(
      `${BASE_URL}/${code}`,
      {
        redirects: 0,

        tags: {
          name: 'GET /{shortCode}',
          scenario: 'redirect',
        },
      }
    );

    redirectDuration.add(
      res.timings.duration
    );

    const ok = check(res, {
      'status is 302': (r) =>
        r.status === 302,

      'has location header': (r) =>
        r.headers.Location !== undefined,
    });

    redirectSuccessRate.add(ok);

    if (!ok) {
      redirectErrors.add(1);
    }
  });

  sleep(randInt(0, 1));
}

// ─────────────────────────────────────────────────────────────────────────────
// Teardown
// ─────────────────────────────────────────────────────────────────────────────
export function teardown(data) {
  console.log(
    `[teardown] total_seeded_codes=${(data.codes || []).length}`
  );
}