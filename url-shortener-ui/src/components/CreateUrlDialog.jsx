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
} from "@mui/material";
import { Plus, Link, Calendar, Zap } from "lucide-react";
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
  const [longUrl, setLongUrl] = useState("");
  const [alias, setAlias] = useState("");
  const [ttl, setTtl] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [urlError, setUrlError] = useState("");

  const reset = () => {
    setLongUrl("");
    setAlias("");
    setTtl("");
    setError("");
    setUrlError("");
  };

  const submit = async () => {
    if (!longUrl.trim()) {
      setUrlError("URL is required");
      return;
    }
    if (!validateUrl(longUrl.trim())) {
      setUrlError("Enter a valid URL starting with http:// or https://");
      return;
    }
    setUrlError("");
    setLoading(true);
    setError("");
    try {
      // 86400 converts days to seconds for the API
      const data = await api.shorten(
        token,
        longUrl,
        alias,
        ttl ? parseInt(ttl) * 86400 : null,
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
      onClose={() => {
        reset();
        onClose();
      }}
      maxWidth="sm"
      fullWidth
      PaperProps={{
        sx: { background: "#0D1117", border: "1px solid #21262D" },
      }}
    >
      <DialogTitle
        sx={{
          fontFamily: "'Syne', sans-serif",

          fontWeight: 700,

          display: "flex",

          alignItems: "center",

          gap: 1.5,
        }}
      >
        <Plus size={20} color="#00FF94" />
        Shorten URL
      </DialogTitle>

      <DialogContent>
        <Stack spacing={2.5} sx={{ mt: 1 }}>
          <TextField
            label="Long URL *"
            value={longUrl}
            onChange={(e) => {
              setLongUrl(e.target.value);
              if (urlError) setUrlError("");
            }}
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
                  <Typography variant="caption" color="text.secondary">
                    trim.it/
                  </Typography>
                </InputAdornment>
              ),
            }}
          />

          <TextField
            label="Expiry (days, optional)"
            value={ttl}
            onChange={(e) => {
              const val = parseInt(e.target.value, 10);

              // Logic check: only update state if value is not negative

              if (!val || val >= 0) {
                setTtl(e.target.value);
              }
            }}
            fullWidth
            type="number"
            placeholder="30"
            // This prevents the "stepper" arrows from going below 0

            inputProps={{ min: 0 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Calendar size={14} color="#7D8590" />
                </InputAdornment>
              ),
            }}
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
        </Stack>
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button
          onClick={() => {
            reset();

            onClose();
          }}
          sx={{ color: "#7D8590" }}
        >
          Cancel
        </Button>

        <Button
          variant="contained"
          onClick={submit}
          disabled={loading}
          endIcon={
            loading ? (
              <CircularProgress size={14} sx={{ color: "#080C10" }} />
            ) : (
              <Zap size={14} />
            )
          }
        >
          Shorten
        </Button>
      </DialogActions>
    </Dialog>
  );
}
