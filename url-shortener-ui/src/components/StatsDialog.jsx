import { useState, useEffect, useCallback } from "react";
import {
  Dialog, DialogTitle, DialogContent, Box, Typography, IconButton,
  Stack, Card, Skeleton, LinearProgress, Tooltip, Alert,
  ToggleButtonGroup, ToggleButton,
} from "@mui/material";
import {
  BarChart2, X, MousePointer, Users, TrendingUp, Globe, RefreshCw,
  Monitor, Smartphone, Tablet, ExternalLink,
} from "lucide-react";
import { api } from "../services/api";
import { fmtNum } from "../utils/formatters";

const PERIODS = [
  { label: "7d",  value: 7  },
  { label: "30d", value: 30 },
  { label: "90d", value: 90 },
];

// Pure-SVG tiny area chart
function MiniAreaChart({ data }) {
  if (!data || Object.keys(data).length === 0) return null;
  const entries = Object.entries(data).sort(([a], [b]) => a.localeCompare(b));
  const values  = entries.map(([, v]) => v);
  const max     = Math.max(...values, 1);
  const W = 500, H = 80, PAD = 2;

  // Single data point — show a centred dot + label instead of an invisible line
  if (entries.length === 1) {
    return (
      <Box sx={{ display: "flex", flexDirection: "column", alignItems: "center",
        justifyContent: "center", height: H, gap: 0.5 }}>
        <Box sx={{ width: 8, height: 8, borderRadius: "50%", background: "#38BDF8" }} />
        <Typography variant="caption"
          sx={{ color: "#38BDF8", fontFamily: "'IBM Plex Mono', monospace", fontSize: "0.65rem" }}>
          {values[0]} click{values[0] !== 1 ? "s" : ""} · {entries[0][0].slice(5)}
        </Typography>
      </Box>
    );
  }

  const TPAD = 22, BPAD = 4, HPAD = 4;
  const pts = entries.map(([, v], i) => {
    const x = HPAD + (i / (entries.length - 1)) * (W - HPAD * 2);
    const y = H - BPAD - ((v / max) * (H - TPAD - BPAD));
    return [x, y];
  });
  const polyline = pts.map((p) => p.join(",")).join(" ");
  const area     = `M${pts[0][0]},${H} ` +
    pts.map((p) => `L${p[0]},${p[1]}`).join(" ") +
    ` L${pts[pts.length - 1][0]},${H} Z`;

  const step       = Math.max(1, Math.floor(entries.length / 6));
  const tickIndices = new Set(entries.map((_, i) => i).filter(i => i % step === 0 || i === entries.length - 1));
  const ticks      = entries.filter((_, i) => tickIndices.has(i));
  const showLabel  = entries.length <= 15 ? () => true : (i) => tickIndices.has(i);

  return (
    <Box sx={{ width: "100%", position: "relative" }}>
      <svg viewBox={`0 0 ${W} ${H}`} style={{ width: "100%", height: H }} preserveAspectRatio="none">
        <defs>
          <linearGradient id="sgr" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%"   stopColor="#38BDF8" stopOpacity="0.35" />
            <stop offset="100%" stopColor="#38BDF8" stopOpacity="0"    />
          </linearGradient>
        </defs>
        <path d={area}     fill="url(#sgr)" />
        <polyline points={polyline} fill="none" stroke="#38BDF8" strokeWidth="1.5" />
        {pts.map(([x, y], i) => (
          <circle key={i} cx={x} cy={y} r="2.5" fill="#38BDF8" opacity="0.8" />
        ))}
      </svg>

      {/* value labels above each dot */}
      {pts.map(([x, y], i) => showLabel(i) && (
        <Typography key={i} variant="caption" sx={{
          position: "absolute",
          left: `${(x / W) * 100}%`,
          top:  `${y - 18}px`,
          transform: "translateX(-50%)",
          fontSize: "0.58rem",
          color: "#38BDF8",
          fontFamily: "'IBM Plex Mono', monospace",
          pointerEvents: "none",
          lineHeight: 1,
        }}>
          {values[i]}
        </Typography>
      ))}

      {/* date labels */}
      <Box sx={{ display: "flex", justifyContent: "space-between", mt: 0.5 }}>
        {ticks.map(([day]) => (
          <Typography key={day} variant="caption"
            sx={{ color: "#7D8590", fontSize: "0.6rem", fontFamily: "'IBM Plex Mono', monospace" }}>
            {day.slice(5)}
          </Typography>
        ))}
      </Box>
    </Box>
  );
}

function StatCard({ icon, label, value, color = "#38BDF8" }) {
  return (
    <Card sx={{ p: 2, flex: 1, minWidth: 0 }}>
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}>
        <Box sx={{ color }}>{icon}</Box>
        <Typography variant="caption" color="text.secondary" sx={{ letterSpacing: "0.08em" }}>
          {label}
        </Typography>
      </Box>
      <Typography variant="h4" sx={{ color, fontFamily: "'Syne', sans-serif" }}>
        {fmtNum(value)}
      </Typography>
    </Card>
  );
}

