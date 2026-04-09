import type { AppLocale } from "@/i18n/routing";

const BCP47: Record<AppLocale, string> = {
  "pt-BR": "pt-BR",
  en: "en-US",
  es: "es",
  "zh-CN": "zh-CN",
};

/** Locale para `toLocaleString` / datas (BCP 47). */
export function toBcp47ForDates(appLocale: string): string {
  const key = appLocale as AppLocale;
  return BCP47[key] ?? "pt-BR";
}
