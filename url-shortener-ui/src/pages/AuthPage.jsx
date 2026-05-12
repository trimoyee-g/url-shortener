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
import { Zap, ArrowRight, Shield } from "lucide-react"; // Import your icons
import { api } from "../services/api"; // Import the API layer we created

export default function AuthPage({ onAuth }) {
  const [tab, setTab] = useState(0);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const submit = async () => {
    setError("");
    setLoading(true);
    try {
      const data =
        tab === 0
          ? await api.login(email, password)
          : await api.register(email, password, name);

      // Note: Check if your API returns 'accessToken' or just 'token'
      onAuth(data.accessToken || data.token);
    } catch (e) {
      setError(e.message);
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
              onChange={(_, v) => {
                setTab(v);
                setError("");
              }}
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
                  onChange={(e) => setName(e.target.value)}
                  fullWidth
                  size="small"
                />
              )}
              <TextField
                label="Email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                fullWidth
                size="small"
              />
              <TextField
                label="Password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                fullWidth
                size="small"
                onKeyDown={(e) => e.key === "Enter" && submit()}
              />

              {error && (
                <Alert
                  severity="error"
                  sx={{
                    fontSize: "0.75rem",
                    fontFamily: "'IBM Plex Mono', monospace",
                  }}
                >
                  {error}
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
