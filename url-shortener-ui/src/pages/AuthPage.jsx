import { useState } from "react";
import {
  Box, Typography, Stack, TextField, Alert,
  Button, CircularProgress,
} from "@mui/material";
import { Zap, ArrowRight, ArrowLeft, Shield } from "lucide-react";
import { api } from "../services/api";

// ─── Validation ───────────────────────────────────────────────────────────────
const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function getPasswordError(pw) {
  if (!pw)              return "Password is required";
  if (pw.length < 8)   return "Use at least 8 characters";
  if (!/[A-Z]/.test(pw)) return "Include at least one uppercase letter";
  if (!/[a-z]/.test(pw)) return "Include at least one lowercase letter";
  if (!/[0-9]/.test(pw)) return "Include at least one number";
  if (!/[^A-Za-z0-9]/.test(pw)) return "Include a special character (e.g. !@#$)";
  return "";
}

function validate(isRegister, email, password, name) {
  const e = {};
  if (isRegister && !name.trim()) e.name = "Name is required";
  if (!email.trim())              e.email = "Email is required";
  else if (!emailRegex.test(email)) e.email = "Enter a valid email";
  const pwErr = isRegister ? getPasswordError(password) : (!password ? "Password is required" : "");
  if (pwErr) e.password = pwErr;
  return e;
}

// ─── Palette (same tokens as LandingPage) ────────────────────────────────────
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

