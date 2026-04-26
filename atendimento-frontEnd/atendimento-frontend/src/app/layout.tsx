import type { ReactNode } from "react";
import "./globals.css";

/** Root layout must provide the document shell. */
export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR" suppressHydrationWarning>
      <body>{children}</body>
    </html>
  );
}
