import { useState, useEffect, useCallback } from "react";
import {
  // ThemeProvider,
  // createTheme,
  // CssBaseline,
  Box,
  Container,
  Typography,
  TextField,
  Button,
  Card,
  CardContent,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Chip,
  Tooltip,
  Snackbar,
  Alert,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Tabs,
  Tab,
  Avatar,
  Menu,
  MenuItem,
  Divider,
  InputAdornment,
  Skeleton,
  Fade,
  Slide,
  CircularProgress,
  Grid,
  Stack,
} from "@mui/material";
import {
  Link,
  BarChart2,
  Copy,
  Trash2,
  QrCode,
  LogOut,
  Eye,
  Globe,
  Zap,
  ChevronRight,
  X,
  Check,
  RefreshCw,
  TrendingUp,
  Users,
  MousePointer,
  Calendar,
  Plus,
  Lock,
  ArrowRight,
  Shield,
} from "lucide-react";

import { api } from './services/api.js';
import { useToken } from './hooks/useToken';
import { fmtDate, copyToClipboard } from './utils/formatters';
import { useSnack } from "./hooks/useSnack.js";

import AuthPage from "./pages/AuthPage.jsx";
import Dashboard from "./pages/Dashboard.jsx";
import CreateUrlDialog from "./components/CreateUrlDialog.jsx";
import QrDialog from "./components/QrDialog.jsx";
import StatsDialog from "./components/StatsDialog.jsx";
import DeleteDialog from "./components/DeleteDialog.jsx";


// ─── Root ─────────────────────────────────────────────────────────────────────

export default function App() {
  const { token, save, clear } = useToken();

  // decode email from JWT
  const email = (() => {
    try {
      return JSON.parse(atob(token.split(".")[1])).sub;
    } catch {
      return null;
    }
  })();

  return (
    <>
      
        {token ? (
          <Dashboard token={token} email={email} onLogout={clear} />
        ) : (
          <AuthPage onAuth={save} />
        )}
    </>
  );
}
