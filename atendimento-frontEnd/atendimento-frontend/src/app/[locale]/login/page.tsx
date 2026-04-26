"use client";

import { signInWithEmailAndPassword } from "firebase/auth";
import * as React from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";

import { usePlan } from "@/components/plan/plan-provider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Link, useRouter } from "@/i18n/navigation";
import { getFirebaseAuth, isFirebaseConfigured } from "@/lib/firebase";
import { mapProfileLevelToPlanTier } from "@/lib/plan-tier";
import {
  CEREBRO_AUTH_TOKEN_KEY,
  getPortalSession,
  toUserFacingApiError,
} from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";

export default function LoginPage() {
  const t = useTranslations("loginPage");
  const tApi = useTranslations("api");
  const router = useRouter();
  const { setTier, setFeatures, setProfileLevel } = usePlan();
  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [submitting, setSubmitting] = React.useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isFirebaseConfigured()) {
      toast.error(t("firebaseNotConfigured"));
      return;
    }
    setSubmitting(true);
    try {
      const auth = getFirebaseAuth();
      const cred = await signInWithEmailAndPassword(auth, email.trim(), password);
      const token = await cred.user.getIdToken();
      try {
        localStorage.setItem(CEREBRO_AUTH_TOKEN_KEY, token);
      } catch {
        /* ignore */
      }
      const session = await getPortalSession();
      try {
        localStorage.setItem(TENANT_STORAGE_KEY, session.tenantId.trim());
      } catch {
        /* ignore */
      }
      setTier(mapProfileLevelToPlanTier(session.profileLevel));
      setProfileLevel(session.profileLevel);
      setFeatures(session.features ?? {});
      toast.success(t("success"));
      router.push("/");
    } catch (err) {
      toast.error(toUserFacingApiError(err, translateApi));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-full flex-col items-center justify-center bg-background p-6">
      <div className="w-full max-w-md space-y-6 rounded-2xl border border-border bg-card p-8 shadow-lg">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
          <p className="mt-2 text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="login-email">{t("email")}</Label>
            <Input
              id="login-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              required
            />
          </div>
          <div className="space-y-2">
            <div className="flex items-center justify-between gap-2">
              <Label htmlFor="login-password">{t("password")}</Label>
              <Link
                href="/forgot-password"
                className="text-xs text-muted-foreground underline-offset-4 hover:underline"
              >
                {t("forgotPassword")}
              </Link>
            </div>
            <Input
              id="login-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </div>
          <Button type="submit" className="w-full" disabled={submitting}>
            {t("submit")}
          </Button>
        </form>
        <p className="text-center text-sm text-muted-foreground">
          <Link href="/register" className="underline-offset-4 hover:underline">
            {t("noAccount")}
          </Link>
        </p>
        <Button variant="ghost" asChild className="w-full">
          <Link href="/">{t("backHome")}</Link>
        </Button>
      </div>
    </div>
  );
}
