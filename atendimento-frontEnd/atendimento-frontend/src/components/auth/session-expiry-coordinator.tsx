"use client";

import { signOut } from "firebase/auth";
import * as React from "react";

import { usePlan } from "@/components/plan/plan-provider";
import { useRouter } from "@/i18n/navigation";
import {
  SESSION_EXPIRED_EVENT,
  clearAuthStorage,
  installAuth401FetchInterceptor,
  isPublicAppPath,
  normalizeAppPathname,
  triggerSessionExpired,
} from "@/lib/auth-session";
import { getFirebaseAuth, isFirebaseConfigured } from "@/lib/firebase";
import { readDefaultPlanTierFromEnv } from "@/lib/plan-tier";

/**
 * Responde a sessão expirada / logout implícito: limpa armazenamento, repõe plano e redireciona para login nas rotas protegidas.
 * Também instala interceptor global de fetch para 401 em pedidos com Bearer.
 */
export function SessionExpiryCoordinator() {
  const router = useRouter();
  const { setTier, setFeatures, setProfileLevel } = usePlan();

  React.useEffect(() => {
    const handler = () => {
      void (async () => {
        clearAuthStorage();
        try {
          if (isFirebaseConfigured()) {
            await signOut(getFirebaseAuth());
          }
        } catch {
          /* ignore */
        }
        setProfileLevel("BASIC");
        setTier(readDefaultPlanTierFromEnv());
        setFeatures({});
        const path =
          typeof window !== "undefined"
            ? normalizeAppPathname(window.location.pathname)
            : "/";
        if (!isPublicAppPath(path)) {
          router.replace("/login");
        }
      })();
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, handler);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handler);
  }, [router, setTier, setFeatures, setProfileLevel]);

  React.useEffect(() => {
    return installAuth401FetchInterceptor(() => {
      triggerSessionExpired();
    });
  }, []);

  return null;
}
