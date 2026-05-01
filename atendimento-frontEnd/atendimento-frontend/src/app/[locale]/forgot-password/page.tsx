"use client";

import { sendPasswordResetEmail } from "firebase/auth";
import { Loader2 } from "lucide-react";
import * as React from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Link } from "@/i18n/navigation";
import { getFirebaseAuth, isFirebaseConfigured } from "@/lib/firebase";
import { cn } from "@/lib/utils";

export default function ForgotPasswordPage() {
  const t = useTranslations("forgotPasswordPage");
  const [email, setEmail] = React.useState("");
  const [submitting, setSubmitting] = React.useState(false);
  const submittingRef = React.useRef(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isFirebaseConfigured()) {
      toast.error(t("firebaseNotConfigured"));
      return;
    }
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const auth = getFirebaseAuth();
      await sendPasswordResetEmail(auth, email.trim());
      toast.success(t("success"));
    } catch {
      toast.error(t("error"));
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
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="forgot-email">{t("email")}</Label>
            <Input
              id="forgot-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              required
            />
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
        <Button variant="ghost" asChild className="w-full">
          <Link href="/login">{t("backLogin")}</Link>
        </Button>
      </div>
    </div>
  );
}
