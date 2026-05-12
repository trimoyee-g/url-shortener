import React, { useState, useEffect } from "react";
import {
  Box, 
  IconButton, 
  Dialog, 
  DialogTitle, 
  DialogContent, 
  DialogActions,
  Button,
  Typography, 
  CircularProgress
} from "@mui/material";
import { X, QrCode, Plus, Link, Calendar, Zap } from "lucide-react";
import { api } from "../services/api";


export default function QrDialog({ open, onClose, token, shortCode }) {
  const [src, setSrc] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open || !shortCode) return;
    setLoading(true);
    api
      .getQr(token, shortCode)
      .then(setSrc)
      .catch(console.error)
      .finally(() => setLoading(false));
    return () => src && URL.revokeObjectURL(src);
  }, [open, shortCode]);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="xs"
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
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <QrCode size={18} color="#00FF94" /> QR Code
        </Box>
        <IconButton onClick={onClose} size="small">
          <X size={16} />
        </IconButton>
      </DialogTitle>
      <DialogContent sx={{ textAlign: "center", pb: 3 }}>
        {loading ? (
          <Box sx={{ py: 4 }}>
            <CircularProgress sx={{ color: "#00FF94" }} />
          </Box>
        ) : src ? (
          <Box>
            <Box
              sx={{
                background: "#fff",
                p: 2,
                borderRadius: 2,
                display: "inline-block",
                mb: 2,
              }}
            >
              <img
                src={src}
                alt="QR"
                style={{ width: 200, height: 200, display: "block" }}
              />
            </Box>
            <Box>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ fontFamily: "'IBM Plex Mono', monospace" }}
              >
                trim.it/{shortCode}
              </Typography>
            </Box>
            <Button
              variant="outlined"
              size="small"
              sx={{ mt: 2, borderColor: "#21262D", color: "#E6EDF3" }}
              onClick={() => {
                const a = document.createElement("a");
                a.href = src;
                a.download = `${shortCode}-qr.png`;
                a.click();
              }}
            >
              Download PNG
            </Button>
          </Box>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}