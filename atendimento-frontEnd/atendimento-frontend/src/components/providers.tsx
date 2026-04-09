"use client";

import { AppThemeProvider } from "@/components/theme/app-theme-provider";
import { ToasterBridge } from "@/components/toaster-bridge";

type ProvidersProps = {
  children: React.ReactNode;
};

/** Tema via classe em &lt;html&gt; — sem &lt;script&gt; injetado por React (compatível com React 19). */
export function Providers({ children }: ProvidersProps) {
  return (
    <AppThemeProvider>
      {children}
      <ToasterBridge />
    </AppThemeProvider>
  );
}
