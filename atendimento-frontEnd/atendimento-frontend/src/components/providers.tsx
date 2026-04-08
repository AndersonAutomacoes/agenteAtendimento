"use client";

import { Toaster } from "sonner";

import { AppThemeProvider } from "@/components/theme/app-theme-provider";

type ProvidersProps = {
  children: React.ReactNode;
};

/** Tema via classe em &lt;html&gt; — sem &lt;script&gt; injetado por React (compatível com React 19). */
export function Providers({ children }: ProvidersProps) {
  return (
    <AppThemeProvider>
      {children}
      <Toaster richColors position="top-right" closeButton />
    </AppThemeProvider>
  );
}
