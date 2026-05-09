"use client";

import { signInWithEmailAndPassword } from "firebase/auth";
import { Loader2 } from "lucide-react";
import * as React from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";

import { AppLogo } from "@/components/brand/app-logo";
import { PasswordInput } from "@/components/auth/password-input";
import { usePlan } from "@/components/plan/plan-provider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { readFirebaseErrorCode, isLikelyEmail } from "@/lib/email-format";
import { Link, useRouter } from "@/i18n/navigation";
import { getFirebaseAuth, isFirebaseConfigured } from "@/lib/firebase";
import { mapProfileLevelToPlanTier } from "@/lib/plan-tier";
import { cn } from "@/lib/utils";
import { setBillingBlockedCookieClient } from "@/lib/billing-cookie";
import {
  CEREBRO_AUTH_TOKEN_KEY,
  getPortalSession,
  toUserFacingApiError,
} from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";

export default function LoginPage() {
  const t = useTranslations("loginPage");
  const tNav = useTranslations("nav");
  const tCommon = useTranslations("common");
  const tApi = useTranslations("api");
  const router = useRouter();
  const { setTier, setFeatures, setProfileLevel } = usePlan();
  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [emailError, setEmailError] = React.useState<string | null>(null);
  const [passwordError, setPasswordError] = React.useState<string | null>(null);
  const [formError, setFormError] = React.useState<string | null>(null);
  const emailRef = React.useRef<HTMLInputElement>(null);
  const passwordRef = React.useRef<HTMLInputElement>(null);
  const [submitting, setSubmitting] = React.useState(false);
  const submittingRef = React.useRef(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setEmailError(null);
    setPasswordError(null);
    setFormError(null);
    if (!isFirebaseConfigured()) {
      toast.error(t("firebaseNotConfigured"));
      return;
    }
    if (!isLikelyEmail(email)) {
      setEmailError(t("invalidEmail"));
      emailRef.current?.focus();
      return;
    }
    if (!password) {
      setPasswordError(t("passwordRequired"));
      passwordRef.current?.focus();
      return;
    }
    if (submittingRef.current) return;
    submittingRef.current = true;
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
      setBillingBlockedCookieClient(session.billing.blocked);
      toast.success(t("success"));
      router.push("/");
    } catch (err) {
      const code = readFirebaseErrorCode(err);
      if (code === "auth/invalid-email") {
        setEmailError(t("invalidEmail"));
        emailRef.current?.focus();
      } else if (code === "auth/wrong-password" || code === "auth/invalid-credential") {
        setFormError(t("credentialMismatch"));
        passwordRef.current?.focus();
      } else if (code === "auth/user-not-found") {
        setFormError(t("credentialMismatch"));
        emailRef.current?.focus();
      } else {
        toast.error(toUserFacingApiError(err, translateApi));
      }
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-full flex-col items-center justify-center bg-background p-6">
      <div className="w-full max-w-md space-y-6 rounded-2xl border border-border bg-card p-8 shadow-lg">
        <div className="flex flex-col items-center gap-6 text-center">
          <Link
            href="/landing"
            aria-label={tNav("logoLinkHome")}
            className="rounded-xl outline-none ring-offset-background focus-visible:ring-2 focus-visible:ring-ring"
          >
            <AppLogo variant="auth" />
          </Link>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
            <p className="mt-2 text-sm text-muted-foreground">{t("subtitle")}</p>
          </div>
        </div>
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {formError ? (
            <p className="text-sm text-destructive" role="alert">
              {formError}
            </p>
          ) : null}
          <div className="space-y-2">
            <Label htmlFor="login-email">{t("email")}</Label>
            <Input
              ref={emailRef}
              id="login-email"
              name="email"
              type="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                setEmailError(null);
                setFormError(null);
              }}
              autoComplete="email"
              aria-invalid={Boolean(emailError)}
              aria-describedby={emailError ? "login-email-err" : undefined}
              className="min-h-11"
            />
            {emailError ? (
              <p id="login-email-err" className="text-sm text-destructive" role="alert">
                {emailError}
              </p>
            ) : null}
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
            <PasswordInput
              ref={passwordRef}
              id="login-password"
              name="password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setPasswordError(null);
                setFormError(null);
              }}
              autoComplete="current-password"
              toggleShowLabel={tCommon("showPassword")}
              toggleHideLabel={tCommon("hidePassword")}
              aria-invalid={Boolean(passwordError)}
              aria-describedby={passwordError ? "login-password-err" : undefined}
            />
            {passwordError ? (
              <p id="login-password-err" className="text-sm text-destructive" role="alert">
                {passwordError}
              </p>
            ) : null}
          </div>
          <Button
            type="submit"
            className={cn("w-full gap-2", submitting && "pointer-events-none opacity-90")}
            aria-busy={submitting}
          >
            {submitting ? (
              <Loader2 className="h-4 w-4 shrink-0 animate-spin" aria-hidden />
            ) : null}
            {submitting ? t("submitProgress") : t("submit")}
          </Button>
        </form>
        <p className="text-center text-sm text-muted-foreground">
          <Link href="/landing" className="underline-offset-4 hover:underline">
            {t("discoverProduct")}
          </Link>
          <span className="mx-2 text-border">·</span>
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
