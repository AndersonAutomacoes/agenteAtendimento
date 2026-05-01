"use client";

import {
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
} from "firebase/auth";
import { Loader2 } from "lucide-react";
import * as React from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";

import { PasswordInput } from "@/components/auth/password-input";
import { usePlan } from "@/components/plan/plan-provider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Link, useRouter } from "@/i18n/navigation";
import { readFirebaseErrorCode, isLikelyEmail } from "@/lib/email-format";
import { getFirebaseAuth, isFirebaseConfigured } from "@/lib/firebase";
import { mapProfileLevelToPlanTier } from "@/lib/plan-tier";
import { cn } from "@/lib/utils";
import {
  CEREBRO_AUTH_TOKEN_KEY,
  getPortalSession,
  postPortalRegister,
  toUserFacingApiError,
} from "@/services/apiService";

const TENANT_STORAGE_KEY = "cerebro-tenant-id";

type FieldKey = "inviteCode" | "email" | "password";

export default function RegisterPage() {
  const t = useTranslations("registerPage");
  const tCommon = useTranslations("common");
  const tApi = useTranslations("api");
  const router = useRouter();
  const { setTier, setFeatures, setProfileLevel } = usePlan();
  const translateApi = React.useCallback((key: string) => tApi(key), [tApi]);
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [inviteCode, setInviteCode] = React.useState("");
  const [fieldErrors, setFieldErrors] = React.useState<Partial<Record<FieldKey, string>>>({});
  const inviteRef = React.useRef<HTMLInputElement>(null);
  const emailRef = React.useRef<HTMLInputElement>(null);
  const passwordRef = React.useRef<HTMLInputElement>(null);
  const [submitting, setSubmitting] = React.useState(false);
  const submittingRef = React.useRef(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});
    if (!isFirebaseConfigured()) {
      toast.error(t("firebaseNotConfigured"));
      return;
    }
    if (!inviteCode.trim()) {
      setFieldErrors({ inviteCode: t("inviteRequired") });
      inviteRef.current?.focus();
      return;
    }
    if (!isLikelyEmail(email)) {
      setFieldErrors({ email: t("invalidEmail") });
      emailRef.current?.focus();
      return;
    }
    if (password.length < 6) {
      setFieldErrors({ password: t("passwordTooShort") });
      passwordRef.current?.focus();
      return;
    }
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const auth = getFirebaseAuth();
      const mail = email.trim();
      let cred;
      try {
        cred = await createUserWithEmailAndPassword(auth, mail, password);
      } catch (createErr: unknown) {
        const code =
          createErr && typeof createErr === "object" && "code" in createErr
            ? String((createErr as { code: string }).code)
            : "";
        if (code === "auth/email-already-in-use") {
          cred = await signInWithEmailAndPassword(auth, mail, password);
        } else {
          throw createErr;
        }
      }
      let token = await cred.user.getIdToken();
      try {
        localStorage.setItem(CEREBRO_AUTH_TOKEN_KEY, token);
      } catch {
        /* ignore */
      }
      const res = await postPortalRegister(inviteCode);
      await cred.user.getIdToken(true);
      token = await cred.user.getIdToken();
      try {
        localStorage.setItem(CEREBRO_AUTH_TOKEN_KEY, token);
        localStorage.setItem(TENANT_STORAGE_KEY, res.tenantId.trim());
      } catch {
        /* ignore */
      }
      setTier(mapProfileLevelToPlanTier(res.profileLevel));
      setProfileLevel(res.profileLevel);
      try {
        const session = await getPortalSession();
        setProfileLevel(session.profileLevel);
        setFeatures(session.features ?? {});
      } catch {
        setFeatures({});
      }
      toast.success(t("success"));
      router.push("/");
    } catch (err) {
      const code = readFirebaseErrorCode(err);
      if (code === "auth/weak-password") {
        setFieldErrors({ password: t("weakPassword") });
        passwordRef.current?.focus();
      } else if (code === "auth/invalid-email") {
        setFieldErrors({ email: t("invalidEmail") });
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
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t("title")}</h1>
          <p className="mt-2 text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          <div className="space-y-2">
            <Label htmlFor="reg-invite">{t("inviteCode")}</Label>
            <Input
              ref={inviteRef}
              id="reg-invite"
              value={inviteCode}
              onChange={(e) => {
                setInviteCode(e.target.value);
                setFieldErrors((prev) => {
                  if (!prev.inviteCode) return prev;
                  const next = { ...prev };
                  delete next.inviteCode;
                  return next;
                });
              }}
              autoComplete="off"
              aria-invalid={Boolean(fieldErrors.inviteCode)}
              aria-describedby={fieldErrors.inviteCode ? "reg-invite-err" : undefined}
              className="min-h-11"
            />
            {fieldErrors.inviteCode ? (
              <p id="reg-invite-err" className="text-sm text-destructive" role="alert">
                {fieldErrors.inviteCode}
              </p>
            ) : null}
          </div>
          <div className="space-y-2">
            <Label htmlFor="reg-email">{t("email")}</Label>
            <Input
              ref={emailRef}
              id="reg-email"
              type="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                setFieldErrors((prev) => {
                  if (!prev.email) return prev;
                  const next = { ...prev };
                  delete next.email;
                  return next;
                });
              }}
              autoComplete="email"
              aria-invalid={Boolean(fieldErrors.email)}
              aria-describedby={fieldErrors.email ? "reg-email-err" : undefined}
              className="min-h-11"
            />
            {fieldErrors.email ? (
              <p id="reg-email-err" className="text-sm text-destructive" role="alert">
                {fieldErrors.email}
              </p>
            ) : null}
          </div>
          <div className="space-y-2">
            <Label htmlFor="reg-password">{t("password")}</Label>
            <PasswordInput
              ref={passwordRef}
              id="reg-password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setFieldErrors((prev) => {
                  if (!prev.password) return prev;
                  const next = { ...prev };
                  delete next.password;
                  return next;
                });
              }}
              autoComplete="new-password"
              toggleShowLabel={tCommon("showPassword")}
              toggleHideLabel={tCommon("hidePassword")}
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={fieldErrors.password ? "reg-password-err" : undefined}
              minLength={6}
            />
            {fieldErrors.password ? (
              <p id="reg-password-err" className="text-sm text-destructive" role="alert">
                {fieldErrors.password}
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
          <Link href="/login" className="underline-offset-4 hover:underline">
            {t("hasAccount")}
          </Link>
        </p>
        <Button variant="ghost" asChild className="w-full">
          <Link href="/">{t("backHome")}</Link>
        </Button>
      </div>
    </div>
  );
}
