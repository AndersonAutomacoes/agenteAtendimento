"use client";

import { signOut } from "firebase/auth";
import * as React from "react";

import { usePlan } from "@/components/plan/plan-provider";
import {
  SESSION_EXPIRED_EVENT,
  buildLoginHrefWithCurrentLocale,
  clearAuthStorage,
  installAuth401FetchInterceptor,
  isPublicAppPath,
  normalizeAppPathname,
  triggerSessionExpired,
} from "@/lib/auth-session";
import { getFirebaseAuth, isFirebaseConfigured } from "@/lib/firebase";
import { readDefaultPlanTierFromEnv } from "@/lib/plan-tier";

/**
 * Responde a sessão expirada / logout implícito: limpa armazenamento, repõe plano e envia o browser para o login
 * (navegação completa) nas rotas protegidas. Instala interceptor global de {@code fetch} para 401 autenticados.
 */
let sessionExpiryFlowRunning = false;

export function SessionExpiryCoordinator() {
  const { setTier, setFeatures, setProfileLevel } = usePlan();

  React.useEffect(() => {
    const handler = () => {
      if (sessionExpiryFlowRunning) {
        return;
      }
      sessionExpiryFlowRunning = true;
      void (async () => {
        try {
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
            // Navegação completa: repõe estado do cliente e garante que se sai da área autenticada.
            window.location.replace(buildLoginHrefWithCurrentLocale());
          }
        } finally {
          sessionExpiryFlowRunning = false;
        }
      })();
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, handler);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handler);
  }, [setTier, setFeatures, setProfileLevel]);

  React.useEffect(() => {
    return installAuth401FetchInterceptor(() => {
      triggerSessionExpired();
    });
  }, []);

  return null;
}
