import { useState, useEffect, useCallback, useRef } from "react";
import {
  Box, Typography, Button, IconButton, Tooltip, Avatar, Menu, MenuItem,
  Divider, Card, Stack, Skeleton, TableContainer, Table,
  TableHead, TableRow, TableCell, TableBody, Paper, Chip, Snackbar, Alert,
  InputBase, CircularProgress,
} from "@mui/material";
import {
  Zap, Plus, RefreshCw, LogOut, Link as LinkIcon, BarChart2, QrCode,
  Trash2, Check, Copy, LayoutDashboard, List, MousePointer,
  Users, TrendingUp, TrendingDown, Globe, Monitor, Smartphone, Tablet,
  Search, ChevronRight, Lock,
} from "lucide-react";

import { api }                                    from "../services/api";
import { useSnack }                               from "../hooks/useSnack";
import { copyToClipboard, fmtDate, fmtNum, fmtPct } from "../utils/formatters";
import CreateUrlDialog                            from "../components/CreateUrlDialog";
import StatsDialog                                from "../components/StatsDialog";
import DeleteDialog                               from "../components/DeleteDialog";
import QrDialog                                   from "../components/QrDialog";

// ─── palette ────────────────────────────────────────────────────────────────
const C = {
  bg:      "#080C10",
  surface: "#0D1117",
  border:  "#21262D",
  muted:   "#7D8590",
  cyan:    "#38BDF8",
  blue:    "#60A5FA",
  amber:   "#F59E0B",
  violet:  "#A78BFA",
  red:     "#FF6B6B",
  green:   "#34D399",
};

