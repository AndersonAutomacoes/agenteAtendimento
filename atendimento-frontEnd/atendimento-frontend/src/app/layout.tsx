import type { ReactNode } from "react";

import { routing } from "@/i18n/routing";

import "./globals.css";

/** Root layout must provide the document shell. */
export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang={routing.defaultLocale} suppressHydrationWarning>
      <body>{children}</body>
    </html>
  );
}
