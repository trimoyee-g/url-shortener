import React, { useRef, useCallback, useMemo } from "react";
import { Box, Typography, Button, Container } from "@mui/material";
import {
  Zap, ArrowRight, BarChart2, Shield, QrCode, ChevronRight,
} from "lucide-react";

// ─── Static data (computed once at module level) ───────────────────────────

const PARTICLES = Array.from({ length: 38 }, (_, i) => ({
  id: i,
  x: (i * 37.3 + 11) % 100,
  y: (i * 53.7 + 7)  % 100,
  size: 1 + (i % 3) * 0.6,
  delay: (i * 0.27) % 4,
  duration: 4 + (i % 5),
}));

const FLOATING_CARDS = [
  {
    x: 2, y: 12,
    rx: -10, ry: 18, scale: 0.82, delay: 0,
    url: "github.com/my-org/very-long-repository-name/issues/247",
    code: "gh8x2k",
  },
  {
    x: 71, y: 7,
    rx: 8, ry: -14, scale: 0.88, delay: 1.9,
    url: "docs.company.com/getting-started/installation-guide",
    code: "docs9z",
  },
  {
    x: 75, y: 62,
    rx: -7, ry: 11, scale: 0.78, delay: 0.8,
    url: "youtube.com/watch?v=dQw4w9WgXcQ&list=PLrandomLongId",
    code: "yt4mx1",
  },
  {
    x: 1, y: 57,
    rx: 12, ry: -9, scale: 0.84, delay: 2.6,
    url: "notion.so/my-workspace/quarterly-report-2025-q1-review",
    code: "n0t8pl",
  },
];

const FEATURES = [
  {
    icon: Zap,
    title: "Instant Shortening",
    desc: "Create short links in milliseconds with custom aliases and configurable expiry dates.",
    accent: "#38BDF8",
    rgb: "56,189,248",
  },
  {
    icon: BarChart2,
    title: "Real-time Analytics",
    desc: "Track clicks, geographic data, device types, and trends with live dashboards.",
    accent: "#3B82F6",
    rgb: "59,130,246",
  },
  {
    icon: Shield,
    title: "Secure & Reliable",
    desc: "JWT authentication, encrypted storage, cuckoo-filter deduplication, 99.9% uptime.",
    accent: "#7C3AED",
    rgb: "124,58,237",
  },
  {
    icon: QrCode,
    title: "QR Code Generator",
    desc: "Generate and download QR codes for every shortened link, instantly.",
    accent: "#F59E0B",
    rgb: "245,158,11",
  },
];

const STEPS = [
  { n: "01", title: "Paste URL",  desc: "Drop in any long link" },
  { n: "02", title: "Customize", desc: "Alias + expiry date"    },
  { n: "03", title: "Share",     desc: "Copy your short link"   },
  { n: "04", title: "Track",     desc: "Watch clicks roll in"   },
];

const STATS = [
  { value: "99.9%",  label: "Uptime"          },
  { value: "<150ms", label: "Redirect Speed"  },
]

// ─── FloatingCard ──────────────────────────────────────────────────────────

