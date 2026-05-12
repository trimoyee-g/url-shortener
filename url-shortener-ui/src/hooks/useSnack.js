import { useState } from "react";

export function useSnack() {
  const [snack, setSnack] = useState({
    open: false,
    msg: "",
    severity: "success",
  });
  const show = (msg, severity = "success") =>
    setSnack({ open: true, msg, severity });
  const hide = () => setSnack((s) => ({ ...s, open: false }));
  return { snack, show, hide };
}