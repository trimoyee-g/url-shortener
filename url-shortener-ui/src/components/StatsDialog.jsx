import React, { useState, useEffect } from "react";
import {
  Dialog, DialogTitle, DialogContent, Box, Typography, IconButton,
  Stack, Card, Skeleton, LinearProgress, Tooltip
} from "@mui/material";
import { 
  BarChart2, X, MousePointer, Users, TrendingUp, Globe 
} from "lucide-react";
import { api } from "../services/api";
import { fmtNum } from "../utils/formatters";

export default function StatsDialog({ open, onClose, token, url }) {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open || !url) return;
    setLoading(true);
    api
      .getStats(token, url.shortCode)
      .then(setStats)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [open, url]);

  const StatCard = ({ icon, label, value, color = "#00FF94" }) => (
    <Card sx={{ p: 2, flex: 1, minWidth: 0 }}>
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}>
        <Box sx={{ color }}>{icon}</Box>
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ letterSpacing: "0.08em" }}
        >
          {label}
        </Typography>
      </Box>
      <Typography variant="h4" sx={{ color, fontFamily: "'Syne', sans-serif" }}>
        {fmtNum(value)}
      </Typography>
    </Card>
  );

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
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
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
          <BarChart2 size={18} color="#00FF94" />
          Analytics —{" "}
          <Typography
            component="span"
            sx={{
              fontFamily: "'IBM Plex Mono', monospace",
              color: "#00FF94",
              fontSize: "0.9rem",
            }}
          >
            {url?.shortCode}
          </Typography>
        </Box>
        <IconButton onClick={onClose} size="small">
          <X size={16} />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        {loading ? (
          <Box sx={{ py: 2 }}>
            <Stack spacing={2}>
              <Stack direction="row" spacing={2}>
                {[0, 1, 2].map((i) => (
                  <Skeleton
                    key={i}
                    variant="rectangular"
                    height={80}
                    sx={{ flex: 1, borderRadius: 1 }}
                  />
                ))}
              </Stack>
              <Skeleton
                variant="rectangular"
                height={200}
                sx={{ borderRadius: 1 }}
              />
            </Stack>
          </Box>
        ) : stats ? (
          <Stack spacing={3} sx={{ pt: 1 }}>
            {/* Stat cards */}
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <StatCard
                icon={<MousePointer size={16} />}
                label="TOTAL CLICKS"
                value={stats.totalClicks}
                color="#00FF94"
              />
              <StatCard
                icon={<Users size={16} />}
                label="UNIQUE IPS"
                value={stats.uniqueIps}
                color="#60A5FA"
              />
              <StatCard
                icon={<TrendingUp size={16} />}
                label="COUNTRIES"
                value={stats.topCountries?.length}
                color="#F59E0B"
              />
            </Stack>

            {/* URL info */}
            <Card sx={{ p: 2 }}>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ letterSpacing: "0.08em", display: "block", mb: 1 }}
              >
                DESTINATION
              </Typography>
              <Typography
                variant="body2"
                sx={{ wordBreak: "break-all", color: "#7D8590" }}
              >
                {stats.longUrl || url?.longUrl}
              </Typography>
            </Card>

            {/* Top countries */}
            {stats.topCountries?.length > 0 && (
              <Card sx={{ p: 2 }}>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{ letterSpacing: "0.08em", display: "block", mb: 2 }}
                >
                  TOP COUNTRIES
                </Typography>
                <Stack spacing={1.5}>
                  {stats.topCountries.map((c, i) => {
                    const pct =
                      stats.totalClicks > 0
                        ? (c.clicks / stats.totalClicks) * 100
                        : 0;
                    return (
                      <Box key={i}>
                        <Box
                          sx={{
                            display: "flex",
                            justifyContent: "space-between",
                            mb: 0.5,
                          }}
                        >
                          <Box
                            sx={{
                              display: "flex",
                              alignItems: "center",
                              gap: 1,
                            }}
                          >
                            <Globe size={12} color="#7D8590" />
                            <Typography variant="caption">
                              {c.country}
                            </Typography>
                          </Box>
                          <Typography variant="caption" color="text.secondary">
                            {fmtNum(c.clicks)} · {pct.toFixed(1)}%
                          </Typography>
                        </Box>
                        <LinearProgress
                          variant="determinate"
                          value={pct}
                          sx={{
                            height: 3,
                            borderRadius: 2,
                            background: "#21262D",
                            "& .MuiLinearProgress-bar": {
                              background: "#00FF94",
                              borderRadius: 2,
                            },
                          }}
                        />
                      </Box>
                    );
                  })}
                </Stack>
              </Card>
            )}

            {/* Clicks by day */}
            {stats.clicksByDay && Object.keys(stats.clicksByDay).length > 0 && (
              <Card sx={{ p: 2 }}>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{ letterSpacing: "0.08em", display: "block", mb: 2 }}
                >
                  CLICKS BY DAY (LAST 30 DAYS)
                </Typography>
                <Box
                  sx={{
                    display: "flex",
                    alignItems: "flex-end",
                    gap: 0.5,
                    height: 80,
                  }}
                >
                  {Object.entries(stats.clicksByDay).map(([day, count], i) => {
                    const max = Math.max(...Object.values(stats.clicksByDay));
                    const h = max > 0 ? (count / max) * 100 : 0;
                    return (
                      <Tooltip key={i} title={`${day}: ${count}`} arrow>
                        <Box
                          sx={{
                            flex: 1,
                            background: `rgba(0,255,148,${0.2 + (h / 100) * 0.8})`,
                            height: `${Math.max(h, 4)}%`,
                            borderRadius: "2px 2px 0 0",
                            transition: "all 0.2s",
                            cursor: "default",
                            "&:hover": { background: "#00FF94" },
                            minWidth: 4,
                          }}
                        />
                      </Tooltip>
                    );
                  })}
                </Box>
              </Card>
            )}
          </Stack>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}