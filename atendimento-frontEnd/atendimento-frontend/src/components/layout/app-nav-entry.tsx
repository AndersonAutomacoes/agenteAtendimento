"use client";

import { Lock } from "lucide-react";
import { useTranslations } from "next-intl";

import { usePlan } from "@/components/plan/plan-provider";
import { Link, usePathname } from "@/i18n/navigation";
import { planMeetsRequirement, type PlanTier } from "@/lib/plan-tier";
import { cn } from "@/lib/utils";

import type { AppNavItem } from "./nav-config";

type AppNavEntryProps = {
  item: AppNavItem;
  /** Fecha drawer mobile após navegação. */
  onNavigate?: () => void;
  touchPadding?: boolean;
};

export function AppNavEntry({ item, onNavigate, touchPadding }: AppNavEntryProps) {
  const { tier } = usePlan();
  const pathname = usePathname();
  const t = useTranslations("nav");
  const tPlan = useTranslations("plan");

  const minPlan: PlanTier = item.minPlan ?? "starter";
  const locked = !planMeetsRequirement(tier, minPlan);

  const label = t(item.labelKey);
  const sub = item.subKey ? t(item.subKey) : undefined;
  const Icon = item.icon;
  const active =
    item.href === "/"
      ? pathname === "/"
      : pathname === item.href || pathname.startsWith(`${item.href}/`);

  const baseClass = cn(
    "flex items-center gap-3 rounded-xl px-3 text-sm font-medium transition-colors",
    touchPadding ? "min-h-11 py-3 touch-manipulation" : "py-2.5",
    active
      ? "bg-primary/15 text-foreground shadow-sm ring-1 ring-primary/25"
      : "text-muted-foreground hover:bg-accent/60 hover:text-foreground",
    locked &&
      "cursor-not-allowed opacity-60 hover:bg-transparent hover:text-muted-foreground",
  );

  if (locked) {
    return (
      <div
        className={baseClass}
        role="link"
        aria-disabled="true"
        title={tPlan("navLockedHint")}
      >
        <Icon className="h-4 w-4 shrink-0" aria-hidden />
        <span className="flex min-w-0 flex-col leading-tight">
          <span className="inline-flex items-center gap-1.5">
            {label}
            <Lock className="h-3.5 w-3.5 shrink-0 opacity-80" aria-hidden />
          </span>
          {sub ? (
            <span className="text-[11px] font-normal text-muted-foreground">{sub}</span>
          ) : null}
        </span>
      </div>
    );
  }

  return (
    <Link href={item.href} onClick={onNavigate} className={baseClass}>
      <Icon className="h-4 w-4 shrink-0" aria-hidden />
      <span className="flex min-w-0 flex-col leading-tight">
        <span>{label}</span>
        {sub ? (
          <span className="text-[11px] font-normal text-muted-foreground">{sub}</span>
        ) : null}
      </span>
    </Link>
  );
}
