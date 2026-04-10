"use client";

import { useTranslations } from "next-intl";

import { Separator } from "@/components/ui/separator";
import { Link } from "@/i18n/navigation";

import { AppNavEntry } from "./app-nav-entry";
import { NavAuthFooter } from "./nav-auth-footer";
import { APP_NAV_ITEMS, NAV_BRAIN_ICON } from "./nav-config";

export function AppSidebar() {
  const t = useTranslations("nav");

  return (
    <aside className="flex h-screen w-64 shrink-0 flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground shadow-[4px_0_24px_-8px_rgba(0,0,0,0.35)]">
      <div className="flex h-16 items-center gap-2 border-b border-sidebar-border px-4">
        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary/15 text-primary">
          <NAV_BRAIN_ICON className="h-5 w-5" aria-hidden />
        </div>
        <div className="min-w-0">
          <Link
            href="/"
            className="block truncate font-semibold tracking-tight"
          >
            {t("brand")}
          </Link>
          <p className="truncate text-[11px] text-muted-foreground">
            {t("brandSubtitle")}
          </p>
        </div>
      </div>
      <nav className="flex flex-1 flex-col gap-0.5 overflow-y-auto p-3">
        {APP_NAV_ITEMS.map((item) => (
          <AppNavEntry key={item.href} item={item} />
        ))}
      </nav>
      <div className="px-3 pb-2">
        <NavAuthFooter variant="sidebar" />
      </div>
      <Separator className="bg-sidebar-border" />
      <p className="p-4 text-xs leading-relaxed text-muted-foreground">
        {t("footer")}
      </p>
    </aside>
  );
}
