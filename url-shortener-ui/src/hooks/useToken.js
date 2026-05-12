import { useState } from 'react';

export function useToken() {
  const [token, setToken] = useState(() => localStorage.getItem("token"));

  const save = (t) => {
    localStorage.setItem("token", t);
    setToken(t);
  };

  const clear = () => {
    localStorage.removeItem("token");
    setToken(null);
  };

  return { token, save, clear };
}