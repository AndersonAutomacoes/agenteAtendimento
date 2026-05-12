"use client";

import { onAuthStateChanged } from "firebase/auth";
import { Loader2 } from "lucide-react";
import * as React from "react";
import { useTranslations } from "next-intl";

import { useRouter } from "@/i18n/navigation";
import { getFirebaseAuth, isFirebaseConfigured } from "@/lib/firebase";
import { CEREBRO_AUTH_TOKEN_KEY } from "@/services/apiService";

type AppAuthenticatedGateProps = {
  children: React.ReactNode;
};

/**
 * Área autenticada: só renderiza a app quando há sessão (Firebase ou token em localStorage sem Firebase).
 * Sem sessão → landing pública.
 */
export function AppAuthenticatedGate({ children }: AppAuthenticatedGateProps) {
  const router = useRouter();
  const t = useTranslations("common");
  const [allowed, setAllowed] = React.useState(false);

  React.useEffect(() => {
    const goLanding = () => {
      router.replace("/landing");
    };

    if (!isFirebaseConfigured()) {
      try {
        const token = localStorage.getItem(CEREBRO_AUTH_TOKEN_KEY);
        if (!token || !token.trim()) {
          goLanding();
          return;
        }
      } catch {
        goLanding();
        return;
      }
      setAllowed(true);
      return;
    }

    const auth = getFirebaseAuth();
    const unsub = onAuthStateChanged(auth, (user) => {
      if (!user) {
        goLanding();
        return;
      }
      setAllowed(true);
    });
    return () => unsub();
  }, [router]);

  if (!allowed) {
    return (
      <div
        className="flex min-h-screen flex-col items-center justify-center gap-3 bg-background text-muted-foreground"
        aria-busy="true"
        aria-live="polite"
      >
        <Loader2 className="h-8 w-8 shrink-0 animate-spin" aria-hidden />
        <span className="text-sm">{t("loadingApp")}</span>
      </div>
    );
  }

  return <>{children}</>;
}
