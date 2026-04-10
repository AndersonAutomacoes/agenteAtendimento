"use client";

import { Menu, X } from "lucide-react";
import { useTranslations } from "next-intl";
import * as React from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog";
import { Separator } from "@/components/ui/separator";
import { Link } from "@/i18n/navigation";
import { cn } from "@/lib/utils";

import { AppNavEntry } from "./app-nav-entry";
import { NavAuthFooter } from "./nav-auth-footer";
import { APP_NAV_ITEMS, NAV_BRAIN_ICON } from "./nav-config";

export function MobileNavDrawer() {
  const [open, setOpen] = React.useState(false);
  const t = useTranslations("nav");

  const menuId = "mobile-main-menu";

  return (
    <>
      <Button
        type="button"
        variant="outline"
        size="icon"
        className="h-11 w-11 shrink-0 md:hidden"
        aria-expanded={open}
        aria-controls={menuId}
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
      >
        <Menu className="h-5 w-5" aria-hidden />
        <span className="sr-only">{t("openMenu")}</span>
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent
        id={menuId}
        hideClose
        className={cn(
          "top-0 left-0 flex h-full max-h-[100dvh] w-[min(100vw,20rem)] max-w-[min(100vw,20rem)] translate-x-0 translate-y-0 flex-col gap-0 rounded-none border-r p-0 shadow-2xl sm:max-w-[min(100vw,20rem)] sm:rounded-none",
        )}
      >
        <DialogTitle className="sr-only">{t("menuTitle")}</DialogTitle>
        <DialogDescription className="sr-only">{t("brandSubtitle")}</DialogDescription>
        <div className="flex h-16 shrink-0 items-center justify-between gap-2 border-b border-sidebar-border px-4">
          <div className="flex min-w-0 items-center gap-2">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary/15 text-primary">
              <NAV_BRAIN_ICON className="h-5 w-5" aria-hidden />
            </div>
            <div className="min-w-0">
              <Link
                href="/"
                className="block truncate font-semibold tracking-tight"
                onClick={() => setOpen(false)}
              >
                {t("brand")}
              </Link>
              <p className="truncate text-[11px] text-muted-foreground">
                {t("brandSubtitle")}
              </p>
            </div>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="h-11 w-11 shrink-0"
            onClick={() => setOpen(false)}
            aria-label={t("closeMenu")}
          >
            <X className="h-5 w-5" />
          </Button>
        </div>
        <nav className="flex flex-1 flex-col gap-0.5 overflow-y-auto p-3">
          {APP_NAV_ITEMS.map((item) => (
            <AppNavEntry
              key={item.href}
              item={item}
              touchPadding
              onNavigate={() => setOpen(false)}
            />
          ))}
          <NavAuthFooter
            variant="drawer"
            onNavigate={() => setOpen(false)}
          />
        </nav>
        <Separator className="bg-sidebar-border" />
        <p className="p-4 text-xs leading-relaxed text-muted-foreground">
          {t("footer")}
        </p>
        </DialogContent>
      </Dialog>
    </>
  );
}
