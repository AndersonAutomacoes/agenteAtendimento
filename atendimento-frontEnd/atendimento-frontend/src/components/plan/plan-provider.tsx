"use client";

import * as React from "react";
import type { PlanFeatureKey, ProfileLevel } from "@/services/apiService";
import { CEREBRO_AUTH_TOKEN_KEY, getPortalSession } from "@/services/apiService";

import { SessionProfileSync } from "@/components/auth/session-profile-sync";
import {
  PLAN_CHANGED_EVENT,
  PLAN_STORAGE_KEY,
  type PlanTier,
  mapProfileLevelToPlanTier,
  parsePlanTier,
  readDefaultPlanTierFromEnv,
} from "@/lib/plan-tier";

type PlanContextValue = {
  tier: PlanTier;
  profileLevel: ProfileLevel;
  setProfileLevel: (profileLevel: ProfileLevel) => void;
  setTier: (tier: PlanTier) => void;
  features: Partial<Record<PlanFeatureKey, boolean>>;
  setFeatures: (features: Partial<Record<PlanFeatureKey, boolean>>) => void;
  featuresHydrated: boolean;
};

const PlanContext = React.createContext<PlanContextValue | null>(null);
const PLAN_FEATURES_STORAGE_KEY = "cerebro-plan-features";

function readTierFromStorage(): PlanTier {
  if (typeof window === "undefined") {
    return readDefaultPlanTierFromEnv();
  }
  try {
    return parsePlanTier(localStorage.getItem(PLAN_STORAGE_KEY)) ?? readDefaultPlanTierFromEnv();
  } catch {
    return readDefaultPlanTierFromEnv();
  }
}

function readFeaturesFromStorage(): Partial<Record<PlanFeatureKey, boolean>> {
  if (typeof window === "undefined") {
    return {};
  }
  try {
    const raw = localStorage.getItem(PLAN_FEATURES_STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const out: Partial<Record<PlanFeatureKey, boolean>> = {};
    const keys: PlanFeatureKey[] = [
      "DASHBOARD",
      "ANALYTICS",
      "ANALYTICS_EXPORT_CSV",
      "ANALYTICS_EXPORT_PDF",
      "APPOINTMENTS",
      "KNOWLEDGE_BASE",
      "MONITORING",
      "SETTINGS",
    ];
    for (const k of keys) {
      if (typeof parsed[k] === "boolean") out[k] = parsed[k] as boolean;
    }
    return out;
  } catch {
    return {};
  }
}

type PlanProviderProps = {
  children: React.ReactNode;
};

export function PlanProvider({ children }: PlanProviderProps) {
  const [tier, setTierState] = React.useState<PlanTier>(() => readDefaultPlanTierFromEnv());
  const [profileLevel, setProfileLevelState] = React.useState<ProfileLevel>("BASIC");
  const [features, setFeaturesState] = React.useState<
    Partial<Record<PlanFeatureKey, boolean>>
  >({});
  const [featuresHydrated, setFeaturesHydrated] = React.useState(false);

  const setTier = React.useCallback((next: PlanTier) => {
    try {
      localStorage.setItem(PLAN_STORAGE_KEY, next);
    } catch {
      /* ignore */
    }
    setTierState(next);
    if (typeof window !== "undefined") {
      window.dispatchEvent(new Event(PLAN_CHANGED_EVENT));
    }
  }, []);

  const setProfileLevel = React.useCallback((next: ProfileLevel) => {
    setProfileLevelState(next);
  }, []);

  const setFeatures = React.useCallback(
    (next: Partial<Record<PlanFeatureKey, boolean>>) => {
      try {
        localStorage.setItem(PLAN_FEATURES_STORAGE_KEY, JSON.stringify(next));
      } catch {
        /* ignore */
      }
      setFeaturesState(next);
      setFeaturesHydrated(true);
    },
    [],
  );

  React.useEffect(() => {
    setTierState(readTierFromStorage());
    setFeaturesState(readFeaturesFromStorage());
  }, []);

  React.useEffect(() => {
    if (typeof window === "undefined") return;
    let cancelled = false;
    const token = localStorage.getItem(CEREBRO_AUTH_TOKEN_KEY);
    if (!token) {
      setFeaturesHydrated(true);
      return;
    }
    void (async () => {
      try {
        const s = await getPortalSession();
        if (cancelled) return;
        setProfileLevelState(s.profileLevel);
        setTierState(mapProfileLevelToPlanTier(s.profileLevel));
        const nextFeatures = s.features ?? {};
        try {
          localStorage.setItem(PLAN_FEATURES_STORAGE_KEY, JSON.stringify(nextFeatures));
        } catch {
          /* ignore */
        }
        setFeaturesState(nextFeatures);
      } catch {
        // Keep last known features to avoid false lockouts.
      } finally {
        if (!cancelled) {
          setFeaturesHydrated(true);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  React.useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key !== PLAN_STORAGE_KEY || e.newValue == null) return;
      const parsed = parsePlanTier(e.newValue);
      if (parsed) setTierState(parsed);
    };
    const onLocal = () => setTierState(readTierFromStorage());
    window.addEventListener("storage", onStorage);
    window.addEventListener(PLAN_CHANGED_EVENT, onLocal);
    return () => {
      window.removeEventListener("storage", onStorage);
      window.removeEventListener(PLAN_CHANGED_EVENT, onLocal);
    };
  }, []);

  const value = React.useMemo(
    () => ({
      tier,
      profileLevel,
      setProfileLevel,
      setTier,
      features,
      setFeatures,
      featuresHydrated,
    }),
    [tier, profileLevel, setProfileLevel, setTier, features, setFeatures, featuresHydrated],
  );

  return (
    <PlanContext.Provider value={value}>
      <SessionProfileSync />
      {children}
    </PlanContext.Provider>
  );
}

export function usePlan(): PlanContextValue {
  const ctx = React.useContext(PlanContext);
  if (!ctx) {
    throw new Error("usePlan must be used within PlanProvider");
  }
  return ctx;
}

/** Para módulos que preferem não depender do provider antes da árvore estar pronta. */
export function usePlanOptional(): PlanContextValue | null {
  return React.useContext(PlanContext);
}