// ─── SVG area chart ─────────────────────────────────────────────────────────
function AreaChart({ data, height = 120 }) {
  if (!data || Object.keys(data).length === 0) {
    return (
      <Box sx={{ height, display: "flex", alignItems: "center",
        justifyContent: "center", color: C.muted }}>
        <Typography variant="caption">No data yet</Typography>
      </Box>
    );
  }
  const entries = Object.entries(data).sort(([a], [b]) => a.localeCompare(b));
  const values  = entries.map(([, v]) => Number(v));
  const max     = Math.max(...values, 1);
  const W = 600, H = height, TPAD = 22, BPAD = 4, HPAD = 4;

  const pts = entries.map(([, v], i) => {
    const x = HPAD + (i / Math.max(entries.length - 1, 1)) * (W - HPAD * 2);
    const y = H - BPAD - ((v / max) * (H - TPAD - BPAD));
    return [x, y];
  });

  const linePts = pts.map((p) => p.join(",")).join(" ");
  const area = [
    `M${pts[0][0]},${H}`,
    ...pts.map((p) => `L${p[0]},${p[1]}`),
    `L${pts[pts.length - 1][0]},${H}`,
    "Z",
  ].join(" ");

  // x-axis labels — show ~6 evenly spaced dates
  const step       = Math.max(1, Math.floor(entries.length / 6));
  const tickIndices = new Set(entries.map((_, i) => i).filter(i => i % step === 0 || i === entries.length - 1));
  const ticks      = entries.filter((_, i) => tickIndices.has(i));

  // show value labels on all points when ≤15, otherwise only on ticks
  const showLabel = entries.length <= 15
    ? () => true
    : (_, i) => tickIndices.has(i);

  return (
    <Box sx={{ width: "100%", position: "relative" }}>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        style={{ width: "100%", height, display: "block" }}
        preserveAspectRatio="none"
      >
        <defs>
          <linearGradient id="acgr" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%"   stopColor={C.cyan} stopOpacity="0.3" />
            <stop offset="100%" stopColor={C.cyan} stopOpacity="0"   />
          </linearGradient>
        </defs>
        <path d={area} fill="url(#acgr)" />
        <polyline points={linePts} fill="none" stroke={C.cyan} strokeWidth="2"
          strokeLinejoin="round" strokeLinecap="round" />
        {pts.map(([x, y], i) => (
          <circle key={i} cx={x} cy={y} r="2.5" fill={C.cyan} opacity="0.7" />
        ))}
      </svg>

      {/* value labels — absolutely positioned above each dot */}
      {pts.map(([x, y], i) => showLabel(null, i) && (
        <Typography key={i} variant="caption" sx={{
          position: "absolute",
          left: `${(x / W) * 100}%`,
          top:  `${y - 18}px`,
          transform: "translateX(-50%)",
          fontSize: "0.58rem",
          color: C.cyan,
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
            sx={{ color: C.muted, fontSize: "0.6rem", fontFamily: "'IBM Plex Mono', monospace" }}>
            {day.slice(5)}
          </Typography>
        ))}
      </Box>
    </Box>
  );
}

// ─── stat card ───────────────────────────────────────────────────────────────
function StatCard({ icon, label, value, sub, subColor, loading }) {
  return (
    <Card sx={{ p: 2.5, flex: 1, minWidth: 0 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
        <Box>
          <Typography variant="caption" color="text.secondary"
            sx={{ letterSpacing: "0.1em", fontSize: "0.65rem" }}>
            {label}
          </Typography>
          {loading ? (
            <Skeleton variant="text" width={60} height={40} />
          ) : (
            <Typography variant="h4"
              sx={{ fontFamily: "'Syne', sans-serif", lineHeight: 1.1, mt: 0.5 }}>
              {value}
            </Typography>
          )}
          {sub && !loading && (
            <Typography variant="caption" sx={{ color: subColor ?? C.muted, fontSize: "0.7rem" }}>
              {sub}
            </Typography>
          )}
        </Box>
        <Box sx={{ color: C.muted, mt: 0.5 }}>{icon}</Box>
      </Box>
    </Card>
  );
}

// ─── sidebar nav item ────────────────────────────────────────────────────────
function NavItem({ icon, label, active, onClick }) {
  return (
    <Box
      onClick={onClick}
      sx={{
        display: "flex", alignItems: "center", gap: 1.5,
        px: 2, py: 1.2, borderRadius: 1.5, cursor: "pointer",
        color: active ? C.cyan : C.muted,
        background: active ? "rgba(56,189,248,0.08)" : "transparent",
        fontSize: "0.8rem",
        fontFamily: "'IBM Plex Mono', monospace",
        transition: "all 0.15s",
        "&:hover": { color: active ? C.cyan : "#CBD5E1",
          background: active ? "rgba(56,189,248,0.08)" : "rgba(255,255,255,0.04)" },
        borderLeft: active ? `2px solid ${C.cyan}` : "2px solid transparent",
        ml: "-2px",
      }}
    >
      {icon}
      <span>{label}</span>
    </Box>
  );
}

// ─── inline shortener bar ────────────────────────────────────────────────────
function InlineShortener({ token, onCreated, show: showDialog }) {
  const [url,     setUrl]     = useState("");
  const [loading, setLoading] = useState(false);
  const inputRef = useRef();

  const submit = async (e) => {
    e?.preventDefault();
    const trimmed = url.trim();
    if (!trimmed) return;
    try { new URL(trimmed); } catch { return; }
    setLoading(true);
    try {
      const data = await api.shorten(token, trimmed, null, null);
      onCreated(data);
      setUrl("");
      inputRef.current?.blur();
    } catch {
      // fall back to full dialog on error
      showDialog();
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box
      component="form"
      onSubmit={submit}
      sx={{
        display: "flex", alignItems: "center",
        background: C.surface, border: `1px solid ${C.border}`,
        borderRadius: 2, overflow: "hidden",
        "&:focus-within": { borderColor: "rgba(56,189,248,0.5)" },
        transition: "border-color 0.2s",
        height: 44,
      }}
    >
      <LinkIcon size={14} color={C.muted} style={{ marginLeft: 14, flexShrink: 0 }} />
      <InputBase
        inputRef={inputRef}
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        placeholder="Paste a long URL to shorten it…"
        sx={{
          flex: 1, px: 1.5, fontSize: "0.82rem",
          fontFamily: "'IBM Plex Mono', monospace", color: "#CBD5E1",
          "& input::placeholder": { color: C.muted },
        }}
      />
      <Button
        type="submit"
        variant="contained"
        size="small"
        disabled={loading || !url.trim()}
        endIcon={loading
          ? <CircularProgress size={12} sx={{ color: "#080C10" }} />
          : <ChevronRight size={14} />}
        sx={{ height: "100%", borderRadius: 0, px: 2.5,
          minWidth: 110, fontSize: "0.75rem" }}
      >
        Shorten
      </Button>
    </Box>
  );
}

// ─── VIEWS ───────────────────────────────────────────────────────────────────

function DashboardView({ token, snack }) {
  const [dashboard, setDashboard] = useState(null);
  const [dashLoading, setDashLoading] = useState(true);
  const [days, setDays] = useState(30);
  const [showAllCountries, setShowAllCountries] = useState(false);
  const [showAllReferrers, setShowAllReferrers] = useState(false);

  const loadDash = useCallback(async () => {
    setDashLoading(true);
    try {
      const d = await api.getDashboard(token, days);
      setDashboard(d);
    } catch (e) {
      snack.show(e.message, "error");
    } finally {
      setDashLoading(false);
    }
  }, [token, days]);

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { loadDash(); }, [loadDash]);

  const changePct = dashboard?.clicksChangePct;
  const isUp = changePct != null && changePct >= 0;

  const PERIOD_OPTS = [
    { label: "7d",  value: 7  },
    { label: "30d", value: 30 },
    { label: "90d", value: 90 },
  ];

  return (
    <Stack spacing={3}>
      {/* Period toggle */}
      <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}>
        {PERIOD_OPTS.map((p) => (
          <Button key={p.value} size="small" variant={days === p.value ? "contained" : "outlined"}
            onClick={() => setDays(p.value)}
            sx={{ minWidth: 48, px: 1.5, fontSize: "0.7rem",
              fontFamily: "'IBM Plex Mono', monospace" }}>
            {p.label}
          </Button>
        ))}
        <Tooltip title="Refresh">
          <IconButton size="small" onClick={loadDash} sx={{ color: C.muted }}>
            <RefreshCw size={13} />
          </IconButton>
        </Tooltip>
      </Box>

      {/* Stat cards */}
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
        <StatCard
          icon={<MousePointer size={18} />}
          label="TOTAL CLICKS"
          value={fmtNum(dashboard?.totalClicks ?? 0)}
          sub={changePct != null
            ? `${fmtPct(changePct)} vs prev period`
            : undefined}
          subColor={changePct != null
            ? (isUp ? C.green : C.red)
            : undefined}
          loading={dashLoading}
        />
        <StatCard
          icon={<LinkIcon size={18} />}
          label="ACTIVE LINKS"
          value={fmtNum(dashboard?.totalLinks ?? 0)}
          loading={dashLoading}
        />
        <StatCard
          icon={<Users size={18} />}
          label="UNIQUE VISITORS"
          value={fmtNum(dashboard?.uniqueVisitors ?? 0)}
          loading={dashLoading}
        />
        <StatCard
          icon={isUp ? <TrendingUp size={18} /> : <TrendingDown size={18} />}
          label="TREND"
          value={changePct != null ? fmtPct(changePct) : "—"}
          subColor={changePct != null ? (isUp ? C.green : C.red) : C.muted}
          loading={dashLoading}
        />
      </Stack>

      {/* Area chart */}
      <Card sx={{ p: 3 }}>
        <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
          <Typography variant="caption" color="text.secondary" sx={{ letterSpacing: "0.1em" }}>
            CLICKS OVER TIME
          </Typography>
          <Typography variant="caption" sx={{ color: C.cyan, fontFamily: "'IBM Plex Mono', monospace" }}>
            last {days} days
          </Typography>
        </Box>
        {dashLoading
          ? <Skeleton variant="rectangular" height={120} sx={{ borderRadius: 1 }} />
          : <AreaChart data={dashboard?.clicksByDay} height={120} />
        }
      </Card>

      {/* Bottom row */}
      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        {/* Top countries */}
        <Card sx={{ p: 2.5, flex: 1 }}>
          <Typography variant="caption" color="text.secondary"
            sx={{ letterSpacing: "0.1em", display: "block", mb: 2 }}>
            TOP COUNTRIES
          </Typography>
          {dashLoading ? (
            <Stack spacing={1}>{[0,1,2,3].map(i =>
              <Skeleton key={i} variant="text" height={28} />)}</Stack>
          ) : dashboard?.topCountries?.length ? (
            <>
              <Stack spacing={1.5}>
                {(showAllCountries ? dashboard.topCountries : dashboard.topCountries.slice(0, 5)).map((c, i) => {
                  const total = dashboard.topCountries.reduce((s, x) => s + x.clicks, 0) || 1;
                  const pct   = (c.clicks / total) * 100;
                  return (
                    <Box key={i}>
                      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
                        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                          <Globe size={11} color={C.muted} />
                          <Typography variant="caption">{c.country}</Typography>
                        </Box>
                        <Typography variant="caption" color="text.secondary">
                          {fmtNum(c.clicks)} · {pct.toFixed(1)}%
                        </Typography>
                      </Box>
                      <Box sx={{ height: 3, borderRadius: 2, background: C.border }}>
                        <Box sx={{ height: 3, borderRadius: 2, background: C.cyan,
                          width: `${pct}%`, transition: "width 0.5s" }} />
                      </Box>
                    </Box>
                  );
                })}
              </Stack>
              {dashboard.topCountries.length > 5 && (
                <Button size="small" onClick={() => setShowAllCountries(v => !v)}
                  sx={{ mt: 1.5, p: 0, minWidth: 0, color: C.muted, fontSize: "0.7rem",
                    textTransform: "none", "&:hover": { color: C.cyan, background: "none" } }}>
                  {showAllCountries ? "Show less" : `View all ${dashboard.topCountries.length}`}
                </Button>
              )}
            </>
          ) : (
            <Typography variant="caption" color="text.secondary">No data yet</Typography>
          )}
        </Card>

        {/* Device breakdown */}
        <Card sx={{ p: 2.5, flex: 1 }}>
          <Typography variant="caption" color="text.secondary"
            sx={{ letterSpacing: "0.1em", display: "block", mb: 2 }}>
            DEVICE BREAKDOWN
          </Typography>
          {dashLoading ? (
            <Stack spacing={1}>{[0,1,2].map(i =>
              <Skeleton key={i} variant="text" height={28} />)}</Stack>
          ) : dashboard?.deviceBreakdown ? (
            <Stack spacing={1.5}>
              {Object.entries(dashboard.deviceBreakdown).map(([key, count]) => {
                const total = Object.values(dashboard.deviceBreakdown).reduce((s, v) => s + v, 0) || 1;
                const pct   = (count / total) * 100;
                const color = key === "MOBILE" ? C.blue : key === "TABLET" ? C.amber : C.cyan;
                const Icon  = key === "MOBILE" ? Smartphone : key === "TABLET" ? Tablet : Monitor;
                return (
                  <Box key={key}>
                    <Box sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
                      <Box sx={{ display: "flex", alignItems: "center", gap: 1, color }}>
                        <Icon size={12} />
                        <Typography variant="caption"
                          sx={{ color, fontFamily: "'IBM Plex Mono', monospace",
                            textTransform: "capitalize" }}>
                          {key.toLowerCase()}
                        </Typography>
                      </Box>
                      <Typography variant="caption" color="text.secondary">
                        {fmtNum(count)} · {pct.toFixed(1)}%
                      </Typography>
                    </Box>
                    <Box sx={{ height: 3, borderRadius: 2, background: C.border }}>
                      <Box sx={{ height: 3, borderRadius: 2, background: color,
                        width: `${pct}%`, transition: "width 0.5s" }} />
                    </Box>
                  </Box>
                );
              })}
            </Stack>
          ) : (
            <Typography variant="caption" color="text.secondary">No data yet</Typography>
          )}
        </Card>

        {/* Top referrers */}
        <Card sx={{ p: 2.5, flex: 1 }}>
          <Typography variant="caption" color="text.secondary"
            sx={{ letterSpacing: "0.1em", display: "block", mb: 2 }}>
            TOP REFERRERS
          </Typography>
          {dashLoading ? (
            <Stack spacing={1}>{[0,1,2,3].map(i =>
              <Skeleton key={i} variant="text" height={28} />)}</Stack>
          ) : dashboard?.topReferrers?.length ? (
            <>
              <Stack spacing={1.5}>
                {(showAllReferrers ? dashboard.topReferrers : dashboard.topReferrers.slice(0, 5)).map((r, i) => {
                  const total = dashboard.topReferrers.reduce((s, x) => s + x.clicks, 0) || 1;
                  const pct   = (r.clicks / total) * 100;
                  return (
                    <Box key={i}>
                      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
                        <Tooltip title={r.referrer} placement="top" enterDelay={400}>
                          <Typography variant="caption"
                            sx={{ flex: 1, minWidth: 0, overflow: "hidden",
                              textOverflow: "ellipsis", whiteSpace: "nowrap", mr: 1 }}>
                            {r.referrer}
                          </Typography>
                        </Tooltip>
                        <Typography variant="caption" color="text.secondary">
                          {fmtNum(r.clicks)} · {pct.toFixed(1)}%
                        </Typography>
                      </Box>
                      <Box sx={{ height: 3, borderRadius: 2, background: C.border }}>
                        <Box sx={{ height: 3, borderRadius: 2, background: C.violet,
                          width: `${pct}%`, transition: "width 0.5s" }} />
                      </Box>
                    </Box>
                  );
                })}
              </Stack>
              {dashboard.topReferrers.length > 5 && (
                <Button size="small" onClick={() => setShowAllReferrers(v => !v)}
                  sx={{ mt: 1.5, p: 0, minWidth: 0, color: C.muted, fontSize: "0.7rem",
                    textTransform: "none", "&:hover": { color: C.violet, background: "none" } }}>
                  {showAllReferrers ? "Show less" : `View all ${dashboard.topReferrers.length}`}
                </Button>
              )}
            </>
          ) : (
            <Typography variant="caption" color="text.secondary">No data yet</Typography>
          )}
        </Card>
      </Stack>

    </Stack>
  );
}

// ─── My Links view ───────────────────────────────────────────────────────────
function LinksView({
  urls, loading, onRefresh, onOpenStats, onOpenQr,
  onDeleteTarget, onOpenCreate, snack,
}) {
  const [search,     setSearch]     = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [copied,     setCopied]     = useState(null);

  const handleCopy = (text, id) => {
    copyToClipboard(text);
    setCopied(id);
    snack.show("Copied!");
    setTimeout(() => setCopied(null), 1800);
  };

  const filtered = urls.filter((u) => {
    const matchSearch =
      u.shortCode.toLowerCase().includes(search.toLowerCase()) ||
      u.longUrl.toLowerCase().includes(search.toLowerCase());
    const matchStatus = statusFilter === "ALL" || u.status === statusFilter;
    return matchSearch && matchStatus;
  });

  return (
    <Stack spacing={3}>
      {/* Toolbar */}
      <Box sx={{ display: "flex", gap: 1.5, flexWrap: "wrap", alignItems: "center" }}>
        <Box sx={{
          flex: 1, minWidth: 200, display: "flex", alignItems: "center", gap: 1,
          background: C.surface, border: `1px solid ${C.border}`, borderRadius: 1.5,
          px: 1.5, height: 38,
          "&:focus-within": { borderColor: "rgba(56,189,248,0.4)" },
        }}>
          <Search size={13} color={C.muted} />
          <InputBase
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search links…"
            sx={{ flex: 1, fontSize: "0.8rem", color: "#CBD5E1",
              "& input::placeholder": { color: C.muted } }}
          />
        </Box>

        {["ALL", "ACTIVE", "EXPIRED"].map((s) => (
          <Button key={s} size="small"
            variant={statusFilter === s ? "contained" : "outlined"}
            onClick={() => setStatusFilter(s)}
            sx={{ fontSize: "0.7rem", px: 1.5,
              fontFamily: "'IBM Plex Mono', monospace" }}>
            {s}
          </Button>
        ))}

        <Tooltip title="Refresh">
          <IconButton size="small" onClick={onRefresh} sx={{ color: C.muted }}>
            <RefreshCw size={13} />
          </IconButton>
        </Tooltip>
        <Button variant="contained" size="small" startIcon={<Plus size={13} />}
          onClick={onOpenCreate}>
          New link
        </Button>
      </Box>

      {loading ? (
        <Stack spacing={1.5}>
          {[0,1,2,3,4].map(i =>
            <Skeleton key={i} variant="rectangular" height={54} sx={{ borderRadius: 1 }} />)}
        </Stack>
      ) : filtered.length === 0 ? (
        <Card sx={{ p: 6, textAlign: "center" }}>
          <LinkIcon size={36} color={C.border} style={{ marginBottom: 12 }} />
          <Typography variant="h6" color="text.secondary" gutterBottom>
            {search ? "No matching links" : "No links yet"}
          </Typography>
          {!search && (
            <Button variant="contained" startIcon={<Plus size={14} />}
              onClick={onOpenCreate} sx={{ mt: 1 }}>
              Shorten URL
            </Button>
          )}
        </Card>
      ) : (
        <TableContainer component={Paper}
          sx={{ background: C.surface, border: `1px solid ${C.border}` }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: 160 }}>SHORT CODE</TableCell>
                <TableCell>DESTINATION</TableCell>
                <TableCell sx={{ width: 100 }}>CLICKS</TableCell>
                <TableCell sx={{ width: 110 }}>STATUS</TableCell>
                <TableCell sx={{ width: 110 }}>CREATED</TableCell>
                <TableCell sx={{ width: 110 }}>EXPIRES</TableCell>
                <TableCell align="right" sx={{ width: 120 }}>ACTIONS</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filtered.map((url) => (
                <TableRow key={url.shortCode}
                  sx={{ "&:hover": { background: "rgba(255,255,255,0.02)" },
                    transition: "background 0.15s" }}>
                  <TableCell>
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1, cursor: "pointer" }}
                      onClick={() => handleCopy(url.shortUrl, url.shortCode)}>
                      <Chip label={url.shortCode} size="small"
                        sx={{ background: "rgba(56,189,248,0.08)", color: C.cyan,
                          border: `1px solid rgba(56,189,248,0.2)`, cursor: "pointer",
                          fontFamily: "'IBM Plex Mono', monospace", fontSize: "0.68rem" }} />
                      {copied === url.shortCode
                        ? <Check size={11} color={C.cyan} />
                        : <Copy  size={11} color={C.muted} />}
                      {url.hasPassword && (
                        <Tooltip title="Password protected">
                          <Lock size={11} color={C.amber} />
                        </Tooltip>
                      )}
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Tooltip title={url.longUrl} arrow>
                      <Typography variant="body2"
                        sx={{ maxWidth: 280, overflow: "hidden", textOverflow: "ellipsis",
                          whiteSpace: "nowrap", color: C.muted, fontSize: "0.73rem" }}>
                        {url.longUrl}
                      </Typography>
                    </Tooltip>
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption"
                      sx={{ fontFamily: "'IBM Plex Mono', monospace", color: "#CBD5E1" }}>
                      {fmtNum(url.clickCount ?? 0)}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={url.status ?? "ACTIVE"}
                      size="small"
                      sx={{
                        background: url.status === "EXPIRED"
                          ? "rgba(255,107,107,0.1)" : "rgba(52,211,153,0.1)",
                        color: url.status === "EXPIRED" ? C.red : C.green,
                        border: `1px solid ${url.status === "EXPIRED"
                          ? "rgba(255,107,107,0.25)" : "rgba(52,211,153,0.25)"}`,
                        fontSize: "0.65rem",
                        fontFamily: "'IBM Plex Mono', monospace",
                      }}
                    />
                  </TableCell>
                  <TableCell sx={{ color: C.muted, fontSize: "0.72rem" }}>
                    {fmtDate(url.createdAt)}
                  </TableCell>
                  <TableCell>
                    {url.expiresAt ? (
                      <Chip label={fmtDate(url.expiresAt)} size="small"
                        sx={{ background: "rgba(245,158,11,0.1)", color: C.amber,
                          border: "1px solid rgba(245,158,11,0.2)", fontSize: "0.65rem" }} />
                    ) : (
                      <Typography variant="caption" color="text.secondary">Never</Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 0.5 }}>
                      <Tooltip title="Analytics">
                        <IconButton size="small" onClick={() => onOpenStats(url)}
                          sx={{ color: C.muted, "&:hover": { color: C.cyan } }}>
                          <BarChart2 size={14} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="QR Code">
                        <IconButton size="small" onClick={() => onOpenQr(url.shortCode)}
                          sx={{ color: C.muted, "&:hover": { color: C.blue } }}>
                          <QrCode size={14} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Delete">
                        <IconButton size="small" onClick={() => onDeleteTarget(url.shortCode)}
                          sx={{ color: C.muted, "&:hover": { color: C.red } }}>
                          <Trash2 size={14} />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Stack>
  );
}

// ─── MAIN DASHBOARD ─────────────────────────────────────────────────────────
export default function Dashboard({ token, email, onLogout }) {
  const [urls,          setUrls]          = useState([]);
  const [urlsLoading,   setUrlsLoading]   = useState(true);
  const [view,          setView]          = useState("dashboard");
  const [createOpen,    setCreateOpen]    = useState(false);
  const [qrUrl,         setQrUrl]         = useState(null);
  const [statsUrl,      setStatsUrl]      = useState(null);
  const [deleteTarget,  setDeleteTarget]  = useState(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [anchorEl,      setAnchorEl]      = useState(null);
  const { snack, show, hide } = useSnack();

  const loadUrls = useCallback(async () => {
    setUrlsLoading(true);
    try {
      const data = await api.getUrls(token);
      setUrls(data);
    } catch (e) {
      show(e.message, "error");
    } finally {
      setUrlsLoading(false);
    }
  }, [token]);

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { loadUrls(); }, [loadUrls]);

  const handleDelete = async () => {
    setDeleteLoading(true);
    try {
      await api.deleteUrl(token, deleteTarget);
      setUrls((u) => u.filter((x) => x.shortCode !== deleteTarget));
      show("Link deleted");
      setDeleteTarget(null);
    } catch (e) {
      show(e.message, "error");
    } finally {
      setDeleteLoading(false);
    }
  };

  const initials = email?.slice(0, 2).toUpperCase() ?? "??";
  const SIDEBAR_W = 220;

  const NAV = [
    { id: "dashboard", icon: <LayoutDashboard size={15} />, label: "Dashboard" },
    { id: "links",     icon: <List           size={15} />, label: "My Links"   }
  ];

  const viewTitle = {
    dashboard: "Overview",
    links:     "My Links"
  }[view];

  return (
    <Box sx={{ minHeight: "100vh", background: C.bg, display: "flex",
      animation: "pageIn 0.4s ease both" }}>

      {/* ── Sidebar ─────────────────────────────────────────────────── */}
      <Box sx={{
        width: SIDEBAR_W, flexShrink: 0, borderRight: `1px solid ${C.border}`,
        background: C.surface, display: { xs: "none", md: "flex" },
        flexDirection: "column", position: "sticky", top: 0, height: "100vh",
        overflowY: "auto",
      }}>
        {/* Logo */}
        <Box sx={{ px: 2.5, py: 2.5, display: "flex", alignItems: "center", gap: 1.5,
          borderBottom: `1px solid ${C.border}` }}>
          <Zap size={16} color={C.cyan} />
          <Typography sx={{ color: C.cyan, fontFamily: "'Syne', sans-serif",
            fontWeight: 700, letterSpacing: "0.12em", fontSize: "0.9rem" }}>
            TrimIt
          </Typography>
        </Box>

        {/* Nav */}
        <Stack spacing={0.5} sx={{ px: 1.5, py: 2, flex: 1 }}>
          {NAV.map((n) => (
            <NavItem key={n.id} icon={n.icon} label={n.label}
              active={view === n.id} onClick={() => setView(n.id)} />
          ))}
        </Stack>

        {/* User footer */}
        <Box sx={{ px: 2, py: 2, borderTop: `1px solid ${C.border}` }}>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
            <Avatar sx={{ width: 28, height: 28, background: "rgba(56,189,248,0.15)",
              color: C.cyan, fontSize: "0.65rem", border: `1px solid rgba(56,189,248,0.3)` }}>
              {initials}
            </Avatar>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography variant="caption" sx={{ color: "#CBD5E1", display: "block",
                overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap",
                fontSize: "0.7rem" }}>
                {email}
              </Typography>
            </Box>
            <Tooltip title="Sign out">
              <IconButton size="small" onClick={onLogout} sx={{ color: C.muted,
                "&:hover": { color: C.red } }}>
                <LogOut size={13} />
              </IconButton>
            </Tooltip>
          </Box>
        </Box>
      </Box>

      {/* ── Main area ─────────────────────────────────────────────────── */}
      <Box sx={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
        {/* Topbar */}
        <Box sx={{
          borderBottom: `1px solid ${C.border}`, px: { xs: 2, md: 3 }, py: 1.5,
          display: "flex", alignItems: "center", gap: 2,
          background: "rgba(13,17,23,0.85)", backdropFilter: "blur(10px)",
          position: "sticky", top: 0, zIndex: 100,
        }}>
          {/* Mobile logo */}
          <Box sx={{ display: { xs: "flex", md: "none" }, alignItems: "center", gap: 1 }}>
            <Zap size={15} color={C.cyan} />
            <Typography sx={{ color: C.cyan, fontFamily: "'Syne', sans-serif",
              fontWeight: 700, fontSize: "0.85rem", letterSpacing: "0.1em" }}>
              TrimIt
            </Typography>
          </Box>

          <Typography variant="h6"
            sx={{ fontFamily: "'Syne', sans-serif", fontSize: "1rem",
              display: { xs: "none", md: "block" }, mr: 1 }}>
            {viewTitle}
          </Typography>

          {/* Inline shortener */}
          <Box sx={{ flex: 1, maxWidth: 520 }}>
            <InlineShortener
              token={token}
              onCreated={(url) => {
                setUrls((u) => [url, ...u]);
                show("Link shortened!");
                setView("links");
              }}
              show={() => setCreateOpen(true)}
            />
          </Box>

          <Box sx={{ display: "flex", alignItems: "center", gap: 0.5, ml: "auto" }}>
            <Button variant="contained" size="small" startIcon={<Plus size={13} />}
              onClick={() => setCreateOpen(true)}
              sx={{ display: { xs: "none", sm: "flex" } }}>
              New link
            </Button>

            {/* Mobile avatar / menu */}
            <Avatar
              sx={{ width: 30, height: 30, background: "rgba(56,189,248,0.15)",
                color: C.cyan, fontSize: "0.65rem", cursor: "pointer",
                border: `1px solid rgba(56,189,248,0.3)`,
                display: { xs: "flex", md: "none" } }}
              onClick={(e) => setAnchorEl(e.currentTarget)}
            >
              {initials}
            </Avatar>
            <Menu anchorEl={anchorEl} open={Boolean(anchorEl)}
              onClose={() => setAnchorEl(null)}
              PaperProps={{ sx: { background: C.surface,
                border: `1px solid ${C.border}`, minWidth: 180 } }}>
              <Box sx={{ px: 2, py: 1.5 }}>
                <Typography variant="caption" color="text.secondary">Signed in as</Typography>
                <Typography variant="body2"
                  sx={{ fontSize: "0.73rem", wordBreak: "break-all" }}>{email}</Typography>
              </Box>
              <Divider sx={{ borderColor: C.border }} />
              {NAV.map((n) => (
                <MenuItem key={n.id}
                  onClick={() => { setView(n.id); setAnchorEl(null); }}
                  sx={{ gap: 1.5, fontSize: "0.8rem",
                    fontFamily: "'IBM Plex Mono', monospace",
                    color: view === n.id ? C.cyan : "inherit" }}>
                  {n.icon} {n.label}
                </MenuItem>
              ))}
              <Divider sx={{ borderColor: C.border }} />
              <MenuItem onClick={onLogout}
                sx={{ color: C.red, gap: 1.5, fontSize: "0.8rem",
                  fontFamily: "'IBM Plex Mono', monospace" }}>
                <LogOut size={13} /> Sign out
              </MenuItem>
            </Menu>
          </Box>
        </Box>

        {/* Content */}
        <Box sx={{ flex: 1, px: { xs: 2, md: 3 }, py: 3, maxWidth: 1100, mx: "auto", width: "100%" }}>
          {view === "dashboard" && (
            <DashboardView
              token={token}
              snack={{ show }}
            />
          )}
          {view === "links" && (
            <LinksView
              token={token}
              urls={urls}
              loading={urlsLoading}
              onRefresh={loadUrls}
              onOpenStats={(url) => setStatsUrl(url)}
              onOpenQr={(code) => setQrUrl(code)}
              onDeleteTarget={(code) => setDeleteTarget(code)}
              onOpenCreate={() => setCreateOpen(true)}
              onCreated={(url) => { setUrls((u) => [url, ...u]); show("Link shortened!"); }}
              snack={{ show }}
            />
          )}
        </Box>
      </Box>

      {/* ── Dialogs ─────────────────────────────────────────────────── */}
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

      {/* ── Snackbar ─────────────────────────────────────────────────── */}
      <Snackbar open={snack.open} autoHideDuration={3000} onClose={hide}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}>
        <Alert onClose={hide} severity={snack.severity}
          sx={{ fontFamily: "'IBM Plex Mono', monospace", fontSize: "0.75rem" }}>
          {snack.msg}
        </Alert>
      </Snackbar>
    </Box>
  );
}
