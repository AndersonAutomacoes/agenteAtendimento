"use client";

import * as React from "react";

/** Registra o service worker apenas em producao (evita conflito com HMR no dev). */
export function PwaRegister() {
  React.useEffect(() => {
    if (process.env.NODE_ENV !== "production") return;
    if (typeof window === "undefined" || !("serviceWorker" in navigator)) return;

    const ctrl = navigator.serviceWorker.controller;
    void navigator.serviceWorker
      .register("/sw.js", { scope: "/" })
      .then((reg) => {
        if (ctrl && reg.waiting) {
          reg.waiting.postMessage({ type: "SKIP_WAITING" });
        }
      })
      .catch(() => {});
  }, []);

  return null;
}
