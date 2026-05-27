import React, { useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Stack,
  InputAdornment,
  Typography,
  Alert,
  CircularProgress,
  Collapse,
  Box,
  Divider,
} from "@mui/material";
import { Plus, Link, Calendar, Zap, ChevronDown, ChevronUp, Tag, Lock } from "lucide-react";
import { api } from "../services/api";

function validateUrl(url) {
  try {
    const parsed = new URL(url);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

export default function CreateUrlDialog({ open, onClose, token, onCreated }) {
  const [longUrl, setLongUrl]     = useState("");
  const [alias, setAlias]         = useState("");
  const [ttl, setTtl]             = useState("");
  const [password, setPassword]   = useState("");
  const [utmSource, setUtmSource] = useState("");
  const [utmMedium, setUtmMedium] = useState("");
  const [utmCampaign, setUtmCampaign] = useState("");
  const [showUtm, setShowUtm]     = useState(false);
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState("");
  const [urlError, setUrlError]   = useState("");

  const reset = () => {
    setLongUrl(""); setAlias(""); setTtl(""); setPassword("");
    setUtmSource(""); setUtmMedium(""); setUtmCampaign("");
    setShowUtm(false); setError(""); setUrlError("");
  };

  const submit = async () => {
    if (!longUrl.trim()) { setUrlError("URL is required"); return; }
    if (!validateUrl(longUrl.trim())) {
      setUrlError("Enter a valid URL starting with http:// or https://");
      return;
    }
    setUrlError("");
    setLoading(true);
    setError("");
    try {
      const data = await api.shorten(
        token,
        longUrl.trim(),
        alias.trim() || null,
        ttl ? parseInt(ttl) * 86400 : null,
        {
          utmSource:   utmSource.trim()   || null,
          utmMedium:   utmMedium.trim()   || null,
          utmCampaign: utmCampaign.trim() || null,
          password:    password           || null,
        }
      );
      onCreated(data);
      reset();
      onClose();
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog
      open={open}
      onClose={() => { reset(); onClose(); }}
      maxWidth="sm"
      fullWidth
      PaperProps={{ sx: { background: "#0D1117", border: "1px solid #21262D" } }}
    >
      <DialogTitle
        sx={{ fontFamily: "'Syne', sans-serif", fontWeight: 700,
              display: "flex", alignItems: "center", gap: 1.5 }}
      >
        <Plus size={20} color="#38BDF8" />
        Shorten URL
      </DialogTitle>

      <DialogContent>
        <Stack spacing={2.5} sx={{ mt: 1 }}>
          {/* Main fields */}
          <TextField
            label="Long URL *"
            value={longUrl}
            onChange={(e) => { setLongUrl(e.target.value); if (urlError) setUrlError(""); }}
            fullWidth
            placeholder="https://example.com/very/long/url"
            error={Boolean(urlError)}
            helperText={urlError}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Link size={14} color="#7D8590" />
                </InputAdornment>
              ),
            }}
          />

          <TextField
            label="Custom alias (optional)"
            value={alias}
            onChange={(e) => setAlias(e.target.value)}
            fullWidth
            placeholder="my-brand"
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Typography variant="caption" color="text.secondary">trim.it/</Typography>
                </InputAdornment>
              ),
            }}
          />

          <Stack direction="row" spacing={2}>
            <TextField
              label="Expiry (days)"
              value={ttl}
              onChange={(e) => {
                const val = parseInt(e.target.value, 10);
                if (!val || val >= 0) setTtl(e.target.value);
              }}
              fullWidth
              type="number"
              placeholder="30"
              inputProps={{ min: 0 }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <Calendar size={14} color="#7D8590" />
                  </InputAdornment>
                ),
              }}
            />

            <TextField
              label="Password (optional)"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              fullWidth
              type="password"
              placeholder="••••••••"
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <Lock size={14} color="#7D8590" />
                  </InputAdornment>
                ),
              }}
            />
          </Stack>

          {/* UTM collapsible */}
          <Box>
            <Button
              size="small"
              onClick={() => setShowUtm((v) => !v)}
              endIcon={showUtm ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
              sx={{
                color: "#7D8590",
                fontSize: "0.72rem",
                textTransform: "none",
                fontFamily: "'IBM Plex Mono', monospace",
                px: 0,
                "&:hover": { color: "#38BDF8", background: "transparent" },
              }}
              startIcon={<Tag size={13} />}
            >
              UTM parameters
            </Button>

            <Collapse in={showUtm}>
              <Stack spacing={2} sx={{ mt: 2 }}>
                <Divider sx={{ borderColor: "#21262D" }} />
                <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                  <TextField
                    label="utm_source"
                    value={utmSource}
                    onChange={(e) => setUtmSource(e.target.value)}
                    fullWidth
                    placeholder="twitter"
                    size="small"
                  />
                  <TextField
                    label="utm_medium"
                    value={utmMedium}
                    onChange={(e) => setUtmMedium(e.target.value)}
                    fullWidth
                    placeholder="social"
                    size="small"
                  />
                </Stack>
                <TextField
                  label="utm_campaign"
                  value={utmCampaign}
                  onChange={(e) => setUtmCampaign(e.target.value)}
                  fullWidth
                  placeholder="launch-2025"
                  size="small"
                />
              </Stack>
            </Collapse>
          </Box>

          {error && (
            <Alert severity="error"
              sx={{ fontSize: "0.75rem", fontFamily: "'IBM Plex Mono', monospace" }}>
              {error}
            </Alert>
          )}
        </Stack>
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button onClick={() => { reset(); onClose(); }} sx={{ color: "#7D8590" }}>
          Cancel
        </Button>
        <Button
          variant="contained"
          onClick={submit}
          disabled={loading}
          endIcon={
            loading
              ? <CircularProgress size={14} sx={{ color: "#080C10" }} />
              : <Zap size={14} />
          }
        >
          Shorten
        </Button>
      </DialogActions>
    </Dialog>
  );
}
