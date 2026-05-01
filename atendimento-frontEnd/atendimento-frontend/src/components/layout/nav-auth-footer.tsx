"use client";

import { onAuthStateChanged, signOut } from "firebase/auth";
import { LogIn, LogOut } from "lucide-react";
import { useTranslations } from "next-intl";
import * as React from "react";

import { usePlan } from "@/components/plan/plan-provider";
import { Link, usePathname, useRouter } from "@/i18n/navigation";
import { getFirebaseAuth, isFirebaseConfigured } from "@/lib/firebase";
import { readDefaultPlanTierFromEnv } from "@/lib/plan-tier";
import { cn } from "@/lib/utils";
import { TENANT_STORAGE_KEY } from "@/lib/auth-session";
import { CEREBRO_AUTH_TOKEN_KEY } from "@/services/apiService";

type NavAuthFooterProps = {
  variant: "sidebar" | "drawer";
  onNavigate?: () => void;
};

export function NavAuthFooter({ variant, onNavigate }: NavAuthFooterProps) {
  const t = useTranslations("nav");
  const router = useRouter();
  const pathname = usePathname();
  const { setTier, setFeatures, setProfileLevel } = usePlan();

  const [authed, setAuthed] = React.useState(false);
  const [identityLabel, setIdentityLabel] = React.useState<string | null>(null);

  const refreshTokenOnlyAuth = React.useCallback(() => {
    try {
      const token = localStorage.getItem(CEREBRO_AUTH_TOKEN_KEY);
      setAuthed(Boolean(token));
      setIdentityLabel(token ? t("sessionActive") : null);
    } catch {
      setAuthed(false);
      setIdentityLabel(null);
    }
  }, [t]);

  React.useEffect(() => {
    if (!isFirebaseConfigured()) {
      refreshTokenOnlyAuth();
      return;
    }
    const auth = getFirebaseAuth();
    return onAuthStateChanged(auth, (user) => {
      setAuthed(Boolean(user));
      if (!user) {
        setIdentityLabel(null);
        return;
      }
      const primary =
        user.email?.trim() ||
        user.displayName?.trim() ||
        user.phoneNumber?.trim() ||
        null;
      setIdentityLabel(primary);
    });
  }, [pathname, refreshTokenOnlyAuth]);

  React.useEffect(() => {
    const sync = () => {
      if (!isFirebaseConfigured()) {
        refreshTokenOnlyAuth();
      }
    };
    window.addEventListener("storage", sync);
    window.addEventListener("focus", sync);
    return () => {
      window.removeEventListener("storage", sync);
      window.removeEventListener("focus", sync);
    };
  }, [refreshTokenOnlyAuth]);

  const linkClass =
    variant === "sidebar"
      ? "flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium text-sidebar-foreground/90 transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
      : "flex min-h-11 w-full items-center gap-2 rounded-lg px-3 py-3 text-sm font-medium text-foreground transition-colors hover:bg-accent hover:text-accent-foreground touch-manipulation";

  const handleLogout = async () => {
    try {
      if (isFirebaseConfigured()) {
        await signOut(getFirebaseAuth());
      }
    } catch {
      /* ignore */
    }
    try {
      localStorage.removeItem(CEREBRO_AUTH_TOKEN_KEY);
      localStorage.removeItem(TENANT_STORAGE_KEY);
    } catch {
      /* ignore */
    }
    setTier(readDefaultPlanTierFromEnv());
    setProfileLevel("BASIC");
    setFeatures({});
    setAuthed(false);
    onNavigate?.();
    router.push("/login");
  };

  if (authed) {
    const identityBlock =
      variant === "sidebar" ? (
        <div className="mb-2 px-3 pt-1">
          {identityLabel ? (
            <p
              className="truncate text-xs font-medium text-sidebar-foreground"
              title={identityLabel}
            >
              {identityLabel}
            </p>
          ) : (
            <p className="truncate text-xs text-muted-foreground">{t("loggedIn")}</p>
          )}
        </div>
      ) : (
        <div className="mb-1 px-3">
          {identityLabel ? (
            <p className="truncate text-xs font-medium text-foreground" title={identityLabel}>
              {identityLabel}
            </p>
          ) : (
            <p className="truncate text-xs text-muted-foreground">{t("loggedIn")}</p>
          )}
        </div>
      );

    return (
      <div className="w-full">
        {identityBlock}
        <button
          type="button"
          className={cn(linkClass, "text-left")}
          onClick={() => handleLogout()}
        >
          <LogOut className="h-4 w-4 shrink-0" aria-hidden />
          {t("logout")}
        </button>
      </div>
    );
  }

  return (
    <Link href="/login" className={linkClass} onClick={onNavigate}>
      <LogIn className="h-4 w-4 shrink-0" aria-hidden />
      {t("login")}
    </Link>
  );
}
