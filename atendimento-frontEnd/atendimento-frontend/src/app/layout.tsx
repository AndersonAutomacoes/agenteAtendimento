import type { ReactNode } from "react";

/** Raiz sem `<html>`: o segmento `[locale]` fornece documento e idioma. */
export default function RootLayout({ children }: { children: ReactNode }) {
  return children;
}
