import type { ProfileLevel } from "@/services/apiService";

/** Tiers cobrados via Stripe no produto (não inclui COMERCIAL). */
export type StripePaidTier = Exclude<ProfileLevel, "COMERCIAL">;

export type BillingIntervalUi = "MONTH" | "YEAR";

const PUBLIC_STRIPE_PRICE_IDS = {
  BASIC: {
    MONTH: process.env.NEXT_PUBLIC_STRIPE_PRICE_BASIC_MONTHLY,
    YEAR: process.env.NEXT_PUBLIC_STRIPE_PRICE_BASIC_YEARLY,
  },
  PRO: {
    MONTH: process.env.NEXT_PUBLIC_STRIPE_PRICE_PRO_MONTHLY,
    YEAR: process.env.NEXT_PUBLIC_STRIPE_PRICE_PRO_YEARLY,
  },
  ULTRA: {
    MONTH: process.env.NEXT_PUBLIC_STRIPE_PRICE_ULTRA_MONTHLY,
    YEAR: process.env.NEXT_PUBLIC_STRIPE_PRICE_ULTRA_YEARLY,
  },
} as const;

/**
 * Price IDs públicos para o Checkout (alinhar com `stripe.price-tier` no backend).
 * Definir env no deploy: NEXT_PUBLIC_STRIPE_PRICE_BASIC_MONTHLY, etc.
 */
export function publicStripePriceId(
  tier: StripePaidTier,
  interval: BillingIntervalUi,
): string | undefined {
  const v = PUBLIC_STRIPE_PRICE_IDS[tier][interval];
  return typeof v === "string" && v.trim().length > 0 ? v.trim() : undefined;
}
