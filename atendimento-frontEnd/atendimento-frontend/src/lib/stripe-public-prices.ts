import type { ProfileLevel } from "@/services/apiService";

/** Tiers cobrados via Stripe no produto (não inclui COMERCIAL). */
export type StripePaidTier = Exclude<ProfileLevel, "COMERCIAL">;

export type BillingIntervalUi = "MONTH" | "YEAR";

/**
 * Price IDs públicos para o Checkout (alinhar com `stripe.price-tier` no backend).
 * Definir env no deploy: NEXT_PUBLIC_STRIPE_PRICE_BASIC_MONTHLY, etc.
 */
export function publicStripePriceId(
  tier: StripePaidTier,
  interval: BillingIntervalUi,
): string | undefined {
  const tierKey = tier === "BASIC" ? "BASIC" : tier === "PRO" ? "PRO" : "ULTRA";
  const intKey = interval === "MONTH" ? "MONTHLY" : "YEARLY";
  const name = `NEXT_PUBLIC_STRIPE_PRICE_${tierKey}_${intKey}`;
  const v = process.env[name];
  return typeof v === "string" && v.trim().length > 0 ? v.trim() : undefined;
}
