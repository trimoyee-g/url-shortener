import React, { useState } from "react";
import {
  Box,
  Typography,
  Card,
  Tabs,
  Tab,
  Stack,
  TextField,
  Alert,
  Button,
  CircularProgress,
  Fade,
} from "@mui/material";
import { Zap, ArrowRight, Shield } from "lucide-react";
import { api } from "../services/api";

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function getPasswordError(password) {
  if (!password) return "Password is required";
  if (password.length < 8) return "Use at least 8 characters";
  if (!/[A-Z]/.test(password)) return "Include at least one uppercase letter";
  if (!/[a-z]/.test(password)) return "Include at least one lowercase letter";
  if (!/[0-9]/.test(password)) return "Include at least one number";
  if (!/[^A-Za-z0-9]/.test(password)) return "Include at least one special character (e.g. !@#$%)";
  return "";
}

function validate(tab, email, password, name) {
  const errors = {};
  if (tab === 1 && !name.trim()) errors.name = "Name is required";
  if (!email.trim()) {
    errors.email = "Email is required";
  } else if (!emailRegex.test(email)) {
    errors.email = "Enter a valid email address";
  }
  const pwErr = tab === 1 ? getPasswordError(password) : (!password ? "Password is required" : "");
  if (pwErr) errors.password = pwErr;
  return errors;
}

export default function AuthPage({ onAuth }) {
  const [tab, setTab] = useState(0);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});

  const handleTabChange = (_, v) => {
    setTab(v);
    setApiError("");
    setFieldErrors({});
  };

  const submit = async () => {
    setApiError("");
    const errors = validate(tab, email, password, name);
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});
    setLoading(true);
    try {
      const data =
        tab === 0
          ? await api.login(email, password)
          : await api.register(email, password, name);
      onAuth(data.accessToken || data.token);
    } catch (e) {
      setApiError(e.message);
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
        background:
          "radial-gradient(ellipse at 50% 0%, rgba(0,255,148,0.06) 0%, transparent 60%)",
        position: "relative",
        overflow: "hidden",
      }}
    >
      {/* Grid background */}
      <Box
        sx={{
          position: "absolute",
          inset: 0,
          opacity: 0.03,
          backgroundImage:
            "linear-gradient(#00FF94 1px, transparent 1px), linear-gradient(90deg, #00FF94 1px, transparent 1px)",
          backgroundSize: "40px 40px",
        }}
      />

      <Fade in timeout={600}>
        <Box
          sx={{
            width: "100%",
            maxWidth: 420,
            px: 2,
            position: "relative",
            zIndex: 1,
          }}
        >
          {/* Logo */}
          <Box sx={{ textAlign: "center", mb: 4 }}>
            <Box
              sx={{
                display: "inline-flex",
                alignItems: "center",
                gap: 1.5,
                border: "1px solid #21262D",
                borderRadius: 2,
                px: 2,
                py: 1,
                background: "rgba(0,255,148,0.04)",
              }}
            >
              <Zap size={18} color="#00FF94" />
              <Typography
                variant="h6"
                sx={{
                  color: "#00FF94",
                  letterSpacing: "0.1em",
                  fontSize: "0.9rem",
                }}
              >
                TrimIt
              </Typography>
            </Box>
            <Typography variant="h3" sx={{ mt: 3, mb: 1, fontSize: "2rem" }}>
              {tab === 0 ? "Welcome back" : "Get started"}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {tab === 0 ? "Sign in to your account" : "Create a free account"}
            </Typography>
          </Box>

          <Card
            sx={{
              p: 3,
              background: "rgba(13,17,23,0.8)",
              backdropFilter: "blur(20px)",
            }}
          >
            <Tabs
              value={tab}
              onChange={handleTabChange}
              sx={{
                mb: 3,
                "& .MuiTab-root": {
                  fontFamily: "'IBM Plex Mono', monospace",
                  fontSize: "0.8rem",
                  color: "#7D8590",
                },
                "& .Mui-selected": { color: "#00FF94 !important" },
                "& .MuiTabs-indicator": { backgroundColor: "#00FF94" },
              }}
            >
              <Tab label="LOGIN" />
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
                  fullWidth
                  size="small"
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
                fullWidth
                size="small"
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
                fullWidth
                size="small"
                onKeyDown={(e) => e.key === "Enter" && submit()}
                error={Boolean(fieldErrors.password)}
                helperText={
                  fieldErrors.password ||
                  (tab === 1 ? "8+ chars, upper & lowercase, number, special char" : "")
                }
              />

              {apiError && (
                <Alert
                  severity="error"
                  sx={{
                    fontSize: "0.75rem",
                    fontFamily: "'IBM Plex Mono', monospace",
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
                  loading ? (
                    <CircularProgress size={16} sx={{ color: "#080C10" }} />
                  ) : (
                    <ArrowRight size={16} />
                  )
                }
                sx={{ mt: 1, py: 1.5 }}
              >
                {tab === 0 ? "Sign In" : "Create Account"}
              </Button>
            </Stack>
          </Card>

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
            <Shield size={12} color="#7D8590" />
            <Typography variant="caption" color="text.secondary">
              JWT secured · Data encrypted
            </Typography>
          </Box>
        </Box>
      </Fade>
    </Box>
  );
}