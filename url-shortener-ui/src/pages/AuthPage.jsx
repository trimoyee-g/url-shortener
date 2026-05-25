import React, { useState, useRef, useCallback } from "react";
import {
  Box, Typography, Card, Tabs, Tab, Stack,
  TextField, Alert, Button, CircularProgress,
} from "@mui/material";
import { Zap, ArrowRight, Shield, ArrowLeft } from "lucide-react";
import { api } from "../services/api";

// ─── Validation ────────────────────────────────────────────────────────────

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function getPasswordError(pw) {
  if (!pw) return "Password is required";
  if (pw.length < 8) return "Use at least 8 characters";
  if (!/[A-Z]/.test(pw)) return "Include at least one uppercase letter";
  if (!/[a-z]/.test(pw)) return "Include at least one lowercase letter";
  if (!/[0-9]/.test(pw)) return "Include at least one number";
  if (!/[^A-Za-z0-9]/.test(pw)) return "Include at least one special character (e.g. !@#$%)";
  return "";
}

function validate(tab, email, password, name) {
  const e = {};
  if (tab === 1 && !name.trim()) e.name = "Name is required";
  if (!email.trim()) {
    e.email = "Email is required";
  } else if (!emailRegex.test(email)) {
    e.email = "Enter a valid email address";
  }
  const pwErr = tab === 1 ? getPasswordError(password) : (!password ? "Password is required" : "");
  if (pwErr) e.password = pwErr;
  return e;
}

// ─── Static particles (computed once) ─────────────────────────────────────

const AUTH_PARTICLES = Array.from({ length: 22 }, (_, i) => ({
  id: i,
  x: (i * 41.3 + 17) % 100,
  y: (i * 61.7 + 9)  % 100,
  size: 1 + (i % 3) * 0.5,
  delay: (i * 0.35) % 4,
  duration: 4 + (i % 4),
}));

// ─── AuthPage ──────────────────────────────────────────────────────────────

