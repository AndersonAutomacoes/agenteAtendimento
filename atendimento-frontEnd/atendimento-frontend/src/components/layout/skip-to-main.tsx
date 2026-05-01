"use client";

import { useTranslations } from "next-intl";

/** Primeiro elemento focável recomendado para leitores de ecrã (WCAG / Web Interface Guidelines). */
export function SkipToMain() {
  const t = useTranslations("nav");

  return (
    <a
      href="#main-content"
      className="bg-primary text-primary-foreground rounded-md px-4 py-2 text-sm font-medium shadow-md outline-none ring-offset-background focus-visible:ring-2 focus-visible:ring-ring absolute top-4 start-[10000px] z-[100] focus-visible:start-4"
    >
      {t("skipToMain")}
    </a>
  );
}