export default function StatsDialog({ open, onClose, token, url }) {
  const [stats,   setStats]   = useState(null);
  const [loading, setLoading] = useState(false);
  const [error,   setError]   = useState(null);
  const [days,    setDays]    = useState(30);

  const fetchStats = useCallback(() => {
    if (!url) return;
    setLoading(true);
    setError(null);
    api
      .getStats(token, url.shortCode, days)
      .then((data) => setStats(data))
      .catch((e) => setError(e.message || "Failed to load analytics"))
      .finally(() => setLoading(false));
  }, [token, url, days]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (!open) { setStats(null); setError(null); return; }
    fetchStats();
  }, [open, fetchStats]);



  const deviceIcon = (key) => {
    if (key === "MOBILE")  return <Smartphone size={13} />;
    if (key === "TABLET")  return <Tablet size={13} />;
    return <Monitor size={13} />;
  };
  const deviceColor = (key) => {
    if (key === "MOBILE")  return "#60A5FA";
    if (key === "TABLET")  return "#F59E0B";
    return "#38BDF8";
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      PaperProps={{ sx: { background: "#0D1117", border: "1px solid #21262D" } }}
    >
      <DialogTitle
        sx={{ fontFamily: "'Syne', sans-serif", fontWeight: 700,
              display: "flex", justifyContent: "space-between", alignItems: "center" }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
          <BarChart2 size={18} color="#38BDF8" />
          Analytics —{" "}
          <Typography component="span"
            sx={{ fontFamily: "'IBM Plex Mono', monospace", color: "#38BDF8", fontSize: "0.9rem" }}>
            {url?.shortCode}
          </Typography>
        </Box>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          {/* Period toggle */}
          <ToggleButtonGroup
            value={days}
            exclusive
            onChange={(_, v) => { if (v) setDays(v); }}
            size="small"
            sx={{
              "& .MuiToggleButton-root": {
                color: "#7D8590", border: "1px solid #21262D",
                fontFamily: "'IBM Plex Mono', monospace", fontSize: "0.65rem",
                px: 1.2, py: 0.4,
                "&.Mui-selected": { color: "#38BDF8", background: "rgba(56,189,248,0.1)",
                  borderColor: "rgba(56,189,248,0.3)" },
              },
            }}
          >
            {PERIODS.map((p) => (
              <ToggleButton key={p.value} value={p.value}>{p.label}</ToggleButton>
            ))}
          </ToggleButtonGroup>
          <Tooltip title="Refresh">
            <IconButton size="small" onClick={fetchStats} disabled={loading} sx={{ color: "#7D8590" }}>
              <RefreshCw size={14} />
            </IconButton>
          </Tooltip>
          <IconButton onClick={onClose} size="small">
            <X size={16} />
          </IconButton>
        </Box>
      </DialogTitle>

      <DialogContent>
        {loading ? (
          <Box sx={{ py: 2 }}>
            <Stack spacing={2}>
              <Stack direction="row" spacing={2}>
                {[0, 1, 2].map((i) => (
                  <Skeleton key={i} variant="rectangular" height={80} sx={{ flex: 1, borderRadius: 1 }} />
                ))}
              </Stack>
              <Skeleton variant="rectangular" height={200} sx={{ borderRadius: 1 }} />
            </Stack>
          </Box>
        ) : error ? (
          <Alert severity="error"
            sx={{ mt: 1, fontFamily: "'IBM Plex Mono', monospace", fontSize: "0.75rem" }}>
            {error}
          </Alert>
        ) : stats ? (
          <Stack spacing={3} sx={{ pt: 1 }}>
            {/* Stat cards */}
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <StatCard icon={<MousePointer size={16} />} label="TOTAL CLICKS"
                value={stats.totalClicks}    color="#38BDF8" />
              <StatCard icon={<Users size={16} />}        label="UNIQUE VISITORS"
                value={stats.uniqueVisitors} color="#60A5FA" />
              <StatCard icon={<TrendingUp size={16} />}   label="COUNTRIES"
                value={stats.topCountries?.length ?? 0}  color="#F59E0B" />
            </Stack>

            {/* Destination */}
            <Card sx={{ p: 2 }}>
              <Typography variant="caption" color="text.secondary"
                sx={{ letterSpacing: "0.08em", display: "block", mb: 1 }}>
                DESTINATION
              </Typography>
              <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1 }}>
                <Typography variant="body2"
                  sx={{ wordBreak: "break-all", color: "#7D8590", flex: 1, fontSize: "0.75rem" }}>
                  {stats.longUrl || url?.longUrl}
                </Typography>
                <a href={stats.longUrl || url?.longUrl} target="_blank" rel="noopener noreferrer"
                  style={{ color: "#38BDF8", flexShrink: 0, marginTop: 2 }}>
                  <ExternalLink size={13} />
                </a>
              </Box>
            </Card>

            {/* Clicks by day — area chart */}
            {stats.clicksByDay && Object.keys(stats.clicksByDay).length > 0 && (
              <Card sx={{ p: 2 }}>
                <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1.5 }}>
                  <Typography variant="caption" color="text.secondary" sx={{ letterSpacing: "0.08em" }}>
                    CLICKS OVER TIME
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {fmtNum(Object.values(stats.clicksByDay).reduce((s, v) => s + v, 0))} total
                  </Typography>
                </Box>
                <MiniAreaChart data={stats.clicksByDay} />
              </Card>
            )}

            {/* Bottom row: countries + devices + referrers */}
            <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
              {/* Countries */}
              {stats.topCountries?.length > 0 && (
                <Card sx={{ p: 2, flex: 1 }}>
                  <Typography variant="caption" color="text.secondary"
                    sx={{ letterSpacing: "0.08em", display: "block", mb: 2 }}>
                    TOP COUNTRIES
                  </Typography>
                  <Stack spacing={1.5}>
                    {stats.topCountries.slice(0, 5).map((c, i) => {
                      const total = stats.topCountries.reduce((s, x) => s + x.clicks, 0) || 1;
                      const pct = (c.clicks / total) * 100;
                      return (
                        <Box key={i}>
                          <Box sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
                            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                              <Globe size={12} color="#7D8590" />
                              <Typography variant="caption">{c.country}</Typography>
                            </Box>
                            <Typography variant="caption" color="text.secondary">
                              {fmtNum(c.clicks)} · {pct.toFixed(1)}%
                            </Typography>
                          </Box>
                          <LinearProgress variant="determinate" value={pct}
                            sx={{ height: 3, borderRadius: 2, background: "#21262D",
                              "& .MuiLinearProgress-bar": { background: "#38BDF8", borderRadius: 2 } }} />
                        </Box>
                      );
                    })}
                  </Stack>
                </Card>
              )}

              {/* Devices */}
              {stats.deviceBreakdown && (
                <Card sx={{ p: 2, flex: 1 }}>
                  <Typography variant="caption" color="text.secondary"
                    sx={{ letterSpacing: "0.08em", display: "block", mb: 2 }}>
                    DEVICES
                  </Typography>
                  <Stack spacing={1.5}>
                    {Object.entries(stats.deviceBreakdown).map(([key, count]) => {
                      const total = Object.values(stats.deviceBreakdown).reduce((s, v) => s + v, 0) || 1;
                      const pct = (count / total) * 100;
                      return (
                        <Box key={key}>
                          <Box sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
                            <Box sx={{ display: "flex", alignItems: "center", gap: 1,
                              color: deviceColor(key) }}>
                              {deviceIcon(key)}
                              <Typography variant="caption" sx={{ color: "inherit",
                                textTransform: "capitalize", fontFamily: "'IBM Plex Mono', monospace" }}>
                                {key.toLowerCase()}
                              </Typography>
                            </Box>
                            <Typography variant="caption" color="text.secondary">
                              {fmtNum(count)} · {pct.toFixed(1)}%
                            </Typography>
                          </Box>
                          <LinearProgress variant="determinate" value={pct}
                            sx={{ height: 3, borderRadius: 2, background: "#21262D",
                              "& .MuiLinearProgress-bar": {
                                background: deviceColor(key), borderRadius: 2 } }} />
                        </Box>
                      );
                    })}
                  </Stack>
                </Card>
              )}

              {/* Referrers */}
              {stats.topReferrers?.length > 0 && (
                <Card sx={{ p: 2, flex: 1 }}>
                  <Typography variant="caption" color="text.secondary"
                    sx={{ letterSpacing: "0.08em", display: "block", mb: 2 }}>
                    TOP REFERRERS
                  </Typography>
                  <Stack spacing={1.5}>
                    {stats.topReferrers.slice(0, 5).map((r, i) => {
                      const total = stats.topReferrers.reduce((s, x) => s + x.clicks, 0) || 1;
                      const pct = (r.clicks / total) * 100;
                      return (
                        <Box key={i}>
                          <Box sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
                            <Tooltip title={r.referrer} placement="top" enterDelay={400}>
                              <Typography variant="caption" sx={{
                                flex: 1, minWidth: 0, overflow: "hidden",
                                textOverflow: "ellipsis", whiteSpace: "nowrap", mr: 1 }}>
                                {r.referrer}
                              </Typography>
                            </Tooltip>
                            <Typography variant="caption" color="text.secondary">
                              {fmtNum(r.clicks)} · {pct.toFixed(1)}%
                            </Typography>
                          </Box>
                          <LinearProgress variant="determinate" value={pct}
                            sx={{ height: 3, borderRadius: 2, background: "#21262D",
                              "& .MuiLinearProgress-bar": {
                                background: "#A78BFA", borderRadius: 2 } }} />
                        </Box>
                      );
                    })}
                  </Stack>
                </Card>
              )}
            </Stack>
          </Stack>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
