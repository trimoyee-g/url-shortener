import React, { useRef, useCallback, useState } from "react";
import { Box, Typography, Button, Container, InputBase, CircularProgress } from "@mui/material";
import {
  Zap, ArrowRight, BarChart2, Shield, QrCode, ChevronRight,
  Link as LinkIcon, Tag, Lock, Globe, Copy, Check, TrendingUp,
} from "lucide-react";

// ─── Palette ─────────────────────────────────────────────────────────────────
// One accent, everything else is grayscale.
const C = {
  bg:      "#080C10",
  surface: "#0D1117",
  border:  "#1C2128",
  muted:   "#6B7280",
  subtle:  "#374151",
  body:    "#9CA3AF",
  text:    "#E6EDF3",
  accent:  "#38BDF8",
};

// ─── Feature data ─────────────────────────────────────────────────────────────
const FEATURES = [
  {
    icon: Zap,
    title: "Instant Shortening",
    desc:  "Custom aliases, configurable expiry, and Snowflake IDs that scale without collision.",
  },
  {
    icon: BarChart2,
    title: "Deep Analytics",
    desc:  "Clicks, countries, devices, referrers — all tracked in real time with period-over-period trends.",
  },
  {
    icon: Tag,
    title: "UTM Tracking",
    desc:  "Automatically append utm_source, medium, and campaign without touching the destination URL.",
  },
  {
    icon: Lock,
    title: "Password Protection",
    desc:  "Gate any link behind a password. Visitors are prompted before the redirect happens.",
  },
  {
    icon: QrCode,
    title: "QR Code Generator",
    desc:  "Every link gets a downloadable QR code, server-generated.",
  },
  {
    icon: Shield,
    title: "Secure by Default",
    desc:  "JWT auth, BCrypt passwords, cuckoo-filter dedup, and non-root containers.",
  },
];

const STEPS = [
  { n: "01", title: "Paste",     desc: "Drop in any long URL"          },
  { n: "02", title: "Customize", desc: "Set alias, expiry, or password" },
  { n: "03", title: "Share",     desc: "Copy and distribute your link"  },
  { n: "04", title: "Analyze",   desc: "Watch the analytics come in"    },
];

const STATS = [
  { value: "99.9%",  label: "Uptime"         },
  { value: "<150ms", label: "Redirect Speed" },
];

// ─── Floating preview card ────────────────────────────────────────────────────
const CARDS = [
  { x: 3,  y: 14, rx: -6,  ry: 14,  scale: 0.85, delay: 0,
    url: "github.com/org/very-long-repository-name/pull/247", code: "gh8x2k", clicks: 142 },
  { x: 72, y: 9,  rx:  5,  ry: -11, scale: 0.88, delay: 2.1,
    url: "docs.company.com/guides/getting-started",           code: "docs9z", clicks:  58 },
  { x: 74, y: 60, rx: -5,  ry:  9,  scale: 0.80, delay: 0.9,
    url: "youtube.com/watch?v=dQw4w9WgXcQ&list=PLrandom",    code: "yt4mx1", clicks: 891 },
  { x: 2,  y: 58, rx:  9,  ry:  -7, scale: 0.83, delay: 2.8,
    url: "notion.so/workspace/quarterly-review-2025-q1",      code: "n0t8pl", clicks:  34 },
];