// ─── AuthPage ─────────────────────────────────────────────────────────────────
export default function AuthPage({ onAuth, onBack }) {
  const [isRegister,   setIsRegister]   = useState(false);
  const [email,        setEmail]        = useState("");
  const [password,     setPassword]     = useState("");
  const [name,         setName]         = useState("");
  const [loading,      setLoading]      = useState(false);
  const [apiError,     setApiError]     = useState("");
  const [fieldErrors,  setFieldErrors]  = useState({});

  const switchMode = (toRegister) => {
    setIsRegister(toRegister);
    setApiError("");
    setFieldErrors({});
  };

  const clearField = (field) =>
    setFieldErrors((p) => ({ ...p, [field]: "" }));

  const submit = async () => {
    setApiError("");
    const errors = validate(isRegister, email, password, name);
    if (Object.keys(errors).length) { setFieldErrors(errors); return; }
    setFieldErrors({});
    setLoading(true);
    try {
      const data = isRegister
        ? await api.register(email, password, name)
        : await api.login(email, password);
      onAuth(data.accessToken || data.token);
    } catch (err) {
      setApiError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{
      minHeight: "100vh",
      display: "flex",
      background: C.bg,
      position: "relative",
      overflow: "hidden",
      animation: "pageIn 0.4s ease both",
    }}>

      {/* ── Background ──────────────────────────────────────── */}
      {/* Single faint glow */}
      <Box sx={{
        position: "absolute", top: "35%", left: "50%",
        transform: "translate(-50%, -50%)",
        width: 600, height: 500,
        background: "radial-gradient(ellipse, rgba(56,189,248,0.055) 0%, transparent 65%)",
        pointerEvents: "none",
      }} />

      {/* Grid with fade mask */}
      <Box sx={{
        position: "absolute", inset: 0, pointerEvents: "none",
        backgroundImage: [
          "linear-gradient(rgba(56,189,248,0.022) 1px, transparent 1px)",
          "linear-gradient(90deg, rgba(56,189,248,0.022) 1px, transparent 1px)",
        ].join(","),
        backgroundSize: "72px 72px",
        maskImage:
          "radial-gradient(ellipse 60% 60% at 50% 40%, black 20%, transparent 100%)",
      }} />

      {/* ── Left panel (desktop only) ───────────────────────── */}
      <Box sx={{
        display: { xs: "none", lg: "flex" },
        flexDirection: "column",
        justifyContent: "space-between",
        width: 420, flexShrink: 0,
        borderRight: `1px solid ${C.border}`,
        px: 6, py: 5,
        position: "relative", zIndex: 1,
      }}>
        {/* Logo */}
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
          <Zap size={15} color={C.accent} />
          <Typography sx={{ color: C.accent, fontFamily: "'Syne', sans-serif",
            fontWeight: 700, letterSpacing: "0.1em", fontSize: "0.85rem" }}>
            TrimIt
          </Typography>
        </Box>

        {/* Middle copy */}
        <Box>
          <Typography variant="h2" sx={{
            fontSize: "2.2rem", fontWeight: 800,
            color: C.text, lineHeight: 1.15,
            letterSpacing: "-0.025em", mb: 2,
          }}>
            Short links.<br />
            Real insights.
          </Typography>
          <Typography sx={{
            fontFamily: "'IBM Plex Mono', monospace",
            color: C.muted, fontSize: "0.82rem", lineHeight: 1.8,
            mb: 4, maxWidth: 300,
          }}>
            Shorten, track, and protect your links — with analytics that
            actually tell you something.
          </Typography>

          {/* Feature list */}
          {[
            "Real-time click analytics",
            "Country & device breakdown",
            "UTM parameter injection",
            "Password-protected links",
            "QR code generation",
          ].map((f) => (
            <Box key={f} sx={{ display: "flex", alignItems: "center",
              gap: 1.5, mb: 1.5 }}>
              <Box sx={{ width: 4, height: 4, borderRadius: "50%",
                background: C.accent, flexShrink: 0 }} />
              <Typography sx={{ fontFamily: "'IBM Plex Mono', monospace",
                fontSize: "0.76rem", color: C.body }}>
                {f}
              </Typography>
            </Box>
          ))}
        </Box>

        {/* Footer */}
        <Typography sx={{ fontFamily: "'IBM Plex Mono', monospace",
          fontSize: "0.65rem", color: C.subtle }}>
          © 2026 TrimIt
        </Typography>
      </Box>

      {/* ── Right panel — form ──────────────────────────────── */}
      <Box sx={{
        flex: 1,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        px: { xs: 2, sm: 4 },
        py: 6,
        position: "relative", zIndex: 1,
      }}>

        {/* Back link */}
        {onBack && (
          <Box sx={{ position: "absolute", top: 24, left: { xs: 16, lg: 32 } }}>
            <Button startIcon={<ArrowLeft size={13} />} onClick={onBack}
              sx={{ color: C.muted, fontSize: "0.76rem",
                fontFamily: "'IBM Plex Mono', monospace", pl: 0,
                "&:hover": { color: C.text, background: "transparent" } }}>
              Back
            </Button>
          </Box>
        )}

        {/* Mobile logo */}
        <Box sx={{ display: { xs: "flex", lg: "none" }, alignItems: "center",
          gap: 1.5, mb: 6 }}>
          <Zap size={15} color={C.accent} />
          <Typography sx={{ color: C.accent, fontFamily: "'Syne', sans-serif",
            fontWeight: 700, letterSpacing: "0.1em", fontSize: "0.85rem" }}>
            TrimIt
          </Typography>
        </Box>

        {/* Form card */}
        <Box sx={{ width: "100%", maxWidth: 380 }}>

          {/* Heading */}
          <Box sx={{ mb: 5 }}>
            <Typography variant="h3" sx={{
              fontSize: "1.75rem", fontWeight: 700,
              color: C.text, letterSpacing: "-0.02em", mb: 1,
            }}>
              {isRegister ? "Create account" : "Welcome back"}
            </Typography>
            <Typography sx={{ fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "0.8rem", color: C.muted }}>
              {isRegister
                ? "Start shortening links for free"
                : "Sign in to your TrimIt account"}
            </Typography>
          </Box>

          {/* Mode toggle — minimal segmented control */}
          <Box sx={{
            display: "flex", mb: 4,
            background: C.surface,
            border: `1px solid ${C.border}`,
            borderRadius: "8px", p: "3px",
          }}>
            {[
              { label: "Sign in",  value: false },
              { label: "Register", value: true  },
            ].map(({ label, value }) => (
              <Box key={label} onClick={() => switchMode(value)} sx={{
                flex: 1, textAlign: "center",
                py: "7px", borderRadius: "6px", cursor: "pointer",
                background: isRegister === value
                  ? "rgba(56,189,248,0.1)" : "transparent",
                border: isRegister === value
                  ? `1px solid rgba(56,189,248,0.2)` : "1px solid transparent",
                transition: "all 0.18s",
              }}>
                <Typography sx={{
                  fontFamily: "'IBM Plex Mono', monospace",
                  fontSize: "0.76rem", fontWeight: 600,
                  color: isRegister === value ? C.accent : C.muted,
                  letterSpacing: "0.04em",
                  transition: "color 0.18s",
                }}>
                  {label}
                </Typography>
              </Box>
            ))}
          </Box>

          {/* Fields */}
          <Stack spacing={2.5}>
            {isRegister && (
              <TextField
                label="Name"
                value={name}
                onChange={(e) => { setName(e.target.value); clearField("name"); }}
                fullWidth size="small"
                error={Boolean(fieldErrors.name)}
                helperText={fieldErrors.name}
                autoComplete="name"
              />
            )}

            <TextField
              label="Email"
              type="email"
              value={email}
              onChange={(e) => { setEmail(e.target.value); clearField("email"); }}
              fullWidth size="small"
              error={Boolean(fieldErrors.email)}
              helperText={fieldErrors.email}
              autoComplete="email"
            />

            <TextField
              label="Password"
              type="password"
              value={password}
              onChange={(e) => { setPassword(e.target.value); clearField("password"); }}
              onKeyDown={(e) => e.key === "Enter" && submit()}
              fullWidth size="small"
              error={Boolean(fieldErrors.password)}
              helperText={
                fieldErrors.password ||
                (isRegister ? "8+ chars · upper + lower · number · symbol" : "")
              }
              autoComplete={isRegister ? "new-password" : "current-password"}
            />

            {apiError && (
              <Alert severity="error" sx={{
                fontSize: "0.74rem", fontFamily: "'IBM Plex Mono', monospace",
                borderRadius: "8px",
                "& .MuiAlert-icon": { fontSize: "1rem" },
              }}>
                {apiError}
              </Alert>
            )}

            <Button
              variant="contained"
              fullWidth size="large"
              onClick={submit}
              disabled={loading}
              endIcon={
                loading
                  ? <CircularProgress size={14} sx={{ color: "#080C10" }} />
                  : <ArrowRight size={14} />
              }
              sx={{
                mt: 0.5, py: 1.5,
                borderRadius: "8px", fontWeight: 700,
                background: C.accent, color: "#080C10",
                fontSize: "0.88rem",
                "&:hover": { background: "#7DD3FC", transform: "translateY(-1px)" },
                "&.Mui-disabled": { background: C.border, color: C.muted },
                transition: "all 0.18s ease",
              }}
            >
              {isRegister ? "Create account" : "Sign in"}
            </Button>
          </Stack>

          {/* Switch mode link */}
          <Box sx={{ mt: 4, display: "flex", alignItems: "center",
            justifyContent: "center", gap: 1 }}>
            <Typography sx={{ fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "0.75rem", color: C.muted }}>
              {isRegister ? "Already have an account?" : "Don't have an account?"}
            </Typography>
            <Button size="small" onClick={() => switchMode(!isRegister)}
              sx={{ color: C.accent, fontSize: "0.75rem", px: 0.5,
                fontFamily: "'IBM Plex Mono', monospace", minWidth: 0,
                "&:hover": { background: "transparent", color: "#7DD3FC" } }}>
              {isRegister ? "Sign in" : "Register"}
            </Button>
          </Box>

          {/* Trust note */}
          <Box sx={{ mt: 3, display: "flex", alignItems: "center",
            justifyContent: "center", gap: 1 }}>
            <Shield size={11} color={C.subtle} />
            <Typography sx={{ fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "0.65rem", color: C.subtle }}>
              JWT secured · BCrypt encrypted
            </Typography>
          </Box>
        </Box>
      </Box>
    </Box>
  );
}
