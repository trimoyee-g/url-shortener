import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Typography,
  Button,
  CircularProgress,
} from "@mui/material";
import { Trash2 } from "lucide-react";

export default function DeleteDialog({ open, onClose, onConfirm, shortCode, loading }) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="xs"
      fullWidth
      PaperProps={{
        sx: { background: "#0D1117", border: "1px solid #FF6B6B44" },
      }}
    >
      <DialogTitle
        sx={{
          fontFamily: "'Syne', sans-serif",
          fontWeight: 700,
          color: "#FF6B6B",
          display: "flex",
          alignItems: "center",
          gap: 1,
        }}
      >
        <Trash2 size={18} /> Delete URL
      </DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary">
          Are you sure you want to delete{" "}
          <Typography
            component="span"
            sx={{
              fontFamily: "'IBM Plex Mono', monospace",
              color: "#E6EDF3",
              fontSize: "0.8rem",
            }}
          >
            {shortCode}
          </Typography>
          ? This action cannot be undone.
        </Typography>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button onClick={onClose} sx={{ color: "#7D8590" }}>
          Cancel
        </Button>
        <Button
          onClick={onConfirm}
          disabled={loading}
          sx={{
            background: "#FF6B6B22",
            color: "#FF6B6B",
            border: "1px solid #FF6B6B44",
            "&:hover": { background: "#FF6B6B33" },
          }}
          endIcon={
            loading ? (
              <CircularProgress size={14} sx={{ color: "#FF6B6B" }} />
            ) : (
              <Trash2 size={14} />
            )
          }
        >
          Delete
        </Button>
      </DialogActions>
    </Dialog>
  );
}