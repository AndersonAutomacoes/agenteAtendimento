"use client";

import * as React from "react";
import { useTranslations } from "next-intl";
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
  const t = useTranslations("billing");
  const tApi = useTranslations("api");
  const [interval, setInterval] = React.useState<BillingIntervalUi>("MONTH");
  const [busy, setBusy] = React.useState<string | null>(null);

  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);

  const subscribe = React.useCallback(
    async (tier: StripePaidTier) => {
      const priceId = publicStripePriceId(tier, interval);
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
    [interval, t, translateApi],
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
            const pid = publicStripePriceId(tier, interval);
            const disabled = !pid || busy !== null;
            const busyHere = busy === `${tier}-${interval}`;
            const featureKeys = tierFeatures(tier);
            return (
              <Card key={tier} className="flex flex-col border-border/80 bg-card shadow-md">
                <CardHeader className="space-y-1">
                  <p className="text-lg font-semibold">{t(tierTranslationKey(tier))}</p>
                  <p className="text-xs text-muted-foreground">
                    {!pid ? t("priceNotConfigured") : "\u00a0"}
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
