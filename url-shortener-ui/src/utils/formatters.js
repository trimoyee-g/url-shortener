export const fmtDate = (iso) =>
  iso
    ? new Date(iso).toLocaleDateString("en-GB", {
        day: "2-digit",
        month: "short",
        year: "numeric",
      })
    : "—";

export const fmtNum = (n) =>
  n >= 1000 ? `${(n / 1000).toFixed(1)}k` : String(n ?? 0);

export const copyToClipboard = (text) => navigator.clipboard.writeText(text);