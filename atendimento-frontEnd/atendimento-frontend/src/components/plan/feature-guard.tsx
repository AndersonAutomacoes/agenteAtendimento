"use client";

import { useTranslations } from "next-intl";
import * as React from "react";
import { Loader2 } from "lucide-react";

import type { PlanFeatureKey, ProfileLevel } from "@/services/apiService";
import { cn } from "@/lib/utils";
import { type PlanTier, planMeetsRequirement } from "@/lib/plan-tier";

import { usePlan } from "./plan-provider";
import { UpgradePlanPanel } from "./upgrade-plan-panel";

export type FeatureGuardProps = {
  /** Plano mínimo para usar o conteúdo sem bloqueio. */
  requiredPlan: PlanTier;
  /** Perfil específico exigido (ex.: COMERCIAL para backoffice interno). */
  requiredProfile?: ProfileLevel;
  /** Feature flag vinda do backend (source of truth quando presente). */
  requiredFeature?: PlanFeatureKey;
  children: React.ReactNode;
  className?: string;
  /** Destino do botão de upgrade (ex.: página de billing). */
  upgradeHref?: string;
};

export function FeatureGuard({
  requiredPlan,
  requiredProfile,
  requiredFeature,
  children,
  className,
  upgradeHref = "/settings",
}: FeatureGuardProps) {
  const t = useTranslations("plan");
  const { tier, profileLevel, features, featuresHydrated } = usePlan();
  const [mounted, setMounted] = React.useState(false);

  React.useEffect(() => {
    setMounted(true);
  }, []);

  if (requiredFeature && (!mounted || !featuresHydrated)) {
    return (
      <div className={cn("flex min-h-[12rem] items-center justify-center rounded-xl border border-border/60", className)}>
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          <span>{t("checkingAccess")}</span>
        </div>
      </div>
    );
  }

  const allowed =
    (requiredProfile == null || profileLevel === requiredProfile) &&
    (requiredFeature == null
      ? planMeetsRequirement(tier, requiredPlan)
      : features[requiredFeature] === true);

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
