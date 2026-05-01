"use client";

import { sendPasswordResetEmail } from "firebase/auth";
import { Loader2 } from "lucide-react";
import * as React from "react";
import { useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Link } from "@/i18n/navigation";
import { readFirebaseErrorCode, isLikelyEmail } from "@/lib/email-format";
import { getFirebaseAuth, isFirebaseConfigured } from "@/lib/firebase";
import { cn } from "@/lib/utils";

export default function ForgotPasswordPage() {
  const t = useTranslations("forgotPasswordPage");
  const [email, setEmail] = React.useState("");
  const [emailError, setEmailError] = React.useState<string | null>(null);
  const [submitError, setSubmitError] = React.useState<string | null>(null);
  const [successVisible, setSuccessVisible] = React.useState(false);
  const [configError, setConfigError] = React.useState(false);
  const emailRef = React.useRef<HTMLInputElement>(null);
  const [submitting, setSubmitting] = React.useState(false);
  const submittingRef = React.useRef(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setEmailError(null);
    setSubmitError(null);
    setSuccessVisible(false);
    setConfigError(false);
    if (!isFirebaseConfigured()) {
      setConfigError(true);
      return;
    }
    if (!isLikelyEmail(email)) {
      setEmailError(t("invalidEmail"));
      emailRef.current?.focus();
      return;
    }
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const auth = getFirebaseAuth();
      await sendPasswordResetEmail(auth, email.trim());
      setSuccessVisible(true);
    } catch (err) {
      const code = readFirebaseErrorCode(err);
      if (code === "auth/invalid-email") {
        setEmailError(t("invalidEmail"));
        emailRef.current?.focus();
      } else {
        setSubmitError(t("error"));
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
        {configError ? (
          <p className="text-sm text-destructive" role="alert">
            {t("firebaseNotConfigured")}
          </p>
        ) : null}
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {successVisible ? (
            <p className="text-sm text-muted-foreground" role="status" aria-live="polite">
              {t("success")}
            </p>
          ) : null}
          {!successVisible ? (
            <>
              <div className="space-y-2">
                <Label htmlFor="forgot-email">{t("email")}</Label>
                <Input
                  ref={emailRef}
                  id="forgot-email"
                  type="email"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    setEmailError(null);
                    setSubmitError(null);
                  }}
                  autoComplete="email"
                  aria-invalid={Boolean(emailError)}
                  aria-describedby={
                    emailError ? "forgot-email-err" : submitError ? "forgot-submit-err" : undefined
                  }
                  className="min-h-11"
                />
                {emailError ? (
                  <p id="forgot-email-err" className="text-sm text-destructive" role="alert">
                    {emailError}
                  </p>
                ) : null}
                {submitError ? (
                  <p id="forgot-submit-err" className="text-sm text-destructive" role="alert">
                    {submitError}
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
            </>
          ) : null}
        </form>
        <Button variant="ghost" asChild className="w-full">
          <Link href="/login">{t("backLogin")}</Link>
        </Button>
      </div>
    </div>
  );
}
