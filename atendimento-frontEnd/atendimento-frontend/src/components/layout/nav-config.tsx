import type { LucideIcon } from "lucide-react";
import {
  Building2,
  BookOpen,
  Bot,
  Brain,
  CalendarClock,
  LayoutDashboard,
  MessageSquareText,
  MonitorDot,
  SlidersHorizontal,
} from "lucide-react";

import type { PlanTier } from "@/lib/plan-tier";
import type { PlanFeatureKey, ProfileLevel } from "@/services/apiService";

export type NavLabelKey =
  | "dashboard"
  | "knowledge"
  | "testChat"
  | "monitoring"
  | "appointments"
  | "settings"
  | "internalTenants"
  | "internalPlans";

export type NavSubKey = "knowledgeSub" | "monitoringSub" | "appointmentsSub";

export type AppNavItem = {
  href: string;
  labelKey: NavLabelKey;
  subKey?: NavSubKey;
  icon: LucideIcon;
  featureKey?: PlanFeatureKey;
  requiredProfile?: ProfileLevel;
  /** Plano mínimo para o item ficar ativo; omitido equivale a starter. */
  minPlan?: PlanTier;
};

export const APP_NAV_ITEMS: AppNavItem[] = [
  { href: "/", labelKey: "dashboard", icon: LayoutDashboard, featureKey: "DASHBOARD" },
  {
    href: "/knowledge",
    labelKey: "knowledge",
    subKey: "knowledgeSub",
    icon: BookOpen,
    featureKey: "KNOWLEDGE_BASE",
    minPlan: "pro",
  },
  { href: "/test-chat", labelKey: "testChat", icon: MessageSquareText },
  {
    href: "/dashboard/monitoramento",
    labelKey: "monitoring",
    subKey: "monitoringSub",
    icon: MonitorDot,
    featureKey: "MONITORING",
  },
  {
    href: "/dashboard/appointments",
    labelKey: "appointments",
    subKey: "appointmentsSub",
    icon: CalendarClock,
    featureKey: "APPOINTMENTS",
    minPlan: "pro",
  },
  {
    href: "/settings",
    labelKey: "settings",
    icon: Bot,
    featureKey: "SETTINGS",
    minPlan: "pro",
  },
  {
    href: "/internal/tenants",
    labelKey: "internalTenants",
    icon: Building2,
    requiredProfile: "COMERCIAL",
  },
  {
    href: "/internal/plans",
    labelKey: "internalPlans",
    icon: SlidersHorizontal,
    requiredProfile: "COMERCIAL",
  },
];

export const NAV_BRAIN_ICON = Brain;
