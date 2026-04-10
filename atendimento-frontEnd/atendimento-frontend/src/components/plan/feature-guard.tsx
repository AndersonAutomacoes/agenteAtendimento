"use client";

import { useTranslations } from "next-intl";
import * as React from "react";

import { cn } from "@/lib/utils";
import { type PlanTier, planMeetsRequirement } from "@/lib/plan-tier";

import { usePlan } from "./plan-provider";
import { UpgradePlanPanel } from "./upgrade-plan-panel";

export type FeatureGuardProps = {
  /** Plano mínimo para usar o conteúdo sem bloqueio. */
  requiredPlan: PlanTier;
  children: React.ReactNode;
  className?: string;
  /** Destino do botão de upgrade (ex.: página de billing). */
  upgradeHref?: string;
};

export function FeatureGuard({
  requiredPlan,
  children,
  className,
  upgradeHref = "/settings",
}: FeatureGuardProps) {
  const t = useTranslations("plan");
  const { tier } = usePlan();

  const allowed = planMeetsRequirement(tier, requiredPlan);

  if (allowed) {
    return <>{children}</>;
  }

  return (
    <div className={cn("relative min-h-[12rem] overflow-hidden rounded-xl", className)}>
      <div
        className="pointer-events-none select-none opacity-[0.22] blur-[0.5px]"
        aria-hidden
      >
        {children}
      </div>
      <div
        className="absolute inset-0 flex flex-col items-center justify-center bg-background/88 px-4 py-8 backdrop-blur-[2px]"
        role="region"
        aria-label={t("lockedAria")}
      >
        <UpgradePlanPanel requiredPlan={requiredPlan} upgradeHref={upgradeHref} className="py-4" />
      </div>
    </div>
  );
}
