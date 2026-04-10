"use client";

import { onAuthStateChanged } from "firebase/auth";
import * as React from "react";

import { usePlan } from "@/components/plan/plan-provider";
import { getFirebaseAuth, isFirebaseConfigured } from "@/lib/firebase";
import { mapProfileLevelToPlanTier } from "@/lib/plan-tier";
import {
  CEREBRO_AUTH_TOKEN_KEY,
  getPortalSession,
} from "@/services/apiService";

/**
 * Mantém o ID token Firebase em localStorage e alinha o plano da UI ao perfil do utilizador (GET /v1/auth/me).
 */
export function SessionProfileSync() {
  const { setTier } = usePlan();

  React.useEffect(() => {
    if (typeof window === "undefined" || !isFirebaseConfigured()) {
      return;
    }
    const auth = getFirebaseAuth();
    return onAuthStateChanged(auth, async (user) => {
      if (!user) {
        try {
          localStorage.removeItem(CEREBRO_AUTH_TOKEN_KEY);
        } catch {
          /* ignore */
        }
        return;
      }
      try {
        const token = await user.getIdToken();
        localStorage.setItem(CEREBRO_AUTH_TOKEN_KEY, token);
      } catch {
        try {
          localStorage.removeItem(CEREBRO_AUTH_TOKEN_KEY);
        } catch {
          /* ignore */
        }
        return;
      }
      try {
        const s = await getPortalSession();
        setTier(mapProfileLevelToPlanTier(s.profileLevel));
      } catch {
        try {
          localStorage.removeItem(CEREBRO_AUTH_TOKEN_KEY);
        } catch {
          /* ignore */
        }
      }
    });
  }, [setTier]);

  return null;
}
