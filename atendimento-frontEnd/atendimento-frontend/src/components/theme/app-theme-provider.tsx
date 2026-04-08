"use client";

import * as React from "react";

/** Mesma chave que o next-themes usava por defeito — mantém preferência já guardada. */
const STORAGE_KEY = "theme";

export type Theme = "light" | "dark" | "system";

export type ThemeContextValue = {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  resolvedTheme: "light" | "dark";
};

const ThemeContext = React.createContext<ThemeContextValue | undefined>(undefined);

function getSystemTheme(): "light" | "dark" {
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function resolveTheme(theme: Theme): "light" | "dark" {
  if (theme === "system") return getSystemTheme();
  return theme;
}

export function AppThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = React.useState<Theme>("dark");
  const [systemEpoch, setSystemEpoch] = React.useState(0);

  React.useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw === "light" || raw === "dark" || raw === "system") {
        setThemeState(raw);
      }
    } catch {
      /* ignore */
    }
  }, []);

  React.useEffect(() => {
    if (theme !== "system") return;
    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => setSystemEpoch((n) => n + 1);
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, [theme]);

  const resolvedTheme = React.useMemo(
    () => resolveTheme(theme),
    [theme, systemEpoch],
  );

  React.useEffect(() => {
    const root = document.documentElement;
    root.classList.remove("dark");
    if (resolvedTheme === "dark") {
      root.classList.add("dark");
    }
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      /* ignore */
    }
  }, [theme, resolvedTheme]);

  const setTheme = React.useCallback((t: Theme) => {
    setThemeState(t);
  }, []);

  const value = React.useMemo(
    (): ThemeContextValue => ({
      theme,
      setTheme,
      resolvedTheme,
    }),
    [theme, setTheme, resolvedTheme],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = React.useContext(ThemeContext);
  if (!ctx) {
    throw new Error("useTheme must be used within AppThemeProvider");
  }
  return ctx;
}
