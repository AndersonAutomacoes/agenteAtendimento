"use client";

import { Toaster } from "sonner";

import { useTheme } from "@/components/theme/app-theme-provider";

/** Toasts alinhados ao tema resolvido (claro / escuro). */
export function ToasterBridge() {
  const { resolvedTheme } = useTheme();
  return (
    <Toaster
      richColors
      theme={resolvedTheme}
      position="top-right"
      closeButton
    />
  );
}
