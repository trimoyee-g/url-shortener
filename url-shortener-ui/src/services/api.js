const BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api/v1";

export const api = {
  async register(email, password, name) {
    const r = await fetch(`${BASE}/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, name }),
    });
    if (!r.ok)
      throw new Error((await r.json()).message || "Registration failed");
    return r.json();
  },

  async login(email, password) {
    const r = await fetch(`${BASE}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    if (!r.ok) throw new Error("Invalid credentials");
    return r.json();
  },

  async shorten(token, longUrl, customAlias, ttlSeconds) {
    const r = await fetch(`${BASE}/urls/shorten`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        longUrl,
        customAlias: customAlias || null,
        ttlSeconds: ttlSeconds || null,
      }),
    });
    if (!r.ok) {
      const body = await r.json();
      const msg = body.message && typeof body.message === "object"
        ? Object.values(body.message).join("; ")
        : body.message || "Failed to shorten";
      throw new Error(msg);
    }
    return r.json();
  },

  async getUrls(token) {
    const r = await fetch(`${BASE}/urls`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!r.ok) throw new Error("Failed to fetch URLs");
    return r.json();
  },

  async getStats(token, shortCode) {
    const r = await fetch(`${BASE}/urls/${shortCode}/stats`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!r.ok) throw new Error("Failed to fetch stats");
    return r.json();
  },

  async deleteUrl(token, shortCode) {
    const r = await fetch(`${BASE}/urls/${shortCode}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!r.ok) throw new Error("Failed to delete");
  },

  async getQr(token, shortCode, size = 300) {
    const r = await fetch(`${BASE}/urls/${shortCode}/qr?size=${size}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!r.ok) throw new Error("Failed to generate QR");
    const blob = await r.blob();
    return URL.createObjectURL(blob);
  },
};