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
  CEREBRO_AUTH_TOKEN_KEY,
  postBillingPortalSession,
  toUserFacingApiError,
} from "@/services/apiService";

export default function BillingSuspendedPage() {
  const t = useTranslations("billing");
  const tApi = useTranslations("api");
  const [portalBusy, setPortalBusy] = React.useState(false);
  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);

  const openPortal = React.useCallback(async () => {
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
    setPortalBusy(true);
    try {
      const { url } = await postBillingPortalSession(tenantId);
      window.location.href = url;
    } catch (e) {
      toast.error(toUserFacingApiError(e, translateApi));
    } finally {
      setPortalBusy(false);
    }
  }, [t, translateApi]);

  return (
    <div className="flex min-h-full flex-col items-center justify-center bg-background px-4 py-12">
      <Card className="w-full max-w-lg border-border/80 shadow-lg">
        <CardHeader>
          <h1 className="text-xl font-semibold">{t("suspendedTitle")}</h1>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground leading-relaxed">{t("suspendedBody")}</p>
        </CardContent>
        <CardFooter className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <Button asChild className="w-full sm:flex-1">
            <Link href="/pricing">{t("suspendedCtaPricing")}</Link>
          </Button>
          <Button
            type="button"
            variant="secondary"
            className="w-full sm:flex-1"
            disabled={portalBusy}
            onClick={() => openPortal()}
          >
            {portalBusy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden /> : null}
            {t("suspendedCtaPortal")}
          </Button>
          <Button asChild variant="outline" className="w-full sm:flex-1">
            <Link href="/login">{t("suspendedCtaLogin")}</Link>
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
