"use client";

import * as React from "react";

import { SessionProfileSync } from "@/components/auth/session-profile-sync";
import {
  PLAN_CHANGED_EVENT,
  PLAN_STORAGE_KEY,
  type PlanTier,
  parsePlanTier,
  readDefaultPlanTierFromEnv,
} from "@/lib/plan-tier";

type PlanContextValue = {
  tier: PlanTier;
  setTier: (tier: PlanTier) => void;
};

const PlanContext = React.createContext<PlanContextValue | null>(null);

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

type PlanProviderProps = {
  children: React.ReactNode;
};

export function PlanProvider({ children }: PlanProviderProps) {
  const [tier, setTierState] = React.useState<PlanTier>(() => readDefaultPlanTierFromEnv());

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

  React.useEffect(() => {
    setTierState(readTierFromStorage());
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

  const value = React.useMemo(() => ({ tier, setTier }), [tier, setTier]);

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
