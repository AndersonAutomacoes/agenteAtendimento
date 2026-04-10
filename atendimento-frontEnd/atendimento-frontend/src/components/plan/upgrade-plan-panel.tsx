"use client";

import { Lock } from "lucide-react";
import { useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";
import { Link } from "@/i18n/navigation";
import { cn } from "@/lib/utils";
import type { PlanTier } from "@/lib/plan-tier";

export type UpgradePlanPanelProps = {
  requiredPlan: PlanTier;
  upgradeHref?: string;
  className?: string;
};

export function UpgradePlanPanel({
  requiredPlan,
  upgradeHref = "/settings",
  className,
}: UpgradePlanPanelProps) {
  const t = useTranslations("plan");

  return (
    <div
      className={cn("flex flex-col items-center justify-center gap-3 px-4 py-2 text-center", className)}
    >
      <div className="flex h-12 w-12 items-center justify-center rounded-full border border-border/80 bg-muted/50 text-muted-foreground shadow-inner">
        <Lock className="h-5 w-5" aria-hidden />
      </div>
      <div className="max-w-sm space-y-1">
        <p className="text-sm font-semibold text-foreground">{t("lockedTitle")}</p>
        <p className="text-xs text-muted-foreground leading-relaxed">
          {t(`requiredPlanHint.${requiredPlan}`)}
        </p>
      </div>
      <Button asChild size="sm" className="mt-1 touch-manipulation">
        <Link href={upgradeHref}>{t("upgradeCta")}</Link>
      </Button>
    </div>
  );
}
