import { useState, useCallback, useMemo } from "react";
import { useToken } from "./hooks/useToken";
import LandingPage from "./pages/LandingPage.jsx";
import AuthPage    from "./pages/AuthPage.jsx";
import Dashboard   from "./pages/Dashboard.jsx";

export default function App() {
  const { token, save, clear } = useToken();

  // Landing → Auth → Dashboard flow
  const [view, setView] = useState(token ? "dashboard" : "landing");

  const handleAuth = useCallback((t) => {
    save(t);
    setView("dashboard");
  }, [save]);

  const handleLogout = useCallback(() => {
    clear();
    setView("landing");
  }, [clear]);

  // Decode email from JWT only when token is present
  const email = useMemo(() => {
    if (!token) return null;
    try {
      return JSON.parse(atob(token.split(".")[1])).sub;
    } catch {
      return null;
    }
  }, [token]);

  if (view === "landing")
    return <LandingPage onGetStarted={() => setView("auth")} />;

  if (view === "auth")
    return <AuthPage onAuth={handleAuth} onBack={() => setView("landing")} />;

  // dashboard — guard against missing token (e.g. localStorage cleared externally)
  if (!token) {
    setView("landing");
    return null;
  }

  return <Dashboard token={token} email={email} onLogout={handleLogout} />;
}
