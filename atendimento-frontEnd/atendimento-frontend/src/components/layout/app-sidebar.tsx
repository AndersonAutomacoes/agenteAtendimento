"use client";

import { useTranslations } from "next-intl";

import { AppLogo } from "@/components/brand/app-logo";
import { Separator } from "@/components/ui/separator";
import { Link } from "@/i18n/navigation";

import { AppNavEntry } from "./app-nav-entry";
import { NavAuthFooter } from "./nav-auth-footer";
import { APP_NAV_ITEMS } from "./nav-config";

export function AppSidebar() {
  const t = useTranslations("nav");

  return (
    <aside className="flex h-screen w-64 shrink-0 flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground shadow-[4px_0_24px_-8px_rgba(0,0,0,0.35)]">
      <div className="flex h-16 items-center border-b border-sidebar-border px-3">
        <Link
          href="/"
          aria-label={t("logoLinkHome")}
          className="flex min-w-0 flex-1 items-center rounded-lg outline-none ring-offset-background focus-visible:ring-2 focus-visible:ring-ring"
        >
          <AppLogo variant="navigation" className="justify-start" />
        </Link>
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
