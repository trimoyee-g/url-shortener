import React, { useState, useEffect, useCallback } from "react";
import {
  Box, Typography, Button, IconButton, Tooltip, Avatar, Menu, MenuItem,
  Divider, Container, Card, Stack, Skeleton, TableContainer, Table,
  TableHead, TableRow, TableCell, TableBody, Paper, Fade, Chip, Snackbar, Alert
} from "@mui/material";
import { 
  Zap, Plus, RefreshCw, LogOut, Link, BarChart2, QrCode, Trash2, Check, Copy 
} from "lucide-react";

// Hook & Service Imports
import { api } from "../services/api";
import { useSnack } from "../hooks/useSnack"; 
import { copyToClipboard } from "../utils/formatters"; 
import { fmtDate } from "../utils/formatters";

// Component Imports
import CreateUrlDialog from "../components/CreateUrlDialog";
import StatsDialog from "../components/StatsDialog";
import DeleteDialog from "../components/DeleteDialog";
import QrDialog from "../components/QrDialog";

export default function Dashboard({ token, email, onLogout }) {
  const [urls, setUrls] = useState([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [qrUrl, setQrUrl] = useState(null);
  const [statsUrl, setStatsUrl] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [anchorEl, setAnchorEl] = useState(null);
  const [copied, setCopied] = useState(null);
  const { snack, show, hide } = useSnack();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await api.getUrls(token);
      setUrls(data);
    } catch (e) {
      show(e.message, "error");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    load();
  }, [load]);

  const handleCopy = (text, id) => {
    copyToClipboard(text);
    setCopied(id);
    show("Copied to clipboard");
    setTimeout(() => setCopied(null), 2000);
  };

  const handleDelete = async () => {
    setDeleteLoading(true);
    try {
      await api.deleteUrl(token, deleteTarget);
      setUrls((u) => u.filter((x) => x.shortCode !== deleteTarget));
      show("URL deleted");
      setDeleteTarget(null);
    } catch (e) {
      show(e.message, "error");
    } finally {
      setDeleteLoading(false);
    }
  };

  const initials = email?.slice(0, 2).toUpperCase() ?? "??";

  return (
    <Box sx={{ minHeight: "100vh", background: "#080C10" }}>
      {/* Topbar */}
      <Box
        sx={{
          borderBottom: "1px solid #21262D",
          px: { xs: 2, md: 4 },
          py: 1.5,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          background: "rgba(13,17,23,0.8)",
          backdropFilter: "blur(10px)",
          position: "sticky",
          top: 0,
          zIndex: 100,
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
          <Zap size={18} color="#00FF94" />
          <Typography
            variant="h6"
            sx={{
              color: "#00FF94",
              letterSpacing: "0.1em",
              fontSize: "0.85rem",
            }}
          >
            TrimIt
          </Typography>
        </Box>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <Button
            variant="contained"
            size="small"
            startIcon={<Plus size={14} />}
            onClick={() => setCreateOpen(true)}
            sx={{ mr: 1 }}
          >
            New URL
          </Button>
          <Tooltip title={`Refresh`}>
            <IconButton size="small" onClick={load} sx={{ color: "#7D8590" }}>
              <RefreshCw size={14} />
            </IconButton>
          </Tooltip>
          <Avatar
            sx={{
              width: 32,
              height: 32,
              background: "#00FF9422",
              color: "#00FF94",
              fontSize: "0.7rem",
              cursor: "pointer",
              border: "1px solid #00FF9444",
            }}
            onClick={(e) => setAnchorEl(e.currentTarget)}
          >
            {initials}
          </Avatar>
          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={() => setAnchorEl(null)}
            PaperProps={{
              sx: {
                background: "#0D1117",
                border: "1px solid #21262D",
                minWidth: 180,
              },
            }}
          >
            <Box sx={{ px: 2, py: 1.5 }}>
              <Typography variant="caption" color="text.secondary">
                Signed in as
              </Typography>
              <Typography
                variant="body2"
                sx={{ fontSize: "0.75rem", wordBreak: "break-all" }}
              >
                {email}
              </Typography>
            </Box>
            <Divider sx={{ borderColor: "#21262D" }} />
            <MenuItem
              onClick={onLogout}
              sx={{
                color: "#FF6B6B",
                gap: 1.5,
                fontSize: "0.8rem",
                fontFamily: "'IBM Plex Mono', monospace",
              }}
            >
              <LogOut size={14} /> Sign out
            </MenuItem>
          </Menu>
        </Box>
      </Box>

      {/* Main */}
      <Container maxWidth="xl" sx={{ py: 4, px: { xs: 2, md: 4 } }}>
        {/* Header */}
        <Box sx={{ mb: 4 }}>
          <Typography variant="h4" sx={{ mb: 0.5 }}>
            Your URLs
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {urls.length} link{urls.length !== 1 ? "s" : ""} · Click to copy
            short URL
          </Typography>
        </Box>

        {/* Table */}
        {loading ? (
          <Stack spacing={1.5}>
            {[0, 1, 2, 3].map((i) => (
              <Skeleton
                key={i}
                variant="rectangular"
                height={56}
                sx={{ borderRadius: 1 }}
              />
            ))}
          </Stack>
        ) : urls.length === 0 ? (
          <Card sx={{ p: 6, textAlign: "center" }}>
            <Link size={40} color="#21262D" style={{ marginBottom: 16 }} />
            <Typography variant="h6" color="text.secondary" gutterBottom>
              No URLs yet
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              Shorten your first URL to get started
            </Typography>
            <Button
              variant="contained"
              startIcon={<Plus size={14} />}
              onClick={() => setCreateOpen(true)}
            >
              Shorten URL
            </Button>
          </Card>
        ) : (
          <TableContainer
            component={Paper}
            sx={{ background: "#0D1117", border: "1px solid #21262D" }}
          >
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>SHORT CODE</TableCell>
                  <TableCell>DESTINATION</TableCell>
                  <TableCell>CREATED</TableCell>
                  <TableCell>EXPIRES</TableCell>
                  <TableCell align="right">ACTIONS</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {urls.map((url, i) => (
                  <Fade in key={url.shortCode} timeout={300 + i * 50}>
                    <TableRow
                      sx={{
                        "&:hover": { background: "rgba(255,255,255,0.02)" },
                        transition: "background 0.15s",
                      }}
                    >
                      <TableCell>
                        <Box
                          sx={{
                            display: "flex",
                            alignItems: "center",
                            gap: 1,
                            cursor: "pointer",
                          }}
                          onClick={() =>
                            handleCopy(url.shortUrl, url.shortCode)
                          }
                        >
                          <Chip
                            label={url.shortCode}
                            size="small"
                            sx={{
                              background: "rgba(0,255,148,0.08)",
                              color: "#00FF94",
                              border: "1px solid rgba(0,255,148,0.2)",
                              cursor: "pointer",
                            }}
                          />
                          {copied === url.shortCode ? (
                            <Check size={12} color="#00FF94" />
                          ) : (
                            <Copy size={12} color="#7D8590" />
                          )}
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Tooltip title={url.longUrl} arrow>
                          <Typography
                            variant="body2"
                            sx={{
                              maxWidth: 300,
                              overflow: "hidden",
                              textOverflow: "ellipsis",
                              whiteSpace: "nowrap",
                              color: "#7D8590",
                              fontSize: "0.75rem",
                            }}
                          >
                            {url.longUrl}
                          </Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell sx={{ color: "#7D8590" }}>
                        {fmtDate(url.createdAt)}
                      </TableCell>
                      <TableCell>
                        {url.expiresAt ? (
                          <Chip
                            label={fmtDate(url.expiresAt)}
                            size="small"
                            sx={{
                              background: "rgba(245,158,11,0.1)",
                              color: "#F59E0B",
                              border: "1px solid rgba(245,158,11,0.2)",
                            }}
                          />
                        ) : (
                          <Typography variant="caption" color="text.secondary">
                            Never
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell align="right">
                        <Box
                          sx={{
                            display: "flex",
                            justifyContent: "flex-end",
                            gap: 0.5,
                          }}
                        >
                          <Tooltip title="Analytics">
                            <IconButton
                              size="small"
                              onClick={() => setStatsUrl(url)}
                              sx={{
                                color: "#7D8590",
                                "&:hover": { color: "#00FF94" },
                              }}
                            >
                              <BarChart2 size={15} />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="QR Code">
                            <IconButton
                              size="small"
                              onClick={() => setQrUrl(url.shortCode)}
                              sx={{
                                color: "#7D8590",
                                "&:hover": { color: "#60A5FA" },
                              }}
                            >
                              <QrCode size={15} />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Delete">
                            <IconButton
                              size="small"
                              onClick={() => setDeleteTarget(url.shortCode)}
                              sx={{
                                color: "#7D8590",
                                "&:hover": { color: "#FF6B6B" },
                              }}
                            >
                              <Trash2 size={15} />
                            </IconButton>
                          </Tooltip>
                        </Box>
                      </TableCell>
                    </TableRow>
                  </Fade>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Container>

      {/* Dialogs */}
      <CreateUrlDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        token={token}
        onCreated={(url) => {
          setUrls((u) => [url, ...u]);
          show("URL shortened successfully!");
        }}
      />

      <QrDialog
        open={Boolean(qrUrl)}
        onClose={() => setQrUrl(null)}
        token={token}
        shortCode={qrUrl}
      />

      <StatsDialog
        open={Boolean(statsUrl)}
        onClose={() => setStatsUrl(null)}
        token={token}
        url={statsUrl}
      />

      <DeleteDialog
        open={Boolean(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDelete}
        shortCode={deleteTarget}
        loading={deleteLoading}
      />

      {/* Snackbar */}
      <Snackbar
        open={snack.open}
        autoHideDuration={3000}
        onClose={hide}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
      >
        <Alert
          onClose={hide}
          severity={snack.severity}
          sx={{ fontFamily: "'IBM Plex Mono', monospace", fontSize: "0.75rem" }}
        >
          {snack.msg}
        </Alert>
      </Snackbar>
    </Box>
  );
}