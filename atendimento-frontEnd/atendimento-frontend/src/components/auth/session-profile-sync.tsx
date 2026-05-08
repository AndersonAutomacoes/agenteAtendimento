"use client";

import { onAuthStateChanged } from "firebase/auth";
import * as React from "react";

import { usePlan } from "@/components/plan/plan-provider";
import { getFirebaseAuth, isFirebaseConfigured } from "@/lib/firebase";
import { mapProfileLevelToPlanTier } from "@/lib/plan-tier";
import { triggerSessionExpired } from "@/lib/auth-session";
import { setBillingBlockedCookieClient } from "@/lib/billing-cookie";
import {
  CEREBRO_AUTH_TOKEN_KEY,
  getPortalSession,
  ApiHttpError,
} from "@/services/apiService";

/**
 * Mantém o ID token Firebase em localStorage e alinha o plano da UI ao perfil do utilizador (GET /v1/auth/me).
 */
export function SessionProfileSync() {
  const { setTier, setFeatures, setProfileLevel } = usePlan();

  React.useEffect(() => {
    if (typeof window === "undefined" || !isFirebaseConfigured()) {
      return;
    }
    const auth = getFirebaseAuth();
    return onAuthStateChanged(auth, async (user) => {
      if (!user) {
        triggerSessionExpired();
        return;
      }
      try {
        const token = await user.getIdToken();
        localStorage.setItem(CEREBRO_AUTH_TOKEN_KEY, token);
      } catch {
        triggerSessionExpired();
        return;
      }
      try {
        const s = await getPortalSession();
        setProfileLevel(s.profileLevel);
        setTier(mapProfileLevelToPlanTier(s.profileLevel));
        setFeatures(s.features ?? {});
        setBillingBlockedCookieClient(s.billing.blocked);
      } catch (e) {
        if (
          e instanceof ApiHttpError &&
          (e.status === 401 || e.status === 403)
        ) {
          triggerSessionExpired();
        }
      }
    });
  }, [setTier, setFeatures, setProfileLevel]);

  return null;
}
