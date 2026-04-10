import type { LucideIcon } from "lucide-react";
import {
  BookOpen,
  Bot,
  Brain,
  LayoutDashboard,
  MessageSquareText,
  MonitorDot,
} from "lucide-react";

import type { PlanTier } from "@/lib/plan-tier";

export type NavLabelKey =
  | "dashboard"
  | "knowledge"
  | "testChat"
  | "monitoring"
  | "settings";

export type NavSubKey = "knowledgeSub" | "monitoringSub";

export type AppNavItem = {
  href: string;
  labelKey: NavLabelKey;
  subKey?: NavSubKey;
  icon: LucideIcon;
  /** Plano mínimo para o item ficar ativo; omitido equivale a starter. */
  minPlan?: PlanTier;
};

export const APP_NAV_ITEMS: AppNavItem[] = [
  { href: "/", labelKey: "dashboard", icon: LayoutDashboard },
  {
    href: "/knowledge",
    labelKey: "knowledge",
    subKey: "knowledgeSub",
    icon: BookOpen,
    minPlan: "pro",
  },
  { href: "/test-chat", labelKey: "testChat", icon: MessageSquareText },
  {
    href: "/dashboard/monitoramento",
    labelKey: "monitoring",
    subKey: "monitoringSub",
    icon: MonitorDot,
    minPlan: "pro",
  },
  { href: "/settings", labelKey: "settings", icon: Bot },
];

export const NAV_BRAIN_ICON = Brain;
