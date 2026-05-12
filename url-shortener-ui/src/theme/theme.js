
import { createTheme } from "@mui/material/styles";

export const theme = createTheme({
  palette: {
    mode: "dark",
    primary: { main: "#00FF94" },
    secondary: { main: "#FF6B6B" },
    background: { default: "#080C10", paper: "#0D1117" },
    text: { primary: "#E6EDF3", secondary: "#7D8590" },
  },
  typography: {
    fontFamily: "'IBM Plex Mono', monospace",
    h1: { fontFamily: "'Syne', sans-serif", fontWeight: 800 },
    h2: { fontFamily: "'Syne', sans-serif", fontWeight: 700 },
    h3: { fontFamily: "'Syne', sans-serif", fontWeight: 700 },
    h4: { fontFamily: "'Syne', sans-serif", fontWeight: 600 },
    h5: { fontFamily: "'Syne', sans-serif", fontWeight: 600 },
    h6: { fontFamily: "'Syne', sans-serif", fontWeight: 600 },
  },
  shape: { borderRadius: 4 },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: "none",
          fontFamily: "'IBM Plex Mono', monospace",
          fontWeight: 600,
          letterSpacing: "0.02em",
        },
        containedPrimary: {
          background: "#00FF94",
          color: "#080C10",
          "&:hover": {
            background: "#00CC77",
            boxShadow: "0 0 20px rgba(0,255,148,0.4)",
          },
        },
      },
    },
    MuiTextField: {
      styleOverrides: {
        root: {
          "& .MuiOutlinedInput-root": {
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "0.875rem",
            "& fieldset": { borderColor: "#21262D" },
            "&:hover fieldset": { borderColor: "#00FF94" },
            "&.Mui-focused fieldset": { borderColor: "#00FF94" },
          },
          "& .MuiInputLabel-root": {
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "0.875rem",
            "&.Mui-focused": { color: "#00FF94" },
          },
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          border: "1px solid #21262D",
          backgroundImage: "none",
          "&:hover": { borderColor: "#30363D" },
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: {
          borderBottom: "1px solid #21262D",
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: "0.8rem",
        },
        head: { color: "#7D8590", fontWeight: 600, letterSpacing: "0.08em" },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { fontFamily: "'IBM Plex Mono', monospace", fontSize: "0.7rem" },
      },
    },
  },
});