export default function AuthPage({ onAuth, onBack }) {
  const [tab,         setTab]         = useState(0);
  const [email,       setEmail]       = useState("");
  const [password,    setPassword]    = useState("");
  const [name,        setName]        = useState("");
  const [loading,     setLoading]     = useState(false);
  const [apiError,    setApiError]    = useState("");
  const [fieldErrors, setFieldErrors] = useState({});

  // 3-D card tilt via direct DOM refs (no re-render on mouse move)
  const cardRef = useRef(null);

  const onCardMouseMove = useCallback((e) => {
    const el = cardRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width  - 0.5;
    const y = (e.clientY - r.top)  / r.height - 0.5;
    el.style.transform = `perspective(1000px) rotateY(${x * 10}deg) rotateX(${-y * 10}deg)`;
    el.style.boxShadow =
      `${-x * 14}px ${y * 14}px 50px rgba(0,0,0,0.5),
       0 0 40px rgba(56,189,248,0.08)`;
  }, []);

  const onCardMouseLeave = useCallback(() => {
    const el = cardRef.current;
    if (!el) return;
    el.style.transform = "perspective(1000px) rotateY(0deg) rotateX(0deg)";
    el.style.boxShadow = "0 8px 40px rgba(0,0,0,0.35)";
  }, []);

  const handleTabChange = (_, v) => {
    setTab(v);
    setApiError("");
    setFieldErrors({});
  };

  const submit = async () => {
    setApiError("");
    const errors = validate(tab, email, password, name);
    if (Object.keys(errors).length > 0) { setFieldErrors(errors); return; }
    setFieldErrors({});
    setLoading(true);
    try {
      const data =
        tab === 0
          ? await api.login(email, password)
          : await api.register(email, password, name);
      onAuth(data.accessToken || data.token);
    } catch (err) {
      setApiError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "#080C10",
        position: "relative",
        overflow: "hidden",
        animation: "pageIn 0.45s ease both",
      }}
    >
      {/* ── Background ─────────────────────────────────────── */}

      {/* Glow orbs */}
      <Box sx={{
        position: "absolute", top: "20%", left: "50%",
        transform: "translate(-50%,-50%)",
        width: 600, height: 600,
        background: "radial-gradient(circle, rgba(56,189,248,0.07) 0%, transparent 65%)",
        borderRadius: "50%",
        animation: "pulseSlow 5s ease-in-out infinite",
        pointerEvents: "none",
      }} />
      <Box sx={{
        position: "absolute", bottom: "10%", left: "15%",
        width: 300, height: 300,
        background: "radial-gradient(circle, rgba(59,130,246,0.06) 0%, transparent 65%)",
        borderRadius: "50%",
        animation: "pulseSlow 7s ease-in-out infinite 3s",
        pointerEvents: "none",
      }} />
      <Box sx={{
        position: "absolute", top: "30%", right: "10%",
        width: 260, height: 260,
        background: "radial-gradient(circle, rgba(124,58,237,0.06) 0%, transparent 65%)",
        borderRadius: "50%",
        animation: "pulseSlow 6s ease-in-out infinite 1.5s",
        pointerEvents: "none",
      }} />

      {/* Grid */}
      <Box sx={{
        position: "absolute", inset: 0, pointerEvents: "none",
        backgroundImage: [
          "linear-gradient(rgba(56,189,248,0.025) 1px, transparent 1px)",
          "linear-gradient(90deg, rgba(56,189,248,0.025) 1px, transparent 1px)",
        ].join(","),
        backgroundSize: "60px 60px",
      }} />

      {/* Particles */}
      {AUTH_PARTICLES.map((p) => (
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

      {/* ── Card wrapper ───────────────────────────────────── */}
      <Box
        sx={{
          width: "100%",
          maxWidth: 440,
          px: 2,
          position: "relative",
          zIndex: 1,
        }}
      >
        {/* Back link */}
        {onBack && (
          <Button
            startIcon={<ArrowLeft size={14} />}
            onClick={onBack}
            sx={{
              color: "#E6EDF3", fontSize: "0.78rem", mb: 2,
              fontFamily: "'IBM Plex Mono', monospace",
              "&:hover": { color: "#E6EDF3", background: "transparent" },
              pl: 0,
            }}
          >
            Back to home
          </Button>
        )}

        {/* Logo */}
        <Box sx={{ textAlign: "center", mb: 4 }}>
          <Box
            sx={{
              display: "inline-flex",
              alignItems: "center",
              gap: 1.5,
              px: 2.5, py: 1,
              background: "rgba(56,189,248,0.06)",
              border: "1px solid rgba(56,189,248,0.22)",
              borderRadius: "10px",
              mb: 3,
              boxShadow: "0 0 20px rgba(56,189,248,0.1)",
            }}
          >
            <Zap size={16} color="#38BDF8" />
            <Typography
              sx={{
                color: "#38BDF8",
                letterSpacing: "0.12em",
                fontSize: "0.85rem",
                fontFamily: "'Syne', sans-serif",
                fontWeight: 700,
              }}
            >
              TrimIt
            </Typography>
          </Box>

          <Typography
            variant="h3"
            sx={{ fontSize: "1.9rem", mb: 0.75, letterSpacing: "-0.01em" }}
          >
            {tab === 0 ? "Welcome back" : "Get started"}
          </Typography>
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ fontFamily: "'IBM Plex Mono', monospace", fontSize: "0.82rem" }}
          >
            {tab === 0 ? "Sign in to your account" : "Create a free account"}
          </Typography>
        </Box>

        {/* 3-D tilt card */}
        <Box
          ref={cardRef}
          onMouseMove={onCardMouseMove}
          onMouseLeave={onCardMouseLeave}
          sx={{
            background: "rgba(13,17,23,0.88)",
            border: "1px solid #21262D",
            borderRadius: "18px",
            p: 3.5,
            backdropFilter: "blur(28px)",
            boxShadow: "0 8px 40px rgba(0,0,0,0.35)",
            transition: "transform 0.14s ease, box-shadow 0.18s ease",
            position: "relative",
            overflow: "hidden",
            /* top shimmer line */
            "&::before": {
              content: '""',
              position: "absolute",
              top: 0, left: 0, right: 0,
              height: "1px",
              background:
                "linear-gradient(90deg, transparent, rgba(56,189,248,0.4), transparent)",
            },
          }}
        >
          {/* Tab switcher */}
          <Tabs
            value={tab}
            onChange={handleTabChange}
            sx={{
              mb: 3,
              "& .MuiTab-root": {
                fontFamily: "'IBM Plex Mono', monospace",
                fontSize: "0.78rem",
                color: "#7D8590",
                letterSpacing: "0.06em",
              },
              "& .Mui-selected":      { color: "#38BDF8 !important" },
              "& .MuiTabs-indicator": { backgroundColor: "#38BDF8" },
            }}
          >
            <Tab label="LOGIN"    />
            <Tab label="REGISTER" />
          </Tabs>

          <Stack spacing={2}>
            {tab === 1 && (
              <TextField
                label="Name"
                value={name}
                onChange={(e) => {
                  setName(e.target.value);
                  if (fieldErrors.name) setFieldErrors((p) => ({ ...p, name: "" }));
                }}
                fullWidth size="small"
                error={Boolean(fieldErrors.name)}
                helperText={fieldErrors.name}
              />
            )}

            <TextField
              label="Email"
              type="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (fieldErrors.email) setFieldErrors((p) => ({ ...p, email: "" }));
              }}
              fullWidth size="small"
              error={Boolean(fieldErrors.email)}
              helperText={fieldErrors.email}
            />

            <TextField
              label="Password"
              type="password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                if (fieldErrors.password) setFieldErrors((p) => ({ ...p, password: "" }));
              }}
              fullWidth size="small"
              onKeyDown={(e) => e.key === "Enter" && submit()}
              error={Boolean(fieldErrors.password)}
              helperText={
                fieldErrors.password ||
                (tab === 1 ? "8+ chars · upper & lower · number · special" : "")
              }
            />

            {apiError && (
              <Alert
                severity="error"
                sx={{
                  fontSize: "0.75rem",
                  fontFamily: "'IBM Plex Mono', monospace",
                  borderRadius: "10px",
                }}
              >
                {apiError}
              </Alert>
            )}

            <Button
              variant="contained"
              fullWidth
              size="large"
              onClick={submit}
              disabled={loading}
              endIcon={
                loading
                  ? <CircularProgress size={15} sx={{ color: "#080C10" }} />
                  : <ArrowRight size={15} />
              }
              sx={{
                mt: 1, py: 1.5,
                borderRadius: "10px",
                fontWeight: 700,
                letterSpacing: "0.03em",
                background: "linear-gradient(135deg,#38BDF8,#0f41a7)",
                color: "#080C10",
                boxShadow: "0 0 24px rgba(56,189,248,0.25)",
                "&:hover": {
                  background: "linear-gradient(135deg,#38BDF8,#0f41a7)",
                  boxShadow: "0 0 40px rgba(56,189,248,0.45)",
                  transform: "translateY(-1px)",
                },
                "&:disabled": {
                  background: "rgba(56,189,248,0.2)",
                  color: "rgba(8,12,16,0.5)",
                },
                transition: "all 0.2s ease",
              }}
            >
              {tab === 0 ? "Sign In" : "Create Account"}
            </Button>
          </Stack>
        </Box>

        {/* Footer note */}
        <Box
          sx={{
            textAlign: "center",
            mt: 3,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: 1,
          }}
        >
          <Shield size={11} color="#7D8590" />
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{ fontFamily: "'IBM Plex Mono', monospace" }}
          >
            JWT secured · Data encrypted
          </Typography>
        </Box>
      </Box>
    </Box>
  );
}
