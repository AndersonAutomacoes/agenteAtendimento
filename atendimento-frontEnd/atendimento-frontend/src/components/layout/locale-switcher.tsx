"use client";

import { useLocale, useTranslations } from "next-intl";

import { usePathname, useRouter } from "@/i18n/navigation";
import { routing } from "@/i18n/routing";
import { cn } from "@/lib/utils";

export function LocaleSwitcher() {
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const tNames = useTranslations("localeNames");
  const tSw = useTranslations("localeSwitcher");

  return (
    <select
      aria-label={tSw("aria")}
      value={locale}
      onChange={(e) => {
        router.replace(pathname, { locale: e.target.value });
      }}
      className={cn(
        "h-9 rounded-lg border border-input bg-background px-2 text-xs font-medium",
        "focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring",
      )}
    >
      {routing.locales.map((loc) => (
        <option key={loc} value={loc}>
          {tNames(loc)}
        </option>
      ))}
    </select>
  );
}
