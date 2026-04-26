/** Níveis de plano (ordem crescente de acesso). */
export const PLAN_TIERS = ["starter", "pro", "enterprise"] as const;
export type PlanTier = (typeof PLAN_TIERS)[number];

export const PLAN_STORAGE_KEY = "cerebro-plan-tier";

export const PLAN_CHANGED_EVENT = "cerebro-plan-tier-changed";

export function tierRank(tier: PlanTier): number {
  const i = PLAN_TIERS.indexOf(tier);
  return i < 0 ? 0 : i;
}

export function parsePlanTier(raw: string | null | undefined): PlanTier | null {
  if (!raw) return null;
  const t = raw.trim().toLowerCase();
  if (t === "starter" || t === "pro" || t === "enterprise") {
    return t;
  }
  return null;
}

/** true se current >= required */
export function planMeetsRequirement(current: PlanTier, required: PlanTier): boolean {
  return tierRank(current) >= tierRank(required);
}

export function readDefaultPlanTierFromEnv(): PlanTier {
  const raw =
    typeof process !== "undefined"
      ? process.env.NEXT_PUBLIC_DEFAULT_PLAN_TIER?.trim().toLowerCase()
      : undefined;
  return parsePlanTier(raw ?? null) ?? "pro";
}

/** Perfil do backend (`tenant_configuration.profile_level`) → tier da UI. */
export function mapProfileLevelToPlanTier(profileLevel: string): PlanTier {
  const u = profileLevel.trim().toUpperCase();
  if (u === "BASIC") return "starter";
  if (u === "PRO") return "pro";
  if (u === "ULTRA" || u === "COMERCIAL") return "enterprise";
  return readDefaultPlanTierFromEnv();
}