function FloatingCard({ x, y, rx, ry, scale, delay, url, code, clicks }) {
  return (
    <Box sx={{
      position: "absolute", left: `${x}%`, top: `${y}%`,
      transform: `perspective(900px) rotateX(${rx}deg) rotateY(${ry}deg) scale(${scale})`,
      pointerEvents: "none",
      display: { xs: "none", lg: "block" },
    }}>
      <Box sx={{ animation: `floatY ${7 + delay * 0.3}s ease-in-out infinite ${delay}s` }}>
        <Box sx={{
          width: 240,
          background: C.surface,
          border: `1px solid ${C.border}`,
          borderRadius: "12px",
          p: "12px 14px",
          boxShadow: "0 20px 60px rgba(0,0,0,0.6)",
          opacity: 0.8,
        }}>
          {/* Traffic lights */}
          <Box sx={{ display: "flex", gap: "5px", mb: "10px", opacity: 0.6 }}>
            {["#555","#555","#555"].map((c, i) => (
              <Box key={i} sx={{ width: 8, height: 8, borderRadius: "50%", background: c }} />
            ))}
          </Box>
          {/* Long URL */}
          <Box sx={{ px: "10px", py: "6px", mb: "8px",
            background: "rgba(0,0,0,0.3)", borderRadius: "6px",
            border: `1px solid ${C.border}` }}>
            <Typography sx={{ fontSize: "0.56rem", color: C.muted,
              fontFamily: "'IBM Plex Mono', monospace",
              overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
              {url}
            </Typography>
          </Box>
          {/* Divider */}
          <Box sx={{ display: "flex", alignItems: "center", gap: 1, my: "6px", opacity: 0.4 }}>
            <Box sx={{ flex: 1, height: "1px", background: C.border }} />
            <Zap size={10} color={C.accent} />
            <Box sx={{ flex: 1, height: "1px", background: C.border }} />
          </Box>
          {/* Short URL */}
          <Box sx={{ px: "10px", py: "7px",
            background: "rgba(56,189,248,0.05)",
            borderRadius: "6px", border: `1px solid rgba(56,189,248,0.15)`,
            display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <Typography sx={{ fontSize: "0.7rem", color: C.accent,
              fontFamily: "'IBM Plex Mono', monospace" }}>
              trim.it/{code}
            </Typography>
            <Typography sx={{ fontSize: "0.6rem", color: C.muted,
              fontFamily: "'IBM Plex Mono', monospace" }}>
              {clicks}
            </Typography>
          </Box>
        </Box>
      </Box>
    </Box>
  );
}

// ─── Hero shortener ───────────────────────────────────────────────────────────
function HeroShortener({ onGetStarted }) {
  const [url,     setUrl]     = useState("");
  const [result,  setResult]  = useState(null);
  const [copied,  setCopied]  = useState(false);
  const [loading, setLoading] = useState(false);

  const submit = async (e) => {
    e?.preventDefault();
    const trimmed = url.trim();
    if (!trimmed) return;
    try { new URL(trimmed); } catch { return; }
    setLoading(true);
    setResult(null);
    await new Promise((r) => setTimeout(r, 580));
    setResult(`trim.it/${Math.random().toString(36).slice(2, 8)}`);
    setLoading(false);
  };

  const copy = () => {
    navigator.clipboard.writeText(`https://${result}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  return (
    <Box sx={{ maxWidth: 520, mx: "auto", mt: 6,
      animation: "fadeInUp 0.7s ease 0.4s both" }}>
      {/* Input row */}
      <Box component="form" onSubmit={submit} sx={{
        display: "flex", height: 50,
        background: C.surface,
        border: `1px solid ${C.border}`,
        borderRadius: "10px", overflow: "hidden",
        transition: "border-color 0.2s",
        "&:focus-within": { borderColor: "rgba(56,189,248,0.4)" },
      }}>
        <Box sx={{ display: "flex", alignItems: "center", pl: 1.8, flexShrink: 0 }}>
          <LinkIcon size={14} color={C.muted} />
        </Box>
        <InputBase
          value={url}
          onChange={(e) => { setUrl(e.target.value); setResult(null); }}
          placeholder="Paste a long URL…"
          sx={{
            flex: 1, px: 1.5, fontSize: "0.83rem",
            fontFamily: "'IBM Plex Mono', monospace", color: C.text,
            "& input::placeholder": { color: C.muted },
          }}
        />
        <Button type="submit" variant="contained"
          disabled={loading || !url.trim()}
          sx={{
            height: "100%", borderRadius: 0, px: 3, flexShrink: 0,
            background: C.accent, color: "#080C10",
            fontWeight: 700, fontSize: "0.8rem",
            "&:hover": { background: "#7DD3FC" },
            "&.Mui-disabled": { background: C.border, color: C.muted },
            transition: "background 0.15s",
          }}>
          {loading
            ? <CircularProgress size={13} sx={{ color: "#080C10" }} />
            : "Shorten"
          }
        </Button>
      </Box>

      {/* Result */}
      {result && (
        <Box sx={{
          mt: 1.5, px: 2, py: 1.4,
          background: "rgba(56,189,248,0.04)",
          border: `1px solid rgba(56,189,248,0.18)`,
          borderRadius: "8px",
          display: "flex", alignItems: "center", justifyContent: "space-between",
          animation: "fadeInUp 0.3s ease both",
        }}>
          <Typography sx={{ fontFamily: "'IBM Plex Mono', monospace",
            color: C.accent, fontSize: "0.88rem" }}>
            https://{result}
          </Typography>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
            <Button size="small" onClick={copy}
              startIcon={copied ? <Check size={12} /> : <Copy size={12} />}
              sx={{ color: copied ? "#34D399" : C.muted, fontSize: "0.7rem",
                fontFamily: "'IBM Plex Mono', monospace", minWidth: 0, px: 0.5,
                "&:hover": { color: C.text, background: "transparent" } }}>
              {copied ? "Copied" : "Copy"}
            </Button>
            <Box sx={{ width: "1px", height: 16, background: C.border }} />
            <Button size="small" onClick={onGetStarted}
              sx={{ color: C.muted, fontSize: "0.7rem",
                fontFamily: "'IBM Plex Mono', monospace", px: 0.5,
                "&:hover": { color: C.accent, background: "transparent" } }}>
              Save →
            </Button>
          </Box>
        </Box>
      )}

      <Typography sx={{ textAlign: "center", mt: 1.5,
        fontSize: "0.66rem", color: C.subtle,
        fontFamily: "'IBM Plex Mono', monospace" }}>
        Demo only · sign up to persist and track your links
      </Typography>
    </Box>
  );
}

// ─── LandingPage ──────────────────────────────────────────────────────────────
export default function LandingPage({ onGetStarted }) {
  const heroRef    = useRef(null);
  const contentRef = useRef(null);

  const onMove = useCallback((e) => {
    const r = heroRef.current?.getBoundingClientRect();
    if (!r || !contentRef.current) return;
    const x = (e.clientX - r.left) / r.width  - 0.5;
    const y = (e.clientY - r.top)  / r.height - 0.5;
    contentRef.current.style.transform =
      `perspective(1400px) rotateX(${-y * 2}deg) rotateY(${x * 2}deg)`;
  }, []);

  const onLeave = useCallback(() => {
    if (contentRef.current)
      contentRef.current.style.transform =
        "perspective(1400px) rotateX(0deg) rotateY(0deg)";
  }, []);

  return (
    <Box sx={{ minHeight: "100vh", background: C.bg,
      overflowX: "hidden", animation: "pageIn 0.45s ease both" }}>

      {/* ── Navbar ───────────────────────────────────────── */}
      <Box component="nav" sx={{
        position: "fixed", top: 0, left: 0, right: 0, zIndex: 200,
        px: { xs: 3, md: 8 }, py: 1.8,
        display: "flex", alignItems: "center", justifyContent: "space-between",
        background: "rgba(8,12,16,0.9)", backdropFilter: "blur(20px)",
        borderBottom: `1px solid ${C.border}`,
      }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
          <Zap size={15} color={C.accent} />
          <Typography sx={{ color: C.accent, letterSpacing: "0.1em",
            fontSize: "0.85rem", fontFamily: "'Syne', sans-serif", fontWeight: 700 }}>
            TrimIt
          </Typography>
        </Box>

        <Box sx={{ display: "flex", gap: 1.5, alignItems: "center" }}>
          <Button variant="text" size="small" onClick={onGetStarted}
            sx={{ color: C.muted, fontSize: "0.78rem",
              "&:hover": { color: C.text, background: "transparent" } }}>
            Sign in
          </Button>
          <Button variant="contained" size="small" onClick={onGetStarted}
            sx={{
              background: C.accent, color: "#080C10", fontWeight: 700,
              borderRadius: "8px", px: 2,
              "&:hover": { background: "#7DD3FC" },
              transition: "background 0.15s",
            }}>
            Get started
          </Button>
        </Box>
      </Box>

      {/* ── Hero ─────────────────────────────────────────── */}
      <Box ref={heroRef} onMouseMove={onMove} onMouseLeave={onLeave}
        sx={{ minHeight: "100vh", display: "flex", alignItems: "center",
          justifyContent: "center", position: "relative", pt: 10, pb: 8 }}>

        {/* Single, very subtle radial glow — no orb zoo */}
        <Box sx={{
          position: "absolute", top: "30%", left: "50%",
          transform: "translate(-50%, -50%)",
          width: 700, height: 500,
          background: "radial-gradient(ellipse, rgba(56,189,248,0.05) 0%, transparent 60%)",
          pointerEvents: "none",
        }} />

        {/* Grid — extremely faint */}
        <Box sx={{
          position: "absolute", inset: 0, pointerEvents: "none",
          backgroundImage: [
            "linear-gradient(rgba(56,189,248,0.025) 1px, transparent 1px)",
            "linear-gradient(90deg, rgba(56,189,248,0.025) 1px, transparent 1px)",
          ].join(","),
          backgroundSize: "72px 72px",
          maskImage:
            "radial-gradient(ellipse 70% 70% at 50% 40%, black 30%, transparent 100%)",
        }} />

        {/* Floating preview cards */}
        {CARDS.map((c, i) => <FloatingCard key={i} {...c} />)}

        {/* Content */}
        <Box ref={contentRef} sx={{
          position: "relative", zIndex: 10,
          textAlign: "center", px: { xs: 2, md: 4 }, maxWidth: 820,
          transition: "transform 0.2s ease",
        }}>
          {/* Badge */}
{/*           <Box sx={{ */}
{/*             display: "inline-flex", alignItems: "center", gap: 1.5, */}
{/*             mb: 5, px: 2.5, py: "7px", */}
{/*             border: `1px solid ${C.border}`, */}
{/*             borderRadius: "20px", */}
{/*             animation: "fadeInDown 0.65s ease both", */}
{/*           }}> */}
{/*             <Box sx={{ width: 6, height: 6, borderRadius: "50%", */}
{/*               background: "#34D399", animation: "pulseDot 2.5s ease-in-out infinite" }} /> */}
{/*             <Typography sx={{ fontSize: "0.7rem", color: C.muted, */}
{/*               fontFamily: "'IBM Plex Mono', monospace", letterSpacing: "0.06em" }}> */}
{/*               Spring Boot · Redis · RabbitMQ · React 19 */}
{/*             </Typography> */}
{/*           </Box> */}

          {/* Headline — two white lines, one accent line */}
          <Typography variant="h1" sx={{
            fontSize: { xs: "3.2rem", sm: "4.5rem", md: "6rem", lg: "7rem" },
            lineHeight: 0.95, fontWeight: 800,
            letterSpacing: "-0.03em", mb: 4,
            animation: "fadeInUp 0.75s ease 0.08s both",
          }}>
            <Box component="span" sx={{ display: "block", color: C.text }}>
              Shorten.
            </Box>
            <Box component="span" sx={{ display: "block", color: C.accent }}>
              Share.
            </Box>
            <Box component="span" sx={{ display: "block", color: C.text }}>
              Track.
            </Box>
          </Typography>

          {/* Subtext */}
          <Typography sx={{
            fontSize: { xs: "0.88rem", md: "1rem" },
            color: C.body, maxWidth: 460, mx: "auto",
            lineHeight: 1.85, mb: 5,
            fontFamily: "'IBM Plex Mono', monospace",
            animation: "fadeInUp 0.75s ease 0.18s both",
          }}>
            Transform long URLs into clean short links — with analytics,
            UTM tracking, password protection, and QR codes.
          </Typography>

          {/* CTA */}
          <Box sx={{ display: "flex", gap: 2, justifyContent: "center",
            flexWrap: "wrap", animation: "fadeInUp 0.75s ease 0.28s both" }}>
            <Button variant="contained" size="large" onClick={onGetStarted}
              endIcon={<ArrowRight size={15} />}
              sx={{
                px: 4.5, py: "12px", fontSize: "0.9rem", fontWeight: 700,
                background: C.accent, color: "#080C10", borderRadius: "10px",
                "&:hover": { background: "#7DD3FC", transform: "translateY(-2px)" },
                transition: "all 0.18s ease",
              }}>
              Get started free
            </Button>
            <Button variant="outlined" size="large"
              onClick={() => document.getElementById("features")
                ?.scrollIntoView({ behavior: "smooth" })}
              sx={{
                px: 4, py: "12px", fontSize: "0.9rem",
                borderColor: C.border, color: C.muted, borderRadius: "10px",
                "&:hover": { borderColor: C.subtle, color: C.text,
                  background: "rgba(255,255,255,0.03)" },
                transition: "all 0.18s ease",
              }}>
              See features
            </Button>
          </Box>

          {/* Live shortener */}
          <HeroShortener onGetStarted={onGetStarted} />
        </Box>
      </Box>

      {/* ── Stats ────────────────────────────────────────── */}
      <Box sx={{
        borderTop: `1px solid ${C.border}`,
        borderBottom: `1px solid ${C.border}`,
        py: 5,
      }}>
        <Container maxWidth="sm">
          <Box sx={{ display: "flex", justifyContent: "center",
            gap: { xs: 8, md: 14 } }}>
            {STATS.map(({ value, label }) => (
              <Box key={label} sx={{ textAlign: "center" }}>
                <Typography sx={{
                  fontFamily: "'Syne', sans-serif", fontWeight: 800,
                  fontSize: { xs: "1.8rem", md: "2.4rem" },
                  color: C.text, lineHeight: 1, mb: 0.5,
                }}>
                  {value}
                </Typography>
                <Typography sx={{ fontSize: "0.64rem", color: C.muted,
                  fontFamily: "'IBM Plex Mono', monospace", letterSpacing: "0.12em" }}>
                  {label.toUpperCase()}
                </Typography>
              </Box>
            ))}
          </Box>
        </Container>
      </Box>

      {/* ── Features ─────────────────────────────────────── */}
      <Box id="features" sx={{ py: { xs: 10, md: 16 } }}>
        <Container maxWidth="lg">
          {/* Section heading */}
          <Box
            sx={{
              mb: { xs: 8, md: 12 },
              textAlign: "center",
            }}
          >
            <Typography
              sx={{
                fontSize: "0.65rem",
                fontFamily: "'IBM Plex Mono', monospace",
                color: C.accent,
                letterSpacing: "0.2em",
                mb: 2,
              }}
            >
              FEATURES
            </Typography>

            <Typography
              variant="h2"
              sx={{
                fontSize: { xs: "1.8rem", md: "2.4rem" },
                fontWeight: 700,
                color: C.text,
                letterSpacing: "-0.02em",
                maxWidth: 440,
                mx: "auto", // important for centering constrained width
              }}
            >
              Everything in one place
            </Typography>
          </Box>

          {/* Grid */}
          <Box sx={{
            display: "grid",
            gridTemplateColumns: { xs: "1fr", sm: "repeat(2,1fr)", md: "repeat(3,1fr)" },
            gap: "1px",
            border: `1px solid ${C.border}`,
            borderRadius: "12px", overflow: "hidden",
          }}>
            {FEATURES.map((f, i) => (
              <Box key={f.title} sx={{
                p: { xs: "28px 24px", md: "36px 32px" },
                background: C.surface,
                borderRight: { md: (i + 1) % 3 !== 0 ? `1px solid ${C.border}` : "none" },
                borderBottom: { xs: i < 5 ? `1px solid ${C.border}` : "none",
                  md: i < 3 ? `1px solid ${C.border}` : "none" },
                transition: "background 0.2s",
                "&:hover": { background: "rgba(56,189,248,0.02)" },
              }}>
                <Box sx={{ color: C.accent, mb: 3, opacity: 0.85 }}>
                  <f.icon size={20} strokeWidth={1.5} />
                </Box>
                <Typography sx={{ fontFamily: "'Syne', sans-serif",
                  fontWeight: 700, fontSize: "0.95rem", color: C.text, mb: 1.5 }}>
                  {f.title}
                </Typography>
                <Typography sx={{ fontFamily: "'IBM Plex Mono', monospace",
                  fontSize: "0.76rem", color: C.muted, lineHeight: 1.8 }}>
                  {f.desc}
                </Typography>
              </Box>
            ))}
          </Box>
        </Container>
      </Box>

      {/* ── How it works ─────────────────────────────────── */}
      <Box sx={{
        py: { xs: 10, md: 16 },
        borderTop: `1px solid ${C.border}`,
      }}>
        <Container maxWidth="md">
          <Box sx={{ textAlign: "center", mb: { xs: 8, md: 12 } }}>
            <Typography sx={{ fontSize: "0.65rem",
              fontFamily: "'IBM Plex Mono', monospace",
              color: C.accent, letterSpacing: "0.2em", mb: 2 }}>
              HOW IT WORKS
            </Typography>
            <Typography variant="h2" sx={{
              fontSize: { xs: "1.8rem", md: "2.4rem" },
              fontWeight: 700, color: C.text, letterSpacing: "-0.02em",
            }}>
              Four steps
            </Typography>
          </Box>

          {/* Steps with connecting line */}
          <Box sx={{ position: "relative" }}>
            {/* Connecting line (desktop only) */}
            <Box sx={{
              display: { xs: "none", md: "block" },
              position: "absolute", top: 20, left: "12.5%", right: "12.5%",
              height: "1px", background: C.border, zIndex: 0,
            }} />
            <Box sx={{ display: "flex", gap: { xs: 5, md: 0 }, flexWrap: "wrap" }}>
              {STEPS.map((s, i) => (
                <Box key={s.n} sx={{
                  flex: { xs: "0 0 50%", md: 1 }, textAlign: "center",
                  position: "relative", zIndex: 1,
                }}>
                  {/* Number circle */}
                  <Box sx={{
                    width: 40, height: 40, borderRadius: "50%",
                    border: `1px solid ${C.border}`,
                    background: C.surface,
                    display: "flex", alignItems: "center", justifyContent: "center",
                    mx: "auto", mb: 2.5,
                  }}>
                    <Typography sx={{ fontFamily: "'IBM Plex Mono', monospace",
                      fontSize: "0.68rem", color: i === 0 ? C.accent : C.muted,
                      fontWeight: 600 }}>
                      {s.n}
                    </Typography>
                  </Box>
                  <Typography sx={{ fontFamily: "'Syne', sans-serif",
                    fontWeight: 700, fontSize: "0.9rem", color: C.text, mb: 0.5 }}>
                    {s.title}
                  </Typography>
                  <Typography sx={{ fontFamily: "'IBM Plex Mono', monospace",
                    color: C.muted, fontSize: "0.72rem" }}>
                    {s.desc}
                  </Typography>
                </Box>
              ))}
            </Box>
          </Box>
        </Container>
      </Box>

      {/* ── Tech strip ───────────────────────────────────── */}
{/*       <Box sx={{ borderTop: `1px solid ${C.border}`, py: 3.5 }}> */}
{/*         <Container maxWidth="md"> */}
{/*           <Box sx={{ display: "flex", justifyContent: "center", */}
{/*             gap: { xs: 3, md: 6 }, flexWrap: "wrap" }}> */}
{/*             {["Spring Boot 3", "MySQL 8", "Redis", "RabbitMQ", "Docker", "Prometheus"].map((t) => ( */}
{/*               <Typography key={t} sx={{ */}
{/*                 fontFamily: "'IBM Plex Mono', monospace", */}
{/*                 fontSize: "0.68rem", color: C.subtle, letterSpacing: "0.04em", */}
{/*               }}> */}
{/*                 {t} */}
{/*               </Typography> */}
{/*             ))} */}
{/*           </Box> */}
{/*         </Container> */}
{/*       </Box> */}

      {/* ── CTA ──────────────────────────────────────────── */}
      <Box sx={{
        borderTop: `1px solid ${C.border}`,
        py: { xs: 12, md: 20 },
        textAlign: "center",
        position: "relative", overflow: "hidden",
      }}>
        {/* Very subtle center glow */}
        <Box sx={{
          position: "absolute", top: "50%", left: "50%",
          transform: "translate(-50%,-50%)",
          width: 500, height: 300,
          background: "radial-gradient(ellipse, rgba(56,189,248,0.05) 0%, transparent 70%)",
          pointerEvents: "none",
        }} />

        <Container maxWidth="xs" sx={{ position: "relative", zIndex: 1 }}>
          <Typography variant="h2" sx={{
            fontSize: { xs: "1.9rem", md: "2.6rem" },
            fontWeight: 800, color: C.text, mb: 2,
            letterSpacing: "-0.025em",
          }}>
            Start for free
          </Typography>
          <Typography sx={{ color: C.muted, mb: 4,
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "0.82rem", lineHeight: 1.7 }}>
            No credit card. Full analytics from day one.
          </Typography>
          <Button variant="contained" size="large" onClick={onGetStarted}
            endIcon={<ChevronRight size={16} />}
            sx={{
              px: 5, py: "13px", fontSize: "0.92rem", fontWeight: 700,
              background: C.accent, color: "#080C10", borderRadius: "10px",
              "&:hover": { background: "#7DD3FC", transform: "translateY(-2px)" },
              transition: "all 0.18s ease",
            }}>
            Create your first link
          </Button>
        </Container>
      </Box>

      {/* ── Footer ───────────────────────────────────────── */}
      <Box sx={{ borderTop: `1px solid ${C.border}`, py: 4 }}>
        <Container maxWidth="lg">
          <Box sx={{ display: "flex", justifyContent: "space-between",
            alignItems: "center", flexWrap: "wrap", gap: 2 }}>
            <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
              <Zap size={13} color={C.accent} />
              <Typography sx={{ fontSize: "0.72rem", color: C.muted,
                fontFamily: "'IBM Plex Mono', monospace", letterSpacing: "0.06em" }}>
                TrimIt
              </Typography>
            </Box>
            <Typography sx={{ fontSize: "0.66rem", color: C.subtle,
              fontFamily: "'IBM Plex Mono', monospace" }}>
              © 2026 TrimIt · Spring Boot + React 19
            </Typography>
            <Box sx={{ display: "flex", gap: 3 }}>
              <Typography
                component="a"
                href="https://github.com/trimoyee-g/url-shortener"
                target="_blank"
                rel="noopener noreferrer"
                sx={{
                  fontSize: "0.66rem",
                  color: C.muted,
                  fontFamily: "'IBM Plex Mono', monospace",
                  cursor: "pointer",
                  textDecoration: "none",
                  "&:hover": { color: C.text },
                  transition: "color 0.15s",
                }}
              >
                GitHub
              </Typography>
            </Box>
          </Box>
        </Container>
      </Box>
    </Box>
  );
}
