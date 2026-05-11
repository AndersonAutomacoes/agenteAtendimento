"use client";

import * as React from "react";
import { useLocale, useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { Link } from "@/i18n/navigation";
import { TENANT_STORAGE_KEY } from "@/lib/auth-session";
import {
  type BillingIntervalUi,
  type StripePaidTier,
  publicStripePriceId,
} from "@/lib/stripe-public-prices";
import { cn } from "@/lib/utils";
import {
  CEREBRO_AUTH_TOKEN_KEY,
  postBillingCheckoutSession,
  toUserFacingApiError,
} from "@/services/apiService";

const TIERS: StripePaidTier[] = ["BASIC", "PRO", "ULTRA"];
type PublicPriceInfo = {
  priceId: string;
  currency: string;
  unitAmount: number;
};
type PublicPricesByTier = Partial<Record<StripePaidTier, Partial<Record<BillingIntervalUi, PublicPriceInfo>>>>;

function tierTranslationKey(t: StripePaidTier): "tierBasic" | "tierPro" | "tierUltra" {
  return t === "BASIC" ? "tierBasic" : t === "PRO" ? "tierPro" : "tierUltra";
}

function tierFeatures(
  t: StripePaidTier,
): ("featureDashboard" | "featureMonitoring" | "featureKb" | "featureAnalytics")[] {
  if (t === "BASIC") {
    return ["featureDashboard", "featureMonitoring"];
  }
  if (t === "PRO") {
    return ["featureDashboard", "featureMonitoring", "featureKb"];
  }
  return ["featureDashboard", "featureMonitoring", "featureKb", "featureAnalytics"];
}

export default function PricingPage() {
  const locale = useLocale();
  const t = useTranslations("billing");
  const tApi = useTranslations("api");
  const [interval, setInterval] = React.useState<BillingIntervalUi>("MONTH");
  const [busy, setBusy] = React.useState<string | null>(null);
  const [publicPrices, setPublicPrices] = React.useState<PublicPricesByTier>({});
  const [publicPricesLoaded, setPublicPricesLoaded] = React.useState(false);

  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);

  React.useEffect(() => {
    let active = true;
    (async () => {
      try {
        const res = await fetch("/api/v1/billing/public-prices", { method: "GET" });
        const json = (await res.json().catch(() => ({}))) as {
          prices?: Array<{
            tier?: string;
            interval?: string;
            priceId?: string;
            currency?: string;
            unitAmount?: number;
          }>;
        };
        if (!active || !res.ok || !Array.isArray(json.prices)) {
          setPublicPricesLoaded(true);
          return;
        }
        const next: PublicPricesByTier = {};
        for (const p of json.prices) {
          const tier = p.tier;
          const int = p.interval;
          if (
            (tier === "BASIC" || tier === "PRO" || tier === "ULTRA") &&
            (int === "MONTH" || int === "YEAR") &&
            typeof p.priceId === "string" &&
            p.priceId.trim() &&
            typeof p.unitAmount === "number" &&
            Number.isFinite(p.unitAmount)
          ) {
            next[tier] ??= {};
            next[tier][int] = {
              priceId: p.priceId.trim(),
              currency: typeof p.currency === "string" && p.currency.trim() ? p.currency.trim() : "USD",
              unitAmount: p.unitAmount,
            };
          }
        }
        if (active) {
          setPublicPrices(next);
          setPublicPricesLoaded(true);
        }
      } catch {
        if (active) setPublicPricesLoaded(true);
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  const priceLabel = React.useCallback(
    (tier: StripePaidTier, int: BillingIntervalUi): string | null => {
      const info = publicPrices[tier]?.[int];
      if (!info) return null;
      const value = info.unitAmount / 100;
      const formatted = new Intl.NumberFormat(locale, {
        style: "currency",
        currency: info.currency.toUpperCase(),
        minimumFractionDigits: 0,
      }).format(value);
      return `${formatted} · ${int === "MONTH" ? t("intervalMonthly") : t("intervalYearly")}`;
    },
    [locale, publicPrices, t],
  );

  const subscribe = React.useCallback(
    async (tier: StripePaidTier) => {
      const fromStripe = publicPrices[tier]?.[interval]?.priceId;
      const priceId = fromStripe ?? publicStripePriceId(tier, interval);
      if (!priceId) {
        toast.error(t("priceNotConfigured"));
        return;
      }
      let tenantId: string | null = null;
      try {
        tenantId = localStorage.getItem(TENANT_STORAGE_KEY)?.trim() || null;
      } catch {
        tenantId = null;
      }
      let token: string | null = null;
      try {
        token = localStorage.getItem(CEREBRO_AUTH_TOKEN_KEY);
      } catch {
        token = null;
      }
      if (!tenantId || !token) {
        toast.error(t("needLogin"));
        return;
      }
      const busyKey = `${tier}-${interval}`;
      setBusy(busyKey);
      try {
        const { url } = await postBillingCheckoutSession(tenantId, priceId);
        window.location.href = url;
      } catch (e) {
        toast.error(toUserFacingApiError(e, translateApi));
      } finally {
        setBusy(null);
      }
    },
    [interval, publicPrices, t, translateApi],
  );

  return (
    <div className="flex min-h-full flex-col bg-background px-4 py-10">
      <div className="mx-auto w-full max-w-6xl space-y-8">
        <div className="text-center space-y-2">
          <h1 className="text-3xl font-semibold tracking-tight">{t("pricingTitle")}</h1>
          <p className="mx-auto max-w-2xl text-sm text-muted-foreground">{t("pricingSubtitle")}</p>
          <div className="flex justify-center gap-2 pt-2">
            <Button
              type="button"
              variant={interval === "MONTH" ? "default" : "outline"}
              size="sm"
              className={cn("rounded-full")}
              onClick={() => setInterval("MONTH")}
            >
              {t("intervalMonthly")}
            </Button>
            <Button
              type="button"
              variant={interval === "YEAR" ? "default" : "outline"}
              size="sm"
              className="rounded-full"
              onClick={() => setInterval("YEAR")}
            >
              {t("intervalYearly")}
            </Button>
          </div>
        </div>

        <div className="grid gap-6 sm:grid-cols-3">
          {TIERS.map((tier) => {
            const pid = publicPrices[tier]?.[interval]?.priceId ?? publicStripePriceId(tier, interval);
            const visiblePrice = priceLabel(tier, interval);
            const disabled = !pid || busy !== null;
            const busyHere = busy === `${tier}-${interval}`;
            const featureKeys = tierFeatures(tier);
            return (
              <Card key={tier} className="flex flex-col border-border/80 bg-card shadow-md">
                <CardHeader className="space-y-1">
                  <p className="text-lg font-semibold">{t(tierTranslationKey(tier))}</p>
                  <p className="text-sm font-medium text-foreground min-h-5">
                    {visiblePrice ?? "\u00a0"}
                  </p>
                  <p className="text-xs text-muted-foreground min-h-4">
                    {!pid ? t("priceNotConfigured") : !visiblePrice && !publicPricesLoaded ? "..." : "\u00a0"}
                  </p>
                </CardHeader>
                <CardContent className="flex-1 space-y-2">
                  <ul className="list-inside list-disc space-y-1 text-sm text-muted-foreground">
                    {featureKeys.map((fk) => (
                      <li key={fk}>{t(fk)}</li>
                    ))}
                  </ul>
                </CardContent>
                <CardFooter className="flex-col gap-2">
                  <Button
                    type="button"
                    className="w-full touch-manipulation"
                    disabled={disabled}
                    onClick={() => subscribe(tier)}
                  >
                    {busyHere ? (
                      <Loader2 className="mr-2 h-4 w-4 shrink-0 animate-spin" aria-hidden />
                    ) : null}
                    {t("subscribe")}
                  </Button>
                </CardFooter>
              </Card>
            );
          })}
        </div>

        <p className="text-center text-xs text-muted-foreground">
          <Link href="/login" className="underline-offset-4 hover:underline">
            {t("needLogin")}
          </Link>
        </p>
      </div>
    </div>
  );
}