function FloatingCard({ x, y, rx, ry, scale, delay, url, code }) {
  return (
    /* Outer: 3-D rotation (static) */
    <Box
      sx={{
        position: "absolute",
        left: `${x}%`,
        top: `${y}%`,
        transform: `perspective(900px) rotateX(${rx}deg) rotateY(${ry}deg) scale(${scale})`,
        pointerEvents: "none",
        opacity: 0.68,
        display: { xs: "none", lg: "block" },
      }}
    >
      {/* Inner: float animation (Y only, no transform conflict) */}
      <Box
        sx={{
          animation: `floatY ${6.5 + delay * 0.4}s ease-in-out infinite ${delay}s`,
        }}
      >
        <Box
          sx={{
            background:
              "linear-gradient(135deg, rgba(13,17,23,0.92) 0%, rgba(10,20,14,0.8) 100%)",
            border: "1px solid rgba(56,189,248,0.15)",
            borderRadius: "14px",
            p: "12px 14px",
            backdropFilter: "blur(22px)",
            width: 238,
            boxShadow:
              "0 12px 40px rgba(0,0,0,0.5), inset 0 1px 0 rgba(255,255,255,0.04)",
          }}
        >
          {/* Browser traffic-light dots */}
          <Box sx={{ display: "flex", gap: "5px", mb: "10px" }}>
            {["#FF5F57", "#FEBC2E", "#28C840"].map((c) => (
              <Box
                key={c}
                sx={{ width: 8, height: 8, borderRadius: "50%", background: c }}
              />
            ))}
          </Box>

          {/* Long URL bar */}
          <Box
            sx={{
              px: 1, py: "5px", mb: "9px",
              background: "rgba(0,0,0,0.35)",
              borderRadius: "6px",
              border: "1px solid rgba(255,255,255,0.05)",
            }}
          >
            <Typography
              sx={{
                fontSize: "0.58rem", color: "#7D8590",
                fontFamily: "'IBM Plex Mono', monospace",
                overflow: "hidden", textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
            >
              🔒 {url}
            </Typography>
          </Box>

          {/* Arrow divider */}
          <Box
            sx={{
              display: "flex", alignItems: "center", gap: 1, my: "6px",
            }}
          >
            <Box sx={{ flex: 1, height: 1, background: "rgba(56,189,248,0.18)" }} />
            <Typography sx={{ fontSize: "0.62rem", color: "#38BDF8" }}>⚡</Typography>
            <Box sx={{ flex: 1, height: 1, background: "rgba(56,189,248,0.18)" }} />
          </Box>

          {/* Short URL result */}
          <Box
            sx={{
              px: 1.5, py: "7px",
              background: "rgba(56,189,248,0.07)",
              borderRadius: "6px",
              border: "1px solid rgba(56,189,248,0.2)",
            }}
          >
            <Typography
              sx={{
                fontSize: "0.7rem", color: "#38BDF8",
                fontFamily: "'IBM Plex Mono', monospace",
                letterSpacing: "0.04em",
              }}
            >
              trim.it/{code}
            </Typography>
          </Box>
        </Box>
      </Box>
    </Box>
  );
}

// ─── FeatureCard ───────────────────────────────────────────────────────────

function FeatureCard({ icon: Icon, title, desc, accent, rgb }) {
  const ref = useRef(null);

  const onMove = (e) => {
    const el = ref.current;
    const r = el.getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width  - 0.5;
    const y = (e.clientY - r.top)  / r.height - 0.5;
    el.style.transform = `perspective(700px) rotateY(${x * 16}deg) rotateX(${-y * 16}deg) translateZ(18px)`;
    el.style.boxShadow = `${-x * 12}px ${y * 12}px 40px rgba(0,0,0,0.35), 0 0 30px rgba(${rgb},0.12)`;
  };

  const onLeave = () => {
    const el = ref.current;
    el.style.transform = "perspective(700px) rotateY(0) rotateX(0) translateZ(0)";
    el.style.boxShadow = "0 4px 20px rgba(0,0,0,0.2)";
  };

  return (
    <Box
      ref={ref}
      onMouseMove={onMove}
      onMouseLeave={onLeave}
      sx={{
        background: "rgba(13,17,23,0.9)",
        border: "1px solid #21262D",
        borderRadius: "16px",
        p: 3,
        cursor: "default",
        transition: "transform 0.12s ease, box-shadow 0.18s ease",
        transformStyle: "preserve-3d",
        boxShadow: "0 4px 20px rgba(0,0,0,0.2)",
        position: "relative",
        overflow: "hidden",
        "&::before": {
          content: '""',
          position: "absolute",
          top: 0, left: 0, right: 0,
          height: "1px",
          background: `linear-gradient(90deg, transparent, rgba(${rgb},0.45), transparent)`,
        },
      }}
    >
      <Box
        sx={{
          display: "inline-flex",
          p: 1.5,
          background: `rgba(${rgb},0.1)`,
          borderRadius: "12px",
          mb: 2.5,
          border: `1px solid rgba(${rgb},0.2)`,
          color: accent,
        }}
      >
        <Icon size={22} />
      </Box>
      <Typography
        variant="h6"
        sx={{ fontSize: "1rem", fontWeight: 600, mb: 1.5, color: "#E6EDF3" }}
      >
        {title}
      </Typography>
      <Typography
        sx={{
          fontSize: "0.8rem", color: "#7D8590", lineHeight: 1.7,
          fontFamily: "'IBM Plex Mono', monospace",
        }}
      >
        {desc}
      </Typography>
    </Box>
  );
}

// ─── LandingPage ───────────────────────────────────────────────────────────

export default function LandingPage({ onGetStarted }) {
  const heroRef   = useRef(null);
  const contentRef = useRef(null);

  const handleHeroMouseMove = useCallback((e) => {
    const rect = heroRef.current?.getBoundingClientRect();
    if (!rect) return;
    const x = (e.clientX - rect.left) / rect.width  - 0.5;
    const y = (e.clientY - rect.top)  / rect.height - 0.5;
    if (contentRef.current) {
      contentRef.current.style.transform =
        `perspective(1400px) rotateX(${-y * 3}deg) rotateY(${x * 3}deg)`;
    }
  }, []);

  const handleHeroMouseLeave = useCallback(() => {
    if (contentRef.current) {
      contentRef.current.style.transform =
        "perspective(1400px) rotateX(0deg) rotateY(0deg)";
    }
  }, []);

  return (
    <Box
      sx={{
        minHeight: "100vh",
        background: "#080C10",
        overflowX: "hidden",
        animation: "pageIn 0.5s ease both",
      }}
    >
      {/* ── Navbar ─────────────────────────────────────────── */}
      <Box
        component="nav"
        sx={{
          position: "fixed", top: 0, left: 0, right: 0, zIndex: 200,
          px: { xs: 3, md: 8 }, py: 2,
          display: "flex", alignItems: "center", justifyContent: "space-between",
          background: "rgba(8,12,16,0.85)",
          backdropFilter: "blur(24px)",
          borderBottom: "1px solid rgba(33,38,45,0.45)",
        }}
      >
        {/* Logo */}
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
          <Box
            sx={{
              p: "6px",
              background: "rgba(56,189,248,0.1)",
              borderRadius: "8px",
              display: "flex",
              boxShadow: "0 0 14px rgba(56,189,248,0.2)",
            }}
          >
            <Zap size={16} color="#38BDF8" />
          </Box>
          <Typography
            sx={{
              color: "#38BDF8",
              letterSpacing: "0.12em",
              fontSize: "0.88rem",
              fontFamily: "'Syne', sans-serif",
              fontWeight: 700,
            }}
          >
            TrimIt
          </Typography>
        </Box>

        {/* Nav actions */}
        <Box sx={{ display: "flex", gap: 1.5, alignItems: "center" }}>
          <Button
            variant="text"
            size="small"
            onClick={onGetStarted}
            sx={{
              color: "#7D8590", fontSize: "0.8rem",
              "&:hover": { color: "#E6EDF3", background: "transparent" },
            }}
          >
            Sign in
          </Button>
          <Button
            variant="contained"
            size="small"
            onClick={onGetStarted}
            endIcon={<ArrowRight size={13} />}
            sx={{
              background: "linear-gradient(135deg,#38BDF8,#0f41a7)",
              color: "#080C10",
              fontWeight: 700,
              borderRadius: "8px",
              px: 2,
              "&:hover": {
                boxShadow: "0 0 24px rgba(56,189,248,0.45)",
                transform: "translateY(-1px)",
              },
              transition: "all 0.2s ease",
            }}
          >
            Get Started
          </Button>
        </Box>
      </Box>

      {/* ── Hero ───────────────────────────────────────────── */}
      <Box
        ref={heroRef}
        onMouseMove={handleHeroMouseMove}
        onMouseLeave={handleHeroMouseLeave}
        sx={{
          minHeight: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          position: "relative",
          pt: 8,
        }}
      >
        {/* Glow orbs */}
        <Box sx={{
          position: "absolute", top: "10%", left: "50%",
          transform: "translate(-50%,-50%)",
          width: 540, height: 540,
          background: "radial-gradient(circle, rgba(56,189,248,0.045) 0%, transparent 65%)",
          borderRadius: "50%",
          animation: "pulseSlow 5s ease-in-out infinite",
          pointerEvents: "none",
        }} />
        <Box sx={{
          position: "absolute", bottom: "22%", left: "8%",
          width: 420, height: 420,
          background: "radial-gradient(circle, rgba(59,130,246,0.07) 0%, transparent 65%)",
          borderRadius: "50%",
          animation: "pulseSlow 7s ease-in-out infinite 2.5s",
          pointerEvents: "none",
        }} />
        <Box sx={{
          position: "absolute", top: "38%", right: "6%",
          width: 360, height: 360,
          background: "radial-gradient(circle, rgba(124,58,237,0.07) 0%, transparent 65%)",
          borderRadius: "50%",
          animation: "pulseSlow 6s ease-in-out infinite 4s",
          pointerEvents: "none",
        }} />

        {/* Grid background */}
        <Box sx={{
          position: "absolute", inset: 0, pointerEvents: "none",
          backgroundImage: [
            "linear-gradient(rgba(56,189,248,0.015) 1px, transparent 1px)",
            "linear-gradient(90deg, rgba(56,189,248,0.015) 1px, transparent 1px)",
          ].join(","),
          backgroundSize: "60px 60px",
        }} />

        {/* Particles */}
        {PARTICLES.map((p) => (
          <Box
            key={p.id}
            sx={{
              position: "absolute",
              left: `${p.x}%`, top: `${p.y}%`,
              width: p.size, height: p.size,
              borderRadius: "50%",
              background: "#38BDF8",
              pointerEvents: "none",
              animation: `twinkle ${p.duration}s ease-in-out infinite ${p.delay}s`,
            }}
          />
        ))}

        {/* 3D Floating URL cards */}
        {FLOATING_CARDS.map((c, i) => (
          <FloatingCard key={i} {...c} />
        ))}

        {/* Hero content */}
        <Box
          ref={contentRef}
          sx={{
            position: "relative", zIndex: 10,
            textAlign: "center",
            px: { xs: 2, md: 4 },
            maxWidth: 820,
            transition: "transform 0.18s ease",
          }}
        >
          {/* Badge */}
          {/* <Box
            sx={{
              display: "inline-flex", alignItems: "center", gap: 1,
              mb: 5, px: 2.5, py: "7px",
              background: "rgba(56,189,248,0.05)",
              border: "1px solid rgba(56,189,248,0.22)",
              borderRadius: "20px",
              animation: "fadeInDown 0.75s ease both",
            }}
          >
            <Box
              sx={{
                width: 6, height: 6, borderRadius: "50%",
                background: "#38BDF8",
                animation: "pulseDot 1.8s ease-in-out infinite",
              }}
            />
            <Typography
              sx={{
                fontSize: "0.72rem", color: "#0b0b0b",
                fontFamily: "'IBM Plex Mono', monospace",
                letterSpacing: "0.07em",
              }}
            >
              Built for performance
            </Typography>
          </Box> */}

          {/* Headline */}
          <Typography
            variant="h1"
            sx={{
              fontSize: { xs: "3rem", sm: "4rem", md: "5.5rem", lg: "6.5rem" },
              lineHeight: 1.0,
              fontWeight: 800,
              letterSpacing: "-0.025em",
              mb: 4,
              animation: "fadeInUp 0.8s ease 0.1s both",
            }}
          >
            <Box component="span" sx={{ display: "block", color: "#E6EDF3" }}>
              Shorten.
            </Box>
            {/* <Box
              component="span"
              sx={{
                display: "block",
                background:
                  "linear-gradient(135deg,#1223e1 0%,#818CF8 55%,#C084FC 100%)",
                backgroundClip: "text",
                WebkitBackgroundClip: "text",
                WebkitTextFillColor: "transparent",
                backgroundSize: "250% 250%",
                animation: "gradientShift 5s ease infinite",
              }}
            >
              Share.
            </Box> */}
            <Box component="span" sx={{ display: "block", color: "#0f41a7" }}>
              Share.
            </Box>
            <Box component="span" sx={{ display: "block", color: "#E6EDF3" }}>
              Track Everything.
            </Box>
          </Typography>

          {/* Subtext */}
          <Typography
            sx={{
              fontSize: { xs: "0.95rem", md: "1.1rem" },
              color: "#000000",
              maxWidth: 520, mx: "auto",
              lineHeight: 1.8,
              mb: 6,
              fontFamily: "'IBM Plex Mono', monospace",
              animation: "fadeInUp 0.8s ease 0.22s both",
            }}
          >
            Transform unwieldy URLs into sleek short links with real‑time
            analytics, QR codes, and enterprise‑grade security.
          </Typography>

          {/* CTA buttons */}
          <Box
            sx={{
              display: "flex", gap: 2, justifyContent: "center",
              flexWrap: "wrap",
              animation: "fadeInUp 0.8s ease 0.38s both",
            }}
          >
            <Button
              variant="contained"
              size="large"
              onClick={onGetStarted}
              endIcon={<ArrowRight size={17} />}
              sx={{
                px: 5, py: "14px",
                fontSize: "0.95rem",
                fontWeight: 700,
                background: "linear-gradient(135deg,#38BDF8,#0f41a7)",
                color: "#080C10",
                borderRadius: "12px",
                boxShadow: "0 0 40px rgba(56,189,248,0.35)",
                letterSpacing: "0.02em",
                animation: "glowPulse 3s ease-in-out infinite",
                "&:hover": {
                  background: "linear-gradient(135deg,#38BDF8,#0f41a7)",
                  boxShadow: "0 0 64px rgba(56,189,248,0.58)",
                  transform: "translateY(-3px) scale(1.02)",
                },
                transition: "transform 0.2s ease, box-shadow 0.2s ease",
              }}
            >
              Get Started — Free
            </Button>
            <Button
              variant="outlined"
              size="large"
              onClick={() =>
                document.getElementById("features")?.scrollIntoView({ behavior: "smooth" })
              }
              sx={{
                px: 4, py: "14px",
                fontSize: "0.95rem",
                borderColor: "rgba(255,255,255,0.09)",
                color: "#000000",
                borderRadius: "12px",
                "&:hover": {
                  borderColor: "rgba(255,255,255,0.22)",
                  background: "rgba(255,255,255,0.03)",
                  color: "#E6EDF3",
                },
                transition: "all 0.2s ease",
              }}
            >
              Explore features ↓
            </Button>
          </Box>
        </Box>

        {/* Scroll indicator */}
        <Box
          sx={{
            position: "absolute", bottom: 40, left: "50%",
            transform: "translateX(-50%)",
            pointerEvents: "none", zIndex: 1,
          }}
        >
          <Box sx={{ animation: "bounceScroll 2.2s ease-in-out infinite" }}>
            <Box
              sx={{
                width: 26, height: 44,
                border: "1.5px solid rgba(125,133,144,0.4)",
                borderRadius: "13px",
                display: "flex",
                alignItems: "flex-start",
                justifyContent: "center",
                pt: "8px",
              }}
            >
              <Box
                sx={{
                  width: "3px", height: "9px",
                  background: "rgba(125,133,144,0.5)",
                  borderRadius: "2px",
                  animation: "scrollDotMove 2.2s ease-in-out infinite",
                }}
              />
            </Box>
          </Box>
        </Box>
      </Box>

      {/* ── Stats bar ──────────────────────────────────────── */}
      <Box
        sx={{
          py: 4,
          borderTop: "1px solid #21262D",
          borderBottom: "1px solid #21262D",
          background: "rgba(13,17,23,0.65)",
          backdropFilter: "blur(12px)",
        }}
      >
        <Container maxWidth="lg">
          <Box
            sx={{
              display: "flex",
              justifyContent: "center",
              gap: { xs: 5, md: 10 },
              flexWrap: "wrap",
            }}
          >
            {STATS.map(({ value, label }) => (
              <Box key={label} sx={{ textAlign: "center" }}>
                <Typography
                  sx={{
                    fontFamily: "'Syne', sans-serif",
                    fontWeight: 800,
                    fontSize: { xs: "1.6rem", md: "2.2rem" },
                    color: "#38BDF8",
                    lineHeight: 1,
                    mb: 0.5,
                  }}
                >
                  {value}
                </Typography>
                <Typography
                  sx={{
                    fontSize: "0.66rem",
                    color: "#7D8590",
                    fontFamily: "'IBM Plex Mono', monospace",
                    letterSpacing: "0.1em",
                  }}
                >
                  {label.toUpperCase()}
                </Typography>
              </Box>
            ))}
          </Box>
        </Container>
      </Box>

      {/* ── Features ───────────────────────────────────────── */}
      <Box id="features" sx={{ py: { xs: 8, md: 14 } }}>
        <Container maxWidth="lg">
          {/* Section label */}
          <Box sx={{ textAlign: "center", mb: { xs: 6, md: 10 } }}>
            <Typography
              sx={{
                fontSize: "0.68rem",
                fontFamily: "'IBM Plex Mono', monospace",
                color: "#38BDF8",
                letterSpacing: "0.2em",
                mb: 2,
              }}
            >
              FEATURES
            </Typography>
            {/* <Typography
              variant="h2"
              sx={{
                fontSize: { xs: "2rem", md: "3rem" },
                fontWeight: 800,
                mb: 2,
                letterSpacing: "-0.02em",
              }}
            >
              Everything you need
            </Typography>
            <Typography
              sx={{
                color: "#7D8590",
                fontFamily: "'IBM Plex Mono', monospace",
                fontSize: "0.88rem",
              }}
            >
              Powerful tools for developers, marketers, and teams
            </Typography> */}
          </Box>

          {/* 4-col feature grid */}
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: {
                xs: "1fr",
                sm: "repeat(2,1fr)",
                md: "repeat(4,1fr)",
              },
              gap: 3,
            }}
          >
            {FEATURES.map((f) => (
              <FeatureCard key={f.title} {...f} />
            ))}
          </Box>

          {/* How it works */}
          <Box sx={{ mt: 12, textAlign: "center" }}>
            <Typography
              sx={{
                fontSize: "0.68rem",
                fontFamily: "'IBM Plex Mono', monospace",
                color: "#38BDF8",
                letterSpacing: "0.2em",
                mb: 6,
              }}
            >
              HOW IT WORKS
            </Typography>
            <Box
              sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: { xs: 4, md: 3 },
                flexWrap: "wrap",
              }}
            >
              {STEPS.map((s, i) => (
                <React.Fragment key={s.n}>
                  <Box sx={{ textAlign: "center" }}>
                    <Box
                      sx={{
                        width: 56, height: 56,
                        borderRadius: "50%",
                        border: "1px solid rgba(56,189,248,0.25)",
                        background: "rgba(56,189,248,0.05)",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        mx: "auto", mb: 1.5,
                        animation: `stepPulse ${3 + i * 0.5}s ease-in-out infinite ${i * 0.6}s`,
                      }}
                    >
                      <Typography
                        sx={{
                          fontFamily: "'Syne', sans-serif",
                          fontWeight: 800,
                          color: "#38BDF8",
                          fontSize: "0.78rem",
                        }}
                      >
                        {s.n}
                      </Typography>
                    </Box>
                    <Typography
                      sx={{
                        fontFamily: "'Syne', sans-serif",
                        fontWeight: 600,
                        fontSize: "0.92rem",
                        mb: 0.5,
                        color: "#E6EDF3",
                      }}
                    >
                      {s.title}
                    </Typography>
                    <Typography
                      sx={{
                        fontFamily: "'IBM Plex Mono', monospace",
                        color: "#7D8590",
                        fontSize: "0.72rem",
                      }}
                    >
                      {s.desc}
                    </Typography>
                  </Box>
                  {i < 3 && (
                    <Box
                      sx={{
                        color: "#30363D",
                        fontSize: "1.4rem",
                        display: { xs: "none", md: "block" },
                        flexShrink: 0,
                      }}
                    >
                      →
                    </Box>
                  )}
                </React.Fragment>
              ))}
            </Box>
          </Box>
        </Container>
      </Box>

      {/* ── Bottom CTA ─────────────────────────────────────── */}
      <Box
        sx={{
          py: { xs: 8, md: 14 },
          textAlign: "center",
          borderTop: "1px solid #21262D",
          position: "relative",
          overflow: "hidden",
        }}
      >
        {/* Glow behind CTA */}
        <Box
          sx={{
            position: "absolute",
            top: "50%", left: "50%",
            transform: "translate(-50%,-50%)",
            width: 700, height: 350,
            background:
              "radial-gradient(ellipse, rgba(56,189,248,0.06) 0%, transparent 70%)",
            pointerEvents: "none",
          }}
        />
        <Container maxWidth="sm" sx={{ position: "relative", zIndex: 1 }}>
          <Typography
            variant="h2"
            sx={{
              fontSize: { xs: "2rem", md: "2.8rem" },
              fontWeight: 800,
              mb: 2,
              letterSpacing: "-0.02em",
            }}
          >
            Ready to start?
          </Typography>
          <Button
            variant="contained"
            size="large"
            onClick={onGetStarted}
            endIcon={<ChevronRight size={18} />}
            sx={{
              my: 2,
              px: 6, py: "14px",
              fontSize: "1rem",
              fontWeight: 700,
              background: "linear-gradient(135deg,#38BDF8,#0f41a7)",
              color: "#080C10",
              borderRadius: "12px",
              boxShadow: "0 0 40px rgba(56,189,248,0.3)",
              "&:hover": {
                background: "linear-gradient(135deg,#38BDF8,#0f41a7)",
                boxShadow: "0 0 60px rgba(56,189,248,0.52)",
                transform: "translateY(-3px) scale(1.02)",
              },
              transition: "all 0.25s ease",
            }}
          >
            Start for Free
          </Button>
        </Container>
      </Box>

      {/* ── Footer ─────────────────────────────────────────── */}
      <Box
        sx={{
          py: 3,
          borderTop: "1px solid #21262D",
          textAlign: "center",
        }}
      >
        <Typography
          sx={{
            fontSize: "0.7rem",
            color: "#7D8590",
            fontFamily: "'IBM Plex Mono', monospace",
          }}
        >
          © 2025 TrimIt · React 19 + Spring Boot
        </Typography>
      </Box>
    </Box>
  );
}
