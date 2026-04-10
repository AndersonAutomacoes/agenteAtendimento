"use client";

import { Brain } from "lucide-react";
import { useTranslations } from "next-intl";

import { Link } from "@/i18n/navigation";
import { cn } from "@/lib/utils";

import { LocaleSwitcher } from "./locale-switcher";
import { MobileNavDrawer } from "./mobile-nav-drawer";
import { ThemeToggle } from "./theme-toggle";

export function AppHeader() {
  const t = useTranslations("nav");

  return (
    <header
      className={cn(
        "flex h-14 shrink-0 items-center justify-between gap-2 border-b border-border/80 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80",
        "px-3 md:px-4",
      )}
    >
      <div className="flex min-w-0 flex-1 items-center gap-2">
        <MobileNavDrawer />
        <Link
          href="/"
          className="flex min-w-0 items-center gap-2 rounded-lg md:hidden"
        >
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary/15 text-primary">
            <Brain className="h-5 w-5" aria-hidden />
          </span>
          <span className="truncate font-semibold tracking-tight">
            {t("brand")}
          </span>
        </Link>
      </div>
      <div className="flex shrink-0 items-center gap-1.5 sm:gap-2">
        <LocaleSwitcher />
        <ThemeToggle />
      </div>
    </header>
  );
}
